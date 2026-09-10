package holyflame.administration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un lien de menu propose a un role que la securite refuse est la pire des incoherences :
 * l'utilisateur voit son ecran, clique, et recoit un acces refuse sans comprendre. Il en
 * conclut que le logiciel est casse, pas qu'il n'a pas le droit.
 *
 * Ce test lit chaque entree du menu, releve les roles nommes dans sa condition d'affichage,
 * et verifie que la regle de securite du chemin vise les accepte tous. Il travaille dans ce
 * sens uniquement : un role autorise sans lien de menu n'est pas forcement un defaut — il
 * atteint parfois l'ecran depuis une autre page, comme l'emploi du temps depuis le tableau
 * de bord.
 */
@DisplayName("Coherence entre menu et securite")
class CoherenceMenuSecuriteTest {

    private static final Path MENU =
        Paths.get("src", "main", "resources", "templates", "fragments", "nav-links-admin.html");
    private static final Path CONFIG =
        Paths.get("src", "main", "java", "holyflame", "administration", "config", "SecurityConfig.java");

    /**
     * Chemins dont la regle depend d'autre chose que du role — un module financier, un module
     * optionnel du Directeur — et que ce test ne sait donc pas trancher.
     */
    private static final Set<String> HORS_PORTEE = Set.of("/finances", "/inventaire", "/surveillant",
        "/infirmerie", "/coordination", "/marketing", "/secretariat", "/secretariat/absences",
        "/secretariat/cartes", "/academique-universite", "/direction/suivi");

    @Test
    void aucuneEntreeDeMenuNeMeneAUnAccesRefuse() throws IOException {
        Map<String, Set<String>> entrees = entreesDuMenu();
        Map<String, Set<String>> regles = reglesDeSecurite();

        assertTrue(entrees.size() > 10, "l'extraction du menu a echoue : " + entrees.size() + " entree(s)");

        List<String> fautes = new ArrayList<>();
        for (var entree : entrees.entrySet()) {
            String chemin = entree.getKey();
            if (HORS_PORTEE.contains(chemin)) continue;

            Set<String> autorises = regleApplicable(regles, chemin);
            if (autorises == null || autorises.isEmpty()) continue; // couverture testee ailleurs

            for (String role : entree.getValue()) {
                if (!autorises.contains(role)) {
                    fautes.add(chemin + " est propose au role " + role
                        + " que la securite refuse (elle admet " + autorises + ")");
                }
            }
        }

        assertTrue(fautes.isEmpty(),
            "Ces entrees de menu menent a un acces refuse. Alignez la condition d'affichage sur "
            + "la regle, ou la regle sur l'usage voulu :\n  " + String.join("\n  ", fautes));
    }

    @Test
    void lesBulletinsSontAtteignablesParLaDirection() throws IOException {
        Set<String> roles = entreesDuMenu().getOrDefault("/bulletins", Set.of());

        assertTrue(roles.contains("ADMIN") && roles.contains("DIRECTEUR"),
            "la securite les autorise sur les bulletins, mais aucune page ne leur en ouvrait la "
            + "porte : en fin de trimestre, un directeur n'avait aucun moyen d'y acceder");
    }

    // ── Extraction ──────────────────────────────────────────────────────

    /** Chaque lien du menu, avec les roles nommes dans sa condition d'affichage. */
    private Map<String, Set<String>> entreesDuMenu() throws IOException {
        Map<String, Set<String>> entrees = new LinkedHashMap<>();
        Pattern role = Pattern.compile("role == '([A-Z_]+)'");

        for (String ligne : Files.readAllLines(MENU, StandardCharsets.UTF_8)) {
            if (!ligne.contains("<a ")) continue;

            Matcher h = Pattern.compile("href=\"(/[a-z0-9/-]*)\"").matcher(ligne);
            if (!h.find()) continue;
            String chemin = h.group(1);

            Matcher c = Pattern.compile("th:if=\"([^\"]*)\"").matcher(ligne);
            if (!c.find()) continue; // sans condition, l'entree s'adresse a qui voit ce menu
            Set<String> roles = new LinkedHashSet<>();
            Matcher r = role.matcher(c.group(1));
            while (r.find()) roles.add(r.group(1));
            if (!roles.isEmpty()) entrees.put(chemin, roles);
        }
        return entrees;
    }

    /** Chaque requestMatchers(...).hasAnyRole(...) : le chemin vise et les roles admis. */
    private Map<String, Set<String>> reglesDeSecurite() throws IOException {
        String source = Files.readString(CONFIG, StandardCharsets.UTF_8);
        Map<String, Set<String>> regles = new LinkedHashMap<>();

        Matcher m = Pattern.compile(
            "requestMatchers\\(([^)]*?)\\)\\s*\\.has(?:Any)?Role\\(([^)]*?)\\)", Pattern.DOTALL)
            .matcher(source);
        while (m.find()) {
            Set<String> roles = new LinkedHashSet<>();
            Matcher r = Pattern.compile("\"([A-Z_]+)\"").matcher(m.group(2));
            while (r.find()) roles.add(r.group(1));

            Matcher chemins = Pattern.compile("\"(/[A-Za-z0-9/*_.-]*)\"").matcher(m.group(1));
            while (chemins.find()) regles.putIfAbsent(chemins.group(1), roles);
        }
        return regles;
    }

    /** La premiere regle dont le motif couvre ce chemin — l'ordre de declaration fait foi. */
    private Set<String> regleApplicable(Map<String, Set<String>> regles, String chemin) {
        for (var regle : regles.entrySet()) {
            String motif = regle.getKey();
            String base = motif.endsWith("/**") ? motif.substring(0, motif.length() - 3)
                        : motif.endsWith("/*") ? motif.substring(0, motif.length() - 2)
                        : motif;
            if (base.isEmpty()) continue;
            if (chemin.equals(base) || chemin.startsWith(base + "/")) return regle.getValue();
        }
        return null;
    }
}
