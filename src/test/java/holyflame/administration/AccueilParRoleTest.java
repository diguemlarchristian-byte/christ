package holyflame.administration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chaque role doit arriver dans son propre espace en se connectant. MARKETING etait le seul
 * a n'avoir aucune destination declaree : il tombait sur le cas par defaut et atterrissait au
 * tableau de bord de l'administration — ou figurent le total encaisse et le budget — alors que
 * son espace, le site vitrine, existe depuis toujours avec ses sept ecrans.
 *
 * L'oubli ne se voyait pas : le cas par defaut renvoie une page valide, personne ne recoit
 * d'erreur, et il faut connaitre l'existence de /marketing pour s'apercevoir qu'on n'y va pas.
 */
@DisplayName("Accueil de chaque role apres connexion")
class AccueilParRoleTest {

    private static final Path HANDLER = Paths.get("src", "main", "java", "holyflame",
        "administration", "config", "CustomAuthenticationSuccessHandler.java");
    private static final Path SECURITE = Paths.get("src", "main", "java", "holyflame",
        "administration", "config", "SecurityConfig.java");
    private static final Path TEMPLATES = Paths.get("src", "main", "resources", "templates");

    /** Roles qui ne se connectent pas a l'application, ou dont l'accueil depend d'un reglage. */
    private static final List<String> SANS_ACCUEIL_PROPRE = List.of("ADMIN");

    @Test
    void chaqueRoleConnuArriveDansSonEspace() throws IOException {
        String source = Files.readString(HANDLER, StandardCharsets.UTF_8);
        List<String> manquants = new ArrayList<>();

        for (String role : rolesConnus()) {
            if (SANS_ACCUEIL_PROPRE.contains(role)) continue;
            if (!source.contains("\"" + role + "\"")) manquants.add(role);
        }

        assertTrue(manquants.isEmpty(),
            "Ces roles n'ont pas de destination declaree apres connexion et tombent sur le cas "
            + "par defaut, qui mene au tableau de bord de l'administration : " + manquants);
    }

    @Test
    void leMarketingArriveSurSonEspaceEtNonSurLeTableauDeBord() throws IOException {
        String source = Files.readString(HANDLER, StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("case \"MARKETING\"[^;]*sendRedirect\\(\"([^\"]+)\"\\)").matcher(source);
        assertTrue(m.find(), "le role MARKETING doit avoir sa propre destination");
        assertTrue(m.group(1).startsWith("/marketing"),
            "il doit arriver sur son espace, pas sur " + m.group(1));
    }

    @Test
    void chaqueDestinationDAccueilExisteVraiment() throws IOException {
        String source = Files.readString(HANDLER, StandardCharsets.UTF_8);
        List<String> introuvables = new ArrayList<>();

        Matcher m = Pattern.compile("case \"([A-Z_]+)\"[^;]*?sendRedirect\\(\"(/[a-z0-9/-]+)\"\\)",
            Pattern.DOTALL).matcher(source);
        while (m.find()) {
            String destination = m.group(2);
            if (!routeExiste(destination)) introuvables.add(m.group(1) + " -> " + destination);
        }

        assertTrue(introuvables.isEmpty(),
            "Ces roles sont envoyes vers une adresse qu'aucun controleur ne sert : " + introuvables);
    }

    // ── Extraction ──────────────────────────────────────────────────────

    /** Les roles que la securite nomme : c'est la liste de reference du logiciel. */
    private List<String> rolesConnus() throws IOException {
        String securite = Files.readString(SECURITE, StandardCharsets.UTF_8);
        List<String> roles = new ArrayList<>();
        Matcher m = Pattern.compile("\"(ADMIN|DIRECTEUR|ENSEIGNANT|SECRETAIRE|TRESORIER|COMPTABLE"
            + "|COORDONNATEUR|SURVEILLANT|INFIRMIER|MARKETING|PARENT|ELEVE|SUPER_ADMIN)\"")
            .matcher(securite);
        while (m.find()) if (!roles.contains(m.group(1))) roles.add(m.group(1));
        return roles;
    }

    /** Un controleur declare-t-il cette route, en prefixe de classe ou en methode ? */
    private boolean routeExiste(String route) throws IOException {
        Path controleurs = Paths.get("src", "main", "java", "holyflame", "administration", "controller");
        try (var fichiers = Files.list(controleurs)) {
            for (Path f : fichiers.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                if (source.contains("@RequestMapping(\"" + route + "\")")
                    || source.contains("Mapping(\"" + route + "\")")) return true;
            }
        }
        return Files.exists(TEMPLATES.resolve(route.substring(1) + ".html"));
    }
}
