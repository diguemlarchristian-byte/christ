package holyflame.administration;

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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le tresorier ouvre /finances sur le journal de caisse. Le taux de recouvrement, le nombre
 * d'eleves en attente et le reste a encaisser etaient calcules a chaque affichage de la page
 * et n'etaient rendus dans aucun template : ce test verrouille leur presence a l'ecran.
 */
class FinancesTemplateTest {

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
    void journalDeCaisseAfficheLeRecouvrementDeLaScolarite() {
        Utilisateur u = new Utilisateur();
        u.setNom("DIALLO"); u.setPrenom("Mamadou"); u.setRole("TRESORIER");

        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("annee", "2025-2026");
        ctx.setVariable("modulesFinanceActifs", Set.of("CAISSE"));

        // Journal de caisse
        ctx.setVariable("journalSolde", 850_000.0);
        ctx.setVariable("journalTotalEntrees", 1_200_000.0);
        ctx.setVariable("journalTotalSorties", 350_000.0);
        ctx.setVariable("journalLignes", List.of());
        ctx.setVariable("journalMoisFiltre", 9);
        ctx.setVariable("journalAnneeFiltre", 2026);
        ctx.setVariable("soldeInitialCaisse", 0.0);
        ctx.setVariable("comptagesCaisse", List.of());
        ctx.setVariable("moisComptables", List.of());

        // Recouvrement (les indicateurs remis a l'ecran)
        ctx.setVariable("nbElevesPayes", 87);
        ctx.setVariable("nbElevesEnAttente", 13);
        ctx.setVariable("tauxScolaritesPayees", 87.0);
        ctx.setVariable("paiementsEnAttente", 2_450_000.0);

        // Listes de saisie
        ctx.setVariable("eleves", List.of());
        ctx.setVariable("elevesRecherche", List.of());
        ctx.setVariable("tousLesFrais", List.of());
        ctx.setVariable("categoriesCharges", List.of());
        ctx.setVariable("categoriesProduits", List.of());
        ctx.setVariable("toutesLesCategories", List.of());
        ctx.setVariable("depenses", List.of());
        ctx.setVariable("scolariteParClasse", List.of());
        ctx.setVariable("scolariteParEleve", List.of());
        ctx.setVariable("arrieresEnCours", List.of());
        ctx.setVariable("evolution", List.of());
        ctx.setVariable("maxEvolution", 1.0);
        ctx.setVariable("tauxPaie", Map.of());

        String html = moteur().process("finances", ctx);

        assertTrue(html.contains("Recouvrement de la scolarite"), "le bloc recouvrement doit etre rendu");
        assertTrue(html.contains("87"), "le nombre d'eleves a jour est affiche");
        assertTrue(html.contains("Eleves en attente"), "le nombre d'eleves en retard est affiche");
        assertTrue(html.contains("Reste a encaisser"), "le montant restant a encaisser est affiche");
    }
}
