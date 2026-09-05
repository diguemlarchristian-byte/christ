package holyflame.administration.controller;

import holyflame.administration.model.ActualiteSite;
import holyflame.administration.model.EvenementPublic;
import holyflame.administration.model.MembreEquipePublic;
import holyflame.administration.model.PhotoGalerie;
import holyflame.administration.model.SiteVitrine;
import holyflame.administration.model.TemoignageSite;
import holyflame.administration.repository.ActualiteSiteRepository;
import holyflame.administration.repository.EvenementPublicRepository;
import holyflame.administration.repository.MembreEquipePublicRepository;
import holyflame.administration.repository.PhotoGalerieRepository;
import holyflame.administration.repository.SiteVitrineRepository;
import holyflame.administration.repository.TemoignageSiteRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import holyflame.administration.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Espace MARKETING : pilote le site vitrine public de l'etablissement (voir SitePublicController
 * pour le rendu public correspondant, sous /ecole/{slug}). N'a acces a aucune donnee d'eleve, de
 * personnel ou de finances — uniquement le contenu du site (actualites, galerie, evenements,
 * page a propos).
 */
@Controller
@RequestMapping("/marketing")
public class MarketingController {

    @Autowired private SiteVitrineRepository siteVitrineRepository;
    @Autowired private ActualiteSiteRepository actualiteSiteRepository;
    @Autowired private PhotoGalerieRepository photoGalerieRepository;
    @Autowired private EvenementPublicRepository evenementPublicRepository;
    @Autowired private TemoignageSiteRepository temoignageSiteRepository;
    @Autowired private MembreEquipePublicRepository membreEquipePublicRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private JournalService journalService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    private static final java.util.Set<String> POLICES_VALIDES = java.util.Set.of("CLASSIQUE", "MODERNE", "ELEGANTE");

    @GetMapping
    public String tableauDeBord(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SiteVitrine site = obtenirOuCreerSite(etabId);
        var etab = etablissementService.getCurrentEtablissement();

        model.addAttribute("site", site);
        model.addAttribute("nomEtablissement", etab != null ? etab.getNom() : "");
        var toutesActualites = actualiteSiteRepository.findByEtablissementIdOrderByDateCreationDesc(etabId);
        model.addAttribute("actualitesRecentes", toutesActualites.stream().limit(5).toList());
        model.addAttribute("nbPhotos", photoGalerieRepository.findByEtablissementIdOrderByDateAjoutDesc(etabId).size());

        // Une actualite redigee puis laissee en brouillon n'etait signalee nulle part : il fallait
        // parcourir la liste et reperer les badges un a un pour s'apercevoir qu'elle n'est pas en ligne.
        model.addAttribute("nbBrouillons", toutesActualites.stream()
            .filter(a -> !"PUBLIE".equals(a.getStatut())).count());

        // Le compteur d'evenements incluait les evenements passes, que le site public masque deja :
        // il pouvait annoncer "12 evenements" quand les visiteurs n'en voyaient aucun.
        var tousEvenements = evenementPublicRepository.findByEtablissementIdOrderByDateEvenementAsc(etabId);
        LocalDate aujourdHui = horlogeService.aujourdHui();
        model.addAttribute("nbEvenements", tousEvenements.stream()
            .filter(e -> e.getDateEvenement() != null && !e.getDateEvenement().isBefore(aujourdHui))
            .count());
        model.addAttribute("nbEvenementsPasses", tousEvenements.stream()
            .filter(e -> e.getDateEvenement() != null && e.getDateEvenement().isBefore(aujourdHui))
            .count());
        return "marketing-dashboard";
    }

    @PostMapping("/activer")
    public String activer(@RequestParam String slug, @RequestParam(required = false) Boolean actif, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SiteVitrine site = obtenirOuCreerSite(etabId);

        String slugNettoye = nettoyerSlug(slug);
        if (slugNettoye.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "Choisissez une adresse valide pour le site (lettres, chiffres, tirets).");
            return "redirect:/marketing";
        }
        var dejaPris = siteVitrineRepository.findBySlug(slugNettoye);
        if (dejaPris.isPresent() && !dejaPris.get().getId().equals(site.getId())) {
            ra.addFlashAttribute("erreurMsg", "Cette adresse est déjà utilisée. Choisissez-en une autre.");
            return "redirect:/marketing";
        }

