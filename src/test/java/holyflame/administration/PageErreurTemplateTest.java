package holyflame.administration;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La page d'erreur portait ses trois messages dans un ternaire ou l'apostrophe de « n'avez »
 * etait doublee. Thymeleaf lit ce doublement comme la fin du litteral : le template ne se
 * parsait pas, et le moindre 403 renvoyait une page blanche a la place du message d'acces
 * refuse. C'est la page que l'on voit quand tout le reste a echoue ; elle doit tenir.
 */
class PageErreurTemplateTest {

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

    private String rendre(Integer status, String path) {
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);
        ctx.setVariable("status", status);
        ctx.setVariable("path", path);
        return moteur().process("error", ctx);
    }

    @Test
    void accesRefuseExpliqueQuIlManqueUneAutorisation() {
        String html = rendre(403, "/personnel");

        assertTrue(html.contains("Acces refuse"), "le titre annonce un acces refuse");
        assertTrue(html.contains("Vous n'avez pas les autorisations"), "le message du 403 est rendu");
        assertTrue(html.contains("/personnel"), "le chemin refuse est rappele");
        assertFalse(html.contains("existe pas ou a ete deplacee"), "le message du 404 ne doit pas s'afficher");
    }

    @Test
    void pageIntrouvableNeParlePasDAutorisation() {
        String html = rendre(404, "/frais/inconnu");

        assertTrue(html.contains("Page introuvable"), "le titre annonce une page introuvable");
        assertTrue(html.contains("La page demandee n'existe pas"), "le message du 404 est rendu");
        assertFalse(html.contains("Vous n'avez pas les autorisations"), "le message du 403 ne doit pas s'afficher");
    }

    @Test
    void statusAbsentRetombeSurLeMessageGeneral() {
        String html = rendre(null, null);

        assertTrue(html.contains("Une erreur est survenue"), "le titre general est rendu");
        assertTrue(html.contains("Une erreur inattendue est survenue"), "le message general est rendu");
        assertFalse(html.contains("Vous n'avez pas les autorisations"), "le message du 403 ne doit pas s'afficher");
    }
}
