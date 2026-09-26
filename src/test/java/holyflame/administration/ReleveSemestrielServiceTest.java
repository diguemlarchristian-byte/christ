package holyflame.administration;

import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.service.RegimeAcademiqueService;
import holyflame.administration.service.RegimeAcademiqueService.ModeObtention;
import holyflame.administration.service.ReleveSemestrielService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le releve semestriel est la piece qui manquait au regime universitaire : la maquette
 * declarait des unites a credits, les enseignants saisissaient des notes, et rien ne reliait
 * les deux. Les regles d'acquisition et de compensation etaient ecrites et testees, mais
 * appelees par aucun code.
 *
 * Ces tests fixent ce que le releve doit dire, et surtout ce qu'il doit refuser de dire : une
 * unite sans note ne se juge pas, et un semestre sans aucune note n'a pas de moyenne. Afficher
 * un zero a la place ferait echouer un etudiant sur une absence de saisie.
 */
@DisplayName("Releve de notes semestriel")
class ReleveSemestrielServiceTest {

    private final RegimeAcademiqueService regime = new RegimeAcademiqueService();
    private final ReleveSemestrielService service = new ReleveSemestrielService(regime);

    private Etablissement universite(boolean compensation) {
        Etablissement e = new Etablissement();
        e.setNom("Universite de N'Djamena");
        e.setRegimeAcademique("LMD");
        e.setSeuilValidationUE(10.0);
        e.setCompensationSemestrielle(compensation);
        e.setNoteEliminatoire(7.0);
        return e;
    }

    private Eleve etudiant() {
        Eleve e = new Eleve();
        e.setId(1L); e.setNom("NGARTA"); e.setPrenom("Josue");
        return e;
    }

    private UniteEnseignement ue(long id, String code, String intitule, int credits, int semestre) {
        UniteEnseignement u = new UniteEnseignement();
        u.setId(id); u.setCode(code); u.setIntitule(intitule);
        u.setCredits(credits); u.setSemestre(semestre);
        return u;
    }

    /** Une unite d'un bloc donne : "FONDAMENTALE" pour le bloc A, autre chose pour le B. */
    private UniteEnseignement ue(long id, String code, String intitule, int credits, int semestre, String type) {
        UniteEnseignement u = ue(id, code, intitule, credits, semestre);
        u.setType(type);
        return u;
    }

    private ElementConstitutif ec(long id, long ueId, String intitule, Long matiereId, double coef) {
        ElementConstitutif e = new ElementConstitutif();
        e.setId(id); e.setUniteEnseignementId(ueId); e.setIntitule(intitule);
        e.setMatiereId(matiereId); e.setCoefficient(coef);
        return e;
    }

    private Note note(long matiereId, double valeur) {
        Matiere m = new Matiere();
        m.setId(matiereId);
        Note n = new Note();
        n.setMatiere(m); n.setValeur(valeur);
        return n;
    }

    @Nested
    @DisplayName("Un semestre ordinaire")
    class SemestreOrdinaire {

