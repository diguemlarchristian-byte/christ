package holyflame.administration;

import holyflame.administration.controller.DemarrageRapideController;
import holyflame.administration.controller.InscriptionEcoleController.DonneesInscription;
import holyflame.administration.model.Etablissement;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.service.CreationEcoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'interface suit le type d'etablissement choisi a la creation.
 *
 * Une universite n'a ni trimestres, ni professeurs titulaires, ni passage de classe. Laisser
 * ces reglages visibles ferait croire qu'ils s'appliquent, et un administrateur passerait du
 * temps a configurer des devoirs trimestriels qui ne seront jamais utilises.
 */
@SpringBootTest
@Transactional
class AdaptationUniversitaireTest {

    @Autowired private CreationEcoleService creationEcoleService;
    @Autowired private EtablissementRepository etablissementRepository;

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Le regime se deduit des niveaux choisis")
    class DeductionDuRegime {

        private DonneesInscription depuisType(String nom, String codeType) {
            DemarrageRapideController.TypeEcole type = DemarrageRapideController.TYPES.get(codeType);
            DonneesInscription d = new DonneesInscription();
            d.nom = nom;
            d.categorie = codeType;
            d.niveaux = type.libelle();
            d.niveauxSelectionnes = new ArrayList<>(type.niveaux());
            return d;
        }

        private Etablissement creerEtRelire(String nom, String codeType, String email) {
            creationEcoleService.creer(depuisType(nom, codeType), "Test USER", email, "ADMIN");
            return etablissementRepository.findAll().stream()
                .filter(e -> nom.equals(e.getNom())).findFirst().orElseThrow();
        }

        @Test
        void choisirEnseignementSuperieurCreeUneUniversite() {
            // Sans cela, il faudrait redire dans Parametres ce qu'on vient de choisir a l'ecran
            // precedent — et l'universite fonctionnerait en trimestres en attendant.
            Etablissement u = creerEtRelire("Universite Auto LMD", "SUPERIEUR", "auto-lmd@test.sn");

            assertEquals("LMD", u.getRegimeAcademique());
            assertTrue(u.estRegimeLMD());
        }

        @Test
        void unePrimaireResteEnRegimeScolaire() {
            Etablissement e = creerEtRelire("Ecole Auto Scolaire", "PRIMAIRE", "auto-scol@test.sn");

            assertEquals("SCOLAIRE", e.getRegimeAcademique());
            assertFalse(e.estRegimeLMD());
        }

        @Test
        void unGroupeScolaireCompletResteEnRegimeScolaire() {
            // Il va de la maternelle a la terminale : ses classes ont besoin de bulletins
            // trimestriels, le LMD les en priverait.
            Etablissement g = creerEtRelire("Groupe Auto Complet", "GROUPE_SCOLAIRE", "auto-gs@test.sn");

            assertEquals("SCOLAIRE", g.getRegimeAcademique());
        }

        @Test
        void unEtablissementMixteSuperieurEtLyceeResteScolaire() {
            DonneesInscription mixte = new DonneesInscription();
            mixte.nom = "Institut Mixte";
            mixte.niveauxSelectionnes = List.of("LYCEE_TERMINALE", "SUPERIEUR_L1", "SUPERIEUR_L2");

            creationEcoleService.creer(mixte, "Test USER", "auto-mixte@test.sn", "ADMIN");
            Etablissement m = etablissementRepository.findAll().stream()
                .filter(e -> "Institut Mixte".equals(e.getNom())).findFirst().orElseThrow();

            assertEquals("SCOLAIRE", m.getRegimeAcademique(),
                "un seul niveau non universitaire suffit a exiger le regime scolaire");
        }

        @Test
        void sansNiveauChoisiLeRegimeResteScolaire() {
            DonneesInscription sansNiveau = new DonneesInscription();
            sansNiveau.nom = "Etablissement Sans Niveau";

            creationEcoleService.creer(sansNiveau, "Test USER", "auto-vide@test.sn", "ADMIN");
            Etablissement s = etablissementRepository.findAll().stream()
                .filter(e -> "Etablissement Sans Niveau".equals(e.getNom())).findFirst().orElseThrow();

            assertEquals("SCOLAIRE", s.getRegimeAcademique(), "le defaut ne change jamais sans raison");
        }
    }

    // ────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Les ecrans n'affichent que ce qui concerne l'etablissement")
    class AffichageConditionnel {

        private String rendre(String template, boolean estUniversite) {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("UTF-8");
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);

            MockServletContext servletContext = new MockServletContext();
            JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
            IServletWebExchange exchange = application.buildExchange(
                new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
            WebContext ctx = new WebContext(exchange);

            ctx.setVariable("estUniversite", estUniversite);
            ctx.setVariable("p", new java.util.HashMap<String, String>());
            ctx.setVariable("etablissement", etablissementUniversitaire(estUniversite));
            ctx.setVariable("utilisateurConnecte", null);
            ctx.setVariable("activePage", "parametres");
            // Le template lit ces collections directement : les omettre ferait echouer le rendu
            // sur un NullPointer, pas sur ce que ce test cherche a verifier.
            ctx.setVariable("frais", java.util.List.of());
            ctx.setVariable("enseignantsDisponibles", java.util.List.of());
            ctx.setVariable("classes", java.util.List.of());
            ctx.setVariable("matieres", java.util.List.of());
            ctx.setVariable("autorisations", java.util.List.of());
            ctx.setVariable("autorisationsAffichage", java.util.List.of());
            return engine.process(template, ctx);
        }

        private Etablissement etablissementUniversitaire(boolean lmd) {
            Etablissement e = new Etablissement();
            e.setNom("Etablissement de test");
            e.setRegimeAcademique(lmd ? "LMD" : "SCOLAIRE");
            return e;
        }

        @Test
        void uneUniversiteNeVoitPasLesReglagesTrimestriels() {
            String html = rendre("parametres", true);

            assertFalse(html.contains("NB_DEVOIRS_PAR_TRIMESTRE"),
                "les devoirs par trimestre n'existent pas en regime universitaire");
            assertFalse(html.contains("BONUS_PARTICIPATION_TP_SEUIL"),
                "le bonus de participation est un dispositif scolaire");
            assertFalse(html.contains("id=\"config-outils\""),
                "une universite organise des filieres, pas des classes standards");
            assertFalse(html.contains("id=\"config-autorisations\""),
                "l'affectation par classe n'a pas d'equivalent universitaire");
        }

        @Test
        void uneEcoleVoitTousSesReglages() {
            String html = rendre("parametres", false);

            assertTrue(html.contains("NB_DEVOIRS_PAR_TRIMESTRE"));
            assertTrue(html.contains("BONUS_PARTICIPATION_TP_SEUIL"));
            assertTrue(html.contains("id=\"config-outils\""));
            assertTrue(html.contains("id=\"config-autorisations\""));
        }

        @Test
        void leRegimeAcademiqueResteReglableDesDeuxCotes() {
            // C'est le seul moyen de corriger une deduction qui ne conviendrait pas :
            // il ne doit jamais disparaitre.
            assertTrue(rendre("parametres", true).contains("Regime academique"));
            assertTrue(rendre("parametres", false).contains("Regime academique"));
        }
    }
}
