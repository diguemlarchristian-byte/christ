package holyflame.administration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le profil local fait tourner une ecole sur sa propre machine, sans MySQL.
 *
 * Tout y tient a un detail de la chaine de connexion : H2 sait fonctionner en memoire, et
 * c'est ce mode que montrent la plupart des exemples. Une ecole demarree ainsi perdrait ses
 * inscriptions, ses paiements et ses notes a chaque arret du serveur — sans message, sans
 * erreur, sans que personne ne s'en apercoive avant le lendemain matin.
 *
 * La difference tient a trois lettres, « mem » au lieu de « file ». Ce test les surveille.
 */
@DisplayName("Profil local sur base H2")
class ProfilLocalH2Test {

    private static final Path PROFIL =
        Paths.get("src", "main", "resources", "application-local.properties");

    private Properties reglages() throws IOException {
        Properties p = new Properties();
        try (var flux = Files.newBufferedReader(PROFIL, StandardCharsets.UTF_8)) {
            p.load(flux);
        }
        return p;
    }

    @Test
    void laBaseEstUnFichierEtJamaisLaMemoire() throws IOException {
        String url = reglages().getProperty("spring.datasource.url");

        assertTrue(url != null && !url.isBlank(), "le profil local doit declarer sa base");
        assertFalse(url.contains("jdbc:h2:mem"),
            "base en memoire : l'ecole perdrait toutes ses donnees a chaque arret du serveur");
        assertTrue(url.startsWith("jdbc:h2:file:"),
            "la base doit etre un fichier, que l'on sauvegarde en le copiant : " + url);
    }

    @Test
    void leServeurEcouteLesAutresPostesDeLEcole() throws IOException {
        String adresse = reglages().getProperty("server.address");

        assertTrue("0.0.0.0".equals(adresse),
            "sans cela le logiciel ne repondrait qu'a la machine serveur elle-meme, et aucun "
            + "autre poste de l'ecole ne pourrait s'y connecter");
    }

    @Test
    void laConsoleDeBaseResteFermee() throws IOException {
        String console = reglages().getProperty("spring.h2.console.enabled");

        assertTrue("false".equals(console),
            "la console H2 donne un acces direct a toutes les donnees : elle ne s'ouvre que "
            + "le temps d'un depannage, jamais par defaut");
    }

    @Test
    void lesFichiersTeleversesVoisinentAvecLaBase() throws IOException {
        Properties p = reglages();
        String base = p.getProperty("spring.datasource.url");
        String fichiers = p.getProperty("app.upload.dir");

        assertTrue(fichiers != null && fichiers.startsWith("./donnees"),
            "justificatifs et bulletins archives doivent vivre a cote de la base, pour qu'une "
            + "seule sauvegarde emporte tout");
        assertTrue(base.contains("./donnees/"),
            "la base vit dans le meme dossier que les fichiers : " + base);
    }

    @Test
    void leProfilParDefautResteSurMySql() throws IOException {
        Properties defaut = new Properties();
        try (var flux = Files.newBufferedReader(
                Paths.get("src", "main", "resources", "application.properties"),
                StandardCharsets.UTF_8)) {
            defaut.load(flux);
        }

        String url = defaut.getProperty("spring.datasource.url");
        assertTrue(url != null && url.contains("mysql"),
            "ajouter H2 ne doit pas deplacer les installations existantes : sans profil actif, "
            + "le logiciel continue de parler a MySQL");
    }
}
