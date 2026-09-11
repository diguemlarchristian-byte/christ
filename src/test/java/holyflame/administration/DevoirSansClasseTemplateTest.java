package holyflame.administration;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Utilisateur;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un enseignant ne peut creer un devoir que dans les classes et matieres qui lui ont ete
 * attribuees. Tant que l'administration ne l'a pas fait, ses deux menus deroulants sont vides
 * — et le formulaire s'affichait quand meme : il pouvait tout remplir, cliquer, et se heurter
 * a un refus sans comprendre que le probleme n'etait pas sa saisie.
 *
 * Un formulaire qu'on ne peut pas soumettre ne doit pas etre propose. L'ecran dit maintenant
 * ce qui manque et a qui le demander.
 */
@DisplayName("Creation d'un devoir sans classe attribuee")
class DevoirSansClasseTemplateTest {

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

    private String rendre(List<Classe> classes, List<Matiere> matieres) {
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);

        Utilisateur u = new Utilisateur();
        u.setNom("NGARTA"); u.setPrenom("Josue"); u.setRole("ENSEIGNANT");
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("classes", classes);
        ctx.setVariable("matieres", matieres);

        return moteur().process("tableau-enseignant-devoir-nouveau", ctx);
    }

    private Classe classe() {
        Classe c = new Classe();
        c.setId(1L); c.setNom("6eme A"); c.setNiveau("6eme");
        return c;
    }

    private Matiere matiere() {
        Matiere m = new Matiere();
        m.setId(1L); m.setNom("Mathematiques");
        return m;
    }

    @Test
    void sansClasseAttribueeLeFormulaireNEstPasPropose() {
        String html = rendre(List.of(), List.of());

        assertTrue(html.contains("Aucune classe ne vous est encore attribuee"),
            "l'enseignant doit comprendre que rien ne manque a sa saisie");
        assertTrue(html.contains("Demandez a"), "et savoir a qui s'adresser");
        assertFalse(html.contains("Creer le devoir"),
            "proposer un bouton qui ne peut pas aboutir revient a promettre une action impossible");
    }

    @Test
    void desQuUneClasseEstAttribueeLeFormulaireReparait() {
        String html = rendre(List.of(classe()), List.of(matiere()));

        assertTrue(html.contains("Creer le devoir"), "le formulaire revient des qu'il a de quoi etre rempli");
        assertTrue(html.contains("6eme A"), "la classe attribuee est proposee");
        assertTrue(html.contains("Mathematiques"), "la matiere aussi");
        assertFalse(html.contains("Aucune classe ne vous est encore attribuee"),
            "le message d'absence ne doit plus paraitre");
    }
}
