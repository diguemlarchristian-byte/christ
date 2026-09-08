package holyflame.administration.controller;

import holyflame.administration.model.ArriereEleve;
import holyflame.administration.model.CategorieComptable;
import holyflame.administration.model.Classe;
import holyflame.administration.model.ComptageCaisse;
import holyflame.administration.model.Depense;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.LigneBudget;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.RemiseEleve;
import holyflame.administration.model.Parametre;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ArriereEleveRepository;
import holyflame.administration.repository.CategorieComptableRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.ComptageCaisseRepository;
import holyflame.administration.repository.DepenseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.LigneBudgetRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EmailService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FinanceModules;
import holyflame.administration.service.FinanceParentService;
import holyflame.administration.service.JournalService;
import holyflame.administration.service.NombreEnLettresService;
import holyflame.administration.util.AnneeScolaireUtil;
import holyflame.administration.util.WhatsAppUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/finances")
public class FinancesController {

    private static final DateTimeFormatter FMT_PARAM_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private PaiementRepository paiementRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private LigneBudgetRepository budgetRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private CategorieComptableRepository categorieComptableRepository;
    @Autowired private ArriereEleveRepository arriereEleveRepository;
    @Autowired private holyflame.administration.repository.RemiseEleveRepository remiseEleveRepository;
    @Autowired private holyflame.administration.service.SituationFinanciereService situationFinanciereService;
    @Autowired private ComptageCaisseRepository comptageCaisseRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private FraisScolariteRepository fraisScolariteRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired private FinanceParentService financeParentService;
    @Autowired private holyflame.administration.service.PlanComptableService planComptableService;
    @Autowired private JournalService journalService;
    @Autowired private EmailService emailService;
    @Autowired private NombreEnLettresService nombreEnLettresService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private holyflame.administration.service.ClotureMensuelleService clotureMensuelleService;

