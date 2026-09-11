package holyflame.administration.service;

import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Parcours;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.repository.ElementConstitutifRepository;
import holyflame.administration.repository.ParcoursRepository;
import holyflame.administration.repository.UniteEnseignementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maquette pedagogique : parcours, unites d'enseignement et elements constitutifs.
 *
 * La maquette est le contrat academique de l'etablissement. Une erreur ici — un semestre a
 * 24 credits au lieu de 30, une unite sans element — ne se voit qu'a la deliberation, quand
 * les notes sont deja saisies et qu'il est trop tard pour corriger sans tout ressaisir.
 * D'ou les controles ci-dessous, qui signalent les incoherences au moment ou la maquette est
 * construite plutot que de laisser le probleme apparaitre en fin de semestre.
 */
@Service
public class MaquettePedagogiqueService {

    @Autowired private ParcoursRepository parcoursRepository;
    @Autowired private UniteEnseignementRepository ueRepository;
    @Autowired private ElementConstitutifRepository ecueRepository;

    /** Refus d'une operation pour une raison que l'administrateur peut corriger. */
    public static class MaquetteRefusee extends RuntimeException {
        public MaquetteRefusee(String message) { super(message); }
    }

    /** Etat d'un semestre au regard du volume de credits attendu. */
    public record EtatSemestre(int semestre, int creditsPrevus, int creditsAttendus, int nbUnites) {
        public boolean estComplet() { return creditsPrevus == creditsAttendus; }
        public int ecart() { return creditsPrevus - creditsAttendus; }
    }

    // ── Parcours ────────────────────────────────────────────────────────

    @Transactional
    public Parcours creerParcours(String libelle, String diplome, Integer nbSemestres, Long etablissementId) {
        if (libelle == null || libelle.isBlank()) {
            throw new MaquetteRefusee("Le libelle du parcours est obligatoire.");
        }
        if (nbSemestres == null || nbSemestres < 1 || nbSemestres > 12) {
            throw new MaquetteRefusee("Un parcours compte entre 1 et 12 semestres.");
        }
        parcoursRepository.findByLibelleIgnoreCaseAndEtablissementId(libelle.trim(), etablissementId)
            .ifPresent(p -> { throw new MaquetteRefusee("Un parcours porte deja ce nom."); });

        Parcours p = new Parcours();
        p.setLibelle(libelle.trim());
        p.setDiplome(diplome);
        p.setNbSemestres(nbSemestres);
        p.setEtablissementId(etablissementId);
        return parcoursRepository.save(p);
    }

    // ── Unites d'enseignement ───────────────────────────────────────────

    @Transactional
    public UniteEnseignement ajouterUnite(Long parcoursId, String code, String intitule,
                                          Integer credits, Integer semestre, String type,
                                          Long etablissementId) {
        // Le rattachement au parcours est verifie ici et non par une contrainte NOT NULL :
        // la table est partagee avec un autre module universitaire qui la remplit autrement.
        if (parcoursId == null) {
            throw new MaquetteRefusee("Une unite d'enseignement appartient toujours a un parcours.");
        }
        Parcours parcours = parcoursRepository.findById(parcoursId)
            .orElseThrow(() -> new MaquetteRefusee("Parcours introuvable."));
        if (intitule == null || intitule.isBlank()) {
            throw new MaquetteRefusee("L'intitule de l'unite est obligatoire.");
        }
        if (credits == null || credits < 1) {
            throw new MaquetteRefusee("Une unite d'enseignement porte au moins un credit.");
        }
        if (semestre == null || semestre < 1 || semestre > parcours.getNbSemestres()) {
            throw new MaquetteRefusee("Le semestre doit se situer entre 1 et " + parcours.getNbSemestres()
                + ", la duree du parcours.");
        }
        String codeRetenu;
        if (code != null && !code.isBlank()) {
            codeRetenu = code.trim();
            ueRepository.findByCodeIgnoreCaseAndEtablissementId(codeRetenu, etablissementId)
                .ifPresent(u -> { throw new MaquetteRefusee("Ce code d'unite est deja utilise."); });
        } else {
            codeRetenu = genererCode(semestre, etablissementId);
        }

        UniteEnseignement ue = new UniteEnseignement();
        ue.setCode(codeRetenu);
        ue.setIntitule(intitule.trim());
        ue.setCredits(credits);
        ue.setSemestre(semestre);
        if (type != null && !type.isBlank()) ue.setType(type);
        ue.setParcoursId(parcoursId);
        ue.setEtablissementId(etablissementId);
        return ueRepository.save(ue);
    }

    /**
     * Code de secours lorsque l'administrateur n'en saisit pas.
     *
     * Le code figure sur le releve de notes de l'etudiant : une unite doit toujours pouvoir
     * y etre designee, meme quand la maquette a ete montee vite. La forme « UE-S1-03 » reste
     * lisible et se renomme ensuite sans consequence.
     */
    private String genererCode(int semestre, Long etablissementId) {
        for (int rang = 1; rang <= 999; rang++) {
            String candidat = String.format("UE-S%d-%02d", semestre, rang);
            if (ueRepository.findByCodeIgnoreCaseAndEtablissementId(candidat, etablissementId).isEmpty()) {
                return candidat;
            }
        }
        throw new MaquetteRefusee("Trop d'unites sur ce semestre pour generer un code automatiquement.");
    }

