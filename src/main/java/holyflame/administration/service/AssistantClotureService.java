package holyflame.administration.service;

import holyflame.administration.model.AnneeScolaire;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.AnneeScolaireRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestration transactionnelle de la cloture reelle de l'annee : clore chaque classe encore
 * ouverte, activer la nouvelle annee, puis verrouiller l'ancienne — dans cet ordre precis
 * (AnneeScolaireService.cloturer refuse de verrouiller une annee encore active). Ceci doit
 * rester un bean Spring distinct du controleur : @Transactional n'a aucun effet sur un appel
 * interne (this.methode(...)) au sein d'une meme classe, seul un appel via le proxy Spring
 * (donc depuis un AUTRE bean) declenche reellement la transaction.
 */
@Service
public class AssistantClotureService {

    @Autowired private AnneeScolaireRepository anneeScolaireRepository;
    @Autowired private AnneeScolaireService anneeScolaireService;
    @Autowired private PassageService passageService;
    @Autowired private JournalService journalService;
    @Autowired private EtablissementService etablissementService;

    @Transactional
    public void executerCloture(Long etabId, String ancienneAnnee, String nouvelleAnnee, List<Classe> classes) {
        if (!anneeScolaireRepository.existsByEtablissementIdAndLibelle(etabId, nouvelleAnnee)) {
            anneeScolaireService.creer(nouvelleAnnee, etabId, ancienneAnnee, true, true, true);
        }

        int totalAdmis = 0, totalRedouble = 0, totalSortis = 0;
        for (Classe c : classes) {
            PassageService.ResultatCloture r = passageService.cloturerClasse(c, nouvelleAnnee, etabId);
            if (r.succes) {
                totalAdmis += r.nbAdmis;
                totalRedouble += r.nbRedouble;
                totalSortis += r.nbSortis;
            }
        }

        AnneeScolaire nouvelle = anneeScolaireRepository.findByEtablissementIdAndLibelle(etabId, nouvelleAnnee).orElseThrow();
        anneeScolaireService.activer(nouvelle.getId(), etabId);

        AnneeScolaire ancienne = anneeScolaireRepository.findByEtablissementIdAndLibelle(etabId, ancienneAnnee).orElseThrow();
        Utilisateur u = etablissementService.getCurrentUtilisateur();
        anneeScolaireService.cloturer(ancienne.getId(), etabId, u != null ? u.getId() : null);

        journalService.log("ANNEE_CLOTUREE", "ELEVES",
            ancienneAnnee + " → " + nouvelleAnnee + " : " + totalAdmis + " admis, " + totalRedouble
                + " redoublant(s), " + totalSortis + " sortant(s).");
    }
}
