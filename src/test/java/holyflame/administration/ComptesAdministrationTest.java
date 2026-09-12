package holyflame.administration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les comptes de demonstration portent des mots de passe ecrits dans le code. C'est commode
 * pour decouvrir le logiciel sur sa propre machine, et sans consequence tant qu'il n'est
 * joignable que de la.
 *
 * Le jour ou l'application recoit une adresse publique, cela change de nature : le depot est
 * lisible, donc « admin123 » l'est aussi. Les deux comptes qui donnent tout pouvoir — le
 * super-administrateur et l'administrateur de l'etablissement — ne doivent donc plus avoir de
 * mot de passe connu d'avance.
 *
 * Ils se reglent par variables d'environnement. A defaut, le mot de passe est tire au hasard
 * au premier demarrage et affiche une seule fois : personne ne peut deviner un compte qui
 * n'existait pas avant.
 */
@DisplayName("Comptes d'administration")
class ComptesAdministrationTest {

    private static final Path SOURCE = Paths.get("src", "main", "java", "holyflame",
        "administration", "config", "DataInitializer.java");

    private String source() throws IOException {
        return Files.readString(SOURCE, StandardCharsets.UTF_8);
    }

    @Test
    void aucunMotDePasseDAdministration_nEstEcritDansLeCode() throws IOException {
        String source = source();

        assertFalse(source.contains("\"super123\""),
            "le mot de passe du super-administrateur ne doit plus figurer dans le depot");
        assertFalse(source.contains("\"admin123\""),
            "celui de l'administrateur non plus");
    }

    @Test
    void lesDeuxComptesSensiblesPassentParLEnvironnement() throws IOException {
        String source = source();

        for (String variable : new String[]{
                "SUPER_ADMIN_EMAIL", "SUPER_ADMIN_PASSWORD", "ADMIN_EMAIL", "ADMIN_PASSWORD"}) {
            assertTrue(source.contains("\"" + variable + "\""),
                variable + " doit pouvoir etre defini a l'exterieur du code");
        }
    }

    @Test
    void aDefautLeMotDePasseEstTireAuHasardEtNonUnDefautConnu() throws IOException {
        String source = source();

        assertTrue(source.contains("SecureRandom"),
            "un mot de passe de repli doit etre imprevisible, pas une valeur ecrite ici");
        assertTrue(source.contains("il ne sera plus jamais affiche"),
            "et il doit etre annonce au demarrage, sans quoi personne ne pourrait se connecter");
    }

    @Test
    void lesComptesDeDemonstrationOrdinairesRestentInchanges() throws IOException {
        String source = source();

        // Ces comptes ne donnent acces qu'a leur propre metier, dans un etablissement de
        // demonstration. Les compliquer rendrait la decouverte du logiciel penible sans rien
        // proteger de reel — ce sont les deux comptes d'administration qui comptent.
        assertTrue(source.contains("secretaire@holyflame.com"),
            "les comptes de demonstration metier restent, pour pouvoir essayer le logiciel");
        assertTrue(source.contains("comptable@holyflame.com"));
    }
}
