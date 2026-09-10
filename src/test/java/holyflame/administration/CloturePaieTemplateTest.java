package holyflame.administration;

import holyflame.administration.model.CloturePaieMensuelle;
import holyflame.administration.model.Personnel;
import holyflame.administration.model.SalaireMensuel;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La vue d'ensemble de la paie doit dire ou en est le mois affiche : ouvert, cloturable, ou
 * clos. Sans cet etat a l'ecran, la cloture serait un bouton dont personne ne saurait pourquoi
 * il est absent — et un mois clos ressemblerait a un mois ordinaire jusqu'au refus.
 */
class CloturePaieTemplateTest {

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

    private SalaireMensuel bulletin(String statut) {
        Personnel p = new Personnel();
        p.setId(1L); p.setNom("MAHAMAT"); p.setPrenom("Achta"); p.setStatut("ACTIF");
        SalaireMensuel s = new SalaireMensuel();
        s.setId(1L); s.setPersonnel(p); s.setMois(10); s.setAnnee(2026);
        s.setStatut(statut); s.setNetAPayer(150_000.0); s.setTotalBrut(180_000.0);
        return s;
    }

    private String rendre(boolean moisCloture, boolean peutCloturer, String obstacle,
                          boolean estAdmin, CloturePaieMensuelle cloture) {
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange);

        Utilisateur u = new Utilisateur();
        u.setNom("MAHAMAT"); u.setPrenom("Achta"); u.setRole(estAdmin ? "ADMIN" : "COMPTABLE");
        ctx.setVariable("utilisateurConnecte", u);
        ctx.setVariable("accentColor", "#00236f");

        ctx.setVariable("salaires", List.of(bulletin("PAYE")));
        ctx.setVariable("personnelSansBulletin", List.of());
        ctx.setVariable("moisFiltre", 10);
        ctx.setVariable("anneeFiltre", 2026);
        ctx.setVariable("statutFiltre", null);
        ctx.setVariable("totalEnAttente", 0.0);
        ctx.setVariable("totalPaye", 150_000.0);
        ctx.setVariable("nbEnAttente", 0L);
        ctx.setVariable("nomsMois", List.of("Janvier","Fevrier","Mars","Avril","Mai","Juin",
            "Juillet","Aout","Septembre","Octobre","Novembre","Decembre"));
        ctx.setVariable("modulesFinanceActifs", Set.of("PAIE_PREPARATION", "PAIE_PAIEMENT"));

        ctx.setVariable("moisCloture", moisCloture);
        ctx.setVariable("cloture", cloture);
        ctx.setVariable("peutCloturer", peutCloturer);
        ctx.setVariable("obstacleCloture", obstacle);
        ctx.setVariable("estAdmin", estAdmin);
        ctx.setVariable("moisClos", Set.of());

        return moteur().process("rh-salaires", ctx);
    }

    @Test
    void unMoisEntierementPayeProposeSaCloture() {
        String html = rendre(false, true, null, false, null);

        assertTrue(html.contains("Cloturer la paie du mois"), "le bouton de cloture est propose");
        assertTrue(html.contains("/rh/salaires/cloturer-mois"), "et vise l'endpoint de cloture");
        assertTrue(html.contains("Paie ouverte"), "l'etat du mois est annonce");
    }

    @Test
    void unMoisIncompletDitCeQuiManqueAuLieuDeCacherLeBouton() {
        String html = rendre(false, false, "2 bulletin(s) ne sont pas encore payes.", false, null);

        assertFalse(html.contains("Cloturer la paie du mois"),
            "on ne propose pas de cloturer un mois incomplet");
        assertTrue(html.contains("2 bulletin(s) ne sont pas encore payes"),
            "mais on explique pourquoi, plutot que de laisser deviner");
    }

    @Test
    void unMoisClosLeDitEtNommeSonAuteur() {
        CloturePaieMensuelle c = new CloturePaieMensuelle();
        c.setMois(10); c.setAnnee(2026); c.setNbBulletins(12); c.setTotalNet(1_800_000.0);
        c.setClotureePar("Achta MAHAMAT");
        c.setDateCloture(LocalDateTime.of(2026, 11, 3, 9, 15));

        String html = rendre(true, false, null, true, c);

        assertTrue(html.contains("Paie cloturee"), "l'etat clos est visible d'emblee");
        assertTrue(html.contains("12 bulletin(s) soldes"), "ce qui a ete solde est rappele");
        assertTrue(html.contains("Achta MAHAMAT"), "une cloture est une affirmation : elle a un auteur");
        assertTrue(html.contains("03/11/2026"), "et une date");
        assertFalse(html.contains("Cloturer la paie du mois"), "un mois clos ne se recloture pas");
    }

    @Test
    void seulUnAdminSeVoitProposerLaReouverture() {
        CloturePaieMensuelle c = new CloturePaieMensuelle();
        c.setMois(10); c.setAnnee(2026); c.setNbBulletins(12); c.setTotalNet(1_800_000.0);

        String pourAdmin = rendre(true, false, null, true, c);
        String pourComptable = rendre(true, false, null, false, c);

        assertTrue(pourAdmin.contains("/rh/salaires/rouvrir-mois"), "l'admin peut rouvrir");
        assertFalse(pourComptable.contains("/rh/salaires/rouvrir-mois"),
            "la comptable ne doit pas pouvoir revenir sur une paie declaree complete");
        assertTrue(pourComptable.contains("Seul un administrateur peut rouvrir"),
            "et on lui dit pourquoi le bouton n'y est pas");
    }
}
