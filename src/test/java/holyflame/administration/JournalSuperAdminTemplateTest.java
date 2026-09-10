package holyflame.administration;

import holyflame.administration.model.JournalAction;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le super-administrateur cree, suspend et supprime des etablissements, et reinitialise le
 * mot de passe de leurs administrateurs. Aucun de ces actes n'etait trace nulle part.
 *
 * Ils ne pouvaient pas non plus etre ecrits dans le journal de l'ecole concernee : son admin
 * y aurait lu des actions qu'il n'a pas commises, et surtout ce journal est efface avec
 * l'etablissement — la trace d'une suppression definitive aurait disparu avec ce qu'elle
 * documente. D'ou un journal a lui, alimente par des entrees rattachees a aucune ecole.
 */
class JournalSuperAdminTemplateTest {

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

    private JournalAction action(String type, String detail) {
        JournalAction a = new JournalAction();
        a.setAction(type);
        a.setModule("SUPER_ADMIN");
        a.setDetail(detail);
        a.setUtilisateurEmail("superadmin@holyflame.com");
        a.setUtilisateurNom("superadmin@holyflame.com");
        a.setDate(LocalDateTime.of(2026, 9, 10, 14, 32));
        a.setEtablissementId(null);
        return a;
    }

    private String rendre(List<JournalAction> actions, String filtre) {
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);

        ctx.setVariable("actions", actions);
        ctx.setVariable("action", filtre);
        ctx.setVariable("typesAction", List.of(
            "CREATION_ETABLISSEMENT", "REINITIALISATION_MOT_DE_PASSE_ADMIN",
            "SUPPRESSION_DEFINITIVE_ETABLISSEMENT"));
        ctx.setVariable("page", 0);
        ctx.setVariable("totalPages", 1);
        ctx.setVariable("totalActions", (long) actions.size());
        ctx.setVariable("pagesAffichees", List.of(0));
        ctx.setVariable("premierElement", actions.isEmpty() ? 0 : 1);
        ctx.setVariable("dernierElement", (long) actions.size());

        return moteur().process("super-admin/journal", ctx);
    }

    @Test
    void lesActesLesPlusLourdsSontLisiblesEtDates() {
        String html = rendre(List.of(
            action("SUPPRESSION_DEFINITIVE_ETABLISSEMENT",
                "Lycee du Sahel (code HF-2026-A1B2C3) supprime definitivement, avec 14 compte(s)"),
            action("REINITIALISATION_MOT_DE_PASSE_ADMIN",
                "College Moderne — mot de passe du compte admin@college.td reinitialise")), null);

        assertTrue(html.contains("Lycee du Sahel"), "l'ecole visee doit etre nommee");
        assertTrue(html.contains("14 compte(s)"), "l'ampleur de la suppression doit etre dite");
        assertTrue(html.contains("admin@college.td"), "le compte dont le mot de passe a change est nomme");
        assertTrue(html.contains("10/09/2026 14:32"), "chaque action est datee a la minute");
        assertTrue(html.contains("superadmin@holyflame.com"), "et rattachee au compte qui l'a faite");
    }

    @Test
    void leMotDePasseLuiMemeNEstJamaisAffiche() {
        String html = rendre(List.of(action("REINITIALISATION_MOT_DE_PASSE_ADMIN",
            "College Moderne — mot de passe du compte admin@college.td reinitialise")), null);

        assertTrue(html.contains("reinitialise"), "l'acte est trace");
        assertTrue(!html.contains("motDePasse") && !html.contains("mot de passe :"),
            "le journal dit qu'un mot de passe a change, jamais lequel");
    }

    @Test
    void unJournalVideLeDitPlutotQueDAfficherUnTableauNu() {
        String html = rendre(List.of(), null);

        assertTrue(html.contains("Aucune action enregistree pour l'instant"),
            "un tableau vide sans phrase laisse croire a une panne");
    }

    @Test
    void unFiltreSansResultatSeDistingueDUnJournalVide() {
        String html = rendre(List.of(), "SUPPRESSION_DEFINITIVE_ETABLISSEMENT");

        assertTrue(html.contains("Aucune action de ce type"),
            "ne pas laisser croire que rien n'a jamais ete fait alors qu'un filtre est actif");
    }

    @Test
    void laPageExpliquePourquoiCesActionsNeSontPasChezLesEcoles() {
        String html = rendre(List.of(), null);

        assertTrue(html.contains("n'apparaissent") || html.contains("apparaissent"),
            "la page doit dire ou ces actions ne figurent pas");
        assertTrue(html.contains("disparait avec elle"),
            "et pourquoi : le journal d'une ecole s'efface avec l'ecole");
    }
}
