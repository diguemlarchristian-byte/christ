package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestion des frais de scolarite, sur un ecran a elle seule.
 *
 * Ces frais se reglaient jusqu'ici depuis Parametres, reserve a l'ADMIN. C'est pourtant le
 * travail quotidien de la comptable, et les tarifs changent en cours d'annee : frais d'examen,
 * sortie scolaire, rattrapage. Elle devait donc demander l'administrateur a chaque nouveau
 * tarif, ou recevoir un acces ADMIN qui lui ouvrait aussi la suppression des comptes et la
 * configuration de l'etablissement.
 *
 * Cet ecran ne fait qu'une chose, et la fait entierement : creer, modifier et retirer les
 * frais. Il ne donne acces a rien d'autre.
 */
@Controller
@RequestMapping("/frais")
public class FraisScolariteController {

    @Autowired private FraisScolariteRepository fraisRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private EtablissementService etablissementService;

    /** Types proposes, dans l'ordre ou une ecole les rencontre. */
    public static final List<String> TYPES = List.of(
        "INSCRIPTION", "SCOLARITE", "CANTINE", "TRANSPORT", "EXAMEN", "FOURNITURES", "AUTRE");

    /** Echeances possibles : a quel rythme la famille doit s'acquitter du frais. */
    public static final List<String> ECHEANCES = List.of(
        "ANNUEL", "T1", "T2", "T3", "MENSUEL", "UNIQUE");

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<FraisScolarite> frais = new java.util.ArrayList<>(
            fraisRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId));
        frais.sort(Comparator.comparing(FraisScolarite::getTypeFrais, Comparator.nullsLast(String::compareTo))
            .thenComparing(FraisScolarite::getDesignation, Comparator.nullsLast(String::compareTo)));

        model.addAttribute("frais", frais);
        model.addAttribute("types", TYPES);
        model.addAttribute("echeances", ECHEANCES);
        model.addAttribute("niveaux", niveauxDeLEtablissement(etabId));
        model.addAttribute("totalObligatoire", frais.stream()
            .filter(FraisScolarite::isObligatoire)
            .mapToDouble(f -> f.getMontant() != null ? f.getMontant() : 0)
            .sum());
        model.addAttribute("activePage", "frais");
        return "frais-scolarite";
    }

    @PostMapping
    public String creer(@RequestParam String designation,
                        @RequestParam String typeFrais,
                        @RequestParam Double montant,
                        @RequestParam String echeance,
                        @RequestParam(required = false) String niveauCible,
                        @RequestParam(required = false, defaultValue = "false") boolean obligatoire,
                        RedirectAttributes ra) {

        String erreur = valider(designation, montant);
        if (erreur != null) {
            ra.addFlashAttribute("erreur", erreur);
            return "redirect:/frais";
        }

        FraisScolarite f = new FraisScolarite();
        appliquer(f, designation, typeFrais, montant, echeance, niveauCible, obligatoire);
        f.setEtablissementId(etablissementService.getCurrentEtablissementId());
        fraisRepository.save(f);

        ra.addFlashAttribute("succes", "« " + f.getDesignation() + " » ajoute.");
        return "redirect:/frais";
    }

    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id,
                           @RequestParam String designation,
                           @RequestParam String typeFrais,
                           @RequestParam Double montant,
                           @RequestParam String echeance,
                           @RequestParam(required = false) String niveauCible,
                           @RequestParam(required = false, defaultValue = "false") boolean obligatoire,
                           RedirectAttributes ra) {

        FraisScolarite f = fraisDeMonEtablissement(id);
        if (f == null) {
            ra.addFlashAttribute("erreur", "Frais introuvable.");
            return "redirect:/frais";
        }
        String erreur = valider(designation, montant);
        if (erreur != null) {
            ra.addFlashAttribute("erreur", erreur);
            return "redirect:/frais";
        }

        appliquer(f, designation, typeFrais, montant, echeance, niveauCible, obligatoire);
        fraisRepository.save(f);

        ra.addFlashAttribute("succes", "« " + f.getDesignation() + " » modifie.");
        return "redirect:/frais";
    }

    /**
     * Retire un frais, sauf s'il a deja servi.
     *
     * Un frais deja regle par des familles ne peut pas disparaitre : leurs paiements y
     * renvoient, et les reçus deja delivres deviendraient inexplicables.
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        FraisScolarite f = fraisDeMonEtablissement(id);
        if (f == null) {
            ra.addFlashAttribute("erreur", "Frais introuvable.");
            return "redirect:/frais";
        }

        long reglements = paiementRepository.findByEtablissementId(f.getEtablissementId()).stream()
            .filter(p -> p.getFraisScolarite() != null && id.equals(p.getFraisScolarite().getId()))
            .count();
        if (reglements > 0) {
            ra.addFlashAttribute("erreur", "« " + f.getDesignation() + " » a deja recu "
                + reglements + " reglement(s) : il ne peut plus etre supprime. Modifiez-le "
                + "ou passez-le en facultatif pour ne plus le reclamer.");
            return "redirect:/frais";
        }

        fraisRepository.delete(f);
        ra.addFlashAttribute("succes", "« " + f.getDesignation() + " » retire.");
        return "redirect:/frais";
    }

    // ── Interne ─────────────────────────────────────────────────────────

    private String valider(String designation, Double montant) {
        if (designation == null || designation.isBlank()) return "La designation est obligatoire.";
        if (montant == null || montant <= 0) return "Le montant doit etre superieur a zero.";
        if (montant > 100_000_000) return "Ce montant parait errone : verifiez la saisie.";
        return null;
    }

    private void appliquer(FraisScolarite f, String designation, String typeFrais, Double montant,
                           String echeance, String niveauCible, boolean obligatoire) {
        f.setDesignation(designation.trim());
        f.setTypeFrais(typeFrais);
        f.setMontant(montant);
        f.setEcheance(echeance);
        // Vide = tous les niveaux : c'est le cas courant, il ne doit pas obliger a choisir.
        f.setNiveauCible(niveauCible != null && !niveauCible.isBlank() ? niveauCible : null);
        f.setObligatoire(obligatoire);
    }

    /** Garde-fou multi-etablissement : on ne touche jamais au frais d'une autre ecole. */
    private FraisScolarite fraisDeMonEtablissement(Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        return fraisRepository.findById(id)
            .filter(f -> etabId != null && etabId.equals(f.getEtablissementId()))
            .orElse(null);
    }

    /** Niveaux reellement ouverts dans l'ecole, pour ne pas proposer des classes inexistantes. */
    private Set<String> niveauxDeLEtablissement(Long etabId) {
        return classeRepository.findByEtablissementId(etabId).stream()
            .map(Classe::getNiveau)
            .filter(n -> n != null && !n.isBlank())
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
