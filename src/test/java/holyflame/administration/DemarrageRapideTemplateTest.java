package holyflame.administration;

import holyflame.administration.controller.DemarrageRapideController;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/** L'ecran de creation en une minute : quatre champs, et le detail de ce qui sera cree. */
class DemarrageRapideTemplateTest {

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
    void lEcranPresenteLesTypesEtAnnonceLesClassesCreees() {
        WebContext ctx = contexteWeb();
        ctx.setVariable("types", DemarrageRapideController.TYPES);

        String html = moteur().process("demarrage-rapide", ctx);

        assertTrue(html.contains("Ecole primaire"), "les types d'etablissement sont proposes");
        assertTrue(html.contains("Groupe scolaire complet"));
        assertTrue(html.contains("6 classes creees"),
            "chaque type annonce le nombre de classes, pour qu'on sache ce qu'on obtient");
        assertTrue(html.contains("/inscription-ecole"),
            "l'assistant detaille reste accessible pour qui veut tout regler");
        assertTrue(html.contains("Deja regle pour vous"),
            "l'ecran doit dire ce qu'il decide a la place de l'utilisateur");
    }

    @Test
    void uneSaisieRefuseeEstReaffichee() {
        WebContext ctx = contexteWeb();
        ctx.setVariable("types", DemarrageRapideController.TYPES);
        ctx.setVariable("erreur", "Un compte existe deja avec cet email.");
        ctx.setVariable("nomEcole", "Ecole Les Baobabs");
        ctx.setVariable("typeEcole", "PRIMAIRE");
        ctx.setVariable("adminNomComplet", "Moussa SARR");
        ctx.setVariable("adminEmail", "direction@baobabs.test");

        String html = moteur().process("demarrage-rapide", ctx);

        assertTrue(html.contains("Un compte existe deja avec cet email."), "l'erreur est montree");
        // Recommencer une saisie de zero est le pire du "stress" que cet ecran doit supprimer.
        assertTrue(html.contains("Ecole Les Baobabs"), "le nom saisi est conserve");
        assertTrue(html.contains("Moussa SARR"), "le nom de l'administrateur est conserve");
        assertTrue(html.contains("direction@baobabs.test"), "l'email est conserve");
    }
}
