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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'infirmier doit savoir qu'un article est epuise avant de recevoir un eleve : le niveau
 * de stock n'etait visible qu'en bas de page, dans une liste triee par ordre alphabetique.
 */
class InfirmerieTemplatesTest {

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

    private ArticleInfirmerie article(String designation, int actuelle, int max) {
        ArticleInfirmerie a = new ArticleInfirmerie();
        a.setDesignation(designation);
        a.setQuantiteActuelle(actuelle);
        a.setQuantiteMax(max);
        a.setUnite("boite");
        return a;
    }

    @Test
    void tableauDeBordSignaleLesStocksCritiques() {
        Utilisateur u = new Utilisateur();
        u.setNom("BA"); u.setPrenom("Aissatou"); u.setRole("INFIRMIER");

        Map<String, String> motifs = new LinkedHashMap<>();
        motifs.put("MALAISE", "Malaise");
        Map<String, String> orientations = new LinkedHashMap<>();
        orientations.put("RETOUR_CLASSE", "Retour en classe");

        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("nomEtablissement", "Ecole Test");
        ctx.setVariable("visitesAujourdhui", 5);
        ctx.setVariable("enObservation", List.of());
        ctx.setVariable("alertesActives", List.of());
        ctx.setVariable("consultationsRecentes", List.of());
        ctx.setVariable("conditionsParEleve", Map.of());
        ctx.setVariable("frequence", List.of(Map.of("jour", "Lun", "total", 3L)));
        ctx.setVariable("maxFrequence", 3L);
        ctx.setVariable("stocks", List.of(article("Paracetamol", 1, 50), article("Pansements", 40, 50)));
        ctx.setVariable("stocksCritiques", 1L);
        ctx.setVariable("paiCounts", Map.of());
        ctx.setVariable("motifs", motifs);
        ctx.setVariable("orientations", orientations);

        String html = moteur().process("infirmerie-dashboard", ctx);

        assertTrue(html.contains("Stock a reapprovisionner"), "le compteur de stock critique doit etre en tete");
        assertTrue(html.contains("/infirmerie/stock"), "et ouvrir la page de gestion du stock");
        int posParacetamol = html.indexOf("Paracetamol");
        int posPansements = html.indexOf("Pansements");
        assertTrue(posParacetamol > 0 && posPansements > 0 && posParacetamol < posPansements,
            "le template rend les stocks dans l'ordre recu (le controleur les trie par criticite)");
    }
}
