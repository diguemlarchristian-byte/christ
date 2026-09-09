package holyflame.administration.controller;

import holyflame.administration.model.DocumentPersonnel;
import holyflame.administration.model.EnseignantAutorisation;
import holyflame.administration.model.Personnel;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.*;
import holyflame.administration.service.EmailService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/personnel")
public class PersonnelController {

    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private DocumentPersonnelRepository documentRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private ContratRepository contratRepository;
    @Autowired private CongeRepository congeRepository;
    @Autowired private SalaireMensuelRepository salaireRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private EmailService emailService;

    @GetMapping
    public String liste(
            @RequestParam(required = false) String fonction,
            @RequestParam(required = false) String statut,
            Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        // Un enseignant n'a pas accès à l'annuaire du personnel : on le renvoie vers son propre espace
        Utilisateur currentUser = etablissementService.getCurrentUtilisateur();
        if (currentUser != null && "ENSEIGNANT".equals(currentUser.getRole())) {
            return "redirect:/tableau-enseignant/eleves";
        }
        model.addAttribute("utilisateurConnecte", currentUser);

        // Migration automatique : associer les anciens personnels sans etabId
        if (etabId != null) {
            int migrated = personnelRepository.migrateNullEtablissementId(etabId);
            if (migrated > 0) {
                model.addAttribute("migrationMsg", migrated + " fiche(s) existante(s) associée(s) à votre établissement.");
            }
        }

        List<Personnel> personnels = etabId != null
            ? personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId)
            : List.of();

        model.addAttribute("totalPersonnels",  personnels.size());
        model.addAttribute("totalActifs", personnels.stream().filter(p -> "ACTIF".equals(p.getStatut())).count());
        model.addAttribute("totalEnseignants", personnels.stream().filter(p -> "ENSEIGNANT".equals(p.getFonction())).count());
        model.addAttribute("totalArchives", personnels.stream().filter(p -> "ARCHIVE".equals(p.getStatut())).count());

        // Filtres (appliqués après calcul des stats globales, pour que les compteurs restent réels/globaux)
        List<Personnel> filtres = personnels.stream()
            .filter(p -> fonction == null || fonction.isBlank() || fonction.equals(p.getFonction()))
            .filter(p -> statut == null || statut.isBlank() || statut.equals(p.getStatut()))
            .collect(Collectors.toList());

        // Classes assignées, uniquement pour le personnel enseignant ayant un compte utilisateur lié (par email)
        Map<Long, List<String>> classesParPersonnel = new HashMap<>();
        for (Personnel p : filtres) {
            if (!"ENSEIGNANT".equals(p.getFonction()) || p.getEmail() == null) continue;
            utilisateurRepository.findByEmail(p.getEmail()).ifPresent(u -> {
                List<EnseignantAutorisation> auths = autorisationRepository
                    .findByEnseignantIdAndEtablissementId(u.getId(), etabId);
                List<String> noms = auths.stream()
                    .map(a -> classeRepository.findById(a.getClasseId()).map(c -> c.getNom()).orElse(null))
                    .filter(n -> n != null)
                    .distinct()
                    .collect(Collectors.toList());
                classesParPersonnel.put(p.getId(), noms);
            });
        }

        model.addAttribute("personnels", filtres);
        model.addAttribute("classesParPersonnel", classesParPersonnel);
        model.addAttribute("selectedFonction", fonction);
        model.addAttribute("selectedStatut", statut);

