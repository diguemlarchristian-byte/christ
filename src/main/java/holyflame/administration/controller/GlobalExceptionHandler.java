package holyflame.administration.controller;

import holyflame.administration.service.AnneeScolaireClotureeException;
import holyflame.administration.service.MoisClotureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AnneeScolaireClotureeException.class)
    public String handleAnneeCloturee(AnneeScolaireClotureeException ex, HttpServletRequest req, RedirectAttributes ra) {
        String msg = "L'annee scolaire " + ex.getAnneeScolaire() + " est cloturee : aucune modification n'est possible. Reouvrez-la depuis Parametres > Annees scolaires si besoin.";
        // Les pages de l'application n'utilisent pas toutes le meme nom d'attribut flash pour les erreurs
        // (erreurMsg / erreur / erreurAuth selon la page) : on renseigne les trois pour un affichage fiable partout.
        ra.addFlashAttribute("erreurMsg", msg);
        ra.addFlashAttribute("erreur", msg);
        ra.addFlashAttribute("erreurAuth", msg);
        String referer = req.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @ExceptionHandler(MoisClotureException.class)
    public String handleMoisCloture(MoisClotureException ex, HttpServletRequest req, RedirectAttributes ra) {
        String msg = "Le mois de " + ex.getMois() + "/" + ex.getAnneeCivile()
            + " est cloture comptablement : aucune modification n'est possible. Reouvrez-le depuis Finances > Parametrage > Clotures mensuelles si besoin.";
        ra.addFlashAttribute("erreurMsg", msg);
        ra.addFlashAttribute("erreur", msg);
        String referer = req.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/finances");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ModelAndView handleNotFound(HttpServletRequest req) {
        ModelAndView mav = new ModelAndView("erreur-suppression");
        mav.setStatus(org.springframework.http.HttpStatus.NOT_FOUND);
        mav.addObject("retourUrl", "/dashboard");
        mav.addObject("titre", "Élément introuvable");
        mav.addObject("detail", "L'élément demandé n'existe pas ou a déjà été supprimé.");
        return mav;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleFkConstraint(HttpServletRequest req, DataIntegrityViolationException ex) {
        ModelAndView mav = new ModelAndView("erreur-suppression");
        String referer = req.getHeader("Referer");
        mav.addObject("retourUrl", referer != null ? referer : "/dashboard");
        String msg = ex.getMessage();
        boolean estSuppression = "POST".equalsIgnoreCase(req.getMethod()) && req.getRequestURI().endsWith("/supprimer");
        if (msg != null && (msg.contains("Duplicate entry") || msg.contains("unique constraint") || msg.contains("UNIQUE"))) {
            mav.addObject("titre", "Enregistrement impossible");
            mav.addObject("detail", "Une valeur unique (matricule, email, code…) est déjà utilisée par un autre enregistrement. Veuillez la modifier.");
        } else if (msg != null && msg.contains("FOREIGN KEY") && estSuppression) {
            mav.addObject("titre", "Suppression impossible");
            mav.addObject("detail", "Cet élément est utilisé par d'autres données (notes, absences, paiements…). Supprimez les données liées en premier.");
        } else {
            mav.addObject("titre", estSuppression ? "Suppression impossible" : "Opération impossible");
            mav.addObject("detail", "Une contrainte de base de données empêche cette opération.");
        }
        return mav;
    }
}
