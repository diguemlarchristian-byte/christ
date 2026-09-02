package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.AnneeScolaireService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import holyflame.administration.util.AnneeScolaireUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/infirmerie")
public class InfirmerieController {

    @Autowired private ConsultationInfirmerieRepository consultationRepository;
    @Autowired private ConditionMedicaleRepository conditionMedicaleRepository;
    @Autowired private ArticleInfirmerieRepository articleInfirmerieRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private AnneeScolaireService anneeScolaireService;
    @Autowired private JournalService journalService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    private static final Map<String, String> MOTIFS = new LinkedHashMap<>() {{
        put("MAUX_TETE", "Maux de tête");
        put("FIEVRE", "Fièvre");
        put("DOULEUR_ABDOMINALE", "Douleur abdominale");
        put("CHUTE_BLESSURE", "Chute / Blessure");
        put("MALAISE", "Malaise");
        put("CRISE_ASTHME", "Crise d'asthme");
        put("REACTION_ALLERGIQUE", "Réaction allergique");
        put("AUTRE", "Autre");
    }};

    private static final Map<String, String> ORIENTATIONS = new LinkedHashMap<>() {{
        put("RETOUR_CLASSE", "Retour en classe");
        put("REPOS_INFIRMERIE", "Repos à l'infirmerie");
        put("RENVOYE_DOMICILE", "Renvoyé chez lui");
        put("HOPITAL_SAMU", "Hôpital / SAMU");
    }};