        @Test
        void laMoyenneDUneUniteSuitLesCoefficientsDeSesElements() {
            var unites = List.of(ue(1, "UE11", "Mathematiques appliquees", 6, 1));
            var elements = List.of(
                ec(1, 1, "Algebre lineaire", 10L, 2.0),
                ec(2, 1, "Statistiques", 20L, 1.0));
            var notes = List.of(note(10L, 15.0), note(20L, 9.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre lineaire", 20L, "Statistiques"));

            // (15 x 2 + 9 x 1) / 3 = 13
            assertEquals(13.0, releve.unites().get(0).moyenne(), 0.001,
                "le coefficient de l'element doit peser, pas le nombre de notes");
        }

        @Test
        void laMoyenneDuSemestreSuitLesCreditsDesUnites() {
            var unites = List.of(
                ue(1, "UE11", "Mathematiques", 6, 1),
                ue(2, "UE12", "Expression", 2, 1));
            var elements = List.of(
                ec(1, 1, "Algebre", 10L, 1.0),
                ec(2, 2, "Francais", 20L, 1.0));
            var notes = List.of(note(10L, 12.0), note(20L, 4.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre", 20L, "Francais"));

            // (12 x 6 + 4 x 2) / 8 = 10
            assertEquals(10.0, releve.moyenneSemestre(), 0.001,
                "une unite pese son poids dans le diplome, pas son nombre d'evaluations");
        }

        @Test
        void uneUniteAuDessusDuSeuilEstAcquiseEtRapporteSesCredits() {
            var unites = List.of(ue(1, "UE11", "Mathematiques", 6, 1));
            var elements = List.of(ec(1, 1, "Algebre", 10L, 1.0));
            var notes = List.of(note(10L, 14.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre"));

            assertEquals(ModeObtention.ACQUISE, releve.unites().get(0).obtention());
            assertEquals(6, releve.creditsAcquis());
            assertEquals(6, releve.creditsPossibles());
        }

        @Test
        void leReleveAnnonceLaMentionDuSemestre() {
            var unites = List.of(ue(1, "UE11", "Mathematiques", 6, 1));
            var elements = List.of(ec(1, 1, "Algebre", 10L, 1.0));
            var notes = List.of(note(10L, 16.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre"));

            assertTrue(releve.mention() != null && !releve.mention().isBlank(),
                "un releve porte la mention obtenue : " + releve.mention());
        }
    }

    @Nested
    @DisplayName("La compensation")
    class Compensation {

        @Test
        void uneUniteFaibleEstCompenseeQuandLeSemestreLePermet() {
            var unites = List.of(
                ue(1, "UE11", "Mathematiques", 6, 1),
                ue(2, "UE12", "Expression", 6, 1));
            var elements = List.of(
                ec(1, 1, "Algebre", 10L, 1.0),
                ec(2, 2, "Francais", 20L, 1.0));
            var notes = List.of(note(10L, 15.0), note(20L, 9.0));

            var releve = service.calculer(universite(true), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre", 20L, "Francais"));

            assertEquals(ModeObtention.ACQUISE, releve.unites().get(0).obtention());
            assertEquals(ModeObtention.COMPENSEE, releve.unites().get(1).obtention(),
                "9 sur 20 passe par compensation quand la moyenne du semestre atteint le seuil");
            assertEquals(12, releve.creditsAcquis(), "une unite compensee rapporte ses credits");
        }

        @Test
        void uneNoteEliminatoireBloqueLaCompensation() {
            var unites = List.of(
                ue(1, "UE11", "Mathematiques", 6, 1),
                ue(2, "UE12", "Expression", 6, 1));
            var elements = List.of(
                ec(1, 1, "Algebre", 10L, 1.0),
                ec(2, 2, "Francais", 20L, 1.0));
            var notes = List.of(note(10L, 18.0), note(20L, 5.0));

            var releve = service.calculer(universite(true), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre", 20L, "Francais"));

            assertEquals(ModeObtention.NON_VALIDEE, releve.unites().get(1).obtention(),
                "sous la note eliminatoire, aucune moyenne de semestre ne rattrape l'unite");
            assertEquals(6, releve.creditsAcquis(), "seuls les credits de l'unite acquise comptent");
        }

        @Test
        void sansCompensationUneUniteFaibleResteNonValidee() {
            var unites = List.of(
                ue(1, "UE11", "Mathematiques", 6, 1),
                ue(2, "UE12", "Expression", 6, 1));
            var elements = List.of(
                ec(1, 1, "Algebre", 10L, 1.0),
                ec(2, 2, "Francais", 20L, 1.0));
            var notes = List.of(note(10L, 15.0), note(20L, 9.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre", 20L, "Francais"));

            assertEquals(ModeObtention.NON_VALIDEE, releve.unites().get(1).obtention());
            assertEquals(6, releve.creditsAcquis());
        }
    }

    @Nested
    @DisplayName("Ce que le releve refuse de dire")
    class Prudence {

        @Test
        void uneUniteSansAucuneNoteNEstPasJugee() {
            var unites = List.of(ue(1, "UE11", "Mathematiques", 6, 1));
            var elements = List.of(ec(1, 1, "Algebre", 10L, 1.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements,
                List.of(), Map.of(10L, "Algebre"));

            var ligne = releve.unites().get(0);
            assertNull(ligne.moyenne(), "aucune note ne vaut pas zero");
            assertNull(ligne.obtention(), "et une unite non evaluee ne peut etre declaree non validee");
            assertEquals(0, releve.creditsAcquis());
            assertEquals(6, releve.creditsPossibles(), "les credits en jeu restent annonces");
        }

        @Test
        void unSemestreSansAucuneNoteNaPasDeMoyenne() {
            var unites = List.of(ue(1, "UE11", "Mathematiques", 6, 1));
            var elements = List.of(ec(1, 1, "Algebre", 10L, 1.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements,
                List.of(), Map.of(10L, "Algebre"));

            assertTrue(releve.sansAucuneNote());
            assertNull(releve.moyenneSemestre(), "afficher 0 ferait echouer un etudiant sur une absence de saisie");
            assertNull(releve.mention());
        }

        @Test
        void unElementSansMatiereRattacheeEstSignaleSansFausserLUnite() {
            var unites = List.of(ue(1, "UE11", "Mathematiques", 6, 1));
            var elements = List.of(
                ec(1, 1, "Algebre", 10L, 1.0),
                ec(2, 1, "Travaux diriges", null, 1.0));
            var notes = List.of(note(10L, 14.0));

            var releve = service.calculer(universite(false), etudiant(), 1, unites, elements, notes,
                Map.of(10L, "Algebre"));

            var ligne = releve.unites().get(0);
            assertEquals(14.0, ligne.moyenne(), 0.001,
                "un element sans matiere ne doit pas tirer la moyenne vers le bas");
            assertNull(ligne.elements().get(1).matiere(),
                "mais il apparait au releve, pour qu'on voie ce qui reste a rattacher");
        }

        @Test
        void unParcoursSansUniteDonneUnReleveVideEtNonUnReleveFaux() {
            var releve = service.calculer(universite(false), etudiant(), 1,
                List.of(), List.of(), List.of(), Map.of());

            assertTrue(releve.estVide());
            assertNull(releve.moyenneSemestre());
            assertEquals(0, releve.creditsPossibles());
        }
    }

    @Nested
    @DisplayName("Le bilan annuel, pour la deliberation")
    class BilanAnnuel {

        private ReleveSemestrielService.Releve semestre(int numero, double moyenne, int credits) {
            var unites = List.of(ue(numero, "UE" + numero, "Unite " + numero, credits, numero));
            var elements = List.of(ec(numero, numero, "Cours", 10L + numero, 1.0));
            var notes = List.of(note(10L + numero, moyenne));
            return service.calculer(universite(false), etudiant(), numero, unites, elements, notes,
                Map.of(10L + numero, "Cours"));
        }

        @Test
        void lesCreditsDesDeuxSemestresSAdditionnent() {
            var bilan = service.bilanAnnuel(universite(false), etudiant(),
                List.of(semestre(1, 14.0, 30), semestre(2, 12.0, 30)));

            assertEquals(60, bilan.creditsAcquis(), "une annee de licence vaut soixante credits");
            assertEquals(60, bilan.creditsPossibles());
        }

        @Test
        void laMoyenneAnnuellePondereChaqueSemestreParSesCredits() {
            var bilan = service.bilanAnnuel(universite(false), etudiant(),
                List.of(semestre(1, 16.0, 40), semestre(2, 10.0, 20)));

            // (16 x 40 + 10 x 20) / 60 = 14
            assertEquals(14.0, bilan.moyenneAnnuelle(), 0.001,
                "un semestre plus charge pese davantage dans l'annee");
        }

        @Test
        void unSemestreSansNoteNeTirePasLAnneeVersLeBas() {
            var unites = List.of(ue(2, "UE2", "A venir", 30, 2));
            var elements = List.of(ec(2, 2, "Cours", 99L, 1.0));
            var vide = service.calculer(universite(false), etudiant(), 2, unites, elements,
                List.of(), Map.of(99L, "Cours"));

            var bilan = service.bilanAnnuel(universite(false), etudiant(), List.of(semestre(1, 15.0, 30), vide));

            assertEquals(15.0, bilan.moyenneAnnuelle(), 0.001,
                "le second semestre n'est pas encore evalue : il ne compte pas encore");
            assertEquals(30, bilan.creditsAcquis());
            assertEquals(60, bilan.creditsPossibles(), "mais les credits en jeu restent annonces");
        }

        @Test
        void uneAnneeSansAucuneNoteNAPasDeMoyenne() {
            var unites = List.of(ue(1, "UE1", "Unite", 30, 1));
            var elements = List.of(ec(1, 1, "Cours", 99L, 1.0));
            var vide = service.calculer(universite(false), etudiant(), 1, unites, elements,
                List.of(), Map.of(99L, "Cours"));

            var bilan = service.bilanAnnuel(universite(false), etudiant(), List.of(vide));

            assertTrue(bilan.sansAucuneNote());
            assertNull(bilan.moyenneAnnuelle());
        }
    }

    @Test
    void lesElementsDUneUniteNeRemontentPasDansUneAutre() {
        var unites = List.of(
            ue(1, "UE11", "Mathematiques", 6, 1),
            ue(2, "UE12", "Expression", 6, 1));
        var elements = new ArrayList<>(List.of(
            ec(1, 1, "Algebre", 10L, 1.0),
            ec(2, 2, "Francais", 20L, 1.0)));
        var notes = List.of(note(10L, 18.0), note(20L, 2.0));

        var releve = service.calculer(universite(false), etudiant(), 1, unites, elements, notes,
            Map.of(10L, "Algebre", 20L, "Francais"));

        assertEquals(18.0, releve.unites().get(0).moyenne(), 0.001);
        assertEquals(2.0, releve.unites().get(1).moyenne(), 0.001);
        assertEquals(1, releve.unites().get(0).elements().size(),
            "chaque unite ne porte que ses propres elements");
    }

    /**
     * Les trois situations du document « Regles de progression des etudiants dans le systeme
     * LMD » (ESU, septembre 2020), reproduites a l'identique.
     *
     * Ce document sert de reference aux jurys : ses tableaux disent, chiffres a l'appui, ce
     * qu'un etudiant capitalise dans trois cas types. Si le logiciel s'en ecarte, il accorde
     * ou refuse des credits qu'un jury referait a la main — et c'est le logiciel qu'on
     * cessera d'utiliser, pas le document.
     *
     * Le bloc A rassemble les unites fondamentales, le bloc B les unites transversales,
     * linguistiques et preprofessionnelles. La compensation ne traverse pas cette frontiere.
     */
    @Nested
    @DisplayName("Les tableaux du document de reference (ESU, RDC)")
    class DocumentDeReference {

        /** L'etablissement du document : compensation active, aucune note eliminatoire. */
        private Etablissement esu() {
            Etablissement e = universite(true);
            e.setNoteEliminatoire(null);
            return e;
        }

        /** Une unite a note unique : le document ne detaille pas les elements constitutifs. */
        private void poser(List<UniteEnseignement> unites, List<ElementConstitutif> elements,
                           List<Note> notes, Map<Long, String> matieres,
                           long id, String code, String bloc, double note, int credits, int semestre) {
            unites.add(ue(id, code, code, credits, semestre, bloc));
            elements.add(ec(id, id, code, id, 1.0));
            notes.add(note(id, note));
            matieres.put(id, code);
        }

        /** Le premier semestre du document, avec la note de EDU102 en parametre. */
        private ReleveSemestrielService.Releve semestreUn(double noteEdu102) {
            List<UniteEnseignement> unites = new ArrayList<>();
            List<ElementConstitutif> elements = new ArrayList<>();
            List<Note> notes = new ArrayList<>();
            Map<Long, String> matieres = new java.util.LinkedHashMap<>();

            poser(unites, elements, notes, matieres, 1, "FRA121", "TRANSVERSALE", 8, 4, 1);
            poser(unites, elements, notes, matieres, 2, "EDU101", "TRANSVERSALE", 12, 3, 1);
            poser(unites, elements, notes, matieres, 3, "EDU102", "TRANSVERSALE", noteEdu102, 6, 1);
            poser(unites, elements, notes, matieres, 4, "ANG121", "TRANSVERSALE", 14, 3, 1);
            poser(unites, elements, notes, matieres, 5, "CHI101", "FONDAMENTALE", 12, 6, 1);
            poser(unites, elements, notes, matieres, 6, "MAT192", "FONDAMENTALE", 8, 6, 1);
            poser(unites, elements, notes, matieres, 7, "CHI102", "FONDAMENTALE", 15, 2, 1);

            return service.calculer(esu(), etudiant(), 1, unites, elements, notes, matieres);
        }

        /** Le second semestre du document. */
        private ReleveSemestrielService.Releve semestreDeux() {
            List<UniteEnseignement> unites = new ArrayList<>();
            List<ElementConstitutif> elements = new ArrayList<>();
            List<Note> notes = new ArrayList<>();
            Map<Long, String> matieres = new java.util.LinkedHashMap<>();

            poser(unites, elements, notes, matieres, 11, "INF121", "TRANSVERSALE", 10.7, 6, 2);
            poser(unites, elements, notes, matieres, 12, "EDU103", "TRANSVERSALE", 11, 3, 2);
            poser(unites, elements, notes, matieres, 13, "CHI103", "FONDAMENTALE", 12, 4, 2);
            poser(unites, elements, notes, matieres, 14, "MAT121", "TRANSVERSALE", 11.5, 3, 2);
            poser(unites, elements, notes, matieres, 15, "CHI104", "FONDAMENTALE", 9, 6, 2);
            poser(unites, elements, notes, matieres, 16, "CHI105", "FONDAMENTALE", 9.3, 4, 2);
            poser(unites, elements, notes, matieres, 17, "PHY198", "FONDAMENTALE", 10, 4, 2);

            return service.calculer(esu(), etudiant(), 2, unites, elements, notes, matieres);
        }

        @Test
        @DisplayName("Tableau 1 : les deux blocs ont la moyenne, tout est capitalise")
        void tableauUn() {
            var r = semestreUn(10);

            assertEquals(10.714, r.moyenneCategorie(UniteEnseignement.CATEGORIE_FONDAMENTALE), 0.001,
                "moyenne du bloc A : 150 points sur 14 credits");
            assertEquals(10.625, r.moyenneCategorie(UniteEnseignement.CATEGORIE_TRANSVERSALE), 0.001,
                "moyenne du bloc B : 170 points sur 16 credits");
            assertEquals(10.667, r.moyenneSemestre(), 0.001, "moyenne du semestre : 320 sur 30");

            // Les deux blocs atteignent dix : MAT192 (8) et FRA121 (8) sont rachetees par
            // leur propre bloc, et l'etudiant emporte les trente credits.
            assertEquals(30, r.creditsAcquis(), "les sept unites sont capitalisees");
        }

        @Test
        @DisplayName("Tableau 2 : un bloc sous dix, malgre la moyenne du semestre")
        void tableauDeux() {
            var r = semestreUn(7);

            assertEquals(10.714, r.moyenneCategorie(UniteEnseignement.CATEGORIE_FONDAMENTALE), 0.001);
            assertEquals(9.5, r.moyenneCategorie(UniteEnseignement.CATEGORIE_TRANSVERSALE), 0.001,
                "le bloc B tombe a 9.5 : 152 points sur 16 credits");
            assertEquals(10.067, r.moyenneSemestre(), 0.001,
                "le semestre reste au-dessus de dix, et cela ne suffit pas");

            // C'est tout l'interet de la separation en blocs : sans elle, la moyenne du
            // semestre a 10.07 aurait rendu les trente credits, dont ceux d'un bloc echoue.
            assertEquals(20, r.creditsAcquis(),
                "14 credits du bloc A compense, plus EDU101 et ANG121 acquises seules");

            assertEquals(ModeObtention.NON_VALIDEE, statut(r, "FRA121"),
                "son bloc ne la rachete pas");
            assertEquals(ModeObtention.NON_VALIDEE, statut(r, "EDU102"), "ni celle-ci");
            assertEquals(ModeObtention.COMPENSEE, statut(r, "MAT192"),
                "mais le bloc A rachete la sienne");
            assertEquals(ModeObtention.ACQUISE, statut(r, "ANG121"),
                "et ce qui depasse dix seul est acquis sans rien devoir a personne");
        }

        @Test
        @DisplayName("Tableau 3 : l'annee rattrape ce que le semestre avait perdu")
        void tableauTrois() {
            var s1 = semestreUn(7);
            var s2 = semestreDeux();

            assertEquals(9.956, s2.moyenneCategorie(UniteEnseignement.CATEGORIE_FONDAMENTALE), 0.001,
                "au second semestre, c'est le bloc A qui passe sous dix");
            assertEquals(20, s2.creditsAcquis(), "vingt credits la aussi");

            var bilan = service.bilanAnnuel(esu(), etudiant(), List.of(s1, s2));

            assertEquals(10.288, bilan.moyenneAnnuelleCategorie(UniteEnseignement.CATEGORIE_FONDAMENTALE), 0.001,
                "sur l'annee, le bloc A repasse au-dessus de dix");
            assertEquals(10.132, bilan.moyenneAnnuelleCategorie(UniteEnseignement.CATEGORIE_TRANSVERSALE), 0.001,
                "et le bloc B aussi");
            assertEquals(10.215, bilan.moyenneAnnuelle(), 0.001, "moyenne annuelle : 612.9 sur 60");

            // Chaque semestre pris seul ne rendait que vingt credits. L'annee, qui est
            // l'unite de deliberation, les rend tous.
            assertEquals(60, bilan.creditsAcquis(),
                "les deux blocs ont la moyenne sur l'annee : soixante credits capitalises");
            assertTrue(bilan.compensationAnnuelleAJoue(),
                "et l'etudiant doit pouvoir lire que c'est l'annee qui l'a rattrape");
        }

        private ModeObtention statut(ReleveSemestrielService.Releve r, String code) {
            return r.unites().stream()
                .filter(l -> code.equals(l.unite().getCode()))
                .findFirst().orElseThrow().obtention();
        }
    }
}
