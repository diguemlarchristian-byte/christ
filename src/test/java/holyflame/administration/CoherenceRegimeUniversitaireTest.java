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
    void leReleveEstAtteignableDepuisLesDeuxMenusQuiLeConcernent() throws IOException {
        // Un ecran sans porte est une capacite invisible. Le releve a failli rester joignable
        // par la seule adresse : il a ete construit, securise, teste, et oublie des menus.
        String admin = lire(TEMPLATES.resolve("fragments/nav-links-admin.html"));
        assertTrue(admin.contains("href=\"/releve-notes\""),
            "le menu de l'administration doit ouvrir le releve en regime LMD");

        int lien = admin.indexOf("href=\"/releve-notes\"");
        String bloc = admin.substring(Math.max(0, lien - 800), lien);
        assertTrue(bloc.contains("estUniversite == true"),
            "il n'a de sens qu'en regime universitaire");

        String coordination = lire(TEMPLATES.resolve("fragments/nav-links-coordination.html"));
        assertTrue(coordination.contains("/releve-notes"),
            "le coordonnateur suit la pedagogie : les resultats le concernent au premier chef");
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
    void lEcranDeParametresNAnnonceQueDesEcransQuiExistent() throws IOException {
        String parametres = lire(TEMPLATES.resolve("parametres.html"));

        int regime = parametres.indexOf("Le mode universitaire");
        assertTrue(regime > 0, "la description du regime doit rester lisible d'un bloc");
        String description = parametres.substring(regime, Math.min(parametres.length(), regime + 1400));

        // Cette assertion etait longtemps l'inverse : la description promettait un releve
        // semestriel que rien ne produisait, et la garde interdisait de l'annoncer. Le releve
        // existe desormais, la garde a donc ete retournee — l'ecran doit le nommer.
        assertTrue(releveSemestrielExiste(),
            "le releve semestriel a disparu : la description du regime le promet toujours");
        assertTrue(description.contains("Releves de notes"),
            "la bascule en LMD doit dire ou les resultats se lisent");
        assertTrue(description.contains("acquise, compensee ou non validee"),
            "et ce que le releve etablit pour chaque unite");
    }

    @Test
    void laDescriptionDitCeQuIlFautRenseignerPourQuUnReleveExiste() throws IOException {
        String parametres = lire(TEMPLATES.resolve("parametres.html"));
        int regime = parametres.indexOf("Le mode universitaire");
        String description = parametres.substring(regime, Math.min(parametres.length(), regime + 1400));

        // Les deux maillons de la chaine sont invisibles depuis cet ecran : sans eux le releve
        // reste vide, et l'administrateur n'a aucune raison de deviner lesquels remplir.
        assertTrue(description.contains("le parcours qu'elle suit"),
            "une classe sans parcours ne renvoie a aucune unite d'enseignement");
        assertTrue(description.contains("la matiere qui porte ses notes"),
            "un element sans matiere ne peut recevoir aucune note");
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
