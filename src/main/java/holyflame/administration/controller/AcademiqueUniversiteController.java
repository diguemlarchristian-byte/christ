package holyflame.administration.controller;

import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Parcours;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ParcoursRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.MaquettePedagogiqueService;
import holyflame.administration.service.MaquettePedagogiqueService.EtatSemestre;
import holyflame.administration.service.MaquettePedagogiqueService.MaquetteRefusee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maquette pedagogique d'un etablissement en regime LMD : parcours, unites d'enseignement
 * et elements constitutifs.
 *
 * Pendant de /gestion-academique, qui raisonne en classes, matieres a coefficient et
 * professeurs titulaires — des notions sans equivalent a l'universite, ou l'etudiant
 * capitalise des credits semestre par semestre.
 *
 * L'ecran expose ce que MaquettePedagogiqueService sait deja verifier : le volume de credits
 * de chaque semestre et les unites dont aucun element n'a ete declare. Ces deux controles
 * sont montres pendant la construction de la maquette, et non a la premiere deliberation,
 * ou il serait trop tard pour corriger sans reprendre des notes deja saisies.
 */
@Controller
@RequestMapping("/academique-universite")
public class AcademiqueUniversiteController {

    @Autowired private MaquettePedagogiqueService maquetteService;
    @Autowired private ParcoursRepository parcoursRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String index(@RequestParam(required = false) Long parcours, Model model) {
        Etablissement etablissement = etablissementService.getCurrentEtablissement();
        // Symetrique de la redirection posee dans GestionAcademiqueController : une ecole qui
        // arrive ici par l'adresse doit retomber sur son propre ecran, pas sur une maquette vide
        // qu'elle ne pourra jamais remplir.
        if (etablissement != null && !etablissement.estRegimeLMD()) {
            return "redirect:/gestion-academique";
        }

        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Parcours> parcoursListe = maquetteService.parcoursDe(etabId);
        model.addAttribute("parcoursListe", parcoursListe);

        // Parcours affiche : celui demande s'il appartient bien a l'etablissement, sinon le
        // premier de la liste. Le controle d'appartenance evite qu'un identifiant devine dans
        // l'URL ouvre la maquette d'un autre etablissement.
        Parcours selectionne = null;
        if (parcours != null) {
            selectionne = parcoursRepository.findById(parcours)
                .filter(p -> p.getEtablissementId() != null && p.getEtablissementId().equals(etabId))
                .orElse(null);
        }
        if (selectionne == null && !parcoursListe.isEmpty()) {
            selectionne = parcoursListe.get(0);
        }

        if (selectionne != null) {
            List<UniteEnseignement> unites = maquetteService.unitesDe(selectionne.getId());

            // Les unites sont regroupees par semestre pour que chaque bloc de l'ecran porte son
            // total de credits a cote du volume attendu — c'est la lecture qui interesse
            // l'administrateur, l'ordre plat des unites ne dit rien de la completude.
            Map<Integer, List<UniteEnseignement>> unitesParSemestre = new LinkedHashMap<>();
            int nbSemestres = selectionne.getNbSemestres() != null ? selectionne.getNbSemestres() : 0;
            for (int s = 1; s <= nbSemestres; s++) unitesParSemestre.put(s, new ArrayList<>());
            for (UniteEnseignement ue : unites) {
                unitesParSemestre.computeIfAbsent(ue.getSemestre(), s -> new ArrayList<>()).add(ue);
            }

            Map<Long, List<ElementConstitutif>> elementsParUnite = new LinkedHashMap<>();
            for (UniteEnseignement ue : unites) {
                elementsParUnite.put(ue.getId(), maquetteService.elementsDe(ue.getId()));
            }

            List<EtatSemestre> etats = maquetteService.controlerCredits(selectionne, etablissement);
            Map<Integer, EtatSemestre> etatParSemestre = etats.stream()
                .collect(Collectors.toMap(EtatSemestre::semestre, e -> e, (a, b) -> a, LinkedHashMap::new));

            model.addAttribute("unites", unites);
            model.addAttribute("unitesParSemestre", unitesParSemestre);
            model.addAttribute("elementsParUnite", elementsParUnite);
            model.addAttribute("etatsSemestres", etats);
            model.addAttribute("etatParSemestre", etatParSemestre);
            model.addAttribute("unitesSansElement", maquetteService.unitesSansElement(selectionne.getId()));
            model.addAttribute("maquetteUtilisable", maquetteService.estUtilisable(selectionne, etablissement));
        }
        model.addAttribute("parcoursSelectionne", selectionne);

        List<Utilisateur> enseignantsDisponibles = utilisateurRepository
            .findByRoleAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId);
        model.addAttribute("enseignantsDisponibles", enseignantsDisponibles);
        model.addAttribute("enseignantParId", enseignantsDisponibles.stream()
            .collect(Collectors.toMap(Utilisateur::getId, u -> u, (a, b) -> a)));