    // ===== PAGE UNIQUE A ONGLETS =====
    @GetMapping
    public String index(
            @RequestParam(defaultValue = "journal") String tab,
            @RequestParam(required = false) String annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer anneeCivile,
            @RequestParam(required = false) String categorieComptableId,
            @RequestParam(required = false) String statutDepense,
            @RequestParam(required = false) Integer rapportTrimestre,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        if (annee == null || annee.isBlank()) annee = etablissementService.getAnneeScolaireActive();
        LocalDate aujourdHui = horlogeService.aujourdHui();
        int moisFiltre = mois != null ? mois : aujourdHui.getMonthValue();
        int anneeFiltre = anneeCivile != null ? anneeCivile : aujourdHui.getYear();
        int trimestreChoisi = rapportTrimestre != null && rapportTrimestre >= 1 && rapportTrimestre <= 3 ? rapportTrimestre : 1;

        Utilisateur utilisateurConnecte = etablissementService.getCurrentUtilisateur();
        model.addAttribute("utilisateurConnecte", utilisateurConnecte);

        // Segmentation Tresorier/Comptable : chaque compte n'a acces qu'aux onglets couverts par
        // ses modules financiers effectifs (personnalises par l'ADMIN, ou par defaut de son role
        // sinon — voir FinanceModules). Un onglet demande hors de ce perimetre est neutralise vers
        // le premier onglet auquel l'utilisateur a droit.
        Set<String> modulesFinance = FinanceModules.effectifs(utilisateurConnecte);
        model.addAttribute("modulesFinanceActifs", modulesFinance);
        tab = ongletAutorise(tab, modulesFinance);
        model.addAttribute("tab", tab);
        model.addAttribute("annee", annee);

        // Filet de securite : un etablissement cree avant ce correctif (ou par tout autre chemin que
        // l'inscription en ligne) peut encore n'avoir aucune categorie comptable — sans quoi Depenses/
        // Budget sont inutilisables. Sans effet si le plan comptable existe deja.
        planComptableService.seedSiVide(etabId);
        List<CategorieComptable> categories = categorieComptableRepository.findByEtablissementIdAndActifTrueOrderByCodeAsc(etabId);
        List<CategorieComptable> categoriesCharges = categories.stream().filter(c -> "CHARGE".equals(c.getSens())).collect(Collectors.toList());
        List<CategorieComptable> categoriesProduits = categories.stream().filter(c -> "PRODUIT".equals(c.getSens())).collect(Collectors.toList());
        // Vue de gestion complete du plan comptable (actives + desactivees) pour l'onglet
        // Parametrage — distincte de "categories" ci-dessus qui reste reservee aux listes
        // deroulantes de saisie (toujours actives uniquement).
        model.addAttribute("toutesLesCategories", categorieComptableRepository.findByEtablissementIdOrderByCodeAsc(etabId));
        model.addAttribute("categoriesCharges", categoriesCharges);
        model.addAttribute("categoriesProduits", categoriesProduits);

        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId);
        List<Depense> depenses = depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId);
        // Tous les eleves, toutes annees confondues : necessaire uniquement pour retrouver un ancien
        // eleve lors de l'enregistrement d'un arriere reporte (l'arriere peut concerner une annee passee).
        List<Eleve> tousLesEleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        // Eleves de l'annee scolaire active uniquement : utilises partout ailleurs (saisie de paiement,
        // suivi de scolarite, KPIs, rapport par classe) pour ne jamais melanger des eleves d'annees ecoulees.
        String anneeActive = etablissementService.getAnneeScolaireActive();
        List<Eleve> elevesAnneeActive = tousLesEleves.stream()
            .filter(e -> e.getClasse() == null || anneeActive.equals(e.getClasse().getAnneeScolaire()))
            .collect(Collectors.toList());
        List<FraisScolarite> tousLesFrais = fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId);
        List<LigneBudget> lignesBudget = budgetRepository.findByEtablissementIdAndAnneeScolaireOrderByDesignationAsc(etabId, annee);

        model.addAttribute("eleves", elevesAnneeActive);
        model.addAttribute("elevesRecherche", tousLesEleves);
        model.addAttribute("tousLesFrais", tousLesFrais);

        buildResume(model, paiements, lignesBudget, elevesAnneeActive, tousLesFrais, moisFiltre);
        buildJournal(model, etabId, paiements, depenses, moisFiltre, anneeFiltre);
        buildScolarite(model, etabId, elevesAnneeActive);
        buildDepensesTab(model, etabId, depenses, lignesBudget, moisFiltre, anneeFiltre, categorieComptableId, statutDepense);
        buildBudgetTab(model, lignesBudget);
        buildRapports(model, etabId, elevesAnneeActive, depenses, paiements, annee);
        buildRapportEconome(model, etabId, paiements, depenses, trimestreChoisi);

        // ===== ONGLET PARAMETRAGE =====
        Map<String, String> tauxPaie = parametreRepository.findByCategorieAndEtablissementIdOrderByCleAsc("PAIE", etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));
        model.addAttribute("tauxPaie", tauxPaie);

        // Clotures mensuelles : verrouillage comptable mois par mois, independant de la cloture
        // d'annee scolaire — voir ClotureMensuelleService.
        List<Map<String, Object>> douzeMois = new ArrayList<>();
        LocalDate curseurMois = horlogeService.aujourdHui().withDayOfMonth(1);
        for (int i = 0; i < 12; i++) {
            boolean cloture = clotureMensuelleService.estCloture(curseurMois.getMonthValue(), curseurMois.getYear(), etabId);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("mois", curseurMois.getMonthValue());
            ligne.put("anneeCivile", curseurMois.getYear());
            ligne.put("libelle", curseurMois.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH) + " " + curseurMois.getYear());
            ligne.put("cloture", cloture);
            douzeMois.add(ligne);
            curseurMois = curseurMois.minusMonths(1);
        }
        model.addAttribute("moisComptables", douzeMois);

        return "finances";
    }

    /** Ramene l'onglet demande vers le premier onglet accessible si l'utilisateur n'a pas le module requis. */
    private String ongletAutorise(String tabDemande, Set<String> modules) {
        String moduleRequis = switch (tabDemande) {
            case "depenses" -> FinanceModules.DEPENSES;
            case "budget" -> FinanceModules.BUDGET_PARAMETRAGE;
            case "rapports" -> FinanceModules.RAPPORTS;
            case "parametrage" -> null; // couvre solde initial (CAISSE) et taux de paie (BUDGET_PARAMETRAGE)
            default -> FinanceModules.CAISSE; // journal, scolarite
        };
        boolean autorise = "parametrage".equals(tabDemande)
            ? (modules.contains(FinanceModules.CAISSE) || modules.contains(FinanceModules.BUDGET_PARAMETRAGE))
            : modules.contains(moduleRequis);
        if (autorise) return tabDemande;
        if (modules.contains(FinanceModules.CAISSE)) return "journal";
        if (modules.contains(FinanceModules.DEPENSES)) return "depenses";
        if (modules.contains(FinanceModules.BUDGET_PARAMETRAGE)) return "budget";
        if (modules.contains(FinanceModules.RAPPORTS)) return "rapports";
        return "journal";
    }

    // ===== ONGLET RAPPORTS — Rapport trimestriel econome (calque du rapport papier de l'econome) =====
    private static final List<String> ORDRE_GROUPES_ECONOME = List.of(
        "FOURNITURES_SCOLAIRES", "ENTRETIEN_SALUBRITE", "FONCTIONNEMENT", "INVESTISSEMENT");
    private static final Map<String, String> LIBELLES_GROUPES_ECONOME = Map.of(
        "FOURNITURES_SCOLAIRES", "Fournitures scolaires",
        "ENTRETIEN_SALUBRITE", "Produit, materiel d'entretien et salubrite",
        "FONCTIONNEMENT", "Fonctionnement",
        "INVESTISSEMENT", "Investissement");

    private void buildRapportEconome(Model model, Long etabId, List<Paiement> tousPaiements, List<Depense> toutesDepenses, int trimestreChoisi) {
        LocalDate debut, fin;
        switch (trimestreChoisi) {
            case 2: debut = lireParametreDate(etabId, "T2_DEBUT"); fin = lireParametreDate(etabId, "T2_FIN"); break;
            case 3: debut = lireParametreDate(etabId, "T3_DEBUT"); fin = lireParametreDate(etabId, "T3_FIN"); break;
            default: debut = lireParametreDate(etabId, "T1_DEBUT"); fin = lireParametreDate(etabId, "T1_FIN");
        }
        model.addAttribute("econTrimestre", trimestreChoisi);
        model.addAttribute("econDebut", debut);
        model.addAttribute("econFin", fin);
        if (debut == null || fin == null || fin.isBefore(debut)) {
            model.addAttribute("econSectionsGroupes", List.of());
            model.addAttribute("econMoisCaisse", List.of());
            return;
        }

        List<Depense> depensesPeriode = toutesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()) && d.getDateDepense() != null
                && !d.getDateDepense().isBefore(debut) && !d.getDateDepense().isAfter(fin))
            .collect(Collectors.toList());

        Map<String, List<Depense>> parGroupe = depensesPeriode.stream()
            .collect(Collectors.groupingBy(d -> {
                String g = d.getCategorieComptable() != null ? d.getCategorieComptable().getGroupe() : null;
                return g != null && !g.isBlank() ? g : "FONCTIONNEMENT";
            }, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> sectionsGroupes = new ArrayList<>();
        double totalToutesDepenses = 0;
        for (String code : ORDRE_GROUPES_ECONOME) {
            List<Depense> lignes = parGroupe.getOrDefault(code, List.of());
            double sousTotal = lignes.stream().mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
            totalToutesDepenses += sousTotal;
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("code", code);
            section.put("libelle", LIBELLES_GROUPES_ECONOME.get(code));
            section.put("lignes", lignes);
            section.put("sousTotal", sousTotal);
            sectionsGroupes.add(section);
        }
        model.addAttribute("econSectionsGroupes", sectionsGroupes);
        model.addAttribute("econTotalDepenses", totalToutesDepenses);

        double soldeInitial = lireParametreDouble(etabId, "SOLDE_INITIAL_CAISSE", 0.0);
        List<Map<String, Object>> moisCaisse = new ArrayList<>();
        YearMonth curseur = YearMonth.from(debut);
        YearMonth dernierMois = YearMonth.from(fin);
        double totalEntreesTrimestre = 0;
        Map<String, Object> dernierePeriode = null;
        while (!curseur.isAfter(dernierMois)) {
            LocalDate debutMois = curseur.atDay(1).isBefore(debut) ? debut : curseur.atDay(1);
            LocalDate finMois = curseur.atEndOfMonth().isAfter(fin) ? fin : curseur.atEndOfMonth();
            Map<String, Object> periode = calculerJournalPeriode(tousPaiements, toutesDepenses, soldeInitial, debutMois, finMois);
            Map<String, Object> moisRow = new LinkedHashMap<>();
            moisRow.put("nomMois", capitaliser(curseur.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH)) + " " + curseur.getYear());
            moisRow.put("lignes", periode.get("lignes"));
            moisRow.put("totalEntrees", periode.get("totalEntrees"));
            moisRow.put("totalSorties", periode.get("totalSorties"));
            moisRow.put("soldeFinal", periode.get("soldeFinal"));
            moisCaisse.add(moisRow);
            totalEntreesTrimestre += (double) periode.get("totalEntrees");
            dernierePeriode = periode;
            curseur = curseur.plusMonths(1);
        }
        double soldeReporte = dernierePeriode != null ? (double) dernierePeriode.get("soldeFinal") : soldeInitial;

        model.addAttribute("econMoisCaisse", moisCaisse);
        model.addAttribute("econTotalEntrees", totalEntreesTrimestre);
        model.addAttribute("econSoldeReporte", soldeReporte);
    }

    private String capitaliser(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @PostMapping("/categories/{id}/groupe")
    public String modifierGroupeCategorie(@PathVariable Long id, @RequestParam String groupe, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        categorieComptableRepository.findById(id)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .ifPresent(c -> { c.setGroupe(groupe); categorieComptableRepository.save(c); });
        ra.addFlashAttribute("successMsg", "Groupe mis a jour.");
        return "redirect:/finances?tab=rapports";
    }

    // ===== PLAN COMPTABLE : creation/modification/desactivation libres, import CSV, modele SYSCOHADA =====

    @PostMapping("/categories")
    public String ajouterCategorie(@RequestParam String code, @RequestParam String libelle,
                                    @RequestParam String sens, @RequestParam(required = false) String groupe,
                                    RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String codeTrim = code != null ? code.trim() : "";
        if (codeTrim.isBlank() || libelle == null || libelle.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "Code et libelle sont obligatoires.");
            return "redirect:/finances?tab=parametrage";
        }
        if (categorieComptableRepository.findByCodeAndEtablissementId(codeTrim, etabId).isPresent()) {
            ra.addFlashAttribute("erreurMsg", "Le code \"" + codeTrim + "\" existe deja.");
            return "redirect:/finances?tab=parametrage";
        }
        CategorieComptable c = new CategorieComptable();
        c.setCode(codeTrim);
        c.setLibelle(libelle.trim());
        c.setSens(sens);
        c.setGroupe(groupe != null && !groupe.isBlank() ? groupe : null);
        c.setActif(true);
        c.setEtablissementId(etabId);
        categorieComptableRepository.save(c);
        ra.addFlashAttribute("successMsg", "Categorie \"" + codeTrim + " — " + libelle.trim() + "\" creee.");
        return "redirect:/finances?tab=parametrage";
    }

    @PostMapping("/categories/{id}/modifier")
    public String modifierCategorie(@PathVariable Long id, @RequestParam String code, @RequestParam String libelle,
                                     @RequestParam String sens, @RequestParam(required = false) String groupe,
                                     RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        CategorieComptable c = categorieComptableRepository.findById(id)
            .filter(x -> etabId != null && etabId.equals(x.getEtablissementId())).orElse(null);
        if (c == null) {
            ra.addFlashAttribute("erreurMsg", "Categorie introuvable.");
            return "redirect:/finances?tab=parametrage";
        }
        String codeTrim = code != null ? code.trim() : "";
        if (codeTrim.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "Le code est obligatoire.");
            return "redirect:/finances?tab=parametrage";
        }
        boolean codeDejaPris = categorieComptableRepository.findByCodeAndEtablissementId(codeTrim, etabId)
            .filter(autre -> !autre.getId().equals(id)).isPresent();
        if (codeDejaPris) {
            ra.addFlashAttribute("erreurMsg", "Le code \"" + codeTrim + "\" est deja utilise par une autre categorie.");
            return "redirect:/finances?tab=parametrage";
        }
        c.setCode(codeTrim);
        c.setLibelle(libelle.trim());
        c.setSens(sens);
        c.setGroupe(groupe != null && !groupe.isBlank() ? groupe : null);
        categorieComptableRepository.save(c);
        ra.addFlashAttribute("successMsg", "Categorie mise a jour.");
        return "redirect:/finances?tab=parametrage";
    }

    @PostMapping("/categories/{id}/toggle-actif")
    public String toggleActifCategorie(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        categorieComptableRepository.findById(id)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .ifPresent(c -> {
                c.setActif(!c.isActif());
                categorieComptableRepository.save(c);
                ra.addFlashAttribute("successMsg", c.isActif()
                    ? "Categorie reactivee." : "Categorie desactivee (elle disparait des listes de saisie, mais l'historique reste intact).");
            });
        return "redirect:/finances?tab=parametrage";
    }

    /** Import CSV du plan comptable : une ligne "code;libelle;sens;groupe" (ou avec des virgules —
        les deux separateurs sont acceptes). Le "sens" et le "groupe" sont optionnels ; sans "sens"
        fourni, le code est classe CHARGE sauf s'il commence par 7 (convention SYSCOHADA : classe 7
        = produits). Une ligne d'en-tete ("code;libelle;...") est detectee et ignoree automatiquement.
        Les codes deja existants sont ignores (jamais ecrases) pour ne rien casser de l'historique. */
    @PostMapping("/categories/importer")
    public String importerCategoriesCsv(@RequestParam("fichier") org.springframework.web.multipart.MultipartFile fichier,
                                         RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (fichier == null || fichier.isEmpty()) {
            ra.addFlashAttribute("erreurMsg", "Choisissez un fichier CSV avant d'importer.");
            return "redirect:/finances?tab=parametrage";
        }
        List<holyflame.administration.service.PlanComptableService.Poste> aImporter = new ArrayList<>();
        int ignorees = 0;
        try (java.io.BufferedReader lecteur = new java.io.BufferedReader(
                new java.io.InputStreamReader(fichier.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String ligne;
            boolean premiereLigne = true;
            while ((ligne = lecteur.readLine()) != null) {
                if (ligne.isBlank()) continue;
                String[] p = ligne.split("[;,]", -1);
                if (premiereLigne) {
                    premiereLigne = false;
                    if (p.length > 0 && p[0].trim().equalsIgnoreCase("code")) continue; // ligne d'en-tete
                }
                if (p.length < 2 || p[0].trim().isBlank() || p[1].trim().isBlank()) { ignorees++; continue; }
                String code = p[0].trim();
                String libelle = p[1].trim();
                String sens = p.length > 2 && !p[2].trim().isBlank()
                    ? p[2].trim().toUpperCase()
                    : (code.startsWith("7") ? "PRODUIT" : "CHARGE");
                String groupe = p.length > 3 && !p[3].trim().isBlank() ? p[3].trim().toUpperCase() : null;
                aImporter.add(new holyflame.administration.service.PlanComptableService.Poste(code, libelle, sens, groupe));
            }
        } catch (java.io.IOException e) {
            ra.addFlashAttribute("erreurMsg", "Impossible de lire le fichier : " + e.getMessage());
            return "redirect:/finances?tab=parametrage";
        }
        int crees = planComptableService.importerPostes(etabId, aImporter);
        int misAJourOuIgnores = aImporter.size() - crees;
        ra.addFlashAttribute("successMsg", crees + " categorie(s) importee(s)"
            + (misAJourOuIgnores > 0 ? ", " + misAJourOuIgnores + " deja existante(s) ignoree(s)" : "")
            + (ignorees > 0 ? ", " + ignorees + " ligne(s) invalide(s) ignoree(s)" : "") + ".");
        return "redirect:/finances?tab=parametrage";
    }

    /** Applique le modele "SYSCOHADA simplifie — etablissement scolaire" (codes officiels a 3-4
        chiffres) : n'ajoute que les codes absents, ne touche jamais aux categories deja en place. */
    @PostMapping("/categories/modele-syscohada")
    public String appliquerModeleSyscohada(RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        int crees = planComptableService.importerPostes(etabId, planComptableService.postesModeleSyscohadaScolaire());
        ra.addFlashAttribute("successMsg", crees > 0
            ? crees + " poste(s) du modele SYSCOHADA simplifie ajoute(s)."
            : "Tous les postes du modele SYSCOHADA simplifie existent deja dans votre plan comptable.");
        return "redirect:/finances?tab=parametrage";
    }

    private void buildResume(Model model, List<Paiement> paiements, List<LigneBudget> lignes,
                              List<Eleve> tousLesEleves, List<FraisScolarite> tousLesFrais, int moisCourant) {
        double totalEncaisse = paiements.stream().mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();

        double totalRevenuPrevu = lignes.stream().filter(LigneBudget::isRevenu).mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double totalDepensePrevu = lignes.stream().filter(l -> !l.isRevenu()).mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double totalRevenuReel = lignes.stream().filter(LigneBudget::isRevenu).mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();
        double totalDepenseReel = lignes.stream().filter(l -> !l.isRevenu()).mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();

        model.addAttribute("totalEncaisse", totalEncaisse);
        model.addAttribute("soldePrevu", totalRevenuPrevu - totalDepensePrevu);
        model.addAttribute("soldeReel", totalRevenuReel - totalDepenseReel);
        model.addAttribute("tauxEncaissement", totalRevenuPrevu > 0 ? totalEncaisse / totalRevenuPrevu * 100 : 0);

        double paiementsEnAttente = Math.max(totalRevenuPrevu - totalEncaisse, 0);
        model.addAttribute("paiementsEnAttente", paiementsEnAttente);

        double depensesDuMois = lignes.stream()
            .filter(l -> !l.isRevenu() && moisCourant == (l.getMois() != null ? l.getMois() : -1))
            .mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();
        model.addAttribute("depensesDuMois", depensesDuMois);

        Map<Integer, double[]> parMois = new LinkedHashMap<>();
        for (LigneBudget l : lignes) {
            if (l.getMois() == null) continue;
            double[] paire = parMois.computeIfAbsent(l.getMois(), m -> new double[2]);
            double montant = l.getMontantReel() != null ? l.getMontantReel() : 0;
            if (l.isRevenu()) paire[0] += montant; else paire[1] += montant;
        }
        List<Map<String, Object>> evolution = new ArrayList<>();
        String[] nomsMois = {"Jan","Fev","Mar","Avr","Mai","Juin","Juil","Aout","Sep","Oct","Nov","Dec"};
        parMois.keySet().stream().sorted().forEach(m -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("mois", nomsMois[m - 1]);
            point.put("revenu", parMois.get(m)[0]);
            point.put("depense", parMois.get(m)[1]);
            evolution.add(point);
        });
        model.addAttribute("evolution", evolution);
        double maxEvolution = evolution.stream()
            .flatMapToDouble(p -> java.util.stream.DoubleStream.of((double) p.get("revenu"), (double) p.get("depense")))
            .max().orElse(1);
        model.addAttribute("maxEvolution", maxEvolution > 0 ? maxEvolution : 1);

        List<Paiement> dernieresTransactions = paiements.stream()
            .sorted(Comparator.comparing(Paiement::getDatePaiement, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(8)
            .collect(Collectors.toList());
        model.addAttribute("dernieresTransactions", dernieresTransactions);

        List<FraisScolarite> fraisObligatoires = tousLesFrais.stream().filter(FraisScolarite::isObligatoire).collect(Collectors.toList());
        Map<Long, Double> paiementsParEleve = paiements.stream()
            .filter(p -> p.getEleve() != null)
            .collect(Collectors.groupingBy(p -> p.getEleve().getId(),
                Collectors.summingDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)));

        // Le du tient compte des remises : un boursier exonere est compte comme a jour, pas
        // comme impaye permanent. Sans cela le tableau devient faux des le premier mois, et
        // un tableau faux cesse d'etre consulte.
        String anneeCourante = etablissementService.getAnneeScolaireActive();
        int nbPayes = 0, nbEnAttente = 0, nbExoneres = 0;
        for (Eleve e : tousLesEleves) {
            List<RemiseEleve> remises = remiseEleveRepository
                .findByEleveIdAndAnneeScolaire(e.getId(), anneeCourante);
            double du = situationFinanciereService.montantBrut(e, fraisObligatoires)
                - situationFinanciereService.totalRemises(e, fraisObligatoires, remises);
            double verse = paiementsParEleve.getOrDefault(e.getId(), 0.0);
            if (du <= 0 && !remises.isEmpty()) nbExoneres++;
            if (du <= 0 || verse >= du) nbPayes++; else nbEnAttente++;
        }
        int totalEleves = nbPayes + nbEnAttente;
        model.addAttribute("nbElevesPayes", nbPayes);
        model.addAttribute("nbElevesEnAttente", nbEnAttente);
        model.addAttribute("nbElevesExoneres", nbExoneres);
        model.addAttribute("tauxScolaritesPayees", totalEleves > 0 ? Math.round(nbPayes * 1000.0 / totalEleves) / 10.0 : 0);
    }

    // ===== ONGLET JOURNAL DE CAISSE =====
    private void buildJournal(Model model, Long etabId, List<Paiement> tousPaiements, List<Depense> toutesDepenses,
                               int moisFiltre, int anneeFiltre) {
        LocalDate debutMois = LocalDate.of(anneeFiltre, moisFiltre, 1);
        LocalDate finMois = debutMois.withDayOfMonth(debutMois.lengthOfMonth());

        double soldeInitial = lireParametreDouble(etabId, "SOLDE_INITIAL_CAISSE", 0.0);
        Map<String, Object> periode = calculerJournalPeriode(tousPaiements, toutesDepenses, soldeInitial, debutMois, finMois);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lignesDuMois = ((List<Map<String, Object>>) periode.get("lignes")).stream()
            .sorted(Comparator.comparing((Map<String, Object> l) -> (LocalDate) l.get("date")).reversed())
            .collect(Collectors.toList());

        model.addAttribute("journalLignes", lignesDuMois);
        model.addAttribute("journalSolde", periode.get("soldeFinal"));
        model.addAttribute("journalTotalEntrees", periode.get("totalEntrees"));
        model.addAttribute("journalTotalSorties", periode.get("totalSorties"));
        model.addAttribute("journalMoisFiltre", moisFiltre);
        model.addAttribute("journalAnneeFiltre", anneeFiltre);
        model.addAttribute("soldeInitialCaisse", soldeInitial);
    }

    /** Calcule les ecritures de caisse (paiements + depenses) sur [debut, fin], avec solde qui roule
        depuis soldeInitial en tenant compte de TOUT l'historique anterieur a fin (pas seulement la periode). */
    private Map<String, Object> calculerJournalPeriode(List<Paiement> tousPaiements, List<Depense> toutesDepenses,
                                                          double soldeInitial, LocalDate debut, LocalDate fin) {
        List<Map<String, Object>> lignesAvant = new ArrayList<>();
        for (Paiement p : tousPaiements) {
            if (p.getDatePaiement() == null || p.getDatePaiement().toLocalDate().isAfter(fin)) continue;
            lignesAvant.add(ligneJournal(p.getDatePaiement().toLocalDate(), "ENTREE", p.getTypePaiement(), p.getDescription(), p.getMontantVerse(), "PAIEMENT", p.getId()));
        }
        for (Depense d : toutesDepenses) {
            if (d.getDateDepense() == null || d.getDateDepense().isAfter(fin)) continue;
            String sens = "PRODUIT".equals(d.getSens()) ? "ENTREE" : "SORTIE";
            String libelle = d.getCategorieComptable() != null ? d.getCategorieComptable().getLibelle() : "Non classe";
            lignesAvant.add(ligneJournal(d.getDateDepense(), sens, libelle, d.getDesignation(), d.getMontant(), "DEPENSE", d.getId()));
        }
        lignesAvant.sort(Comparator.comparing(l -> (LocalDate) l.get("date")));

        double solde = soldeInitial;
        for (Map<String, Object> l : lignesAvant) {
            double montant = (double) l.get("montant");
            solde += "ENTREE".equals(l.get("sens")) ? montant : -montant;
            l.put("soldeApres", solde);
        }

        List<Map<String, Object>> lignesPeriode = lignesAvant.stream()
            .filter(l -> !((LocalDate) l.get("date")).isBefore(debut))
            .sorted(Comparator.comparing(l -> (LocalDate) l.get("date")))
            .collect(Collectors.toList());

        double totalEntrees = lignesPeriode.stream().filter(l -> "ENTREE".equals(l.get("sens"))).mapToDouble(l -> (double) l.get("montant")).sum();
        double totalSorties = lignesPeriode.stream().filter(l -> "SORTIE".equals(l.get("sens"))).mapToDouble(l -> (double) l.get("montant")).sum();

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("lignes", lignesPeriode);
        resultat.put("soldeFinal", solde);
        resultat.put("totalEntrees", totalEntrees);
        resultat.put("totalSorties", totalSorties);
        return resultat;
    }

    private Map<String, Object> ligneJournal(LocalDate date, String sens, String libelle, String detail, Double montant, String source, Long refId) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("date", date);
        l.put("sens", sens);
        l.put("libelle", libelle != null ? libelle : "—");
        l.put("detail", detail);
        l.put("montant", montant != null ? montant : 0.0);
        l.put("source", source);
        l.put("refId", refId);
        return l;
    }

    private double lireParametreDouble(Long etabId, String cle, double defaut) {
        return parametreRepository.findByCleAndEtablissementId(cle, etabId)
            .map(Parametre::getValeur)
            .map(v -> { try { return Double.parseDouble(v); } catch (NumberFormatException e) { return defaut; } })
            .orElse(defaut);
    }

    @PostMapping("/parametres/solde-initial")
    public String soldeInitial(@RequestParam Double montant, @RequestParam(defaultValue = "journal") String tab,
                                RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Parametre p = parametreRepository.findByCleAndEtablissementId("SOLDE_INITIAL_CAISSE", etabId).orElseGet(Parametre::new);
        p.setCle("SOLDE_INITIAL_CAISSE"); p.setValeur(String.valueOf(montant));
        p.setDescription("Solde de caisse au debut de l'annee scolaire"); p.setCategorie("FINANCES");
        p.setEtablissementId(etabId);
        parametreRepository.save(p);
        ra.addFlashAttribute("successMsg", "Solde initial de caisse mis a jour.");
        return "redirect:/finances?tab=" + tab;
    }

    // ===== ONGLET PARAMETRAGE — taux de paie par defaut, modifiables par le tresorier lui-meme
    // (auparavant fige a la creation de l'etablissement, sans aucune page pour les corriger) =====
    private static final List<String> CLES_TAUX_PAIE = List.of(
        "TAUX_CNPS_EMPLOYE", "TAUX_IRPP", "TAUX_TAXE_APPRENTISSAGE",
        "TAUX_TAXE_FORFAITAIRE", "TAUX_CNPS_ACCIDENT_TRAVAIL", "TAUX_CNPS_ALLOCATIONS_FAMILIALES",
        "TAUX_CNPS_PENSION_VIEILLESSE");

    @PostMapping("/parametres/taux-paie")
    public String tauxPaie(@RequestParam Map<String, String> formParams, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        for (String cle : CLES_TAUX_PAIE) {
            String valeur = formParams.get(cle);
            if (valeur == null || valeur.isBlank()) continue;
            Parametre p = parametreRepository.findByCleAndEtablissementId(cle, etabId).orElseGet(Parametre::new);
            p.setCle(cle); p.setValeur(valeur.trim());
            p.setCategorie("PAIE"); p.setEtablissementId(etabId);
            if (p.getDescription() == null) p.setDescription(cle);
            parametreRepository.save(p);
        }
        ra.addFlashAttribute("successMsg", "Taux de paie par defaut mis a jour. Ils pre-rempliront desormais chaque nouveau bulletin.");
        return "redirect:/finances?tab=parametrage";
    }

    @PostMapping("/parametrage/clotures/cloturer")
    public String cloturerMois(@RequestParam int mois, @RequestParam int anneeCivile, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur moi = etablissementService.getCurrentUtilisateur();
        clotureMensuelleService.cloturer(mois, anneeCivile, etabId, moi != null ? moi.getId() : null);
        ra.addFlashAttribute("successMsg", "Mois " + mois + "/" + anneeCivile + " cloture : aucune depense de cette periode ne pourra plus etre modifiee.");
        return "redirect:/finances?tab=parametrage";
    }

    @PostMapping("/parametrage/clotures/reouvrir")
    public String reouvrirMois(@RequestParam int mois, @RequestParam int anneeCivile, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        clotureMensuelleService.reouvrir(mois, anneeCivile, etabId);
        ra.addFlashAttribute("successMsg", "Mois " + mois + "/" + anneeCivile + " reouvert.");
        return "redirect:/finances?tab=parametrage";
    }

    // ===== ONGLET SCOLARITE & ARRIERES =====
    private void buildScolarite(Model model, Long etabId, List<Eleve> tousLesEleves) {
        FinanceParentService.ResumeSolde resume = financeParentService.calculerResume(tousLesEleves, etabId);

        Map<Long, List<Map<String, Object>>> lignesParEleve = resume.lignes.stream()
            .collect(Collectors.groupingBy(l -> (Long) l.get("eleveId"), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> parEleve = new ArrayList<>();
        for (Eleve e : tousLesEleves) {
            // Seuls les frais obligatoires comptent pour le solde/statut d'un eleve : un frais
            // facultatif (ex. cantine optionnelle) reste visible et payable, mais ne doit jamais
            // empecher l'eleve d'apparaitre "a jour" tant que ses frais obligatoires sont soldes.
            List<Map<String, Object>> lignes = lignesParEleve.getOrDefault(e.getId(), List.of()).stream()
                .filter(l -> Boolean.TRUE.equals(l.get("obligatoire")))
                .toList();
            double du = lignes.stream().mapToDouble(l -> (double) l.get("montant")).sum();
            double reste = lignes.stream().mapToDouble(l -> (double) l.get("resteAPayer")).sum();
            boolean enRetard = lignes.stream().anyMatch(l -> "EN_RETARD".equals(l.get("statut")));

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("eleve", e);
            ligne.put("classe", e.getClasse() != null ? e.getClasse().getNiveau() : "—");
            ligne.put("du", du);
            ligne.put("percu", du - reste);
            ligne.put("reste", reste);
            ligne.put("statut", reste <= 0 ? "A_JOUR" : (enRetard ? "EN_RETARD" : "EN_ATTENTE"));
            if (reste > 0) {
                String message = "Bonjour, nous vous contactons au sujet de la scolarite de " + e.getPrenom() + " " + e.getNom()
                    + ". Solde restant a regler : " + Math.round(reste) + " F. Merci de contacter l'ecole pour regulariser.";
                ligne.put("lienWhatsapp", WhatsAppUtil.lien(telephoneWhatsapp(e), message));
            }
            parEleve.add(ligne);
        }

        Map<String, List<Map<String, Object>>> parClasse = parEleve.stream()
            .collect(Collectors.groupingBy(l -> (String) l.get("classe"), LinkedHashMap::new, Collectors.toList()));

        model.addAttribute("scolariteParClasse", parClasse);
        model.addAttribute("scolariteParEleve", parEleve);

        List<ArriereEleve> arrieresEnCours = arriereEleveRepository.findByEtablissementIdOrderByAnneeScolaireOrigineDesc(etabId).stream()
            .filter(a -> (a.getMontant() != null ? a.getMontant() : 0) - (a.getMontantRegle() != null ? a.getMontantRegle() : 0) > 0)
            .collect(Collectors.toList());
        model.addAttribute("arrieresEnCours", arrieresEnCours);
    }

    @PostMapping("/arrieres")
    public String ajouterArriere(
            @RequestParam Long eleveId,
            @RequestParam String anneeScolaireOrigine,
            @RequestParam Double montant,
            @RequestParam(required = false) String notes,
            @RequestParam(defaultValue = "scolarite") String tab,
            RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        if (etabId == null || !etabId.equals(eleve.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Eleve introuvable dans cet établissement.");
        }
        ArriereEleve a = new ArriereEleve();
        a.setEleve(eleve); a.setAnneeScolaireOrigine(anneeScolaireOrigine);
        a.setMontant(montant); a.setNotes(notes);
        a.setDateEnregistrement(horlogeService.aujourdHui()); a.setEtablissementId(etabId);
        arriereEleveRepository.save(a);
        journalService.log("ARRIERE_AJOUTE", "FINANCES", eleve.getNom() + " " + eleve.getPrenom() + " — " + montant + " F (" + anneeScolaireOrigine + ")");
        ra.addFlashAttribute("successMsg", "Arriere enregistre pour " + eleve.getNom() + " " + eleve.getPrenom() + ".");
        return "redirect:/finances?tab=" + tab;
    }

    @PostMapping("/arrieres/{id}/reglement")
    public String reglerArriere(@PathVariable Long id, @RequestParam Double montantRegle, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        arriereEleveRepository.findById(id)
            .filter(a -> etabId != null && etabId.equals(a.getEtablissementId()))
            .ifPresent(a -> {
                double actuel = a.getMontantRegle() != null ? a.getMontantRegle() : 0;
                a.setMontantRegle(actuel + montantRegle);
                arriereEleveRepository.save(a);
            });
        ra.addFlashAttribute("successMsg", "Reglement d'arriere enregistre.");
        return "redirect:/finances?tab=scolarite";
    }

    @PostMapping("/rappels")
    public String envoyerRappels(RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String anneeActive = etablissementService.getAnneeScolaireActive();
        // Uniquement les eleves de l'annee active : un eleve parti/diplome les annees precedentes
        // n'a pas a recevoir de rappel de paiement pour l'annee en cours.
        List<Eleve> elevesAnneeActive = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(e -> e.getClasse() == null || anneeActive.equals(e.getClasse().getAnneeScolaire()))
            .collect(Collectors.toList());
        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId);
        List<FraisScolarite> fraisObligatoires = fraisScolariteRepository
            .findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId).stream()
            .filter(FraisScolarite::isObligatoire)
            .collect(Collectors.toList());
        Map<Long, Double> paiementsParEleve = paiements.stream()
            .filter(p -> p.getEleve() != null)
            .collect(Collectors.groupingBy(p -> p.getEleve().getId(),
                Collectors.summingDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)));

        int nbRappels = 0;
        for (Eleve e : elevesAnneeActive) {
            // Le du passe par SituationFinanciereService pour que les remises soient deduites :
            // relancer un boursier exonere serait une faute vis-a-vis de la famille.
            double du = situationFinanciereService.montantBrut(e, fraisObligatoires)
                - situationFinanciereService.totalRemises(e, fraisObligatoires,
                    remiseEleveRepository.findByEleveIdAndAnneeScolaire(e.getId(), anneeActive));
            double verse = paiementsParEleve.getOrDefault(e.getId(), 0.0);
            if (du > 0 && verse < du) {
                e.setDernierRappelPaiement(horlogeService.maintenant());
                eleveRepository.save(e);
                nbRappels++;
            }
        }
        journalService.log("RAPPELS_PAIEMENT_ENVOYÉS", "FINANCES", nbRappels + " eleve(s) marque(s) comme rappele(s).");
        ra.addFlashAttribute("successMsg",
            "Rappel enregistre pour " + nbRappels + " eleve(s) en attente de paiement. "
            + "(Aucun email n'est envoye : la configuration SMTP n'est pas encore en place.)");
        return "redirect:/finances?tab=scolarite";
    }

    // ===== PAIEMENTS =====
    @PostMapping("/paiements")
    public String enregistrerPaiement(
            @RequestParam Long eleveId,
            @RequestParam Double montantVerse,
            @RequestParam String typePaiement,
            @RequestParam String modePaiement,
            @RequestParam(required = false) String recuNumero,
            @RequestParam(required = false) String fraisScolariteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePaiement,
            @RequestParam(required = false) String description,
            RedirectAttributes ra) {

        Long etabIdCourant = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        if (etabIdCourant == null || !etabIdCourant.equals(eleve.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Élève introuvable dans cet établissement.");
        }
        if (montantVerse == null || montantVerse <= 0) {
            ra.addFlashAttribute("erreur", "Le montant versé doit être supérieur à 0.");
            return "redirect:/finances?tab=journal";
        }
        LocalDate dateEffective = datePaiement != null ? datePaiement : horlogeService.aujourdHui();
        String anneeScolairePaiement = AnneeScolaireUtil.pour(dateEffective);
        anneeScolaireService.verifierModifiable(anneeScolairePaiement, etabIdCourant);
        Paiement p = new Paiement();
        p.setEleve(eleve); p.setMontantVerse(montantVerse);
        p.setTypePaiement(typePaiement); p.setModePaiement(modePaiement);
        p.setDatePaiement(dateEffective.atStartOfDay());
        // L'annee scolaire du paiement suit celle de la classe actuelle de l'eleve (a quelle
        // scolarite ce versement se rattache), pas seulement la date calendaire du jour : une
        // ecole peut deja avoir avance ses classes sur l'annee suivante avant le bascule de
        // septembre (ex: inscriptions de rentree en aout).
        p.setAnneeScolaire(eleve.getClasse() != null && eleve.getClasse().getAnneeScolaire() != null
            ? eleve.getClasse().getAnneeScolaire() : anneeScolairePaiement);
        p.setDescription(description);
        p.setRecuNumero(recuNumero != null && !recuNumero.isBlank() ? recuNumero : prochainNumeroRecu(etabIdCourant, dateEffective));
        if (fraisScolariteId != null && !fraisScolariteId.isBlank()) {
            fraisScolariteRepository.findById(Long.parseLong(fraisScolariteId)).ifPresent(p::setFraisScolarite);
        }
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) p.setEnregistreParId(utilisateur.getId());
        paiementRepository.save(p);

        // Le versement solde les echeances en attente, de la plus ancienne a la plus recente :
        // une famille qui paie rattrape d'abord son retard. Sans cette imputation, l'echeancier
        // resterait a jamais impaye alors que l'argent est bien entre.
        situationFinanciereService.imputer(eleve.getId(),
            etablissementService.getAnneeScolaireActive(), montantVerse,
            p.getDatePaiement() != null ? p.getDatePaiement().toLocalDate() : LocalDate.now());

        journalService.log("PAIEMENT_ENREGISTRÉ", "FINANCES",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + montantVerse + " F (" + typePaiement + ")");

        // Controle "solde" : si le frais choisi est rattache a ce paiement, on verifie si le cumul
        // des versements atteint (ou depasse) le montant renseigne dans Parametres > Frais, et on
        // previent explicitement l'utilisateur — plutot que de le laisser deviner en comparant
        // lui-meme les montants.
        String messageSolde = null;
        if (p.getFraisScolarite() != null) {
            FraisScolarite frais = p.getFraisScolarite();
            double montantFrais = frais.getMontant() != null ? frais.getMontant() : 0;
            double totalVerse = paiementRepository.findByEleveId(eleveId).stream()
                .filter(pp -> pp.getFraisScolarite() != null && pp.getFraisScolarite().getId().equals(frais.getId()))
                .filter(pp -> p.getAnneeScolaire().equals(pp.getAnneeScolaire()))
                .mapToDouble(pp -> pp.getMontantVerse() != null ? pp.getMontantVerse() : 0)
                .sum();
            if (montantFrais > 0 && totalVerse >= montantFrais) {
                double excedent = totalVerse - montantFrais;
                messageSolde = "\"" + frais.getDesignation() + "\" est desormais SOLDE pour " + eleve.getPrenom() + " " + eleve.getNom()
                    + (excedent > 0
                        ? " (excedent de " + Math.round(excedent) + " F au-dela du montant renseigne dans Parametres)."
                        : ".");
            }
        }
        if (messageSolde != null) ra.addFlashAttribute("soldeInfoMsg", messageSolde);

        boolean envoye = envoyerRecuParEmail(p);
        String emailDestinataire = adresseParent(eleve);
        if (envoye) {
            ra.addFlashAttribute("successMsg", "Paiement enregistre. Une copie du recu a ete envoyee par email a " + emailDestinataire + ".");
        } else if (emailDestinataire != null) {
            ra.addFlashAttribute("erreurEmail", "Paiement enregistré, mais le service d'envoi d'email n'est pas configuré sur ce serveur (ou l'envoi a échoué).");
        }
        return "redirect:/finances/paiements/" + p.getId() + "/recu";
    }

    /** Solde en direct d'un frais precis pour un eleve (utilise par l'auto-completion du formulaire de paiement). */
    @GetMapping("/frais-solde")
    @ResponseBody
    public Map<String, Object> fraisSolde(@RequestParam Long eleveId, @RequestParam Long fraisId) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
        FraisScolarite frais = fraisScolariteRepository.findById(fraisId).orElse(null);
        if (eleve == null || frais == null || etabId == null
                || !etabId.equals(eleve.getEtablissementId()) || !etabId.equals(frais.getEtablissementId())) {
            resultat.put("erreur", "Eleve ou frais introuvable.");
            return resultat;
        }
        String anneeEleve = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : etablissementService.getAnneeScolaireActive();
        double montantFrais = frais.getMontant() != null ? frais.getMontant() : 0;
        double dejaVerse = paiementRepository.findByEleveId(eleveId).stream()
            .filter(p -> p.getFraisScolarite() != null && p.getFraisScolarite().getId().equals(fraisId))
            .filter(p -> anneeEleve == null || p.getAnneeScolaire() == null || anneeEleve.equals(p.getAnneeScolaire()))
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)
            .sum();
        resultat.put("designation", frais.getDesignation());
        resultat.put("montantFrais", montantFrais);
        resultat.put("dejaVerse", dejaVerse);
        resultat.put("reste", Math.max(0, montantFrais - dejaVerse));
        return resultat;
    }

    private String prochainNumeroRecu(Long etabId, LocalDate date) {
        String anneeScolaire = AnneeScolaireUtil.pour(date);
        long compte = paiementRepository.findByEtablissementId(etabId).stream()
            .filter(p -> p.getDatePaiement() != null && anneeScolaire.equals(AnneeScolaireUtil.pour(p.getDatePaiement().toLocalDate())))
            .count();
        return "HF-" + anneeScolaire + "-" + String.format("%03d", compte + 1);
    }

    @GetMapping("/paiements/{id}/recu")
    public String recu(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Paiement p = paiementRepository.findById(id).orElse(null);
        if (p == null || p.getEleve() == null || !etabId.equals(p.getEleve().getEtablissementId())) {
            ra.addFlashAttribute("erreur", "Paiement introuvable.");
            return "redirect:/finances?tab=journal";
        }

        Map<String, String> params = parametreRepository.findByEtablissementId(etabId).stream()
            .collect(Collectors.toMap(
                Parametre::getCle,
                Parametre::getValeur,
                (a, b) -> a));

        holyflame.administration.model.Etablissement etab = etablissementService.getCurrentEtablissement();
        String monnaie = params.getOrDefault("MONNAIE", "FCFA");
        long montantEntier = p.getMontantVerse() != null ? Math.round(p.getMontantVerse()) : 0;

        model.addAttribute("paiement",   p);
        model.addAttribute("nomEtab",    etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame");
        model.addAttribute("adresseEtab",etab != null && etab.getAdresse() != null ? etab.getAdresse() : "");
        model.addAttribute("telEtab",    params.getOrDefault("TELEPHONE_ECOLE", ""));
        model.addAttribute("anneeScolaire", p.getAnneeScolaire() != null ? p.getAnneeScolaire() : etablissementService.getAnneeScolaireActive());
        model.addAttribute("logoPath",   params.getOrDefault("LOGO_ETAB", null));
        model.addAttribute("devise",     params.getOrDefault("DEVISE", ""));
        model.addAttribute("emailEtab",  params.getOrDefault("EMAIL_ECOLE", ""));
        model.addAttribute("monnaie",    monnaie);
        model.addAttribute("montantEnLettres", nombreEnLettresService.convertir(montantEntier, monnaie));
        model.addAttribute("modePaiementLabel", libelleModePaiement(p.getModePaiement()));
        model.addAttribute("chefEtablissement", params.get("CONTACT_PRINCIPAL"));
        model.addAttribute("enregistrePar", p.getEnregistreParId() != null
            ? utilisateurRepository.findById(p.getEnregistreParId()).orElse(null) : null);
        model.addAttribute("emailDestinataire", adresseParent(p.getEleve()));
        return "recu-paiement";
    }

    @PostMapping("/paiements/{id}/renvoyer-email")
    public String renvoyerEmail(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Paiement p = paiementRepository.findById(id).orElse(null);
        if (p == null || p.getEleve() == null || !etabId.equals(p.getEleve().getEtablissementId())) {
            ra.addFlashAttribute("erreur", "Paiement introuvable.");
            return "redirect:/finances?tab=journal";
        }
        String emailDestinataire = adresseParent(p.getEleve());
        if (emailDestinataire == null || emailDestinataire.isBlank()) {
            ra.addFlashAttribute("erreurEmail",
                "Impossible d'envoyer le reçu : aucune adresse email n'est enregistrée pour le père, la mère ou le tuteur de cet élève. Ajoutez-en une depuis sa fiche au Secrétariat.");
            return "redirect:/finances/paiements/" + id + "/recu";
        }
        boolean envoye = envoyerRecuParEmail(p);
        if (envoye) {
            ra.addFlashAttribute("successMsg", "Recu renvoye par email a " + emailDestinataire + ".");
        } else {
            ra.addFlashAttribute("erreurEmail",
                "L'adresse email (" + emailDestinataire + ") est enregistrée, mais le service d'envoi d'email n'est pas configuré sur ce serveur (ou l'envoi a échoué). Contactez l'administrateur technique.");
        }
        return "redirect:/finances/paiements/" + id + "/recu";
    }

    private String adresseParent(Eleve eleve) {
        if (eleve.getPereEmail() != null && !eleve.getPereEmail().isBlank()) return eleve.getPereEmail();
        if (eleve.getMereEmail() != null && !eleve.getMereEmail().isBlank()) return eleve.getMereEmail();
        return eleve.getEmailParent();
    }

    private String telephoneWhatsapp(Eleve eleve) {
        if (eleve.getPereTelephone() != null && !eleve.getPereTelephone().isBlank()) return eleve.getPereTelephone();
        if (eleve.getMereTelephone() != null && !eleve.getMereTelephone().isBlank()) return eleve.getMereTelephone();
        return eleve.getTelephoneParent();
    }

    private boolean envoyerRecuParEmail(Paiement p) {
        String destinataire = adresseParent(p.getEleve());
        if (destinataire == null || destinataire.isBlank()) return false;
        String corps = "<p>Bonjour,</p>"
            + "<p>Nous confirmons la reception d'un paiement pour <strong>" + p.getEleve().getPrenom() + " " + p.getEleve().getNom() + "</strong>.</p>"
            + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Reçu n°</td><td><strong>" + p.getRecuNumero() + "</strong></td></tr>"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Type</td><td>" + (p.getFraisScolarite() != null ? p.getFraisScolarite().getDesignation() : p.getTypePaiement()) + "</td></tr>"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Date</td><td>" + p.getDatePaiement().toLocalDate() + "</td></tr>"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Montant versé</td><td><strong>" + Math.round(p.getMontantVerse()) + " F</strong></td></tr>"
            + "</table>"
            + "<p>Merci de conserver cet email comme justificatif. Pour toute question, contactez le secrétariat.</p>";
        return emailService.envoyer(destinataire, "Reçu de paiement " + p.getRecuNumero(), corps);
    }

    private String libelleModePaiement(String mode) {
        if (mode == null) return "--";
        return switch (mode) {
            case "ESPECES" -> "Espèces";
            case "VIREMENT" -> "Virement bancaire";
            case "MOBILE_MONEY" -> "Mobile Money";
            case "CHEQUE" -> "Chèque";
            default -> mode;
        };
    }

    @PostMapping("/paiements/{id}/supprimer")
    public String supprimerPaiement(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        paiementRepository.findById(id)
            .filter(p -> p.getEleve() != null && etabId != null && etabId.equals(p.getEleve().getEtablissementId()))
            .ifPresent(p -> {
                anneeScolaireService.verifierModifiable(p.getAnneeScolaire(), etabId);
                journalService.log("PAIEMENT_SUPPRIMÉ", "FINANCES",
                    "Reçu " + (p.getRecuNumero() != null ? p.getRecuNumero() : id));
                paiementRepository.deleteById(id);
            });
        return "redirect:/finances?tab=journal";
    }

    // ===== ANCIENNES PAGES => REDIRECTIONS VERS LES ONGLETS =====
    @GetMapping("/budget")
    public String budgetPage(@RequestParam(required = false) String annee) {
        return "redirect:/finances?tab=budget&annee=" + (annee != null && !annee.isBlank() ? annee : etablissementService.getAnneeScolaireActive());
    }

    @GetMapping("/nouvelle-transaction")
    public String nouvelleTransaction() {
        return "redirect:/finances?tab=journal";
    }

    // ===== ONGLET DEPENSES =====
    /** Transforme une repartition {categorie -> montant} en tranches pretes pour un donut SVG (pourcentage + offset cumule). */
    private List<Map<String, Object>> calculerRepartition(Map<String, Double> parCategorie, double total) {
        List<Map<String, Object>> repartition = new ArrayList<>();
        double cumul = 0;
        List<Map.Entry<String, Double>> triees = parCategorie.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        for (Map.Entry<String, Double> e : triees) {
            double pourcentage = total > 0 ? Math.round(e.getValue() / total * 1000) / 10.0 : 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categorie", e.getKey());
            row.put("montant", e.getValue());
            row.put("pourcentage", pourcentage);
            row.put("offsetCumule", -cumul);
            repartition.add(row);
            cumul += pourcentage;
        }
        return repartition;
    }

    private void buildDepensesTab(Model model, Long etabId, List<Depense> toutesLesDepenses, List<LigneBudget> lignesAnneeCourante,
                                   int moisFiltre, int anneeFiltre, String categorieComptableId, String statutFiltre) {

        List<Depense> depensesDuMois = toutesLesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()))
            .filter(d -> d.getDateDepense() != null
                && d.getDateDepense().getMonthValue() == moisFiltre
                && d.getDateDepense().getYear() == anneeFiltre)
            .collect(Collectors.toList());

        List<Depense> depensesFiltrees = depensesDuMois.stream()
            .filter(d -> categorieComptableId == null || categorieComptableId.isBlank()
                || (d.getCategorieComptable() != null && categorieComptableId.equals(String.valueOf(d.getCategorieComptable().getId()))))
            .filter(d -> statutFiltre == null || statutFiltre.isBlank() || statutFiltre.equals(d.getStatut()))
            .collect(Collectors.toList());

        double totalDuMois = depensesDuMois.stream().mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        LocalDate moisPrecedentDate = LocalDate.of(anneeFiltre, moisFiltre, 1).minusMonths(1);
        double totalMoisPrecedent = toutesLesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()) && d.getDateDepense() != null
                && d.getDateDepense().getMonthValue() == moisPrecedentDate.getMonthValue()
                && d.getDateDepense().getYear() == moisPrecedentDate.getYear())
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        Map<String, Double> parCategorie = depensesDuMois.stream()
            .collect(Collectors.groupingBy(
                d -> d.getCategorieComptable() != null ? d.getCategorieComptable().getLibelle() : "Non classe",
                LinkedHashMap::new,
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));

        List<Map<String, Object>> repartition = calculerRepartition(parCategorie, totalDuMois);
        List<Map.Entry<String, Double>> triees = parCategorie.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());

        String categorieMajeure = triees.isEmpty() ? null : triees.get(0).getKey();
        double pourcentageCategorieMajeure = totalDuMois > 0 && !triees.isEmpty()
            ? Math.round(triees.get(0).getValue() / totalDuMois * 1000) / 10.0 : 0;

        Depense plusGrosseDepense = depensesDuMois.stream()
            .max(Comparator.comparing(d -> d.getMontant() != null ? d.getMontant() : 0))
            .orElse(null);

        List<LigneBudget> lignesDepense = lignesAnneeCourante.stream().filter(l -> !l.isRevenu()).collect(Collectors.toList());
        double budgetAnnuelPrevu = lignesDepense.stream().mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        String anneeScolaireCourante = moisFiltre >= 9 ? anneeFiltre + "-" + (anneeFiltre + 1) : (anneeFiltre - 1) + "-" + anneeFiltre;
        double depenseAnnuelleTotale = toutesLesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()) && anneeScolaireCourante.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double budgetRestant = budgetAnnuelPrevu - depenseAnnuelleTotale;
        double tauxBudgetUtilise = budgetAnnuelPrevu > 0 ? Math.round(depenseAnnuelleTotale / budgetAnnuelPrevu * 1000) / 10.0 : 0;

        model.addAttribute("nomsMois", List.of("Janvier","Fevrier","Mars","Avril","Mai","Juin",
            "Juillet","Aout","Septembre","Octobre","Novembre","Decembre"));
        model.addAttribute("depenses", depensesFiltrees);
        model.addAttribute("depMoisFiltre", moisFiltre);
        model.addAttribute("depAnneeFiltre", anneeFiltre);
        model.addAttribute("categorieComptableFiltre", categorieComptableId);
        model.addAttribute("statutFiltre", statutFiltre);
        model.addAttribute("totalDuMois", totalDuMois);
        model.addAttribute("variationVsMoisPrecedent", totalDuMois - totalMoisPrecedent);
        model.addAttribute("categorieMajeure", categorieMajeure);
        model.addAttribute("pourcentageCategorieMajeure", pourcentageCategorieMajeure);
        model.addAttribute("budgetRestant", budgetRestant);
        model.addAttribute("budgetAnnuelPrevu", budgetAnnuelPrevu);
        model.addAttribute("depenseAnnuelleTotale", depenseAnnuelleTotale);
        model.addAttribute("tauxBudgetUtiliseDep", tauxBudgetUtilise);
        model.addAttribute("repartitionDepenses", repartition);
        model.addAttribute("plusGrosseDepense", plusGrosseDepense);
        model.addAttribute("etablissement", etablissementService.getCurrentEtablissement());
    }

    // ===== ONGLET BUDGET =====
    private void buildBudgetTab(Model model, List<LigneBudget> lignes) {
        double totalRevenuPrevu = lignes.stream().filter(LigneBudget::isRevenu).mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double totalDepensePrevu = lignes.stream().filter(l -> !l.isRevenu()).mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double totalRevenuReel = lignes.stream().filter(LigneBudget::isRevenu).mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();
        double totalDepenseReel = lignes.stream().filter(l -> !l.isRevenu()).mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();

        model.addAttribute("lignes", lignes);
        model.addAttribute("budgetSoldePrevu", totalRevenuPrevu - totalDepensePrevu);
        model.addAttribute("budgetSoldeReel", totalRevenuReel - totalDepenseReel);
    }

    @PostMapping("/budget")
    public String ajouterBudget(
            @RequestParam String designation,
            @RequestParam Long categorieComptableId,
            @RequestParam Double montantPrevu,
            @RequestParam(required = false) Double montantReel,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) String anneeScolaire,
            @RequestParam(required = false) String notes) {

        Long etabIdBudget = etablissementService.getCurrentEtablissementId();
        if (anneeScolaire == null || anneeScolaire.isBlank()) anneeScolaire = etablissementService.getAnneeScolaireActive();
        anneeScolaireService.verifierModifiable(anneeScolaire, etabIdBudget);

        LigneBudget l = new LigneBudget();
        l.setDesignation(designation);
        categorieComptableRepository.findById(categorieComptableId).ifPresent(l::setCategorieComptable);
        l.setMontantPrevu(montantPrevu); l.setMontantReel(montantReel); l.setMois(mois);
        l.setAnneeScolaire(anneeScolaire); l.setNotes(notes); l.setDateCreation(horlogeService.aujourdHui());
        l.setEtablissementId(etabIdBudget);
        budgetRepository.save(l);
        return "redirect:/finances?tab=budget&annee=" + anneeScolaire;
    }

    @PostMapping("/budget/{id}/supprimer")
    public String supprimerBudget(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        LigneBudget ligne = budgetRepository.findById(id).orElse(null);
        String annee = ligne != null ? ligne.getAnneeScolaire() : etablissementService.getAnneeScolaireActive();
        if (ligne != null && etabId != null && etabId.equals(ligne.getEtablissementId())) {
            anneeScolaireService.verifierModifiable(ligne.getAnneeScolaire(), etabId);
            budgetRepository.deleteById(id);
        }
        return "redirect:/finances?tab=budget&annee=" + annee;
    }

    @PostMapping("/budget/{id}/realiser")
    public String realiserBudget(@PathVariable Long id, @RequestParam Double montantReel) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String annee = budgetRepository.findById(id)
            .filter(l -> etabId != null && etabId.equals(l.getEtablissementId()))
            .map(l -> {
                anneeScolaireService.verifierModifiable(l.getAnneeScolaire(), etabId);
                l.setMontantReel(montantReel); budgetRepository.save(l); return l.getAnneeScolaire();
            })
            .orElse(etablissementService.getAnneeScolaireActive());
        return "redirect:/finances?tab=budget&annee=" + annee;
    }

    // ===== ONGLET RAPPORTS =====
    private void buildRapports(Model model, Long etabId, List<Eleve> tousLesEleves, List<Depense> toutesDepenses,
                                List<Paiement> tousPaiements, String annee) {

        // 1. Caisse : solde theorique a ce jour + historique des comptages
        double soldeInitial = lireParametreDouble(etabId, "SOLDE_INITIAL_CAISSE", 0.0);
        double totalEntrees = tousPaiements.stream().mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        for (Depense d : toutesDepenses) {
            double m = d.getMontant() != null ? d.getMontant() : 0;
            if ("PRODUIT".equals(d.getSens())) totalEntrees += m;
        }
        double totalSorties = toutesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double soldeTheorique = soldeInitial + totalEntrees - totalSorties;

        List<ComptageCaisse> comptages = comptageCaisseRepository.findByEtablissementIdOrderByDateComptageDesc(etabId);

        model.addAttribute("rapportSoldeTheorique", soldeTheorique);
        model.addAttribute("comptagesCaisse", comptages);

        // 2. Par classe
        FinanceParentService.ResumeSolde resume = financeParentService.calculerResume(tousLesEleves, etabId);
        Map<Long, List<Map<String, Object>>> lignesParEleve = resume.lignes.stream()
            .collect(Collectors.groupingBy(l -> (Long) l.get("eleveId"), LinkedHashMap::new, Collectors.toList()));

        Map<String, double[]> parClasse = new LinkedHashMap<>(); // [nbEleves, du, percu, reste]
        for (Eleve e : tousLesEleves) {
            String niveau = e.getClasse() != null ? e.getClasse().getNiveau() : "—";
            List<Map<String, Object>> lignes = lignesParEleve.getOrDefault(e.getId(), List.of());
            double du = lignes.stream().mapToDouble(l -> (double) l.get("montant")).sum();
            double reste = lignes.stream().mapToDouble(l -> (double) l.get("resteAPayer")).sum();
            double[] acc = parClasse.computeIfAbsent(niveau, k -> new double[4]);
            acc[0] += 1; acc[1] += du; acc[2] += (du - reste); acc[3] += reste;
        }
        List<Map<String, Object>> rapportParClasse = new ArrayList<>();
        parClasse.forEach((niveau, acc) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("classe", niveau); row.put("nbEleves", (int) acc[0]);
            row.put("du", acc[1]); row.put("percu", acc[2]); row.put("reste", acc[3]);
            rapportParClasse.add(row);
        });
        model.addAttribute("rapportParClasse", rapportParClasse);

        // 3. Trimestriel (T1/T2/T3, dates issues des parametres deja utilises pour le calendrier scolaire)
        List<LigneBudget> lignesAnnee = budgetRepository.findByEtablissementIdAndAnneeScolaireOrderByDesignationAsc(etabId, annee);
        LocalDate t1Debut = lireParametreDate(etabId, "T1_DEBUT");
        LocalDate t1Fin = lireParametreDate(etabId, "T1_FIN");
        LocalDate t2Debut = lireParametreDate(etabId, "T2_DEBUT");
        LocalDate t2Fin = lireParametreDate(etabId, "T2_FIN");
        LocalDate t3Debut = lireParametreDate(etabId, "T3_DEBUT");
        LocalDate t3Fin = lireParametreDate(etabId, "T3_FIN");

        int anneeDebut;
        try { anneeDebut = Integer.parseInt(annee.split("-")[0].trim()); } catch (Exception e) { anneeDebut = horlogeService.aujourdHui().getYear(); }

        List<Map<String, Object>> rapportTrimestriel = new ArrayList<>();
        String[] noms = {"1er trimestre", "2e trimestre", "3e trimestre"};
        LocalDate[] debuts = {t1Debut, t2Debut, t3Debut};
        LocalDate[] fins = {t1Fin, t2Fin, t3Fin};
        for (int i = 0; i < 3; i++) {
            final LocalDate d = debuts[i], f = fins[i];
            final int anneeDebutF = anneeDebut;
            List<LigneBudget> lignesTrimestre = lignesAnnee.stream()
                .filter(l -> l.getMois() != null)
                .filter(l -> {
                    int annee2 = l.getMois() >= 9 ? anneeDebutF : anneeDebutF + 1;
                    LocalDate date = LocalDate.of(annee2, l.getMois(), 1);
                    return d != null && f != null && !date.isBefore(d) && !date.isAfter(f);
                }).collect(Collectors.toList());
            double prevu = lignesTrimestre.stream().mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
            double reel = lignesTrimestre.stream().mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nom", noms[i]); row.put("debut", d); row.put("fin", f);
            row.put("prevu", prevu); row.put("reel", reel);
            rapportTrimestriel.add(row);
        }
        model.addAttribute("rapportTrimestriel", rapportTrimestriel);

        // 4. Rapport annuel (compte de resultat simplifie sur l'annee scolaire complete)
        double produitsAnneeViaPaiements = tousPaiements.stream()
            .filter(p -> p.getDatePaiement() != null && annee.equals(AnneeScolaireUtil.pour(p.getDatePaiement().toLocalDate())))
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        double produitsAnneeViaDepenses = toutesDepenses.stream()
            .filter(d -> "PRODUIT".equals(d.getSens()) && annee.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double totalProduitsAnnee = produitsAnneeViaPaiements + produitsAnneeViaDepenses;
        double totalChargesAnnee = toutesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()) && annee.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double resultatNetAnnee = totalProduitsAnnee - totalChargesAnnee;

        Map<String, Double> chargesParPoste = toutesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()) && annee.equals(d.getAnneeScolaire()))
            .collect(Collectors.groupingBy(
                d -> d.getCategorieComptable() != null ? d.getCategorieComptable().getLibelle() : "Non classe",
                LinkedHashMap::new,
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));
        List<Map<String, Object>> repartitionChargesAnnee = calculerRepartition(chargesParPoste, totalChargesAnnee);

        // Balance des comptes : chaque poste du plan comptable ayant eu un mouvement cette annee,
        // avec son total — la premiere chose qu'un comptable regarde pour verifier la coherence du
        // classement, distincte de la repartition en pourcentage ci-dessus (pensee pour un graphique).
        List<CategorieComptable> planComptableActif = categorieComptableRepository.findByEtablissementIdAndActifTrueOrderByCodeAsc(etabId);
        Map<Long, Double> totalParPosteId = toutesDepenses.stream()
            .filter(d -> annee.equals(d.getAnneeScolaire()) && d.getCategorieComptable() != null)
            .collect(Collectors.groupingBy(d -> d.getCategorieComptable().getId(),
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));
        List<Map<String, Object>> balanceComptes = new ArrayList<>();
        for (CategorieComptable c : planComptableActif) {
            Double total = totalParPosteId.get(c.getId());
            if (total == null || total == 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", c.getCode());
            row.put("libelle", c.getLibelle());
            row.put("sens", c.getSens());
            row.put("montant", total);
            balanceComptes.add(row);
        }
        model.addAttribute("balanceComptes", balanceComptes);

        List<Map<String, Object>> evolutionAnnuelle = new ArrayList<>();
        String[] nomsMoisCourt = {"Sep","Oct","Nov","Dec","Jan","Fev","Mar","Avr","Mai","Jun","Jul","Aou"};
        int[] moisOrdre = {9,10,11,12,1,2,3,4,5,6,7,8};
        double soldeCourant = soldeInitial;
        List<Double> soldesFinMois = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int mois = moisOrdre[i];
            int anneeDuMois = mois >= 9 ? anneeDebut : anneeDebut + 1;
            double entrees = tousPaiements.stream()
                .filter(p -> p.getDatePaiement() != null && p.getDatePaiement().getMonthValue() == mois && p.getDatePaiement().getYear() == anneeDuMois)
                .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
            entrees += toutesDepenses.stream()
                .filter(d -> "PRODUIT".equals(d.getSens()) && d.getDateDepense() != null && d.getDateDepense().getMonthValue() == mois && d.getDateDepense().getYear() == anneeDuMois)
                .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
            double sorties = toutesDepenses.stream()
                .filter(d -> "CHARGE".equals(d.getSens()) && d.getDateDepense() != null && d.getDateDepense().getMonthValue() == mois && d.getDateDepense().getYear() == anneeDuMois)
                .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
            soldeCourant += entrees - sorties;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("mois", nomsMoisCourt[i]);
            point.put("entrees", entrees);
            point.put("sorties", sorties);
            point.put("solde", soldeCourant);
            evolutionAnnuelle.add(point);
            soldesFinMois.add(soldeCourant);
        }
        double maxEvolutionAnnuelle = evolutionAnnuelle.stream()
            .flatMapToDouble(p -> java.util.stream.DoubleStream.of((double) p.get("entrees"), (double) p.get("sorties")))
            .max().orElse(1);

        // Courbe de solde de caisse (12 points normalises pour un <polyline> SVG, viewBox 0 0 100 40)
        double soldeMin = soldesFinMois.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double soldeMax = soldesFinMois.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double ecartSolde = soldeMax - soldeMin;
        StringBuilder pointsCourbe = new StringBuilder();
        for (int i = 0; i < soldesFinMois.size(); i++) {
            double x = i * (100.0 / (soldesFinMois.size() - 1));
            double y = ecartSolde > 0 ? 36 - ((soldesFinMois.get(i) - soldeMin) / ecartSolde * 32) : 20;
            if (i > 0) pointsCourbe.append(' ');
            pointsCourbe.append(String.format(Locale.ROOT, "%.2f,%.2f", x, y));
        }

        double totalImpayesAnnee = rapportParClasse.stream().mapToDouble(r -> (double) r.get("reste")).sum();

        model.addAttribute("totalProduitsAnnee", totalProduitsAnnee);
        model.addAttribute("totalChargesAnnee", totalChargesAnnee);
        model.addAttribute("resultatNetAnnee", resultatNetAnnee);
        model.addAttribute("repartitionChargesAnnee", repartitionChargesAnnee);
        model.addAttribute("evolutionAnnuelle", evolutionAnnuelle);
        model.addAttribute("maxEvolutionAnnuelle", maxEvolutionAnnuelle > 0 ? maxEvolutionAnnuelle : 1);
        model.addAttribute("totalImpayesAnnee", totalImpayesAnnee);
        model.addAttribute("statCourbePoints", pointsCourbe.toString());
        model.addAttribute("statSoldeMin", soldeMin);
        model.addAttribute("statSoldeMax", soldeMax);
        model.addAttribute("statSoldeActuel", soldeCourant);
    }

    private LocalDate lireParametreDate(Long etabId, String cle) {
        return parametreRepository.findByCleAndEtablissementId(cle, etabId)
            .map(Parametre::getValeur)
            .map(v -> { try { return LocalDate.parse(v, FMT_PARAM_DATE); } catch (Exception e) { return null; } })
            .orElse(null);
    }

    @PostMapping("/comptages")
    public String ajouterComptage(@RequestParam Double soldeCompte, @RequestParam(required = false) String note,
                                   RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        double soldeInitial = lireParametreDouble(etabId, "SOLDE_INITIAL_CAISSE", 0.0);
        double totalEntrees = paiementRepository.findByEtablissementId(etabId).stream()
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        List<Depense> toutesDepenses = depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId);
        for (Depense d : toutesDepenses) {
            if ("PRODUIT".equals(d.getSens())) totalEntrees += d.getMontant() != null ? d.getMontant() : 0;
        }
        double totalSorties = toutesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double soldeTheorique = soldeInitial + totalEntrees - totalSorties;

        ComptageCaisse c = new ComptageCaisse();
        c.setDateComptage(horlogeService.aujourdHui());
        c.setSoldeTheorique(soldeTheorique);
        c.setSoldeCompte(soldeCompte);
        c.setEcart(soldeCompte - soldeTheorique);
        c.setNote(note);
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) c.setEnregistreParId(utilisateur.getId());
        c.setEtablissementId(etabId);
        comptageCaisseRepository.save(c);

        journalService.log("COMPTAGE_CAISSE", "FINANCES", "Solde compte : " + soldeCompte + " F (ecart " + c.getEcart() + " F)");
        ra.addFlashAttribute("successMsg", c.getEcart() == 0
            ? "Comptage enregistre, aucun ecart."
            : "Comptage enregistre : ecart de " + c.getEcart() + " F.");
        return "redirect:/finances?tab=rapports";
    }
}
