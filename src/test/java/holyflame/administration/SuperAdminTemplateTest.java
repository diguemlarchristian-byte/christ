package holyflame.administration;

import holyflame.administration.model.Etablissement;
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
 * Un etablissement sans compte ADMIN est inutilisable : personne ne peut s'y connecter pour
 * l'administrer. Le tableau se contentait d'un discret "--" dans la colonne Admin, qui ne
 * distingue pas une panne bloquante d'une donnee manquante anodine.
 */
class SuperAdminTemplateTest {

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

    private Etablissement etablissement(long id, String nom) {
        Etablissement e = new Etablissement();
        e.setId(id);
        e.setNom(nom);
        e.setCodeAcces("CODE" + id);
        e.setStatut("ACTIF");
        e.setPlanAbonnement("GRATUIT");
        return e;
    }

    @Test
    void unEtablissementSansAdminEstSignaleCommeBloquant() {
        Etablissement avecAdmin = etablissement(1L, "Lycee Saint-Michel");
        Etablissement sansAdmin = etablissement(2L, "College Les Palmiers");

        Utilisateur admin = new Utilisateur();
        admin.setId(10L);
        admin.setNom("SARR");
        admin.setPrenom("Moussa");
        admin.setRole("ADMIN");

        WebContext ctx = contexteWeb();
        ctx.setVariable("etablissements", List.of(avecAdmin, sansAdmin));
        ctx.setVariable("corbeille", List.of());
        ctx.setVariable("adminParEtab", Map.of(1L, admin));
        ctx.setVariable("nbElevesParEtab", Map.of(1L, 120L, 2L, 0L));
        ctx.setVariable("nbPersonnelsParEtab", Map.of(1L, 14L, 2L, 0L));
        ctx.setVariable("totalElevesGlobal", 120L);
        ctx.setVariable("totalEtablissements", 2);
        ctx.setVariable("totalActifs", 2L);
        ctx.setVariable("totalUtilisateurs", 1);
        ctx.setVariable("tousUtilisateurs", List.of(admin));
        ctx.setVariable("etablissementsSansAdmin", List.of(sansAdmin));

        String html = moteur().process("super-admin/dashboard", ctx);

        assertTrue(html.contains("sans compte administrateur"),
            "l'alerte doit nommer la panne, pas la laisser deviner");
        assertTrue(html.contains("College Les Palmiers"),
            "l'etablissement bloque est nomme dans l'alerte");
        assertTrue(html.contains("Aucun admin"),
            "la ligne du tableau le signale aussi, au lieu d'un simple tiret");
    }
}
