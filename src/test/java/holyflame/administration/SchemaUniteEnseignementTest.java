package holyflame.administration;

import holyflame.administration.service.MaquettePedagogiqueService;
import holyflame.administration.service.MaquettePedagogiqueService.MaquetteRefusee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garde contre le retour du conflit entre les deux modules universitaires.
 *
 * Deux modeles coexistent sur la meme base holyflame_db : celui de
 * « Copy of administration - Copie » (Faculte > Departement > Filiere > UE, table
 * unites_enseignement) et celui-ci (Parcours > UE > ECUE, table parcours_unites).
 *
 * Ils ont partage une seule table pendant un temps, et chacun exigeait alors la cle etrangere
 * de l'autre : plus aucune unite ne pouvait etre creee, ni d'un cote ni de l'autre, avec pour
 * seul symptome « Field ... doesn't have a default value ». Ces tests verifient que la
 * separation tient.
 */
@SpringBootTest
class SchemaUniteEnseignementTest {

    @Autowired private DataSource dataSource;
    @Autowired private MaquettePedagogiqueService maquette;

    @Test
    @DisplayName("Les deux modeles ecrivent chacun dans sa table")
    void lesTablesRestentSeparees() throws Exception {
        assertEquals("ABSENTE", nullable("parcours_unites", "filiere_id"),
            "la table de ce module ne doit pas porter la cle etrangere de l'autre modele");
        assertEquals("NO", nullable("parcours_unites", "parcours_id"),
            "ici, le rattachement au parcours est bien obligatoire");

        // La table de l'autre module doit rester intacte : ce projet n'a pas a la modifier.
        String filiereChezEux = nullable("unites_enseignement", "filiere_id");
        if (!"ABSENTE".equals(filiereChezEux)) {
            assertEquals("NO", filiereChezEux,
                "la contrainte du module existant ne doit pas avoir ete relachee");
        }
    }

    @Test
    @DisplayName("Une unite sans parcours est refusee avant meme d'atteindre la base")
    void leServiceRefuseUneUniteSansParcours() {
        var refus = assertThrows(MaquetteRefusee.class, () -> maquette.ajouterUnite(
            null, "UE-X", "Unite orpheline", 6, 1, "FONDAMENTALE", 1L));
        assertTrue(refus.getMessage().toLowerCase().contains("parcours"));
    }

    @Test
    @DisplayName("Un parcours inexistant est refuse aussi")
    void unParcoursInconnuEstRefuse() {
        assertThrows(MaquetteRefusee.class, () -> maquette.ajouterUnite(
            999_999_999L, "UE-Y", "Unite egaree", 6, 1, "FONDAMENTALE", 1L));
    }

    private String nullable(String table, String colonne) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "' " +
                "AND COLUMN_NAME = '" + colonne + "'");
            return rs.next() ? rs.getString(1) : "ABSENTE";
        }
    }
}
