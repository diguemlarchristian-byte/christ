package holyflame.administration;

import holyflame.administration.model.Personnel;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le tableau d'evolution du coordonnateur se construit a partir des fiches de controle :
 * un enseignant jamais visite n'y figurait donc pas du tout, alors que c'est precisement
 * celui qu'il reste a programmer.
 */
class CoordinationTemplateTest {

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

    private Personnel enseignant(long id, String nom, String prenom) {
        Personnel p = new Personnel();
        p.setId(id);
        p.setNom(nom);
        p.setPrenom(prenom);
        p.setFonction("ENSEIGNANT");
        return p;
    }

    @Test
    void accueilSignaleLesEnseignantsASuivreEtCeuxJamaisVisites() {
        Personnel visite = enseignant(1L, "SOW", "Fatou");
        Personnel jamaisVu = enseignant(2L, "TRAORE", "Ibrahim");

        Map<String, Object> ligneEnBaisse = new LinkedHashMap<>();
        ligneEnBaisse.put("enseignant", visite);
        ligneEnBaisse.put("nbVisites", 2);
        ligneEnBaisse.put("derniereNote", "INSUFFISANT");
        ligneEnBaisse.put("derniereDate", LocalDate.of(2026, 6, 12));
        ligneEnBaisse.put("tendance", "BAISSE");
        ligneEnBaisse.put("historique", List.of());

        Utilisateur u = new Utilisateur();
        u.setNom("CISSE"); u.setPrenom("Awa"); u.setRole("COORDONNATEUR");

        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("nomEtablissement", "Ecole Test");
        ctx.setVariable("fiches", List.of());
        ctx.setVariable("nbFiches", 2);
        ctx.setVariable("nbEnseignantsVisites", 1L);
        ctx.setVariable("evolutionEnseignants", List.of(ligneEnBaisse));
        ctx.setVariable("enseignantsASuivre", List.of(ligneEnBaisse));
        ctx.setVariable("enseignantsJamaisVisites", List.of(jamaisVu));
        ctx.setVariable("qualiteNotes", List.of());
        ctx.setVariable("avisRecurrents", List.of());

        String html = moteur().process("coordination", ctx);

        assertTrue(html.contains("Enseignants a suivre en priorite"),
            "les enseignants en difficulte doivent etre isoles, pas noyes dans le tableau");
        assertTrue(html.contains("Enseignants jamais visites"),
            "les enseignants sans aucune fiche doivent apparaitre");
        assertTrue(html.contains("TRAORE"),
            "l'enseignant jamais visite est nomme : il n'existait sur aucun ecran auparavant");
        assertTrue(html.contains("Jamais visites"), "le compteur est visible avant les onglets");
    }
}
