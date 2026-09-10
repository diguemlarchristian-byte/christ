package holyflame.administration;

import holyflame.administration.model.SalaireMensuel;
import holyflame.administration.service.CloturePaieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un bulletin paye etait deja fige. Ce qui manquait, c'est le mois : rien n'empechait
 * d'etablir en novembre un bulletin « oublie » pour aout, que personne ne serait revenu
 * verifier — la liste du personnel sans bulletin ne montre que le mois affiche.
 *
 * La condition de cloture est le point delicat : cloturer un mois ou il reste un bulletin en
 * attente enfermerait quelqu'un dehors, son salaire ne pouvant plus etre paye sans rouvrir.
 */
@DisplayName("Cloture de la paie du mois")
class CloturePaieServiceTest {

    private final CloturePaieService service = new CloturePaieService();

    private SalaireMensuel bulletin(String statut, double net) {
        SalaireMensuel s = new SalaireMensuel();
        s.setStatut(statut);
        s.setNetAPayer(net);
        return s;
    }

    @Test
    void unMoisEntierementPayeSeCloture() {
        List<SalaireMensuel> mois = List.of(
            bulletin("PAYE", 150_000), bulletin("PAYE", 120_000), bulletin("PAYE", 95_000));

        assertTrue(service.peutCloturer(mois, 0));
        assertNull(service.obstacleACloture(mois, 0), "rien ne s'y oppose");
    }

    @Test
    void unBulletinEnAttenteEmpecheLaCloture() {
        List<SalaireMensuel> mois = List.of(
            bulletin("PAYE", 150_000), bulletin("EN_ATTENTE", 120_000));

        assertFalse(service.peutCloturer(mois, 0),
            "cloturer ici rendrait le second salaire impayable sans rouvrir le mois");
        assertEquals("1 bulletin(s) ne sont pas encore payes.", service.obstacleACloture(mois, 0));
    }

    @Test
    void unMembreDuPersonnelSansBulletinEmpecheLaCloture() {
        List<SalaireMensuel> mois = List.of(bulletin("PAYE", 150_000));

        assertFalse(service.peutCloturer(mois, 2),
            "un mois n'est pas complet tant que quelqu'un n'a pas ete paye");
        assertTrue(service.obstacleACloture(mois, 2).contains("2 membre(s)"),
            "le message dit combien il en manque");
    }

    @Test
    void unMoisSansAucunBulletinNeSeCloturePas() {
        assertFalse(service.peutCloturer(List.of(), 0),
            "cloturer un mois vide le declarerait solde alors que rien n'a ete fait");
        assertTrue(service.obstacleACloture(List.of(), 0).contains("Aucun bulletin"));
    }

    @Test
    void lObstacleLePlusBloquantEstAnnonceEnPremier() {
        // Personnel sans bulletin d'abord : c'est ce qu'il faut regler avant de pouvoir payer.
        List<SalaireMensuel> mois = List.of(bulletin("EN_ATTENTE", 100_000));

        assertTrue(service.obstacleACloture(mois, 1).contains("membre(s)"),
            "inutile de parler des bulletins impayes tant qu'il en manque");
    }
}
