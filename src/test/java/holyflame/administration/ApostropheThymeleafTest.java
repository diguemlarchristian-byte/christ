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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le piege de l'apostrophe doublee, qui a casse trois pages a trois moments differents.
 *
 * Dans une expression Thymeleaf, doubler une apostrophe ne l'echappe QUE si l'on se trouve
 * deja a l'interieur de ${...}, ou c'est le moteur d'expressions qui lit la chaine. Ecrite
 * dans un litteral de l'expression standard — th:text="'Cumul de l''annee'" — elle ne
 * s'echappe pas : le moteur echoue a analyser la page, et ce n'est pas la ligne fautive qui
 * disparait mais l'ecran entier, remplace par une erreur technique.
 *
 * Le symptome est trompeur : la page marchait hier, un mot a ete ajoute, tout est casse. On
 * cherche alors du cote du controleur ou des donnees, jamais du cote d'une apostrophe.
 *
 * Ce test relit chaque expression de chaque gabarit, met de cote ce qui est a l'interieur de
 * ${...} — ou le doublement est correct et courant — et refuse ce qui reste.
 */
@DisplayName("Apostrophes dans les gabarits")
class ApostropheThymeleafTest {

    private static final Path TEMPLATES = Paths.get("src", "main", "resources", "templates");

    /** Les attributs dont la valeur est lue comme une expression standard. */
    private static final Pattern EXPRESSION = Pattern.compile(
        "th:(?:text|utext|value|title|placeholder|alt|href|action|classappend|with|if|unless)=\"([^\"]*)\"");

    /** Tout ce qui est entre ${ et } : l'apostrophe doublee y est legitime. */
    private static final Pattern INTERIEUR = Pattern.compile("[$*#@]\\{[^}]*\\}");

    /**
     * Une apostrophe doublee collee a une lettre : l''annee, d''un, n''a, qu''il.
     *
     * Il ne suffit pas de chercher deux apostrophes : la chaine vide s'ecrit '' et se
     * rencontre partout — condition ? 'actif' : ''. C'est un usage correct, et le confondre
     * avec le defaut remplirait ce test de fausses alertes jusqu'a ce qu'on cesse de le lire.
     * Seule l'apostrophe accolee a un mot trahit une tentative d'echappement.
     */
    private static final Pattern DOUBLEE_DANS_UN_MOT =
        Pattern.compile("\\p{L}''|''\\p{L}");

    @Test
    void aucuneApostropheDoubleeHorsDUneExpressionInterne() throws IOException {
        List<String> fautes = new ArrayList<>();

        try (Stream<Path> fichiers = Files.walk(TEMPLATES)) {
            for (Path f : fichiers.filter(p -> p.toString().endsWith(".html")).toList()) {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = EXPRESSION.matcher(source);
                while (m.find()) {
                    String reste = INTERIEUR.matcher(m.group(1)).replaceAll("");
                    if (DOUBLEE_DANS_UN_MOT.matcher(reste).find()) {
                        fautes.add(TEMPLATES.relativize(f) + " : " + m.group(1).trim());
                    }
                }
            }
        }

        assertTrue(fautes.isEmpty(),
            "Ces expressions doublent une apostrophe hors de ${...}. Thymeleaf ne saura pas "
            + "analyser la page, et c'est l'ecran entier qui tombera. Sortez le texte de "
            + "l'expression et laissez-le en HTML :\n  " + String.join("\n  ", fautes));
    }

    @Test
    void laDetectionReconnaitLeDefautEtLaisseLaChaineVideTranquille() {
        // La faute, sous ses formes reelles.
        for (String faute : List.of("'Cumul de l''annee'", "'vous n''avez pas'", "'qu''il reste'")) {
            assertTrue(DOUBLEE_DANS_UN_MOT.matcher(faute).find(),
                "cette expression casse la page et doit etre signalee : " + faute);
        }
        // Et les usages corrects, qu'il ne faut surtout pas signaler.
        for (String correct : List.of("${actif} ? 'oui' : ''", "'x' + ${a} + ''", "${b} ? '' : 'non'")) {
            assertTrue(!DOUBLEE_DANS_UN_MOT.matcher(correct).find(),
                "la chaine vide est un usage normal, la signaler ferait cesser de lire ce test : " + correct);
        }
    }

    @Test
    void lExtractionTrouveBienDesExpressionsARelire() throws IOException {
        int expressions = 0;
        try (Stream<Path> fichiers = Files.walk(TEMPLATES)) {
            for (Path f : fichiers.filter(p -> p.toString().endsWith(".html")).toList()) {
                Matcher m = EXPRESSION.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) expressions++;
            }
        }
        // Sans ce garde-fou, une expression reguliere devenue fausse rendrait le test
        // ci-dessus vert en ne lisant plus rien.
        assertTrue(expressions > 500,
            "seulement " + expressions + " expressions relues : l'extraction ne fonctionne plus");
    }
}