    /**
     * Supprime une unite et les elements qu'elle contient.
     *
     * Les elements sont supprimes explicitement : sans cela ils resteraient orphelins en base,
     * invisibles dans l'interface mais toujours rattaches a des notes.
     */
    @Transactional
    public void supprimerUnite(Long uniteId) {
        ecueRepository.deleteByUniteEnseignementId(uniteId);
        ueRepository.deleteById(uniteId);
    }

    // ── Elements constitutifs ───────────────────────────────────────────

    @Transactional
    public ElementConstitutif ajouterElement(Long uniteId, String intitule, Double coefficient,
                                             Integer volumeHoraire, Long enseignantId,
                                             Long matiereId, Long etablissementId) {
        ueRepository.findById(uniteId)
            .orElseThrow(() -> new MaquetteRefusee("Unite d'enseignement introuvable."));
        if (intitule == null || intitule.isBlank()) {
            throw new MaquetteRefusee("L'intitule de l'element est obligatoire.");
        }
        if (coefficient == null || coefficient <= 0) {
            throw new MaquetteRefusee("Le coefficient doit etre superieur a zero.");
        }

        ElementConstitutif ec = new ElementConstitutif();
        ec.setIntitule(intitule.trim());
        ec.setCoefficient(coefficient);
        ec.setVolumeHoraire(volumeHoraire);
        // La matiere reste facultative : on declare souvent la maquette avant que les matieres
        // de l'annee existent. Un element sans matiere est signale a l'ecran du releve plutot
        // que refuse ici, pour ne pas bloquer la construction du programme.
        ec.setMatiereId(matiereId);
        ec.setEnseignantId(enseignantId);
        ec.setUniteEnseignementId(uniteId);
        ec.setEtablissementId(etablissementId);
        return ecueRepository.save(ec);
    }

    // ── Controle de coherence ───────────────────────────────────────────

    /**
     * Volume de credits de chaque semestre d'un parcours, compare a ce que l'etablissement attend.
     *
     * Presente a l'administrateur pendant qu'il construit sa maquette : un semestre incomplet
     * est signale tout de suite, pas au moment de la premiere deliberation.
     */
    public List<EtatSemestre> controlerCredits(Parcours parcours, Etablissement etablissement) {
        int attendus = etablissement != null && etablissement.getCreditsParSemestre() != null
            ? etablissement.getCreditsParSemestre() : 30;
        int nbSemestres = parcours.getNbSemestres() != null ? parcours.getNbSemestres() : 0;

        Map<Integer, int[]> parSemestre = new LinkedHashMap<>();
        for (int s = 1; s <= nbSemestres; s++) parSemestre.put(s, new int[]{0, 0});

        for (UniteEnseignement ue : ueRepository.findByParcoursIdOrderBySemestreAscIntituleAsc(parcours.getId())) {
            int[] cumul = parSemestre.get(ue.getSemestre());
            if (cumul == null) continue; // unite hors bornes du parcours : ignoree ici
            cumul[0] += ue.getCredits() != null ? ue.getCredits() : 0;
            cumul[1] += 1;
        }

        List<EtatSemestre> etats = new ArrayList<>();
        parSemestre.forEach((semestre, cumul) ->
            etats.add(new EtatSemestre(semestre, cumul[0], attendus, cumul[1])));
        return etats;
    }

    /** Unites dont aucun element n'a ete declare : leur moyenne serait incalculable. */
    public List<UniteEnseignement> unitesSansElement(Long parcoursId) {
        List<UniteEnseignement> vides = new ArrayList<>();
        for (UniteEnseignement ue : ueRepository.findByParcoursIdOrderBySemestreAscIntituleAsc(parcoursId)) {
            if (ecueRepository.countByUniteEnseignementId(ue.getId()) == 0) vides.add(ue);
        }
        return vides;
    }

    /** Vrai quand la maquette peut porter une deliberation : credits justes et unites remplies. */
    public boolean estUtilisable(Parcours parcours, Etablissement etablissement) {
        return controlerCredits(parcours, etablissement).stream().allMatch(EtatSemestre::estComplet)
            && unitesSansElement(parcours.getId()).isEmpty();
    }

    // ── Lectures ────────────────────────────────────────────────────────

    public List<Parcours> parcoursDe(Long etablissementId) {
        return parcoursRepository.findByEtablissementIdOrderByLibelleAsc(etablissementId);
    }

    public List<UniteEnseignement> unitesDe(Long parcoursId) {
        return ueRepository.findByParcoursIdOrderBySemestreAscIntituleAsc(parcoursId);
    }

    public List<UniteEnseignement> unitesDuSemestre(Long parcoursId, int semestre) {
        return ueRepository.findByParcoursIdAndSemestreOrderByIntituleAsc(parcoursId, semestre);
    }

    public List<ElementConstitutif> elementsDe(Long uniteId) {
        return ecueRepository.findByUniteEnseignementIdOrderByIntituleAsc(uniteId);
    }

    /** Elements confies a un enseignant — ce qu'il verra a la saisie des notes. */
    public List<ElementConstitutif> elementsDeLEnseignant(Long enseignantId) {
        return ecueRepository.findByEnseignantIdOrderByIntituleAsc(enseignantId);
    }
}
