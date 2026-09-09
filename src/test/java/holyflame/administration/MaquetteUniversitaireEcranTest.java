package holyflame.administration;

import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Parcours;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.service.MaquettePedagogiqueService.EtatSemestre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'ecran de la maquette pedagogique universitaire.
 *
 * Ce qui compte pour l'administrateur qui monte une maquette, c'est de voir tout de suite ce
 * qui l'empeche encore de deliberer : un semestre qui n'atteint pas son volume de credits, et
 * une unite d'enseignement dont aucun element n'a ete declare — sa moyenne serait incalculable.
 * Ces deux signaux sont verifies ici, ainsi que le fait que le menu ne propose jamais
 * « Gestion Academique » et « Structure academique » en meme temps.
 */
class MaquetteUniversitaireEcranTest {

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
        u.setNom("NDIAYE");
        u.setPrenom("Fatou");
        u.setRole(role);
        return u;
    }

    private Parcours parcours(long id, int nbSemestres) {
        Parcours p = new Parcours();
        p.setId(id);
        p.setLibelle("Licence Informatique");
        p.setDiplome("Licence");
        p.setNbSemestres(nbSemestres);
        p.setEtablissementId(1L);
        return p;
    }

    private UniteEnseignement unite(long id, String code, String intitule, int credits, int semestre) {
        UniteEnseignement ue = new UniteEnseignement();
        ue.setId(id);
        ue.setCode(code);
        ue.setIntitule(intitule);
        ue.setCredits(credits);
        ue.setSemestre(semestre);
        ue.setParcoursId(1L);
        ue.setEtablissementId(1L);
        return ue;
    }

    private ElementConstitutif element(long id, long uniteId, String intitule) {
        ElementConstitutif ec = new ElementConstitutif();
        ec.setId(id);
        ec.setIntitule(intitule);
        ec.setCoefficient(1.0);
        ec.setUniteEnseignementId(uniteId);
        ec.setEtablissementId(1L);
        return ec;
    }

    /**
     * Rend l'ecran pour un parcours d'un seul semestre.
     *
     * @param creditsUnite credits portes par l'unique unite du semestre
     * @param avecElement  false pour simuler une unite dont aucun element n'a ete declare
     */
    private String rendreEcran(int creditsUnite, boolean avecElement) {
        Parcours p = parcours(1L, 1);
        UniteEnseignement ue = unite(10L, "UE-S1-01", "Algorithmique", creditsUnite, 1);

        Map<Integer, List<UniteEnseignement>> unitesParSemestre = new LinkedHashMap<>();
        unitesParSemestre.put(1, List.of(ue));

        Map<Long, List<ElementConstitutif>> elementsParUnite = new LinkedHashMap<>();
        elementsParUnite.put(ue.getId(),
            avecElement ? List.of(element(100L, ue.getId(), "Travaux diriges")) : List.of());

        List<UniteEnseignement> sansElement = new ArrayList<>();
        if (!avecElement) sansElement.add(ue);

        EtatSemestre etat = new EtatSemestre(1, creditsUnite, 30, 1);
        Map<Integer, EtatSemestre> etatParSemestre = new LinkedHashMap<>();
        etatParSemestre.put(1, etat);

        WebContext ctx = contexteWeb();
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("estUniversite", true);
        ctx.setVariable("activePage", "academique-universite");
        ctx.setVariable("utilisateurConnecte", utilisateur("ADMIN"));
        ctx.setVariable("nomEtablissement", "Universite de test");
        ctx.setVariable("creditsAttendus", 30);
        ctx.setVariable("parcoursListe", List.of(p));
        ctx.setVariable("parcoursSelectionne", p);
        ctx.setVariable("unites", List.of(ue));
        ctx.setVariable("unitesParSemestre", unitesParSemestre);
        ctx.setVariable("elementsParUnite", elementsParUnite);
        ctx.setVariable("etatsSemestres", List.of(etat));
        ctx.setVariable("etatParSemestre", etatParSemestre);
        ctx.setVariable("unitesSansElement", sansElement);
        ctx.setVariable("maquetteUtilisable", creditsUnite == 30 && avecElement);
        ctx.setVariable("enseignantsDisponibles", List.of());
        ctx.setVariable("enseignantParId", Map.of());
        return moteur().process("academique-universite", ctx);
    }

    @Nested
    @DisplayName("Ce qui empeche de deliberer est visible tout de suite")
    class Controles {

        @Test
        void unSemestreIncompletAnnonceLeNombreDeCreditsManquants() {
            String html = rendreEcran(24, true);

            assertTrue(html.contains("ne peut pas encore porter une deliberation"),
                "un semestre a 24 credits sur 30 doit etre signale comme bloquant");
            assertTrue(html.contains("24"), "le total effectif doit apparaitre");
            assertTrue(html.contains("-6"),
                "l'ecart doit etre donne : l'administrateur ne doit pas avoir a le calculer");
        }

        @Test
        void uneUniteSansElementEstNommee() {
            String html = rendreEcran(30, false);

            assertTrue(html.contains("Sans element constitutif"),
                "une unite sans element rend sa moyenne incalculable, il faut le dire");
            assertTrue(html.contains("UE-S1-01"),
                "l'unite en cause doit etre nommee, sinon le message n'est pas actionnable");
        }

        @Test
        void uneMaquetteCompleteEstAnnonceeComplete() {
            String html = rendreEcran(30, true);

            assertTrue(html.contains("Maquette complete"),
                "credits atteints et unites remplies : rien ne doit plus etre signale");
            assertFalse(html.contains("ne peut pas encore porter une deliberation"),
                "aucun blocage ne doit subsister");
        }
    }

    @Nested
    @DisplayName("Le menu ne propose qu'un seul ecran academique a la fois")
    class Menu {

        private String rendreMenu(boolean estUniversite, String role) {
            WebContext ctx = contexteWeb();
            ctx.setVariable("activePage", "academique-universite");
            ctx.setVariable("estUniversite", estUniversite);
            ctx.setVariable("utilisateurConnecte", utilisateur(role));
            return moteur().process("fragments/nav-links-admin", ctx);
        }

        @Test
        void uneUniversiteVoitLaStructureAcademiqueEtPasLaGestionAcademique() {
            String html = rendreMenu(true, "ADMIN");

            assertTrue(html.contains("/academique-universite"),
                "l'entree vers la maquette doit etre proposee");
            assertFalse(html.contains("href=\"/gestion-academique\""),
                "l'ecran en classes et coefficients n'a pas de sens pour une universite");
        }

        @Test
        void uneEcoleVoitLaGestionAcademiqueEtPasLaStructureAcademique() {
            String html = rendreMenu(false, "ADMIN");

            assertTrue(html.contains("href=\"/gestion-academique\""),
                "une ecole garde son ecran de classes et de matieres");
            assertFalse(html.contains("/academique-universite"),
                "la maquette universitaire n'a pas de sens pour une ecole");
        }

        @Test
        void leCoordonnateurDUneUniversiteAtteintLaMaquetteDeSaFiliere() {
            String html = rendreMenu(true, "COORDONNATEUR");

            assertTrue(html.contains("/academique-universite"),
                "le coordonnateur suit la maquette de sa filiere : l'entree doit lui etre offerte");
        }

        /**
         * Le fragment est aussi rendu par des tests d'autres ecrans, qui ne posent pas
         * l'attribut global estUniversite. Une expression qui ne tolere pas son absence
         * y jetait une exception au lieu de se comporter comme « pas une universite ».
         */
        @Test
        void leMenuSeRendMemeSansLAttributEstUniversite() {
            WebContext ctx = contexteWeb();
            ctx.setVariable("activePage", "dashboard");
            ctx.setVariable("utilisateurConnecte", utilisateur("ADMIN"));

            String html = moteur().process("fragments/nav-links-admin", ctx);

            assertTrue(html.contains("href=\"/gestion-academique\""),
                "sans l'attribut, l'etablissement doit etre traite comme une ecole");
        }
    }
}
