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
 * L'hebergement a echoue plusieurs jours sur une seule cause : un {@code Procfile} dont
 * Railway avait recopie la commande dans les reglages du service. Cette commande lancait
 * {@code target/administration.jar} — le chemin ou Maven depose le jar pendant la
 * construction, pas celui ou le Dockerfile le place dans l'image finale. Le conteneur
 * redemarrait indefiniment sur « Unable to access jarfile ».
 *
 * Supprimer le Procfile n'a rien change : une fois recopiee dans le tableau de bord, la
 * commande y vit sa propre vie. C'est {@code railway.json} qui la reprend en main, parce
 * que la configuration versionnee l'emporte sur celle saisie a la main.
 *
 * Ce test garde les deux bouts : aucun Procfile ne revient, et railway.json continue de
 * designer le jar la ou le Dockerfile le met vraiment.
 */
@DisplayName("Demarrage chez Railway")
class DemarrageRailwayTest {

    private static final Path CONFIG = Paths.get("railway.json");
    private static final Path DOCKERFILE = Paths.get("Dockerfile");

    private String lire(Path fichier) throws IOException {
        return Files.readString(fichier, StandardCharsets.UTF_8);
    }

    @Test
    void aucunProcfileNeReprendLaMain() {
        assertFalse(Files.exists(Paths.get("Procfile")),
            "Railway prefere le Procfile au Dockerfile : en remettre un casse le demarrage");
    }

    @Test
    void laCommandeDeDemarrageDesigneLeJarTelQueLImageLeContient() throws IOException {
        String config = lire(CONFIG);
        String dockerfile = lire(DOCKERFILE);

        assertTrue(dockerfile.contains("WORKDIR /app") && dockerfile.contains("app.jar"),
            "le Dockerfile doit continuer de deposer le jar sous ce nom, a la racine du WORKDIR");
        assertTrue(config.contains("\"startCommand\": \"java -jar app.jar\""),
            "la commande versionnee doit lancer ce jar-la, depuis le WORKDIR /app");
        assertFalse(config.contains("target/administration.jar"),
            "ce chemin n'existe que pendant la construction, jamais dans l'image finale");
    }

    @Test
    void unePageReellementServieDecideDuSuccesDuDeploiement() throws IOException {
        String config = lire(CONFIG);

        // « Online » ne signifie que « le conteneur tourne » : un programme qui echoue et
        // redemarre chaque seconde coche cette case aussi bien qu'un programme qui fonctionne.
        // Le healthcheck exige une vraie reponse HTTP avant de declarer le deploiement reussi.
        assertTrue(config.contains("\"healthcheckPath\": \"/login\""),
            "sans page interrogee, un demarrage rate passerait pour un succes");
        assertTrue(config.contains("\"restartPolicyMaxRetries\""),
            "et un echec doit s'arreter au lieu de boucler aux frais de l'hebergement");
    }
}