        site.setSlug(slugNettoye);
        boolean nouvellementActif = Boolean.TRUE.equals(actif) && !site.isActif();
        site.setActif(Boolean.TRUE.equals(actif));
        if (nouvellementActif) site.setDateActivation(LocalDateTime.now());
        siteVitrineRepository.save(site);

        journalService.log(site.isActif() ? "SITE_VITRINE_ACTIVE" : "SITE_VITRINE_DESACTIVE",
            "MARKETING", "Site public : /ecole/" + site.getSlug());
        ra.addFlashAttribute("successMsg", site.isActif()
            ? "Site public activé : /ecole/" + site.getSlug()
            : "Site public désactivé.");
        return "redirect:/marketing";
    }

    @PostMapping("/a-propos")
    public String enregistrerAPropos(
            @RequestParam(required = false) String accroche,
            @RequestParam(required = false) String aPropos,
            @RequestParam(required = false) String infosAdmissions,
            @RequestParam(required = false) String emailContact,
            @RequestParam(required = false) String telephoneContact,
            @RequestParam(required = false) String facebookUrl,
            @RequestParam(required = false) String instagramUrl,
            RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SiteVitrine site = obtenirOuCreerSite(etabId);
        site.setAccroche(accroche);
        site.setaPropos(aPropos);
        site.setInfosAdmissions(infosAdmissions);
        site.setEmailContact(emailContact);
        site.setTelephoneContact(telephoneContact);
        site.setFacebookUrl(facebookUrl);
        site.setInstagramUrl(instagramUrl);
        siteVitrineRepository.save(site);
        ra.addFlashAttribute("successMsg", "Informations mises à jour.");
        return "redirect:/marketing";
    }

    // ── Design (heros, couleurs, typographie) ─────────────────────────────────
    // C'est ce bloc que l'assistant IA modifie aussi (voir AssistantService, outil
    // modifier_apparence_site) pour tout ce qui est exprimable en texte (URL video,
    // couleur hex, choix de police) — l'image de couverture reste un upload de fichier,
    // impossible a faire depuis une conversation.

    @PostMapping("/design")
    public String enregistrerDesign(
            @RequestParam(required = false) MultipartFile heroImage,
            @RequestParam(required = false) String heroVideoUrl,
            @RequestParam(required = false) String couleurSecondaire,
            @RequestParam(required = false) String police,
            @RequestParam(required = false) Boolean supprimerHeroImage,
            RedirectAttributes ra) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SiteVitrine site = obtenirOuCreerSite(etabId);

        if (Boolean.TRUE.equals(supprimerHeroImage) && site.getHeroImagePath() != null) {
            fileStorageService.delete(site.getHeroImagePath());
            site.setHeroImagePath(null);
        }
        if (heroImage != null && !heroImage.isEmpty()) {
            if (site.getHeroImagePath() != null) fileStorageService.delete(site.getHeroImagePath());
            site.setHeroImagePath(fileStorageService.store(heroImage, "site-public"));
        }
        site.setHeroVideoUrl(heroVideoUrl != null && !heroVideoUrl.isBlank() ? heroVideoUrl.trim() : null);
        site.setCouleurSecondaire(couleurSecondaire != null && !couleurSecondaire.isBlank() ? couleurSecondaire.trim() : null);
        if (police != null && POLICES_VALIDES.contains(police)) {
            site.setPolice(police);
        }
        siteVitrineRepository.save(site);
        journalService.log("SITE_VITRINE_DESIGN_MODIFIE", "MARKETING", "Apparence du site mise à jour.");
        ra.addFlashAttribute("successMsg", "Apparence du site mise à jour.");
        return "redirect:/marketing/design";
    }

    @GetMapping("/design")
    public String design(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("site", obtenirOuCreerSite(etabId));
        return "marketing-design";
    }

    // ── Temoignages ────────────────────────────────────────────────────────

    @GetMapping("/temoignages")
    public String temoignages(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("temoignages", temoignageSiteRepository.findByEtablissementIdOrderByDateAjoutDesc(etabId));
        return "marketing-temoignages";
    }

    @PostMapping("/temoignages")
    public String creerTemoignage(@RequestParam String auteur, @RequestParam(required = false) String role,
            @RequestParam String contenu, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        TemoignageSite t = new TemoignageSite();
        t.setEtablissementId(etabId);
        t.setAuteur(auteur);
        t.setRole(role);
        t.setContenu(contenu);
        t.setDateAjout(LocalDateTime.now());
        temoignageSiteRepository.save(t);
        journalService.log("TEMOIGNAGE_AJOUTE", "MARKETING", auteur);
        ra.addFlashAttribute("successMsg", "Témoignage ajouté.");
        return "redirect:/marketing/temoignages";
    }

    @PostMapping("/temoignages/{id}/supprimer")
    public String supprimerTemoignage(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        temoignageSiteRepository.findById(id)
            .filter(t -> etabId.equals(t.getEtablissementId()))
            .ifPresent(temoignageSiteRepository::delete);
        return "redirect:/marketing/temoignages";
    }

    // ── Equipe mise en avant publiquement ─────────────────────────────────

    @GetMapping("/equipe")
    public String equipe(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("membres", membreEquipePublicRepository.findByEtablissementIdOrderByOrdreAsc(etabId));
        return "marketing-equipe";
    }

    @PostMapping("/equipe")
    public String creerMembreEquipe(@RequestParam String nom, @RequestParam(required = false) String fonction,
            @RequestParam(required = false) String bio, @RequestParam(required = false) MultipartFile photo,
            @RequestParam(defaultValue = "0") Integer ordre, RedirectAttributes ra) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        MembreEquipePublic m = new MembreEquipePublic();
        m.setEtablissementId(etabId);
        m.setNom(nom);
        m.setFonction(fonction);
        m.setBio(bio);
        m.setOrdre(ordre);
        if (photo != null && !photo.isEmpty()) {
            m.setPhotoPath(fileStorageService.store(photo, "site-public"));
        }
        membreEquipePublicRepository.save(m);
        journalService.log("MEMBRE_EQUIPE_AJOUTE", "MARKETING", nom);
        ra.addFlashAttribute("successMsg", "Membre ajouté à l'équipe publique.");
        return "redirect:/marketing/equipe";
    }

    @PostMapping("/equipe/{id}/supprimer")
    public String supprimerMembreEquipe(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        membreEquipePublicRepository.findById(id)
            .filter(m -> etabId.equals(m.getEtablissementId()))
            .ifPresent(m -> {
                if (m.getPhotoPath() != null) fileStorageService.delete(m.getPhotoPath());
                membreEquipePublicRepository.delete(m);
            });
        return "redirect:/marketing/equipe";
    }

    // ── Actualites ──────────────────────────────────────────────────────────

    @GetMapping("/actualites")
    public String actualites(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("actualites", actualiteSiteRepository.findByEtablissementIdOrderByDateCreationDesc(etabId));
        return "marketing-actualites";
    }

    @PostMapping("/actualites")
    public String creerActualite(@RequestParam String titre, @RequestParam String contenu,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(defaultValue = "BROUILLON") String statut,
            RedirectAttributes ra) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        ActualiteSite a = new ActualiteSite();
        a.setEtablissementId(etabId);
        a.setTitre(titre);
        a.setContenu(contenu);
        a.setStatut("PUBLIE".equals(statut) ? "PUBLIE" : "BROUILLON");
        a.setDateCreation(LocalDateTime.now());
        if ("PUBLIE".equals(a.getStatut())) a.setDatePublication(LocalDateTime.now());
        if (image != null && !image.isEmpty()) {
            a.setImagePath(fileStorageService.store(image, "site-public"));
        }
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) a.setPublieParId(utilisateur.getId());
        actualiteSiteRepository.save(a);
        journalService.log("ACTUALITE_CREEE", "MARKETING", titre);
        ra.addFlashAttribute("successMsg", "Actualité enregistrée.");
        return "redirect:/marketing/actualites";
    }

    @PostMapping("/actualites/{id}/publier")
    public String publierActualite(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        ActualiteSite a = actualiteSiteRepository.findById(id).orElse(null);
        if (a == null || !etabId.equals(a.getEtablissementId())) {
            ra.addFlashAttribute("erreurMsg", "Actualité introuvable.");
            return "redirect:/marketing/actualites";
        }
        a.setStatut("PUBLIE");
        a.setDatePublication(LocalDateTime.now());
        actualiteSiteRepository.save(a);
        journalService.log("ACTUALITE_PUBLIEE", "MARKETING", a.getTitre());
        return "redirect:/marketing/actualites";
    }

    @PostMapping("/actualites/{id}/supprimer")
    public String supprimerActualite(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        actualiteSiteRepository.findById(id)
            .filter(a -> etabId.equals(a.getEtablissementId()))
            .ifPresent(a -> {
                if (a.getImagePath() != null) fileStorageService.delete(a.getImagePath());
                actualiteSiteRepository.delete(a);
                journalService.log("ACTUALITE_SUPPRIMEE", "MARKETING", a.getTitre());
            });
        return "redirect:/marketing/actualites";
    }

    // ── Galerie ─────────────────────────────────────────────────────────────

    @GetMapping("/galerie")
    public String galerie(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("photos", photoGalerieRepository.findByEtablissementIdOrderByDateAjoutDesc(etabId));
        return "marketing-galerie";
    }

    @PostMapping("/galerie")
    public String ajouterPhoto(@RequestParam MultipartFile image, @RequestParam(required = false) String legende,
            RedirectAttributes ra) throws IOException {
        if (image == null || image.isEmpty()) {
            ra.addFlashAttribute("erreurMsg", "Choisissez une image.");
            return "redirect:/marketing/galerie";
        }
        Long etabId = etablissementService.getCurrentEtablissementId();
        PhotoGalerie p = new PhotoGalerie();
        p.setEtablissementId(etabId);
        p.setImagePath(fileStorageService.store(image, "site-public"));
        p.setLegende(legende);
        p.setDateAjout(LocalDateTime.now());
        photoGalerieRepository.save(p);
        journalService.log("PHOTO_AJOUTEE", "MARKETING", legende != null && !legende.isBlank() ? legende : "(sans légende)");
        return "redirect:/marketing/galerie";
    }

    @PostMapping("/galerie/{id}/supprimer")
    public String supprimerPhoto(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        photoGalerieRepository.findById(id)
            .filter(p -> etabId.equals(p.getEtablissementId()))
            .ifPresent(p -> {
                fileStorageService.delete(p.getImagePath());
                photoGalerieRepository.delete(p);
            });
        return "redirect:/marketing/galerie";
    }

    // ── Evenements publics ─────────────────────────────────────────────────

    @GetMapping("/evenements")
    public String evenements(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("evenements", evenementPublicRepository.findByEtablissementIdOrderByDateEvenementAsc(etabId));
        return "marketing-evenements";
    }

    @PostMapping("/evenements")
    public String creerEvenement(@RequestParam String titre, @RequestParam(required = false) String description,
            @RequestParam String dateEvenement, @RequestParam(required = false) String lieu, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        EvenementPublic e = new EvenementPublic();
        e.setEtablissementId(etabId);
        e.setTitre(titre);
        e.setDescription(description);
        e.setLieu(lieu);
        try {
            e.setDateEvenement(LocalDate.parse(dateEvenement));
        } catch (Exception ex) {
            ra.addFlashAttribute("erreurMsg", "Date invalide.");
            return "redirect:/marketing/evenements";
        }
        evenementPublicRepository.save(e);
        journalService.log("EVENEMENT_PUBLIC_CREE", "MARKETING", titre);
        return "redirect:/marketing/evenements";
    }

    @PostMapping("/evenements/{id}/supprimer")
    public String supprimerEvenement(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        evenementPublicRepository.findById(id)
            .filter(e -> etabId.equals(e.getEtablissementId()))
            .ifPresent(evenementPublicRepository::delete);
        return "redirect:/marketing/evenements";
    }

    private SiteVitrine obtenirOuCreerSite(Long etabId) {
        return siteVitrineRepository.findByEtablissementId(etabId).orElseGet(() -> {
            SiteVitrine s = new SiteVitrine();
            s.setEtablissementId(etabId);
            s.setActif(false);
            return siteVitrineRepository.save(s);
        });
    }

    private String nettoyerSlug(String brut) {
        if (brut == null) return "";
        String s = brut.trim().toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-{2,}", "-");
        return s.replaceAll("^-+|-+$", "");
    }
}
