package holyflame.administration.controller;

import holyflame.administration.service.DocumentsComptablesService;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Grand livre et balance generale.
 *
 * Les deux documents qu'un expert-comptable reclame en premier, et que le logiciel ne savait
 * pas produire : il fallait les reconstituer sous Excel a partir de l'export des paiements.
 */
@Controller
@RequestMapping("/comptabilite")
public class DocumentsComptablesController {

    @Autowired private DocumentsComptablesService documents;
    @Autowired private EtablissementService etablissementService;

    @GetMapping("/grand-livre")
    public String grandLivre(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false, defaultValue = "false") boolean tousLesComptes,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDate[] periode = periode(debut, fin);

        model.addAttribute("comptes", documents.grandLivre(etabId, periode[0], periode[1], tousLesComptes));
        model.addAttribute("tousLesComptes", tousLesComptes);
        return preparer(model, periode, "grand-livre", "documents-grand-livre");
    }

    @GetMapping("/balance")
    public String balance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDate[] periode = periode(debut, fin);

        model.addAttribute("balance", documents.balance(etabId, periode[0], periode[1]));
        return preparer(model, periode, "balance", "documents-balance");
    }

    /**
     * Periode par defaut : l'annee civile en cours.
     *
     * Une periode ouverte afficherait tout l'historique et deviendrait illisible des la deuxieme
     * annee. Des dates inversees sont remises dans l'ordre plutot que refusees — l'intention est
     * evidente, et un message d'erreur n'apprendrait rien a personne.
     */
    private LocalDate[] periode(LocalDate debut, LocalDate fin) {
        LocalDate d = debut != null ? debut : LocalDate.now().withDayOfYear(1);
        LocalDate f = fin != null ? fin : LocalDate.now();
        return d.isAfter(f) ? new LocalDate[]{f, d} : new LocalDate[]{d, f};
    }

    private String preparer(Model model, LocalDate[] periode, String page, String vue) {
        model.addAttribute("debut", periode[0]);
        model.addAttribute("fin", periode[1]);
        model.addAttribute("etablissement", etablissementService.getCurrentEtablissement());
        model.addAttribute("activePage", page);
        return vue;
    }
}
