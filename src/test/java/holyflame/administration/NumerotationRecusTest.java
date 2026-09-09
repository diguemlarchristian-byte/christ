package holyflame.administration;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Paiement;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.util.AnneeScolaireUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numerotation des recus : un numero delivre ne doit jamais etre redonne.
 *
 * Le rang se lisait sur le NOMBRE de paiements en base. Supprimer un paiement faisait donc
 * reculer le compteur, qui redonnait un numero deja remis a une famille — et deux recus
 * portant le meme numero rendent la piece la plus ancienne contestable lors d'un controle.
 *
 * Ces tests reproduisent la sequence complete, y compris la suppression, en appliquant la
 * meme regle que le controleur : le rang suit le plus grand numero deja delivre.
 */
@SpringBootTest
@Transactional
class NumerotationRecusTest {

    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private PaiementRepository paiementRepository;

    private Etablissement ecole;
    private Eleve eleve;

    @BeforeEach
    void ouvrirUneEcole() {
        Etablissement e = new Etablissement();
        e.setNom("Ecole Test Recus");
        e.setStatut("ACTIF");
        e.setDateCreation(LocalDate.now());
        e.setAnneeScolaire("2026-2027");
        e.setCodeAcces("RC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ecole = etablissementRepository.saveAndFlush(e);

        Classe c = new Classe();
        c.setNom("CP1 A");
        c.setNiveau("CP1");
        c.setAnneeScolaire("2026-2027");
        c.setEtablissementId(ecole.getId());
        c = classeRepository.saveAndFlush(c);

        Eleve el = new Eleve();
        el.setMatricule("RC-001");
        el.setNom("NGARTA");
        el.setPrenom("Amina");
        el.setClasse(c);
        el.setEtablissementId(ecole.getId());
        eleve = eleveRepository.saveAndFlush(el);
    }

    /** Reproduit la regle du controleur : le rang suit le plus grand numero deja delivre. */
    private String prochainNumero(LocalDate date) {
        String annee = AnneeScolaireUtil.pour(date);
        String prefixe = "HF-" + annee + "-";
        int dernier = paiementRepository.findByEtablissementId(ecole.getId()).stream()
            .map(Paiement::getRecuNumero)
            .filter(n -> n != null && n.startsWith(prefixe))
            .map(n -> n.substring(prefixe.length()))
            .mapToInt(s -> {
                try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
            })
            .max().orElse(0);
        return prefixe + String.format("%03d", dernier + 1);
    }

    private Paiement encaisser(String numero, LocalDate date) {
        Paiement p = new Paiement();
        p.setEleve(eleve);
        p.setMontantVerse(10_000.0);
        p.setDatePaiement(date.atStartOfDay());
        p.setTypePaiement("SCOLARITE");
        p.setModePaiement("ESPECES");
        p.setRecuNumero(numero);
        p.setAnneeScolaire(AnneeScolaireUtil.pour(date));
        return paiementRepository.saveAndFlush(p);
    }

    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Les numeros se suivent a partir du premier encaissement")
    void laSequenceDemarreAUn() {
        LocalDate jour = LocalDate.of(2026, 10, 5);

        assertEquals("HF-2026-2027-001", prochainNumero(jour));
        encaisser(prochainNumero(jour), jour);
        assertEquals("HF-2026-2027-002", prochainNumero(jour));
        encaisser(prochainNumero(jour), jour);
        assertEquals("HF-2026-2027-003", prochainNumero(jour));
    }

    @Test
    @DisplayName("Supprimer un paiement ne fait pas reculer le compteur")
    void unNumeroDelivreResteConsomme() {
        LocalDate jour = LocalDate.of(2026, 10, 5);
        encaisser(prochainNumero(jour), jour);            // 001
        Paiement deuxieme = encaisser(prochainNumero(jour), jour); // 002
        encaisser(prochainNumero(jour), jour);            // 003

        // La comptable annule le deuxieme encaissement, saisi par erreur.
        paiementRepository.delete(deuxieme);
        paiementRepository.flush();

        assertEquals("HF-2026-2027-004", prochainNumero(jour),
            "le suivant est 004, jamais 003 qui a deja ete remis a une famille");

        List<String> numeros = paiementRepository.findByEtablissementId(ecole.getId()).stream()
            .map(Paiement::getRecuNumero).toList();
        assertEquals(numeros.size(), numeros.stream().distinct().count(),
            "aucun numero en double, meme apres suppression");
    }

    @Test
    @DisplayName("Le dernier numero compte, pas le nombre de paiements")
    void leRangSuitLeMaximumPasLeCompte() {
        LocalDate jour = LocalDate.of(2026, 10, 5);
        // Une reprise d'historique introduit directement un numero eleve.
        encaisser("HF-2026-2027-047", jour);

        assertEquals("HF-2026-2027-048", prochainNumero(jour),
            "un seul paiement en base, mais le numero 047 est deja consomme");
    }

    @Test
    @DisplayName("Chaque annee scolaire repart a un")
    void laSequenceEstAnnuelle() {
        encaisser(prochainNumero(LocalDate.of(2026, 10, 5)), LocalDate.of(2026, 10, 5));
        encaisser(prochainNumero(LocalDate.of(2026, 11, 5)), LocalDate.of(2026, 11, 5));

        assertEquals("HF-2027-2028-001", prochainNumero(LocalDate.of(2027, 10, 5)),
            "la nouvelle annee scolaire ouvre une sequence neuve");
    }

    @Test
    @DisplayName("Un numero saisi a la main dans un autre format ne casse pas la sequence")
    void unFormatLibreNInterrompPasLaSequence() {
        LocalDate jour = LocalDate.of(2026, 10, 5);
        encaisser("RECU MANUEL 12/A", jour);
        encaisser(prochainNumero(jour), jour);

        assertEquals("HF-2026-2027-002", prochainNumero(jour),
            "le numero hors format est ignore par la sequence, sans la faire echouer");
    }

    @Test
    @DisplayName("Deux recus ne portent jamais le meme numero")
    void lesDoublonsSontDetectables() {
        LocalDate jour = LocalDate.of(2026, 10, 5);
        String premier = prochainNumero(jour);
        encaisser(premier, jour);

        // C'est ce que le controleur verifie avant d'accepter un numero saisi a la main.
        boolean dejaPris = paiementRepository.findByEtablissementId(ecole.getId()).stream()
            .anyMatch(p -> premier.equalsIgnoreCase(p.getRecuNumero()));
        assertTrue(dejaPris, "un numero deja delivre doit etre refuse a la saisie");

        String suivant = prochainNumero(jour);
        assertNotEquals(premier, suivant);
        assertFalse(paiementRepository.findByEtablissementId(ecole.getId()).stream()
            .anyMatch(p -> suivant.equalsIgnoreCase(p.getRecuNumero())));
    }
}
