package holyflame.administration;

import holyflame.administration.model.Classe;
import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.service.RegimeAcademiqueService;
import holyflame.administration.service.ReleveSemestrielService;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le releve remis a l'etudiant.
 *
 * C'est le seul document qu'il emporte, et celui sur lequel il contestera. S'il n'y lit
 * que la moyenne du semestre, il ne comprendra pas pourquoi il perd des credits avec plus
 * de dix de moyenne — et personne au guichet ne saura le lui expliquer.
 */
@DisplayName("Releve remis a l'etudiant")
class ReleveEtudiantTemplateTest {

    private final RegimeAcademiqueService regime = new RegimeAcademiqueService();
    private final ReleveSemestrielService service = new ReleveSemestrielService(regime);

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

    private Etablissement universite() {
        Etablissement e = new Etablissement();
        e.setNom("Universite de N'Djamena");
        e.setRegimeAcademique("LMD");
        e.setSeuilValidationUE(10.0);
        e.setCompensationSemestrielle(true);
        return e;
    }

    private Eleve etudiant() {
        Eleve e = new Eleve();
        e.setId(1L); e.setNom("NGARTA"); e.setPrenom("Josue");
        return e;
    }

    /** Le semestre du tableau 2 du document : moyenne au-dessus de dix, bloc B en dessous. */
    private ReleveSemestrielService.Releve semestreAvecUnBlocSousDix() {
        List<UniteEnseignement> unites = new ArrayList<>();
        List<ElementConstitutif> elements = new ArrayList<>();
        List<Note> notes = new ArrayList<>();
        Map<Long, String> matieres = new LinkedHashMap<>();

        long[][] table = {
            // id, credits, note x10, bloc (1 = fondamentale)
            {1, 4, 80, 0}, {2, 3, 120, 0}, {3, 6, 70, 0}, {4, 3, 140, 0},
            {5, 6, 120, 1}, {6, 6, 80, 1}, {7, 2, 150, 1},
        };
        for (long[] l : table) {
            UniteEnseignement u = new UniteEnseignement();
            u.setId(l[0]); u.setCode("UE" + l[0]); u.setIntitule("Unite " + l[0]);
            u.setCredits((int) l[1]); u.setSemestre(1);
            u.setType(l[3] == 1 ? "FONDAMENTALE" : "TRANSVERSALE");
            unites.add(u);

            ElementConstitutif ec = new ElementConstitutif();
            ec.setId(l[0]); ec.setUniteEnseignementId(l[0]);
            ec.setIntitule("Cours"); ec.setMatiereId(l[0]); ec.setCoefficient(1.0);
            elements.add(ec);

            Matiere m = new Matiere(); m.setId(l[0]);
            Note n = new Note(); n.setMatiere(m); n.setValeur(l[2] / 10.0);
            notes.add(n);
            matieres.put(l[0], "Cours " + l[0]);
        }
        return service.calculer(universite(), etudiant(), 1, unites, elements, notes, matieres);
    }

    private String rendre() {
        var releve = semestreAvecUnBlocSousDix();
        Classe classe = new Classe();
        classe.setId(1L); classe.setNom("Chimie L1");

        WebContext ctx = contexteWeb();
        ctx.setVariable("accentColor", "#00236f");
        ctx.setVariable("eleve", etudiant());
        ctx.setVariable("classe", classe);
        ctx.setVariable("parcours", null);
        ctx.setVariable("semestre", 1);
        ctx.setVariable("nbSemestres", 6);
        ctx.setVariable("nomEtab", "Universite de N'Djamena");
        ctx.setVariable("anneeScolaire", "2026-2027");
        ctx.setVariable("releve", releve);
        ctx.setVariable("bilan", service.bilanAnnuel(universite(), etudiant(), List.of(releve)));
        ctx.setVariable("semestresDeLAnnee", new int[]{1, 2});
        ctx.setVariable("utilisateurConnecte", null);
        return moteur().process("releve-notes-etudiant", ctx);
    }

    @Test
    void leReleveSeRendSansErreur() {
        String html = rendre();
        assertTrue(html.contains("NGARTA"), "le releve doit porter le nom de l'etudiant");
        assertTrue(html.length() > 800, "le rendu doit produire une page, pas un fragment vide");
    }

    @Test
    void lesMoyennesDeChaqueBlocSontLues() {
        String html = rendre();

        // Sans ces deux lignes, un etudiant a 10.07 de moyenne qui perd dix credits n'a
        // aucun moyen de comprendre pourquoi — ni personne au guichet de le lui dire.
        assertTrue(html.contains("unités fondamentales"),
            "la moyenne du bloc disciplinaire doit figurer au releve");
        assertTrue(html.contains("unités transversales"),
            "celle du bloc transversal aussi");
        assertTrue(html.contains("10,71"), "moyenne du bloc fondamental du tableau 2");
        assertTrue(html.contains("9,50"), "et celle du bloc transversal, qui explique les credits perdus");
    }

    @Test
    void leCumulDeLAnneeEstAffiche() {
        String html = rendre();

        // Le cumul annuel etait calcule et teste depuis longtemps, mais affiche nulle part :
        // il existait sans que personne ne puisse le lire.
        assertTrue(html.contains("Cumul de l"), "le jury delibere sur l'annee, pas sur un semestre");
        assertTrue(html.contains("Moyenne annuelle"), "et la moyenne annuelle doit y figurer");
    }
}
