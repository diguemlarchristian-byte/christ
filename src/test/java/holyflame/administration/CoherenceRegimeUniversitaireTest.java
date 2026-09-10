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
 * Basculer un etablissement en regime LMD masque les ecrans propres au trimestre. Deux choses
 * s'en sont suivies sans qu'on les voie.
 *
 * D'abord, « Gestion academique » disparaissait du menu — or c'etait la seule porte vers les
 * matieres, et toute note est rattachee a une matiere. Une universite ne pouvait donc plus
 * creer les matieres dont sa propre saisie de notes depend.
 *
 * Ensuite, l'ecran de parametres annoncait que le mode universitaire « remplace les bulletins
 * trimestriels par des releves de notes semestriels » et « fait valider les etudiants unite
 * par unite ». Aucun releve semestriel n'existe, et les regles de RegimeAcademiqueService ne
 * sont appelees par rien : un administrateur basculait sur la foi d'une promesse.
 *
 * Ces tests tiennent les deux : une porte vers les matieres en LMD, et un texte qui ne promet
 * que ce qui existe. Quand le releve semestriel sera construit, le second test dira ou remettre
 * la phrase.
 */
@DisplayName("Coherence du regime universitaire")
class CoherenceRegimeUniversitaireTest {

    private static final Path TEMPLATES = Paths.get("src", "main", "resources", "templates");
    private static final Path SOURCES = Paths.get("src", "main", "java", "holyflame", "administration");

    private String lire(Path chemin) throws IOException {
        return Files.readString(chemin, StandardCharsets.UTF_8);
    }

    @Test
    void uneUniversiteGardeUnePorteVersLesMatieres() throws IOException {
        String menu = lire(TEMPLATES.resolve("fragments/nav-links-admin.html"));

        assertTrue(menu.contains("href=\"/matieres\""),
            "sans ce lien, une universite ne peut plus creer les matieres dont ses notes dependent");

        int lien = menu.indexOf("href=\"/matieres\"");
        String bloc = menu.substring(Math.max(0, lien - 700), lien);
        assertTrue(bloc.contains("estUniversite == true"),
            "le lien doit apparaitre en regime LMD, ou Gestion academique n'est plus la");
        assertTrue(bloc.contains("'ADMIN'") && bloc.contains("'DIRECTEUR'"),
            "et n'etre propose qu'aux roles que /matieres autorise reellement");
    }

    @Test
    void leMenuNeMontreQueDesEcransQueLeRegimeJustifie() throws IOException {
        String menu = lire(TEMPLATES.resolve("fragments/nav-links-admin.html"));

        for (String scolaire : new String[]{"href=\"/gestion-academique\"", "href=\"/passage\""}) {
            int lien = menu.indexOf(scolaire);
            assertTrue(lien > 0, scolaire + " doit rester dans le menu");
            String bloc = menu.substring(Math.max(0, lien - 700), lien);
            assertTrue(bloc.contains("estUniversite != true"),
                scolaire + " n'a pas de sens en LMD et doit y rester masque");
        }
    }

    @Test
    void lEcranDeParametresNePrometPasUnReleveQuiNExistePas() throws IOException {
        String parametres = lire(TEMPLATES.resolve("parametres.html"));

        int regime = parametres.indexOf("Le mode universitaire");
        assertTrue(regime > 0, "la description du regime doit rester lisible d'un bloc");
        String description = parametres.substring(regime, Math.min(parametres.length(), regime + 700));

        // Tant qu'aucun ecran ne produit de releve semestriel, la description ne doit pas
        // l'annoncer. Quand il existera, remplacer cette assertion par sa reciproque.
        assertFalse(releveSemestrielExiste(),
            "un releve de notes semestriel existe maintenant : remettez-le dans la description "
            + "du regime et retirez cette garde");
        assertFalse(description.contains("releves de notes semestriels"),
            "l'ecran promettait un releve semestriel qui n'est produit nulle part");
        assertFalse(description.contains("valider les etudiants unite par unite"),
            "les regles de validation sont enregistrees, mais appliquees a aucun etudiant");
    }

    @Test
    void laDescriptionDitCeQueLaBasculeChangeVraiment() throws IOException {
        String parametres = lire(TEMPLATES.resolve("parametres.html"));
        int regime = parametres.indexOf("Le mode universitaire");
        String description = parametres.substring(regime, Math.min(parametres.length(), regime + 700));

        assertTrue(description.contains("Structure academique"),
            "elle doit nommer l'ecran que la bascule ouvre reellement");
        assertTrue(description.contains("masque les reglages propres au trimestre"),
            "et prevenir de ce qu'elle retire");
        assertTrue(description.contains("bulletins restent disponibles"),
            "un administrateur doit savoir qu'il ne perd pas ses bulletins");
    }

    /**
     * Vrai le jour ou un ecran produira reellement un releve de notes semestriel.
     *
     * Le motif est volontairement etroit : « /releve » tout court designe deja le releve
     * financier remis aux parents, qui n'a rien d'academique. Chercher ce mot seul faisait
     * croire que le releve semestriel existait.
     */
    private boolean releveSemestrielExiste() throws IOException {
        Path controleurs = SOURCES.resolve("controller");
        try (var fichiers = Files.list(controleurs)) {
            for (Path f : fichiers.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = lire(f);
                if (source.contains("releve-notes") || source.contains("releve-de-notes")) return true;
            }
        }
        return Files.exists(TEMPLATES.resolve("releve-notes.html"))
            || Files.exists(TEMPLATES.resolve("releve-semestriel.html"));
    }
}
