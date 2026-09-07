package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import holyflame.administration.service.JournalService;
import holyflame.administration.service.RegimeAcademiqueService;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/parametres")
public class ParametreController {

    @Autowired private ParametreRepository parametreRepository;
    @Autowired private RegimeAcademiqueService regimeAcademiqueService;
    @Autowired private FraisScolariteRepository fraisRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JournalService journalService;
    @Autowired private holyflame.administration.repository.ProfilAccesRepository profilAccesRepository;
    @Autowired private JournalActionRepository journalActionRepository;
    @Autowired private SignalementMessagerieRepository signalementMessagerieRepository;
    @Autowired private PeriodeCalendrierRepository periodeCalendrierRepository;
    @Autowired private EvenementCalendrierRepository evenementCalendrierRepository;
    @Autowired private holyflame.administration.service.CalendrierScolaireService calendrierScolaireService;
    @Autowired private AnneeScolaireRepository anneeScolaireRepository;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;

    private static final Set<String> CLES_ETABLISSEMENT = Set.of(
        "nomEtablissement", "anneeScolaire", "adresse", "langueSysteme", "fuseauHoraire", "couleurPrimaire",
        // Regime academique et regles de deliberation : champs de l'entite Etablissement,
        // pas des parametres cle/valeur — ils conditionnent des calculs, pas de l'affichage.
        "regimeAcademique", "seuilValidationUE", "compensationSemestrielle",
        "noteEliminatoireActive", "noteEliminatoire", "creditsParSemestre");

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        // Paramètres bruts
        Map<String, String> p = parametreRepository.findByEtablissementId(etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));
        model.addAttribute("p", p);
        model.addAttribute("etablissement", etablissementService.getCurrentEtablissement());
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("couleursAccent", List.of("#00236f", "#1e3a8a", "#10b981", "#f59e0b", "#ef4444"));

        // Frais
        model.addAttribute("frais", fraisRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId));

        // Migration automatique des anciens enregistrements sans etabId
        matiereRepository.migrateNullEtablissementId(etabId);
        classeRepository.migrateNullEtablissementId(etabId);

        List<Matiere> matieres = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId);
        List<Classe>  toutesClasses = classeRepository.findByEtablissementId(etabId);
        model.addAttribute("matieres", matieres);
        model.addAttribute("toutesClasses", toutesClasses);
        // Niveaux reellement utilises par les classes de l'etablissement : sert de liste fermee
        // pour le "Niveau cible" d'un frais, afin d'eviter qu'une saisie libre (accent/orthographe
        // differente de Classe.niveau) cree un frais qui ne s'applique jamais a personne — bug
        // silencieux observe (parametrage "reussi" mais jamais pris en compte a l'inscription).
        List<String> niveauxDisponibles = toutesClasses.stream()
            .map(Classe::getNiveau)
            .filter(n -> n != null && !n.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        model.addAttribute("niveauxDisponibles", niveauxDisponibles);

        List<EnseignantAutorisation> autorisations = autorisationRepository.findByEtablissementId(etabId);

        // ── Affectations enseignant / matiere / classe ──────────────────
        List<Utilisateur> enseignantsDisponibles = utilisateurRepository
            .findByRoleAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId);
        model.addAttribute("enseignantsDisponibles", enseignantsDisponibles);

        Map<Long, Utilisateur> enseignantParId = enseignantsDisponibles.stream()
            .collect(Collectors.toMap(Utilisateur::getId, u -> u, (a, b) -> a));
        Map<Long, Matiere> matiereParId = matieres.stream().collect(Collectors.toMap(Matiere::getId, m -> m));
        Map<Long, Classe> classeParId = toutesClasses.stream().collect(Collectors.toMap(Classe::getId, c -> c));
        List<Map<String, Object>> autorisationsAffichage = new ArrayList<>();
        for (EnseignantAutorisation a : autorisations) {
            Utilisateur ens = enseignantParId.get(a.getEnseignantId());
            Matiere mat = matiereParId.get(a.getMatiereId());
            Classe cla = classeParId.get(a.getClasseId());
            if (ens == null || mat == null || cla == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("enseignantNom", ens.getPrenom() + " " + ens.getNom());
            row.put("matiereNom", mat.getNom());
            row.put("classeNom", cla.getNom());
            autorisationsAffichage.add(row);
        }
        autorisationsAffichage.sort(Comparator.comparing(r -> (String) r.get("enseignantNom")));
        model.addAttribute("autorisationsAffichage", autorisationsAffichage);

        // Délégation direction (la page /direction/suivi peut être confiée à un DIRECTEUR non-ADMIN)
        boolean delegationActive = "true".equals(p.get("DELEGATION_DIRECTION"));
        model.addAttribute("delegationActive", delegationActive);

        // Signalements de messagerie non traites — pour la carte de navigation rapide
        long nbSignalementsOuverts = signalementMessagerieRepository.countByEtablissementIdAndStatut(etabId, "OUVERT");
        model.addAttribute("nbSignalementsOuverts", nbSignalementsOuverts);

        // ── Checklist de premiere configuration ─────────────────────────
        // Guide un etablissement tout juste cree : que reste-t-il a faire avant d'etre operationnel ?
        holyflame.administration.model.Etablissement etab = etablissementService.getCurrentEtablissement();
        boolean anneeActiveExiste = !anneeScolaireRepository.findByEtablissementIdAndStatut(etabId, "ACTIVE").isEmpty();
        List<Map<String, Object>> checklist = new ArrayList<>();
        checklist.add(etapeChecklist("Identite de l'etablissement", etab != null && etab.getNom() != null && !etab.getNom().isBlank(), "#config-generale"));
        checklist.add(etapeChecklist("Annee scolaire active", anneeActiveExiste, "/parametres/annees"));
        checklist.add(etapeChecklist("Classes creees", !toutesClasses.isEmpty(), "#config-outils"));
        checklist.add(etapeChecklist("Matieres et coefficients", !matieres.isEmpty(), "/gestion-academique"));
        checklist.add(etapeChecklist("Frais de scolarite", !fraisRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId).isEmpty(), "#config-frais"));
        checklist.add(etapeChecklist("Enseignants affectes a leurs classes", !autorisations.isEmpty(), "#config-autorisations"));
        long nbEtapesFaites = checklist.stream().filter(e -> (boolean) e.get("fait")).count();
        model.addAttribute("checklist", checklist);
        model.addAttribute("checklistFaites", nbEtapesFaites);
        model.addAttribute("checklistTotal", checklist.size());

        return "parametres";
    }

    private Map<String, Object> etapeChecklist(String libelle, boolean fait, String lien) {
        Map<String, Object> etape = new LinkedHashMap<>();
        etape.put("libelle", libelle);
        etape.put("fait", fait);
        etape.put("lien", lien);
        return etape;
    }

    @PostMapping("/update")
    public String update(@RequestParam Map<String, String> formParams, RedirectAttributes ra) {
        String tab = formParams.getOrDefault("_tab", "etablissement");

        if ("etablissement".equals(tab)) {
            List.of("TYPE_MATERNELLE","TYPE_PRIMAIRE","TYPE_COLLEGE","TYPE_LYCEE","TYPE_LYCEE_GENERAL")
                .forEach(key -> upsertParam(key, "false", key, "ETABLISSEMENT"));
        }

        boolean toucheEtablissement = CLES_ETABLISSEMENT.stream().anyMatch(formParams::containsKey);
        if (toucheEtablissement) {
            Etablissement etab = etablissementService.getCurrentEtablissement();
            if (etab != null) {
                Long etabId = etab.getId();
                if (formParams.containsKey("nomEtablissement") && !formParams.get("nomEtablissement").isBlank()) {
                    etab.setNom(formParams.get("nomEtablissement").trim());
                }
                if (formParams.containsKey("adresse")) etab.setAdresse(formParams.get("adresse"));
                if (formParams.containsKey("langueSysteme")) etab.setLangueSysteme(formParams.get("langueSysteme"));
                if (formParams.containsKey("fuseauHoraire")) etab.setFuseauHoraire(formParams.get("fuseauHoraire"));
                if (formParams.containsKey("couleurPrimaire")) etab.setCouleurPrimaire(formParams.get("couleurPrimaire"));
                // Regime academique et seuils de deliberation : regles portees par le service,
                // partagees avec le calcul des resultats.
                regimeAcademiqueService.configurer(etab, formParams, formParams.containsKey("_regimeSoumis"));
                etablissementRepository.save(etab);

                // L'annee academique ne doit jamais ecraser Etablissement.anneeScolaire directement :
                // ce champ doit toujours passer par AnneeScolaireService (creation + activation),
                // sinon il se desynchronise silencieusement des Classe/AnneeScolaire deja crees —
                // symptome observe : un nouvel eleve ou le solde en Tresorerie qui "ne voient pas"
                // le parametrage qu'on vient pourtant de faire.
                if (formParams.containsKey("anneeScolaire")) {
                    String nouvelleAnnee = formParams.get("anneeScolaire").trim();
                    if (!nouvelleAnnee.isBlank() && !nouvelleAnnee.equals(etab.getAnneeScolaire())) {
                        AnneeScolaire annee = anneeScolaireRepository.findByEtablissementIdAndLibelle(etabId, nouvelleAnnee)
                            .orElseGet(() -> anneeScolaireService.creer(nouvelleAnnee, etabId, null, false, false, false));
                        anneeScolaireService.activer(annee.getId(), etabId);
                    }
                }
            }
        }

        if (formParams.containsKey("nomComplet") || formParams.containsKey("email")) {
            Utilisateur u = etablissementService.getCurrentUtilisateur();
            if (u != null) {
                if (formParams.containsKey("nomComplet") && !formParams.get("nomComplet").isBlank()) {
                    String[] parts = formParams.get("nomComplet").trim().split("\\s+", 2);
                    u.setPrenom(parts.length > 1 ? parts[0] : "");
                    u.setNom(parts.length > 1 ? parts[1] : parts[0]);
                }
                if (formParams.containsKey("email") && !formParams.get("email").isBlank()) {
                    u.setEmail(formParams.get("email").trim());
                }
                utilisateurRepository.save(u);
            }
        }

        formParams.forEach((cle, valeur) -> {
            if (!cle.startsWith("_") && !CLES_ETABLISSEMENT.contains(cle)
                && !"nomComplet".equals(cle) && !"email".equals(cle)) {
                upsertParam(cle, valeur, cle, tab.toUpperCase());
            }
        });

        ra.addFlashAttribute("savedTab", tab);
        ra.addFlashAttribute("successMsg", "Parametres enregistres avec succes.");
        return "redirect:/parametres?saved=true&tab=" + tab;
    }

    // ── Changer son propre mot de passe (utilisateur connecte) ──────────
    @PostMapping("/mot-de-passe")
    public String changerMotDePasse(
            @RequestParam String motDePasseActuel,
            @RequestParam String nouveauMotDePasse,
            @RequestParam String confirmationMotDePasse,
            RedirectAttributes ra) {

        Utilisateur u = etablissementService.getCurrentUtilisateur();
        if (u == null) {
            return "redirect:/login";
        }
        if (!passwordEncoder.matches(motDePasseActuel, u.getMotDePasse())) {
            ra.addFlashAttribute("erreurMdp", "Le mot de passe actuel est incorrect.");
            return "redirect:/parametres?tab=securite";
        }
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 6) {
            ra.addFlashAttribute("erreurMdp", "Le nouveau mot de passe doit contenir au moins 6 caracteres.");
            return "redirect:/parametres?tab=securite";
        }
        if (!nouveauMotDePasse.equals(confirmationMotDePasse)) {
            ra.addFlashAttribute("erreurMdp", "La confirmation ne correspond pas au nouveau mot de passe.");
            return "redirect:/parametres?tab=securite";
        }
        u.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        utilisateurRepository.save(u);
        ra.addFlashAttribute("successMdp", "Mot de passe modifie avec succes.");
        return "redirect:/parametres?tab=securite";
    }

    @PostMapping("/delegation")
    public String toggleDelegation(@RequestParam(defaultValue = "false") boolean delegationDirection,
                                   RedirectAttributes ra) {
        upsertParam("DELEGATION_DIRECTION", String.valueOf(delegationDirection),
                    "Délégation accès direction", "SECURITE");
        ra.addFlashAttribute("successMsg", "Paramètre de délégation mis à jour.");
        return "redirect:/parametres?saved=true&tab=autorisations";
    }

    @PostMapping("/classes/generer")
    public String genererClasses(RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String annee = etablissementService.getAnneeScolaireActive();

        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("TYPE_MATERNELLE",   List.of("Petite Section", "Moyenne Section", "Grande Section"));
        mapping.put("TYPE_PRIMAIRE",     List.of("CP1", "CP2", "CE1", "CE2", "CM1", "CM2"));
        mapping.put("TYPE_COLLEGE",      List.of("6ème", "5ème", "4ème", "3ème"));
        mapping.put("TYPE_LYCEE",        List.of("2nde", "1ère", "Terminale"));
        mapping.put("TYPE_LYCEE_GENERAL",List.of("2nde G", "1ère G", "Terminale G"));

        int count = 0;
        for (Map.Entry<String, List<String>> e : mapping.entrySet()) {
            boolean actif = "true".equals(
                parametreRepository.findByCleAndEtablissementId(e.getKey(), etabId)
                    .map(Parametre::getValeur).orElse("false"));
            if (actif) {
                for (String nom : e.getValue()) {
                    if (!classeRepository.existsByNomAndAnneeScolaireAndEtablissementId(nom, annee, etabId)) {
                        Classe c = new Classe();
                        c.setNom(nom); c.setNiveau(nom);
                        c.setAnneeScolaire(annee); c.setEtablissementId(etabId);
                        classeRepository.save(c);
                        count++;
                    }
                }
            }
        }
        ra.addFlashAttribute("classesGenerees", count);
        return "redirect:/parametres?saved=true&tab=etablissement";
    }

    @PostMapping("/frais")
    public String ajouterFrais(
            @RequestParam String designation, @RequestParam String typeFrais,
            @RequestParam Double montant, @RequestParam String echeance,
            @RequestParam(required = false) String niveauCible,
            @RequestParam(required = false) boolean obligatoire) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        FraisScolarite f = new FraisScolarite();
        f.setDesignation(designation); f.setTypeFrais(typeFrais);
        f.setMontant(montant); f.setEcheance(echeance);
        f.setNiveauCible(niveauCible); f.setObligatoire(obligatoire);
        f.setEtablissementId(etabId);
        fraisRepository.save(f);
        return "redirect:/parametres?saved=true&tab=frais";
    }

    @PostMapping("/frais/{id}/modifier")
    public String modifierFrais(@PathVariable Long id,
            @RequestParam String designation, @RequestParam String typeFrais,
            @RequestParam Double montant, @RequestParam String echeance,
            @RequestParam(required = false) String niveauCible,
            @RequestParam(required = false) boolean obligatoire) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        FraisScolarite f = fraisRepository.findById(id).orElseThrow();
        if (etabId == null || !etabId.equals(f.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Frais introuvable dans cet établissement.");
        }
        f.setDesignation(designation); f.setTypeFrais(typeFrais);
        f.setMontant(montant); f.setEcheance(echeance);
        f.setNiveauCible(niveauCible); f.setObligatoire(obligatoire);
        fraisRepository.save(f);
        return "redirect:/parametres?saved=true&tab=frais";
    }

    @PostMapping("/frais/{id}/supprimer")
    public String supprimerFrais(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        fraisRepository.findById(id)
            .filter(f -> etabId != null && etabId.equals(f.getEtablissementId()))
            .ifPresent(fraisRepository::delete);
        return "redirect:/parametres?tab=frais";
    }

    // ── Personnel : créer un compte Utilisateur ENSEIGNANT ───────────────
    @PostMapping("/personnel/{id}/creer-compte")
    public String creerCompte(@PathVariable Long id,
                              @RequestParam String motDePasse,
                              RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel pers = personnelRepository.findById(id).orElse(null);
        if (pers == null || etabId == null || !etabId.equals(pers.getEtablissementId())) {
            ra.addFlashAttribute("erreurMsg", "Personnel introuvable.");
            return "redirect:/parametres?tab=autorisations";
        }
        if (pers.getEmail() == null || pers.getEmail().isBlank()) {
            ra.addFlashAttribute("erreurMsg",
                pers.getNom() + " " + pers.getPrenom() + " n'a pas d'email — ajoutez-en un dans sa fiche Personnel d'abord.");
            return "redirect:/parametres?tab=autorisations";
        }
        if (utilisateurRepository.findByEmail(pers.getEmail()).isPresent()) {
            ra.addFlashAttribute("erreurMsg",
                "Un compte existe déjà pour l'email " + pers.getEmail() + ".");
            return "redirect:/parametres?tab=autorisations";
        }
        Etablissement etab = etablissementService.getCurrentEtablissement();
        Utilisateur u = new Utilisateur();
        u.setNom(pers.getNom());
        u.setPrenom(pers.getPrenom());
        u.setEmail(pers.getEmail());
        u.setMotDePasse(passwordEncoder.encode(motDePasse));
        u.setRole("ENSEIGNANT");
        u.setEtablissement(etab);
        utilisateurRepository.save(u);
        ra.addFlashAttribute("successMsg",
            "Compte créé pour " + pers.getNom() + " " + pers.getPrenom()
            + " — email : " + pers.getEmail() + ", rôle : ENSEIGNANT.");
        return "redirect:/parametres?tab=autorisations";
    }

    // ── Personnel : réinitialiser le mot de passe d'un compte existant ──
    @PostMapping("/personnel/{id}/reset-mdp")
    public String resetMotDePasse(@PathVariable Long id,
                                  @RequestParam String motDePasse,
                                  RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel pers = personnelRepository.findById(id).orElse(null);
        if (pers == null || pers.getEmail() == null || etabId == null || !etabId.equals(pers.getEtablissementId())) {
            ra.addFlashAttribute("erreurMsg", "Personnel ou email introuvable.");
            return "redirect:/parametres?tab=autorisations";
        }
        utilisateurRepository.findByEmail(pers.getEmail()).ifPresentOrElse(u -> {
            u.setMotDePasse(passwordEncoder.encode(motDePasse));
            utilisateurRepository.save(u);
            ra.addFlashAttribute("successMsg",
                "Mot de passe réinitialisé pour " + pers.getNom() + " " + pers.getPrenom() + ".");
        }, () -> ra.addFlashAttribute("erreurMsg", "Aucun compte trouvé pour cet enseignant."));
        return "redirect:/parametres?tab=autorisations";
    }

    // ── Personnel : mise à jour du codeAcces ───────────────────────────
    @PostMapping("/personnel/{id}/codeAcces")
    public String updateCodeAcces(@PathVariable Long id,
                                  @RequestParam String codeAcces,
                                  RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        personnelRepository.findById(id)
            .filter(p -> etabId != null && etabId.equals(p.getEtablissementId()))
            .ifPresent(p -> {
                p.setCodeAcces(codeAcces.isBlank() ? null : codeAcces.trim());
                personnelRepository.save(p);
            });
        ra.addFlashAttribute("successMsg", "Code d'accès mis à jour.");
        return "redirect:/parametres?tab=autorisations";
    }

    // ── Double vérification : réinitialiser le mot de passe d'un parent ──
    @PostMapping("/verification/parent")
    public String verifierParent(@RequestParam String nomParent,
                                 @RequestParam String nomEnfant,
                                 RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String parentQ = normaliser(nomParent);
        String enfantQ = normaliser(nomEnfant);

        Eleve trouve = null;
        String emailParentTrouve = null;
        for (Eleve e : eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId)) {
            boolean enfantOk = enfantQ.equals(normaliser(e.getPrenom() + " " + e.getNom()))
                || enfantQ.equals(normaliser(e.getNom() + " " + e.getPrenom()))
                || enfantQ.equals(normaliser(e.getNom()));
            if (!enfantOk) continue;

            if (e.getPereNom() != null && parentQ.equals(normaliser(e.getPereNom()))) {
                trouve = e; emailParentTrouve = e.getPereEmail(); break;
            }
            if (e.getMereNom() != null && parentQ.equals(normaliser(e.getMereNom()))) {
                trouve = e; emailParentTrouve = e.getMereEmail(); break;
            }
        }

        if (trouve == null) {
            ra.addFlashAttribute("verifParentErreur",
                "Aucun eleve de cet etablissement ne correspond a ce nom de parent et d'enfant.");
            return "redirect:/parametres?tab=securite";
        }
        if (emailParentTrouve == null || emailParentTrouve.isBlank()) {
            ra.addFlashAttribute("verifParentErreur",
                "Aucun email parent enregistre pour " + trouve.getPrenom() + " " + trouve.getNom() + ".");
            return "redirect:/parametres?tab=securite";
        }
        Utilisateur compte = utilisateurRepository.findByEmail(emailParentTrouve).orElse(null);
        if (compte == null) {
            ra.addFlashAttribute("verifParentErreur",
                "Aucun compte parent cree pour " + trouve.getPrenom() + " " + trouve.getNom()
                + " (email : " + emailParentTrouve + ").");
            return "redirect:/parametres?tab=securite";
        }
        String nouveauMdp = genererMotDePasseTemporaire();
        compte.setMotDePasse(passwordEncoder.encode(nouveauMdp));
        utilisateurRepository.save(compte);
        journalService.log("MDP_PARENT_REINITIALISE", JOURNAL_MODULE_ROLES,
            "Parent de " + trouve.getPrenom() + " " + trouve.getNom() + " (" + compte.getEmail() + ")");
        ra.addFlashAttribute("verifParentSuccess",
            "Nouveau mot de passe pour " + compte.getPrenom() + " " + compte.getNom()
            + " (" + compte.getEmail() + ") : " + nouveauMdp);
        return "redirect:/parametres?tab=securite";
    }

    // ── Double vérification : réinitialiser le mot de passe d'un personnel ──
    @PostMapping("/verification/personnel")
    public String verifierPersonnel(@RequestParam String nomPersonnel,
                                    @RequestParam String prenomPersonnel,
                                    RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String nomQ = normaliser(nomPersonnel);
        String prenomQ = normaliser(prenomPersonnel);

        Personnel trouve = personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(p -> nomQ.equals(normaliser(p.getNom())) && prenomQ.equals(normaliser(p.getPrenom())))
            .findFirst().orElse(null);

        if (trouve == null) {
            ra.addFlashAttribute("verifPersonnelErreur",
                "Aucun membre du personnel de cet etablissement ne correspond a ce nom et prenom.");
            return "redirect:/parametres?tab=securite";
        }
        if (trouve.getEmail() == null || trouve.getEmail().isBlank()) {
            ra.addFlashAttribute("verifPersonnelErreur",
                "Aucun email enregistre pour " + trouve.getNom() + " " + trouve.getPrenom() + ".");
            return "redirect:/parametres?tab=securite";
        }
        Utilisateur compte = utilisateurRepository.findByEmail(trouve.getEmail()).orElse(null);
        if (compte == null) {
            ra.addFlashAttribute("verifPersonnelErreur",
                "Aucun compte utilisateur cree pour " + trouve.getNom() + " " + trouve.getPrenom()
                + " (email : " + trouve.getEmail() + ").");
            return "redirect:/parametres?tab=securite";
        }
        String nouveauMdp = genererMotDePasseTemporaire();
        compte.setMotDePasse(passwordEncoder.encode(nouveauMdp));
        utilisateurRepository.save(compte);
        journalService.log("MDP_PERSONNEL_REINITIALISE", JOURNAL_MODULE_ROLES,
            trouve.getNom() + " " + trouve.getPrenom() + " (" + compte.getEmail() + ")");
        ra.addFlashAttribute("verifPersonnelSuccess",
            "Nouveau mot de passe pour " + compte.getPrenom() + " " + compte.getNom()
            + " (" + compte.getEmail() + ") : " + nouveauMdp);
        return "redirect:/parametres?tab=securite";
    }

    private String normaliser(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String genererMotDePasseTemporaire() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    // ── Gestion des roles ────────────────────────────────────────────
    private static final List<String> ROLES_ASSIGNABLES = List.of("ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COMPTABLE", "COORDONNATEUR");
    private static final String JOURNAL_MODULE_ROLES = "UTILISATEURS";

    // Modules operationnels que l'ADMIN peut confier ponctuellement a un compte DIRECTEUR,
    // en plus de son acces pedagogique/RH de base — jamais de module financier ici, le
    // Directeur ne doit jamais pouvoir se voir attribuer finances/tresorerie/budget/salaires.
    private static final Map<String, String> MODULES_OPTIONNELS_DIRECTEUR = new LinkedHashMap<>();
    static {
        MODULES_OPTIONNELS_DIRECTEUR.put("SECRETARIAT", "Secretariat (eleves, inscriptions, cartes d'identite)");
        MODULES_OPTIONNELS_DIRECTEUR.put("SURVEILLANCE", "Surveillance & discipline");
        MODULES_OPTIONNELS_DIRECTEUR.put("INFIRMERIE", "Infirmerie scolaire");
        MODULES_OPTIONNELS_DIRECTEUR.put("MARKETING", "Site vitrine & communication externe");
        MODULES_OPTIONNELS_DIRECTEUR.put("INVENTAIRE", "Inventaire / stock");
        MODULES_OPTIONNELS_DIRECTEUR.put("COORDINATION", "Coordination pedagogique");
    }

    @GetMapping("/roles")
    public String roles(@RequestParam(required = false) String role,
                        @RequestParam(required = false) String q, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Utilisateur> tousLesComptes = utilisateurRepository.findByEtablissementId(etabId);

        List<Utilisateur> comptesAffiches = (role == null || role.isBlank())
            ? tousLesComptes
            : tousLesComptes.stream().filter(u -> role.equals(u.getRole())).toList();

        if (q != null && !q.isBlank()) {
            String terme = q.trim().toLowerCase();
            comptesAffiches = comptesAffiches.stream()
                .filter(u -> (u.getNom() != null && u.getNom().toLowerCase().contains(terme))
                    || (u.getPrenom() != null && u.getPrenom().toLowerCase().contains(terme))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(terme)))
                .toList();
        }

        comptesAffiches = comptesAffiches.stream()
            .sorted(Comparator.comparing((Utilisateur u) -> u.getNom() == null ? "" : u.getNom()))
            .toList();

        long total = tousLesComptes.size();
        long actifs = tousLesComptes.stream().filter(Utilisateur::isActif).count();
        long suspendus = total - actifs;
        double tauxActivite = total > 0 ? Math.round(actifs * 1000.0 / total) / 10.0 : 0;

        Map<String, Long> comptesParRole = new LinkedHashMap<>();
        for (String r : List.of("ADMIN", "DIRECTEUR", "ENSEIGNANT", "TRESORIER", "COMPTABLE", "SECRETAIRE", "COORDONNATEUR", "ELEVE")) {
            comptesParRole.put(r, tousLesComptes.stream().filter(u -> r.equals(u.getRole())).count());
        }

        List<JournalAction> historique = journalActionRepository
            .findByEtablissementIdAndModuleOrderByDateDesc(etabId, JOURNAL_MODULE_ROLES, PageRequest.of(0, 6));

        model.addAttribute("comptes", comptesAffiches);
        model.addAttribute("filtreRole", role);
        model.addAttribute("filtreQ", q);
        model.addAttribute("totalUtilisateurs", total);
        model.addAttribute("tauxActivite", tauxActivite);
        model.addAttribute("suspendus", suspendus);
        model.addAttribute("comptesParRole", comptesParRole);
        model.addAttribute("rolesActifsCount", comptesParRole.values().stream().filter(c -> c > 0).count());
        model.addAttribute("historique", historique);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("rolesDisponibles", ROLES_ASSIGNABLES);
        model.addAttribute("rolesRecherche", List.of("ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COMPTABLE", "SURVEILLANT", "COORDONNATEUR", "ELEVE", "PARENT"));
        return "parametres-roles";
    }

    // ── Acces modulaire des comptes DIRECTEUR (modules operationnels optionnels)
    // et TRESORIER/COMPTABLE (modules financiers) ──────────────────────────────
    private static final java.util.Set<String> ROLES_ACCES_MODULAIRE = java.util.Set.of("DIRECTEUR", "TRESORIER", "COMPTABLE");

    @GetMapping("/roles/{id}/acces")
    public String acces(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null || etabId == null || u.getEtablissement() == null || !etabId.equals(u.getEtablissement().getId())
                || !ROLES_ACCES_MODULAIRE.contains(u.getRole())) {
            ra.addFlashAttribute("erreurMsg", "Cette gestion d'acces n'est disponible que pour un compte Directeur, Tresorier ou Comptable.");
            return "redirect:/parametres/roles";
        }
        model.addAttribute("compte", u);
        String typeAcces;
        if ("DIRECTEUR".equals(u.getRole())) {
            typeAcces = "DIRECTEUR";
            model.addAttribute("modulesOptionnels", MODULES_OPTIONNELS_DIRECTEUR);
            model.addAttribute("modulesActifs", u.getModulesOptionnelsActifs());
            model.addAttribute("typeAcces", typeAcces);
        } else {
            typeAcces = "FINANCE";
            model.addAttribute("modulesOptionnels", holyflame.administration.service.FinanceModules.LIBELLES);
            Set<String> personnalises = u.getModulesFinanceActifs();
            model.addAttribute("modulesActifs", personnalises != null ? personnalises : holyflame.administration.service.FinanceModules.defautsPourRole(u.getRole()));
            model.addAttribute("modulesPersonnalises", personnalises != null);
            model.addAttribute("typeAcces", typeAcces);
        }
        model.addAttribute("profils", profilAccesRepository.findByEtablissementIdAndTypeOrderByNomAsc(etabId, typeAcces));
        return "parametres-acces";
    }

    @PostMapping("/roles/{id}/acces")
    public String enregistrerAcces(@PathVariable Long id,
                                   @RequestParam(required = false) List<String> modules,
                                   RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null || etabId == null || u.getEtablissement() == null || !etabId.equals(u.getEtablissement().getId())
                || !ROLES_ACCES_MODULAIRE.contains(u.getRole())) {
            ra.addFlashAttribute("erreurMsg", "Cette gestion d'acces n'est disponible que pour un compte Directeur, Tresorier ou Comptable.");
            return "redirect:/parametres/roles";
        }
        boolean estDirecteur = "DIRECTEUR".equals(u.getRole());
        Set<String> valides = estDirecteur ? MODULES_OPTIONNELS_DIRECTEUR.keySet() : holyflame.administration.service.FinanceModules.TOUS;
        Set<String> selection = modules == null ? Set.of()
            : modules.stream().filter(valides::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (estDirecteur) {
            u.setModulesOptionnelsActifs(selection);
        } else {
            u.setModulesFinanceActifs(selection);
        }
        utilisateurRepository.save(u);
        journalService.log("ACCES_MODULES_MODIFIÉ", JOURNAL_MODULE_ROLES,
            u.getPrenom() + " " + u.getNom() + " : "
                + (selection.isEmpty() ? "aucun module" : String.join(", ", selection)));
        ra.addFlashAttribute("successMsg", "Acces mis a jour pour " + u.getPrenom() + " " + u.getNom() + ".");
        return "redirect:/parametres/roles";
    }

    // ── Revenir aux droits par defaut du role pour un compte TRESORIER/COMPTABLE personnalise ──
    @PostMapping("/roles/{id}/acces/reinitialiser")
    public String reinitialiserAcces(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null || etabId == null || u.getEtablissement() == null || !etabId.equals(u.getEtablissement().getId())
                || ("TRESORIER".equals(u.getRole()) == false && "COMPTABLE".equals(u.getRole()) == false)) {
            ra.addFlashAttribute("erreurMsg", "Cette action n'est disponible que pour un compte Tresorier ou Comptable.");
            return "redirect:/parametres/roles";
        }
        u.reinitialiserModulesFinance();
        utilisateurRepository.save(u);
        journalService.log("ACCES_MODULES_RÉINITIALISÉ", JOURNAL_MODULE_ROLES,
            u.getPrenom() + " " + u.getNom() + " : retour aux droits par defaut du role " + u.getRole());
        ra.addFlashAttribute("successMsg", "Acces de " + u.getPrenom() + " " + u.getNom() + " reinitialise aux droits par defaut du role.");
        return "redirect:/parametres/roles/" + id + "/acces";
    }

    // ── Profils d'acces reutilisables (gabarits de modules, applicables en un clic) ──
    @PostMapping("/roles/{id}/acces/appliquer-profil")
    public String appliquerProfil(@PathVariable Long id, @RequestParam Long profilId, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        holyflame.administration.model.ProfilAcces profil = profilAccesRepository.findById(profilId).orElse(null);
        if (u == null || profil == null || etabId == null || u.getEtablissement() == null
                || !etabId.equals(u.getEtablissement().getId()) || !etabId.equals(profil.getEtablissementId())
                || !ROLES_ACCES_MODULAIRE.contains(u.getRole())) {
            ra.addFlashAttribute("erreurMsg", "Compte ou profil introuvable.");
            return "redirect:/parametres/roles";
        }
        boolean estDirecteur = "DIRECTEUR".equals(u.getRole());
        String typeAttendu = estDirecteur ? "DIRECTEUR" : "FINANCE";
        if (!typeAttendu.equals(profil.getType())) {
            ra.addFlashAttribute("erreurMsg", "Ce profil ne correspond pas au type de compte (" + typeAttendu + ").");
            return "redirect:/parametres/roles/" + id + "/acces";
        }
        Set<String> valides = estDirecteur ? MODULES_OPTIONNELS_DIRECTEUR.keySet() : holyflame.administration.service.FinanceModules.TOUS;
        Set<String> selection = profil.getModulesActifs().stream().filter(valides::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (estDirecteur) {
            u.setModulesOptionnelsActifs(selection);
        } else {
            u.setModulesFinanceActifs(selection);
        }
        utilisateurRepository.save(u);
        journalService.log("PROFIL_ACCES_APPLIQUÉ", JOURNAL_MODULE_ROLES,
            u.getPrenom() + " " + u.getNom() + " : profil \"" + profil.getNom() + "\"");
        ra.addFlashAttribute("successMsg", "Profil \"" + profil.getNom() + "\" applique a " + u.getPrenom() + " " + u.getNom() + ".");
        return "redirect:/parametres/roles/" + id + "/acces";
    }

    @PostMapping("/roles/{id}/acces/enregistrer-profil")
    public String enregistrerProfil(@PathVariable Long id, @RequestParam String nomProfil, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null || etabId == null || u.getEtablissement() == null || !etabId.equals(u.getEtablissement().getId())
                || !ROLES_ACCES_MODULAIRE.contains(u.getRole()) || nomProfil == null || nomProfil.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "Impossible d'enregistrer ce profil.");
            return "redirect:/parametres/roles/" + id + "/acces";
        }
        boolean estDirecteur = "DIRECTEUR".equals(u.getRole());
        String type = estDirecteur ? "DIRECTEUR" : "FINANCE";
        Set<String> modulesActuels = estDirecteur
            ? u.getModulesOptionnelsActifs()
            : holyflame.administration.service.FinanceModules.effectifs(u);
        holyflame.administration.model.ProfilAcces profil = new holyflame.administration.model.ProfilAcces();
        profil.setNom(nomProfil.trim());
        profil.setType(type);
        profil.setModulesActifs(modulesActuels);
        profil.setEtablissementId(etabId);
        profilAccesRepository.save(profil);
        journalService.log("PROFIL_ACCES_CRÉÉ", JOURNAL_MODULE_ROLES, "Profil \"" + profil.getNom() + "\" (" + type + ")");
        ra.addFlashAttribute("successMsg", "Profil \"" + profil.getNom() + "\" enregistre — reutilisable pour d'autres comptes.");
        return "redirect:/parametres/roles/" + id + "/acces";
    }

    @PostMapping("/profils-acces/{id}/supprimer")
    public String supprimerProfilAcces(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        profilAccesRepository.findById(id)
            .filter(p -> etabId != null && etabId.equals(p.getEtablissementId()))
            .ifPresent(p -> {
                journalService.log("PROFIL_ACCES_SUPPRIMÉ", JOURNAL_MODULE_ROLES, "Profil \"" + p.getNom() + "\"");
                profilAccesRepository.delete(p);
            });
        ra.addFlashAttribute("successMsg", "Profil supprime.");
        return "redirect:/parametres/roles";
    }

    // ── Reinitialiser directement le mot de passe d'un compte (depuis la liste des roles) ──
    @PostMapping("/roles/{id}/reset-mdp")
    public String reinitialiserMotDePasseCompte(@PathVariable Long id,
                                                @RequestParam(required = false) String role,
                                                @RequestParam(required = false) String q,
                                                RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null || u.getEtablissement() == null || etabId == null || !etabId.equals(u.getEtablissement().getId())) {
            ra.addFlashAttribute("erreurMsg", "Ce compte est introuvable dans cet établissement.");
            return "redirect:/parametres/roles";
        }
        String nouveauMdp = genererMotDePasseTemporaire();
        u.setMotDePasse(passwordEncoder.encode(nouveauMdp));
        utilisateurRepository.save(u);
        journalService.log("MDP_REINITIALISE", JOURNAL_MODULE_ROLES,
            u.getPrenom() + " " + u.getNom() + " (" + u.getEmail() + ")");
        ra.addFlashAttribute("successMsg",
            "Nouveau mot de passe pour " + u.getPrenom() + " " + u.getNom() + " (" + u.getEmail() + ") : " + nouveauMdp);
        String suffixe = "?role=" + (role != null ? role : "") + "&q=" + (q != null ? q : "");
        return "redirect:/parametres/roles" + suffixe;
    }

    @PostMapping("/roles/{id}")
    public String modifierRole(@PathVariable Long id, @RequestParam String role, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur courant = etablissementService.getCurrentUtilisateur();
        if (courant != null && courant.getId().equals(id)) {
            ra.addFlashAttribute("erreurMsg", "Vous ne pouvez pas modifier votre propre role.");
            return "redirect:/parametres/roles";
        }
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null || etabId == null || u.getEtablissement() == null || !etabId.equals(u.getEtablissement().getId())
                || "SUPER_ADMIN".equals(u.getRole()) || "ELEVE".equals(u.getRole())) {
            ra.addFlashAttribute("erreurMsg", "Ce compte ne peut pas etre modifie depuis cette page.");
            return "redirect:/parametres/roles";
        }
        if (!ROLES_ASSIGNABLES.contains(role)) {
            ra.addFlashAttribute("erreurMsg", "Role invalide.");
            return "redirect:/parametres/roles";
        }
        String ancien = u.getRole();
        u.setRole(role);
        // Nettoyage : un compte qui change de famille de role ne doit jamais garder des modules
        // personnalises devenus sans objet (ex. un ex-Directeur redevenu Enseignant garderait sinon
        // silencieusement l'acces au module SECRETARIAT — un droit fantome invisible dans l'UI).
        if (!"DIRECTEUR".equals(role)) {
            u.setModulesOptionnelsActifs(null);
        }
        if (!"TRESORIER".equals(role) && !"COMPTABLE".equals(role)) {
            u.reinitialiserModulesFinance();
        }
        utilisateurRepository.save(u);
        journalService.log("ROLE_MODIFIÉ", JOURNAL_MODULE_ROLES,
            u.getPrenom() + " " + u.getNom() + " : " + ancien + " → " + role);
        ra.addFlashAttribute("successMsg", u.getPrenom() + " " + u.getNom() + " : role change de " + ancien + " a " + role + ".");
        return "redirect:/parametres/roles";
    }

    @PostMapping("/roles/{id}/toggle-actif")
    public String toggleActif(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur courant = etablissementService.getCurrentUtilisateur();
        if (courant != null && courant.getId().equals(id)) {
            ra.addFlashAttribute("erreurMsg", "Vous ne pouvez pas suspendre votre propre compte.");
            return "redirect:/parametres/roles";
        }
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null || etabId == null || u.getEtablissement() == null || !etabId.equals(u.getEtablissement().getId())
                || "SUPER_ADMIN".equals(u.getRole())) {
            ra.addFlashAttribute("erreurMsg", "Ce compte ne peut pas etre modifie depuis cette page.");
            return "redirect:/parametres/roles";
        }
        u.setActif(!u.isActif());
        utilisateurRepository.save(u);
        journalService.log(u.isActif() ? "COMPTE_RÉACTIVÉ" : "COMPTE_SUSPENDU", JOURNAL_MODULE_ROLES,
            u.getPrenom() + " " + u.getNom() + " (" + u.getRole() + ")");
        ra.addFlashAttribute("successMsg", u.getPrenom() + " " + u.getNom()
            + (u.isActif() ? " a ete reactive." : " a ete suspendu."));
        return "redirect:/parametres/roles";
    }

    // ── Signalements messagerie ──────────────────────────────────────
    @GetMapping("/signalements")
    public String signalements(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<SignalementMessagerie> signalements = signalementMessagerieRepository
            .findByEtablissementIdOrderByDateSignalementDesc(etabId);
        long nbOuverts = signalements.stream().filter(s -> "OUVERT".equals(s.getStatut())).count();
        model.addAttribute("signalements", signalements);
        model.addAttribute("nbOuverts", nbOuverts);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "parametres-signalements";
    }

    @PostMapping("/signalements/{id}/traiter")
    public String traiterSignalement(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        signalementMessagerieRepository.findById(id)
            .filter(s -> etabId != null && etabId.equals(s.getEtablissementId()))
            .ifPresent(s -> {
                s.setStatut("TRAITE");
                signalementMessagerieRepository.save(s);
            });
        ra.addFlashAttribute("successMsg", "Signalement marque comme traite.");
        return "redirect:/parametres/signalements";
    }

    // ── Upload logo établissement ───────────────────────────────────
    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    public String uploadLogo(@RequestParam("logo") MultipartFile logo, RedirectAttributes ra) {
        if (logo.isEmpty()) {
            ra.addFlashAttribute("erreurMsg", "Veuillez sélectionner un fichier image.");
            return "redirect:/parametres?tab=etablissement";
        }
        String ct = logo.getContentType() != null ? logo.getContentType() : "";
        if (!ct.startsWith("image/")) {
            ra.addFlashAttribute("erreurMsg", "Fichier invalide : seules les images sont acceptées (PNG, JPG, SVG).");
            return "redirect:/parametres?tab=etablissement";
        }
        try {
            String path = fileStorageService.store(logo, "logos");
            upsertParam("LOGO_ETAB", path, "Logo de l'établissement", "ETABLISSEMENT");
            ra.addFlashAttribute("successMsg", "Logo mis à jour avec succès.");
        } catch (java.io.IOException e) {
            ra.addFlashAttribute("erreurMsg", "Erreur lors de l'enregistrement du logo : " + e.getMessage());
        }
        return "redirect:/parametres?tab=etablissement";
    }

    private void upsertParam(String cle, String valeur, String desc, String cat) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Parametre p = parametreRepository.findByCleAndEtablissementId(cle, etabId)
            .orElseGet(() -> {
                Parametre np = new Parametre();
                np.setCle(cle); np.setDescription(desc);
                np.setCategorie(cat); np.setEtablissementId(etabId);
                return np;
            });
        p.setValeur(valeur);
        parametreRepository.save(p);
    }

    // ── Gestion des annees scolaires (cloture, activation, duplication de structure) ──
    @GetMapping("/annees")
    public String annees(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("annees", anneeScolaireService.lister(etabId));
        model.addAttribute("anneeActive", etablissementService.getAnneeScolaireActive());
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "parametres-annees";
    }

    @PostMapping("/annees/creer")
    public String creerAnnee(@RequestParam String libelle,
                              @RequestParam(required = false) String anneeSource,
                              @RequestParam(defaultValue = "false") boolean dupliquerClasses,
                              @RequestParam(defaultValue = "false") boolean dupliquerBudget,
                              @RequestParam(defaultValue = "false") boolean dupliquerAffectations,
                              RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        try {
            anneeScolaireService.creer(libelle.trim(), etabId,
                (anneeSource != null && !anneeSource.isBlank()) ? anneeSource : null,
                dupliquerClasses, dupliquerBudget, dupliquerAffectations);
            ra.addFlashAttribute("successMsg", "Annee scolaire " + libelle + " creee" + (dupliquerClasses ? " (classes dupliquees)" : "") + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erreurMsg", e.getMessage());
        }
        return "redirect:/parametres/annees";
    }

    @PostMapping("/annees/{id}/activer")
    public String activerAnnee(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        try {
            anneeScolaireService.activer(id, etabId);
            ra.addFlashAttribute("successMsg", "Annee scolaire activee : elle devient l'annee par defaut de l'application.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("erreurMsg", e.getMessage());
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("erreurMsg", "Annee scolaire introuvable.");
        }
        return "redirect:/parametres/annees";
    }

    @PostMapping("/annees/{id}/cloturer")
    public String cloturerAnnee(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        var utilisateur = etablissementService.getCurrentUtilisateur();
        try {
            anneeScolaireService.cloturer(id, etabId, utilisateur != null ? utilisateur.getId() : null);
            ra.addFlashAttribute("successMsg", "Annee scolaire cloturee : plus aucune modification n'est possible sur ses donnees.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("erreurMsg", e.getMessage());
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("erreurMsg", "Annee scolaire introuvable.");
        }
        return "redirect:/parametres/annees";
    }

    @PostMapping("/annees/{id}/reouvrir")
    public String reouvrirAnnee(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        try {
            anneeScolaireService.reouvrir(id, etabId);
            ra.addFlashAttribute("successMsg", "Annee scolaire reouverte : les modifications sont de nouveau autorisees.");
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("erreurMsg", "Annee scolaire introuvable.");
        }
        return "redirect:/parametres/annees";
    }

    // ── Calendrier scolaire (trimestres / vacances / jours feries, pour un calcul d'assiduite fiable) ──
    private static final Map<String, String> TYPES_PERIODE_CALENDRIER = new LinkedHashMap<>() {{
        put("TRIMESTRE1", "Trimestre 1");
        put("TRIMESTRE2", "Trimestre 2");
        put("TRIMESTRE3", "Trimestre 3");
        put("VACANCES", "Vacances");
        put("FERIE", "Jour ferie");
    }};

    @GetMapping("/calendrier")
    public String calendrier(@RequestParam(required = false) String annee, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActuelle = annee != null && !annee.isBlank() ? annee
            : etablissementService.getAnneeScolaireActive();

        List<PeriodeCalendrier> periodes = periodeCalendrierRepository
            .findByEtablissementIdAndAnneeScolaireOrderByDateDebutAsc(etabId, anneeActuelle);
        List<EvenementCalendrier> evenements = evenementCalendrierRepository
            .findByEtablissementIdAndAnneeScolaireOrderByDateAsc(etabId, anneeActuelle);

        model.addAttribute("periodes", periodes);
        model.addAttribute("evenements", evenements);
        model.addAttribute("anneeActuelle", anneeActuelle);
        model.addAttribute("anneesExistantes", periodeCalendrierRepository.findDistinctAnneesScolaires(etabId));
        model.addAttribute("typesDisponibles", TYPES_PERIODE_CALENDRIER);
        model.addAttribute("apercuMensuel", construireApercuMensuel(anneeActuelle, periodes, evenements));
        model.addAttribute("suiviTrimestres", construireSuiviTrimestres(periodes, etabId));
        model.addAttribute("suiviAnnee", construireSuiviAnnee(periodes, etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "parametres-calendrier";
    }

    @PostMapping("/calendrier/periodes")
    public String ajouterPeriode(@RequestParam(required = false) Long id,
                                  @RequestParam String nom,
                                  @RequestParam String type,
                                  @RequestParam String anneeScolaire,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
                                  RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (dateFin.isBefore(dateDebut)) {
            ra.addFlashAttribute("erreurMsg", "La date de fin doit etre posterieure a la date de debut.");
            return "redirect:/parametres/calendrier?annee=" + anneeScolaire;
        }
        anneeScolaireService.verifierModifiable(anneeScolaire, etabId);

        PeriodeCalendrier p = id != null
            ? periodeCalendrierRepository.findById(id)
                .filter(existante -> etabId != null && etabId.equals(existante.getEtablissementId()))
                .orElseGet(PeriodeCalendrier::new)
            : new PeriodeCalendrier();
        p.setNom(nom); p.setType(type); p.setAnneeScolaire(anneeScolaire);
        p.setDateDebut(dateDebut); p.setDateFin(dateFin); p.setEtablissementId(etabId);
        periodeCalendrierRepository.save(p);

        if (type.startsWith("TRIMESTRE")) {
            String cle = "T" + type.charAt("TRIMESTRE".length());
            upsertParam(cle + "_DEBUT", dateDebut.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), "Debut " + nom, "CALENDRIER");
            upsertParam(cle + "_FIN", dateFin.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), "Fin " + nom, "CALENDRIER");
        }

        ra.addFlashAttribute("successMsg", "Calendrier mis a jour.");
        return "redirect:/parametres/calendrier?annee=" + anneeScolaire;
    }

    @PostMapping("/calendrier/periodes/{id}/supprimer")
    public String supprimerPeriode(@PathVariable Long id, @RequestParam String annee, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        periodeCalendrierRepository.findById(id)
            .filter(p -> etabId != null && etabId.equals(p.getEtablissementId()))
            .ifPresent(p -> {
                periodeCalendrierRepository.delete(p);
                // Une periode de type trimestre supprimee ne doit plus laisser de dates T{n}_DEBUT/FIN
                // perimees dans les Parametre : ces cles sont sinon relues telles quelles par les
                // rapports (Finances, Passage de classe) comme si le trimestre existait encore.
                if (p.getType() != null && p.getType().startsWith("TRIMESTRE")) {
                    String cle = "T" + p.getType().charAt("TRIMESTRE".length());
                    parametreRepository.findByCleAndEtablissementId(cle + "_DEBUT", etabId).ifPresent(parametreRepository::delete);
                    parametreRepository.findByCleAndEtablissementId(cle + "_FIN", etabId).ifPresent(parametreRepository::delete);
                }
            });
        ra.addFlashAttribute("successMsg", "Periode supprimee.");
        return "redirect:/parametres/calendrier?annee=" + annee;
    }

    @PostMapping("/calendrier/evenements")
    public String ajouterEvenement(@RequestParam String nom,
                                    @RequestParam(required = false) String type,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestParam(defaultValue = "#db5e1b") String couleur,
                                    @RequestParam(required = false) String description,
                                    @RequestParam String anneeScolaire,
                                    RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        EvenementCalendrier e = new EvenementCalendrier();
        e.setNom(nom); e.setType(type); e.setDate(date); e.setCouleur(couleur);
        e.setDescription(description); e.setAnneeScolaire(anneeScolaire); e.setEtablissementId(etabId);
        evenementCalendrierRepository.save(e);
        ra.addFlashAttribute("successMsg", "Evenement ajoute au calendrier.");
        return "redirect:/parametres/calendrier?annee=" + anneeScolaire;
    }

    @PostMapping("/calendrier/evenements/{id}/supprimer")
    public String supprimerEvenement(@PathVariable Long id, @RequestParam String annee, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        evenementCalendrierRepository.findById(id)
            .filter(e -> etabId != null && etabId.equals(e.getEtablissementId()))
            .ifPresent(evenementCalendrierRepository::delete);
        ra.addFlashAttribute("successMsg", "Evenement supprime.");
        return "redirect:/parametres/calendrier?annee=" + annee;
    }

    private List<Map<String, Object>> construireApercuMensuel(String anneeScolaire, List<PeriodeCalendrier> periodes,
                                                                List<EvenementCalendrier> evenements) {
        String[] nomsMois = {"Septembre", "Octobre", "Novembre", "Decembre", "Janvier", "Fevrier", "Mars", "Avril", "Mai", "Juin", "Juillet", "Aout"};

        List<Map<String, Object>> mois = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int m = holyflame.administration.util.AnneeScolaireUtil.MOIS_ORDRE_SCOLAIRE[i];
            int annee = holyflame.administration.util.AnneeScolaireUtil.anneeCalendairePourMois(anneeScolaire, m);
            YearMonth ym = YearMonth.of(annee, m);
            int decalage = ym.atDay(1).getDayOfWeek().getValue() - 1;

            List<Map<String, Object>> jours = new ArrayList<>();
            for (int d = 0; d < decalage; d++) jours.add(null);
            long joursOuvresMois = 0, joursOuvrablesMois = 0;
            for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                LocalDate date = ym.atDay(d);
                String type = periodes.stream()
                    .filter(p -> !date.isBefore(p.getDateDebut()) && !date.isAfter(p.getDateFin()))
                    .sorted(Comparator.comparing(p -> !p.isExclusion()))
                    .map(PeriodeCalendrier::getType)
                    .findFirst().orElse(null);
                DayOfWeek jourSemaine = date.getDayOfWeek();
                boolean exclu = "VACANCES".equals(type) || "FERIE".equals(type);
                boolean ouvrable = jourSemaine != DayOfWeek.SUNDAY;
                boolean ouvre = ouvrable && jourSemaine != DayOfWeek.SATURDAY && !exclu;
                if (ouvrable) joursOuvrablesMois++;
                if (ouvre) joursOuvresMois++;
                List<EvenementCalendrier> evenementsDuJour = evenements.stream()
                    .filter(e -> date.equals(e.getDate())).toList();
                Map<String, Object> jour = new LinkedHashMap<>();
                jour.put("numero", d);
                jour.put("type", type);
                jour.put("evenements", evenementsDuJour);
                jours.add(jour);
            }

            Map<String, Object> moisMap = new LinkedHashMap<>();
            moisMap.put("nom", nomsMois[i]);
            moisMap.put("annee", annee);
            moisMap.put("jours", jours);
            moisMap.put("joursOuvres", joursOuvresMois);
            moisMap.put("joursOuvrables", joursOuvrablesMois);
            mois.add(moisMap);
        }
        return mois;
    }

    private List<Map<String, Object>> construireSuiviTrimestres(List<PeriodeCalendrier> periodes, Long etabId) {
        List<Map<String, Object>> suivi = new ArrayList<>();
        for (PeriodeCalendrier p : periodes) {
            if (p.getType() == null || !p.getType().startsWith("TRIMESTRE")) continue;
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("nom", p.getNom());
            ligne.put("dateDebut", p.getDateDebut());
            ligne.put("dateFin", p.getDateFin());
            ligne.put("joursCalendaires", calendrierScolaireService.joursCalendaires(p.getDateDebut(), p.getDateFin()));
            ligne.put("joursOuvrables", calendrierScolaireService.joursOuvrables(p.getDateDebut(), p.getDateFin()));
            ligne.put("joursOuvres", calendrierScolaireService.joursEcoleOuvres(p.getDateDebut(), p.getDateFin(), etabId));
            suivi.add(ligne);
        }
        return suivi;
    }

    private Map<String, Object> construireSuiviAnnee(List<PeriodeCalendrier> periodes, Long etabId) {
        if (periodes.isEmpty()) return null;
        LocalDate debutAnnee = periodes.stream().map(PeriodeCalendrier::getDateDebut).min(LocalDate::compareTo).orElse(null);
        LocalDate finAnnee = periodes.stream().map(PeriodeCalendrier::getDateFin).max(LocalDate::compareTo).orElse(null);
        Map<String, Object> suivi = new LinkedHashMap<>();
        suivi.put("dateDebut", debutAnnee);
        suivi.put("dateFin", finAnnee);
        suivi.put("joursCalendaires", calendrierScolaireService.joursCalendaires(debutAnnee, finAnnee));
        suivi.put("joursOuvrables", calendrierScolaireService.joursOuvrables(debutAnnee, finAnnee));
        suivi.put("joursOuvres", calendrierScolaireService.joursEcoleOuvres(debutAnnee, finAnnee, etabId));
        return suivi;
    }
}
