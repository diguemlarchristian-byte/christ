package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/direction")
public class DirectionController {

    @Autowired private EtablissementService etablissementService;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private holyflame.administration.service.SuiviSaisieService suiviSaisieService;

    @GetMapping("/suivi")
    public String suivi(
            @RequestParam(required = false) String filtreEnseignant,
            @RequestParam(required = false) Long filtreMatiere,
            @RequestParam(required = false) Long filtreClasse,
            Model model) {

        Utilisateur currentUser = etablissementService.getCurrentUtilisateur();
        Long etabId = etablissementService.getCurrentEtablissementId();

        // Contrôle d'accès : ADMIN ou DIRECTEUR direct, ou délégation active + est directeur
        boolean isAdmin = currentUser != null
            && ("ADMIN".equals(currentUser.getRole()) || "DIRECTEUR".equals(currentUser.getRole()));
        if (!isAdmin) {
            boolean delegue = etabId != null && parametreRepository
                .findByCleAndEtablissementId("DELEGATION_DIRECTION", etabId)
                .map(p -> "true".equals(p.getValeur())).orElse(false);
            if (!delegue) return "redirect:/dashboard";

            String email = currentUser != null ? currentUser.getEmail() : "";
            Personnel perso = etabId != null
                ? personnelRepository.findByEmailAndEtablissementId(email, etabId).orElse(null)
                : null;
            if (perso == null || !"DIRECTEUR".equals(perso.getFonction())) {
                return "redirect:/dashboard";
            }
        }

        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));

        List<Map<String, Object>> suiviRows =
            suiviSaisieService.lignes(etabId, filtreEnseignant, filtreMatiere, filtreClasse);
        model.addAttribute("suiviRows", suiviRows);
        model.addAttribute("filtreEnseignant", filtreEnseignant);
        model.addAttribute("filtreMatiere", filtreMatiere);
        model.addAttribute("filtreClasse", filtreClasse);

        // Synthese : ce que le directeur cherche en arrivant, avant meme de lire le tableau.
        model.addAttribute("nbNonRempli", compter(suiviRows, holyflame.administration.service.SuiviSaisieService.NON_REMPLI));
        model.addAttribute("nbEnCours",   compter(suiviRows, holyflame.administration.service.SuiviSaisieService.EN_COURS));
        model.addAttribute("nbComplet",   compter(suiviRows, holyflame.administration.service.SuiviSaisieService.COMPLET));

        // Necessaires a la barre laterale partagee, sinon la page s'affiche sans navigation.
        model.addAttribute("utilisateurConnecte", currentUser);
        model.addAttribute("nomEtablissement",
            etablissementService.getCurrentEtablissement() != null
                ? etablissementService.getCurrentEtablissement().getNom() : "EduSystem Pro");

        return "direction/suivi";
    }

    private long compter(List<Map<String, Object>> lignes, String statut) {
        return lignes.stream().filter(l -> statut.equals(l.get("statut"))).count();
    }
}
