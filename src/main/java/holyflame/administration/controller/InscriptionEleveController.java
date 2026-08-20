package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.DocumentEleve;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.Paiement;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.DocumentEleveRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import holyflame.administration.service.JournalService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/secretariat/eleves/nouveau")
public class InscriptionEleveController {

    private static final String SESSION_KEY = "inscriptionEleveDonnees";

    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private FraisScolariteRepository fraisScolariteRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private DocumentEleveRepository documentEleveRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    public static class DonneesInscriptionEleve implements Serializable {
        // Etape 1 : Informations Personnelles
        public String nomComplet;
        public LocalDate dateNaissance;
        public String genre;
        public String nationalite;
        public String adresse;
        // Etape 2 : Contacts Parents
        public String pereNom;
        public String pereTelephone;
        public String pereProfession;
        public String pereEmail;
        public String pereCodeAcces;
        public String mereNom;
        public String mereTelephone;
        public String mereProfession;
        public String mereEmail;
        public String mereCodeAcces;
        public String contactUrgenceNom;
        public String urgenceRelation;
        public String contactUrgenceTelephone;
        // Etape 3 : Informations Academiques
        public Long classeId;
        public String anneeScolaire;
        public LocalDate dateInscription;
        public String ecoleProvenance;
        public String langueVivante1;
        public String langueVivante2;
        public List<String> optionsSpecialites;
        // Etape 4 : Frais & Reglement
        public Double paiementMontant;
        public String paiementMode;
    }

    private DonneesInscriptionEleve getDonnees(HttpSession session) {
        return (DonneesInscriptionEleve) session.getAttribute(SESSION_KEY);
    }

    // ── Point d'entree : reinitialise le parcours ───────────────────────────
    @GetMapping
    public String demarrer(HttpSession session) {
        session.setAttribute(SESSION_KEY, new DonneesInscriptionEleve());
        return "redirect:/secretariat/eleves/nouveau/etape-1";
    }

