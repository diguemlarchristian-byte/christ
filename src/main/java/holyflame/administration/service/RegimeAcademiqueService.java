package holyflame.administration.service;

import holyflame.administration.model.Etablissement;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Regime academique d'un etablissement et regles de deliberation qui en decoulent.
 *
 * Deux acteurs se partagent ce service : l'administrateur, qui regle le regime et les seuils
 * depuis Parametres, et le moteur de calcul des resultats, qui les lit au moment de decider
 * si une unite d'enseignement est validee.
 *
 * Rien n'est ecrit en dur ici. La compensation, la note eliminatoire et le seuil de validation
 * varient d'un pays et d'un etablissement a l'autre ; les figer dans le code produirait des
 * releves de notes faux pour tous ceux qui ne suivent pas la regle majoritaire.
 */
@Service
public class RegimeAcademiqueService {

    public static final String SCOLAIRE = "SCOLAIRE";
    public static final String LMD = "LMD";

    /** Comment une unite d'enseignement a ete obtenue — figure sur le releve de notes. */
    public enum ModeObtention { ACQUISE, COMPENSEE, NON_VALIDEE }

    // ── Cote administrateur : reglage depuis l'ecran Parametres ──────────

    /**
     * Applique au dossier de l'etablissement le regime et les seuils saisis par l'administrateur.
     *
     * Une valeur hors bornes est ignoree plutot que corrigee en silence : un seuil de validation
     * a 25/20 rendrait toute unite impossible a valider, et l'administrateur chercherait
     * longtemps pourquoi ses etudiants echouent tous.
     *
     * @param formulaireComplet vrai quand la requete vient bien de l'ecran Parametres (champ
     *        {@code _regimeSoumis}) : sans lui, une case decochee — absente de la requete — serait
     *        confondue avec un champ simplement non soumis, et desactiverait la compensation a tort.
     */
    public void configurer(Etablissement etab, Map<String, String> params, boolean formulaireComplet) {
        if (etab == null || params == null) return;

        String regime = params.get("regimeAcademique");
        if (SCOLAIRE.equals(regime) || LMD.equals(regime)) {
            etab.setRegimeAcademique(regime);
        }

        lireDecimal(params, "seuilValidationUE")
            .filter(v -> v > 0 && v <= 20)
            .ifPresent(etab::setSeuilValidationUE);

        lireDecimal(params, "creditsParSemestre")
            .filter(v -> v > 0 && v <= 120)
            .ifPresent(v -> etab.setCreditsParSemestre(v.intValue()));

        if (formulaireComplet) {
            etab.setCompensationSemestrielle(params.containsKey("compensationSemestrielle"));
            if (!params.containsKey("noteEliminatoireActive")) {
                etab.setNoteEliminatoire(null);
            }
        }
        if (params.containsKey("noteEliminatoireActive")) {
            lireDecimal(params, "noteEliminatoire")
                .filter(v -> v >= 0 && v <= 20)
                .ifPresent(etab::setNoteEliminatoire);
        }
    }

    // ── Cote calcul : regles appliquees aux resultats des etudiants ──────

    public boolean estLMD(Etablissement etab) {
        return etab != null && LMD.equals(etab.getRegimeAcademique());
    }

    public double seuilValidation(Etablissement etab) {
        if (etab == null || etab.getSeuilValidationUE() == null) return 10.0;
        return etab.getSeuilValidationUE();
    }

    /** Une unite dont la moyenne atteint le seuil est acquise, sans condition. */
    public boolean estAcquise(Etablissement etab, double moyenneUE) {
        return moyenneUE >= seuilValidation(etab);
    }

    /**
     * Une unite sous le seuil peut-elle etre rattrapee par la moyenne du semestre ?
     * Non si l'etablissement refuse la compensation, non si la note eliminatoire est franchie.
     */
    public boolean peutEtreCompensee(Etablissement etab, double moyenneUE) {
        if (etab == null || !etab.isCompensationSemestrielle()) return false;
        Double eliminatoire = etab.getNoteEliminatoire();
        if (eliminatoire != null && moyenneUE < eliminatoire) return false;
        return true;
    }

    /**
     * Sort d'une unite, une fois la moyenne du semestre connue.
     *
     * @param moyenneSemestre moyenne du semestre ponderee par les credits.
     */
    public ModeObtention statuer(Etablissement etab, double moyenneUE, double moyenneSemestre) {
        if (estAcquise(etab, moyenneUE)) return ModeObtention.ACQUISE;
        if (moyenneSemestre >= seuilValidation(etab) && peutEtreCompensee(etab, moyenneUE)) {
            return ModeObtention.COMPENSEE;
        }
        return ModeObtention.NON_VALIDEE;
    }

    /** Mention portee au releve de notes semestriel. */
    public String mention(Etablissement etab, double moyenneSemestre) {
        if (moyenneSemestre < seuilValidation(etab)) return "Ajourne";
        if (moyenneSemestre >= 16) return "Tres bien";
        if (moyenneSemestre >= 14) return "Bien";
        if (moyenneSemestre >= 12) return "Assez bien";
        return "Passable";
    }

    /** Lit un nombre du formulaire en acceptant la virgule decimale. */
    private Optional<Double> lireDecimal(Map<String, String> params, String cle) {
        String brut = params.get(cle);
        if (brut == null || brut.isBlank()) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(brut.trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
