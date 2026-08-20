package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.BulletinService;
import holyflame.administration.service.CalendrierScolaireService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deliberation de fin d'annee : decide, classe par classe, qui est admis, qui redouble,
 * qui quitte l'etablissement (brouillon des decisions, puis cloture qui applique le passage
 * reel) ; deliberations/conseils de classe trimestriels avec jury et generation de PV ;
 * et reinscription d'un ancien eleve en conservant son matricule.
 */
@Controller
@RequestMapping("/passage")
public class PassageController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private DecisionPassageRepository decisionPassageRepository;
    @Autowired private SeanceDeliberationRepository seanceDeliberationRepository;
    @Autowired private MembreJuryRepository membreJuryRepository;
    @Autowired private BulletinService bulletinService;
    @Autowired private CalendrierScolaireService calendrierScolaireService;
    @Autowired private JournalService journalService;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired private holyflame.administration.service.PassageService passageService;

    private static final DateTimeFormatter FMT_PARAM_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> ROLES_JURY = List.of(
        "Président du Jury", "Professeur Principal", "Professeur", "CPE", "Économe", "Délégué(e) Parents", "Autre");

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActuelle = anneeScolaireActuelle(etabId);

        List<Classe> classes = classeRepository.findByAnneeScolaireAndEtablissementId(anneeActuelle, etabId).stream()
            .sorted(Comparator.comparing(Classe::getNom))
            .collect(Collectors.toList());
        Map<Long, Long> effectifParClasse = new LinkedHashMap<>();
        for (Classe c : classes) effectifParClasse.put(c.getId(), eleveRepository.countByClasseId(c.getId()));

        model.addAttribute("classes", classes);
        model.addAttribute("effectifParClasse", effectifParClasse);
        model.addAttribute("anneeActuelle", anneeActuelle);
        model.addAttribute("nouvelleAnneeSuggeree", passageService.anneeSuivante(anneeActuelle));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("seances", seanceDeliberationRepository.findByEtablissementIdOrderByDateSeanceDesc(etabId));
        return "passage";
    }

    // ===== DELIBERATION DE FIN D'ANNEE : brouillon puis cloture =====

    @GetMapping("/classe/{classeId}")
    public String classe(@PathVariable Long classeId,
                          @RequestParam(required = false) String nouvelleAnnee,
                          Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet établissement."));

        String nouvelleAnneeFinale = nouvelleAnnee != null && !nouvelleAnnee.isBlank()
            ? nouvelleAnnee : passageService.anneeSuivante(classe.getAnneeScolaire());

        LocalDate debutAnnee = lireParametreDate(etabId, "T1_DEBUT");
        LocalDate finAnnee = lireParametreDate(etabId, "T3_FIN");

        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeId);
        Map<Long, DecisionPassage> decisionParEleve = decisionPassageRepository
            .findByClasseOrigineIdAndAnneeScolaire(classeId, classe.getAnneeScolaire()).stream()
            .collect(Collectors.toMap(d -> d.getEleve().getId(), d -> d, (a, b) -> a));

        List<Map<String, Object>> lignes = new ArrayList<>();
        int nbAdmis = 0, nbRedouble = 0, nbSort = 0, nbDecides = 0;
        double sommeMoyennes = 0;
        for (Eleve e : eleves) {
            double moyenne = bulletinService.calculerMoyenneAnnuelle(e, classe.getAnneeScolaire());
            long absencesNonJustifiees = compterAbsencesNonJustifiees(e, debutAnnee, finAnnee);
            DecisionPassage existante = decisionParEleve.get(e.getId());
            boolean decide = existante != null;
            String decision = decide ? existante.getDecision() : (moyenne >= 10 ? "ADMIS" : "REDOUBLE");
            if (decide) nbDecides++;
            if ("ADMIS".equals(decision)) nbAdmis++;
            else if ("REDOUBLE".equals(decision)) nbRedouble++;
            else nbSort++;
            sommeMoyennes += moyenne;

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("eleve", e);
            ligne.put("moyenne", Math.round(moyenne * 100.0) / 100.0);
            ligne.put("absences", absencesNonJustifiees);
            ligne.put("decision", decision);
            ligne.put("decide", decide);
            lignes.add(ligne);
        }

        int total = eleves.size();
        model.addAttribute("classe", classe);
        model.addAttribute("lignes", lignes);
        model.addAttribute("nouvelleAnnee", nouvelleAnneeFinale);
        model.addAttribute("niveauSuivant", passageService.niveauSuivant(classe.getNiveau()));
        model.addAttribute("tauxReussite", total > 0 ? Math.round(100.0 * nbAdmis / total) : 0);
        model.addAttribute("tauxRedoublement", total > 0 ? Math.round(100.0 * nbRedouble / total) : 0);
        model.addAttribute("tauxExclusion", total > 0 ? Math.round(100.0 * nbSort / total) : 0);
        model.addAttribute("moyenneClasse", total > 0 ? Math.round(sommeMoyennes / total * 100.0) / 100.0 : 0);
        model.addAttribute("decisionsValidees", nbDecides);
        model.addAttribute("totalEleves", total);
        model.addAttribute("avancement", total > 0 ? Math.round(100.0 * nbDecides / total) : 0);
        model.addAttribute("clotureable", total > 0 && nbDecides == total);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "passage-classe";
    }

    @PostMapping("/classe/{classeId}/decider")
    public String deciderUnEleve(@PathVariable Long classeId, @RequestParam Long eleveId,
                                  @RequestParam String decision, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet établissement."));
        Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
        if (eleve != null && eleve.getClasse() != null && classeId.equals(eleve.getClasse().getId())) {
            enregistrerDecision(eleve, classe, decision);
        }
        return "redirect:/passage/classe/" + classeId;
    }

    @PostMapping("/classe/{classeId}/admettre-si-moyenne")
    public String admettreSiMoyenne(@PathVariable Long classeId, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet établissement."));

        int nb = 0;
        for (Eleve e : eleveRepository.findByClasseIdOrderByNomAsc(classeId)) {
            if (decisionPassageRepository.findByEleveIdAndAnneeScolaire(e.getId(), classe.getAnneeScolaire()).isPresent()) continue;
            double moyenne = bulletinService.calculerMoyenneAnnuelle(e, classe.getAnneeScolaire());
            if (moyenne >= 10) {
                enregistrerDecision(e, classe, "ADMIS");
                nb++;
            }
        }
        ra.addFlashAttribute("successMsg", nb + " élève(s) admis automatiquement (moyenne ≥ 10).");
        return "redirect:/passage/classe/" + classeId;
    }

    @PostMapping("/classe/{classeId}/enregistrer-tout")
    public String enregistrerTout(@PathVariable Long classeId, @RequestParam Map<String, String> allParams, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet établissement."));

        int nb = 0;
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (!entry.getKey().startsWith("decision_")) continue;
            Long eleveId;
            try {
                eleveId = Long.parseLong(entry.getKey().substring("decision_".length()));
            } catch (NumberFormatException ex) {
                continue;
            }
            Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
            if (eleve == null || eleve.getClasse() == null || !classeId.equals(eleve.getClasse().getId())) continue;
            enregistrerDecision(eleve, classe, entry.getValue());
            nb++;
        }
        ra.addFlashAttribute("successMsg", nb + " décision(s) enregistrée(s).");
        return "redirect:/passage/classe/" + classeId;
    }

    private void enregistrerDecision(Eleve eleve, Classe classeOrigine, String decision) {
        anneeScolaireService.verifierModifiable(classeOrigine.getAnneeScolaire(), classeOrigine.getEtablissementId());
        double moyenne = bulletinService.calculerMoyenneAnnuelle(eleve, classeOrigine.getAnneeScolaire());
        DecisionPassage d = decisionPassageRepository
            .findByEleveIdAndAnneeScolaire(eleve.getId(), classeOrigine.getAnneeScolaire())
            .orElseGet(DecisionPassage::new);
        d.setEleve(eleve);
        d.setClasseOrigine(classeOrigine);
        d.setAnneeScolaire(classeOrigine.getAnneeScolaire());
        d.setDecision(decision);
        d.setMoyenne(Math.round(moyenne * 100.0) / 100.0);
        d.setDateDecision(horlogeService.aujourdHui());
        Utilisateur u = etablissementService.getCurrentUtilisateur();
        d.setDecideParId(u != null ? u.getId() : null);
        d.setEtablissementId(classeOrigine.getEtablissementId());
        decisionPassageRepository.save(d);
    }

    @PostMapping("/classe/{classeId}/cloturer")
    public String cloturer(@PathVariable Long classeId, @RequestParam String nouvelleAnnee, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classeOrigine = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet établissement."));

        holyflame.administration.service.PassageService.ResultatCloture resultat =
            passageService.cloturerClasse(classeOrigine, nouvelleAnnee, etabId);
        if (!resultat.succes) {
            ra.addFlashAttribute("erreurMsg", resultat.erreur);
            return "redirect:/passage/classe/" + classeId;
        }

        ra.addFlashAttribute("successMsg", "Délibération clôturée : " + resultat.nbAdmis + " admis, "
            + resultat.nbRedouble + " redoublant(s), " + resultat.nbSortis + " sortant(s).");
        return "redirect:/passage";
    }

    private long compterAbsencesNonJustifiees(Eleve eleve, LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) return absenceRepository.findByEleveIdOrderByDateDesc(eleve.getId()).stream()
            .filter(a -> !a.isEstJustifiee()).count();
        return absenceRepository.findByEleveIdAndDateBetween(eleve.getId(), debut, fin).stream()
            .filter(a -> !a.isEstJustifiee()).count();
    }


    private String anneeScolaireActuelle(Long etabId) {
        return etablissementService.getAnneeScolaireActive();
    }

    private LocalDate lireParametreDate(Long etabId, String cle) {
        return parametreRepository.findByCleAndEtablissementId(cle, etabId)
            .map(Parametre::getValeur)
            .map(v -> { try { return LocalDate.parse(v, FMT_PARAM_DATE); } catch (Exception e) { return null; } })
            .orElse(null);
    }

    // ===== CONSEIL DE CLASSE / JURY / PROCES-VERBAL =====

    @GetMapping("/pv/nouveau")
    public String pvNouveauForm(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActuelle = anneeScolaireActuelle(etabId);
        model.addAttribute("classes", classeRepository.findByAnneeScolaireAndEtablissementId(anneeActuelle, etabId).stream()
            .sorted(Comparator.comparing(Classe::getNom)).collect(Collectors.toList()));
        model.addAttribute("anneeActuelle", anneeActuelle);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "passage-pv-nouveau";
    }

    @PostMapping("/pv/nouveau")
    public String pvCreerSeances(@RequestParam List<Long> classeIds,
                                  @RequestParam(required = false) Integer trimestre,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateSeance,
                                  @RequestParam String rapporteurNom,
                                  RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActuelle = anneeScolaireActuelle(etabId);

        List<Long> idsSeances = new ArrayList<>();
        for (Long classeId : classeIds) {
            Classe classe = classeRepository.findById(classeId)
                .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
                .orElse(null);
            if (classe == null) continue;
            SeanceDeliberation s = new SeanceDeliberation();
            s.setClasse(classe);
            s.setAnneeScolaire(anneeActuelle);
            s.setTrimestre(trimestre);
            s.setDateSeance(dateSeance);
            s.setRapporteurNom(rapporteurNom);
            s.setStatut("EN_PREPARATION");
            s.setEtablissementId(etabId);
            seanceDeliberationRepository.save(s);
            idsSeances.add(s.getId());
        }

        if (idsSeances.isEmpty()) {
            ra.addFlashAttribute("erreurMsg", "Aucune classe valide selectionnee.");
            return "redirect:/passage/pv/nouveau";
        }

        Long premiere = idsSeances.get(0);
        String autres = idsSeances.stream().skip(1).map(String::valueOf).collect(Collectors.joining(","));
        return "redirect:/passage/jury/" + premiere + (autres.isEmpty() ? "" : "?batch=" + autres);
    }

    @GetMapping("/jury/{seanceId}")
    public String jury(@PathVariable Long seanceId, @RequestParam(required = false) String batch, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SeanceDeliberation seance = seanceDeliberationRepository.findById(seanceId)
            .filter(s -> etabId != null && etabId.equals(s.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Seance introuvable dans cet établissement."));

        model.addAttribute("seance", seance);
        model.addAttribute("membres", membreJuryRepository.findBySeanceDeliberationIdOrderByIdAsc(seanceId));
        model.addAttribute("rolesJury", ROLES_JURY);
        model.addAttribute("batch", batch);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "passage-jury";
    }

    @PostMapping("/jury/{seanceId}/membre")
    public String ajouterMembreJury(@PathVariable Long seanceId,
                                     @RequestParam(required = false) String batch,
                                     @RequestParam String nomComplet,
                                     @RequestParam String role,
                                     @RequestParam(defaultValue = "PRESENT") String statutPresence,
                                     RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        for (Long id : idsDuBatch(seanceId, batch)) {
            SeanceDeliberation s = seanceDeliberationRepository.findById(id)
                .filter(sd -> etabId != null && etabId.equals(sd.getEtablissementId())).orElse(null);
            if (s == null) continue;
            MembreJury m = new MembreJury();
            m.setSeanceDeliberation(s);
            m.setNomComplet(nomComplet);
            m.setRole(role);
            m.setStatutPresence(statutPresence);
            membreJuryRepository.save(m);
        }
        return "redirect:/passage/jury/" + seanceId + (batch != null && !batch.isBlank() ? "?batch=" + batch : "");
    }

    @PostMapping("/jury/{seanceId}/membre/{membreId}/supprimer")
    public String supprimerMembreJury(@PathVariable Long seanceId, @PathVariable Long membreId,
                                       @RequestParam(required = false) String batch) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        boolean seanceAutorisee = seanceDeliberationRepository.findById(seanceId)
            .filter(sd -> etabId != null && etabId.equals(sd.getEtablissementId()))
            .isPresent();
        if (seanceAutorisee) {
            membreJuryRepository.deleteByIdAndSeanceDeliberationId(membreId, seanceId);
        }
        return "redirect:/passage/jury/" + seanceId + (batch != null && !batch.isBlank() ? "?batch=" + batch : "");
    }

    private List<Long> idsDuBatch(Long seanceId, String batch) {
        List<Long> ids = new ArrayList<>();
        ids.add(seanceId);
        if (batch != null && !batch.isBlank()) {
            for (String s : batch.split(",")) {
                try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return ids;
    }

    @PostMapping("/jury/{seanceId}/generer")
    public String genererPv(@PathVariable Long seanceId, @RequestParam(required = false) String batch, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Long> ids = idsDuBatch(seanceId, batch);
        for (Long id : ids) {
            SeanceDeliberation s = seanceDeliberationRepository.findById(id)
                .filter(sd -> etabId != null && etabId.equals(sd.getEtablissementId())).orElse(null);
            if (s == null) continue;
            s.setStatut("CLOTUREE");
            if (s.getReferencePv() == null || s.getReferencePv().isBlank()) {
                s.setReferencePv("PV-DEL-" + s.getAnneeScolaire() + "-" + (s.getTrimestre() != null ? "T" + s.getTrimestre() : "FINAL")
                    + "-" + s.getClasse().getNom().replaceAll("\\s+", "") + "-" + s.getId());
            }
            seanceDeliberationRepository.save(s);
            journalService.log("PV_GENERE", "ELEVES", "PV de délibération généré pour " + s.getClasse().getNom());
        }
        return "redirect:/passage/pv/" + seanceId;
    }

    @GetMapping("/pv/{seanceId}")
    public String voirPv(@PathVariable Long seanceId, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SeanceDeliberation seance = seanceDeliberationRepository.findById(seanceId)
            .filter(s -> etabId != null && etabId.equals(s.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Seance introuvable dans cet établissement."));

        Classe classe = seance.getClasse();
        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classe.getId());
        Map<Long, DecisionPassage> decisionParEleve = seance.getTrimestre() == null
            ? decisionPassageRepository.findByClasseOrigineIdAndAnneeScolaire(classe.getId(), seance.getAnneeScolaire()).stream()
                .collect(Collectors.toMap(d -> d.getEleve().getId(), d -> d, (a, b) -> a))
            : Map.of();

        List<Map<String, Object>> resultats = new ArrayList<>();
        int nbAdmis = 0, nbRedouble = 0, nbExclus = 0;
        double sommeMoyennes = 0;
        for (Eleve e : eleves) {
            double moyenne = seance.getTrimestre() != null
                ? bulletinService.calculerMoyenneAnnuelle(e, seance.getAnneeScolaire()) // approximation : moyenne cumulee a date
                : bulletinService.calculerMoyenneAnnuelle(e, seance.getAnneeScolaire());
            sommeMoyennes += moyenne;
            String libelleDecision;
            if (seance.getTrimestre() == null) {
                DecisionPassage d = decisionParEleve.get(e.getId());
                String decision = d != null ? d.getDecision() : null;
                if ("ADMIS".equals(decision)) {
                    libelleDecision = moyenne >= 16 ? "Admis avec Félicitations" : moyenne >= 14 ? "Admis avec Compliments" : "Admis";
                    nbAdmis++;
                } else if ("REDOUBLE".equals(decision)) { libelleDecision = "Redouble"; nbRedouble++; }
                else if ("SORT".equals(decision)) { libelleDecision = "Exclu"; nbExclus++; }
                else { libelleDecision = "En attente"; }
            } else {
                libelleDecision = bulletinService.getMention(moyenne);
            }
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("eleve", e);
            ligne.put("moyenne", Math.round(moyenne * 100.0) / 100.0);
            ligne.put("decision", libelleDecision);
            resultats.add(ligne);
        }
        int total = eleves.size();

        Map<String, String> params = parametreRepository.findByEtablissementId(etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));
        Etablissement etab = etablissementService.getCurrentEtablissement();

        model.addAttribute("seance", seance);
        model.addAttribute("classe", classe);
        model.addAttribute("membres", membreJuryRepository.findBySeanceDeliberationIdOrderByIdAsc(seanceId));
        model.addAttribute("resultats", resultats);
        model.addAttribute("nbAdmis", nbAdmis);
        model.addAttribute("nbRedouble", nbRedouble);
        model.addAttribute("nbExclus", nbExclus);
        model.addAttribute("totalEleves", total);
        model.addAttribute("moyenneClasse", total > 0 ? Math.round(sommeMoyennes / total * 100.0) / 100.0 : 0);
        model.addAttribute("nomEtab", etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame");
        model.addAttribute("adresseEtab", etab != null && etab.getAdresse() != null ? etab.getAdresse() : "");
        model.addAttribute("logoPath", params.getOrDefault("LOGO_ETAB", null));
        return "passage-pv";
    }

    // ===== REINSCRIPTION D'UN ANCIEN ELEVE (meme matricule) =====

    @GetMapping("/reinscription")
    public String reinscriptionForm(@RequestParam(required = false) String q, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Eleve> resultats = q != null && !q.isBlank()
            ? eleveRepository.searchByEtablissement(q, etabId).stream()
                .filter(e -> "ABANDON".equals(e.getStatutInscription()))
                .collect(Collectors.toList())
            : List.of();

        model.addAttribute("resultats", resultats);
        model.addAttribute("q", q);
        model.addAttribute("classesActuelles", classeRepository.findByAnneeScolaireAndEtablissementId(anneeScolaireActuelle(etabId), etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "passage-reinscription";
    }

    @PostMapping("/reinscription/{eleveId}")
    public String reinscrire(@PathVariable Long eleveId, @RequestParam Long classeId, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId)
            .filter(e -> etabId != null && etabId.equals(e.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Eleve introuvable dans cet établissement."));
        Classe classe = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet établissement."));

        eleve.setStatutInscription("INSCRIT");
        eleve.setClasse(classe);
        eleveRepository.save(eleve);
        journalService.log("ELEVE_REINSCRIT", "ELEVES",
            eleve.getNom() + " " + eleve.getPrenom() + " (" + eleve.getMatricule() + ") — reinscription en " + classe.getNom());
        ra.addFlashAttribute("successMsg",
            eleve.getNom() + " " + eleve.getPrenom() + " reinscrit(e) avec le meme matricule (" + eleve.getMatricule() + ").");
        return "redirect:/passage/reinscription";
    }
}
