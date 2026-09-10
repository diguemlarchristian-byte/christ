package holyflame.administration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toute route sans regle explicite tombe sur {@code anyRequest().authenticated()} — elle
 * s'ouvre donc a n'importe quel compte connecte, parent et eleve compris.
 *
 * C'est ainsi que le tableau de bord, qui affiche le total encaisse, le budget et les
 * effectifs de l'etablissement, et la recherche, qui parcourt l'annuaire des eleves et du
 * personnel, sont restes ouverts a tout le monde : non par une regle trop large, mais par
 * l'absence de regle. Un oubli de ce genre ne se voit pas en relisant SecurityConfig,
 * puisqu'il s'agit precisement de ce qui n'y figure pas.
 *
 * Ce test compare donc les routes declarees par les controleurs aux regles ecrites, et
 * signale celles qui n'ont ni regle propre, ni prefixe qui les couvre, ni place dans la
 * liste publique. Il ne juge pas si une regle est bonne — seulement qu'une decision a ete
 * prise.
 */
@DisplayName("Couverture des routes par la securite")
class CouvertureSecuriteRoutesTest {

    private static final Path CONTROLEURS =
        Paths.get("src", "main", "java", "holyflame", "administration", "controller");
    private static final Path CONFIG_SECURITE =
        Paths.get("src", "main", "java", "holyflame", "administration", "config", "SecurityConfig.java");

    /**
     * Routes admises sans decision explicite : elles ne servent rien de sensible, ou leur
     * controleur restreint lui-meme ce qu'il montre au compte connecte. Toute addition ici
     * doit s'accompagner de sa raison.
     */
    private static final Set<String> TOLEREES = Set.of(
        // Pages du site vitrine public d'un etablissement, servies sans compte.
        "/actualites", "/a-propos", "/galerie", "/evenements", "/temoignages", "/equipe",
        // Assistant de demarrage : n'affiche que l'avancement de la configuration en cours.
        "/demarrer",
        // Baremes et corrections : le controleur filtre sur les classes de l'enseignant.
        "/bareme", "/correction",
        // Servies en fichiers statiques, deja dans la liste publique.
        "/uploads"
    );

    @Test
    void chaqueRouteDeControleurEstCouverteParUneDecision() throws IOException {
        String securite = Files.readString(CONFIG_SECURITE, StandardCharsets.UTF_8);
        List<String> motifs = motifsDeSecurite(securite);
        Set<String> routes = routesDeControleurs();

        assertTrue(routes.size() > 30,
            "l'extraction des routes a echoue : " + routes.size() + " route(s) trouvee(s)");

        List<String> decouvertes = new ArrayList<>();
        for (String route : routes) {
            if (TOLEREES.contains(route)) continue;
            if (motifs.stream().noneMatch(m -> couvre(m, route))) decouvertes.add(route);
        }

        assertTrue(decouvertes.isEmpty(),
            "Ces routes n'ont aucune regle de securite et s'ouvrent donc a tout compte connecte, "
            + "parent et eleve compris. Donnez-leur une regle dans SecurityConfig, ou inscrivez-les "
            + "dans TOLEREES avec la raison : " + decouvertes);
    }

    @Test
    void lesEcransDeLEtablissementNeSontPasOuvertsAuxFamilles() throws IOException {
        String securite = Files.readString(CONFIG_SECURITE, StandardCharsets.UTF_8);

        for (String route : List.of("/dashboard", "/recherche", "/emploi-du-temps")) {
            List<String> motifs = motifsDeSecurite(securite);
            assertTrue(motifs.stream().anyMatch(m -> couvre(m, route)),
                route + " affiche des donnees de tout l'etablissement : il lui faut une regle propre");
        }

        // La regle qui les porte ne doit nommer ni PARENT ni ELEVE.
        Matcher m = Pattern.compile(
            "requestMatchers\\(\"/dashboard\"[^;]*?\\)\\s*\\n?\\s*\\.hasAnyRole\\(([^;]*?)\\)",
            Pattern.DOTALL).matcher(securite);
        assertTrue(m.find(), "la regle du tableau de bord doit rester lisible d'un bloc");
        String roles = m.group(1);
        assertTrue(!roles.contains("\"PARENT\""), "un parent n'a pas a voir le budget de l'ecole");
        assertTrue(!roles.contains("\"ELEVE\""), "un eleve non plus");
    }

    // ── Extraction ──────────────────────────────────────────────────────

    /** Les chemins nommes dans un requestMatchers(...), regles et liste publique confondues. */
    private List<String> motifsDeSecurite(String source) {
        List<String> motifs = new ArrayList<>();
        Matcher m = Pattern.compile("\"(/[A-Za-z0-9/*_.-]*)\"").matcher(source);
        while (m.find()) motifs.add(m.group(1));
        return motifs;
    }

    /** Le prefixe de chaque controleur, ou a defaut ses routes de premier niveau. */
    private Set<String> routesDeControleurs() throws IOException {
        Set<String> routes = new LinkedHashSet<>();
        Pattern classe = Pattern.compile("@RequestMapping\\(\"(/[a-z0-9/-]+)\"\\)");
        Pattern methode = Pattern.compile("@(?:Get|Post)Mapping\\(\"(/[a-z0-9-]+)");

        try (Stream<Path> fichiers = Files.list(CONTROLEURS)) {
            for (Path f : fichiers.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                Matcher c = classe.matcher(source);
                if (c.find()) {
                    routes.add(c.group(1));
                } else {
                    Matcher mm = methode.matcher(source);
                    while (mm.find()) routes.add(mm.group(1));
                }
            }
        }
        return routes;
    }

    /** Un motif couvre une route s'il la nomme, ou s'il en est un prefixe de chemin. */
    private boolean couvre(String motif, String route) {
        String base = motif.endsWith("/**") ? motif.substring(0, motif.length() - 3)
                    : motif.endsWith("/*") ? motif.substring(0, motif.length() - 2)
                    : motif;
        if (base.isEmpty()) return false;
        return route.equals(base) || route.startsWith(base + "/");
    }
}
