package holyflame.administration.controller;

import holyflame.administration.model.ActualiteSite;
import holyflame.administration.model.DemandeInscription;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.EvenementPublic;
import holyflame.administration.model.PhotoGalerie;
import holyflame.administration.model.SiteVitrine;
import holyflame.administration.repository.ActualiteSiteRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.EvenementPublicRepository;
import holyflame.administration.repository.MembreEquipePublicRepository;
import holyflame.administration.repository.PersonnelRepository;
import holyflame.administration.repository.PhotoGalerieRepository;
import holyflame.administration.repository.SiteVitrineRepository;
import holyflame.administration.repository.TemoignageSiteRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Site vitrine public d'un etablissement, sous /ecole/{slug} — accessible sans authentification
 * (voir SecurityConfig, permitAll). N'expose QUE ce qui a ete explicitement publie depuis
 * l'espace MARKETING (voir MarketingController) : aucune donnee d'eleve, de personnel ou de
 * finances ne transite jamais par ces routes. Un site desactive ou un slug inconnu renvoie une
 * page "introuvable" en 404, jamais d'erreur technique.
 */
@Controller
@RequestMapping("/ecole/{slug}")
public class SitePublicController {

    @Autowired private SiteVitrineRepository siteVitrineRepository;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private ActualiteSiteRepository actualiteSiteRepository;
    @Autowired private PhotoGalerieRepository photoGalerieRepository;
    @Autowired private EvenementPublicRepository evenementPublicRepository;
    @Autowired private TemoignageSiteRepository temoignageSiteRepository;
    @Autowired private MembreEquipePublicRepository membreEquipePublicRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private holyflame.administration.service.PreInscriptionService preInscriptionService;

    @GetMapping
    public String accueil(@PathVariable String slug, Model model, HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";

        List<ActualiteSite> actus = actualiteSiteRepository
            .findByEtablissementIdAndStatutOrderByDatePublicationDesc(etab.getId(), "PUBLIE");
        List<PhotoGalerie> photos = photoGalerieRepository.findByEtablissementIdOrderByDateAjoutDesc(etab.getId());
        List<EvenementPublic> evenements = evenementPublicRepository
            .findByEtablissementIdAndDateEvenementGreaterThanEqualOrderByDateEvenementAsc(etab.getId(), LocalDate.now());

        model.addAttribute("actualites", actus.stream().limit(3).toList());
        model.addAttribute("photos", photos.stream().limit(6).toList());
        model.addAttribute("evenements", evenements.stream().limit(3).toList());
        model.addAttribute("temoignages", temoignageSiteRepository.findByEtablissementIdOrderByDateAjoutDesc(etab.getId()));
        model.addAttribute("equipe", membreEquipePublicRepository.findByEtablissementIdOrderByOrdreAsc(etab.getId()));
        model.addAttribute("chiffresCles", chiffresCles(etab.getId()));
        return "site-public-accueil";
    }

    /**
     * Chiffres reels de l'etablissement (jamais de valeur inventee) pour la section
     * "chiffres cles" du site public : effectif, personnel, nombre de classes.
     */
    private Map<String, Long> chiffresCles(Long etabId) {
        return Map.of(
            "eleves", eleveRepository.countByEtablissementId(etabId),
            "personnel", personnelRepository.countByEtablissementId(etabId),
            "classes", (long) classeRepository.findByEtablissementId(etabId).size()
        );
    }

    @GetMapping("/actualites")
    public String actualites(@PathVariable String slug, Model model, HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";

        model.addAttribute("actualites", actualiteSiteRepository
            .findByEtablissementIdAndStatutOrderByDatePublicationDesc(etab.getId(), "PUBLIE"));
        return "site-public-actualites";
    }

    @GetMapping("/actualites/{id}")
    public String actualiteDetail(@PathVariable String slug, @PathVariable Long id, Model model, HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";

        ActualiteSite actu = actualiteSiteRepository.findById(id).orElse(null);
        if (actu == null || !etab.getId().equals(actu.getEtablissementId()) || !"PUBLIE".equals(actu.getStatut())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "site-public-introuvable";
        }
        model.addAttribute("actualite", actu);
        return "site-public-actualite-detail";
    }

