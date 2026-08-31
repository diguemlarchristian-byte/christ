package holyflame.administration.controller;

import holyflame.administration.model.Conduite;
import holyflame.administration.model.CreneauHoraire;
import holyflame.administration.model.Devoir;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Examen;
import holyflame.administration.model.EvenementEcole;
import holyflame.administration.model.Note;
import holyflame.administration.model.Personnel;
import holyflame.administration.model.Programme;
import holyflame.administration.model.Publication;
import holyflame.administration.model.SoumissionDevoir;
import holyflame.administration.model.TachePersonnelle;
import holyflame.administration.repository.*;
import holyflame.administration.service.BulletinService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/portail")
public class PortailController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private PublicationRepository publicationRepository;
    @Autowired private CreneauHoraireRepository creneauHoraireRepository;
    @Autowired private ExamenRepository examenRepository;
    @Autowired private ProgrammeRepository programmeRepository;
    @Autowired private EvenementEcoleRepository evenementEcoleRepository;
    @Autowired private ConduiteRepository conduiteRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private DevoirRepository devoirRepository;
    @Autowired private SoumissionDevoirRepository soumissionDevoirRepository;
    @Autowired private TachePersonnelleRepository tachePersonnelleRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private BulletinService bulletinService;
    @Autowired private holyflame.administration.service.BulletinPdfService bulletinPdfService;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private holyflame.administration.service.CalendrierScolaireService calendrierScolaireService;
    @Autowired private PeriodeCalendrierRepository periodeCalendrierRepository;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    private static final String[] JOURS_NOMS = {"", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};

    /** Resout l'eleve associe au compte connecte (compte propre ou, a defaut, compte parent). */
    private Optional<Eleve> resoudreEleveConnecte(Authentication auth) {
        String email = auth != null ? auth.getName() : "";
        Optional<Eleve> eleveOpt = eleveRepository.findByCompteEmail(email);
        if (eleveOpt.isEmpty()) eleveOpt = eleveRepository.findByEmailParent(email);
        if (eleveOpt.isEmpty()) return Optional.empty();

        Eleve eleveRaw = eleveOpt.get();
        if (eleveRaw.getEtablissementId() == null) {
            Long etabIdAdmin = etablissementService.getCurrentEtablissementId();
            if (etabIdAdmin != null) {
                eleveRaw.setEtablissementId(etabIdAdmin);
                eleveRepository.save(eleveRaw);
            }
        }
        return Optional.of(eleveRepository.findById(eleveRaw.getId()).orElse(eleveRaw));
    }

    @GetMapping
    public String portail(Authentication auth, Model model) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) {
            model.addAttribute("pasDeCompte", true);
            return "portail";
        }
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();
        Long classeId = eleve.getClasse() != null ? eleve.getClasse().getId() : null;
        LocalDate aujourdHui = horlogeService.aujourdHui();

        // ── Notes publiees (toutes periodes), pour la moyenne generale et l'activite recente ──
        List<Note> toutesNotes = noteRepository.findByEleveOrderByDateEvaluationDesc(eleve).stream()
            .filter(n -> "PUBLIE".equals(n.getStatut()))
            .toList();
        double moyenneGenerale = moyennePonderee(toutesNotes);
        List<Note> notesRecentes = toutesNotes.stream().limit(6).toList();

        // ── Rang de classe du trimestre le plus recent ayant des notes : reprend le meme calcul
        // que le bulletin (poids culturel important, souvent le premier chiffre regarde) plutot
        // que de le reserver a la seule consultation du bulletin complet. ──
        Integer trimestreActuel = toutesNotes.stream()
            .map(Note::getTrimestre).filter(t -> t != null).max(Integer::compareTo).orElse(null);
        Integer rangClasse = null;
        Integer effectifClasse = null;
        if (trimestreActuel != null) {
            Map<String, Object> donneesBulletin = bulletinService.calculerBulletin(eleve, trimestreActuel, etabId);
            rangClasse = (Integer) donneesBulletin.get("rang");
            effectifClasse = (Integer) donneesBulletin.get("effectif");
        }

        // ── Progression mensuelle reelle (moyenne ponderee des notes publiees, par mois) ──
        Map<String, double[]> accumulateurParMois = new LinkedHashMap<>();
        List<Note> notesTrieesParDate = toutesNotes.stream()
            .filter(n -> n.getDateEvaluation() != null)
            .sorted(Comparator.comparing(Note::getDateEvaluation))
            .toList();
        for (Note n : notesTrieesParDate) {
            if (n.getValeur() == null || n.getCoefficient() == null) continue;
            String cle = n.getDateEvaluation().getMonth().getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            double[] acc = accumulateurParMois.computeIfAbsent(cle, k -> new double[2]);
            acc[0] += n.getValeur() * n.getCoefficient();
            acc[1] += n.getCoefficient();
        }
        List<String> progressionLabels = accumulateurParMois.keySet().stream()
            .skip(Math.max(0, accumulateurParMois.size() - 9)).toList();
        List<Double> progressionValeurs = progressionLabels.stream()
            .map(mois -> {
                double[] acc = accumulateurParMois.get(mois);
                return acc[1] > 0 ? Math.round((acc[0] / acc[1]) * 100.0) / 100.0 : 0.0;
            }).toList();
        List<Map<String, Object>> progressionPoints = new java.util.ArrayList<>();
        for (int i = 0; i < progressionLabels.size(); i++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("mois", progressionLabels.get(i));
            point.put("valeur", progressionValeurs.get(i));
            progressionPoints.add(point);
        }

        // ── Emploi du temps du jour ──
        List<CreneauHoraire> creneauxAujourdhui = (etabId != null && classeId != null)
            ? creneauHoraireRepository.findByEtablissementIdAndClasseIdOrderByJourAscHeureDebutAsc(etabId, classeId).stream()
                .filter(c -> c.getJour() != null && c.getJour() == aujourdHui.getDayOfWeek().getValue())
                .toList()
            : List.of();

        // ── Evaluations a venir (examens de la classe) ──
        List<Examen> examensAVenir = (etabId != null && classeId != null)
            ? examenRepository.findByEtablissementIdAndClasseIdOrderByDateExamenAscHeureDebutAsc(etabId, classeId).stream()
                .filter(e -> e.getDateExamen() != null && !e.getDateExamen().isBefore(aujourdHui))
                .toList()
            : List.of();
        Map<Long, String> libelleJoursRestants = calculerLibellesJoursRestants(examensAVenir, aujourdHui);
        long examensSemaine = examensAVenir.stream().filter(e -> !e.getDateExamen().isAfter(aujourdHui.plusDays(7))).count();

        // ── Assiduite reelle ──
        List<holyflame.administration.model.Absence> absences = absenceRepository.findByEleveIdOrderByDateDesc(eleve.getId());
        long absencesNonJustifiees = absences.stream().filter(a -> !a.isEstJustifiee()).count();
        long joursOuvres = calendrierScolaireService.joursEcoleOuvres(
            eleve.getDateInscription() != null ? eleve.getDateInscription() : aujourdHui.minusMonths(3), aujourdHui, etabId);
        double tauxAssiduite = joursOuvres > 0
            ? Math.max(0, Math.min(100, Math.round((100.0 - (absencesNonJustifiees * 100.0 / joursOuvres)) * 10) / 10.0))
            : 100;

        model.addAttribute("eleve", eleve);
        model.addAttribute("moyenneGenerale", Math.round(moyenneGenerale * 100.0) / 100.0);
        model.addAttribute("rangClasse", rangClasse);
        model.addAttribute("effectifClasse", effectifClasse);
        model.addAttribute("notesRecentes", notesRecentes);
        model.addAttribute("progressionPoints", progressionPoints);
        model.addAttribute("nombreNotesPubliees", toutesNotes.size());
        model.addAttribute("creneauxAujourdhui", creneauxAujourdhui);
        model.addAttribute("titreEmploiDuJour", "Aujourd'hui (" + JOURS_NOMS[Math.min(aujourdHui.getDayOfWeek().getValue(), 6)] + ")");
        model.addAttribute("examensAVenir", examensAVenir.stream().limit(4).toList());
        model.addAttribute("libelleJoursRestants", libelleJoursRestants);
        model.addAttribute("examensSemaine", examensSemaine);
        model.addAttribute("tauxAssiduite", tauxAssiduite);
        model.addAttribute("absencesNonJustifiees", absencesNonJustifiees);
        return "portail";
    }

    // ── Emploi du temps complet (semaine), grille horaire positionnee ────────
    private static final int HAUTEUR_LIGNE_PX = 100;
    private static final int LARGEUR_COLONNE_HEURES_PX = 80;
    private static final int NB_JOURS_GRILLE = 6;

    @GetMapping("/emploi-du-temps")
    public String emploiDuTemps(@RequestParam(defaultValue = "0") int semaine, Authentication auth, Model model) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();
        Long classeId = eleve.getClasse() != null ? eleve.getClasse().getId() : null;

        List<CreneauHoraire> creneaux = (etabId != null && classeId != null)
            ? creneauHoraireRepository.findByEtablissementIdAndClasseIdOrderByJourAscHeureDebutAsc(etabId, classeId)
            : List.of();

        // ── Bornes horaires reelles de la grille (arrondies a l'heure) ──────
        int heureMin = 8, heureMax = 17;
        for (CreneauHoraire c : creneaux) {
            Integer debut = minutesDepuis(c.getHeureDebut());
            Integer fin = minutesDepuis(c.getHeureFin());
            if (debut != null) heureMin = Math.min(heureMin, debut / 60);
            if (fin != null) heureMax = Math.max(heureMax, (fin + 59) / 60);
        }
        List<String> heuresGrille = new java.util.ArrayList<>();
        for (int h = heureMin; h < heureMax; h++) heuresGrille.add(String.format("%02d:00", h));

        // ── Positionnement absolu (top/height/left/width) de chaque creneau ─
        List<Map<String, Object>> creneauxPositionnes = new java.util.ArrayList<>();
        for (CreneauHoraire c : creneaux) {
            if (c.getJour() == null) continue;
            Integer debut = minutesDepuis(c.getHeureDebut());
            Integer fin = minutesDepuis(c.getHeureFin());
            if (debut == null || fin == null) continue;
            double top = (debut - heureMin * 60) / 60.0 * HAUTEUR_LIGNE_PX;
            double hauteur = Math.max(40, (fin - debut) / 60.0 * HAUTEUR_LIGNE_PX);
            String style = String.format(java.util.Locale.US,
                "top:%.0fpx;height:%.0fpx;left:calc(%dpx + %d * ((100%% - %dpx) / %d));width:calc((100%% - %dpx) / %d - 12px)",
                top, hauteur, LARGEUR_COLONNE_HEURES_PX, c.getJour() - 1, LARGEUR_COLONNE_HEURES_PX, NB_JOURS_GRILLE,
                LARGEUR_COLONNE_HEURES_PX, NB_JOURS_GRILLE);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("creneau", c);
            point.put("style", style);
            creneauxPositionnes.add(point);
        }

        // ── Dates reelles de la semaine affichee (aujourd'hui + decalage de semaine) ──
        LocalDate lundi = horlogeService.aujourdHui().minusDays(horlogeService.aujourdHui().getDayOfWeek().getValue() - 1).plusWeeks(semaine);
        List<Map<String, Object>> joursSemaine = new java.util.ArrayList<>();
        for (int j = 1; j <= NB_JOURS_GRILLE; j++) {
            Map<String, Object> jour = new LinkedHashMap<>();
            jour.put("nom", JOURS_NOMS[j]);
            jour.put("date", lundi.plusDays(j - 1));
            joursSemaine.add(jour);
        }

        // ── Charge horaire hebdomadaire reelle (total + repartition par jour) ──
        double totalHeures = 0;
        double[] heuresParJour = new double[NB_JOURS_GRILLE + 1];
        for (CreneauHoraire c : creneaux) {
            Integer debut = minutesDepuis(c.getHeureDebut());
            Integer fin = minutesDepuis(c.getHeureFin());
            if (debut == null || fin == null || c.getJour() == null) continue;
            double h = Math.max(0, (fin - debut) / 60.0);
            totalHeures += h;
            if (c.getJour() >= 1 && c.getJour() <= NB_JOURS_GRILLE) heuresParJour[c.getJour()] += h;
        }
        double maxHeuresJour = 1;
        for (double h : heuresParJour) maxHeuresJour = Math.max(maxHeuresJour, h);
        List<Map<String, Object>> chargeParJour = new java.util.ArrayList<>();
        for (int j = 1; j <= NB_JOURS_GRILLE; j++) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("nom", JOURS_NOMS[j]);
            ligne.put("heures", Math.round(heuresParJour[j] * 10) / 10.0);
            ligne.put("pourcentage", Math.round(heuresParJour[j] / maxHeuresJour * 100));
            chargeParJour.add(ligne);
        }

        // ── Prochaine echeance reelle (devoir ou examen), toutes categories confondues ──
        LocalDate aujourdHui = horlogeService.aujourdHui();
        String prochaineEcheanceLibelle = null;
        LocalDate prochaineEcheanceDate = null;
        if (classeId != null) {
            Optional<Devoir> prochainDevoir = devoirRepository.findByClasseIdAndStatutOrderByDateEcheanceAsc(classeId, "PUBLIE").stream()
                .filter(d -> d.getDateEcheance() != null && !d.getDateEcheance().isBefore(aujourdHui))
                .findFirst();
            Optional<Examen> prochainExamen = (etabId != null)
                ? examenRepository.findByEtablissementIdAndClasseIdOrderByDateExamenAscHeureDebutAsc(etabId, classeId).stream()
                    .filter(e -> e.getDateExamen() != null && !e.getDateExamen().isBefore(aujourdHui))
                    .findFirst()
                : Optional.empty();
            boolean devoirAvant = prochainDevoir.isPresent()
                && (prochainExamen.isEmpty() || !prochainDevoir.get().getDateEcheance().isAfter(prochainExamen.get().getDateExamen()));
            if (devoirAvant) {
                prochaineEcheanceLibelle = "Devoir : " + prochainDevoir.get().getTitre();
                prochaineEcheanceDate = prochainDevoir.get().getDateEcheance();
            } else if (prochainExamen.isPresent()) {
                Examen ex = prochainExamen.get();
                prochaineEcheanceLibelle = "Examen" + (ex.getMatiere() != null ? " de " + ex.getMatiere().getNom() : "");
                prochaineEcheanceDate = ex.getDateExamen();
            }
        }

        model.addAttribute("eleve", eleve);
        model.addAttribute("heuresGrille", heuresGrille);
        model.addAttribute("joursSemaine", joursSemaine);
        model.addAttribute("creneauxPositionnes", creneauxPositionnes);
        model.addAttribute("hauteurGrillePx", heuresGrille.size() * HAUTEUR_LIGNE_PX);
        model.addAttribute("semaine", semaine);
        model.addAttribute("totalHeures", Math.round(totalHeures * 10) / 10.0);
        model.addAttribute("chargeParJour", chargeParJour);
        model.addAttribute("prochaineEcheanceLibelle", prochaineEcheanceLibelle);
        model.addAttribute("prochaineEcheanceDate", prochaineEcheanceDate);
        return "portail-emploi-du-temps";
    }

    /** Convertit "HH:mm" en minutes depuis minuit, ou null si le format est invalide/absent. */
    private Integer minutesDepuis(String heure) {
        if (heure == null || !heure.matches("\\d{1,2}:\\d{2}")) return null;
        String[] parts = heure.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    // ── Evaluations : a venir + historique recent ────────────────────────────
    @GetMapping("/devoirs")
    public String devoirs(Authentication auth, Model model) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        Eleve eleve = eleveOpt.get();

        creerSoumissionsManquantesPourEleve(eleve);

        List<SoumissionDevoir> soumissions = soumissionDevoirRepository.findByEleveIdOrderByIdDesc(eleve.getId());
        List<TachePersonnelle> taches = tachePersonnelleRepository.findByEleveIdOrderByDateCreationDesc(eleve.getId());

        Map<String, List<SoumissionDevoir>> soumissionsParStatut = new LinkedHashMap<>();
        Map<String, List<TachePersonnelle>> tachesParStatut = new LinkedHashMap<>();
        for (String s : List.of("A_FAIRE", "EN_COURS", "TERMINE")) {
            String statut = s;
            soumissionsParStatut.put(statut, soumissions.stream().filter(x -> statut.equals(x.getStatut())).toList());
            tachesParStatut.put(statut, taches.stream().filter(x -> statut.equals(x.getStatut())).toList());
        }

        model.addAttribute("eleve", eleve);
        model.addAttribute("soumissionsParStatut", soumissionsParStatut);
        model.addAttribute("tachesParStatut", tachesParStatut);
        model.addAttribute("totalAFaire", soumissionsParStatut.get("A_FAIRE").size() + tachesParStatut.get("A_FAIRE").size());
        model.addAttribute("totalEnCours", soumissionsParStatut.get("EN_COURS").size() + tachesParStatut.get("EN_COURS").size());
        model.addAttribute("totalTermine", soumissionsParStatut.get("TERMINE").size() + tachesParStatut.get("TERMINE").size());
        return "portail-devoirs";
    }

    // ── Kanban : faire debuter un devoir assigne ──────────────────────────────
    @PostMapping("/devoirs/soumissions/{id}/commencer")
    public String commencerSoumission(@PathVariable Long id, Authentication auth) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        soumissionDevoirRepository.findById(id)
            .filter(s -> s.getEleve().getId().equals(eleveOpt.get().getId()))
            .ifPresent(s -> {
                s.setStatut("EN_COURS");
                s.setDateDebut(horlogeService.maintenant());
                soumissionDevoirRepository.save(s);
            });
        return "redirect:/portail/devoirs";
    }

    // ── Kanban : mettre a jour le pourcentage d'avancement ───────────────────
    @PostMapping("/devoirs/soumissions/{id}/avancement")
    public String avancementSoumission(@PathVariable Long id, @RequestParam Integer pourcentage, Authentication auth) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        soumissionDevoirRepository.findById(id)
            .filter(s -> s.getEleve().getId().equals(eleveOpt.get().getId()))
            .ifPresent(s -> {
                s.setPourcentageAvancement(Math.max(0, Math.min(100, pourcentage)));
                soumissionDevoirRepository.save(s);
            });
        return "redirect:/portail/devoirs";
    }

    // ── Kanban : marquer un devoir termine, avec depot de fichier optionnel ──
    @PostMapping("/devoirs/soumissions/{id}/terminer")
    public String terminerSoumission(@PathVariable Long id,
                                     @RequestParam(required = false) MultipartFile fichier,
                                     Authentication auth) throws IOException {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        Optional<SoumissionDevoir> soumissionOpt = soumissionDevoirRepository.findById(id)
            .filter(s -> s.getEleve().getId().equals(eleveOpt.get().getId()));
        if (soumissionOpt.isPresent()) {
            SoumissionDevoir s = soumissionOpt.get();
            s.setStatut("TERMINE");
            s.setPourcentageAvancement(100);
            s.setDateSoumission(horlogeService.maintenant());
            if (fichier != null && !fichier.isEmpty()) {
                String chemin = fileStorageService.store(fichier, "devoirs/soumissions");
                s.setFichierPath(chemin);
                s.setFichierNomOriginal(fichier.getOriginalFilename());
            }
            soumissionDevoirRepository.save(s);
        }
        return "redirect:/portail/devoirs";
    }

    // ── Taches personnelles (hors devoirs assignes) ──────────────────────────
    @PostMapping("/devoirs/taches")
    public String creerTache(@RequestParam String titre,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEcheance,
                             Authentication auth) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        TachePersonnelle tache = new TachePersonnelle();
        tache.setEleveId(eleveOpt.get().getId());
        tache.setTitre(titre);
        tache.setDateEcheance(dateEcheance);
        tache.setDateCreation(horlogeService.maintenant());
        tachePersonnelleRepository.save(tache);
        return "redirect:/portail/devoirs";
    }

    @PostMapping("/devoirs/taches/{id}/statut")
    public String changerStatutTache(@PathVariable Long id, @RequestParam String statut, Authentication auth) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        tachePersonnelleRepository.findById(id)
            .filter(t -> t.getEleveId().equals(eleveOpt.get().getId()))
            .ifPresent(t -> {
                t.setStatut(statut);
                t.setPourcentageAvancement("TERMINE".equals(statut) ? 100 : t.getPourcentageAvancement());
                tachePersonnelleRepository.save(t);
            });
        return "redirect:/portail/devoirs";
    }

    @PostMapping("/devoirs/taches/{id}/supprimer")
    public String supprimerTache(@PathVariable Long id, Authentication auth) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        tachePersonnelleRepository.deleteByIdAndEleveId(id, eleveOpt.get().getId());
        return "redirect:/portail/devoirs";
    }

    /** Cree les lignes de suivi manquantes pour un eleve (devoirs publies apres son inscription, migration...). */
    private void creerSoumissionsManquantesPourEleve(Eleve eleve) {
        if (eleve.getClasse() == null) return;
        List<Devoir> devoirsPublies = devoirRepository.findByClasseIdAndStatutOrderByDateEcheanceAsc(eleve.getClasse().getId(), "PUBLIE");
        for (Devoir d : devoirsPublies) {
            if (soumissionDevoirRepository.findByDevoirIdAndEleveId(d.getId(), eleve.getId()).isEmpty()) {
                SoumissionDevoir s = new SoumissionDevoir();
                s.setDevoir(d);
                s.setEleve(eleve);
                soumissionDevoirRepository.save(s);
            }
        }
    }

    // ── Cours : programmes publies pour la classe de l'eleve ─────────────────
    @GetMapping("/cours")
    public String cours(Authentication auth, Model model) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();
        Long classeId = eleve.getClasse() != null ? eleve.getClasse().getId() : null;

        List<Programme> programmes = (etabId != null && classeId != null)
            ? programmeRepository.findByEtablissementIdOrderByDateDebutDesc(etabId).stream()
                .filter(p -> "PUBLIE".equals(p.getStatut()) && p.getClasse() != null && classeId.equals(p.getClasse().getId()))
                .toList()
            : List.of();

        Map<Long, String> enseignantParProgramme = new LinkedHashMap<>();
        for (Programme p : programmes) {
            if (p.getEnseignantId() == null) continue;
            personnelRepository.findById(p.getEnseignantId())
                .ifPresent(ens -> enseignantParProgramme.put(p.getId(), ens.getPrenom() + " " + ens.getNom()));
        }

        model.addAttribute("eleve", eleve);
        model.addAttribute("programmes", programmes);
        model.addAttribute("enseignantParProgramme", enseignantParProgramme);
        return "portail-cours";
    }

    // ── Bulletin (reutilise le meme calcul/template que /bulletins/{id}, mais sans parametre d'id :
    //    l'eleve est toujours resolu depuis le compte connecte, aucune fuite possible vers un autre dossier) ──
    @GetMapping("/bulletin")
    public String bulletin(@RequestParam(defaultValue = "1") Integer trimestre, Authentication auth, Model model, RedirectAttributes ra) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();

        Map<String, Object> donnees = bulletinService.calculerBulletin(eleve, trimestre, etabId);

        // ── Assiduite reelle du trimestre consulte (pas l'historique complet depuis
        // l'inscription) : numerateur et denominateur bornes a la meme periode de calendrier
        // scolaire (Parametres > Calendrier), quand elle est configuree pour cette annee. ──
        LocalDate aujourdHui = horlogeService.aujourdHui();
        String anneeScolaireEleve = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : null;
        holyflame.administration.model.PeriodeCalendrier periodeTrimestre = anneeScolaireEleve != null
            ? periodeCalendrierRepository.findByEtablissementIdAndAnneeScolaireOrderByDateDebutAsc(etabId, anneeScolaireEleve)
                .stream().filter(p -> ("TRIMESTRE" + trimestre).equals(p.getType())).findFirst().orElse(null)
            : null;
        long absencesNonJustifiees = bulletinService.absencesDuTrimestre(eleve, trimestre, anneeScolaireEleve, etabId)
            .stream().filter(a -> !a.isEstJustifiee()).count();
        LocalDate debutPeriode = periodeTrimestre != null ? periodeTrimestre.getDateDebut()
            : (eleve.getDateInscription() != null ? eleve.getDateInscription() : aujourdHui.minusMonths(3));
        LocalDate finPeriode = periodeTrimestre != null && periodeTrimestre.getDateFin().isBefore(aujourdHui)
            ? periodeTrimestre.getDateFin() : aujourdHui;
        long joursOuvres = calendrierScolaireService.joursEcoleOuvres(debutPeriode, finPeriode, etabId);
        double tauxAssiduite = joursOuvres > 0
            ? Math.max(0, Math.min(100, Math.round((100.0 - (absencesNonJustifiees * 100.0 / joursOuvres)) * 10) / 10.0))
            : 100;

        // ── Nombre de notes publiees ce trimestre ────────────────────────────
        long nombreNotes = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
            .filter(n -> "PUBLIE".equals(n.getStatut())).count();

        // ── Tendance reelle : comparaison avec la moyenne du trimestre precedent ──
        Double tendanceDelta = null;
        if (trimestre > 1) {
            Map<String, Object> donneesPrecedentes = bulletinService.calculerBulletin(eleve, trimestre - 1, etabId);
            Object moyennePrecedente = donneesPrecedentes.get("moyenneGenerale");
            Object moyenneActuelle = donnees.get("moyenneGenerale");
            if (moyennePrecedente instanceof Double mp && moyenneActuelle instanceof Double ma && mp > 0) {
                tendanceDelta = Math.round((ma - mp) * 100.0) / 100.0;
            }
        }

        // ── Point fort / point a ameliorer reels (matiere avec la meilleure/plus faible moyenne) ──
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> poles = (Map<String, List<Map<String, Object>>>) donnees.get("poles");
        Map<String, Object> pointFort = null, pointAmeliorer = null;
        boolean aDesAppreciations = false;
        if (poles != null) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : poles.entrySet()) {
                for (Map<String, Object> ligne : entry.getValue()) {
                    double moy = (Double) ligne.get("moyenne");
                    if (pointFort == null || moy > (Double) pointFort.get("moyenne")) pointFort = ligne;
                    if (pointAmeliorer == null || moy < (Double) pointAmeliorer.get("moyenne")) pointAmeliorer = ligne;
                    Object appreciation = ligne.get("appreciation");
                    if (appreciation instanceof String s && !s.isBlank()) aDesAppreciations = true;
                }
            }
        }

        model.addAllAttributes(donnees);
        model.addAttribute("trimestre", trimestre);
        model.addAttribute("tauxAssiduite", tauxAssiduite);
        model.addAttribute("nombreNotes", nombreNotes);
        model.addAttribute("aDesAppreciations", aDesAppreciations);
        model.addAttribute("tendanceDelta", tendanceDelta);
        model.addAttribute("pointFort", pointFort);
        model.addAttribute("pointAmeliorer", pointAmeliorer);
        return "portail-bulletin";
    }

    /** Telechargement du bulletin en PDF genere cote serveur, pour l'eleve/parent connecte. */
    @GetMapping("/bulletin/pdf")
    public void bulletinPdf(@RequestParam(defaultValue = "1") Integer trimestre, Authentication auth,
                            HttpServletResponse response) throws IOException {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();

        Map<String, String> params = parametreRepository.findByEtablissementId(etabId).stream()
            .collect(java.util.stream.Collectors.toMap(
                holyflame.administration.model.Parametre::getCle,
                holyflame.administration.model.Parametre::getValeur, (a, b) -> a));
        holyflame.administration.model.Etablissement etab = etablissementService.getCurrentEtablissement();
        Map<String, Object> enTete = new LinkedHashMap<>();
        enTete.put("nomEtab", etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame");
        enTete.put("adresseEtab", etab != null && etab.getAdresse() != null ? etab.getAdresse() : "");
        enTete.put("emailEtab", params.getOrDefault("EMAIL_ECOLE", ""));
        enTete.put("logoPath", params.get("LOGO_ETAB"));
        enTete.put("devise", params.get("DEVISE"));
        enTete.put("chefEtablissement", params.get("CONTACT_PRINCIPAL"));

        byte[] pdf = bulletinPdfService.genererPdf(eleve, trimestre, etabId, enTete);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"bulletin-" + eleve.getNom() + "-" + eleve.getPrenom() + "-T" + trimestre + ".pdf\"");
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    // ── Vie scolaire : annonces, evenements, assiduite, conduite ─────────────
    @GetMapping("/vie-scolaire")
    public String vieScolaire(Authentication auth, Model model) {
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail";
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();
        Long classeId = eleve.getClasse() != null ? eleve.getClasse().getId() : null;
        LocalDate aujourdHui = horlogeService.aujourdHui();

        List<Publication> publications = (etabId != null && classeId != null)
            ? publicationRepository.findForEleve(etabId, classeId)
            : List.of();

        List<EvenementEcole> evenementsAVenir = new java.util.ArrayList<>();
        if (etabId != null) {
            for (EvenementEcole ev : evenementEcoleRepository.findByEtablissementIdOrderByDateEvenementAsc(etabId)) {
                if (ev.getDateEvenement() == null || ev.getDateEvenement().isBefore(aujourdHui)) continue;
                if (ev.getClasse() != null && (classeId == null || !classeId.equals(ev.getClasse().getId()))) continue;
                evenementsAVenir.add(ev);
            }
        }

        List<holyflame.administration.model.Absence> absences = absenceRepository.findByEleveIdOrderByDateDesc(eleve.getId());
        List<Conduite> conduites = conduiteRepository.findByEleveOrderBySaisieAtDesc(eleve);

        model.addAttribute("eleve", eleve);
        model.addAttribute("publications", publications);
        model.addAttribute("evenementsAVenir", evenementsAVenir);
        model.addAttribute("absences", absences);
        model.addAttribute("conduites", conduites);
        return "portail-vie-scolaire";
    }

    /** Libelle pret-a-afficher ("Aujourd'hui" ou "N j") pour chaque examen a venir. */
    private Map<Long, String> calculerLibellesJoursRestants(List<Examen> examensAVenir, LocalDate aujourdHui) {
        Map<Long, String> libelles = new LinkedHashMap<>();
        for (Examen ex : examensAVenir) {
            long jours = aujourdHui.until(ex.getDateExamen(), java.time.temporal.ChronoUnit.DAYS);
            libelles.put(ex.getId(), jours == 0 ? "Aujourd'hui" : jours + " j");
        }
        return libelles;
    }

    /** Moyenne ponderee d'une liste de notes deja filtrees (publiees). */
    private double moyennePonderee(List<Note> notes) {
        double somme = notes.stream().filter(n -> n.getValeur() != null && n.getCoefficient() != null)
            .mapToDouble(n -> n.getValeur() * n.getCoefficient()).sum();
        double coef = notes.stream().filter(n -> n.getCoefficient() != null)
            .mapToDouble(Note::getCoefficient).sum();
        return coef > 0 ? somme / coef : 0;
    }

}
