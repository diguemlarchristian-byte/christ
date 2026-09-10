package holyflame.administration;

import holyflame.administration.model.LigneSalaire;
import holyflame.administration.model.Personnel;
import holyflame.administration.model.SalaireMensuel;
import holyflame.administration.service.BulletinPaiePdfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le bulletin de paie s'imprimait depuis le navigateur, a partir des taux en vigueur ce
 * jour-la. Reimprime six mois plus tard, apres un changement de taux de CNPS ou d'IRPP, il ne
 * montrait plus ce qui avait ete remis : en cas de contestation, l'employeur et l'employe
 * n'avaient aucun document commun a comparer.
 *
 * Le PDF est donc fige au moment du paiement, avec un code qui permet de rapprocher un papier
 * de son archive. Ces tests portent sur ce qui fait la valeur de cette preuve : le PDF doit
 * etre reellement produit, et le code doit changer des que les montants changent.
 */
@SpringBootTest
@Transactional
class BulletinPaieArchiveTest {

    @Autowired private BulletinPaiePdfService service;

    private SalaireMensuel bulletin(double brut, double retenues, double net) {
        Personnel p = new Personnel();
        p.setId(1L);
        p.setNom("MAHAMAT"); p.setPrenom("Achta");
        p.setMatricule("P-014"); p.setFonction("ENSEIGNANT");
        p.setDateEmbauche(LocalDate.of(2022, 9, 1));

        SalaireMensuel s = new SalaireMensuel();
        s.setId(42L);
        s.setPersonnel(p);
        s.setMois(10); s.setAnnee(2026);
        s.setPeriodeDebut(LocalDate.of(2026, 10, 1));
        s.setPeriodeFin(LocalDate.of(2026, 10, 31));
        s.setTotalBrut(brut);
        s.setTotalRetenuesSalariales(retenues);
        s.setTotalChargesPatronales(0.0);
        s.setNetAPayer(net);
        s.setStatut("PAYE");
        s.setDatePaiement(LocalDate.of(2026, 10, 31));
        return s;
    }

    @Test
    void leCodeChangeDesQueLesMontantsChangent() {
        LocalDateTime instant = LocalDateTime.of(2026, 10, 31, 16, 5);

        String code = service.genererCode(bulletin(200_000, 20_000, 180_000), instant);
        String codeAutreNet = service.genererCode(bulletin(200_000, 20_000, 175_000), instant);

        assertNotEquals(code, codeAutreNet,
            "un bulletin refait avec d'autres chiffres ne doit pas pouvoir porter le meme code");
    }

    @Test
    void leMemeBulletinAuMemeInstantDonneLeMemeCode() {
        LocalDateTime instant = LocalDateTime.of(2026, 10, 31, 16, 5);

        assertEquals(service.genererCode(bulletin(200_000, 20_000, 180_000), instant),
                     service.genererCode(bulletin(200_000, 20_000, 180_000), instant),
            "le code est une empreinte, pas un tirage au sort : il doit etre reproductible");
    }

    @Test
    void leCodePorteLaPeriodeEnClair() {
        String code = service.genererCode(bulletin(200_000, 20_000, 180_000),
            LocalDateTime.of(2026, 10, 31, 16, 5));

        assertTrue(code.startsWith("PAIE-202610-"),
            "lu sur un papier, le code doit deja dire de quel mois il parle : " + code);
    }

    @Test
    void lArchiveEstUnVraiPdfQuiPorteLesFaitsDuBulletin() {
        // La transaction du test ne doit pas emporter de lignes fantomes : ce bulletin n'a pas
        // ete persiste, le service se contente de lire ses lignes (aucune ici) et ses montants.
        TestTransaction.flagForRollback();

        LocalDateTime instant = LocalDateTime.of(2026, 10, 31, 16, 5);
        SalaireMensuel s = bulletin(200_000, 20_000, 180_000);
        String code = service.genererCode(s, instant);

        byte[] pdf = service.genererPdf(s, code, instant);

        assertTrue(pdf.length > 1000, "un PDF de bulletin fait plus d'un kilo-octet");
        String entete = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", entete, "le fichier archive doit etre un PDF, pas du HTML");

        String contenu = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(contenu.contains("/Type /Page"), "le document doit avoir au moins une page");
    }

    @Test
    void unBulletinSansArchiveSeSignaleCommeTel() {
        SalaireMensuel s = bulletin(200_000, 20_000, 180_000);

        assertTrue(!s.isArchive(), "tant que rien n'est range, le bulletin n'a pas de preuve");

        s.setArchiveChemin("bulletins-paie/abc.pdf");
        assertTrue(s.isArchive(), "une fois le PDF range, la preuve existe");
    }
}