    private static final String[] JOURS_COURTS = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};

    /** En dessous de ce niveau de remplissage, l'article est signale comme a reapprovisionner. */
    private static final int SEUIL_STOCK_CRITIQUE = 20;

    @GetMapping
    public String dashboard(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDate aujourdHui = horlogeService.aujourdHui();
        LocalDateTime debutJour = aujourdHui.atStartOfDay();
        LocalDateTime finJour = aujourdHui.atTime(23, 59, 59);

        List<ConsultationInfirmerie> consultationsAujourdhui =
            consultationRepository.findByEtablissementIdAndDateHeureBetween(etabId, debutJour, finJour);

        List<ConsultationInfirmerie> enCours = consultationRepository.findByEtablissementIdAndStatutOrderByDateHeureDesc(etabId, "EN_COURS");
        List<ConsultationInfirmerie> enObservation = enCours.stream()
            .filter(c -> "REPOS_INFIRMERIE".equals(c.getOrientation())).toList();
        List<ConsultationInfirmerie> alertesActives = enCours.stream()
            .filter(c -> "HOPITAL_SAMU".equals(c.getOrientation())).toList();

        List<ConsultationInfirmerie> recentes = consultationRepository.findByEtablissementIdOrderByDateHeureDesc(etabId)
            .stream().limit(10).toList();

        List<ConditionMedicale> conditionsActives = conditionMedicaleRepository.findByEtablissementIdAndActifTrueOrderByLibelleAsc(etabId);
        Map<Long, List<ConditionMedicale>> conditionsParEleve = conditionsActives.stream()
            .collect(Collectors.groupingBy(c -> c.getEleve().getId()));

        List<Map<String, Object>> frequence = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate jour = aujourdHui.minusDays(i);
            long total = consultationRepository.findByEtablissementIdAndDateHeureBetween(
                etabId, jour.atStartOfDay(), jour.atTime(23, 59, 59)).size();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("jour", JOURS_COURTS[jour.getDayOfWeek().getValue() - 1]);
            point.put("total", total);
            frequence.add(point);
        }
        long maxFrequence = Math.max(1, frequence.stream().mapToLong(p -> (long) p.get("total")).max().orElse(1));

        // Trie du plus critique au mieux approvisionne : un article epuise ne doit pas se perdre
        // au milieu d'une liste alphabetique, l'infirmier doit le voir avant de recevoir un eleve.
        List<ArticleInfirmerie> stocks = articleInfirmerieRepository.findByEtablissementIdOrderByDesignationAsc(etabId)
            .stream()
            .sorted(java.util.Comparator.comparingInt(ArticleInfirmerie::getPourcentage))
            .toList();
        model.addAttribute("stocksCritiques", stocks.stream().filter(s -> s.getPourcentage() <= SEUIL_STOCK_CRITIQUE).count());

        Map<String, Long> paiCounts = conditionsActives.stream()
            .collect(Collectors.groupingBy(ConditionMedicale::getLibelle, LinkedHashMap::new, Collectors.counting()));

        model.addAttribute("visitesAujourdhui", consultationsAujourdhui.size());
        model.addAttribute("enObservation", enObservation);
        model.addAttribute("alertesActives", alertesActives);
        model.addAttribute("consultationsRecentes", recentes);
        model.addAttribute("conditionsParEleve", conditionsParEleve);
        model.addAttribute("frequence", frequence);
        model.addAttribute("maxFrequence", maxFrequence);
        model.addAttribute("stocks", stocks);
        model.addAttribute("paiCounts", paiCounts);
        model.addAttribute("motifs", MOTIFS);
        model.addAttribute("orientations", ORIENTATIONS);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "infirmerie-dashboard";
    }

    @GetMapping("/eleves/recherche")
    @ResponseBody
    public List<Map<String, Object>> rechercherEleves(@RequestParam String q) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (q == null || q.isBlank()) return List.of();
        return eleveRepository.searchByEtablissement(q.trim(), etabId).stream()
            .limit(10)
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getId());
                m.put("nom", e.getNom() + " " + e.getPrenom());
                m.put("classe", e.getClasse() != null ? e.getClasse().getNom() : "");
                return m;
            }).toList();
    }

    @PostMapping("/consultations")
    public String ajouterConsultation(
            @RequestParam Long eleveId,
            @RequestParam String motif,
            @RequestParam(required = false) String observations,
            @RequestParam(required = false) String orientation,
            @RequestParam(defaultValue = "false") boolean parentsInformes,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId)
            .filter(e -> etabId != null && etabId.equals(e.getEtablissementId()))
            .orElse(null);
        if (eleve == null) {
            ra.addFlashAttribute("erreurMsg", "Élève introuvable dans cet établissement.");
            return "redirect:/infirmerie";
        }

        LocalDateTime maintenant = horlogeService.maintenant();
        String anneeScolaireConsult = AnneeScolaireUtil.pour(maintenant.toLocalDate());
        anneeScolaireService.verifierModifiable(anneeScolaireConsult, etabId);

        ConsultationInfirmerie c = new ConsultationInfirmerie();
        c.setEleve(eleve);
        c.setDateHeure(maintenant);
        c.setMotif(motif);
        c.setObservations(observations);
        c.setParentsInformes(parentsInformes);
        c.setOrientation(orientation != null && !orientation.isBlank() ? orientation : "RETOUR_CLASSE");
        c.setStatut("REPOS_INFIRMERIE".equals(c.getOrientation()) || "HOPITAL_SAMU".equals(c.getOrientation()) ? "EN_COURS" : "TERMINEE");
        c.setAnneeScolaire(anneeScolaireConsult);
        c.setEtablissementId(etabId);
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) c.setInfirmierParId(utilisateur.getId());
        consultationRepository.save(c);

        journalService.log("CONSULTATION_INFIRMERIE", "INFIRMERIE",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + MOTIFS.getOrDefault(motif, motif));

        ra.addFlashAttribute("successMsg", "Consultation enregistrée pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        return "redirect:/infirmerie";
    }

    @GetMapping("/consultations")
    public String historique(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("consultations", consultationRepository.findByEtablissementIdOrderByDateHeureDesc(etabId));
        model.addAttribute("motifs", MOTIFS);
        model.addAttribute("orientations", ORIENTATIONS);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "infirmerie-historique";
    }

    @PostMapping("/consultations/{id}/cloturer")
    public String cloturerConsultation(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        consultationRepository.findById(id)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .ifPresent(c -> {
                c.setStatut("TERMINEE");
                consultationRepository.save(c);
            });
        ra.addFlashAttribute("successMsg", "Prise en charge clôturée.");
        return "redirect:/infirmerie";
    }

    // ── PAI / Allergies ──
    @GetMapping("/pai")
    public String pai(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("conditions", conditionMedicaleRepository.findByEtablissementIdAndActifTrueOrderByLibelleAsc(etabId));
        model.addAttribute("eleves", eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "infirmerie-pai";
    }

    @PostMapping("/pai")
    public String ajouterPai(@RequestParam Long eleveId, @RequestParam String type,
                              @RequestParam String libelle, @RequestParam(required = false) String protocole,
                              RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId)
            .filter(e -> etabId != null && etabId.equals(e.getEtablissementId()))
            .orElse(null);
        if (eleve == null) {
            ra.addFlashAttribute("erreurMsg", "Élève introuvable dans cet établissement.");
            return "redirect:/infirmerie/pai";
        }
        ConditionMedicale c = new ConditionMedicale();
        c.setEleve(eleve);
        c.setType(type);
        c.setLibelle(libelle);
        c.setProtocole(protocole);
        c.setActif(true);
        c.setEtablissementId(etabId);
        conditionMedicaleRepository.save(c);
        ra.addFlashAttribute("successMsg", "Fiche médicale ajoutée pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        return "redirect:/infirmerie/pai";
    }

    @PostMapping("/pai/{id}/desactiver")
    public String desactiverPai(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        conditionMedicaleRepository.findById(id)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .ifPresent(c -> { c.setActif(false); conditionMedicaleRepository.save(c); });
        ra.addFlashAttribute("successMsg", "Fiche médicale désactivée.");
        return "redirect:/infirmerie/pai";
    }

    // ── Stock ──
    @GetMapping("/stock")
    public String stock(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("stocks", articleInfirmerieRepository.findByEtablissementIdOrderByDesignationAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "infirmerie-stock";
    }

    @PostMapping("/stock")
    public String ajouterArticle(@RequestParam String designation, @RequestParam Integer quantiteActuelle,
                                  @RequestParam Integer quantiteMax, @RequestParam(required = false) String unite,
                                  RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        ArticleInfirmerie a = new ArticleInfirmerie();
        a.setDesignation(designation);
        a.setQuantiteActuelle(quantiteActuelle);
        a.setQuantiteMax(quantiteMax);
        a.setUnite(unite);
        a.setEtablissementId(etabId);
        articleInfirmerieRepository.save(a);
        ra.addFlashAttribute("successMsg", "Article ajouté au stock.");
        return "redirect:/infirmerie/stock";
    }

    @PostMapping("/stock/{id}/ajuster")
    public String ajusterStock(@PathVariable Long id, @RequestParam Integer quantiteActuelle, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        articleInfirmerieRepository.findById(id)
            .filter(a -> etabId != null && etabId.equals(a.getEtablissementId()))
            .ifPresent(a -> { a.setQuantiteActuelle(quantiteActuelle); articleInfirmerieRepository.save(a); });
        ra.addFlashAttribute("successMsg", "Stock mis à jour.");
        return "redirect:/infirmerie/stock";
    }
}
