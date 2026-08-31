package holyflame.administration.controller;

import holyflame.administration.model.CategorieComptable;
import holyflame.administration.model.Depense;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.LigneBudget;
import holyflame.administration.repository.CategorieComptableRepository;
import holyflame.administration.repository.DepenseRepository;
import holyflame.administration.repository.LigneBudgetRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import holyflame.administration.service.HorlogeService;
import holyflame.administration.service.JournalService;
import holyflame.administration.util.AnneeScolaireUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/finances/depenses")
public class DepenseController {

    @Autowired private DepenseRepository depenseRepository;
    @Autowired private LigneBudgetRepository budgetRepository;
    @Autowired private CategorieComptableRepository categorieComptableRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired private holyflame.administration.service.ClotureMensuelleService clotureMensuelleService;
    @Autowired private JournalService journalService;
    @Autowired private HorlogeService horlogeService;
    @Autowired private FileStorageService fileStorageService;

    @GetMapping
    public String index(
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) String categorieComptableId,
            @RequestParam(required = false) String statut) {
        StringBuilder qs = new StringBuilder("redirect:/finances?tab=depenses");
        if (mois != null) qs.append("&mois=").append(mois);
        if (annee != null) qs.append("&anneeCivile=").append(annee);
        if (categorieComptableId != null && !categorieComptableId.isBlank()) qs.append("&categorieComptableId=").append(categorieComptableId);
        if (statut != null && !statut.isBlank()) qs.append("&statutDepense=").append(statut);
        return qs.toString();
    }

    @GetMapping("/detail")
    public String detail(
            @RequestParam(required = false, defaultValue = "MOIS") String periode,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDate aujourdHui = horlogeService.aujourdHui();
        int moisFiltre = mois != null ? mois : aujourdHui.getMonthValue();
        int anneeFiltre = annee != null ? annee : aujourdHui.getYear();

        LocalDate ancre = LocalDate.of(anneeFiltre, moisFiltre, 1);
        LocalDate debutPeriode;
        LocalDate finPeriode;
        LocalDate debutPeriodePrecedente;
        LocalDate finPeriodePrecedente;

        switch (periode) {
            case "TRIMESTRE" -> {
                int debutTrimestreMois = ((moisFiltre - 1) / 3) * 3 + 1;
                debutPeriode = LocalDate.of(anneeFiltre, debutTrimestreMois, 1);
                finPeriode = debutPeriode.plusMonths(3).minusDays(1);
                debutPeriodePrecedente = debutPeriode.minusMonths(3);
                finPeriodePrecedente = debutPeriode.minusDays(1);
            }
            case "ANNEE" -> {
                debutPeriode = LocalDate.of(anneeFiltre, 1, 1);
                finPeriode = LocalDate.of(anneeFiltre, 12, 31);
                debutPeriodePrecedente = debutPeriode.minusYears(1);
                finPeriodePrecedente = finPeriode.minusYears(1);
            }
            default -> {
                debutPeriode = ancre.withDayOfMonth(1);
                finPeriode = ancre.withDayOfMonth(ancre.lengthOfMonth());
                debutPeriodePrecedente = debutPeriode.minusMonths(1);
                finPeriodePrecedente = debutPeriodePrecedente.withDayOfMonth(debutPeriodePrecedente.lengthOfMonth());
            }
        }

        List<Depense> toutesLesDepenses = depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId).stream()
            .filter(d -> "CHARGE".equals(d.getSens())).collect(Collectors.toList());

        List<Depense> depensesPeriode = toutesLesDepenses.stream()
            .filter(d -> d.getDateDepense() != null
                && !d.getDateDepense().isBefore(debutPeriode) && !d.getDateDepense().isAfter(finPeriode))
            .collect(Collectors.toList());
        List<Depense> depensesPeriodePrecedente = toutesLesDepenses.stream()
            .filter(d -> d.getDateDepense() != null
                && !d.getDateDepense().isBefore(debutPeriodePrecedente) && !d.getDateDepense().isAfter(finPeriodePrecedente))
            .collect(Collectors.toList());

        double totalPeriode = depensesPeriode.stream().mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double totalPeriodePrecedente = depensesPeriodePrecedente.stream().mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        Map<String, Double> parCategorie = depensesPeriode.stream()
            .collect(Collectors.groupingBy(
                d -> d.getCategorieComptable() != null ? d.getCategorieComptable().getLibelle() : "Non classe",
                LinkedHashMap::new,
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));
        double maxCategorie = parCategorie.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        List<Map<String, Object>> repartition = parCategorie.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("categorie", e.getKey());
                row.put("montant", e.getValue());
                row.put("pourcentageBarre", maxCategorie > 0 ? Math.round(e.getValue() / maxCategorie * 1000) / 10.0 : 0);
                return row;
            })
            .collect(Collectors.toList());

        Map<String, Double> parFournisseur = depensesPeriode.stream()
            .filter(d -> d.getBeneficiaire() != null && !d.getBeneficiaire().isBlank())
            .collect(Collectors.groupingBy(Depense::getBeneficiaire, LinkedHashMap::new,
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));
        List<Map<String, Object>> principauxFournisseurs = parFournisseur.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(4)
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("nom", e.getKey());
                row.put("montant", e.getValue());
                String cat = depensesPeriode.stream()
                    .filter(d -> e.getKey().equals(d.getBeneficiaire()))
                    .map(d -> d.getCategorieComptable() != null ? d.getCategorieComptable().getLibelle() : "")
                    .findFirst().orElse("");
                row.put("categorie", cat);
                String[] mots = e.getKey().trim().split("\\s+");
                String initiales = mots.length > 1
                    ? ("" + mots[0].charAt(0) + mots[1].charAt(0)).toUpperCase()
                    : e.getKey().substring(0, Math.min(2, e.getKey().length())).toUpperCase();
                row.put("initiales", initiales);
                return row;
            })
            .collect(Collectors.toList());

        String anneeScolairePeriode = AnneeScolaireUtil.pour(ancre);
        List<LigneBudget> lignesDepense = budgetRepository
            .findByEtablissementIdAndAnneeScolaireOrderByDesignationAsc(etabId, anneeScolairePeriode)
            .stream().filter(l -> !l.isRevenu()).collect(Collectors.toList());
        double budgetAnnuelPrevu = lignesDepense.stream()
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double depenseAnnuelleTotale = toutesLesDepenses.stream()
            .filter(d -> anneeScolairePeriode.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double tauxBudgetUtilise = budgetAnnuelPrevu > 0 ? Math.round(depenseAnnuelleTotale / budgetAnnuelPrevu * 1000) / 10.0 : 0;

        double servicesPublicsPeriode = parCategorie.getOrDefault("SNE", 0.0) + parCategorie.getOrDefault("Eau", 0.0);
        double servicesPublicsPeriodePrecedente = depensesPeriodePrecedente.stream()
            .filter(d -> d.getCategorieComptable() != null
                && ("SNE".equals(d.getCategorieComptable().getLibelle()) || "Eau".equals(d.getCategorieComptable().getLibelle())))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double variationServicesPublics = servicesPublicsPeriode - servicesPublicsPeriodePrecedente;

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("periode", periode);
        model.addAttribute("moisFiltre", moisFiltre);
        model.addAttribute("anneeFiltre", anneeFiltre);
        model.addAttribute("debutPeriode", debutPeriode);
        model.addAttribute("finPeriode", finPeriode);
        model.addAttribute("totalPeriode", totalPeriode);
        model.addAttribute("variationVsPeriodePrecedente", totalPeriode - totalPeriodePrecedente);
        model.addAttribute("tauxBudgetUtilise", tauxBudgetUtilise);
        model.addAttribute("budgetAnnuelPrevu", budgetAnnuelPrevu);
        model.addAttribute("variationServicesPublics", variationServicesPublics);
        model.addAttribute("repartition", repartition);
        model.addAttribute("principauxFournisseurs", principauxFournisseurs);
        model.addAttribute("depensesPeriode", depensesPeriode.stream()
            .sorted(Comparator.comparing(Depense::getDateDepense).reversed()).collect(Collectors.toList()));

        return "depenses-detail";
    }

    @PostMapping
    public String ajouterDepense(
            @RequestParam String designation,
            @RequestParam(required = false) Long categorieComptableId,
            @RequestParam(required = false) String beneficiaire,
            @RequestParam(required = false) Double quantite,
            @RequestParam(required = false) Double prixUnitaire,
            @RequestParam Double montant,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDepense,
            @RequestParam(defaultValue = "PAYE") String statut,
            @RequestParam(required = false) MultipartFile justificatif,
            RedirectAttributes ra) throws IOException {

        Long etabId = etablissementService.getCurrentEtablissementId();
        anneeScolaireService.verifierModifiable(AnneeScolaireUtil.pour(dateDepense), etabId);
        clotureMensuelleService.verifierModifiable(dateDepense, etabId);
        Depense d = new Depense();
        d.setDesignation(designation);
        String sens = "CHARGE";
        if (categorieComptableId != null) {
            CategorieComptable cat = categorieComptableRepository.findById(categorieComptableId).orElse(null);
            if (cat != null) { d.setCategorieComptable(cat); sens = cat.getSens(); }
        }
        d.setSens(sens);
        d.setBeneficiaire(beneficiaire);
        d.setQuantite(quantite);
        d.setPrixUnitaire(prixUnitaire);
        d.setMontant(montant);
        d.setDateDepense(dateDepense);
        d.setStatut(statut);
        d.setAnneeScolaire(AnneeScolaireUtil.pour(dateDepense));
        d.setEtablissementId(etabId);
        if (justificatif != null && !justificatif.isEmpty()) {
            String chemin = fileStorageService.store(justificatif, "depenses");
            d.setJustificatifPath(chemin);
            d.setJustificatifNomOriginal(justificatif.getOriginalFilename());
        }
        depenseRepository.save(d);

        journalService.log("PRODUIT".equals(sens) ? "RECETTE_AJOUTÉE" : "DEPENSE_AJOUTÉE", "FINANCES", designation + " — " + montant + " F (" + beneficiaire + ")");
        ra.addFlashAttribute("successMsg", ("PRODUIT".equals(sens) ? "Recette enregistree : " : "Depense enregistree : ") + designation + ".");
        return "redirect:/finances?tab=depenses&mois=" + dateDepense.getMonthValue() + "&anneeCivile=" + dateDepense.getYear();
    }

    @PostMapping("/{id}/supprimer")
    public String supprimerDepense(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        depenseRepository.findById(id)
            .filter(d -> etabId != null && etabId.equals(d.getEtablissementId()))
            .ifPresent(d -> {
                clotureMensuelleService.verifierModifiable(d.getDateDepense(), etabId);
                journalService.log("DEPENSE_SUPPRIMÉE", "FINANCES", d.getDesignation() + " — " + d.getMontant() + " F");
                if (d.getJustificatifPath() != null) fileStorageService.delete(d.getJustificatifPath());
                depenseRepository.delete(d);
            });
        ra.addFlashAttribute("successMsg", "Depense supprimee.");
        return "redirect:/finances?tab=depenses";
    }
}
