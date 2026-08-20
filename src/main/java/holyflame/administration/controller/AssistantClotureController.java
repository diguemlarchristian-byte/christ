package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.AnneeScolaireService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.HorlogeService;
import holyflame.administration.service.JournalService;
import holyflame.administration.service.PassageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assistant guide de cloture de fin d'annee : enchaine dans le bon ordre les briques deja
 * existantes (sante de la saisie/decisions, deliberations/PV, apercu du passage, puis
 * activation + cloture reelle) plutot que de laisser l'utilisateur naviguer seul entre
 * /passage et /parametres/annees. Reserve a l'ADMIN (voir SecurityConfig).
 */
@Controller
@RequestMapping("/passage/assistant")
public class AssistantClotureController {

    @Autowired private ClasseRepository classeRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private DecisionPassageRepository decisionPassageRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private EnseignantAutorisationRepository enseignantAutorisationRepository;
    @Autowired private SeanceDeliberationRepository seanceDeliberationRepository;
    @Autowired private AnneeScolaireRepository anneeScolaireRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private AnneeScolaireService anneeScolaireService;
    @Autowired private PassageService passageService;
    @Autowired private JournalService journalService;
    @Autowired private HorlogeService horlogeService;
    @Autowired private holyflame.administration.service.AssistantClotureService assistantClotureService;

    @GetMapping
    public String index() {
        return "redirect:/passage/assistant/verification";
    }

    // ===== ETAPE 1 : VERIFICATION =====
    @GetMapping("/verification")
    public String verification(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActive = etablissementService.getAnneeScolaireActive();
        List<Classe> classes = classesActives(etabId, anneeActive);
        List<EnseignantAutorisation> autorisations = enseignantAutorisationRepository.findByEtablissementId(etabId);

        List<Map<String, Object>> lignes = new ArrayList<>();
        int classesManquantes = 0;
        long elevesSansDecisionTotal = 0;
        double sommeCouverture = 0;

        for (Classe c : classes) {
            long totalEleves = eleveRepository.countByClasseId(c.getId());
            long decisions = decisionPassageRepository.findByClasseOrigineIdAndAnneeScolaire(c.getId(), anneeActive).size();
            long elevesSansDecision = Math.max(0, totalEleves - decisions);

            Set<Long> matieresAssignees = autorisations.stream()
                .filter(a -> c.getId().equals(a.getClasseId()))
                .map(EnseignantAutorisation::getMatiereId)
                .collect(Collectors.toSet());
            Set<Long> matieresAvecNotes = new HashSet<>(
                noteRepository.findDistinctMatiereIdByClasseIdAndAnneeScolaire(c.getId(), anneeActive));
            double couverture = matieresAssignees.isEmpty() ? 100.0
                : 100.0 * matieresAssignees.stream().filter(matieresAvecNotes::contains).count() / matieresAssignees.size();

            String statut;
            if (!matieresAssignees.isEmpty() && matieresAvecNotes.isEmpty()) statut = "MANQUANT";
            else if (couverture < 100) statut = "VERIFICATION_MANUELLE";
            else if (elevesSansDecision > 0) statut = "ELEVES_SANS_DECISION";
            else statut = "VERIFIE";

            if ("MANQUANT".equals(statut)) classesManquantes++;
            elevesSansDecisionTotal += elevesSansDecision;
            sommeCouverture += couverture;

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("classe", c);
            ligne.put("totalEleves", totalEleves);
            ligne.put("elevesSansDecision", elevesSansDecision);
            ligne.put("couverture", Math.round(couverture));
            ligne.put("statut", statut);
            lignes.add(ligne);
        }

        model.addAttribute("lignes", lignes);
        model.addAttribute("pourcentageNotesGlobal", classes.isEmpty() ? 100 : Math.round(sommeCouverture / classes.size()));
        model.addAttribute("classesManquantes", classesManquantes);
        model.addAttribute("elevesSansDecisionTotal", elevesSansDecisionTotal);
        model.addAttribute("anneeActive", anneeActive);
        model.addAttribute("etapeActuelle", 1);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "assistant-cloture-verification";
    }

