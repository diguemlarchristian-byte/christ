package holyflame.administration;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Matiere;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le directeur ouvre /dashboard et doit pouvoir atteindre sa page de suivi : celle-ci
 * n'etait liee depuis aucune navigation, et le tableau de bord lui masquait le resume
 * budgetaire sans rien mettre a la place.
 */
class DirectionTemplatesTest {

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

    private Utilisateur utilisateur(String role) {
        Utilisateur u = new Utilisateur();
        u.setNom("SARR");
        u.setPrenom("Ibrahima");
        u.setEmail("directeur@ecole.sn");
        u.setRole(role);
        return u;
    }

    private Map<String, Object> ligneSuivi(String enseignant, String statut, long saisies, long total) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("enseignantNom", enseignant);
        l.put("matiereNom", "Mathematiques");
        l.put("classeNom", "6eme A");
        l.put("derniereSaisie", "COMPLET".equals(statut) ? LocalDateTime.of(2026, 9, 1, 10, 30) : null);
        l.put("nbNotes", saisies);
        l.put("nbElevesSaisies", saisies);
        l.put("totalEleves", total);
        l.put("statut", statut);
        return l;
    }

    @Test
    void pageSuiviAfficheLaSyntheseEtLaNavigation() {
        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", utilisateur("DIRECTEUR"));
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("nomEtablissement", "Ecole Test");
        Matiere m = new Matiere(); m.setId(1L); m.setNom("Mathematiques");
        Classe c = new Classe(); c.setId(1L); c.setNom("6eme A");
        ctx.setVariable("matieres", List.of(m));
        ctx.setVariable("classes", List.of(c));
        ctx.setVariable("filtreEnseignant", null);
        ctx.setVariable("filtreMatiere", null);
        ctx.setVariable("filtreClasse", null);
        ctx.setVariable("suiviRows", List.of(
            ligneSuivi("DIALLO Awa", "NON_REMPLI", 0, 30),
            ligneSuivi("BA Cheikh", "COMPLET", 30, 30)));
        ctx.setVariable("nbNonRempli", 1L);
        ctx.setVariable("nbEnCours", 0L);
        ctx.setVariable("nbComplet", 1L);

        String html = moteur().process("direction/suivi", ctx);

        assertTrue(html.contains("Aucune note saisie"), "la synthese doit precer le tableau");
        assertTrue(html.contains("DIALLO Awa"), "les lignes de suivi doivent etre rendues");
        assertTrue(html.contains("Non rempli"), "le statut doit etre traduit");
        assertTrue(html.contains("/direction/suivi"), "la page reste filtrable/reinitialisable");
        assertTrue(html.contains("Suivi des saisies"),
            "la barre laterale partagee doit exposer le lien, sinon la page reste inatteignable");
    }

    @Test
    void tableauDeBordDuDirecteurRemplaceLeBudgetParSonSuiviPedagogique() {
        String html = moteur().process("dashboard", contexteDashboard(utilisateur("DIRECTEUR"), 4L));

        assertTrue(html.contains("Cours sans aucune note"), "le directeur voit son indicateur de saisie");
        assertTrue(html.contains("/direction/suivi"), "et peut ouvrir le detail");
        assertFalse(html.contains("Resume budgetaire"), "le budget reste hors de son perimetre");
    }

    @Test
    void tableauDeBordDeLAdminGardeLeResumeBudgetaire() {
        String html = moteur().process("dashboard", contexteDashboard(utilisateur("ADMIN"), 0L));

        assertTrue(html.contains("Resume budgetaire"), "l'admin conserve le bloc financier");
        assertFalse(html.contains("Cours sans aucune note"), "l'indicateur directeur ne le concerne pas");
    }

    private WebContext contexteDashboard(Utilisateur u, long saisiesNonRemplies) {
        WebContext ctx = contexteWeb();
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("nomEtablissement", "Ecole Test");
        ctx.setVariable("alertes", List.of());
        ctx.setVariable("totalEleves", 120L);
        ctx.setVariable("totalClasses", 6);
        ctx.setVariable("totalAbsences", 12L);
        ctx.setVariable("totalPersonnels", 18L);
        ctx.setVariable("totalEncaisse", 1_500_000.0);
        ctx.setVariable("budgetRevenu", 2_000_000.0);
        ctx.setVariable("budgetDepense", 1_200_000.0);
        ctx.setVariable("anneeScolaire", "2025-2026");
        ctx.setVariable("paiementsLabels", List.of("Jan","Fev","Mar","Avr","Mai","Jun","Jul","Aou","Sep","Oct","Nov","Dec"));
        ctx.setVariable("paiementsData", List.of(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,100.0,0.0,0.0,0.0));
        ctx.setVariable("maxPaiementMois", 100.0);
        ctx.setVariable("moisCourantIndex", 8);
        ctx.setVariable("totalEncaisseMois", 100.0);
        ctx.setVariable("absencesData", List.of(0L,0L,0L,0L,0L,0L,0L,0L,3L,0L,0L,0L));
        ctx.setVariable("maxAbsences", 3L);
        ctx.setVariable("notesMentions", List.of(2L,5L,8L,10L,3L));
        ctx.setVariable("totalNotes", 28L);
        ctx.setVariable("saisiesNonRemplies", saisiesNonRemplies);
        return ctx;
    }
}
