package holyflame.administration.controller;

import holyflame.administration.model.ArticleInventaire;
import holyflame.administration.model.Depense;
import holyflame.administration.model.MouvementInventaire;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ArticleInventaireRepository;
import holyflame.administration.repository.DepenseRepository;
import holyflame.administration.repository.MouvementInventaireRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.InventaireRegles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/inventaire")
public class InventaireController {

    @Autowired private ArticleInventaireRepository articleRepository;
    @Autowired private MouvementInventaireRepository mouvementRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    @GetMapping
    public String index(@RequestParam(required = false) String q,
                        @RequestParam(required = false) String categorie,
                        @RequestParam(required = false) String etat,
                        Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur utilisateur = etablissementService.getCurrentUtilisateur();
        model.addAttribute("utilisateurConnecte", utilisateur);

        List<ArticleInventaire> tous = etabId != null
            ? articleRepository.findByEtablissementIdOrderByCategorieAscNomAsc(etabId)
            : List.of();

        // Les compteurs decrivent tout l'inventaire, jamais le resultat filtre : un total qui
        // change quand on tape dans la recherche ne veut plus rien dire.
        model.addAttribute("totalArticles", tous.size());
        model.addAttribute("totalEnService", tous.stream().mapToInt(ArticleInventaire::getQuantiteEnService).sum());
        model.addAttribute("totalReparation", tous.stream().mapToInt(ArticleInventaire::getQuantiteEnReparation).sum());
        model.addAttribute("totalHorsService", tous.stream().mapToInt(ArticleInventaire::getQuantiteHorsService).sum());
        model.addAttribute("valeurTotale", tous.stream().mapToDouble(ArticleInventaire::getValeurEnService).sum());

        List<ArticleInventaire> articles = tous.stream()
            .filter(a -> categorie == null || categorie.isBlank() || categorie.equals(a.getCategorie()))
            .filter(a -> etat == null || etat.isBlank() || etat.equals(a.getEtat()))
            .filter(a -> correspond(a, q))
            .toList();
        model.addAttribute("articles", articles);
        // Le formulaire de modification est rempli cote navigateur. On ne lui envoie pas les
        // entites : leur collection de mouvements est chargee paresseusement, et la serialiser
        // en JSON la reveillerait hors session. Cette projection ne porte que les champs du
        // formulaire, et la date au format que l'input attend.
        model.addAttribute("articlesJson", articles.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("nom", a.getNom());
            m.put("categorie", a.getCategorie());
            m.put("etat", a.getEtat());
            m.put("quantite", a.getQuantite());
            m.put("valeurUnitaire", a.getValeurUnitaire());
            m.put("localisation", a.getLocalisation());
            m.put("fournisseur", a.getFournisseur());
            m.put("numSerie", a.getNumSerie());
            m.put("dateAcquisition", a.getDateAcquisition() != null ? a.getDateAcquisition().toString() : null);
            m.put("notes", a.getNotes());
            m.put("depenseId", a.getDepenseId());
            return m;
        }).toList());
        model.addAttribute("filtre", q);
        model.addAttribute("filtreCategorie", categorie);
        model.addAttribute("filtreEtat", etat);
        model.addAttribute("filtreActif",
            (q != null && !q.isBlank()) || (categorie != null && !categorie.isBlank())
                || (etat != null && !etat.isBlank()));

        // Un seul aller-retour pour tout l'historique : une requete par ligne affichee aurait
        // rendu la page lente des la premiere centaine d'articles.
        Map<Long, List<MouvementInventaire>> historique = etabId != null
            ? mouvementRepository.findByArticleEtablissementIdOrderByDateDescIdDesc(etabId).stream()
                .collect(Collectors.groupingBy(m -> m.getArticle().getId()))
            : Map.of();
        model.addAttribute("historique", historique);

        model.addAttribute("categories", InventaireRegles.CATEGORIES);
        model.addAttribute("etats", InventaireRegles.ETATS);
        model.addAttribute("typesMouvement", InventaireRegles.MOUVEMENTS);

        // Rattacher un article a la depense qui l'a paye suppose de voir les depenses. Le
        // Directeur, qui peut recevoir le module Inventaire, est tenu a l'ecart de la finance :
        // la liste ne lui est ni proposee, ni transmise.
        boolean peutRattacherDepense = utilisateur != null && "ADMIN".equals(utilisateur.getRole());
        model.addAttribute("peutRattacherDepense", peutRattacherDepense);
        List<Depense> depenses = peutRattacherDepense && etabId != null
            ? depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId)
            : List.of();
        model.addAttribute("depenses", depenses);
        model.addAttribute("depensesParId", depenses.stream()
            .collect(Collectors.toMap(Depense::getId, d -> d, (x, y) -> x)));

        return "inventaire";
    }

    /** Recherche sur ce qui sert a retrouver un materiel : son nom, ou l'endroit ou il se trouve. */
    private boolean correspond(ArticleInventaire a, String q) {
        if (q == null || q.isBlank()) return true;
        String terme = q.trim().toLowerCase();
        return contient(a.getNom(), terme) || contient(a.getLocalisation(), terme)
            || contient(a.getNumSerie(), terme) || contient(a.getFournisseur(), terme);
    }

    private boolean contient(String valeur, String terme) {
        return valeur != null && valeur.toLowerCase().contains(terme);
    }

    @PostMapping
    public String ajouter(@RequestParam String nom,
                          @RequestParam String categorie,
                          @RequestParam int quantite,
                          @RequestParam String etat,
                          @RequestParam(required = false) String localisation,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateAcquisition,
                          @RequestParam(required = false) Double valeurUnitaire,
                          @RequestParam(required = false) String fournisseur,
                          @RequestParam(required = false) String numSerie,
                          @RequestParam(required = false) String notes,
                          @RequestParam(required = false) Long depenseId,
                          RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        // Un article cree sans etablissement etait ensuite rattache au premier qui ouvrait la
        // page : mieux vaut refuser la saisie que la voir atterrir dans une autre ecole.
        if (etabId == null) {
            ra.addFlashAttribute("erreur",
                "Aucun etablissement actif : impossible d'enregistrer un article.");
            return "redirect:/inventaire";
        }

        ArticleInventaire a = new ArticleInventaire();
        a.setEtablissementId(etabId);
        appliquerLaSaisie(a, nom, categorie, quantite, etat, localisation, dateAcquisition,
            valeurUnitaire, fournisseur, numSerie, notes, depenseId);
        articleRepository.save(a);
        ra.addFlashAttribute("succes", "Article " + a.getNom() + " ajoute a l'inventaire.");
        return "redirect:/inventaire";
    }

    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id,
                           @RequestParam String nom,
                           @RequestParam String categorie,
                           @RequestParam int quantite,
                           @RequestParam String etat,
                           @RequestParam(required = false) String localisation,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateAcquisition,
                           @RequestParam(required = false) Double valeurUnitaire,
                           @RequestParam(required = false) String fournisseur,
                           @RequestParam(required = false) String numSerie,
                           @RequestParam(required = false) String notes,
                           @RequestParam(required = false) Long depenseId,
                           RedirectAttributes ra) {

        ArticleInventaire a = articleRepository.findById(id).orElseThrow();
        verifierProprietaire(a);
        appliquerLaSaisie(a, nom, categorie, quantite, etat, localisation, dateAcquisition,
            valeurUnitaire, fournisseur, numSerie, notes, depenseId);

        // Reduire un lot sous le nombre d'unites deja declarees indisponibles laisserait des
        // compteurs impossibles (3 en reparation sur 2).
        if (a.getQuantiteEnReparation() + a.getQuantiteHorsService() > a.getQuantite()) {
            ra.addFlashAttribute("erreur", "Quantite trop faible : " + a.getQuantiteEnReparation()
                + " unite(s) en reparation et " + a.getQuantiteHorsService()
                + " hors service sont deja enregistrees pour cet article.");
            return "redirect:/inventaire";
        }

        articleRepository.save(a);
        ra.addFlashAttribute("succes", "Article " + a.getNom() + " modifie.");
        return "redirect:/inventaire";
    }

    private void appliquerLaSaisie(ArticleInventaire a, String nom, String categorie, int quantite,
                                   String etat, String localisation, LocalDate dateAcquisition,
                                   Double valeurUnitaire, String fournisseur, String numSerie,
                                   String notes, Long depenseId) {
        a.setNom(nom);
        a.setCategorie(categorie);
        a.setQuantite(Math.max(0, quantite));
        a.setEtat(etat);
        a.setLocalisation(localisation);
        a.setDateAcquisition(dateAcquisition);
        a.setValeurUnitaire(valeurUnitaire);
        a.setFournisseur(fournisseur);
        a.setNumSerie(numSerie);
        a.setNotes(notes);

        // Le rattachement comptable n'est accepte que de qui a le droit de voir les depenses ;
        // sans ce controle, un depenseId poste a la main suffirait a contourner l'ecran.
        Utilisateur u = etablissementService.getCurrentUtilisateur();
        if (u != null && "ADMIN".equals(u.getRole())) {
            a.setDepenseId(estRattachable(depenseId) ? depenseId : null);
        }
    }

    /** La depense doit exister et appartenir au meme etablissement que l'article. */
    private boolean estRattachable(Long depenseId) {
        if (depenseId == null) return false;
        Long etabId = etablissementService.getCurrentEtablissementId();
        return depenseRepository.findById(depenseId)
            .map(d -> etabId != null && etabId.equals(d.getEtablissementId()))
            .orElse(false);
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        ArticleInventaire a = articleRepository.findById(id).orElseThrow();
        verifierProprietaire(a);
        String nom = a.getNom();
        articleRepository.delete(a);
        ra.addFlashAttribute("succes", "Article " + nom + " supprime, avec son historique.");
        return "redirect:/inventaire";
    }

    @PostMapping("/{id}/mouvement")
    public String ajouterMouvement(@PathVariable Long id,
                                   @RequestParam String type,
                                   @RequestParam int quantite,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) String motif,
                                   @RequestParam(required = false) String effectuePar,
                                   RedirectAttributes ra) {

        ArticleInventaire a = articleRepository.findById(id).orElseThrow();
        verifierProprietaire(a);

        try {
            InventaireRegles.appliquer(a, type, quantite);
        } catch (IllegalArgumentException e) {
            // Le refus est explique a l'utilisateur : rien n'est enregistre, ni le mouvement,
            // ni la moindre correction silencieuse des compteurs.
            ra.addFlashAttribute("erreur", e.getMessage());
            return "redirect:/inventaire";
        }

        MouvementInventaire m = new MouvementInventaire();
        m.setArticle(a);
        m.setType(type);
        m.setQuantite(quantite);
        m.setDate(date != null ? date : horlogeService.aujourdHui());
        m.setMotif(motif);
        m.setEffectuePar(effectuePar != null && !effectuePar.isBlank()
            ? effectuePar : nomDeLUtilisateurConnecte());

        mouvementRepository.save(m);
        articleRepository.save(a);
        ra.addFlashAttribute("succes", InventaireRegles.libelleMouvement(type)
            + " : " + quantite + " unite(s) sur " + a.getNom() + ".");
        return "redirect:/inventaire";
    }

    /** Qui a fait le mouvement : a defaut de saisie, le compte connecte, jamais une case vide. */
    private String nomDeLUtilisateurConnecte() {
        Utilisateur u = etablissementService.getCurrentUtilisateur();
        if (u == null) return null;
        String nom = ((u.getPrenom() != null ? u.getPrenom() + " " : "")
            + (u.getNom() != null ? u.getNom() : "")).trim();
        return nom.isEmpty() ? u.getEmail() : nom;
    }

    private void verifierProprietaire(ArticleInventaire article) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null || article.getEtablissementId() == null
                || !etabId.equals(article.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Article introuvable dans cet établissement.");
        }
    }
}
