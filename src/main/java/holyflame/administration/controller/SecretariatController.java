package holyflame.administration.controller;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.RetardRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import jakarta.transaction.Transactional;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/secretariat")
public class SecretariatController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private RetardRepository retardRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private holyflame.administration.repository.MessagePriveRepository messagePriveRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired private JournalService journalService;
    @Autowired private holyflame.administration.service.SmsService smsService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "false") boolean toutesAnnees, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        String anneeActive = etablissementService.getAnneeScolaireActive();
        var elevesTous = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        var classesTous = classeRepository.findByEtablissementId(etabId);
        // Par defaut, seule l'annee scolaire active est affichee : les eleves/classes des annees
        // precedentes restent consultables via "Voir aussi les annees precedentes" sans encombrer l'usage quotidien.
        var eleves = toutesAnnees ? elevesTous : elevesTous.stream()
            .filter(e -> e.getClasse() == null || anneeActive.equals(e.getClasse().getAnneeScolaire()))
            .toList();
        var classes = toutesAnnees ? classesTous : classesTous.stream()
            .filter(c -> anneeActive.equals(c.getAnneeScolaire()))
            .toList();
        model.addAttribute("eleves",        eleves);
        model.addAttribute("classes",       classes);
        model.addAttribute("toutesAnnees",  toutesAnnees);
        model.addAttribute("anneeActive",   anneeActive);
        model.addAttribute("nbElevesAnneesPrecedentes", elevesTous.size() - eleves.size());
        var absences = absenceRepository.findByEtablissementId(etabId);
        model.addAttribute("absences",      absences);
        model.addAttribute("totalAbsences", absences.size());
        model.addAttribute("totalEleves",   eleves.size());
        model.addAttribute("totalClasses",  classes.size());
        model.addAttribute("totalAvecCompte", eleves.stream().filter(e -> e.getCompteEmail() != null).count());
        long totalInscrits = eleves.stream().filter(e -> "INSCRIT".equals(e.getStatutInscription())).count();
        model.addAttribute("totalInscrits",  totalInscrits);
        model.addAttribute("totalEnAttente", eleves.size() - totalInscrits);
        java.util.Set<Long> eleveIdsAbsentsAujourdHui = absences.stream()
            .filter(a -> horlogeService.aujourdHui().equals(a.getDate()))
            .map(a -> a.getEleve().getId())
            .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> eleveIdsRetardAujourdHui = retardRepository.findByEtablissementIdAndDate(etabId, horlogeService.aujourdHui()).stream()
            .map(r -> r.getEleve().getId())
            .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("eleveIdsAbsentsAujourdHui", eleveIdsAbsentsAujourdHui);
        model.addAttribute("eleveIdsRetardAujourdHui", eleveIdsRetardAujourdHui);

        // Bandeau "A traiter aujourd'hui" : ce que la secretaire doit voir sans avoir a chercher.
        java.util.Set<Long> idsAffiches = eleves.stream().map(Eleve::getId).collect(java.util.stream.Collectors.toSet());
        long absencesAJustifier = absences.stream()
            .filter(a -> a.getEleve() != null && idsAffiches.contains(a.getEleve().getId()))
            .filter(a -> !a.isEstJustifiee())
            .filter(a -> horlogeService.aujourdHui().equals(a.getDate()))
            .count();
        model.addAttribute("absencesAJustifier", absencesAJustifier);
        model.addAttribute("dateAujourdHui", horlogeService.aujourdHui());
        model.addAttribute("retardsAujourdHui", eleveIdsRetardAujourdHui.size());
        model.addAttribute("totalSansCompte", eleves.stream().filter(e -> e.getCompteEmail() == null).count());
        Utilisateur moi = etablissementService.getCurrentUtilisateur();
        model.addAttribute("messagesNonLus",
            moi != null ? messagePriveRepository.countByDestinataireEmailAndLuFalse(moi.getEmail()) : 0L);
        return "secretariat";
    }

    @PostMapping("/eleves")
    public String ajouterEleve(
            @RequestParam String matricule,
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
            @RequestParam(required = false) String telephoneParent,
            @RequestParam(required = false) String emailParent,
            @RequestParam(required = false) String adresse,
            @RequestParam(required = false) String statutInscription,
            @RequestParam Long classeId) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        if (!classe.getEtablissementId().equals(etabId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet etablissement.");
        }
        Eleve eleve = new Eleve();
        eleve.setMatricule(matricule);
        eleve.setNom(nom.toUpperCase());
        eleve.setPrenom(prenom);
        eleve.setDateNaissance(dateNaissance);
        eleve.setTelephoneParent(telephoneParent);
        eleve.setEmailParent(emailParent);
        eleve.setAdresse(adresse);
        eleve.setStatutInscription(statutInscription != null ? statutInscription : "INSCRIT");
        eleve.setClasse(classe);
        eleve.setEtablissementId(etablissementService.getCurrentEtablissementId());
        eleveRepository.save(eleve);
        journalService.log("ÉLÈVE_AJOUTÉ", "ELEVES", nom.toUpperCase() + " " + prenom + " — " + matricule);
        return "redirect:/secretariat";
    }

    @GetMapping("/eleves/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElseThrow();
        verifierProprietaire(eleve, etabId);
        model.addAttribute("eleve", eleve);
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        return "eleve-form";
    }

    @PostMapping("/eleves/{id}/modifier")
    public String modifierEleve(
            @PathVariable Long id,
            @RequestParam String matricule,
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
            @RequestParam(required = false) String telephoneParent,
            @RequestParam(required = false) String emailParent,
            @RequestParam(required = false) String adresse,
            @RequestParam(required = false) String statutInscription,
            @RequestParam Long classeId) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElseThrow();
        verifierProprietaire(eleve, etabId);
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        if (!classe.getEtablissementId().equals(etabId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet etablissement.");
        }
        eleve.setMatricule(matricule);
        eleve.setNom(nom.toUpperCase());
        eleve.setPrenom(prenom);
        eleve.setDateNaissance(dateNaissance);
        eleve.setTelephoneParent(telephoneParent);
        eleve.setEmailParent(emailParent);
        eleve.setAdresse(adresse);
        eleve.setStatutInscription(statutInscription);
        eleve.setClasse(classe);
        eleveRepository.save(eleve);
        journalService.log("ÉLÈVE_MODIFIÉ", "ELEVES", nom.toUpperCase() + " " + prenom + " — " + matricule);
        return "redirect:/secretariat";
    }

    @Transactional
    @PostMapping("/eleves/{id}/supprimer")
    public String supprimerEleve(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElseThrow();
        verifierProprietaire(eleve, etabId);
        journalService.log("ÉLÈVE_SUPPRIMÉ", "ELEVES", eleve.getNom() + " " + eleve.getPrenom());
        noteRepository.deleteByEleveId(id);
        absenceRepository.deleteByEleveId(id);
        paiementRepository.deleteByEleveId(id);
        retardRepository.deleteByEleveId(id);
        eleveRepository.deleteById(id);
        return "redirect:/secretariat";
    }

    // ── Créer un compte portail pour un élève ──────────────────────────────
    @PostMapping("/eleves/{id}/creer-compte")
    public String creerCompteEleve(@PathVariable Long id,
                                   @RequestParam String email,
                                   @RequestParam String motDePasse,
                                   RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElse(null);
        if (eleve == null || !eleve.getEtablissementId().equals(etabId)) {
            ra.addFlashAttribute("erreurMsg", "Élève introuvable.");
            return "redirect:/secretariat";
        }
        if (email == null || email.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "L'adresse email est obligatoire.");
            return "redirect:/secretariat";
        }
        String emailTrim = email.trim().toLowerCase();
        if (utilisateurRepository.findByEmail(emailTrim).isPresent()) {
            ra.addFlashAttribute("erreurMsg",
                "Un compte existe déjà pour l'adresse " + emailTrim + ".");
            return "redirect:/secretariat";
        }
        // Créer le compte Utilisateur
        Utilisateur u = new Utilisateur();
        u.setNom(eleve.getNom());
        u.setPrenom(eleve.getPrenom());
        u.setEmail(emailTrim);
        u.setMotDePasse(passwordEncoder.encode(motDePasse));
        u.setRole("ELEVE");
        u.setEtablissement(etablissementService.getCurrentEtablissement());
        utilisateurRepository.save(u);

        // Lier l'élève à ce compte
        eleve.setCompteEmail(emailTrim);
        eleveRepository.save(eleve);

        ra.addFlashAttribute("successMsg",
            "Compte portail créé pour " + eleve.getNom() + " " + eleve.getPrenom()
            + " — email : " + emailTrim);
        return "redirect:/secretariat";
    }

    // ── Réinitialiser le mot de passe du compte portail d'un élève ───────
    @PostMapping("/eleves/{id}/reset-mdp")
    public String resetMdpEleve(@PathVariable Long id,
                                @RequestParam String motDePasse,
                                RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElse(null);
        if (eleve == null || eleve.getCompteEmail() == null || !eleve.getEtablissementId().equals(etabId)) {
            ra.addFlashAttribute("erreurMsg", "Élève ou compte introuvable.");
            return "redirect:/secretariat";
        }
        utilisateurRepository.findByEmail(eleve.getCompteEmail()).ifPresentOrElse(u -> {
            u.setMotDePasse(passwordEncoder.encode(motDePasse));
            utilisateurRepository.save(u);
            ra.addFlashAttribute("successMsg",
                "Mot de passe réinitialisé pour " + eleve.getNom() + " " + eleve.getPrenom() + ".");
        }, () -> ra.addFlashAttribute("erreurMsg", "Aucun compte trouvé pour cet élève."));
        return "redirect:/secretariat";
    }

    @PostMapping("/absences")
    public String ajouterAbsence(
            @RequestParam Long eleveId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) boolean estJustifiee,
            @RequestParam(required = false) String motif,
            @RequestParam(required = false) String retour) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        verifierProprietaire(eleve, etabId);
        String anneeScolaireAbsence = holyflame.administration.util.AnneeScolaireUtil.pour(date);
        anneeScolaireService.verifierModifiable(anneeScolaireAbsence, etabId);
        Absence absence = new Absence();
        absence.setEleve(eleve);
        absence.setDate(date);
        absence.setAnneeScolaire(anneeScolaireAbsence);
        absence.setPeriode(periode);
        absence.setEstJustifiee(estJustifiee);
        absence.setMotif(motif);
        absenceRepository.save(absence);
        journalService.log("ABSENCE_SAISIE", "ABSENCES",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + date);
        if (!estJustifiee) alerterAbsenceParSms(eleve, date);
        return "absences".equals(retour) ? "redirect:/secretariat/absences" : "redirect:/secretariat";
    }

    // Une absence non justifiee n'etait visible que si le parent pensait a se reconnecter au
    // portail — parfois des semaines plus tard. Le SMS (canal lu quasi immediatement, contrairement
    // a l'email) alerte le jour meme. Best-effort : ne doit jamais faire echouer la saisie.
    private void alerterAbsenceParSms(Eleve eleve, LocalDate date) {
        try {
            String telephone = eleve.getPereTelephone() != null && !eleve.getPereTelephone().isBlank() ? eleve.getPereTelephone()
                : eleve.getMereTelephone() != null && !eleve.getMereTelephone().isBlank() ? eleve.getMereTelephone()
                : eleve.getTelephoneParent();
            if (telephone == null || telephone.isBlank()) return;
            String message = "Absence non justifiee de " + eleve.getPrenom() + " " + eleve.getNom()
                + " le " + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ". Contactez le secretariat pour la justifier.";
            smsService.envoyer(telephone, message);
        } catch (Exception ignored) {
        }
    }

    // Le secretariat recoit les appels et les mots des parents : c'est lui qui justifie une absence.
    // Le POST de saisie existait deja, mais aucun ecran ne permettait ni de saisir, ni de justifier.
    @GetMapping("/absences")
    public String absences(@RequestParam(required = false) Long classeId, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        String anneeActive = etablissementService.getAnneeScolaireActive();

        var classes = classeRepository.findByEtablissementId(etabId).stream()
            .filter(c -> anneeActive.equals(c.getAnneeScolaire()))
            .toList();
        model.addAttribute("classes", classes);
        model.addAttribute("classeId", classeId);

        var toutes = absenceRepository.findByEtablissementId(etabId).stream()
            .filter(a -> a.getEleve() != null)
            .filter(a -> classeId == null
                || (a.getEleve().getClasse() != null && classeId.equals(a.getEleve().getClasse().getId())))
            .toList();

        LocalDate aujourdHui = horlogeService.aujourdHui();
        model.addAttribute("dateAujourdHui", aujourdHui);
        model.addAttribute("absencesDuJour", toutes.stream()
            .filter(a -> aujourdHui.equals(a.getDate()))
            .sorted(java.util.Comparator.comparing(a -> a.getEleve().getNom()))
            .toList());
        // Les absences anciennes restent justifiables : un parent apporte souvent le mot plusieurs jours apres.
        model.addAttribute("absencesEnAttente", toutes.stream()
            .filter(a -> !a.isEstJustifiee() && a.getDate() != null && a.getDate().isBefore(aujourdHui))
            .sorted(java.util.Comparator.comparing(Absence::getDate).reversed())
            .limit(50)
            .toList());

        var eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(e -> e.getClasse() == null || anneeActive.equals(e.getClasse().getAnneeScolaire()))
            .toList();
        model.addAttribute("eleves", eleves);
        return "secretariat-absences";
    }

    @PostMapping("/absences/{id}/justifier")
    public String justifierAbsence(@PathVariable Long id,
                                   @RequestParam(required = false) String motif,
                                   RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Absence absence = absenceRepository.findById(id).orElseThrow();
        if (absence.getEleve() != null) verifierProprietaire(absence.getEleve(), etabId);
        anneeScolaireService.verifierModifiable(absence.getAnneeScolaire(), etabId);
        absence.setEstJustifiee(true);
        if (motif != null && !motif.isBlank()) absence.setMotif(motif.trim());
        absenceRepository.save(absence);
        journalService.log("ABSENCE_JUSTIFIEE", "ABSENCES",
            absence.getEleve().getNom() + " " + absence.getEleve().getPrenom() + " — " + absence.getDate());
        ra.addFlashAttribute("successMsg", "Absence justifiee pour "
            + absence.getEleve().getPrenom() + " " + absence.getEleve().getNom() + ".");
        return "redirect:/secretariat/absences";
    }

    @PostMapping("/absences/{id}/supprimer")
    public String supprimerAbsence(@PathVariable Long id,
                                   @RequestParam(required = false) String retour) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Absence absence = absenceRepository.findById(id).orElseThrow();
        if (absence.getEleve() != null) verifierProprietaire(absence.getEleve(), etabId);
        anneeScolaireService.verifierModifiable(absence.getAnneeScolaire(), etabId);
        absenceRepository.deleteById(id);
        return "absences".equals(retour) ? "redirect:/secretariat/absences" : "redirect:/secretariat";
    }

    // ──────────────────────────────────────────────────────────────
    // Import Excel des eleves
    // ──────────────────────────────────────────────────────────────

    private static final String SESSION_IMPORT_ELEVES_KEY = "importElevesValides";
    private static final DateTimeFormatter FORMAT_DATE_IMPORT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static class LigneImportEleve implements Serializable {
        public String nom;
        public String prenom;
        public String dateNaissanceTexte;
        public LocalDate dateNaissance;
        public String genre;
        public String classeNom;
        public transient Classe classe;
        public Long classeId;
        public String telephoneParent;
        public String emailParent;
        public String adresse;
        public String pereNom;
        public String pereTelephone;
        public String pereEmail;
        public String mereNom;
        public String mereTelephone;
        public String mereEmail;
        public boolean valide;
        public String statut; // VALIDE, NOM_MANQUANT, CLASSE_MANQUANTE, CLASSE_INTROUVABLE, DATE_INVALIDE
    }

    @GetMapping("/eleves/import")
    public String importForm(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "secretariat-eleves-import";
    }

    @GetMapping("/eleves/import/modele")
    public void telechargerModeleEleves(HttpServletResponse response) throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet("Eleves");

        XSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0, (byte) 35, (byte) 111}, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont headerFont = wb.createFont();
        headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] entetes = {
            "Nom", "Prenom", "DateNaissance (JJ/MM/AAAA)", "Genre (M/F)", "Classe (nom exact)",
            "TelephoneParent", "EmailParent", "Adresse",
            "PereNom", "PereTelephone", "PereEmail",
            "MereNom", "MereTelephone", "MereEmail"
        };
        Row ligneEntete = sheet.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell c = ligneEntete.createCell(i);
            c.setCellValue(entetes[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 20 * 256);
        }

        Row exemple = sheet.createRow(1);
        String[] valeursExemple = {
            "KONE", "Awa", "12/05/2013", "F", "6eme A",
            "0700000000", "parent@example.com", "Abidjan, Cocody",
            "Kone Ibrahim", "0701000000", "pere@example.com",
            "Kone Fatou", "0702000000", "mere@example.com"
        };
        for (int i = 0; i < valeursExemple.length; i++) {
            exemple.createCell(i).setCellValue(valeursExemple[i]);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"modele-import-eleves.xlsx\"");
        wb.write(response.getOutputStream());
        wb.close();
    }

    @PostMapping("/eleves/import")
    public String importerElevesApercu(@RequestParam MultipartFile fichier, HttpSession session, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        List<LigneImportEleve> apercu = new ArrayList<>();
        String erreurFichier = null;
        if (fichier == null || fichier.isEmpty()) {
            erreurFichier = "Aucun fichier sélectionné.";
        } else {
            try (Workbook wb = WorkbookFactory.create(fichier.getInputStream())) {
                Sheet sheet = wb.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // en-tête
                    String nom = lireCelluleTexteEleve(row.getCell(0));
                    String prenom = lireCelluleTexteEleve(row.getCell(1));
                    String dateTexte = lireCelluleTexteEleve(row.getCell(2));
                    String genre = lireCelluleTexteEleve(row.getCell(3));
                    String classeNom = lireCelluleTexteEleve(row.getCell(4));
                    String telParent = lireCelluleTexteEleve(row.getCell(5));
                    String emailParent = lireCelluleTexteEleve(row.getCell(6));
                    String adresse = lireCelluleTexteEleve(row.getCell(7));
                    String pereNom = lireCelluleTexteEleve(row.getCell(8));
                    String pereTel = lireCelluleTexteEleve(row.getCell(9));
                    String pereEmail = lireCelluleTexteEleve(row.getCell(10));
                    String mereNom = lireCelluleTexteEleve(row.getCell(11));
                    String mereTel = lireCelluleTexteEleve(row.getCell(12));
                    String mereEmail = lireCelluleTexteEleve(row.getCell(13));

                    boolean ligneVide = (nom == null || nom.isBlank()) && (prenom == null || prenom.isBlank())
                        && (classeNom == null || classeNom.isBlank());
                    if (ligneVide) continue;

                    LigneImportEleve ligne = new LigneImportEleve();
                    ligne.nom = nom;
                    ligne.prenom = prenom;
                    ligne.dateNaissanceTexte = dateTexte;
                    ligne.genre = genre;
                    ligne.classeNom = classeNom;
                    ligne.telephoneParent = telParent;
                    ligne.emailParent = emailParent;
                    ligne.adresse = adresse;
                    ligne.pereNom = pereNom;
                    ligne.pereTelephone = pereTel;
                    ligne.pereEmail = pereEmail;
                    ligne.mereNom = mereNom;
                    ligne.mereTelephone = mereTel;
                    ligne.mereEmail = mereEmail;

                    if (dateTexte != null && !dateTexte.isBlank()) {
                        try {
                            ligne.dateNaissance = LocalDate.parse(dateTexte.trim(), FORMAT_DATE_IMPORT);
                        } catch (DateTimeParseException ignored) {
                            // date invalide : on continue sans bloquer l'import, le champ restera vide
                        }
                    }

                    if (nom == null || nom.isBlank() || prenom == null || prenom.isBlank()) {
                        ligne.valide = false;
                        ligne.statut = "NOM_MANQUANT";
                    } else if (classeNom == null || classeNom.isBlank()) {
                        ligne.valide = false;
                        ligne.statut = "CLASSE_MANQUANTE";
                    } else {
                        Classe classe = classeRepository.findByNomIgnoreCaseAndEtablissementId(classeNom.trim(), etabId).orElse(null);
                        if (classe == null) {
                            ligne.valide = false;
                            ligne.statut = "CLASSE_INTROUVABLE";
                        } else {
                            ligne.classe = classe;
                            ligne.classeId = classe.getId();
                            ligne.valide = true;
                            ligne.statut = "VALIDE";
                        }
                    }
                    apercu.add(ligne);
                }
            } catch (IOException | RuntimeException ex) {
                erreurFichier = "Fichier illisible. Utilisez un fichier Excel (.xlsx ou .xls) valide.";
            }
        }

        List<LigneImportEleve> validesUniquement = apercu.stream().filter(l -> l.valide).toList();
        session.setAttribute(SESSION_IMPORT_ELEVES_KEY, new ArrayList<>(validesUniquement));

        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("erreurImport", erreurFichier);
        model.addAttribute("importFichierNom", fichier != null ? fichier.getOriginalFilename() : null);
        model.addAttribute("importApercu", apercu);
        model.addAttribute("importTotal", apercu.size());
        model.addAttribute("importPret", validesUniquement.size());
        model.addAttribute("importErreurs", apercu.size() - validesUniquement.size());
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "secretariat-eleves-import";
    }

    @PostMapping("/eleves/import/confirmer")
    public String confirmerImportEleves(HttpSession session, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        @SuppressWarnings("unchecked")
        List<LigneImportEleve> lignes = (List<LigneImportEleve>) session.getAttribute(SESSION_IMPORT_ELEVES_KEY);
        int saved = 0;
        if (lignes != null) {
            for (LigneImportEleve l : lignes) {
                Classe classe = l.classeId != null ? classeRepository.findById(l.classeId).orElse(null) : null;
                if (classe == null) continue;

                Eleve eleve = new Eleve();
                eleve.setNom(l.nom.trim().toUpperCase());
                eleve.setPrenom(l.prenom.trim());
                eleve.setDateNaissance(l.dateNaissance);
                eleve.setGenre(l.genre);
                eleve.setClasse(classe);
                eleve.setTelephoneParent(l.telephoneParent);
                eleve.setEmailParent(l.emailParent);
                eleve.setAdresse(l.adresse);
                eleve.setPereNom(l.pereNom);
                eleve.setPereTelephone(l.pereTelephone);
                eleve.setPereEmail(l.pereEmail);
                eleve.setMereNom(l.mereNom);
                eleve.setMereTelephone(l.mereTelephone);
                eleve.setMereEmail(l.mereEmail);
                eleve.setStatutInscription("INSCRIT");
                eleve.setDateInscription(horlogeService.aujourdHui());
                eleve.setEtablissementId(etabId);
                eleve.setMatricule("HF-" + horlogeService.aujourdHui().getYear() + "-" + String.format("%03d", (eleveRepository.count() + 1)));
                if (l.pereEmail != null && !l.pereEmail.isBlank()) eleve.setPereCodeAcces(genererCodeAcces());
                if (l.mereEmail != null && !l.mereEmail.isBlank()) eleve.setMereCodeAcces(genererCodeAcces());
                eleveRepository.save(eleve);
                saved++;
            }
            session.removeAttribute(SESSION_IMPORT_ELEVES_KEY);
        }
        journalService.log("ELEVES_IMPORTES", "ELEVES", saved + " eleve(s) importe(s) depuis Excel");
        ra.addFlashAttribute("successMsg", saved + " eleve(s) importe(s) avec succes.");
        return "redirect:/secretariat";
    }

    private String genererCodeAcces() {
        String caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
        return sb.toString();
    }

    private String lireCelluleTexteEleve(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            String v = cell.getStringCellValue().trim();
            return v.isEmpty() ? null : v;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().format(FORMAT_DATE_IMPORT);
            }
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }

    /** Verifie que l'eleve appartient bien a l'etablissement de l'utilisateur connecte (isolation multi-tenant). */
    private void verifierProprietaire(Eleve eleve, Long etabId) {
        if (etabId == null || eleve.getEtablissementId() == null || !etabId.equals(eleve.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Élève introuvable dans cet établissement.");
        }
    }
}
