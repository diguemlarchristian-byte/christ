package holyflame.administration;

import holyflame.administration.model.CritereAdmission;
import holyflame.administration.model.DemandeInscription;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.service.EtudeDossierService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les ecrans de la commission.
 *
 * Ce sont eux qu'on ouvre devant un dossier a trancher. Une page qui tombe a ce
 * moment-la ne coute pas un ecran : elle coute la seance, et la commission se remet au
 * papier — d'ou elle ne reviendra pas.
 */
@DisplayName("Ecrans de l'etude de dossier")
class EtudeDossierTemplateTest {

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

    /** Thymeleaf echappe les apostrophes a la sortie : on compare sur le texte lisible. */
    private String lisible(String html) {
        return html.replace("&#39;", "'").replace("&amp;", "&");
    }

    private Utilisateur secretaire() {
        Utilisateur u = new Utilisateur();
        u.setId(1L); u.setNom("MAHAMAT"); u.setPrenom("Fatimé"); u.setRole("SECRETAIRE");
        return u;
    }

    private CritereAdmission critere(long id, String libelle, double poids, boolean actif) {
        CritereAdmission c = new CritereAdmission();
        c.setId(id); c.setEtablissementId(1L); c.setLibelle(libelle);
        c.setPoids(poids); c.setOrdre((int) id); c.setActif(actif);
        return c;
    }

    private DemandeInscription demande(String statut, Double note) {
        DemandeInscription d = new DemandeInscription();
        d.setId(1L); d.setEtablissementId(1L);
        d.setReference("PRE-2026-4K7Q");
        d.setNomComplet("DEMBA Aicha");
        d.setParentNom("DEMBA Moussa");
        d.setParentTelephone("66112233");
        d.setAnneeScolaire("2026-2027");
        d.setDateDemande(LocalDateTime.now());
        d.setStatut(statut);
        d.setNoteDossier(note);
        return d;
    }

    private WebContext contexte(DemandeInscription d, EtudeDossierService.Dossier dossier) {
        WebContext ctx = contexteWeb();
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("utilisateurConnecte", secretaire());
        ctx.setVariable("peutFaire", Set.of("PREINSCRIPTIONS", "SECRETARIAT"));
        ctx.setVariable("demande", d);
        ctx.setVariable("dossier", dossier);
        return ctx;
    }

    @Test
    void laGrilleSAfficheAvecSesCoefficients() {
        var d = demande(DemandeInscription.NOUVELLE, 13.0);
        var dossier = new EtudeDossierService.Dossier(d, List.of(
            new EtudeDossierService.LigneGrille(critere(1, "Bulletin de l'année précédente", 2.0, true), 15.0),
            new EtudeDossierService.LigneGrille(critere(2, "Entretien", 1.0, true), 9.0)
        ), 13.0, 2, 2);

        String html = lisible(moteur().process("secretariat-demande-detail", contexte(d, dossier)));

        assertTrue(html.contains("Bulletin de l'année précédente"), "le critère doit être nommé");
        assertTrue(html.contains("coefficient 2"), "et son poids visible, sinon le total est incompréhensible");
        assertTrue(html.contains("name=\"note_1\""), "chaque ligne doit être saisissable");
        assertTrue(html.contains("13,00 / 20"), "le total doit s'afficher");
    }

    @Test
    void uneGrilleAMoitieRemplieLeDitFranchement() {
        var d = demande(DemandeInscription.NOUVELLE, 15.0);
        var dossier = new EtudeDossierService.Dossier(d, List.of(
            new EtudeDossierService.LigneGrille(critere(1, "Bulletin", 1.0, true), 15.0),
            new EtudeDossierService.LigneGrille(critere(2, "Entretien", 1.0, true), null)
        ), 15.0, 1, 2);

        String html = lisible(moteur().process("secretariat-demande-detail", contexte(d, dossier)));

        // Un total fonde sur la moitie de la grille classe le dossier comme s'il etait
        // complet. Sans avertissement, personne ne s'en apercoit.
        assertTrue(html.contains("1 critère(s) notés sur 2"),
            "il faut dire sur quoi porte réellement le total");
    }

