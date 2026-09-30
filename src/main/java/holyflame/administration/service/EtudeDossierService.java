package holyflame.administration.service;

import holyflame.administration.model.CritereAdmission;
import holyflame.administration.model.DemandeInscription;
import holyflame.administration.model.NoteAdmission;
import holyflame.administration.repository.CritereAdmissionRepository;
import holyflame.administration.repository.DemandeInscriptionRepository;
import holyflame.administration.repository.NoteAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'etude d'un dossier de candidature : noter, classer, decider.
 *
 * Trois principes y sont tenus, et chacun repond a une question qu'une famille pose un
 * jour ou l'autre.
 *
 * « Pourquoi mon enfant a-t-il ete refuse ? » — les notes sont conservees critere par
 * critere, jamais repliees en un seul total. Une commission qui ne peut plus detailler sa
 * decision ne peut plus la defendre.
 *
 * « Pourquoi lui et pas nous ? » — le classement suit la note, et rien d'autre. Il se
 * recalcule a l'identique a chaque consultation.
 *
 * « On nous avait dit admis » — admis n'est pas inscrit. Tant que la famille n'a pas
 * termine le dossier, la place reste a prendre, et la liste d'attente doit pouvoir etre
 * rappelee.
 */
@Service
public class EtudeDossierService {

    /** Une ligne de la grille, avec ce que le jury y a mis. */
    public record LigneGrille(CritereAdmission critere, Double note) {

        public boolean estNotee() { return note != null; }
    }

    /** Un dossier tel qu'il se presente a la commission. */
    public record Dossier(DemandeInscription demande,
                          List<LigneGrille> grille,
                          Double note,
                          int criteresNotes,
                          int criteresTotal) {

        /** Vrai quand toute la grille est remplie : noter a moitie fausse le classement. */
        public boolean estComplet() { return criteresTotal > 0 && criteresNotes == criteresTotal; }

        public boolean estVierge() { return criteresNotes == 0; }
    }

    /** Une place dans la liste d'attente. */
    public record Rang(int position, DemandeInscription demande, Double note) {}

    private final CritereAdmissionRepository criteres;
    private final NoteAdmissionRepository notes;
    private final DemandeInscriptionRepository demandes;

    public EtudeDossierService(CritereAdmissionRepository criteres,
                               NoteAdmissionRepository notes,
                               DemandeInscriptionRepository demandes) {
        this.criteres = criteres;
        this.notes = notes;
        this.demandes = demandes;
    }

    /**
     * Le dossier d'une candidature, grille comprise.
     *
     * La grille montre les criteres actifs, plus ceux qui sont retires mais sur lesquels ce
     * dossier a deja ete note : sinon une note saisie l'an dernier disparaitrait de l'ecran
     * tout en pesant encore dans le total, et personne ne comprendrait le calcul.
     */
    public Dossier dossier(DemandeInscription demande) {
        Map<Long, Double> saisies = new LinkedHashMap<>();
        for (NoteAdmission n : notes.findByDemandeId(demande.getId())) {
            saisies.put(n.getCritereId(), n.getNote());
        }

        List<LigneGrille> grille = new ArrayList<>();
        for (CritereAdmission c : criteres.findByEtablissementIdOrderByOrdreAsc(demande.getEtablissementId())) {
            if (!c.isActif() && !saisies.containsKey(c.getId())) continue;
            grille.add(new LigneGrille(c, saisies.get(c.getId())));
        }

        int notees = (int) grille.stream().filter(LigneGrille::estNotee).count();
        return new Dossier(demande, grille, demande.getNoteDossier(), notees, grille.size());
    }

    /**
     * Enregistre les notes et recalcule le total.
     *
     * Une note hors de l'echelle est ignoree plutot que ramenee a une borne : 25 sur 20 est
     * une faute de frappe, et la transformer en 20 inventerait une appreciation que
     * personne n'a portee.
     */
    @Transactional
    public Double noter(DemandeInscription demande, Map<Long, Double> saisies) {
        List<CritereAdmission> actifs =
            criteres.findByEtablissementIdAndActifTrueOrderByOrdreAsc(demande.getEtablissementId());

        notes.deleteByDemandeId(demande.getId());

        double sommePonderee = 0;
        double sommePoids = 0;
        List<NoteAdmission> aEnregistrer = new ArrayList<>();

        for (CritereAdmission c : actifs) {
            Double note = saisies.get(c.getId());
            if (note == null || note < 0 || note > 20) continue;

            aEnregistrer.add(new NoteAdmission(demande.getId(), c.getId(), note));
            double poids = c.getPoids() != null && c.getPoids() > 0 ? c.getPoids() : 1.0;
            sommePonderee += note * poids;
            sommePoids += poids;
        }
        notes.saveAll(aEnregistrer);

        // Un dossier sans aucune note n'a pas de total. Poser zero le classerait dernier,
        // alors qu'il n'a simplement pas encore ete regarde.
        Double total = sommePoids > 0 ? sommePonderee / sommePoids : null;
        demande.setNoteDossier(total);
        demandes.save(demande);
        return total;
    }

    /** Porte la decision de la commission sur ce dossier. */
    @Transactional
    public void decider(DemandeInscription demande, String decision, String appreciation, String par) {
        demande.setStatut(decision);
        demande.setAppreciation(appreciation == null || appreciation.isBlank() ? null : appreciation.trim());
        demande.setDecidePar(par);
        demande.setDateDecision(LocalDateTime.now());
        demande.setDateTraitement(LocalDateTime.now());
        demande.setTraiteePar(par);
        demandes.save(demande);
    }

    /**
     * La liste d'attente, dans l'ordre ou l'ecole rappellera.
     *
     * Les dossiers sans note passent apres ceux qui en ont une : ils n'ont pas demerite,
     * ils n'ont pas encore ete etudies. Les placer devant reviendrait a servir celui que
     * personne n'a regarde avant celui qu'on a juge bon.
     */
    public List<Rang> listeDAttente(Long etablissementId, String anneeScolaire) {
        List<DemandeInscription> attente = demandes
            .findByEtablissementIdAndStatutOrderByDateDemandeDesc(
                etablissementId, DemandeInscription.LISTE_ATTENTE)
            .stream()
            .filter(d -> anneeScolaire == null || anneeScolaire.equals(d.getAnneeScolaire()))
            .sorted(Comparator
                .comparing((DemandeInscription d) -> d.getNoteDossier() == null)
                .thenComparing(d -> d.getNoteDossier() == null ? 0.0 : -d.getNoteDossier())
                .thenComparing(DemandeInscription::getDateDemande))
            .toList();

        List<Rang> rangs = new ArrayList<>();
        for (int i = 0; i < attente.size(); i++) {
            rangs.add(new Rang(i + 1, attente.get(i), attente.get(i).getNoteDossier()));
        }
        return rangs;
    }

    /** Efface les notes d'un dossier, sans toucher a la grille de l'etablissement. */
    @Transactional
    public void effacerNotes(DemandeInscription demande) {
        notes.deleteByDemandeId(demande.getId());
        demande.setNoteDossier(null);
        demandes.save(demande);
    }
}
