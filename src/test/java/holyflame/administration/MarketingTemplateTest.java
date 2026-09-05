package holyflame.administration;

import holyflame.administration.model.SiteVitrine;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le compteur d'evenements du tableau de bord Marketing s'intitulait "Evenements a venir"
 * mais comptait aussi les evenements passes, que le site public masque deja. Et une
 * actualite laissee en brouillon n'etait signalee par aucun compteur.
 */
class MarketingTemplateTest {

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

    @Test
    void tableauDeBordSignaleBrouillonsEtEvenementsPasses() {
        Utilisateur u = new Utilisateur();
        u.setNom("FALL"); u.setPrenom("Seynabou"); u.setRole("MARKETING");

        SiteVitrine site = new SiteVitrine();
        site.setSlug("ecole-test");
        site.setActif(true);

        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("nomEtablissement", "Ecole Test");
        ctx.setVariable("site", site);
        ctx.setVariable("actualitesRecentes", List.of());
        ctx.setVariable("nbPhotos", 24);
        ctx.setVariable("nbEvenements", 2L);
        ctx.setVariable("nbEvenementsPasses", 10L);
        ctx.setVariable("nbBrouillons", 3L);

        String html = moteur().process("marketing-dashboard", ctx);

        assertTrue(html.contains("Actualités en brouillon"), "le compteur de brouillons doit exister");
        assertTrue(html.contains("plus visible(s) du public"),
            "les evenements passes sont distingues de ceux que le public voit");
        assertTrue(html.contains("/marketing/actualites"), "le compteur ouvre la page des actualites");
    }
}
