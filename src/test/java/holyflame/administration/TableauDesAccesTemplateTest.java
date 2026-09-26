package holyflame.administration;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.service.Fonctionnalites;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'ecran ou l'on coche les acces.
 *
 * C'est la seule page depuis laquelle un etablissement decide qui fait quoi. Si elle ne
 * s'affiche pas, ou si elle n'affiche qu'une partie du catalogue, l'administrateur croit
 * avoir accorde des droits qu'il n'a pas accordes — et ne s'en apercoit que le jour ou
 * quelqu'un se heurte a un refus.
 */
@DisplayName("Tableau des acces")
class TableauDesAccesTemplateTest {

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

    private Utilisateur compte(String role) {
        Utilisateur u = new Utilisateur();
        u.setId(7L);
        u.setNom("NGARTA");
        u.setPrenom("Marthe");
        u.setRole(role);
        return u;
    }

    private String rendre(Utilisateur u, boolean personnalise) {
        WebContext ctx = contexteWeb();
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("compte", u);
        ctx.setVariable("domaines", Fonctionnalites.parDomaine());
        ctx.setVariable("actives", Fonctionnalites.effectives(u));
        ctx.setVariable("defauts", Fonctionnalites.defautsPourRole(u.getRole()));
        ctx.setVariable("personnalise", personnalise);
        ctx.setVariable("profils", List.of());
        ctx.setVariable("utilisateurConnecte", compte("ADMIN"));
        ctx.setVariable("peutFaire", Fonctionnalites.defautsPourRole("ADMIN"));
        return moteur().process("parametres-acces", ctx);
    }

    /**
     * Thymeleaf echappe les apostrophes en &amp;#39; a la sortie de th:text. On compare donc
     * sur un texte ramene a sa forme lisible, sinon tout libelle contenant une apostrophe
     * — « Assistant de cloture de fin d'annee » — passerait pour absent.
     */
    private String lisible(String html) {
        return html.replace("&#39;", "'").replace("&amp;", "&").replace("&quot;", "\"");
    }

    @Test
    void leTableauMontreToutesLesFonctionnalitesDuLogiciel() {
        String html = lisible(rendre(compte("COORDONNATEUR"), false));

        // Une seule ligne manquante est un droit que personne ne pourra jamais accorder :
        // la securite le verifiera, mais aucune case ne permettra de le donner.
        for (var f : Fonctionnalites.catalogue().values()) {
            assertTrue(html.contains("value=\"" + f.code() + "\""),
                "la fonctionnalite " + f.code() + " n'a aucune case dans le tableau");
            assertTrue(html.contains(f.libelle()),
                "la fonctionnalite " + f.code() + " est cochable sans dire ce qu'elle ouvre");
        }
    }

    @Test
    void chaqueDomaineFormeUnBlocLisible() {
        String html = rendre(compte("SECRETAIRE"), false);

        for (String domaine : Fonctionnalites.parDomaine().keySet()) {
            assertTrue(html.contains(domaine),
                "le domaine « " + domaine + " » n'apparait pas : ses lignes seraient noyees");
        }
    }

    @Test
    void lesAccesDejaAccordesArriventCoches() {
        Utilisateur secretaire = compte("SECRETAIRE");
        String html = rendre(secretaire, false);

        int ligne = html.indexOf("value=\"" + Fonctionnalites.SECRETARIAT + "\"");
        assertTrue(ligne > 0, "la ligne du secretariat doit exister");
        // On lit la balise <input> elle-meme : la case doit etre cochee, sinon enregistrer
        // sans rien toucher retirerait a cette personne ce qu'elle avait deja.
        String balise = html.substring(html.lastIndexOf("<input", ligne), html.indexOf(">", ligne) + 1);
        assertTrue(balise.contains("checked"),
            "le secretariat fait partie des acces de ce role : la case doit arriver cochee");
    }

    @Test
    void ceQuiNeSeDeleguePasEstMontreMaisVerrouille() {
        String html = rendre(compte("SECRETAIRE"), false);

        int ligne = html.indexOf("value=\"" + Fonctionnalites.PARAMETRES + "\"");
        assertTrue(ligne > 0,
            "les parametres doivent rester visibles : mieux vaut voir une porte fermee que "
            + "chercher en vain une case qui n'existe pas");

        String balise = html.substring(html.lastIndexOf("<input", ligne), html.indexOf(">", ligne) + 1);
        assertTrue(balise.contains("disabled"),
            "mais la case doit etre verrouillee : qui obtient les parametres peut ensuite "
            + "s'attribuer tout le reste");
        assertTrue(html.contains("ne se delegue pas"), "et la raison doit etre dite");
    }

    @Test
    void lEcranDitSiLeCompteSuitSonRoleOuUneListePropre() {
        assertTrue(rendre(compte("ENSEIGNANT"), false).contains("par defaut de son role"),
            "sans personnalisation, il faut dire que c'est le role qui decide");

        String personnalise = rendre(compte("ENSEIGNANT"), true);
        assertTrue(personnalise.contains("personnalises"),
            "avec une personnalisation, il faut le dire aussi");
        assertTrue(personnalise.contains("acces/reinitialiser"),
            "et offrir le retour en arriere, sinon la personnalisation est un aller simple");
    }

    @Test
    void leFormulaireRenvoieLesCodesSousLeNomAttenduParLeControleur() {
        String html = rendre(compte("ENSEIGNANT"), false);

        // Le controleur lit @RequestParam List<String> fonctionnalites. Un autre nom ici et
        // enregistrer viderait silencieusement tous les acces de la personne.
        assertTrue(html.contains("name=\"fonctionnalites\""),
            "les cases doivent etre postees sous le nom que le controleur attend");
        assertFalse(html.contains("name=\"modules\""),
            "l'ancien nom de champ ne doit plus trainer : il serait ignore sans erreur");
    }
}
