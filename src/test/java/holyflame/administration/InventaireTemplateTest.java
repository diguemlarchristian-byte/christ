package holyflame.administration;

import holyflame.administration.model.ArticleInventaire;
import holyflame.administration.model.MouvementInventaire;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.service.InventaireRegles;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'ecran d'inventaire n'exposait que « Ajouter » et « Supprimer ». Modifier une fiche,
 * enregistrer un mouvement et lire l'historique existaient cote serveur mais n'etaient
 * appelables depuis aucun bouton : un materiel casse ne pouvait pas etre declare casse.
 * Ce test verrouille la presence de ces commandes a l'ecran, et l'affichage des donnees
 * qui etaient saisies puis jamais relues.
 */
class InventaireTemplateTest {

    private SpringTemplateEngine moteur() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private ArticleInventaire tablesBancs() {
        ArticleInventaire a = new ArticleInventaire();
        a.setId(7L);
        a.setNom("Tables-bancs 2 places");
        a.setCategorie("MATERIEL_BUREAU");
        a.setEtat("BON_ETAT");
        a.setQuantite(40);
        a.setQuantiteEnReparation(3);
        a.setQuantiteHorsService(1);
        a.setValeurUnitaire(25_000.0);
        a.setLocalisation("Salle 6eme A");
        a.setFournisseur("Menuiserie du Chari");
        a.setNumSerie("TB-2026-114");
        a.setDateAcquisition(LocalDate.of(2026, 9, 15));
        a.setNotes("Deux pieds a resserrer chaque trimestre.");
        return a;
    }

    private String rendre(ArticleInventaire article, boolean filtreActif, String filtre) {
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);

        Utilisateur u = new Utilisateur();
        u.setNom("MAHAMAT"); u.setPrenom("Achta"); u.setRole("ADMIN");
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");

        List<ArticleInventaire> articles = article == null ? List.of() : List.of(article);
        ctx.setVariable("articles", articles);
        ctx.setVariable("articlesJson", List.of());
        ctx.setVariable("totalArticles", articles.size());
        ctx.setVariable("totalEnService", 36);
        ctx.setVariable("totalReparation", 3);
        ctx.setVariable("totalHorsService", 1);
        ctx.setVariable("valeurTotale", 900_000.0);
        ctx.setVariable("filtre", filtre);
        ctx.setVariable("filtreCategorie", null);
        ctx.setVariable("filtreEtat", null);
        ctx.setVariable("filtreActif", filtreActif);

        MouvementInventaire m = new MouvementInventaire();
        m.setId(1L); m.setArticle(article); m.setType("REPARATION"); m.setQuantite(3);
        m.setDate(LocalDate.of(2027, 1, 12));
        m.setMotif("Pieds casses, atelier du quartier");
        m.setEffectuePar("Achta MAHAMAT");
        ctx.setVariable("historique", article == null ? Map.of() : Map.of(7L, List.of(m)));

        ctx.setVariable("categories", InventaireRegles.CATEGORIES);
        ctx.setVariable("etats", InventaireRegles.ETATS);
        ctx.setVariable("typesMouvement", InventaireRegles.MOUVEMENTS);
        ctx.setVariable("peutRattacherDepense", false);
        ctx.setVariable("depenses", List.of());
        ctx.setVariable("depensesParId", Map.of());

        return moteur().process("inventaire", ctx);
    }

    @Test
    void lesTroisCommandesQuiNExistaientPasSontALEcran() {
        String html = rendre(tablesBancs(), false, null);

        // Le formulaire de modification est partage avec l'ajout : c'est le bouton qui le
        // reoriente vers /inventaire/{id}/modifier, d'ou la verification en deux temps.
        assertTrue(html.contains("ouvrirModification(7)"),
            "chaque article doit porter un bouton de modification");
        assertTrue(html.contains("'/inventaire/' + id + '/modifier'"),
            "ce bouton doit viser l'endpoint de modification");
        assertTrue(html.contains("/inventaire/7/mouvement"),
            "l'enregistrement d'un mouvement doit etre atteignable");
        assertTrue(html.contains("/inventaire/7/supprimer"),
            "la suppression reste possible");
    }

    @Test
    void lesUnitesIndisponiblesSeLisentSansOuvrirLaFiche() {
        String html = rendre(tablesBancs(), false, null);

        assertTrue(html.contains("En reparation"), "le compteur des unites en reparation est affiche");
        assertTrue(html.contains("Hors service"), "celui des unites reformees aussi");
        assertTrue(html.contains("Unites en service"), "et celui des unites utilisables");
    }

    @Test
    void lesDonneesSaisiesPuisJamaisReluesSontEnfinAffichees() {
        String html = rendre(tablesBancs(), false, null);

        assertTrue(html.contains("Menuiserie du Chari"), "le fournisseur etait saisi sans jamais etre relu");
        assertTrue(html.contains("TB-2026-114"), "le numero de serie aussi");
        assertTrue(html.contains("15/09/2026"), "la date d'acquisition aussi");
        assertTrue(html.contains("Deux pieds a resserrer"), "les notes n'etaient meme pas saisissables");
    }

    @Test
    void lHistoriqueDesMouvementsEstRendu() {
        String html = rendre(tablesBancs(), false, null);

        assertTrue(html.contains("Depart en reparation"), "le type du mouvement est en clair");
        assertTrue(html.contains("Pieds casses"), "son motif est affiche");
        assertTrue(html.contains("Achta MAHAMAT"), "et qui l'a effectue");
        assertTrue(html.contains("12/01/2027"), "et quand");
    }

    @Test
    void lesCodesTechniquesNeSontPlusAffichesBruts() {
        String html = rendre(tablesBancs(), false, null);

        assertTrue(html.contains("Materiel de bureau"), "la categorie est en francais");
        assertFalse(html.contains(">MATERIEL_BUREAU<"), "le code technique ne doit plus paraitre dans le tableau");
        assertTrue(html.contains("Bon etat"), "l'etat aussi");
    }

    @Test
    void laRechercheEtLesFiltresSontProposes() {
        String html = rendre(tablesBancs(), false, null);

        assertTrue(html.contains("name=\"q\""), "un champ de recherche est present");
        assertTrue(html.contains("name=\"categorie\""), "un filtre par categorie aussi");
        assertTrue(html.contains("Exporter"), "l'inventaire peut etre sorti pour etre presente");
    }

    @Test
    void uneRechercheSansResultatLeDitAutrementQuUnInventaireVide() {
        String html = rendre(null, true, "videoprojecteur");

        assertTrue(html.contains("Aucun article ne correspond a cette recherche"),
            "ne pas laisser croire que l'inventaire est vide alors qu'un filtre est actif");
    }

    @Test
    void laDepenseNEstProposeeQuAQuiPeutVoirLesDepenses() {
        String html = rendre(tablesBancs(), false, null);

        assertFalse(html.contains("name=\"depenseId\""),
            "sans droit sur les depenses, le champ de rattachement ne doit pas etre rendu");
    }
}
