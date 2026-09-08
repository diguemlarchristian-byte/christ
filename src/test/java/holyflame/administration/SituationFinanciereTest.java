package holyflame.administration;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Echeance;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.RemiseEleve;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EcheanceRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.RemiseEleveRepository;
import holyflame.administration.model.Etablissement;
import holyflame.administration.service.SituationFinanciereService;
import holyflame.administration.service.SituationFinanciereService.Situation;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Remises et echeanciers : ce que doit reellement une famille.
 *
 * Ces calculs decident qui apparait en impaye et qui recoit un rappel. Une erreur ici se
 * traduit par une relance envoyee a un boursier exonere, ou par un eleve en retard qu'on ne
 * relance jamais — deux fautes visibles de l'exterieur.
 */
@SpringBootTest
@Transactional
class SituationFinanciereTest {

    @Autowired private SituationFinanciereService service;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private FraisScolariteRepository fraisRepository;
    @Autowired private RemiseEleveRepository remiseRepository;
    @Autowired private EcheanceRepository echeanceRepository;
    @Autowired private PaiementRepository paiementRepository;

    private Etablissement ecole;
    private Eleve eleve;
    private final String annee = "2026-2027";

    @BeforeEach
    void ouvrirUneEcole() {
        Etablissement e = new Etablissement();
        e.setNom("Ecole Test Remises");
        e.setStatut("ACTIF");
        e.setDateCreation(LocalDate.now());
        e.setAnneeScolaire(annee);
        e.setCodeAcces("RM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ecole = etablissementRepository.saveAndFlush(e);

        Classe c = new Classe();
        c.setNom("CP1 A");
        c.setNiveau("CP1");
        c.setAnneeScolaire(annee);
        c.setEtablissementId(ecole.getId());
        c = classeRepository.saveAndFlush(c);

        Eleve el = new Eleve();
        el.setMatricule("RM-001");
        el.setNom("NGARTA");
        el.setPrenom("Amina");
        el.setClasse(c);
        el.setEtablissementId(ecole.getId());
        eleve = eleveRepository.saveAndFlush(el);
    }

    private FraisScolarite frais(String designation, double montant, String niveau, boolean obligatoire) {
        FraisScolarite f = new FraisScolarite();
        f.setDesignation(designation);
        f.setTypeFrais("SCOLARITE");
        f.setMontant(montant);
        f.setEcheance("ANNUEL");
        f.setNiveauCible(niveau);
        f.setObligatoire(obligatoire);
        f.setEtablissementId(ecole.getId());
        return fraisRepository.saveAndFlush(f);
    }

    private RemiseEleve remise(String type, double valeur, Long fraisId, String motif) {
        RemiseEleve r = new RemiseEleve();
        r.setEleveId(eleve.getId());
        r.setType(type);
        r.setValeur(valeur);
        r.setFraisScolariteId(fraisId);
        r.setMotif(motif);
        r.setAnneeScolaire(annee);
        r.setDateAccord(LocalDate.now());
        r.setEtablissementId(ecole.getId());
        return remiseRepository.saveAndFlush(r);
    }

    private void encaisser(double montant) {
        Paiement p = new Paiement();
        p.setEleve(eleve);
        p.setMontantVerse(montant);
        p.setDatePaiement(LocalDate.now().atStartOfDay());
        p.setTypePaiement("SCOLARITE");
        p.setModePaiement("ESPECES");
        p.setAnneeScolaire(annee);
        paiementRepository.saveAndFlush(p);
    }

    private Situation situation() {
        return service.situation(eleve, annee, service.fraisObligatoires(ecole.getId()));
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Ce que doit un eleve")
    class MontantDu {

        @Test
        void sansRemiseIlDoitTousLesFraisObligatoiresDeSonNiveau() {
            frais("Scolarite CP1", 150_000, "CP1", true);
            frais("Inscription", 25_000, null, true);
            frais("Cantine", 60_000, "CP1", false);       // facultatif
            frais("Scolarite CM2", 180_000, "CM2", true); // autre niveau

            Situation s = situation();
            assertEquals(175_000, s.montantBrut(), 0.01,
                "seuls les frais obligatoires de son niveau, plus ceux de toute l'ecole");
            assertEquals(175_000, s.montantDu(), 0.01);
            assertEquals(175_000, s.reste(), 0.01);
        }

        @Test
        void unePartDejaVerseeReduitLeReste() {
            frais("Scolarite CP1", 150_000, "CP1", true);
            encaisser(50_000);

            Situation s = situation();
            assertEquals(50_000, s.verse(), 0.01);
            assertEquals(100_000, s.reste(), 0.01);
            assertFalse(s.estSolde());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Remises")
    class Remises {

        @Test
        void unPourcentageSAppliqueSurLeTotal() {
            frais("Scolarite CP1", 150_000, "CP1", true);
            frais("Inscription", 50_000, null, true);
            remise("POURCENTAGE", 50, null, "FRATRIE");

            Situation s = situation();
            assertEquals(200_000, s.montantBrut(), 0.01);
            assertEquals(100_000, s.remises(), 0.01);
            assertEquals(100_000, s.montantDu(), 0.01);
        }

        @Test
        void unMontantFixeSeDeduitTelQuel() {
            frais("Scolarite CP1", 150_000, "CP1", true);
            remise("MONTANT", 40_000, null, "SOCIALE");

            assertEquals(110_000, situation().montantDu(), 0.01);
        }

        @Test
        void uneRemiseCibleeNeTouchePasLesAutresFrais() {
            FraisScolarite scolarite = frais("Scolarite CP1", 150_000, "CP1", true);
            frais("Inscription", 50_000, null, true);
            remise("POURCENTAGE", 100, scolarite.getId(), "PERSONNEL");

            Situation s = situation();
            assertEquals(150_000, s.remises(), 0.01, "seule la scolarite est exoneree");
            assertEquals(50_000, s.montantDu(), 0.01, "l'inscription reste due");
        }

        @Test
        void uneRemiseTotaleRendLEleveExonere() {
            frais("Scolarite CP1", 150_000, "CP1", true);
            remise("POURCENTAGE", 100, null, "BOURSE");

            Situation s = situation();
            assertEquals(0, s.montantDu(), 0.01);
            assertTrue(s.estSolde(), "un boursier ne doit pas figurer parmi les impayes");
            assertTrue(s.estExonere(), "et il est exonere, pas simplement a jour");
        }

        @Test
        void uneRemiseNePeutPasDepasserCeQuElleReduit() {
            // Une remise de 200 000 sur 150 000 dus ne rend pas 50 000 a la famille.
            frais("Scolarite CP1", 150_000, "CP1", true);
            remise("MONTANT", 200_000, null, "AUTRE");

            Situation s = situation();
            assertEquals(150_000, s.remises(), 0.01);
            assertEquals(0, s.montantDu(), 0.01);
            assertEquals(0, s.reste(), 0.01, "jamais de du negatif");
        }

        @Test
        void plusieursRemisesSAdditionnentSansDepasserLeTotal() {
            frais("Scolarite CP1", 100_000, "CP1", true);
            remise("POURCENTAGE", 60, null, "BOURSE");
            remise("POURCENTAGE", 70, null, "FRATRIE");

            Situation s = situation();
            assertEquals(100_000, s.remises(), 0.01, "130 % cumules restent plafonnes a 100 %");
            assertEquals(0, s.montantDu(), 0.01);
        }

        @Test
        void uneRemiseDuneAutreAnneeNeSAppliquePas() {
            frais("Scolarite CP1", 150_000, "CP1", true);
            RemiseEleve ancienne = remise("POURCENTAGE", 100, null, "BOURSE");
            ancienne.setAnneeScolaire("2025-2026");
            remiseRepository.saveAndFlush(ancienne);

            assertEquals(150_000, situation().montantDu(), 0.01,
                "une bourse ne se reconduit pas toute seule d'une annee sur l'autre");
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Echeanciers")
    class Echeanciers {

        @Test
        void troisVersementsSeRepartissentSansPerdreUnFranc() {
            // 100 000 en trois : le dernier versement absorbe l'arrondi, sinon il resterait
            // un franc du a la fin de l'annee et l'eleve ne serait jamais solde.
            List<Echeance> plan = service.planifier(eleve, annee, 3,
                LocalDate.of(2026, 10, 1), 100_000, ecole.getId());

            assertEquals(3, plan.size());
            double somme = plan.stream().mapToDouble(Echeance::getMontantPrevu).sum();
            assertEquals(100_000, somme, 0.001, "la somme des versements retombe juste");
            assertEquals(33_333.33, plan.get(0).getMontantPrevu(), 0.01);
            assertEquals(33_333.34, plan.get(2).getMontantPrevu(), 0.01);
        }

        @Test
        void lesVersementsSontMensuelsAPartirDeLaDateChoisie() {
            List<Echeance> plan = service.planifier(eleve, annee, 3,
                LocalDate.of(2026, 10, 15), 90_000, ecole.getId());

            assertEquals(LocalDate.of(2026, 10, 15), plan.get(0).getDatePrevue());
            assertEquals(LocalDate.of(2026, 11, 15), plan.get(1).getDatePrevue());
            assertEquals(LocalDate.of(2026, 12, 15), plan.get(2).getDatePrevue());
        }

        @Test
        void unVersementSoldeLesEcheancesLesPlusAnciennes() {
            service.planifier(eleve, annee, 3, LocalDate.of(2026, 10, 1), 90_000, ecole.getId());
            service.imputer(eleve.getId(), annee, 45_000, LocalDate.of(2026, 10, 3));

            List<Echeance> apres = echeanceRepository.findByEleveIdAndAnneeScolaireOrderByRangAsc(eleve.getId(), annee);
            assertTrue(apres.get(0).estSoldee(), "le premier versement est couvert");
            assertEquals(15_000, apres.get(1).getMontantRegle(), 0.01, "le reliquat entame le deuxieme");
            assertEquals(0, apres.get(2).getMontantRegle(), 0.01, "le troisieme reste intact");
        }

        @Test
        void uneEcheanceDepasseeEtNonSoldeeEstEnRetard() {
            service.planifier(eleve, annee, 2, LocalDate.of(2026, 1, 10), 60_000, ecole.getId());

            List<Echeance> retards = service.echeancesEnRetard(ecole.getId(), annee, LocalDate.of(2026, 3, 1));
            assertEquals(2, retards.size(), "janvier et fevrier sont passes sans reglement");

            service.imputer(eleve.getId(), annee, 30_000, LocalDate.of(2026, 3, 1));
            assertEquals(1, service.echeancesEnRetard(ecole.getId(), annee, LocalDate.of(2026, 3, 1)).size());
        }

        @Test
        void unVersementDavanceNestPasEnRetard() {
            service.planifier(eleve, annee, 3, LocalDate.of(2026, 10, 1), 90_000, ecole.getId());
            assertTrue(service.echeancesEnRetard(ecole.getId(), annee, LocalDate.of(2026, 9, 1)).isEmpty(),
                "rien n'est du avant la premiere date convenue");
        }

        @Test
        void replanifierRemplaceLAncienEcheancier() {
            service.planifier(eleve, annee, 3, LocalDate.of(2026, 10, 1), 90_000, ecole.getId());
            service.planifier(eleve, annee, 2, LocalDate.of(2026, 11, 1), 90_000, ecole.getId());

            List<Echeance> plan = echeanceRepository.findByEleveIdAndAnneeScolaireOrderByRangAsc(eleve.getId(), annee);
            assertEquals(2, plan.size(), "l'ancien echeancier ne doit pas cohabiter avec le nouveau");
            assertEquals(45_000, plan.get(0).getMontantPrevu(), 0.01);
        }

        @Test
        void lEcheancierEtaleCeQuiResteApresRemise() {
            frais("Scolarite CP1", 200_000, "CP1", true);
            remise("POURCENTAGE", 50, null, "FRATRIE");

            Situation s = situation();
            assertEquals(100_000, s.reste(), 0.01);

            List<Echeance> plan = service.planifier(eleve, annee, 2,
                LocalDate.of(2026, 10, 1), s.reste(), ecole.getId());
            assertEquals(50_000, plan.get(0).getMontantPrevu(), 0.01,
                "on n'etale que ce que la famille doit reellement");
        }
    }
}
