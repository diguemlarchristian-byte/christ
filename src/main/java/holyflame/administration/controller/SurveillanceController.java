package holyflame.administration.controller;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Personnel;
import holyflame.administration.model.Programme;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.DocumentEleveRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.PersonnelRepository;
import holyflame.administration.repository.ProgrammeRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/surveillance")
public class SurveillanceController {

    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ProgrammeRepository programmeRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private DocumentEleveRepository documentEleveRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired private holyflame.administration.service.SmsService smsService;

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("absences",  absenceRepository.findByEtablissementId(etabId));
        model.addAttribute("eleves",    eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("programmes", programmeRepository.findByEtablissementIdOrderByDateDebutDesc(etabId));
        model.addAttribute("classes",   classeRepository.findByEtablissementId(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillance";
    }

    // ── Nouveau programme de cours (page dediee) ─────────────────────────
    @GetMapping("/programmes/nouveau")
    public String nouveauProgramme(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("enseignants", personnelRepository.findByFonctionAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId));
        model.addAttribute("anneeScolaire", etablissementService.getAnneeScolaireActive());
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillance-programme-nouveau";
    }

    // ── Signaler une absence (page dediee) ────────────────────────────────
    @GetMapping("/absences/nouvelle")
    public String nouvelleAbsence(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Eleve> eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);

        Map<Long, String> photosParEleve = new HashMap<>();
        Map<Long, long[]> statsParEleve = new HashMap<>(); // [absencesAnnee, absencesMois, absencesInjustifiees]
        LocalDate maintenant = horlogeService.aujourdHui();
        for (Eleve e : eleves) {
            documentEleveRepository.findByEleveIdOrderByDateUploadDesc(e.getId()).stream()
                .filter(d -> "PHOTO_IDENTITE".equals(d.getTypeDocument()))
                .findFirst()
                .ifPresent(d -> photosParEleve.put(e.getId(), d.getCheminFichier()));

            List<Absence> historique = absenceRepository.findByEleveIdOrderByDateDesc(e.getId());
            long absencesMois = historique.stream()
                .filter(a -> a.getDate() != null && a.getDate().getMonth() == maintenant.getMonth() && a.getDate().getYear() == maintenant.getYear())
                .count();
            long absencesInjustifiees = historique.stream().filter(a -> !a.isEstJustifiee()).count();
            statsParEleve.put(e.getId(), new long[]{historique.size(), absencesMois, absencesInjustifiees});
        }

        model.addAttribute("eleves", eleves);
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("photosParEleve", photosParEleve);
        model.addAttribute("statsParEleve", statsParEleve);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillance-absence-nouvelle";
    }

