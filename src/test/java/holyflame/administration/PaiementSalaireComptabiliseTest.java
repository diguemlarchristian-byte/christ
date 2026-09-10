package holyflame.administration;

import holyflame.administration.model.Depense;
import holyflame.administration.model.SalaireMensuel;
import holyflame.administration.repository.DepenseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Payer un salaire ecrit dans les comptes. Deux clics sur « Payer » — une double soumission,
 * un retour arriere, une connexion lente — passaient tous deux le controle de statut et
 * comptabilisaient le salaire deux fois. Le doublon etait alors indiscernable d'une seconde
 * depense legitime : rien, dans une depense, ne disait de quel bulletin elle venait.
 *
 * Ce test porte sur ce qui rend la double ecriture detectable, donc evitable : le lien entre
 * une depense et le bulletin qui l'a produite.
 */
@SpringBootTest
@Transactional
@DisplayName("Comptabilisation d'un salaire paye")
class PaiementSalaireComptabiliseTest {

    @Autowired private DepenseRepository depenseRepository;

    private Depense depenseDeSalaire(Long salaireId, double montant) {
        Depense d = new Depense();
        d.setDesignation("Salaire 10/2026 - Achta MAHAMAT");
        d.setSens("CHARGE");
        d.setBeneficiaire("Achta MAHAMAT");
        d.setMontant(montant);
        d.setDateDepense(LocalDate.of(2026, 10, 31));
        d.setStatut("PAYE");
        d.setAnneeScolaire("2026-2027");
        d.setEtablissementId(9_999L);
        d.setSalaireMensuelId(salaireId);
        return d;
    }

    @Test
    void uneDepenseDeSalaireSaitDeQuelBulletinElleVient() {
        Depense d = depenseRepository.save(depenseDeSalaire(4242L, 200_000));

        assertEquals(4242L, d.getSalaireMensuelId(),
            "sans ce lien, un doublon serait indiscernable d'une depense legitime");
        assertTrue(depenseRepository.existsBySalaireMensuelId(4242L));
    }

    @Test
    void unBulletinNonComptabiliseNeRemonteAucuneDepense() {
        assertFalse(depenseRepository.existsBySalaireMensuelId(777_001L),
            "c'est ce test qui autorise la premiere ecriture");
    }

    @Test
    void leSecondClicTrouveLaTraceDuPremier() {
        // Ce qu'observe le second appel de payerSalaire : la depense du premier existe deja,
        // donc il refuse d'ecrire au lieu de doubler la masse salariale.
        assertFalse(depenseRepository.existsBySalaireMensuelId(4343L));

        depenseRepository.save(depenseDeSalaire(4343L, 200_000));

        assertTrue(depenseRepository.existsBySalaireMensuelId(4343L),
            "le doublon est desormais detectable avant d'etre ecrit");
        assertEquals(1, depenseRepository.findBySalaireMensuelId(4343L).size());
    }

    @Test
    void lesDeuxEcrituresDUnMemePaiementRestentRattachees() {
        // Un paiement produit deux depenses : le brut et les charges patronales. Toutes deux
        // portent le meme bulletin, sinon la seconde passerait pour une depense orpheline.
        depenseRepository.save(depenseDeSalaire(4444L, 200_000));
        Depense charges = depenseDeSalaire(4444L, 30_000);
        charges.setDesignation("Charges patronales sur salaire 10/2026 - Achta MAHAMAT");
        depenseRepository.save(charges);

        assertEquals(2, depenseRepository.findBySalaireMensuelId(4444L).size(),
            "le brut et les charges appartiennent au meme paiement");
    }

    @Test
    void unBulletinNeufNaPasEncoreDeCodeNiDArchive() {
        SalaireMensuel s = new SalaireMensuel();
        s.setMois(10); s.setAnnee(2026);

        assertFalse(s.isArchive());
        assertEquals(null, s.getCodeVerification(),
            "l'archive et son code n'apparaissent qu'au paiement");
    }
}
