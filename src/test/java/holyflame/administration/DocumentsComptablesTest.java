package holyflame.administration;

import holyflame.administration.model.CategorieComptable;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Depense;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Paiement;
import holyflame.administration.repository.CategorieComptableRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.DepenseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.service.DocumentsComptablesService;
import holyflame.administration.service.DocumentsComptablesService.Balance;
import holyflame.administration.service.DocumentsComptablesService.CompteDetaille;
import holyflame.administration.service.PlanComptableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grand livre et balance : les deux documents que reclame un expert-comptable.
 *
 * Ce qui compte ici, c'est que les chiffres soient justes et qu'ils correspondent aux memes
 * depenses et encaissements que l'ecran Finances. Un releve faux est pire qu'un releve absent :
 * il sera signe et transmis.
 */
@SpringBootTest
@Transactional
class DocumentsComptablesTest {

    @Autowired private DocumentsComptablesService documents;
    @Autowired private PlanComptableService planComptable;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private CategorieComptableRepository categorieRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;

    private Etablissement ecole;
    private Eleve eleve;
    private final LocalDate debut = LocalDate.of(2026, 1, 1);
    private final LocalDate fin = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void ouvrirUneEcole() {
        Etablissement e = new Etablissement();
        e.setNom("Ecole Test Grand Livre");
        e.setStatut("ACTIF");
        e.setDateCreation(LocalDate.now());
        e.setAnneeScolaire("2026-2027");
        e.setCodeAcces("GL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ecole = etablissementRepository.saveAndFlush(e);
        planComptable.seedSiVide(ecole.getId());

        Classe c = new Classe();
        c.setNom("CP1 A");
        c.setNiveau("CP1");
        c.setAnneeScolaire("2026-2027");
        c.setEtablissementId(ecole.getId());
        c = classeRepository.saveAndFlush(c);

        Eleve el = new Eleve();
        el.setMatricule("GL-001");
        el.setNom("NGARTA");
        el.setPrenom("Amina");
        el.setClasse(c);
        el.setEtablissementId(ecole.getId());
        eleve = eleveRepository.saveAndFlush(el);
    }

    private CategorieComptable poste(String code) {
        return categorieRepository.findByEtablissementIdAndActifTrueOrderByCodeAsc(ecole.getId()).stream()
            .filter(c -> code.equals(c.getCode())).findFirst().orElseThrow();
    }

    private void depenser(String code, String designation, double montant, LocalDate date) {
        Depense d = new Depense();
        d.setDesignation(designation);
        d.setCategorieComptable(poste(code));
        d.setMontant(montant);
        d.setDateDepense(date);
        d.setSens("CHARGE");
        d.setEtablissementId(ecole.getId());
        depenseRepository.saveAndFlush(d);
    }

    private void encaisser(double montant, String recu, LocalDate date) {
        Paiement p = new Paiement();
        p.setEleve(eleve);
        p.setMontantVerse(montant);
        p.setDatePaiement(date.atStartOfDay());
        p.setTypePaiement("SCOLARITE");
        p.setModePaiement("ESPECES");
        p.setRecuNumero(recu);
        // Le paiement n'a pas d'etablissement propre : il le tient de son eleve.
        p.setAnneeScolaire("2026-2027");
        paiementRepository.saveAndFlush(p);
    }

    private CompteDetaille compte(List<CompteDetaille> comptes, String code) {
        return comptes.stream().filter(c -> code.equals(c.code())).findFirst().orElseThrow();
    }

    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Une ecole neuve a un grand livre vide, pas un grand livre absent")
    void grandLivreVideSurEcoleNeuve() {
        assertTrue(documents.grandLivre(ecole.getId(), debut, fin, false).isEmpty(),
            "aucun mouvement, donc aucun compte mouvemente");

        List<CompteDetaille> avecVides = documents.grandLivre(ecole.getId(), debut, fin, true);
        assertEquals(56, avecVides.size(), "les 56 postes du plan SYSCOHADA restent consultables");
        assertTrue(avecVides.stream().noneMatch(CompteDetaille::estMouvemente));
    }

    @Test
    @DisplayName("Une depense apparait au debit de son poste")
    void uneDepenseVaAuDebit() {
        depenser("60470", "Ramettes de papier", 45_000, LocalDate.of(2026, 3, 12));

        CompteDetaille c = compte(documents.grandLivre(ecole.getId(), debut, fin, false), "60470");
        assertEquals(1, c.ecritures().size());
        assertEquals(45_000, c.totalDebit(), 0.01);
        assertEquals(0, c.totalCredit(), 0.01);
        assertEquals(45_000, c.solde(), 0.01);
        assertEquals("Ramettes de papier", c.ecritures().get(0).libelle());
    }

    @Test
    @DisplayName("Un encaissement de scolarite va au credit du compte 70110")
    void unEncaissementVaAuCreditDeLaScolarite() {
        encaisser(150_000, "REC-0001", LocalDate.of(2026, 2, 5));

        CompteDetaille c = compte(documents.grandLivre(ecole.getId(), debut, fin, false), "70110");
        assertEquals(1, c.ecritures().size());
        assertEquals(150_000, c.totalCredit(), 0.01);
        assertEquals(0, c.totalDebit(), 0.01);
        assertEquals("Recu REC-0001", c.ecritures().get(0).libelle(),
            "le numero de recu doit figurer au grand livre pour retrouver la piece");
        assertTrue(c.ecritures().get(0).tiers().contains("NGARTA"),
            "le nom de l'eleve identifie le tiers");
    }

    @Test
    @DisplayName("Le solde cumule suit l'ordre des dates")
    void leSoldeSeCumuleDansLOrdre() {
        depenser("60470", "Premier achat", 10_000, LocalDate.of(2026, 5, 10));
        depenser("60470", "Deuxieme achat", 15_000, LocalDate.of(2026, 3, 1));
        depenser("60470", "Troisieme achat", 5_000, LocalDate.of(2026, 7, 20));

        CompteDetaille c = compte(documents.grandLivre(ecole.getId(), debut, fin, false), "60470");
        assertEquals(3, c.ecritures().size());
        assertEquals("Deuxieme achat", c.ecritures().get(0).libelle(), "mars vient avant mai");
        assertEquals(15_000, c.ecritures().get(0).soldeCumule(), 0.01);
        assertEquals(25_000, c.ecritures().get(1).soldeCumule(), 0.01);
        assertEquals(30_000, c.ecritures().get(2).soldeCumule(), 0.01);
        assertEquals(30_000, c.solde(), 0.01);
    }

    @Test
    @DisplayName("La periode exclut ce qui tombe en dehors")
    void laPeriodeEstRespectee() {
        depenser("60470", "Dans la periode", 10_000, LocalDate.of(2026, 6, 15));
        depenser("60470", "Avant la periode", 99_000, LocalDate.of(2025, 12, 31));
        depenser("60470", "Apres la periode", 88_000, LocalDate.of(2027, 1, 1));

        CompteDetaille c = compte(documents.grandLivre(ecole.getId(), debut, fin, false), "60470");
        assertEquals(1, c.ecritures().size(), "une seule ecriture tombe dans l'annee");
        assertEquals(10_000, c.totalDebit(), 0.01);
    }

    @Test
    @DisplayName("La balance recapitule exactement le grand livre")
    void laBalanceCorrespondAuGrandLivre() {
        depenser("60470", "Fournitures", 45_000, LocalDate.of(2026, 3, 12));
        depenser("66110", "Salaires mars", 800_000, LocalDate.of(2026, 3, 31));
        encaisser(150_000, "REC-0001", LocalDate.of(2026, 2, 5));
        encaisser(120_000, "REC-0002", LocalDate.of(2026, 2, 8));

        Balance b = documents.balance(ecole.getId(), debut, fin);

        assertEquals(3, b.lignes().size(), "trois comptes mouvementes");
        assertEquals(845_000, b.totalDebit(), 0.01, "45 000 + 800 000 de charges");
        assertEquals(270_000, b.totalCredit(), 0.01, "150 000 + 120 000 encaisses");

        // Le total de la balance doit egaler la somme des comptes du grand livre :
        // deux chiffres differents entre les deux ecrans seraient un defaut, pas une nuance.
        double debitGrandLivre = documents.grandLivre(ecole.getId(), debut, fin, false).stream()
            .mapToDouble(CompteDetaille::totalDebit).sum();
        assertEquals(debitGrandLivre, b.totalDebit(), 0.01);
    }

    @Test
    @DisplayName("Le resultat de la periode est l'ecart entre credit et debit")
    void leResultatEstLEcart() {
        encaisser(500_000, "REC-0001", LocalDate.of(2026, 2, 5));
        depenser("66110", "Salaires", 300_000, LocalDate.of(2026, 2, 28));

        Balance b = documents.balance(ecole.getId(), debut, fin);
        assertEquals(200_000, b.resultat(), 0.01);
        assertTrue(b.estBeneficiaire());

        depenser("66110", "Salaires mars", 400_000, LocalDate.of(2026, 3, 31));
        Balance apres = documents.balance(ecole.getId(), debut, fin);
        assertEquals(-200_000, apres.resultat(), 0.01);
        assertFalse(apres.estBeneficiaire(), "les charges depassent les produits");
    }

    @Test
    @DisplayName("Une recette diverse va au credit, meme saisie comme depense")
    void uneRecetteDiverseVaAuCredit() {
        // Un don se saisit dans le meme ecran que les depenses, avec le sens PRODUIT.
        Depense don = new Depense();
        don.setDesignation("Don d'un partenaire");
        don.setCategorieComptable(poste("71824"));
        don.setMontant(250_000.0);
        don.setDateDepense(LocalDate.of(2026, 4, 2));
        don.setSens("PRODUIT");
        don.setEtablissementId(ecole.getId());
        depenseRepository.saveAndFlush(don);

        CompteDetaille c = compte(documents.grandLivre(ecole.getId(), debut, fin, false), "71824");
        assertEquals(250_000, c.totalCredit(), 0.01, "un don entre en caisse, il ne sort pas");
        assertEquals(0, c.totalDebit(), 0.01);
    }

    @Test
    @DisplayName("Le grand livre d'une ecole ne montre jamais les ecritures d'une autre")
    void cloisonnementParEtablissement() {
        depenser("60470", "Achat de cette ecole", 45_000, LocalDate.of(2026, 3, 12));

        Etablissement autre = new Etablissement();
        autre.setNom("Autre Ecole");
        autre.setStatut("ACTIF");
        autre.setDateCreation(LocalDate.now());
        autre.setCodeAcces("GL2-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        autre = etablissementRepository.saveAndFlush(autre);
        planComptable.seedSiVide(autre.getId());

        assertTrue(documents.grandLivre(autre.getId(), debut, fin, false).isEmpty(),
            "l'autre ecole ne voit rien de nos ecritures");
        assertEquals(0, documents.balance(autre.getId(), debut, fin).totalDebit(), 0.01);
    }
}
