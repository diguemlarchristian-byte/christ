package holyflame.administration.controller;

import holyflame.administration.model.Echeance;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.RemiseEleve;
import holyflame.administration.repository.EcheanceRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.RemiseEleveRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import holyflame.administration.service.SituationFinanciereService;
import holyflame.administration.service.SituationFinanciereService.Situation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Suivi financier des familles : remises accordees et echeanciers de paiement.
 *
 * Les deux manques qui genaient le plus la comptable au quotidien. Sans remise, un boursier
 * apparait en impaye permanent et fausse tout le tableau des arrieres. Sans echeancier, le
 * paiement fractionne — la norme dans une ecole — se suit a la main, hors du logiciel.
 */
@Controller
@RequestMapping("/suivi-familles")
public class SuiviFamillesController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private RemiseEleveRepository remiseRepository;
    @Autowired private EcheanceRepository echeanceRepository;
    @Autowired private SituationFinanciereService situations;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;

    /** Motifs de remise, dans l'ordre de frequence reelle dans une ecole. */
    public static final List<String> MOTIFS = List.of(
        "BOURSE", "FRATRIE", "PERSONNEL", "SOCIALE", "AUTRE");

    @GetMapping
    public String index(@RequestParam(required = false) String recherche, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String annee = etablissementService.getAnneeScolaireActive();

        List<Eleve> eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        if (recherche != null && !recherche.isBlank()) {
            String q = recherche.trim().toLowerCase();
            eleves = eleves.stream()
                .filter(e -> (e.getNom() + " " + e.getPrenom() + " " + e.getMatricule()).toLowerCase().contains(q))
                .toList();
        }

        Map<Long, Situation> parEleve = situations.situations(eleves, etabId, annee);
        LocalDate aujourdhui = LocalDate.now();

        // Les eleves en retard d'echeance remontent en tete : c'est ce sur quoi il faut agir.
        List<Eleve> ordonnes = new ArrayList<>(eleves);
        ordonnes.sort(Comparator
            .comparing((Eleve e) -> parEleve.get(e.getId()).echeancesEnRetard(aujourdhui).isEmpty())
            .thenComparing(Eleve::getNom, Comparator.nullsLast(String::compareTo)));

        model.addAttribute("eleves", ordonnes);
        model.addAttribute("situations", parEleve);
        model.addAttribute("recherche", recherche);
        model.addAttribute("motifs", MOTIFS);
        model.addAttribute("aujourdhui", aujourdhui);
        model.addAttribute("anneeScolaire", annee);
        model.addAttribute("echeancesEnRetard", situations.echeancesEnRetard(etabId, annee, aujourdhui));
        model.addAttribute("activePage", "suivi-familles");
        return "suivi-familles";
    }

    // ── Remises ─────────────────────────────────────────────────────────

    @PostMapping("/remises")
    public String accorderRemise(@RequestParam Long eleveId,
                                 @RequestParam String type,
                                 @RequestParam Double valeur,
                                 @RequestParam(required = false) String motif,
                                 @RequestParam(required = false) String commentaire,
                                 RedirectAttributes ra) {

        Eleve eleve = eleveDeMonEtablissement(eleveId);
        if (eleve == null) {
            ra.addFlashAttribute("erreur", "Eleve introuvable.");
            return "redirect:/suivi-familles";
        }
        if (valeur == null || valeur <= 0) {
            ra.addFlashAttribute("erreur", "La valeur de la remise doit etre superieure a zero.");
            return "redirect:/suivi-familles";
        }
        if ("POURCENTAGE".equals(type) && valeur > 100) {
            ra.addFlashAttribute("erreur", "Une remise ne peut pas depasser 100 %.");
            return "redirect:/suivi-familles";
        }

        RemiseEleve r = new RemiseEleve();
        r.setEleveId(eleveId);
        r.setType("MONTANT".equals(type) ? "MONTANT" : "POURCENTAGE");
        r.setValeur(valeur);
        r.setMotif(motif);
        r.setCommentaire(commentaire);
        r.setAnneeScolaire(etablissementService.getAnneeScolaireActive());
        r.setDateAccord(LocalDate.now());
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) r.setAccordeeParId(utilisateur.getId());
        r.setEtablissementId(etablissementService.getCurrentEtablissementId());
        remiseRepository.save(r);

        journalService.log("REMISE_ACCORDEE", "FINANCES",
            eleve.getNom() + " " + eleve.getPrenom() + " — "
            + valeur + ("MONTANT".equals(r.getType()) ? " F" : " %")
            + (motif != null ? " (" + motif + ")" : ""));

        ra.addFlashAttribute("succes", "Remise accordee a " + eleve.getNom() + " " + eleve.getPrenom() + ".");
        return "redirect:/suivi-familles";
    }

    @PostMapping("/remises/{id}/retirer")
    public String retirerRemise(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        remiseRepository.findById(id)
            .filter(r -> etabId != null && etabId.equals(r.getEtablissementId()))
            .ifPresent(r -> {
                remiseRepository.delete(r);
                journalService.log("REMISE_RETIREE", "FINANCES", "Remise #" + id + " retiree.");
            });
        ra.addFlashAttribute("succes", "Remise retiree.");
        return "redirect:/suivi-familles";
    }

    // ── Echeanciers ─────────────────────────────────────────────────────

    @PostMapping("/echeanciers")
    public String planifier(@RequestParam Long eleveId,
                            @RequestParam Integer nbVersements,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate premiereEcheance,
                            @RequestParam(required = false) Double montantTotal,
                            RedirectAttributes ra) {

        Eleve eleve = eleveDeMonEtablissement(eleveId);
        if (eleve == null) {
            ra.addFlashAttribute("erreur", "Eleve introuvable.");
            return "redirect:/suivi-familles";
        }

        Long etabId = etablissementService.getCurrentEtablissementId();
        String annee = etablissementService.getAnneeScolaireActive();
        Situation s = situations.situation(eleve, annee, situations.fraisObligatoires(etabId));

        // Par defaut on etale ce que l'eleve doit encore, remises deduites : c'est le montant
        // que la famille a effectivement a regler.
        double aEtaler = montantTotal != null && montantTotal > 0 ? montantTotal : s.reste();
        if (aEtaler <= 0) {
            ra.addFlashAttribute("erreur",
                eleve.getNom() + " " + eleve.getPrenom() + " ne doit plus rien : aucun echeancier necessaire.");
            return "redirect:/suivi-familles";
        }

        try {
            List<Echeance> creees = situations.planifier(eleve, annee, nbVersements,
                premiereEcheance, aEtaler, etabId);
            // Ce qui est deja verse s'impute aussitot : sinon un eleve ayant deja paye une
            // partie verrait son premier versement affiche comme du.
            if (s.verse() > 0) situations.imputer(eleveId, annee, s.verse(), LocalDate.now());

            journalService.log("ECHEANCIER_CREE", "FINANCES",
                eleve.getNom() + " " + eleve.getPrenom() + " — " + creees.size() + " versements");
            ra.addFlashAttribute("succes",
                creees.size() + " versements planifies pour " + eleve.getNom() + " " + eleve.getPrenom() + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/suivi-familles";
    }

    @PostMapping("/echeanciers/{eleveId}/supprimer")
    public String supprimerEcheancier(@PathVariable Long eleveId, RedirectAttributes ra) {
        Eleve eleve = eleveDeMonEtablissement(eleveId);
        if (eleve != null) {
            echeanceRepository.deleteByEleveIdAndAnneeScolaire(eleveId,
                etablissementService.getAnneeScolaireActive());
            ra.addFlashAttribute("succes", "Echeancier retire. Les paiements deja enregistres sont conserves.");
        }
        return "redirect:/suivi-familles";
    }

    private Eleve eleveDeMonEtablissement(Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        return eleveRepository.findById(id)
            .filter(e -> etabId != null && etabId.equals(e.getEtablissementId()))
            .orElse(null);
    }
}