    @Test
    void unCritereRetireResteLisibleAvecSaRaison() {
        var d = demande(DemandeInscription.NOUVELLE, 16.0);
        var dossier = new EtudeDossierService.Dossier(d, List.of(
            new EtudeDossierService.LigneGrille(critere(1, "Test d'entrée", 1.0, false), 16.0)
        ), 16.0, 1, 1);

        String html = lisible(moteur().process("secretariat-demande-detail", contexte(d, dossier)));

        assertTrue(html.contains("Critère retiré de la grille"),
            "sinon on lit une note sans comprendre d'où elle vient");
        assertTrue(html.contains("disabled"), "et on ne doit plus pouvoir la modifier");
    }

    @Test
    void lesTroisDecisionsSontOffertes() {
        var d = demande(DemandeInscription.NOUVELLE, null);
        var dossier = new EtudeDossierService.Dossier(d, List.of(), null, 0, 0);

        String html = lisible(moteur().process("secretariat-demande-detail", contexte(d, dossier)));

        for (String decision : List.of("ADMIS", "LISTE_ATTENTE", "REFUSEE")) {
            assertTrue(html.contains("value=\"" + decision + "\""),
                "la commission doit pouvoir prononcer : " + decision);
        }
        assertTrue(html.contains("n'inscrit personne"),
            "et lire que l'admission ne crée pas l'élève");
    }

    @Test
    void sansGrilleDefinieAucunTableauNEstMontre() {
        var d = demande(DemandeInscription.NOUVELLE, null);
        var dossier = new EtudeDossierService.Dossier(d, List.of(), null, 0, 0);

        String html = moteur().process("secretariat-demande-detail", contexte(d, dossier));

        // Une ecole qui inscrit tout le monde n'a rien a noter. Lui montrer un tableau
        // vide lui ferait croire qu'elle a oublie un reglage.
        assertFalse(html.contains("Étude du dossier"),
            "pas de grille définie, pas de tableau de notation");
    }

    @Test
    void laListeDAttenteSeRendDansLOrdre() {
        var premier = demande(DemandeInscription.LISTE_ATTENTE, 16.0);
        var second = demande(DemandeInscription.LISTE_ATTENTE, 9.0);
        second.setId(2L);
        second.setNomComplet("KOSSI Jean");

        WebContext ctx = contexteWeb();
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("utilisateurConnecte", secretaire());
        ctx.setVariable("peutFaire", Set.of("PREINSCRIPTIONS"));
        ctx.setVariable("demandes", List.of(premier, second));
        ctx.setVariable("statut", "TOUTES");
        ctx.setVariable("nbEnAttente", 0L);
        ctx.setVariable("listeDAttente", List.of(
            new EtudeDossierService.Rang(1, premier, 16.0),
            new EtudeDossierService.Rang(2, second, 9.0)));

        String html = lisible(moteur().process("secretariat-demandes", ctx));

        assertTrue(html.contains("Liste d'attente"), "la file doit apparaître");
        assertTrue(html.indexOf("DEMBA Aicha") < html.indexOf("KOSSI Jean"),
            "la meilleure note doit être rappelée en premier");
        assertTrue(html.contains("16,00 / 20"), "avec la note qui justifie le rang");
    }

    @Test
    void laPageDeLaGrilleSeRendEtExpliqueLeRetrait() {
        WebContext ctx = contexteWeb();
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("utilisateurConnecte", secretaire());
        ctx.setVariable("peutFaire", Set.of("PREINSCRIPTIONS"));
        ctx.setVariable("criteres", List.of(
            critere(1, "Bulletin", 2.0, true),
            critere(2, "Test d'entrée", 1.0, false)));

        String html = lisible(moteur().process("secretariat-criteres-admission", ctx));

        assertTrue(html.contains("Bulletin"), "la grille doit se lire");
        assertTrue(html.contains("coefficient 2.0") || html.contains("coefficient 2"),
            "avec le poids de chaque ligne");
        assertTrue(html.contains("retiré"), "et l'état de celles qu'on a retirées");
        assertTrue(html.contains("name=\"libelle\""), "on doit pouvoir en ajouter une");
    }
}
