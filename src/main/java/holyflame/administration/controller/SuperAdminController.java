package holyflame.administration.controller;

import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.PersonnelRepository;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;
import java.util.UUID;

@Controller
@RequestMapping("/super-admin")
public class SuperAdminController {

    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping
    public String dashboard(Model model) {
        // Liste principale : tous sauf SUPPRIME
        List<Etablissement> etablissements = etablissementRepository.findByStatutNotOrderByNomAsc("SUPPRIME");
        // Corbeille : uniquement les SUPPRIME
        List<Etablissement> corbeille = etablissementRepository.findByStatutOrderByNomAsc("SUPPRIME");

        java.util.Map<Long, Utilisateur> adminParEtab = new java.util.HashMap<>();
        for (Etablissement e : etablissements) {
            List<Utilisateur> admins = utilisateurRepository
                    .findByRoleAndEtablissementIdOrderByNomAsc("ADMIN", e.getId());
            if (!admins.isEmpty()) adminParEtab.put(e.getId(), admins.get(0));
        }
        // Inclure aussi les admins des établissements en corbeille (pour affichage)
        for (Etablissement e : corbeille) {
            List<Utilisateur> admins = utilisateurRepository
                    .findByRoleAndEtablissementIdOrderByNomAsc("ADMIN", e.getId());
            if (!admins.isEmpty()) adminParEtab.put(e.getId(), admins.get(0));
        }

        // Stats par établissement : nb élèves + nb personnels
        Map<Long, Long> nbElevesParEtab = new HashMap<>();
        Map<Long, Long> nbPersonnelsParEtab = new HashMap<>();
        long totalElevesGlobal = 0;
        for (Etablissement e : etablissements) {
            long nbE = eleveRepository.countByEtablissementId(e.getId());
            long nbP = personnelRepository.countByEtablissementId(e.getId());
            nbElevesParEtab.put(e.getId(), nbE);
            nbPersonnelsParEtab.put(e.getId(), nbP);
            totalElevesGlobal += nbE;
        }

        List<Utilisateur> tousUtilisateurs = utilisateurRepository.findAllWithEtablissement();

        model.addAttribute("etablissements", etablissements);
        model.addAttribute("corbeille", corbeille);
        model.addAttribute("adminParEtab", adminParEtab);
        model.addAttribute("nbElevesParEtab", nbElevesParEtab);
        model.addAttribute("nbPersonnelsParEtab", nbPersonnelsParEtab);
        model.addAttribute("totalElevesGlobal", totalElevesGlobal);
        model.addAttribute("totalEtablissements", etablissements.size());
        model.addAttribute("totalActifs", etablissements.stream().filter(e -> "ACTIF".equals(e.getStatut())).count());
        model.addAttribute("totalUtilisateurs", tousUtilisateurs.size());
        model.addAttribute("tousUtilisateurs", tousUtilisateurs);

        // Un etablissement sans compte ADMIN est inutilisable : personne ne peut s'y connecter
        // pour l'administrer. La colonne "Admin" du tableau se contentait d'afficher "--", ce qui
        // ne distingue pas une panne bloquante d'une donnee manquante anodine.
        List<Etablissement> sansAdmin = etablissements.stream()
            .filter(e -> !adminParEtab.containsKey(e.getId()))
            .toList();
        model.addAttribute("etablissementsSansAdmin", sansAdmin);
        return "super-admin/dashboard";
    }

    @PostMapping("/etablissements")
    public String creerEtablissement(
            @RequestParam String nom,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String contact,
            @RequestParam(required = false) String typeEtablissement,
            @RequestParam(required = false) String anneeScolaire,
            @RequestParam String adminNom,
            @RequestParam String adminPrenom,
            @RequestParam String adminEmail,
            @RequestParam String adminMotDePasse,
            RedirectAttributes ra) {

        Etablissement etab = new Etablissement();
        etab.setNom(nom);
        etab.setVille(ville);
        etab.setTelephone(telephone);
        etab.setEmail(email);
        etab.setContact(contact);
        etab.setTypeEtablissement(typeEtablissement);
        etab.setAnneeScolaire(anneeScolaire != null ? anneeScolaire : "2025-2026");
        etab.setStatut("ACTIF");
        etab.setDateCreation(LocalDate.now());
        String code = "HF-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        etab.setCodeAcces(code);
        etablissementRepository.save(etab);

        Utilisateur admin = new Utilisateur();
        admin.setNom(adminNom.toUpperCase());
        admin.setPrenom(adminPrenom);
        admin.setEmail(adminEmail);
        admin.setMotDePasse(passwordEncoder.encode(adminMotDePasse));
        admin.setRole("ADMIN");
        admin.setEtablissement(etab);
        utilisateurRepository.save(admin);

        ra.addFlashAttribute("success", "Établissement \"" + nom + "\" créé. Code d'accès : " + code);
        return "redirect:/super-admin";
    }