        model.addAttribute("creditsAttendus", etablissement != null && etablissement.getCreditsParSemestre() != null
            ? etablissement.getCreditsParSemestre() : 30);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "academique-universite";
    }

    @PostMapping("/parcours")
    public String creerParcours(@RequestParam String libelle,
                                @RequestParam(required = false) String diplome,
                                @RequestParam(required = false) Integer nbSemestres,
                                RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        try {
            Parcours cree = maquetteService.creerParcours(libelle, diplome, nbSemestres, etabId);
            ra.addFlashAttribute("succes", "Parcours \"" + cree.getLibelle() + "\" cree.");
            return "redirect:/academique-universite?parcours=" + cree.getId();
        } catch (MaquetteRefusee e) {
            ra.addFlashAttribute("erreur", e.getMessage());
            return "redirect:/academique-universite";
        }
    }

    @PostMapping("/unites")
    public String ajouterUnite(@RequestParam Long parcoursId,
                               @RequestParam(required = false) String code,
                               @RequestParam String intitule,
                               @RequestParam(required = false) Integer credits,
                               @RequestParam(required = false) Integer semestre,
                               @RequestParam(required = false) String type,
                               RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (!appartientALEtablissement(parcoursId, etabId)) {
            ra.addFlashAttribute("erreur", "Parcours introuvable.");
            return "redirect:/academique-universite";
        }
        try {
            maquetteService.ajouterUnite(parcoursId, code, intitule, credits, semestre, type, etabId);
            ra.addFlashAttribute("succes", "Unite d'enseignement ajoutee.");
        } catch (MaquetteRefusee e) {
            ra.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/academique-universite?parcours=" + parcoursId;
    }

    @PostMapping("/unites/{id}/supprimer")
    public String supprimerUnite(@PathVariable Long id,
                                 @RequestParam Long parcoursId,
                                 RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (!appartientALEtablissement(parcoursId, etabId)) {
            ra.addFlashAttribute("erreur", "Parcours introuvable.");
            return "redirect:/academique-universite";
        }
        maquetteService.supprimerUnite(id);
        ra.addFlashAttribute("succes", "Unite supprimee, ainsi que ses elements constitutifs.");
        return "redirect:/academique-universite?parcours=" + parcoursId;
    }

    @PostMapping("/elements")
    public String ajouterElement(@RequestParam Long uniteId,
                                 @RequestParam Long parcoursId,
                                 @RequestParam String intitule,
                                 @RequestParam(required = false) Double coefficient,
                                 @RequestParam(required = false) Integer volumeHoraire,
                                 @RequestParam(required = false) Long enseignantId,
                                 RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (!appartientALEtablissement(parcoursId, etabId)) {
            ra.addFlashAttribute("erreur", "Parcours introuvable.");
            return "redirect:/academique-universite";
        }
        try {
            maquetteService.ajouterElement(uniteId, intitule, coefficient, volumeHoraire, enseignantId, etabId);
            ra.addFlashAttribute("succes", "Element constitutif ajoute.");
        } catch (MaquetteRefusee e) {
            ra.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/academique-universite?parcours=" + parcoursId;
    }

    /** Un parcours d'un autre etablissement doit rester inaccessible, meme avec son identifiant. */
    private boolean appartientALEtablissement(Long parcoursId, Long etabId) {
        if (parcoursId == null || etabId == null) return false;
        return parcoursRepository.findById(parcoursId)
            .map(p -> etabId.equals(p.getEtablissementId()))
            .orElse(false);
    }
}
