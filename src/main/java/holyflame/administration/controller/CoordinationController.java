package holyflame.administration.controller;

import holyflame.administration.model.AvisParent;
import holyflame.administration.model.Classe;
import holyflame.administration.model.FicheControle;
import holyflame.administration.model.FicheControleReponse;
import holyflame.administration.model.Note;
import holyflame.administration.model.Personnel;
import holyflame.administration.repository.AvisParentRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.FicheControleReponseRepository;
import holyflame.administration.repository.FicheControleRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.PersonnelRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import holyflame.administration.util.CriteresControle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/coordination")
public class CoordinationController {

    private static final List<String> ECHELLE_NOTE = List.of("INSUFFISANT", "MOYEN", "BIEN", "TRES_BIEN");

    @Autowired private FicheControleRepository ficheControleRepository;
    @Autowired private FicheControleReponseRepository reponseRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private NoteRepository noteRepository;
    @Autowired private AvisParentRepository avisParentRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired private JournalService journalService;

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<FicheControle> fiches = ficheControleRepository.findByEtablissementIdOrderByDateVisiteDesc(etabId);

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("fiches", fiches);
        model.addAttribute("nbFiches", fiches.size());
        model.addAttribute("nbEnseignantsVisites", fiches.stream()
            .filter(f -> f.getEnseignant() != null)
            .map(f -> f.getEnseignant().getId()).distinct().count());

        List<Map<String, Object>> evolution = evolutionParEnseignant(fiches);
        model.addAttribute("evolutionEnseignants", evolution);
        model.addAttribute("qualiteNotes", qualiteDesNotes(etabId));
        model.addAttribute("avisRecurrents", avisRecurrents(etabId));

