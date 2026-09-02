package holyflame.administration;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rend les pages du secretariat hors contexte Spring : les erreurs d'expression Thymeleaf
 * (attribut absent, appel de methode sur null, fragment introuvable) n'apparaissent qu'au
 * rendu et passeraient sinon inapercues jusqu'a l'ouverture de la page par la secretaire.
 */
class SecretariatTemplatesTest {

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

    /** Les templates utilisent des URL relatives au contexte (@{/...}), qui exigent un contexte web. */
    private WebContext contexteWeb() {
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        return new WebContext(exchange);
    }

    @Test
    void pageAbsencesAfficheLesAbsencesDuJourEtCellesEnRetard() {
        WebContext ctx = contexteWeb();
        ctx.setVariables(donneesCommunes());
        ctx.setVariable("dateAujourdHui", LocalDate.of(2026, 9, 2));
        ctx.setVariable("classeId", null);
        ctx.setVariable("absencesDuJour", List.of(
            absence(1L, eleve(10L, "NDOYE", "Awa"), LocalDate.of(2026, 9, 2), false, null),
            absence(2L, eleve(11L, "FALL", "Moussa"), LocalDate.of(2026, 9, 2), true, "Maladie")));
        ctx.setVariable("absencesEnAttente", List.of(
            absence(3L, eleve(12L, "SOW", "Bineta"), LocalDate.of(2026, 8, 28), false, null)));

        String html = moteur().process("secretariat-absences", ctx);

        assertTrue(html.contains("NDOYE"), "l'absence du jour doit apparaitre");
        assertTrue(html.contains("Maladie"), "le motif d'une absence justifiee doit apparaitre");
        assertTrue(html.contains("/secretariat/absences/1/justifier"),
            "une absence non justifiee doit exposer son formulaire de justification");
        assertFalse(html.contains("/secretariat/absences/2/justifier"),
            "une absence deja justifiee ne doit plus proposer le formulaire");
        assertTrue(html.contains("/secretariat/absences/3/justifier"),
            "une absence anterieure non justifiee reste justifiable");
    }

    @Test
    void bandeauSecretariatListeCeQuiResteATraiter() {
        WebContext ctx = contexteWeb();
        ctx.setVariables(donneesCommunes());
        ctx.setVariable("dateAujourdHui", LocalDate.of(2026, 9, 2));
        ctx.setVariable("eleves", List.of(eleve(10L, "NDOYE", "Awa")));
        ctx.setVariable("classes", List.of(classe(1L, "6eme A")));
        ctx.setVariable("absences", List.of());
        ctx.setVariable("toutesAnnees", false);
        ctx.setVariable("anneeActive", "2025-2026");
        ctx.setVariable("nbElevesAnneesPrecedentes", 0);
        ctx.setVariable("totalAbsences", 0);
        ctx.setVariable("totalEleves", 1);
        ctx.setVariable("totalClasses", 1);
        ctx.setVariable("totalAvecCompte", 0L);
        ctx.setVariable("totalInscrits", 1L);
        ctx.setVariable("totalEnAttente", 3L);
        ctx.setVariable("eleveIdsAbsentsAujourdHui", java.util.Set.of());
        ctx.setVariable("eleveIdsRetardAujourdHui", java.util.Set.of());
        ctx.setVariable("absencesAJustifier", 2L);
        ctx.setVariable("retardsAujourdHui", 0);
        ctx.setVariable("totalSansCompte", 1L);
        ctx.setVariable("messagesNonLus", 4L);

        String html = moteur().process("secretariat", ctx);

        assertTrue(html.contains("A traiter aujourd'hui"), "le bandeau doit etre rendu");
        assertTrue(html.contains("Inscription(s) a finaliser"), "les inscriptions en attente sont exposees");
        assertTrue(html.contains("Absence(s) du jour a justifier"), "les absences du jour sont exposees");
        assertTrue(html.contains("Message(s) non lu(s)"), "les messages non lus sont exposes");
        assertTrue(html.contains("/secretariat/absences"),
            "le raccourci absences pointe vers la page du secretariat, accessible a son role");
        assertFalse(html.contains("Rien en attente"), "l'etat vide ne doit pas s'afficher quand il reste du travail");
    }

    @Test
    void bandeauSecretariatAfficheUnEtatVideQuandToutEstATour() {
        WebContext ctx = contexteWeb();
        ctx.setVariables(donneesCommunes());
        ctx.setVariable("dateAujourdHui", LocalDate.of(2026, 9, 2));
        ctx.setVariable("eleves", List.of());
        ctx.setVariable("classes", List.of());
        ctx.setVariable("absences", List.of());
        ctx.setVariable("toutesAnnees", false);
        ctx.setVariable("anneeActive", "2025-2026");
        ctx.setVariable("nbElevesAnneesPrecedentes", 0);
        ctx.setVariable("totalAbsences", 0);
        ctx.setVariable("totalEleves", 0);
        ctx.setVariable("totalClasses", 0);
        ctx.setVariable("totalAvecCompte", 0L);
        ctx.setVariable("totalInscrits", 0L);
        ctx.setVariable("totalEnAttente", 0L);
        ctx.setVariable("eleveIdsAbsentsAujourdHui", java.util.Set.of());
        ctx.setVariable("eleveIdsRetardAujourdHui", java.util.Set.of());
        ctx.setVariable("absencesAJustifier", 0L);
        ctx.setVariable("retardsAujourdHui", 0);
        ctx.setVariable("totalSansCompte", 0L);
        ctx.setVariable("messagesNonLus", 0L);

        String html = moteur().process("secretariat", ctx);

        assertTrue(html.contains("Rien en attente"), "l'etat vide rassure au lieu d'afficher des compteurs a zero");
        assertFalse(html.contains("Inscription(s) a finaliser"), "aucune tuile ne doit rester quand il n'y a rien a faire");
    }

    private Map<String, Object> donneesCommunes() {
        Utilisateur u = new Utilisateur();
        u.setNom("DIOP");
        u.setPrenom("Fatou");
        u.setEmail("secretaire@ecole.sn");
        u.setRole("SECRETAIRE");
        Map<String, Object> vars = new HashMap<>();
        vars.put("utilisateurConnecte", u);
        vars.put("accentColor", "#00236f");
        vars.put("nomEtablissement", "Ecole Test");
        return vars;
    }

    private Eleve eleve(Long id, String nom, String prenom) {
        Eleve e = new Eleve();
        e.setId(id);
        e.setNom(nom);
        e.setPrenom(prenom);
        e.setMatricule("MAT" + id);
        e.setStatutInscription("INSCRIT");
        e.setClasse(classe(1L, "6eme A"));
        return e;
    }

    private Classe classe(Long id, String nom) {
        Classe c = new Classe();
        c.setId(id);
        c.setNom(nom);
        c.setAnneeScolaire("2025-2026");
        return c;
    }

    private Absence absence(Long id, Eleve eleve, LocalDate date, boolean justifiee, String motif) {
        Absence a = new Absence();
        a.setId(id);
        a.setEleve(eleve);
        a.setDate(date);
        a.setEstJustifiee(justifiee);
        a.setMotif(motif);
        return a;
    }
}