    @PostMapping("/etablissements/{id}/suspendre")
    public String suspendre(@PathVariable Long id, RedirectAttributes ra) {
        etablissementRepository.findById(id).ifPresent(e -> {
            e.setStatut("SUSPENDU");
            etablissementRepository.save(e);
        });
        ra.addFlashAttribute("success", "Établissement suspendu.");
        return "redirect:/super-admin";
    }

    @PostMapping("/etablissements/{id}/activer")
    public String activer(@PathVariable Long id, RedirectAttributes ra) {
        etablissementRepository.findById(id).ifPresent(e -> {
            e.setStatut("ACTIF");
            etablissementRepository.save(e);
        });
        ra.addFlashAttribute("success", "Établissement activé.");
        return "redirect:/super-admin";
    }

    @PostMapping("/etablissements/{id}/modifier")
    public String modifierEtablissement(
            @PathVariable Long id,
            @RequestParam String nom,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String contact,
            @RequestParam(required = false) String typeEtablissement,
            @RequestParam(required = false) String anneeScolaire,
            @RequestParam(required = false) String nouveauMotDePasse,
            RedirectAttributes ra) {

        etablissementRepository.findById(id).ifPresent(e -> {
            e.setNom(nom);
            e.setVille(ville);
            e.setTelephone(telephone);
            e.setEmail(email);
            e.setContact(contact);
            e.setTypeEtablissement(typeEtablissement);
            if (anneeScolaire != null && !anneeScolaire.isBlank()) {
                e.setAnneeScolaire(anneeScolaire);
            }
            etablissementRepository.save(e);
        });

        // Réinitialisation du mot de passe admin si renseigné
        if (nouveauMotDePasse != null && !nouveauMotDePasse.isBlank()) {
            List<Utilisateur> admins = utilisateurRepository
                    .findByRoleAndEtablissementIdOrderByNomAsc("ADMIN", id);
            if (!admins.isEmpty()) {
                Utilisateur admin = admins.get(0);
                admin.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
                utilisateurRepository.save(admin);
                ra.addFlashAttribute("success", "Établissement \"" + nom + "\" modifié et mot de passe réinitialisé.");
                return "redirect:/super-admin";
            }
        }
        ra.addFlashAttribute("success", "Établissement \"" + nom + "\" modifié avec succès.");
        return "redirect:/super-admin";
    }

    // Soft delete : marque SUPPRIME sans rien effacer
    @PostMapping("/etablissements/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        etablissementRepository.findById(id).ifPresent(e -> {
            e.setStatut("SUPPRIME");
            etablissementRepository.save(e);
        });
        ra.addFlashAttribute("success", "Établissement déplacé dans la corbeille. Il peut être restauré.");
        return "redirect:/super-admin";
    }

    // Restaurer un établissement depuis la corbeille
    @PostMapping("/etablissements/{id}/restaurer")
    public String restaurer(@PathVariable Long id, RedirectAttributes ra) {
        etablissementRepository.findById(id).ifPresent(e -> {
            e.setStatut("ACTIF");
            etablissementRepository.save(e);
        });
        ra.addFlashAttribute("success", "Établissement restauré avec succès.");
        return "redirect:/super-admin";
    }

    // Suppression définitive depuis la corbeille uniquement
    @PostMapping("/etablissements/{id}/supprimer-definitif")
    public String supprimerDefinitif(@PathVariable Long id, RedirectAttributes ra) {
        etablissementRepository.findById(id).ifPresent(e -> {
            if ("SUPPRIME".equals(e.getStatut())) {
                List<Utilisateur> utilisateurs = utilisateurRepository.findByEtablissementId(id);
                utilisateurRepository.deleteAll(utilisateurs);
                etablissementRepository.delete(e);
            }
        });
        ra.addFlashAttribute("success", "Établissement supprimé définitivement.");
        return "redirect:/super-admin";
    }

    // Changer le plan d'abonnement
    @PostMapping("/etablissements/{id}/abonnement")
    public String changerAbonnement(@PathVariable Long id,
                                    @RequestParam String plan,
                                    RedirectAttributes ra) {
        etablissementRepository.findById(id).ifPresent(e -> {
            e.setPlanAbonnement(plan);
            etablissementRepository.save(e);
        });
        ra.addFlashAttribute("success", "Plan mis à jour : " + plan + ".");
        return "redirect:/super-admin";
    }
}
