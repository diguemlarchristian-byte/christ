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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un lien de menu qui mene a un acces refuse est la pire des incoherences : l'utilisateur
 * voit son ecran, clique, et recoit un refus sans comprendre. Il en conclut que le logiciel
 * est casse, pas qu'il n'a pas le droit. L'inverse est aussi fautif depuis que les acces se
 * cochent : une case activee qui ne fait apparaitre aucun lien donne un droit invisible,
 * donc inutilisable.
 *
 * Les deux cotes nomment desormais la meme chose — une fonctionnalite du registre. Le menu
 * ecrit peutFaire.contains('MATIERES'), la securite ecrit peut(Fonctionnalites.MATIERES).
 * Ce test verifie que ce sont bien les memes codes, chemin par chemin.
 *
 * C'est une garantie plus forte qu'avant : on ne verifie plus qu'une liste de roles est
 * incluse dans une autre, mais que les deux cotes disent exactement la meme chose.
 */
@DisplayName("Coherence entre menu et securite")
class CoherenceMenuSecuriteTest {

    private static final Path MENU =
        Paths.get("src", "main", "resources", "templates", "fragments", "nav-links-admin.html");
    private static final Path CONFIG =
        Paths.get("src", "main", "java", "holyflame", "administration", "config", "SecurityConfig.java");

    /**
     * Entrees dont la condition d'affichage ne peut pas se resumer a un code unique.
     *
     * « Finances » mene a une page a onglets dont chacun suit sa propre fonctionnalite :
     * le menu s'affiche des qu'une seule porte est ouverte, et c'est voulu.
     */
    private static final Set<String> HORS_PORTEE = Set.of("/finances");

    @Test
    void chaqueLienDeMenuExigeExactementCeQueLaSecuriteExige() throws IOException {
        Map<String, String> menu = codesDuMenu();
        Map<String, String> securite = codesDeLaSecurite();

        assertTrue(menu.size() > 10, "l'extraction du menu a echoue : " + menu.size() + " entree(s)");

        List<String> fautes = new ArrayList<>();
        for (var entree : menu.entrySet()) {
            String chemin = entree.getKey();
            if (HORS_PORTEE.contains(chemin)) continue;

            String attendu = regleApplicable(securite, chemin);
            if (attendu == null) continue; // couverture des routes testee ailleurs

            if (!attendu.equals(entree.getValue())) {
                fautes.add(chemin + " : le menu demande " + entree.getValue()
                    + ", la securite demande " + attendu);
            }
        }

        assertTrue(fautes.isEmpty(),
            "Menu et securite ne nomment pas la meme fonctionnalite. L'un des deux donne un "
            + "acces que l'autre refuse :\n  " + String.join("\n  ", fautes));
    }

    @Test
    void toutLienDeMenuNommeUneFonctionnaliteConnue() throws IOException {
        Set<String> codesConnus = codesDuRegistre();
        List<String> inconnus = new ArrayList<>();

        for (var entree : codesDuMenu().entrySet()) {
            if (HORS_PORTEE.contains(entree.getKey())) continue;
            if (!codesConnus.contains(entree.getValue())) {
                inconnus.add(entree.getKey() + " nomme " + entree.getValue());
            }
        }

        assertTrue(inconnus.isEmpty(),
            "Une condition de menu cite une fonctionnalite qui n'existe pas dans le registre. "
            + "Elle sera toujours fausse, et le lien ne s'affichera jamais :\n  "
            + String.join("\n  ", inconnus));
    }

    @Test
    void lesBulletinsSontAtteignablesParLaDirection() throws IOException {
        // La direction edite les bulletins en fin de trimestre. Le lien doit suivre la
        // fonctionnalite BULLETINS, que le role DIRECTEUR recoit par defaut.
        assertEquals("BULLETINS", codesDuMenu().get("/bulletins"),
            "sans ce lien, un directeur n'a aucun moyen d'atteindre les bulletins");
    }

    // ── Extraction ──────────────────────────────────────────────────────

    /** Chaque lien du menu, avec la fonctionnalite nommee dans sa condition d'affichage. */
    private Map<String, String> codesDuMenu() throws IOException {
        Map<String, String> entrees = new LinkedHashMap<>();
        Pattern code = Pattern.compile("peutFaire\\.contains\\('([A-Z_]+)'\\)");

        for (String ligne : Files.readAllLines(MENU, StandardCharsets.UTF_8)) {
            if (!ligne.contains("<a ")) continue;

            Matcher h = Pattern.compile("href=\"(/[a-z0-9/-]*)\"").matcher(ligne);
            if (!h.find()) continue;

            Matcher c = Pattern.compile("th:if=\"([^\"]*)\"").matcher(ligne);
            if (!c.find()) continue;
            Matcher r = code.matcher(c.group(1));
            if (r.find()) entrees.put(h.group(1), r.group(1));
        }
        return entrees;
    }

    /** Chaque requestMatchers(...).access(peut(Fonctionnalites.X)) : le chemin et le code. */
    private Map<String, String> codesDeLaSecurite() throws IOException {
        String source = Files.readString(CONFIG, StandardCharsets.UTF_8);
        Map<String, String> regles = new LinkedHashMap<>();

        Matcher m = Pattern.compile(
            "requestMatchers\\(([^)]*?)\\)\\s*\\.access\\(peut\\(Fonctionnalites\\.([A-Z_]+)\\)\\)",
            Pattern.DOTALL).matcher(source);
        while (m.find()) {
            String code = m.group(2);
            Matcher chemins = Pattern.compile("\"(/[A-Za-z0-9/*_.-]*)\"").matcher(m.group(1));
            while (chemins.find()) regles.putIfAbsent(chemins.group(1), code);
        }
        return regles;
    }

    /** Les codes declares dans le registre, lus a la source. */
    private Set<String> codesDuRegistre() throws IOException {
        String source = Files.readString(Paths.get("src", "main", "java", "holyflame",
            "administration", "service", "Fonctionnalites.java"), StandardCharsets.UTF_8);
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = Pattern.compile("public static final String [A-Z_]+ = \"([A-Z_]+)\";").matcher(source);
        while (m.find()) codes.add(m.group(1));
        return codes;
    }

    /** La premiere regle dont le motif couvre ce chemin — l'ordre de declaration fait foi. */
    private String regleApplicable(Map<String, String> regles, String chemin) {
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
