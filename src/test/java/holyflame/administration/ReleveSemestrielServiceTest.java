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
}