    @GetMapping("/galerie")
    public String galerie(@PathVariable String slug, Model model, HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";

        model.addAttribute("photos", photoGalerieRepository.findByEtablissementIdOrderByDateAjoutDesc(etab.getId()));
        return "site-public-galerie";
    }

    @GetMapping("/evenements")
    public String evenements(@PathVariable String slug, Model model, HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";

        model.addAttribute("evenements", evenementPublicRepository
            .findByEtablissementIdAndDateEvenementGreaterThanEqualOrderByDateEvenementAsc(etab.getId(), LocalDate.now()));
        return "site-public-evenements";
    }

    @GetMapping("/a-propos")
    public String aPropos(@PathVariable String slug, Model model, HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";
        return "site-public-a-propos";
    }

    // ── Pre-inscription ─────────────────────────────────────────────────
    //
    // La seule page de ce site qui accepte quelque chose au lieu de se contenter de
    // publier. Tout ce qui y entre vient d'un inconnu : le service qui la recoit borne,
    // coupe et refuse, et rien n'atteint le registre de l'ecole sans qu'une personne du
    // secretariat l'ait lu.

    @GetMapping("/pre-inscription")
    public String formulairePreInscription(@PathVariable String slug, Model model,
                                           HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";
        if (!site.isPreinscriptionActive()) {
            // Fermee n'est pas introuvable : la famille doit comprendre que l'ecole existe
            // bien, mais qu'elle ne recoit pas de demande en ce moment.
            return "site-public-preinscription-fermee";
        }
        model.addAttribute("niveaux", niveauxProposes(etab.getId()));
        return "site-public-preinscription";
    }

    @PostMapping("/pre-inscription")
    public String envoyerPreInscription(@PathVariable String slug,
                                        @ModelAttribute DemandeInscription demande,
                                        @RequestParam(required = false) String siteWeb,
                                        Model model, HttpServletResponse response) {
        SiteVitrine site = chargerSiteActif(slug, response);
        if (site == null) return "site-public-introuvable";
        Etablissement etab = chargerEtablissement(site, model, response);
        if (etab == null) return "site-public-introuvable";
        if (!site.isPreinscriptionActive()) return "site-public-preinscription-fermee";

        // L'etablissement vient du site, jamais du formulaire : sans cela, un envoi forge
        // deposerait une demande dans l'ecole de son choix.
        demande.setEtablissementId(etab.getId());
        demande.setEleveId(null);
        demande.setTraiteePar(null);
        demande.setNoteInterne(null);

        String annee = etab.getAnneeScolaire() != null ? etab.getAnneeScolaire() : "";
        var resultat = preInscriptionService.enregistrer(demande, annee, siteWeb);

        if (!resultat.accepte()) {
            if (resultat.message() == null) {
                // Envoi automatique : on affiche la meme page de confirmation qu'a une
                // famille, sans rien avoir enregistre.
                model.addAttribute("reference", null);
                return "site-public-preinscription-recue";
            }
            model.addAttribute("erreur", resultat.message());
            model.addAttribute("demande", demande);
            model.addAttribute("niveaux", niveauxProposes(etab.getId()));
            return "site-public-preinscription";
        }

        model.addAttribute("reference", resultat.demande().getReference());
        return "site-public-preinscription-recue";
    }

    /**
     * Les niveaux que l'ecole ouvre reellement, lus dans ses classes.
     *
     * Une liste ecrite en dur proposerait la sixieme a une ecole primaire, et une famille
     * deposerait une demande que personne ne peut satisfaire.
     */
    private java.util.List<String> niveauxProposes(Long etablissementId) {
        return classeRepository.findByEtablissementId(etablissementId).stream()
            .map(c -> c.getNiveau())
            .filter(n -> n != null && !n.isBlank())
            .map(String::trim)
            .distinct()
            .sorted()
            .toList();
    }

    private SiteVitrine chargerSiteActif(String slug, HttpServletResponse response) {
        SiteVitrine site = siteVitrineRepository.findBySlugAndActifTrue(slug).orElse(null);
        if (site == null) response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return site;
    }

    private Etablissement chargerEtablissement(SiteVitrine site, Model model, HttpServletResponse response) {
        Etablissement etab = etablissementRepository.findById(site.getEtablissementId()).orElse(null);
        if (etab == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        model.addAttribute("site", site);
        model.addAttribute("etablissement", etab);
        return etab;
    }
}