    @PostMapping("/absences")
    public String ajouterAbsence(
            @RequestParam Long eleveId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) boolean estJustifiee,
            @RequestParam(required = false) String motif,
            @RequestParam(required = false) MultipartFile document,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId)
            .filter(e -> etabId.equals(e.getEtablissementId()))
            .orElse(null);
        if (eleve == null) {
            ra.addFlashAttribute("erreurMsg", "Élève introuvable dans cet établissement.");
            return "redirect:/surveillance";
        }

        String anneeScolaireAbsence = holyflame.administration.util.AnneeScolaireUtil.pour(date);
        anneeScolaireService.verifierModifiable(anneeScolaireAbsence, etabId);

        Absence absence = new Absence();
        absence.setEleve(eleve);
        absence.setDate(date);
        absence.setAnneeScolaire(anneeScolaireAbsence);
        absence.setPeriode(periode != null ? periode : "Journée entière");
        absence.setEstJustifiee(estJustifiee);
        absence.setMotif(motif);
        if (document != null && !document.isEmpty()) {
            try {
                String chemin = fileStorageService.store(document, "absences");
                absence.setDocumentPath(chemin);
                absence.setDocumentNomOriginal(document.getOriginalFilename());
            } catch (IOException e) {
                ra.addFlashAttribute("erreurMsg", "L'absence a ete enregistree, mais le document n'a pas pu etre televerse.");
            }
        }
        absenceRepository.save(absence);
        if (!estJustifiee) alerterAbsenceParSms(eleve, date);
        ra.addFlashAttribute("successMsg", "Absence enregistree pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        return "redirect:/surveillance";
    }

    // Voir SecretariatController.alerterAbsenceParSms — meme logique, dupliquee car ce controleur
    // a son propre point d'entree de saisie d'absence (double guichet secretariat/surveillance).
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

    @PostMapping("/absences/{id}/supprimer")
    public String supprimerAbsence(@PathVariable Long id) {
        // Sans le filtre par etablissement, n'importe quel id d'absence (y compris d'un autre
        // etablissement) etait supprimable par n'importe quel enseignant/admin connecte.
        Long etabId = etablissementService.getCurrentEtablissementId();
        absenceRepository.findById(id)
            .filter(a -> a.getEleve() != null && etabId != null && etabId.equals(a.getEleve().getEtablissementId()))
            .ifPresent(absenceRepository::delete);
        return "redirect:/surveillance";
    }

    @PostMapping("/programmes")
    public String creerProgramme(
            @RequestParam String titre,
            @RequestParam(required = false) String contenu,
            @RequestParam(required = false) String objectifs,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) Long classeId,
            @RequestParam(required = false) Long matiereId,
            @RequestParam(required = false) Long enseignantId,
            @RequestParam(required = false) Integer heuresEstimees,
            @RequestParam(required = false) String frequence,
            @RequestParam(required = false) MultipartFile syllabus,
            Authentication auth,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeScolaireProgramme = holyflame.administration.util.AnneeScolaireUtil.pour(dateDebut != null ? dateDebut : horlogeService.aujourdHui());
        anneeScolaireService.verifierModifiable(anneeScolaireProgramme, etabId);

        Programme programme = new Programme();
        programme.setTitre(titre);
        programme.setContenu(contenu);
        programme.setObjectifs(objectifs);
        programme.setDateDebut(dateDebut);
        programme.setDateFin(dateFin);
        programme.setAnneeScolaire(anneeScolaireProgramme);
        programme.setStatut("BROUILLON");
        programme.setAuteur(auth.getName());
        programme.setEtablissementId(etabId);
        programme.setHeuresEstimees(heuresEstimees);
        programme.setFrequence(frequence);
        if (classeId != null) {
            classeRepository.findById(classeId)
                .filter(c -> etabId.equals(c.getEtablissementId()))
                .ifPresent(programme::setClasse);
        }
        if (matiereId != null) {
            matiereRepository.findById(matiereId)
                .filter(m -> etabId.equals(m.getEtablissementId()))
                .ifPresent(programme::setMatiere);
        }
        if (enseignantId != null) {
            personnelRepository.findById(enseignantId)
                .filter(p -> etabId.equals(p.getEtablissementId()))
                .ifPresent(p -> programme.setEnseignantId(p.getId()));
        }
        if (syllabus != null && !syllabus.isEmpty()) {
            try {
                String chemin = fileStorageService.store(syllabus, "programmes");
                programme.setSyllabusPath(chemin);
                programme.setSyllabusNomOriginal(syllabus.getOriginalFilename());
            } catch (IOException e) {
                ra.addFlashAttribute("erreurMsg", "Le programme a ete enregistre, mais le syllabus n'a pas pu etre televerse.");
            }
        }
        programmeRepository.save(programme);
        ra.addFlashAttribute("successMsg", "Programme \"" + titre + "\" cree avec succes.");
        return "redirect:/surveillance";
    }

    @PostMapping("/programmes/{id}/publier")
    public String togglePublier(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        programmeRepository.findById(id)
            .filter(p -> etabId.equals(p.getEtablissementId()))
            .ifPresent(programme -> {
                anneeScolaireService.verifierModifiable(programme.getAnneeScolaire(), etabId);
                programme.setStatut("BROUILLON".equals(programme.getStatut()) ? "PUBLIE" : "BROUILLON");
                programmeRepository.save(programme);
            });
        return "redirect:/surveillance";
    }

    @PostMapping("/programmes/{id}/supprimer")
    public String supprimerProgramme(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        programmeRepository.findById(id)
            .filter(p -> etabId.equals(p.getEtablissementId()))
            .ifPresent(programme -> programmeRepository.delete(programme));
        return "redirect:/surveillance";
    }
}