    // ===== ETAPE 2 : DELIBERATIONS =====
    @GetMapping("/deliberations")
    public String deliberations(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActive = etablissementService.getAnneeScolaireActive();
        List<Classe> classes = classesActives(etabId, anneeActive);

        List<Map<String, Object>> lignes = new ArrayList<>();
        int classesValidees = 0;
        for (Classe c : classes) {
            long totalEleves = eleveRepository.countByClasseId(c.getId());
            long decisions = decisionPassageRepository.findByClasseOrigineIdAndAnneeScolaire(c.getId(), anneeActive).size();
            SeanceDeliberation seance = seanceDeliberationRepository
                .findByClasseIdAndAnneeScolaireOrderByDateSeanceDesc(c.getId(), anneeActive).stream()
                .filter(s -> s.getTrimestre() == null)
                .findFirst().orElse(null);
            String statutPv = seance == null ? "NON_CREE" : "CLOTUREE".equals(seance.getStatut()) ? "VALIDE" : "EN_ATTENTE";
            if ("VALIDE".equals(statutPv)) classesValidees++;

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("classe", c);
            ligne.put("totalEleves", totalEleves);
            ligne.put("avancement", totalEleves > 0 ? Math.round(100.0 * decisions / totalEleves) : 0);
            ligne.put("statutPv", statutPv);
            lignes.add(ligne);
        }

        model.addAttribute("lignes", lignes);
        model.addAttribute("classesValidees", classesValidees);
        model.addAttribute("totalClasses", classes.size());
        model.addAttribute("anneeActive", anneeActive);
        model.addAttribute("etapeActuelle", 2);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "assistant-cloture-deliberations";
    }

