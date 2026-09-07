package holyflame.administration;

import holyflame.administration.model.Etablissement;
import holyflame.administration.service.RegimeAcademiqueService;
import holyflame.administration.service.RegimeAcademiqueService.ModeObtention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Section Universite, etape 1 : regime academique et regles de deliberation.
 *
 * Les tests sont regroupes par acteur, parce que c'est ainsi que le cahier des charges
 * decoupe le module : l'administrateur regle, l'enseignant et le directeur appliquent les
 * regles au moment de la deliberation, l'etudiant les lit sur son releve.
 */
class RegimeAcademiqueTest {

    private RegimeAcademiqueService service;
    private Etablissement etab;

    @BeforeEach
    void avantChaqueTest() {
        service = new RegimeAcademiqueService();
        etab = new Etablissement();
        etab.setNom("Universite de N'Djamena");
    }

    /** Le formulaire Parametres tel que le navigateur le soumet. */
    private Map<String, String> formulaire(String... clesValeurs) {
        Map<String, String> m = new HashMap<>();
        m.put("_regimeSoumis", "1");
        for (int i = 0; i < clesValeurs.length; i += 2) m.put(clesValeurs[i], clesValeurs[i + 1]);
        return m;
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Administrateur — regle le regime depuis Parametres")
    class Administrateur {

        @Test
        void unEtablissementNeufEstScolaireEtNonUniversitaire() {
            // Non-regression : tous les etablissements deja en base doivent rester scolaires.
            assertEquals("SCOLAIRE", etab.getRegimeAcademique());
            assertFalse(service.estLMD(etab));
            assertFalse(etab.estRegimeLMD());
        }

        @Test
        void basculerEnLMDDonneDesValeursParDefautUtilisables() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD"), true);

            assertTrue(service.estLMD(etab));
            assertEquals(10.0, service.seuilValidation(etab), 0.001);
            assertEquals(30, etab.getCreditsParSemestre());
            assertNull(etab.getNoteEliminatoire(), "pas de note eliminatoire tant qu'on ne l'active pas");
        }

        @Test
        void lAdministrateurChoisitSesPropresSeuils() {
            service.configurer(etab, formulaire(
                "regimeAcademique", "LMD",
                "seuilValidationUE", "12",
                "creditsParSemestre", "36",
                "compensationSemestrielle", "true",
                "noteEliminatoireActive", "true",
                "noteEliminatoire", "7"), true);

            assertEquals(12.0, service.seuilValidation(etab), 0.001);
            assertEquals(36, etab.getCreditsParSemestre());
            assertTrue(etab.isCompensationSemestrielle());
            assertEquals(7.0, etab.getNoteEliminatoire(), 0.001);
        }

        @Test
        void laVirguleDecimaleEstAcceptee() {
            // Un clavier francais produit "10,5" : le refuser silencieusement laisserait
            // l'administrateur croire que sa saisie a ete prise en compte.
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "seuilValidationUE", "10,5"), true);
            assertEquals(10.5, service.seuilValidation(etab), 0.001);
        }

        @Test
        void unSeuilAberrantEstIgnoreAuLieuDeToutRendreEchouable() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "seuilValidationUE", "25"), true);
            assertEquals(10.0, service.seuilValidation(etab), 0.001);

            service.configurer(etab, formulaire("regimeAcademique", "LMD", "seuilValidationUE", "pas un nombre"), true);
            assertEquals(10.0, service.seuilValidation(etab), 0.001);

            service.configurer(etab, formulaire("regimeAcademique", "LMD", "creditsParSemestre", "0"), true);
            assertEquals(30, etab.getCreditsParSemestre());
        }

        @Test
        void decocherLaCompensationLaDesactiveVraiment() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "compensationSemestrielle", "true"), true);
            assertTrue(etab.isCompensationSemestrielle());

            // Une case decochee n'est pas envoyee par le navigateur : c'est son absence qui compte.
            service.configurer(etab, formulaire("regimeAcademique", "LMD"), true);
            assertFalse(etab.isCompensationSemestrielle());
        }

        @Test
        void decocherLaNoteEliminatoireLaSupprime() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD",
                "noteEliminatoireActive", "true", "noteEliminatoire", "6"), true);
            assertEquals(6.0, etab.getNoteEliminatoire(), 0.001);

            service.configurer(etab, formulaire("regimeAcademique", "LMD"), true);
            assertNull(etab.getNoteEliminatoire());
        }

        @Test
        void enregistrerUnAutreOngletNeToucheAuxReglesDeDeliberation() {
            // L'ecran Parametres a plusieurs onglets. Enregistrer l'onglet "Identite" ne doit
            // pas desactiver la compensation au motif que sa case n'etait pas sur la page.
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "compensationSemestrielle", "true"), true);

            Map<String, String> autreOnglet = new HashMap<>();
            autreOnglet.put("DEVISE", "Travail et Discipline");
            service.configurer(etab, autreOnglet, false);

            assertTrue(etab.isCompensationSemestrielle(), "la compensation survit a l'enregistrement d'un autre onglet");
            assertTrue(service.estLMD(etab), "le regime aussi");
        }

        @Test
        void unRegimeInconnuEstRefuse() {
            service.configurer(etab, formulaire("regimeAcademique", "BACHELOR"), true);
            assertEquals("SCOLAIRE", etab.getRegimeAcademique());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Enseignant et directeur — deliberation sur les unites")
    class Deliberation {

        @BeforeEach
        void enRegimeUniversitaire() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "compensationSemestrielle", "true"), true);
        }

        @Test
        void uneUniteAuSeuilEstAcquise() {
            assertTrue(service.estAcquise(etab, 10.0), "10/20 pile valide l'unite");
            assertTrue(service.estAcquise(etab, 15.5));
            assertFalse(service.estAcquise(etab, 9.99));
        }

        @Test
        void uneUniteFaibleEstCompenseeQuandLeSemestreEstBon() {
            assertEquals(ModeObtention.COMPENSEE, service.statuer(etab, 8.0, 11.0));
        }

        @Test
        void uneUniteFaibleResteEchoueeQuandLeSemestreEstFaible() {
            assertEquals(ModeObtention.NON_VALIDEE, service.statuer(etab, 8.0, 9.0));
        }

        @Test
        void uneUniteAcquiseNestJamaisDiteCompensee() {
            // Distinction visible par l'etudiant sur son releve : elle doit rester exacte
            // meme quand le semestre entier est largement au-dessus du seuil.
            assertEquals(ModeObtention.ACQUISE, service.statuer(etab, 14.0, 16.0));
        }

        @Test
        void sansCompensationUneUniteFaibleEchoueMalgreUnBonSemestre() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD"), true); // case decochee
            assertEquals(ModeObtention.NON_VALIDEE, service.statuer(etab, 8.0, 14.0));
        }

        @Test
        void laNoteEliminatoireBloqueLaCompensation() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD",
                "compensationSemestrielle", "true",
                "noteEliminatoireActive", "true", "noteEliminatoire", "7"), true);

            assertEquals(ModeObtention.COMPENSEE, service.statuer(etab, 8.0, 12.0),
                "8 reste au-dessus de l'eliminatoire : compensable");
            assertEquals(ModeObtention.NON_VALIDEE, service.statuer(etab, 6.0, 12.0),
                "6 passe sous l'eliminatoire : plus rien ne la sauve");
        }

        @Test
        void unSeuilReleveDeplaceLaFrontiereDeValidation() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "seuilValidationUE", "12"), true);
            assertFalse(service.estAcquise(etab, 11.0), "11 ne suffit plus quand l'etablissement exige 12");
            assertTrue(service.estAcquise(etab, 12.0));
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Etudiant — ce que porte le releve de notes")
    class Etudiant {

        @BeforeEach
        void enRegimeUniversitaire() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD"), true);
        }

        @Test
        void lesMentionsSuiventLeBareme() {
            assertEquals("Ajourne", service.mention(etab, 9.5));
            assertEquals("Passable", service.mention(etab, 10.0));
            assertEquals("Passable", service.mention(etab, 11.99));
            assertEquals("Assez bien", service.mention(etab, 12.0));
            assertEquals("Bien", service.mention(etab, 14.0));
            assertEquals("Tres bien", service.mention(etab, 16.0));
            assertEquals("Tres bien", service.mention(etab, 20.0));
        }

        @Test
        void laMentionSuitLeSeuilChoisiParLEtablissement() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "seuilValidationUE", "12"), true);
            assertEquals("Ajourne", service.mention(etab, 11.0),
                "sous le seuil de l'etablissement, pas de mention");
            assertEquals("Assez bien", service.mention(etab, 12.0));
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Etablissement scolaire — rien ne change pour lui")
    class NonRegression {

        @Test
        void unCollegeResteEnDehorsDuRegimeUniversitaire() {
            Etablissement college = new Etablissement();
            college.setNom("College Holy Flame");

            assertFalse(service.estLMD(college));
            assertEquals("SCOLAIRE", college.getRegimeAcademique());
            // Les valeurs par defaut existent mais ne servent a rien tant qu'on est en scolaire.
            assertEquals(10.0, service.seuilValidation(college), 0.001);
        }

        @Test
        void repasserEnScolaireNeDetruitPasLesSeuilsDejaRegles() {
            service.configurer(etab, formulaire("regimeAcademique", "LMD", "seuilValidationUE", "12"), true);
            service.configurer(etab, formulaire("regimeAcademique", "SCOLAIRE", "seuilValidationUE", "12"), true);

            assertFalse(service.estLMD(etab));
            assertEquals(12.0, service.seuilValidation(etab), 0.001,
                "revenir en arriere par erreur ne doit pas couter le parametrage");
        }
    }
}
