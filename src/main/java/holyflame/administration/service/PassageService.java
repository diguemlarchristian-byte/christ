package holyflame.administration.service;

import holyflame.administration.model.Classe;
import holyflame.administration.model.DecisionPassage;
import holyflame.administration.model.Eleve;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.DecisionPassageRepository;
import holyflame.administration.repository.EleveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Logique partagee du passage de classe (resolution de la classe cible, application reelle
 * des decisions de fin d'annee) — utilisee a la fois par PassageController (cloture classe par
 * classe) et par AssistantClotureController (orchestration complete de la rentree), pour eviter
 * de dupliquer cette logique une troisieme fois.
 */
@Service
public class PassageService {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private DecisionPassageRepository decisionPassageRepository;
    @Autowired private JournalService journalService;
    @Autowired private AnneeScolaireService anneeScolaireService;

    public static final List<String> ORDRE_NIVEAUX = List.of(
        "Petite Section", "Moyenne Section", "Grande Section",
        "CP1", "CP2", "CE1", "CE2", "CM1", "CM2",
        "6ème", "5ème", "4ème", "3ème",
        "2nde", "1ère", "Terminale");

    public String niveauSuivant(String niveauActuel) {
        int idx = ORDRE_NIVEAUX.indexOf(niveauActuel);
        if (idx < 0 || idx == ORDRE_NIVEAUX.size() - 1) return null;
        return ORDRE_NIVEAUX.get(idx + 1);
    }

    public String anneeSuivante(String annee) {
        if (annee == null) return null;
        try {
            int debut = Integer.parseInt(annee.split("-")[0].trim());
            return (debut + 1) + "-" + (debut + 2);
        } catch (Exception e) {
            return annee;
        }
    }

    public String nomClasseCible(Classe classeOrigine, String niveauCible) {
        return classeOrigine.getNiveau() != null && classeOrigine.getNom() != null
            ? classeOrigine.getNom().replaceFirst(Pattern.quote(classeOrigine.getNiveau()), niveauCible)
            : niveauCible;
    }

    public Classe trouverOuCreerClasse(Classe classeOrigine, String niveauCible, String nouvelleAnnee, Long etabId) {
        String nomCible = nomClasseCible(classeOrigine, niveauCible);
        return classeRepository.findByNomIgnoreCaseAndAnneeScolaireAndEtablissementId(nomCible, nouvelleAnnee, etabId)
            .orElseGet(() -> {
                Classe c = new Classe();
                c.setNom(nomCible);
                c.setNiveau(niveauCible);
                c.setAnneeScolaire(nouvelleAnnee);
                c.setEtablissementId(etabId);
                return classeRepository.save(c);
            });
    }

    public static class ResultatCloture {
        public boolean succes;
        public String erreur;
        public int nbAdmis;
        public int nbRedouble;
        public int nbSortis;
    }

    /** Applique reellement le passage pour tous les eleves d'une classe (brouillon -> effectif).
        Idempotent : rappeler cette methode sur une classe deja cloturee ne fait que retraiter
        les eventuels eleves restants (deja sortis), sans effet indesirable. */
    public ResultatCloture cloturerClasse(Classe classeOrigine, String nouvelleAnnee, Long etabId) {
        anneeScolaireService.verifierModifiable(classeOrigine.getAnneeScolaire(), etabId);
        ResultatCloture resultat = new ResultatCloture();

        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeOrigine.getId());
        Map<Long, DecisionPassage> decisionParEleve = decisionPassageRepository
            .findByClasseOrigineIdAndAnneeScolaire(classeOrigine.getId(), classeOrigine.getAnneeScolaire()).stream()
            .collect(Collectors.toMap(d -> d.getEleve().getId(), d -> d, (a, b) -> a));

        if (eleves.isEmpty() || decisionParEleve.size() < eleves.size()) {
            resultat.succes = false;
            resultat.erreur = "Toutes les décisions doivent être validées avant de clôturer.";
            return resultat;
        }

        String niveauSuivantVal = niveauSuivant(classeOrigine.getNiveau());
        for (Eleve eleve : eleves) {
            String decision = decisionParEleve.get(eleve.getId()).getDecision();

            if ("SORT".equals(decision)) {
                eleve.setStatutInscription("ABANDON");
                eleveRepository.save(eleve);
                resultat.nbSortis++;
                journalService.log("ELEVE_SORTANT", "ELEVES",
                    eleve.getNom() + " " + eleve.getPrenom() + " (" + eleve.getMatricule() + ") — fin de scolarite dans l'etablissement");
                continue;
            }

            String niveauCible = "REDOUBLE".equals(decision) ? classeOrigine.getNiveau() : niveauSuivantVal;
            if (niveauCible == null) {
                eleve.setStatutInscription("ABANDON");
                eleveRepository.save(eleve);
                resultat.nbSortis++;
                journalService.log("ELEVE_FIN_CYCLE", "ELEVES",
                    eleve.getNom() + " " + eleve.getPrenom() + " (" + eleve.getMatricule() + ") — fin de cycle, aucun niveau suivant configure");
                continue;
            }

            Classe classeCible = trouverOuCreerClasse(classeOrigine, niveauCible, nouvelleAnnee, etabId);
            eleve.setClasse(classeCible);
            eleveRepository.save(eleve);
            if ("REDOUBLE".equals(decision)) {
                resultat.nbRedouble++;
                journalService.log("ELEVE_REDOUBLE", "ELEVES",
                    eleve.getNom() + " " + eleve.getPrenom() + " — reprend " + niveauCible + " (" + nouvelleAnnee + ")");
            } else {
                resultat.nbAdmis++;
                journalService.log("ELEVE_ADMIS", "ELEVES",
                    eleve.getNom() + " " + eleve.getPrenom() + " — passe en " + niveauCible + " (" + nouvelleAnnee + ")");
            }
        }
        resultat.succes = true;
        return resultat;
    }
}