    // ── Etape 1 : Informations Personnelles ─────────────────────────────────
    @GetMapping("/etape-1")
    public String etape1(Model model, HttpSession session) {
        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null) {
            donnees = new DonneesInscriptionEleve();
            session.setAttribute(SESSION_KEY, donnees);
        }
        model.addAttribute("donnees", donnees);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("etapeActuelle", 1);
        return "eleve-nouveau-etape1";
    }

    @PostMapping("/etape-1")
    public String enregistrerEtape1(
            @RequestParam String nomComplet,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String nationalite,
            @RequestParam(required = false) String adresse,
            HttpSession session, Model model) {

        if (nomComplet == null || nomComplet.isBlank()) {
            model.addAttribute("erreur", "Le nom complet est obligatoire.");
            model.addAttribute("donnees", getDonnees(session));
            model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
            model.addAttribute("etapeActuelle", 1);
            return "eleve-nouveau-etape1";
        }

        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null) donnees = new DonneesInscriptionEleve();
        donnees.nomComplet = nomComplet.trim();
        donnees.dateNaissance = dateNaissance;
        donnees.genre = genre;
        donnees.nationalite = nationalite;
        donnees.adresse = adresse;
        session.setAttribute(SESSION_KEY, donnees);

        return "redirect:/secretariat/eleves/nouveau/etape-2";
    }

    // ── Etape 2 : Contacts Parents ───────────────────────────────────────────
    @GetMapping("/etape-2")
    public String etape2(Model model, HttpSession session) {
        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }
        model.addAttribute("donnees", donnees);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("etapeActuelle", 2);
        return "eleve-nouveau-etape2";
    }

    @PostMapping("/etape-2")
    public String enregistrerEtape2(
            @RequestParam(required = false) String pereNom,
            @RequestParam(required = false) String pereTelephone,
            @RequestParam(required = false) String pereProfession,
            @RequestParam(required = false) String pereEmail,
            @RequestParam(required = false) String pereCodeAcces,
            @RequestParam(required = false) String mereNom,
            @RequestParam(required = false) String mereTelephone,
            @RequestParam(required = false) String mereProfession,
            @RequestParam(required = false) String mereEmail,
            @RequestParam(required = false) String mereCodeAcces,
            @RequestParam(required = false) String contactUrgenceNom,
            @RequestParam(required = false) String urgenceRelation,
            @RequestParam(required = false) String contactUrgenceTelephone,
            HttpSession session) {

        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }
        donnees.pereNom = pereNom;
        donnees.pereTelephone = pereTelephone;
        donnees.pereProfession = pereProfession;
        donnees.pereEmail = pereEmail;
        donnees.pereCodeAcces = pereCodeAcces;
        donnees.mereNom = mereNom;
        donnees.mereTelephone = mereTelephone;
        donnees.mereProfession = mereProfession;
        donnees.mereEmail = mereEmail;
        donnees.mereCodeAcces = mereCodeAcces;
        donnees.contactUrgenceNom = contactUrgenceNom;
        donnees.urgenceRelation = urgenceRelation;
        donnees.contactUrgenceTelephone = contactUrgenceTelephone;
        session.setAttribute(SESSION_KEY, donnees);

        return "redirect:/secretariat/eleves/nouveau/etape-3";
    }

    // ── Etape 3 : Informations Academiques ──────────────────────────────────
    @GetMapping("/etape-3")
    public String etape3(Model model, HttpSession session) {
        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }
        Long etabId = etablissementService.getCurrentEtablissementId();
        Etablissement etab = etablissementService.getCurrentEtablissement();
        String anneeScolaire;
        if (donnees.anneeScolaire != null && !donnees.anneeScolaire.isBlank()) {
            anneeScolaire = donnees.anneeScolaire;
        } else if (etab != null && etab.getAnneeScolaire() != null && !etab.getAnneeScolaire().isBlank()) {
            anneeScolaire = etab.getAnneeScolaire();
        } else {
            anneeScolaire = horlogeService.aujourdHui().getYear() + "-" + (horlogeService.aujourdHui().getYear() + 1);
        }
        model.addAttribute("donnees", donnees);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("etapeActuelle", 3);
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("anneeScolaire", anneeScolaire);
        return "eleve-nouveau-etape3";
    }

    @PostMapping("/etape-3")
    public String enregistrerEtape3(
            @RequestParam Long classeId,
            @RequestParam(required = false) String anneeScolaire,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateInscription,
            @RequestParam(required = false) String ecoleProvenance,
            @RequestParam(required = false) String langueVivante1,
            @RequestParam(required = false) String langueVivante2,
            @RequestParam(required = false) List<String> optionsSpecialites,
            HttpSession session) {

        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }
        donnees.classeId = classeId;
        donnees.anneeScolaire = anneeScolaire;
        donnees.dateInscription = dateInscription != null ? dateInscription : horlogeService.aujourdHui();
        donnees.ecoleProvenance = ecoleProvenance;
        donnees.langueVivante1 = langueVivante1;
        donnees.langueVivante2 = langueVivante2;
        donnees.optionsSpecialites = optionsSpecialites;
        session.setAttribute(SESSION_KEY, donnees);

        return "redirect:/secretariat/eleves/nouveau/etape-4";
    }

    // ── Etape 4 : Frais & Reglement ──────────────────────────────────────────
    @GetMapping("/etape-4")
    public String etape4(Model model, HttpSession session) {
        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null || donnees.classeId == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(donnees.classeId).orElse(null);
        List<FraisScolarite> tousFrais = fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId);
        List<FraisScolarite> fraisApplicables = tousFrais.stream()
            .filter(f -> f.getNiveauCible() == null || f.getNiveauCible().isBlank()
                || (classe != null && f.getNiveauCible().equalsIgnoreCase(classe.getNiveau())))
            .toList();

        model.addAttribute("donnees", donnees);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("etapeActuelle", 4);
        model.addAttribute("classe", classe);
        model.addAttribute("fraisApplicables", fraisApplicables);
        model.addAttribute("totalFrais", fraisApplicables.stream().mapToDouble(f -> f.getMontant() != null ? f.getMontant() : 0).sum());
        return "eleve-nouveau-etape4";
    }

    @PostMapping("/etape-4")
    public String enregistrerEtape4(
            @RequestParam(required = false) Double paiementMontant,
            @RequestParam(required = false) String paiementMode,
            HttpSession session) {

        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null || donnees.classeId == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }
        donnees.paiementMontant = paiementMontant;
        donnees.paiementMode = paiementMode;
        session.setAttribute(SESSION_KEY, donnees);

        return "redirect:/secretariat/eleves/nouveau/etape-5";
    }

    // ── Etape 5 : Pieces Justificatives (etape finale) ──────────────────────
    @GetMapping("/etape-5")
    public String etape5(Model model, HttpSession session) {
        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null || donnees.classeId == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }
        model.addAttribute("donnees", donnees);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("etapeActuelle", 5);
        return "eleve-nouveau-etape5";
    }

    @PostMapping(value = "/etape-5/finaliser", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String finaliser(
            @RequestParam(required = false) MultipartFile acteNaissance,
            @RequestParam(required = false) MultipartFile certificatScolarite,
            @RequestParam(required = false) List<MultipartFile> photosIdentite,
            @RequestParam(required = false) MultipartFile dossierMedical,
            @RequestParam(required = false) Boolean certifieAuthentique,
            @RequestParam(required = false) Boolean accepteTraitementSante,
            HttpSession session, Model model, RedirectAttributes ra) {

        DonneesInscriptionEleve donnees = getDonnees(session);
        if (donnees == null || donnees.nomComplet == null || donnees.classeId == null) {
            return "redirect:/secretariat/eleves/nouveau";
        }

        if (!Boolean.TRUE.equals(certifieAuthentique) || !Boolean.TRUE.equals(accepteTraitementSante)) {
            model.addAttribute("erreur", "Veuillez cocher les deux cases de verification finale avant d'enregistrer l'inscription.");
            model.addAttribute("donnees", donnees);
            model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
            model.addAttribute("etapeActuelle", 5);
            return "eleve-nouveau-etape5";
        }

        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(donnees.classeId).orElseThrow();

        String[] parts = donnees.nomComplet.trim().split("\\s+", 2);
        String prenom = parts.length > 1 ? parts[0] : "";
        String nomFamille = parts.length > 1 ? parts[1] : parts[0];

        Eleve eleve = new Eleve();
        eleve.setMatricule("HF-" + horlogeService.aujourdHui().getYear() + "-" + String.format("%03d", (eleveRepository.count() + 1)));
        eleve.setNom(nomFamille.toUpperCase());
        eleve.setPrenom(prenom);
        eleve.setDateNaissance(donnees.dateNaissance);
        eleve.setGenre(donnees.genre);
        eleve.setNationalite(donnees.nationalite);
        eleve.setAdresse(donnees.adresse);

        eleve.setPereNom(donnees.pereNom);
        eleve.setPereTelephone(donnees.pereTelephone);
        eleve.setPereProfession(donnees.pereProfession);
        eleve.setPereEmail(donnees.pereEmail);
        eleve.setPereCodeAcces(donnees.pereCodeAcces);
        eleve.setMereNom(donnees.mereNom);
        eleve.setMereTelephone(donnees.mereTelephone);
        eleve.setMereProfession(donnees.mereProfession);
        eleve.setMereEmail(donnees.mereEmail);
        eleve.setMereCodeAcces(donnees.mereCodeAcces);
        eleve.setContactUrgenceNom(donnees.contactUrgenceNom);
        eleve.setUrgenceRelation(donnees.urgenceRelation);
        eleve.setContactUrgenceTelephone(donnees.contactUrgenceTelephone);

        // Champs historiques (compte rendu unique) derives du parent principal reellement saisi,
        // conserves pour la compatibilite avec les fonctionnalites existantes (portail parent, bulletins, finances...)
        boolean pereRenseigne = donnees.pereEmail != null && !donnees.pereEmail.isBlank();
        eleve.setNomTuteur(pereRenseigne ? donnees.pereNom : donnees.mereNom);
        eleve.setLienParente(pereRenseigne ? "PERE" : "MERE");
        eleve.setTelephoneParent(pereRenseigne ? donnees.pereTelephone : donnees.mereTelephone);
        eleve.setEmailParent(pereRenseigne ? donnees.pereEmail : donnees.mereEmail);
        eleve.setClasse(classe);
        eleve.setDateInscription(donnees.dateInscription != null ? donnees.dateInscription : horlogeService.aujourdHui());
        eleve.setEcoleProvenance(donnees.ecoleProvenance);
        eleve.setLangueVivante1(donnees.langueVivante1);
        eleve.setLangueVivante2(donnees.langueVivante2);
        eleve.setOptionsSpecialites(donnees.optionsSpecialites != null ? String.join(", ", donnees.optionsSpecialites) : null);
        eleve.setStatutInscription("INSCRIT");
        eleve.setEtablissementId(etabId);
        eleveRepository.save(eleve);
        journalService.log("ÉLÈVE_AJOUTÉ", "ELEVES", eleve.getNom() + " " + eleve.getPrenom() + " — " + eleve.getMatricule());

        Long paiementId = null;
        if (donnees.paiementMontant != null && donnees.paiementMontant > 0) {
            Paiement paiement = new Paiement();
            paiement.setEleve(eleve);
            paiement.setMontantVerse(donnees.paiementMontant);
            paiement.setTypePaiement("INSCRIPTION");
            paiement.setModePaiement(donnees.paiementMode != null && !donnees.paiementMode.isBlank() ? donnees.paiementMode : "ESPECES");
            paiement.setDatePaiement(horlogeService.maintenant());
            paiement.setAnneeScolaire(holyflame.administration.util.AnneeScolaireUtil.pour(horlogeService.aujourdHui()));
            paiement.setRecuNumero("HF-" + System.currentTimeMillis());
            fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId).stream()
                .filter(f -> "INSCRIPTION".equals(f.getTypeFrais()))
                .findFirst()
                .ifPresent(paiement::setFraisScolarite);
            paiementRepository.save(paiement);
            paiementId = paiement.getId();
            journalService.log("PAIEMENT_ENREGISTRÉ", "FINANCES",
                eleve.getNom() + " " + eleve.getPrenom() + " — " + donnees.paiementMontant + " F (INSCRIPTION)");
        }

        enregistrerDocument(eleve, acteNaissance, "ACTE_NAISSANCE");
        enregistrerDocument(eleve, certificatScolarite, "CERTIFICAT_SCOLARITE");
        enregistrerDocument(eleve, dossierMedical, "DOSSIER_MEDICAL");
        if (photosIdentite != null) {
            for (MultipartFile photo : photosIdentite) {
                enregistrerDocument(eleve, photo, "PHOTO_IDENTITE");
            }
        }

        session.removeAttribute(SESSION_KEY);

        StringBuilder message = new StringBuilder(eleve.getPrenom() + " " + eleve.getNom()
            + " a été inscrit(e) avec succès (matricule " + eleve.getMatricule() + ").");
        if (paiementId != null) {
            message.append(" Règlement de ").append(String.format("%.0f", donnees.paiementMontant))
                .append(" F enregistré — reçu disponible ci-dessous.");
        } else {
            message.append(" Aucun règlement saisi à l'inscription : le paiement pourra être enregistré depuis le module Finances.");
        }
        ra.addFlashAttribute("successMsg", message.toString());
        if (paiementId != null) {
            ra.addFlashAttribute("paiementRecuId", paiementId);
        }
        // Le Tresorier/Comptable n'a pas acces au tableau de bord Secretariat : on le ramene
        // vers son propre espace (Finances) plutot que /secretariat.
        var utilisateurConnecte = etablissementService.getCurrentUtilisateur();
        boolean estFinancier = utilisateurConnecte != null
            && ("TRESORIER".equals(utilisateurConnecte.getRole()) || "COMPTABLE".equals(utilisateurConnecte.getRole()));
        return estFinancier ? "redirect:/finances" : "redirect:/secretariat";
    }

    private void enregistrerDocument(Eleve eleve, MultipartFile fichier, String typeDocument) {
        if (fichier == null || fichier.isEmpty()) return;
        try {
            String path = fileStorageService.store(fichier, "eleve/" + eleve.getId());
            DocumentEleve doc = new DocumentEleve();
            doc.setEleve(eleve);
            doc.setNomOriginal(fichier.getOriginalFilename());
            doc.setNomFichier(Paths.get(path).getFileName().toString());
            doc.setCheminFichier(path);
            doc.setTypeDocument(typeDocument);
            doc.setContentType(fichier.getContentType());
            doc.setTaille(fichier.getSize());
            doc.setDateUpload(horlogeService.maintenant());
            documentEleveRepository.save(doc);
        } catch (IOException ignored) {
        }
    }
}