    @PostMapping("/deliberations/{classeId}/valider-pv")
    public String validerPv(@PathVariable Long classeId, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId())).orElse(null);
        if (classe == null) {
            ra.addFlashAttribute("erreurMsg", "Classe introuvable.");
            return "redirect:/passage/assistant/deliberations";
        }
        String anneeActive = etablissementService.getAnneeScolaireActive();
        SeanceDeliberation seance = seanceDeliberationRepository
            .findByClasseIdAndAnneeScolaireOrderByDateSeanceDesc(classeId, anneeActive).stream()
            .filter(s -> s.getTrimestre() == null)
            .findFirst().orElse(null);
        if (seance == null) {
            seance = new SeanceDeliberation();
            seance.setClasse(classe);
            seance.setAnneeScolaire(anneeActive);
            seance.setTrimestre(null);
            seance.setDateSeance(horlogeService.aujourdHui());
            Utilisateur u = etablissementService.getCurrentUtilisateur();
            seance.setRapporteurNom(u != null ? u.getPrenom() + " " + u.getNom() : "Administration");
            seance.setStatut("EN_PREPARATION");
            seance.setEtablissementId(etabId);
            seance = seanceDeliberationRepository.save(seance);
        }
        seance.setStatut("CLOTUREE");
        if (seance.getReferencePv() == null || seance.getReferencePv().isBlank()) {
            seance.setReferencePv("PV-DEL-" + anneeActive + "-FINAL-" + classe.getNom().replaceAll("\\s+", "") + "-" + seance.getId());
        }
        seanceDeliberationRepository.save(seance);
        journalService.log("PV_GENERE", "ELEVES", "PV de délibération de fin d'année généré pour " + classe.getNom());
        ra.addFlashAttribute("successMsg", "PV validé pour " + classe.getNom() + ".");
        return "redirect:/passage/assistant/deliberations";
    }

    // ===== ETAPE 3 : PASSAGE & AFFECTATION (lecture seule, calculee) =====
    @GetMapping("/affectation")
    public String affectation(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String ancienneAnnee = etablissementService.getAnneeScolaireActive();
        String nouvelleAnnee = passageService.anneeSuivante(ancienneAnnee);
        boolean anneePreparee = anneeScolaireRepository.existsByEtablissementIdAndLibelle(etabId, nouvelleAnnee);
        List<Classe> classes = classesActives(etabId, ancienneAnnee);

        List<Map<String, Object>> mapping = new ArrayList<>();
        Map<String, long[]> recap = new LinkedHashMap<>(); // nom cible -> {admis, redoublants}

        for (Classe c : classes) {
            List<DecisionPassage> decisions = decisionPassageRepository.findByClasseOrigineIdAndAnneeScolaire(c.getId(), ancienneAnnee);
            long admis = decisions.stream().filter(d -> "ADMIS".equals(d.getDecision())).count();
            long redoublants = decisions.stream().filter(d -> "REDOUBLE".equals(d.getDecision())).count();
            long sortants = decisions.stream().filter(d -> "SORT".equals(d.getDecision())).count();
            String niveauSuivantVal = passageService.niveauSuivant(c.getNiveau());

            if (redoublants > 0) recap.computeIfAbsent(c.getNom(), k -> new long[2])[1] += redoublants;
            if (admis > 0 && niveauSuivantVal != null) {
                recap.computeIfAbsent(passageService.nomClasseCible(c, niveauSuivantVal), k -> new long[2])[0] += admis;
            }

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("classeOrigine", c);
            ligne.put("niveauCibleAdmis", niveauSuivantVal != null ? niveauSuivantVal : "Fin de cycle");
            ligne.put("admis", admis);
            ligne.put("redoublants", redoublants);
            ligne.put("sortants", sortants);
            mapping.add(ligne);
        }

        List<Map<String, Object>> recapFinal = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : recap.entrySet()) {
            long admisN = entry.getValue()[0], redoublantsN = entry.getValue()[1], total = admisN + redoublantsN;
            Classe classeCible = anneePreparee
                ? classeRepository.findByNomIgnoreCaseAndAnneeScolaireAndEtablissementId(entry.getKey(), nouvelleAnnee, etabId).orElse(null)
                : null;
            Integer capacite = classeCible != null ? classeCible.getCapacite() : null;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("nom", entry.getKey());
            r.put("admis", admisN);
            r.put("redoublants", redoublantsN);
            r.put("total", total);
            r.put("capacite", capacite);
            r.put("surcapacite", capacite != null && capacite > 0 && total > capacite);
            r.put("existeDeja", classeCible != null);
            recapFinal.add(r);
        }

        model.addAttribute("mapping", mapping);
        model.addAttribute("recap", recapFinal);
        model.addAttribute("anneePreparee", anneePreparee);
        model.addAttribute("ancienneAnnee", ancienneAnnee);
        model.addAttribute("nouvelleAnnee", nouvelleAnnee);
        model.addAttribute("etapeActuelle", 3);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "assistant-cloture-affectation";
    }

    @PostMapping("/affectation/preparer-annee")
    public String preparerAnnee(RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String ancienneAnnee = etablissementService.getAnneeScolaireActive();
        String nouvelleAnnee = passageService.anneeSuivante(ancienneAnnee);
        if (anneeScolaireRepository.existsByEtablissementIdAndLibelle(etabId, nouvelleAnnee)) {
            ra.addFlashAttribute("successMsg", "L'année " + nouvelleAnnee + " existe déjà.");
            return "redirect:/passage/assistant/affectation";
        }
        anneeScolaireService.creer(nouvelleAnnee, etabId, ancienneAnnee, true, true, true);
        journalService.log("ANNEE_PREPAREE", "ELEVES",
            "Année " + nouvelleAnnee + " créée (classes, budget et affectations enseignants dupliqués depuis " + ancienneAnnee + ")");
        ra.addFlashAttribute("successMsg", "Année " + nouvelleAnnee + " préparée : classes, budget et affectations enseignants dupliqués depuis " + ancienneAnnee + ".");
        return "redirect:/passage/assistant/affectation";
    }

    // ===== ETAPE 4 : CLOTURE & ARCHIVAGE =====
    @GetMapping("/cloture")
    public String cloture(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActive = etablissementService.getAnneeScolaireActive();
        List<Classe> classes = classesActives(etabId, anneeActive);

        long totalEleves = 0, totalAdmisPrevus = 0, totalRedoublantsPrevus = 0, totalSortantsPrevus = 0;
        List<String> classesIncompletes = new ArrayList<>();
        for (Classe c : classes) {
            long effectif = eleveRepository.countByClasseId(c.getId());
            totalEleves += effectif;
            List<DecisionPassage> decisions = decisionPassageRepository.findByClasseOrigineIdAndAnneeScolaire(c.getId(), anneeActive);
            if (effectif > 0 && decisions.size() < effectif) classesIncompletes.add(c.getNom());
            totalAdmisPrevus += decisions.stream().filter(d -> "ADMIS".equals(d.getDecision())).count();
            totalRedoublantsPrevus += decisions.stream().filter(d -> "REDOUBLE".equals(d.getDecision())).count();
            totalSortantsPrevus += decisions.stream().filter(d -> "SORT".equals(d.getDecision())).count();
        }

        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId).stream()
            .filter(p -> anneeActive.equals(p.getAnneeScolaire())).toList();
        List<Depense> depenses = depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId).stream()
            .filter(d -> anneeActive.equals(d.getAnneeScolaire())).toList();
        double totalEncaisse = paiements.stream().mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        double totalDepense = depenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        model.addAttribute("totalEleves", totalEleves);
        model.addAttribute("totalAdmisPrevus", totalAdmisPrevus);
        model.addAttribute("totalRedoublantsPrevus", totalRedoublantsPrevus);
        model.addAttribute("totalSortantsPrevus", totalSortantsPrevus);
        model.addAttribute("classesIncompletes", classesIncompletes);
        model.addAttribute("pretPourCloture", classesIncompletes.isEmpty());
        model.addAttribute("totalEncaisse", totalEncaisse);
        model.addAttribute("totalDepense", totalDepense);
        model.addAttribute("soldeAnnee", totalEncaisse - totalDepense);
        model.addAttribute("anneeActive", anneeActive);
        model.addAttribute("nouvelleAnnee", passageService.anneeSuivante(anneeActive));
        model.addAttribute("etapeActuelle", 4);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "assistant-cloture-cloture";
    }

    /** Export JSON telechargeable des donnees critiques de l'annee, a conserver avant la cloture
        irreversible — pas un vrai dump binaire de base de donnees (fragile a operer depuis
        l'application), mais un instantane exploitable des donnees academiques et financieres. */
    @GetMapping("/cloture/export")
    public void exporterSnapshot(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActive = etablissementService.getAnneeScolaireActive();
        List<Classe> classes = classesActives(etabId, anneeActive);

        List<Map<String, Object>> elevesExport = new ArrayList<>();
        List<Map<String, Object>> decisionsExport = new ArrayList<>();
        for (Classe c : classes) {
            for (Eleve e : eleveRepository.findByClasseIdOrderByNomAsc(c.getId())) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("matricule", e.getMatricule());
                em.put("nom", e.getNom());
                em.put("prenom", e.getPrenom());
                em.put("classe", c.getNom());
                em.put("statutInscription", e.getStatutInscription());
                elevesExport.add(em);
            }
            for (DecisionPassage d : decisionPassageRepository.findByClasseOrigineIdAndAnneeScolaire(c.getId(), anneeActive)) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("eleve", d.getEleve() != null ? d.getEleve().getNom() + " " + d.getEleve().getPrenom() : null);
                dm.put("classeOrigine", c.getNom());
                dm.put("decision", d.getDecision());
                dm.put("moyenne", d.getMoyenne());
                decisionsExport.add(dm);
            }
        }
        double totalEncaisse = paiementRepository.findByEtablissementId(etabId).stream()
            .filter(p -> anneeActive.equals(p.getAnneeScolaire()))
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        double totalDepense = depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId).stream()
            .filter(d -> anneeActive.equals(d.getAnneeScolaire()) && "CHARGE".equals(d.getSens()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("anneeScolaire", anneeActive);
        export.put("dateExport", horlogeService.maintenant().toString());
        export.put("nombreClasses", classes.size());
        export.put("eleves", elevesExport);
        export.put("decisionsPassage", decisionsExport);
        Map<String, Object> bilanFinancier = new LinkedHashMap<>();
        bilanFinancier.put("totalEncaisse", totalEncaisse);
        bilanFinancier.put("totalDepense", totalDepense);
        bilanFinancier.put("solde", totalEncaisse - totalDepense);
        export.put("bilanFinancier", bilanFinancier);

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"cloture-" + anneeActive + ".json\"");
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValue(response.getOutputStream(), export);
    }

    @PostMapping("/cloture/executer")
    public String executerCloture(@RequestParam(required = false) String confirmation, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (!"on".equals(confirmation) && !"true".equals(confirmation)) {
            ra.addFlashAttribute("erreurMsg", "Vous devez cocher la case de confirmation avant de clôturer.");
            return "redirect:/passage/assistant/cloture";
        }
        String ancienneAnnee = etablissementService.getAnneeScolaireActive();
        String nouvelleAnnee = passageService.anneeSuivante(ancienneAnnee);
        List<Classe> classes = classesActives(etabId, ancienneAnnee);

        // Validation prealable (avant toute ecriture) : on refuse net plutot que de cloturer a moitie.
        List<String> classesIncompletes = new ArrayList<>();
        for (Classe c : classes) {
            long effectif = eleveRepository.countByClasseId(c.getId());
            long decisions = decisionPassageRepository.findByClasseOrigineIdAndAnneeScolaire(c.getId(), ancienneAnnee).size();
            if (effectif > 0 && decisions < effectif) classesIncompletes.add(c.getNom());
        }
        if (!classesIncompletes.isEmpty()) {
            ra.addFlashAttribute("erreurMsg", "Décisions incomplètes pour : " + String.join(", ", classesIncompletes)
                + ". Terminez les délibérations avant de clôturer.");
            return "redirect:/passage/assistant/cloture";
        }

        assistantClotureService.executerCloture(etabId, ancienneAnnee, nouvelleAnnee, classes);

        ra.addFlashAttribute("successMsg", "Année scolaire " + ancienneAnnee + " clôturée avec succès. "
            + nouvelleAnnee + " est maintenant l'année active.");
        return "redirect:/dashboard";
    }

    private List<Classe> classesActives(Long etabId, String anneeActive) {
        return classeRepository.findByAnneeScolaireAndEtablissementId(anneeActive, etabId).stream()
            .sorted(Comparator.comparing(Classe::getNom))
            .collect(Collectors.toList());
    }
}
