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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le tableau de bord marketing est ecrit deux fois : un bloc « md:hidden » pour le
 * telephone, un autre pour l'ecran d'ordinateur.
 *
 * Une carte ajoutee a un seul des deux disparait pour la moitie des utilisateurs, et rien
 * ne le signale : la page s'affiche, elle est simplement incomplete. C'est arrive en
 * ajoutant l'interrupteur des pre-inscriptions, qui n'existait que sur telephone — un
 * directeur devant son ordinateur ne pouvait pas ouvrir ses demandes, et ne pouvait pas
 * non plus deviner que le reglage existait ailleurs.
 *
 * Tant que cet ecran vit en double, ce test garde les deux moities en accord.
 */
@DisplayName("Tableau de bord marketing : mobile et bureau")
class MarketingDeuxAffichagesTest {

    private static final Path PAGE = Paths.get("src", "main", "resources", "templates",
        "marketing-dashboard.html");

    @Test
    void chaqueFormulaireExisteDansLesDeuxAffichages() throws IOException {
        String source = Files.readString(PAGE, StandardCharsets.UTF_8);

        Map<String, Integer> occurrences = new LinkedHashMap<>();
        Matcher m = Pattern.compile("th:action=\"@\\{(/marketing/[a-z-]+)\\}\"").matcher(source);
        while (m.find()) occurrences.merge(m.group(1), 1, Integer::sum);

        assertTrue(occurrences.size() >= 2,
            "l'extraction a echoue : " + occurrences.size() + " action(s) trouvee(s)");

        List<String> orphelins = new ArrayList<>();
        for (var e : occurrences.entrySet()) {
            if (e.getValue() < 2) orphelins.add(e.getKey() + " (" + e.getValue() + " fois)");
        }

        assertTrue(orphelins.isEmpty(),
            "Ces formulaires n'apparaissent que dans un seul des deux affichages du tableau "
            + "de bord. La moitie des utilisateurs ne les verra jamais, sans aucune erreur "
            + "visible :\n  " + String.join("\n  ", orphelins));
    }

    @Test
    void lInterrupteurDesPreInscriptionsEstAtteignableDesDeuxCotes() throws IOException {
        String source = Files.readString(PAGE, StandardCharsets.UTF_8);

        int formulaires = source.split(Pattern.quote("th:action=\"@{/marketing/pre-inscription}\""), -1).length - 1;
        assertTrue(formulaires >= 2,
            "l'interrupteur des demandes doit exister sur telephone ET sur ordinateur, "
            + "trouve " + formulaires + " fois");

        // Le bloc telephone precede toujours celui de l'ordinateur dans ce fichier : si le
        // premier interrupteur se trouve apres la fin du bloc md:hidden, c'est que celui du
        // telephone manque.
        int mobile = source.indexOf("md:hidden");
        int premier = source.indexOf("/marketing/pre-inscription");
        assertTrue(mobile >= 0 && premier > mobile,
            "le premier interrupteur doit se trouver dans le bloc telephone");
    }
}
