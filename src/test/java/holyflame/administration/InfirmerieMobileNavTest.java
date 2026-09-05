package holyflame.administration;

import holyflame.administration.model.ArticleInfirmerie;
import holyflame.administration.model.Utilisateur;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les pages Infirmerie ne portaient qu'une barre laterale "hidden md:flex" — et les pages
 * secondaires aucune : sous 768px l'infirmier perdait toute navigation entre le tableau de
 * bord, les consultations, les PAI et le stock.
 */
class InfirmerieMobileNavTest {

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

    private WebContext contexteCommun() {
        Utilisateur u = new Utilisateur();
        u.setNom("BA"); u.setPrenom("Aissatou"); u.setRole("INFIRMIER");
        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("nomEtablissement", "Ecole Test");
        return ctx;
    }

    private void verifierBarreMobile(String html, String page) {
        assertTrue(html.contains("/infirmerie/consultations"),
            page + " : la barre mobile doit mener aux consultations");
        assertTrue(html.contains("/infirmerie/pai"), page + " : ... aux PAI");
        assertTrue(html.contains("/infirmerie/stock"), page + " : ... au stock");
        assertTrue(html.contains("infirmerie-menu-mobile"),
            page + " : le panneau Plus (assistant, deconnexion) doit exister");
    }

    @Test
    void laPageStockPorteLaBarreDeNavigationMobile() {
        ArticleInfirmerie a = new ArticleInfirmerie();
        a.setId(1L);
        a.setDesignation("Paracetamol");
        a.setQuantiteActuelle(5);
        a.setQuantiteMax(50);
        a.setUnite("boite");

        WebContext ctx = contexteCommun();
        ctx.setVariable("stocks", List.of(a));

        String html = moteur().process("infirmerie-stock", ctx);
        verifierBarreMobile(html, "stock");
        assertTrue(html.contains("pb-20"),
            "stock : le contenu doit reserver la hauteur de la barre, sinon elle recouvre le bas de page");
    }

    @Test
    void laPagePaiPorteLaBarreDeNavigationMobile() {
        WebContext ctx = contexteCommun();
        ctx.setVariable("conditions", List.of());
        ctx.setVariable("eleves", List.of());

        verifierBarreMobile(moteur().process("infirmerie-pai", ctx), "pai");
    }

    @Test
    void laPageConsultationsPorteLaBarreDeNavigationMobile() {
        WebContext ctx = contexteCommun();
        ctx.setVariable("consultations", List.of());
        ctx.setVariable("motifs", Map.of("MALAISE", "Malaise"));
        ctx.setVariable("orientations", Map.of("RETOUR_CLASSE", "Retour en classe"));

        verifierBarreMobile(moteur().process("infirmerie-historique", ctx), "consultations");
    }

    @Test
    void leTableauDeBordPorteLaBarreDeNavigationMobile() {
        WebContext ctx = contexteCommun();
        ctx.setVariable("visitesAujourdhui", 0);
        ctx.setVariable("enObservation", List.of());
        ctx.setVariable("alertesActives", List.of());
        ctx.setVariable("consultationsRecentes", List.of());
        ctx.setVariable("conditionsParEleve", Map.of());
        ctx.setVariable("frequence", List.of(Map.of("jour", "Lun", "total", 0L)));
        ctx.setVariable("maxFrequence", 1L);
        ctx.setVariable("stocks", List.of());
        ctx.setVariable("stocksCritiques", 0L);
        ctx.setVariable("paiCounts", Map.of());
        ctx.setVariable("motifs", Map.of("MALAISE", "Malaise"));
        ctx.setVariable("orientations", Map.of("RETOUR_CLASSE", "Retour en classe"));

        verifierBarreMobile(moteur().process("infirmerie-dashboard", ctx), "dashboard");
    }
}