        // Le tableau d'evolution se construit a partir des fiches : un enseignant jamais visite
        // n'y figure donc pas du tout. C'est pourtant lui que le coordonnateur doit programmer.
        Set<Long> dejaVisites = fiches.stream()
            .filter(f -> f.getEnseignant() != null)
            .map(f -> f.getEnseignant().getId())
            .collect(Collectors.toSet());
        List<Personnel> jamaisVisites = personnelRepository
            .findByFonctionAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId).stream()
            .filter(p -> !dejaVisites.contains(p.getId()))
            .toList();
        model.addAttribute("enseignantsJamaisVisites", jamaisVisites);

        // Ceux qui appellent une action : derniere visite insuffisante, ou note en recul.
        model.addAttribute("enseignantsASuivre", evolution.stream()
            .filter(l -> "INSUFFISANT".equals(l.get("derniereNote")) || "BAISSE".equals(l.get("tendance")))
            .toList());

        return "coordination";
    }

    // ===== Bloc 1 : evolution des enseignants (a partir des fiches de controle) =====
    private List<Map<String, Object>> evolutionParEnseignant(List<FicheControle> fiches) {
        Map<Long, List<FicheControle>> parEnseignant = fiches.stream()
            .filter(f -> f.getEnseignant() != null)
            .collect(Collectors.groupingBy(f -> f.getEnseignant().getId()));

        List<Map<String, Object>> resultat = new ArrayList<>();
        for (List<FicheControle> visites : parEnseignant.values()) {
            visites.sort(Comparator.comparing(FicheControle::getDateVisite));
            FicheControle premiere = visites.get(0);
            FicheControle derniere = visites.get(visites.size() - 1);

            int indicePremiere = premiere.getNoteGlobale() != null ? ECHELLE_NOTE.indexOf(premiere.getNoteGlobale()) : -1;
            int indiceDerniere = derniere.getNoteGlobale() != null ? ECHELLE_NOTE.indexOf(derniere.getNoteGlobale()) : -1;
            String tendance = "N_A";
            if (visites.size() > 1 && indicePremiere >= 0 && indiceDerniere >= 0) {
                tendance = indiceDerniere > indicePremiere ? "HAUSSE" : (indiceDerniere < indicePremiere ? "BAISSE" : "STABLE");
            }

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("enseignant", derniere.getEnseignant());
            ligne.put("nbVisites", visites.size());
            ligne.put("derniereNote", derniere.getNoteGlobale());
            ligne.put("derniereDate", derniere.getDateVisite());
            ligne.put("tendance", tendance);
            ligne.put("historique", visites);
            resultat.add(ligne);
        }
        resultat.sort(Comparator.comparing((Map<String, Object> l) -> (LocalDate) l.get("derniereDate")).reversed());
        return resultat;
    }

    // ===== Bloc 2 : qualite des notes deja saisies dans l'application, par classe et par trimestre =====
    private List<Map<String, Object>> qualiteDesNotes(Long etabId) {
        List<Note> notes = noteRepository.findByEtablissementId(etabId);

        Map<String, Map<Integer, double[]>> parClasse = new LinkedHashMap<>(); // classe -> trimestre -> [sommePonderee, sommeCoef]
        for (Note n : notes) {
            if (n.getEleve() == null || n.getEleve().getClasse() == null || n.getValeur() == null || n.getTrimestre() == null) continue;
            String classe = n.getEleve().getClasse().getNom();
            double coef = n.getCoefficient() != null ? n.getCoefficient() : 1.0;
            double[] acc = parClasse.computeIfAbsent(classe, k -> new LinkedHashMap<>())
                .computeIfAbsent(n.getTrimestre(), t -> new double[2]);
            acc[0] += n.getValeur() * coef;
            acc[1] += coef;
        }

        List<Map<String, Object>> resultat = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, double[]>> entry : parClasse.entrySet()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("classe", entry.getKey());
            Double m1 = moyenneOuNull(entry.getValue().get(1));
            Double m2 = moyenneOuNull(entry.getValue().get(2));
            Double m3 = moyenneOuNull(entry.getValue().get(3));
            ligne.put("t1", m1);
            ligne.put("t2", m2);
            ligne.put("t3", m3);
            Double derniere = m3 != null ? m3 : (m2 != null ? m2 : m1);
            Double premiere = m1 != null ? m1 : (m2 != null ? m2 : m3);
            ligne.put("tendance", (derniere != null && premiere != null && !derniere.equals(premiere))
                ? (derniere > premiere ? "HAUSSE" : "BAISSE") : "STABLE");
            resultat.add(ligne);
        }
        resultat.sort(Comparator.comparing(l -> (String) l.get("classe")));
        return resultat;
    }

    private Double moyenneOuNull(double[] acc) {
        return (acc == null || acc[1] == 0) ? null : Math.round((acc[0] / acc[1]) * 100) / 100.0;
    }

    // ===== Bloc 3 : avis des parents qui se repetent (meme categorie + meme classe, au moins 2 occurrences) =====
    private List<Map<String, Object>> avisRecurrents(Long etabId) {
        List<AvisParent> avis = avisParentRepository.findByEtablissementIdOrderByDateSignalementDesc(etabId);

        Map<String, List<AvisParent>> groupes = avis.stream()
            .filter(a -> a.getEleve() != null && a.getEleve().getClasse() != null)
            .collect(Collectors.groupingBy(a -> a.getCategorie() + "|" + a.getEleve().getClasse().getNom()));

        List<Map<String, Object>> resultat = new ArrayList<>();
        for (List<AvisParent> groupe : groupes.values()) {
            if (groupe.size() < 2) continue;
            AvisParent plusRecent = groupe.stream().max(Comparator.comparing(AvisParent::getDateSignalement)).orElseThrow();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("categorie", plusRecent.getCategorie());
            ligne.put("classe", plusRecent.getEleve().getClasse().getNom());
            ligne.put("nombre", groupe.size());
            ligne.put("dernierSignalement", plusRecent.getDateSignalement());
            ligne.put("avis", groupe.stream().sorted(Comparator.comparing(AvisParent::getDateSignalement).reversed()).toList());
            resultat.add(ligne);
        }
        resultat.sort(Comparator.comparing((Map<String, Object> l) -> (Integer) l.get("nombre")).reversed());
        return resultat;
    }

    @GetMapping("/nouvelle")
    public String nouvelleForm(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("enseignants", personnelRepository.findByFonctionAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId));
        model.addAttribute("criteres", CriteresControle.LISTE);
        model.addAttribute("sections", CriteresControle.LISTE.stream().map(CriteresControle.Critere::section).distinct().toList());
        return "coordination-nouvelle";
    }

    @PostMapping
    public String enregistrer(
            @RequestParam(required = false) Long classeId,
            @RequestParam(required = false) Long enseignantId,
            @RequestParam(required = false) String cours,
            @RequestParam(required = false) Integer effectifs,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateVisite,
            @RequestParam(required = false) String evaluationGlobale,
            @RequestParam(required = false) String noteGlobale,
            @RequestParam Map<String, String> tousLesParametres,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeScolaireFiche = holyflame.administration.util.AnneeScolaireUtil.pour(dateVisite);
        anneeScolaireService.verifierModifiable(anneeScolaireFiche, etabId);

        FicheControle fiche = new FicheControle();
        if (classeId != null) classeRepository.findById(classeId).ifPresent(fiche::setClasse);
        if (enseignantId != null) personnelRepository.findById(enseignantId).ifPresent(fiche::setEnseignant);
        fiche.setCours(cours);
        fiche.setEffectifs(effectifs);
        fiche.setDateVisite(dateVisite);
        fiche.setAnneeScolaire(anneeScolaireFiche);
        fiche.setEvaluationGlobale(evaluationGlobale);
        fiche.setNoteGlobale(noteGlobale);
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) fiche.setCoordonnateurId(utilisateur.getId());
        fiche.setEtablissementId(etabId);
        fiche.setDateCreation(horlogeService.maintenant());
        ficheControleRepository.save(fiche);

        for (int i = 0; i < CriteresControle.LISTE.size(); i++) {
            CriteresControle.Critere c = CriteresControle.LISTE.get(i);
            String appreciation = tousLesParametres.get("appreciation_" + i);
            String conseils = tousLesParametres.get("conseils_" + i);
            if ((appreciation == null || appreciation.isBlank()) && (conseils == null || conseils.isBlank())) continue;
            FicheControleReponse r = new FicheControleReponse();
            r.setFicheControle(fiche);
            r.setSection(c.section());
            r.setCritere(c.libelle());
            r.setAppreciation(appreciation);
            r.setConseils(conseils);
            reponseRepository.save(r);
        }

        journalService.log("FICHE_CONTROLE_ENREGISTREE", "COORDINATION",
            "Visite du " + dateVisite + (fiche.getEnseignant() != null ? " — " + fiche.getEnseignant().getNom() + " " + fiche.getEnseignant().getPrenom() : ""));
        ra.addFlashAttribute("successMsg", "Fiche de controle enregistree.");
        return "redirect:/coordination/" + fiche.getId();
    }

    @GetMapping("/{id}")
    public String voir(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        FicheControle fiche = ficheControleRepository.findById(id).orElse(null);
        if (fiche == null || !etabId.equals(fiche.getEtablissementId())) {
            ra.addFlashAttribute("erreur", "Fiche introuvable.");
            return "redirect:/coordination";
        }
        List<FicheControleReponse> reponses = reponseRepository.findByFicheControleIdOrderByIdAsc(id);
        Map<String, List<FicheControleReponse>> parSection = reponses.stream()
            .collect(Collectors.groupingBy(FicheControleReponse::getSection, LinkedHashMap::new, Collectors.toList()));

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("fiche", fiche);
        model.addAttribute("reponsesParSection", parSection);
        return "coordination-fiche";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        ficheControleRepository.findById(id)
            .filter(f -> etabId.equals(f.getEtablissementId()))
            .ifPresent(f -> {
                journalService.log("FICHE_CONTROLE_SUPPRIMEE", "COORDINATION", "Visite du " + f.getDateVisite());
                reponseRepository.deleteByFicheControleId(f.getId());
                ficheControleRepository.delete(f);
            });
        ra.addFlashAttribute("successMsg", "Fiche supprimee.");
        return "redirect:/coordination";
    }
}
