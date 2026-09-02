package holyflame.administration;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Retard;
import holyflame.administration.model.Retenue;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.model.Zone;
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
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le surveillant saisit les retards chaque matin et encadre les retenues le soir :
 * ni les uns ni les autres n'apparaissaient sur son tableau de bord d'ouverture.
 */
class SurveillantTemplatesTest {

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
    void tableauDeBordExposeRetardsEtRetenuesDuJour() {
        Eleve e = new Eleve();
        e.setId(1L);
        e.setNom("KANE");
        e.setPrenom("Ousmane");
        Classe c = new Classe(); c.setId(1L); c.setNom("5eme B");
        e.setClasse(c);

        Retard retard = new Retard();
        retard.setId(1L);
        retard.setEleve(e);
        retard.setDate(LocalDate.of(2026, 9, 2));
        retard.setHeureArrivee(LocalTime.of(8, 15));
        retard.setMotif("Transport");

        Retenue retenue = new Retenue();
        retenue.setId(1L);
        retenue.setEleve(e);
        retenue.setDateRetenue(LocalDate.of(2026, 9, 2));
        retenue.setHeureRetenue("16:00");
        retenue.setSalle("Salle 3");
        retenue.setMotif("Bavardage repete");

        Zone z = new Zone(); z.setId(1L); z.setNom("Portail principal");

        Utilisateur u = new Utilisateur();
        u.setNom("NDIAYE"); u.setPrenom("Marie"); u.setRole("SURVEILLANT");

        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("nomEtablissement", "Ecole Test");
        ctx.setVariable("pointagesRecents", List.of());
        ctx.setVariable("incidentsRecents", List.of());
        ctx.setVariable("retenuesAVenir", List.of());
        ctx.setVariable("retenuesDuJour", List.of(retenue));
        ctx.setVariable("retardsDuJour", List.of(retard));
        ctx.setVariable("retardsAujourdHui", 1);
        ctx.setVariable("zones", List.of(z));
        ctx.setVariable("pointagesAujourdHui", 42L);
        ctx.setVariable("incidentsAujourdHui", 1L);

        String html = moteur().process("surveillant-dashboard", ctx);

        assertTrue(html.contains("Retards du jour"), "le bloc retards doit exister");
        assertTrue(html.contains("08:15"), "l'heure d'arrivee du retardataire est affichee");
        assertTrue(html.contains("A encadrer aujourd'hui"), "les retenues du jour ont leur propre bloc");
        assertTrue(html.contains("Salle 3"), "la salle de retenue est affichee");
        assertTrue(html.contains("/surveillant/retards"),
            "les compteurs ouvrent la page ou l'on agit, un chiffre seul ne sert a rien");
    }
}
