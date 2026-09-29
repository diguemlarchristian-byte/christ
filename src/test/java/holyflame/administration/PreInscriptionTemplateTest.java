package holyflame.administration;

import holyflame.administration.model.DemandeInscription;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.SiteVitrine;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les pages qu'une famille voit.
 *
 * Ce sont les seules pages du logiciel destinees a quelqu'un qui n'appartient pas a
 * l'ecole, et souvent les premieres qu'on en voit. Une page qui tombe n'y coute pas un
 * ecran : elle coute une inscription, et la famille ne rappellera pas pour signaler le bug.
 */
@DisplayName("Pages publiques de pre-inscription")
class PreInscriptionTemplateTest {

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

    private WebContext contexteWeb() {
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        return new WebContext(exchange);
    }

    private SiteVitrine site(boolean preinscriptionOuverte) {
        SiteVitrine s = new SiteVitrine();
        s.setId(1L);
        s.setSlug("lycee-espoir");
        s.setActif(true);
        s.setPreinscriptionActive(preinscriptionOuverte);
        s.setTelephoneContact("+235 66 00 00 00");
        s.setDateActivation(LocalDateTime.now());
        return s;
    }

    private Etablissement etablissement() {
        Etablissement e = new Etablissement();
        e.setId(1L);
        e.setNom("Lycée de l'Espoir");
        e.setCouleurPrimaire("#00236f");
        return e;
    }

    private WebContext base(boolean ouverte) {
        WebContext ctx = contexteWeb();
        ctx.setVariable("site", site(ouverte));
        ctx.setVariable("etablissement", etablissement());
        return ctx;
    }

    /** Thymeleaf echappe les apostrophes a la sortie : on compare sur le texte lisible. */
    private String lisible(String html) {
        return html.replace("&#39;", "'").replace("&amp;", "&");
    }

    @Test
    void leFormulaireSeRendEtDemandeLEssentiel() {
        WebContext ctx = base(true);
        ctx.setVariable("niveaux", List.of("6ème", "5ème", "Terminale"));

        String html = lisible(moteur().process("site-public-preinscription", ctx));

        for (String champ : List.of("nomComplet", "parentNom", "parentTelephone")) {
            assertTrue(html.contains("name=\"" + champ + "\""),
                "sans ce champ, la demande est inexploitable : " + champ);
        }
        assertTrue(html.contains("Terminale"), "les niveaux de l'ecole doivent etre proposes");
        assertTrue(html.contains("Lycée de l'Espoir"), "la famille doit reconnaitre l'etablissement");
    }

    @Test
    void lePiegeARobotsResteInvisible() {
        WebContext ctx = base(true);
        ctx.setVariable("niveaux", List.of());

        String html = moteur().process("site-public-preinscription", ctx);

        assertTrue(html.contains("name=\"siteWeb\""), "le champ piege doit etre present");
        // S'il devenait visible, chaque famille le remplirait et toutes les demandes
        // seraient silencieusement jetees.
        int champ = html.indexOf("name=\"siteWeb\"");
        String entourage = html.substring(Math.max(0, champ - 400), champ);
        assertTrue(entourage.contains("left:-9999px"), "il doit rester hors de l'ecran");
    }

    @Test
    void uneErreurDeSaisieNeVidePasLeFormulaire() {
        DemandeInscription d = new DemandeInscription();
        d.setNomComplet("DEMBA Aicha");
        d.setParentNom("DEMBA Moussa");
        d.setParentTelephone("66112233");

        WebContext ctx = base(true);
        ctx.setVariable("niveaux", List.of());
        ctx.setVariable("erreur", "Indiquez un numéro de téléphone.");
        ctx.setVariable("demande", d);

        String html = moteur().process("site-public-preinscription", ctx);

        assertTrue(html.contains("Indiquez un numéro"), "l'erreur doit etre lisible");
        // Refaire toute la saisie apres une erreur fait abandonner : c'est la premiere
        // cause de formulaire laisse en plan.
        assertTrue(html.contains("DEMBA Aicha"), "ce que la famille avait saisi doit rester");
        assertTrue(html.contains("66112233"), "le numero aussi");
    }

    @Test
    void laConfirmationDonneLaReferenceAEmporter() {
        WebContext ctx = base(true);
        ctx.setVariable("reference", "PRE-2026-4K7Q");

        String html = moteur().process("site-public-preinscription-recue", ctx);

        assertTrue(html.contains("PRE-2026-4K7Q"),
            "sans reference, un parent qui rappelle ne peut designer sa demande que par un nom");
        assertTrue(html.contains("+235 66 00 00 00"),
            "et le numero de l'ecole doit y figurer, pour qu'il puisse relancer");
    }

    @Test
    void laConfirmationTientSansReference() {
        WebContext ctx = base(true);
        ctx.setVariable("reference", null);

        // C'est le cas du piege a robots : la page doit s'afficher normalement, sans
        // reference et sans erreur, pour ne rien laisser deviner.
        String html = moteur().process("site-public-preinscription-recue", ctx);
        assertTrue(html.contains("Merci"), "la page doit rester accueillante");
        assertFalse(html.contains("null"), "aucune valeur technique ne doit transparaitre");
    }

    @Test
    void formulaireFermeLEcoleResteJoignable() {
        String html = moteur().process("site-public-preinscription-fermee", base(false));

        assertTrue(html.contains("closes"), "la famille doit comprendre que c'est temporaire");
        assertTrue(html.contains("+235 66 00 00 00"),
            "une porte fermee sans numero de telephone est une impasse");
    }

    @Test
    void leBoutonNApparaitQueSiLEcoleRecoitDesDemandes() {
        String ouvert = moteur().process("site-public-a-propos", base(true));
        String ferme = moteur().process("site-public-a-propos", base(false));

        assertTrue(ouvert.contains("/pre-inscription"),
            "quand l'ecole recrute, le bouton doit se voir sur tout le site");
        assertFalse(ferme.contains("/pre-inscription"),
            "hors periode, il enverrait les familles vers une page fermee");
    }
}
