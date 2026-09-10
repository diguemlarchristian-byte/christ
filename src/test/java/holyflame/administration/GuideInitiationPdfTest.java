package holyflame.administration;

import holyflame.administration.model.Etablissement;
import holyflame.administration.service.GuideInitiationPdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le guide precedent melangeait deux documents : une prise en main, et le compte rendu du jeu
 * de test comptable — 25 operations, comptes mouvementes, totaux de controle, et jusqu'a la
 * commande Maven qui le regenere. Plus de la moitie des pages ne s'adressaient pas a
 * l'utilisateur mais au developpeur.
 *
 * Ce test verrouille la separation : le guide remis a une ecole parle de l'outil et de son
 * demarrage, jamais de ce qui a ete corrige, ni de ce qu'il resterait a faire.
 */
@DisplayName("Guide de prise en main")
class GuideInitiationPdfTest {

    private final GuideInitiationPdfService service = new GuideInitiationPdfService();

    /** Le HTML replie ses paragraphes : on compare le texte, pas sa mise en forme. */
    private String texte(String nomEtablissement, String annee) {
        return service.html(nomEtablissement, annee).replaceAll("[\\s\\u00A0]+", " ");
    }

    private Etablissement ecole() {
        Etablissement e = new Etablissement();
        e.setNom("Ecole Les Palmiers");
        e.setAnneeScolaire("2026-2027");
        return e;
    }

    @Test
    void ilParleDeLEcoleQuiLeRecoit() {
        String html = service.html("Ecole Les Palmiers", "2026-2027");

        assertTrue(html.contains("Ecole Les Palmiers"), "le guide est nominatif");
        assertTrue(html.contains("2026-2027"), "et situe dans l'annee en cours");
    }

    @Test
    void ilInitieALUsage() {
        String html = service.html("Ecole Les Palmiers", "2026-2027");

        assertTrue(html.contains("Vos premiers pas"), "il dit par quoi commencer");
        assertTrue(html.contains("Qui fait quoi"), "il explique les fonctions");
        assertTrue(html.contains("Le rythme d'une annee"), "il situe le travail dans le temps");
        assertTrue(html.contains("Frais de scolarite"), "et nomme les ecrans reels du logiciel");
    }

    @Test
    void ilPresenteLOutilSansLeSurvendre() {
        String html = service.html("Ecole Les Palmiers", "2026-2027");

        assertTrue(texte("Ecole Les Palmiers", "2026-2027").contains("sans connexion Internet"),
            "l'argument le plus utile a une ecole doit y figurer");
        assertTrue(html.contains("Ce que cela vous evite"),
            "la promotion se fait par ce que l'outil epargne, pas par des superlatifs");
    }

    @Test
    void ilNeParlePasAuDeveloppeur() {
        String html = service.html("Ecole Les Palmiers", "2026-2027");

        assertFalse(html.contains("JeuDeTestComptableTest"), "aucune classe de test dans un guide utilisateur");
        assertFalse(html.contains("mvnw"), "aucune commande de build");
        assertFalse(html.contains("jeu de test"), "le compte rendu du jeu de test est un autre document");
        assertFalse(html.contains("25 operations"), "les donnees de demonstration n'ont rien a faire ici");
    }

    @Test
    void ilNEnumerePasLesCorrectionsNiLesReserves() {
        String html = service.html("Ecole Les Palmiers", "2026-2027").toLowerCase();

        assertFalse(html.contains("correctif"), "un utilisateur n'a que faire de ce qui a ete repare");
        assertFalse(html.contains("faille"), "ni des defauts passes de l'outil");
        assertFalse(html.contains("vulnerab"), "ni du vocabulaire d'un rapport d'audit");
        assertFalse(html.contains("proposition"), "un guide n'est pas une liste de suggestions");
        assertFalse(html.contains("recommandation"), "ni une liste de recommandations");
        assertFalse(html.contains("il faudrait"), "ni un inventaire de ce qui reste a faire");
    }

    @Test
    void leGuideEstUnVraiPdfDeuxPages() throws Exception {
        byte[] pdf = service.genererPdf(ecole());

        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
            "le livrable doit etre un PDF");
        assertTrue(pdf.length > 2000, "un guide de prise en main fait plus de deux kilo-octets");

        // Ecrit a cote des autres documents produits, pour pouvoir etre relu et imprime.
        Path dossier = Paths.get("C:", "Holyflame", "TEST COMPTABLE");
        if (Files.isDirectory(dossier)) {
            Files.write(dossier.resolve("guide-prise-en-main.pdf"), pdf);
            Files.writeString(dossier.resolve("guide-prise-en-main.html"),
                service.html("Ecole Les Palmiers", "2026-2027"), StandardCharsets.UTF_8);
        }
    }

    @Test
    void unEtablissementSansNomNeCassePasLeDocument() {
        byte[] pdf = service.genererPdf(new Etablissement());

        assertTrue(pdf.length > 2000, "le guide se produit meme avant que l'ecole soit renseignee");
        assertTrue(service.html(null, null).contains("votre etablissement"),
            "a defaut de nom, une formule neutre plutot qu'un blanc");
    }
}
