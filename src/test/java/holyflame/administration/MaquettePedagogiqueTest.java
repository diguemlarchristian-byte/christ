package holyflame.administration;

import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Parcours;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.service.MaquettePedagogiqueService;
import holyflame.administration.service.MaquettePedagogiqueService.EtatSemestre;
import holyflame.administration.service.MaquettePedagogiqueService.MaquetteRefusee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Section Universite, etape 2 : maquette pedagogique.
 *
 * Tests groupes par acteur. La maquette est le contrat academique de l'etablissement : une
 * erreur de credits ou une unite vide ne se verrait qu'a la deliberation, notes deja saisies.
 * Ces tests verrouillent les controles qui l'empechent.
 */
@SpringBootTest
@Transactional
class MaquettePedagogiqueTest {

    @Autowired private MaquettePedagogiqueService maquette;
    @Autowired private EtablissementRepository etablissementRepository;

    private Etablissement universite;

    @BeforeEach
    void creerUneUniversite() {
        Etablissement e = new Etablissement();
        e.setNom("Universite Test Maquette");
        e.setStatut("ACTIF");
        e.setDateCreation(LocalDate.now());
        e.setAnneeScolaire("2026-2027");
        e.setCodeAcces("MAQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        e.setRegimeAcademique("LMD");
        e.setCreditsParSemestre(30);
        universite = etablissementRepository.saveAndFlush(e);
    }

    private Parcours licenceGestion() {
        return maquette.creerParcours("Licence Gestion", "LICENCE", 6, universite.getId());
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Administrateur — construit la maquette")
    class Administrateur {

        @Test
        void creerUnParcoursDiplomant() {
            Parcours p = licenceGestion();

            assertEquals("Licence Gestion", p.getLibelle());
            assertEquals("LICENCE", p.getDiplome());
            assertEquals(6, p.getNbSemestres());
            assertTrue(p.isActif());
            assertEquals(universite.getId(), p.getEtablissementId());
        }

        @Test
        void deuxParcoursNePeuventPasPorterLeMemeNom() {
            licenceGestion();
            var refus = assertThrows(MaquetteRefusee.class,
                () -> maquette.creerParcours("licence gestion", "LICENCE", 6, universite.getId()));
            assertTrue(refus.getMessage().toLowerCase().contains("nom"));
        }

        @Test
        void uneDureeAberranteEstRefusee() {
            assertThrows(MaquetteRefusee.class,
                () -> maquette.creerParcours("Parcours vide", "LICENCE", 0, universite.getId()));
            assertThrows(MaquetteRefusee.class,
                () -> maquette.creerParcours("Parcours interminable", "LICENCE", 20, universite.getId()));
        }

        @Test
        void ajouterUneUniteAvecSesCredits() {
            Parcours p = licenceGestion();
            UniteEnseignement ue = maquette.ajouterUnite(p.getId(), "UE-L1-01",
                "Comptabilite generale", 6, 1, "FONDAMENTALE", universite.getId());

            assertEquals("Comptabilite generale", ue.getIntitule());
            assertEquals(6, ue.getCredits());
            assertEquals(1, ue.getSemestre());
            assertEquals("FONDAMENTALE", ue.getType());
        }

        @Test
        void uneUniteHorsDesSemestresDuParcoursEstRefusee() {
            Parcours p = licenceGestion(); // 6 semestres
            var refus = assertThrows(MaquetteRefusee.class, () -> maquette.ajouterUnite(
                p.getId(), null, "Unite fantome", 6, 7, "FONDAMENTALE", universite.getId()));
            assertTrue(refus.getMessage().contains("6"), "le message rappelle la duree du parcours");
        }

        @Test
        void uneUniteSansCreditEstRefusee() {
            Parcours p = licenceGestion();
            assertThrows(MaquetteRefusee.class, () -> maquette.ajouterUnite(
                p.getId(), null, "Unite sans credit", 0, 1, "FONDAMENTALE", universite.getId()));
        }

        @Test
        void unCodeDUniteNePeutPasServirDeuxFois() {
            Parcours p = licenceGestion();
            maquette.ajouterUnite(p.getId(), "UE-01", "Premiere", 6, 1, "FONDAMENTALE", universite.getId());
            var refus = assertThrows(MaquetteRefusee.class, () -> maquette.ajouterUnite(
                p.getId(), "ue-01", "Seconde", 6, 2, "FONDAMENTALE", universite.getId()));
            assertTrue(refus.getMessage().toLowerCase().contains("code"));
        }

        @Test
        void uneUniteSansCodeSaisiEnRecoitUnAutomatiquement() {
            // Le code figure sur le releve de notes : aucune unite ne peut en etre depourvue,
            // meme montee a la hate.
            Parcours p = licenceGestion();
            UniteEnseignement premiere = maquette.ajouterUnite(p.getId(), null, "Compta", 6, 1, null, universite.getId());
            UniteEnseignement seconde = maquette.ajouterUnite(p.getId(), "  ", "Droit", 6, 1, null, universite.getId());

            assertEquals("UE-S1-01", premiere.getCode());
            assertEquals("UE-S1-02", seconde.getCode(), "les codes generes ne se marchent pas dessus");

            UniteEnseignement autreSemestre = maquette.ajouterUnite(p.getId(), null, "Marketing", 6, 2, null, universite.getId());
            assertEquals("UE-S2-01", autreSemestre.getCode(), "la numerotation repart par semestre");
        }

        @Test
        void supprimerUneUniteEmporteSesElements() {
            Parcours p = licenceGestion();
            UniteEnseignement ue = maquette.ajouterUnite(p.getId(), null, "A supprimer", 6, 1, null, universite.getId());
            maquette.ajouterElement(ue.getId(), "Cours magistral", 2.0, 30, null, null, universite.getId());
            assertEquals(1, maquette.elementsDe(ue.getId()).size());

            maquette.supprimerUnite(ue.getId());

            assertTrue(maquette.elementsDe(ue.getId()).isEmpty(),
                "les elements ne doivent pas rester orphelins en base");
            assertTrue(maquette.unitesDe(p.getId()).isEmpty());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Administrateur — controle du volume de credits")
    class ControleCredits {

        @Test
        void unSemestreIncompletEstSignale() {
            Parcours p = licenceGestion();
            maquette.ajouterUnite(p.getId(), null, "Compta", 6, 1, null, universite.getId());
            maquette.ajouterUnite(p.getId(), null, "Droit", 6, 1, null, universite.getId());

            EtatSemestre s1 = maquette.controlerCredits(p, universite).get(0);
            assertEquals(12, s1.creditsPrevus());
            assertEquals(30, s1.creditsAttendus());
            assertFalse(s1.estComplet());
            assertEquals(-18, s1.ecart(), "il manque 18 credits pour boucler le semestre");
            assertEquals(2, s1.nbUnites());
        }

        @Test
        void unSemestreCompletEstReconnu() {
            Parcours p = licenceGestion();
            maquette.ajouterUnite(p.getId(), null, "Compta", 12, 1, null, universite.getId());
            maquette.ajouterUnite(p.getId(), null, "Droit", 12, 1, null, universite.getId());
            maquette.ajouterUnite(p.getId(), null, "Anglais", 6, 1, null, universite.getId());

            EtatSemestre s1 = maquette.controlerCredits(p, universite).get(0);
            assertEquals(30, s1.creditsPrevus());
            assertTrue(s1.estComplet());
            assertEquals(0, s1.ecart());
        }

        @Test
        void unExcedentDeCreditsEstSignaleAussi() {
            Parcours p = licenceGestion();
            maquette.ajouterUnite(p.getId(), null, "Trop genereuse", 36, 1, null, universite.getId());

            EtatSemestre s1 = maquette.controlerCredits(p, universite).get(0);
            assertFalse(s1.estComplet());
            assertEquals(6, s1.ecart(), "six credits de trop, tout aussi faux qu'un manque");
        }

        @Test
        void leControleSuitLeVolumeChoisiParLEtablissement() {
            // Un etablissement qui compte 36 credits par semestre ne doit pas etre juge sur 30.
            universite.setCreditsParSemestre(36);
            etablissementRepository.saveAndFlush(universite);

            Parcours p = licenceGestion();
            maquette.ajouterUnite(p.getId(), null, "Bloc unique", 36, 1, null, universite.getId());

            EtatSemestre s1 = maquette.controlerCredits(p, universite).get(0);
            assertEquals(36, s1.creditsAttendus());
            assertTrue(s1.estComplet());
        }

        @Test
        void tousLesSemestresDuParcoursSontRestitues() {
            Parcours p = licenceGestion();
            List<EtatSemestre> etats = maquette.controlerCredits(p, universite);

            assertEquals(6, etats.size(), "une licence a six semestres, tous a controler");
            assertTrue(etats.stream().allMatch(e -> e.creditsPrevus() == 0),
                "une maquette vide n'a encore aucun credit");
            assertEquals(1, etats.get(0).semestre());
            assertEquals(6, etats.get(5).semestre());
        }

        @Test
        void uneUniteSansElementRendLaMaquetteInutilisable() {
            Parcours p = licenceGestion();
            UniteEnseignement ue = maquette.ajouterUnite(p.getId(), null, "Vide", 30, 1, null, universite.getId());

            assertEquals(List.of(ue.getId()),
                maquette.unitesSansElement(p.getId()).stream().map(UniteEnseignement::getId).toList(),
                "une unite sans element a une moyenne incalculable");
            assertFalse(maquette.estUtilisable(p, universite));
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Enseignant — les elements dont il a la charge")
    class Enseignant {

        @Test
        void unEnseignantNeVoitQueSesPropresElements() {
            Parcours p = licenceGestion();
            UniteEnseignement ue = maquette.ajouterUnite(p.getId(), null, "Compta", 6, 1, null, universite.getId());

            Long moi = 4001L;
            Long collegue = 4002L;
            maquette.ajouterElement(ue.getId(), "Comptabilite - CM", 2.0, 30, moi, null, universite.getId());
            maquette.ajouterElement(ue.getId(), "Comptabilite - TD", 1.0, 20, moi, null, universite.getId());
            maquette.ajouterElement(ue.getId(), "Comptabilite - TP", 1.0, 10, collegue, null, universite.getId());

            List<ElementConstitutif> miens = maquette.elementsDeLEnseignant(moi);
            assertEquals(2, miens.size());
            assertTrue(miens.stream().allMatch(e -> moi.equals(e.getEnseignantId())));
            assertEquals(1, maquette.elementsDeLEnseignant(collegue).size());
        }

        @Test
        void unElementPeutResterSansEnseignantAffecte() {
            // La maquette se construit avant la rentree : l'affectation vient souvent plus tard.
            Parcours p = licenceGestion();
            UniteEnseignement ue = maquette.ajouterUnite(p.getId(), null, "Droit", 6, 1, null, universite.getId());
            ElementConstitutif ec = maquette.ajouterElement(ue.getId(), "Droit civil", 1.0, 30, null, null, universite.getId());

            assertEquals(1, maquette.elementsDe(ue.getId()).size());
            assertTrue(ec.getEnseignantId() == null);
        }

        @Test
        void unCoefficientNulEstRefuse() {
            Parcours p = licenceGestion();
            UniteEnseignement ue = maquette.ajouterUnite(p.getId(), null, "Compta", 6, 1, null, universite.getId());

            assertThrows(MaquetteRefusee.class,
                () -> maquette.ajouterElement(ue.getId(), "Sans poids", 0.0, 30, null, null, universite.getId()));
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Secretaire — consultation de la maquette")
    class Secretaire {

        @Test
        void lesUnitesDUnSemestreSontIsolables() {
            // Ce que la secretaire doit voir pour inscrire un etudiant a un semestre donne.
            Parcours p = licenceGestion();
            maquette.ajouterUnite(p.getId(), null, "S1 - Compta", 15, 1, null, universite.getId());
            maquette.ajouterUnite(p.getId(), null, "S1 - Droit", 15, 1, null, universite.getId());
            maquette.ajouterUnite(p.getId(), null, "S2 - Marketing", 30, 2, null, universite.getId());

            assertEquals(2, maquette.unitesDuSemestre(p.getId(), 1).size());
            assertEquals(1, maquette.unitesDuSemestre(p.getId(), 2).size());
            assertTrue(maquette.unitesDuSemestre(p.getId(), 3).isEmpty());
        }

        @Test
        void laMaquetteEstCloisonneeParEtablissement() {
            licenceGestion();

            Etablissement autre = new Etablissement();
            autre.setNom("Autre Universite");
            autre.setStatut("ACTIF");
            autre.setDateCreation(LocalDate.now());
            autre.setCodeAcces("AUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            autre = etablissementRepository.saveAndFlush(autre);

            assertTrue(maquette.parcoursDe(autre.getId()).isEmpty(),
                "un etablissement ne doit jamais voir la maquette d'un autre");
            assertEquals(1, maquette.parcoursDe(universite.getId()).size());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Maquette complete — prete pour une deliberation")
    class MaquetteComplete {

        @Test
        void uneMaquetteJusteEstDeclareeUtilisable() {
            Parcours p = maquette.creerParcours("Master Informatique", "MASTER", 2, universite.getId());

            for (int semestre = 1; semestre <= 2; semestre++) {
                UniteEnseignement a = maquette.ajouterUnite(p.getId(), "UE-S" + semestre + "-A",
                    "Algorithmique S" + semestre, 18, semestre, "FONDAMENTALE", universite.getId());
                UniteEnseignement b = maquette.ajouterUnite(p.getId(), "UE-S" + semestre + "-B",
                    "Anglais S" + semestre, 12, semestre, "TRANSVERSALE", universite.getId());
                maquette.ajouterElement(a.getId(), "Cours S" + semestre, 2.0, 40, null, null, universite.getId());
                maquette.ajouterElement(b.getId(), "Anglais S" + semestre, 1.0, 20, null, null, universite.getId());
            }

            assertTrue(maquette.controlerCredits(p, universite).stream().allMatch(EtatSemestre::estComplet));
            assertTrue(maquette.unitesSansElement(p.getId()).isEmpty());
            assertTrue(maquette.estUtilisable(p, universite),
                "credits justes et unites remplies : la deliberation peut s'appuyer dessus");
        }
    }
}