        return "personnel";
    }

    @GetMapping("/{id}")
    public String fiche(@PathVariable Long id, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(id).orElseThrow();
        verifierProprietaire(p, etabId);
        List<DocumentPersonnel> docs = documentRepository.findByPersonnelIdOrderByDateUploadDesc(id);
        Map<String, List<DocumentPersonnel>> docsByType = docs.stream()
            .collect(Collectors.groupingBy(d -> d.getTypeDocument() != null ? d.getTypeDocument() : "AUTRE"));

        String roleApplicatif = roleApplicatifPour(p.getFonction());
        boolean compteExistant = p.getEmail() != null && utilisateurRepository.findByEmail(p.getEmail()).isPresent();

        model.addAttribute("personnel",  p);
        model.addAttribute("documents",  docs);
        model.addAttribute("docsByType", docsByType);
        model.addAttribute("contrats",   contratRepository.findByPersonnelId(id));
        model.addAttribute("conges",     congeRepository.findByPersonnelId(id));
        model.addAttribute("salaires",   salaireRepository.findByPersonnelIdOrderByAnneeDescMoisDesc(id));
        model.addAttribute("roleApplicatif", roleApplicatif);
        model.addAttribute("compteExistant", compteExistant);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "personnel-fiche";
    }

    @GetMapping("/nouveau")
    public String nouveauForm(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        return "personnel-nouveau";
    }

    @PostMapping("/nouveau")
    public String enregistrerNouveau(
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam(required = false) String matricule,
            @RequestParam String fonction,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String matiereEnseignee,
            @RequestParam(required = false) String codeAcces,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEmbauche,
            @RequestParam(required = false) String diplomes,
            @RequestParam(required = false) String specialisations,
            @RequestParam(required = false) Integer anneesExperience,
            @RequestParam(required = false) List<Long> affectationMatiereId,
            @RequestParam(required = false) List<Long> affectationClasseId,
            Model model,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();

        if (nom == null || nom.isBlank() || prenom == null || prenom.isBlank() || fonction == null || fonction.isBlank()) {
            model.addAttribute("erreur", "Le nom, le prenom et la fonction sont obligatoires.");
            model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
            model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
            return "personnel-nouveau";
        }

        Personnel p = new Personnel();
        p.setNom(nom.trim().toUpperCase());
        p.setPrenom(prenom.trim());
        p.setMatricule(matricule != null && !matricule.isBlank() ? matricule.trim() : null);
        p.setEmail(email != null && !email.isBlank() ? email.trim() : null);
        p.setTelephone(telephone);
        p.setFonction(fonction);
        p.setMatiereEnseignee(matiereEnseignee);
        p.setStatut("ACTIF");
        p.setDateNaissance(dateNaissance);
        p.setGenre(genre);
        p.setDateEmbauche(dateEmbauche);
        p.setDiplomes(diplomes);
        p.setSpecialisations(specialisations);
        p.setAnneesExperience(anneesExperience);
        p.setCodeAcces(codeAcces != null && !codeAcces.isBlank() ? codeAcces.trim()
            : "EMP-" + horlogeService.aujourdHui().getYear() + "-" + String.format("%03d", (personnelRepository.count() + 1)));
        p.setEtablissementId(etabId);
        personnelRepository.save(p);

        // Cree un compte de connexion si un email est fourni, qu'aucun compte n'existe deja
        // et que la fonction correspond a un role applicatif reconnu
        String roleCompte = roleApplicatifPour(fonction);

        String motDePasseGenere = null;
        if (roleCompte != null && p.getEmail() != null && utilisateurRepository.findByEmail(p.getEmail()).isEmpty()) {
            Utilisateur u = new Utilisateur();
            u.setNom(p.getNom());
            u.setPrenom(p.getPrenom());
            u.setEmail(p.getEmail());
            motDePasseGenere = genererMotDePasse();
            u.setMotDePasse(passwordEncoder.encode(motDePasseGenere));
            u.setRole(roleCompte);
            u.setEtablissement(etablissementService.getCurrentEtablissement());
            utilisateurRepository.save(u);

            // Affectations de classes, uniquement pertinentes pour un compte enseignant
            if ("ENSEIGNANT".equals(roleCompte) && affectationMatiereId != null && affectationClasseId != null) {
                int n = Math.min(affectationMatiereId.size(), affectationClasseId.size());
                for (int i = 0; i < n; i++) {
                    Long mId = affectationMatiereId.get(i);
                    Long cId = affectationClasseId.get(i);
                    if (mId == null || cId == null) continue;
                    EnseignantAutorisation auth = new EnseignantAutorisation();
                    auth.setEnseignantId(u.getId());
                    auth.setMatiereId(mId);
                    auth.setClasseId(cId);
                    auth.setEtablissementId(etabId);
                    autorisationRepository.save(auth);
                }
            }
        }

        if (motDePasseGenere != null) {
            ra.addFlashAttribute("nomComplet", p.getPrenom() + " " + p.getNom());
            ra.addFlashAttribute("adminEmail", p.getEmail());
            ra.addFlashAttribute("telephone", p.getTelephone());
            ra.addFlashAttribute("motDePasse", motDePasseGenere);
            ra.addFlashAttribute("codeAcces", p.getCodeAcces());
            return "redirect:/personnel/nouveau/confirmation";
        }

        ra.addFlashAttribute("successMsg", p.getNom() + " " + p.getPrenom() + " a été ajouté(e) avec succès.");
        return "redirect:/personnel";
    }

    @GetMapping("/nouveau/confirmation")
    public String confirmationNouveau(Model model) {
        if (!model.containsAttribute("motDePasse")) {
            return "redirect:/personnel";
        }
        return "personnel-nouveau-confirmation";
    }

    @PostMapping("/nouveau/confirmation/envoyer")
    public String envoyerIdentifiants(
            @RequestParam String canal,
            @RequestParam String nomComplet,
            @RequestParam(required = false) String adminEmail,
            @RequestParam(required = false) String telephone,
            @RequestParam String motDePasse,
            @RequestParam String codeAcces,
            RedirectAttributes ra) {

        boolean envoye = false;
        String messageErreur = null;

        if ("EMAIL".equals(canal)) {
            if (adminEmail == null || adminEmail.isBlank()) {
                messageErreur = "Aucune adresse email renseignee pour cet enseignant.";
            } else {
                String corps = "<p>Bonjour " + nomComplet + ",</p>"
                    + "<p>Votre compte EduSystem Pro a ete cree. Voici vos identifiants de connexion :</p>"
                    + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Identifiant (email)</td><td><strong>" + adminEmail + "</strong></td></tr>"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Mot de passe temporaire</td><td><strong>" + motDePasse + "</strong></td></tr>"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">ID employe</td><td>" + codeAcces + "</td></tr>"
                    + "</table>"
                    + "<p>Merci de vous connecter et de changer ce mot de passe des que possible.</p>";
                envoye = emailService.envoyer(adminEmail, "Vos identifiants EduSystem Pro", corps);
                if (!envoye) messageErreur = "L'envoi par email a echoue.";
            }
        } else {
            messageErreur = "Le canal " + canal + " n'est pas encore configure sur cette installation.";
        }

        ra.addFlashAttribute("nomComplet", nomComplet);
        ra.addFlashAttribute("adminEmail", adminEmail);
        ra.addFlashAttribute("telephone", telephone);
        ra.addFlashAttribute("motDePasse", motDePasse);
        ra.addFlashAttribute("codeAcces", codeAcces);
        if (envoye) {
            ra.addFlashAttribute("successMsg", "Identifiants envoyes par email a " + adminEmail + ".");
        } else {
            ra.addFlashAttribute("erreurMsg", messageErreur);
        }
        return "redirect:/personnel/nouveau/confirmation";
    }

    /** Fonction RH -> role applicatif donnant acces a une interface de connexion. Null = fiche RH seule. */
    private String roleApplicatifPour(String fonction) {
        return switch (fonction) {
            case "ENSEIGNANT"  -> "ENSEIGNANT";
            case "SECRETAIRE"  -> "SECRETAIRE";
            case "TRESORIER"   -> "TRESORIER";
            // Le comptable a son propre role depuis qu'il fallait pouvoir lui ouvrir la
            // scolarite sans lui montrer la remuneration de ses collegues : le tresorier voit
            // les salaires et le budget, le comptable non.
            case "COMPTABLE"   -> "COMPTABLE";
            case "SURVEILLANT" -> "SURVEILLANT";
            case "DIRECTEUR"   -> "DIRECTEUR";
            case "COORDONNATEUR" -> "COORDONNATEUR";
            case "MARKETING"   -> "MARKETING";
            default -> null; // AUTRE : fiche RH seule, pas de compte de connexion automatique
        };
    }

    // ── Creer un compte de connexion pour un personnel existant (retroactif) ─
    @PostMapping("/{id}/creer-compte")
    public String creerCompte(@PathVariable Long id,
                              @RequestParam String email,
                              @RequestParam String motDePasse,
                              RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(id).orElseThrow();
        if (p.getEtablissementId() != null) verifierProprietaire(p, etabId);

        String roleCompte = roleApplicatifPour(p.getFonction());
        if (roleCompte == null) {
            ra.addFlashAttribute("erreurMsg", "La fonction \"" + p.getFonction() + "\" ne correspond a aucun espace de connexion applicatif.");
            return "redirect:/personnel/" + id;
        }
        String emailTrim = email != null ? email.trim().toLowerCase() : "";
        if (emailTrim.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "L'adresse email est obligatoire.");
            return "redirect:/personnel/" + id;
        }
        if (utilisateurRepository.findByEmail(emailTrim).isPresent()) {
            ra.addFlashAttribute("erreurMsg", "Un compte existe deja pour l'adresse " + emailTrim + ".");
            return "redirect:/personnel/" + id;
        }

        Utilisateur u = new Utilisateur();
        u.setNom(p.getNom());
        u.setPrenom(p.getPrenom());
        u.setEmail(emailTrim);
        u.setMotDePasse(passwordEncoder.encode(motDePasse));
        u.setRole(roleCompte);
        u.setEtablissement(etablissementService.getCurrentEtablissement());
        utilisateurRepository.save(u);

        if (p.getEmail() == null || p.getEmail().isBlank()) {
            p.setEmail(emailTrim);
            personnelRepository.save(p);
        }

        ra.addFlashAttribute("successMsg", "Compte de connexion cree pour " + p.getPrenom() + " " + p.getNom() + " — email : " + emailTrim);
        return "redirect:/personnel/" + id;
    }

    // ── Reinitialiser le mot de passe du compte de connexion d'un personnel ──
    @PostMapping("/{id}/reset-mdp")
    public String resetMdp(@PathVariable Long id,
                           @RequestParam String motDePasse,
                           RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(id).orElseThrow();
        if (p.getEtablissementId() != null) verifierProprietaire(p, etabId);

        if (p.getEmail() == null || p.getEmail().isBlank()) {
            ra.addFlashAttribute("erreurMsg", "Aucun compte trouve pour ce personnel.");
            return "redirect:/personnel/" + id;
        }
        utilisateurRepository.findByEmail(p.getEmail()).ifPresentOrElse(u -> {
            u.setMotDePasse(passwordEncoder.encode(motDePasse));
            utilisateurRepository.save(u);
            ra.addFlashAttribute("successMsg", "Mot de passe reinitialise pour " + p.getPrenom() + " " + p.getNom() + ".");
        }, () -> ra.addFlashAttribute("erreurMsg", "Aucun compte trouve pour cet email."));
        return "redirect:/personnel/" + id;
    }

    private String genererMotDePasse() {
        String caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return sb.toString();
    }

    @PostMapping("/{id}/modifier")
    public String modifier(
            @PathVariable Long id,
            @RequestParam String nom, @RequestParam String prenom,
            @RequestParam(required = false) String matricule,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String telephone,
            @RequestParam String fonction,
            @RequestParam(required = false) String matiereEnseignee,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEmbauche,
            @RequestParam(required = false) String adresse,
            @RequestParam(required = false) String remarques,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(id).orElseThrow();
        if (p.getEtablissementId() != null) verifierProprietaire(p, etabId);
        p.setNom(nom.toUpperCase().trim());
        p.setPrenom(prenom.trim());
        p.setMatricule(matricule != null && !matricule.isBlank() ? matricule.trim() : null);
        p.setEmail(email != null && !email.isBlank() ? email.trim() : null);
        p.setTelephone(telephone);
        p.setFonction(fonction);
        p.setMatiereEnseignee(matiereEnseignee);
        if (statut != null && !statut.isBlank()) p.setStatut(statut);
        p.setDateEmbauche(dateEmbauche);
        p.setAdresse(adresse);
        p.setRemarques(remarques);
        // Préserver ou affecter l'etabId si absent
        if (p.getEtablissementId() == null) {
            p.setEtablissementId(etabId);
        }
        personnelRepository.save(p);

        ra.addFlashAttribute("successMsg", p.getNom() + " " + p.getPrenom() + " mis(e) à jour.");
        return "redirect:/personnel";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(id).orElseThrow();
        if (p.getEtablissementId() != null) verifierProprietaire(p, etabId);
        String nom = p.getNom() + " " + p.getPrenom();
        documentRepository.findByPersonnelIdOrderByDateUploadDesc(id)
            .forEach(d -> fileStorageService.delete(d.getCheminFichier()));
        personnelRepository.delete(p);
        ra.addFlashAttribute("infoMsg", nom + " supprimé(e).");
        return "redirect:/personnel";
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadDocument(
            @PathVariable Long id,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("typeDocument") String typeDocument) {

        if (fichier.isEmpty()) return "redirect:/personnel/" + id + "#documents";
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(id).orElseThrow();
        if (p.getEtablissementId() != null) verifierProprietaire(p, etabId);
        try {
            String path = fileStorageService.store(fichier, "personnel/" + id);
            DocumentPersonnel doc = new DocumentPersonnel();
            doc.setPersonnel(p);
            doc.setNomOriginal(fichier.getOriginalFilename());
            doc.setNomFichier(Paths.get(path).getFileName().toString());
            doc.setCheminFichier(path);
            doc.setTypeDocument(typeDocument);
            doc.setContentType(fichier.getContentType());
            doc.setTaille(fichier.getSize());
            doc.setDateUpload(horlogeService.maintenant());
            documentRepository.save(doc);
        } catch (IOException e) {
            return "redirect:/personnel/" + id + "?error=upload#documents";
        }
        return "redirect:/personnel/" + id + "?uploaded=true#documents";
    }

    @GetMapping("/{id}/documents/{docId}/telecharger")
    public ResponseEntity<Resource> telecharger(@PathVariable Long id, @PathVariable Long docId) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        DocumentPersonnel doc = documentRepository.findById(docId).orElseThrow();
        if (doc.getPersonnel() != null && doc.getPersonnel().getEtablissementId() != null) {
            verifierProprietaire(doc.getPersonnel(), etabId);
        }
        Resource resource = fileStorageService.loadAsResource(doc.getCheminFichier());
        String ct = doc.getContentType() != null ? doc.getContentType() : "application/octet-stream";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(ct))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getNomOriginal() + "\"")
            .body(resource);
    }

    @PostMapping("/{id}/documents/{docId}/supprimer")
    public String supprimerDocument(@PathVariable Long id, @PathVariable Long docId) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        DocumentPersonnel doc = documentRepository.findById(docId).orElseThrow();
        if (doc.getPersonnel() != null && doc.getPersonnel().getEtablissementId() != null) {
            verifierProprietaire(doc.getPersonnel(), etabId);
        }
        fileStorageService.delete(doc.getCheminFichier());
        documentRepository.delete(doc);
        return "redirect:/personnel/" + id + "#documents";
    }

    private void verifierProprietaire(Personnel p, Long etabId) {
        if (etabId == null || !etabId.equals(p.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Personnel introuvable dans cet établissement.");
        }
    }
}
