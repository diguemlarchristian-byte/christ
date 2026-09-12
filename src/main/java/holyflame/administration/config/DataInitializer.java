package holyflame.administration.config;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.util.AnneeScolaireUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private FraisScolariteRepository fraisRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private CategorieComptableRepository categorieComptableRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private AnneeScolaireRepository anneeScolaireRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private SalaireMensuelRepository salaireMensuelRepository;
    @Autowired private PeriodeCalendrierRepository periodeCalendrierRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private ArticleInfirmerieRepository articleInfirmerieRepository;
    @Autowired private ArticleInventaireRepository articleInventaireRepository;
    @Autowired private FicheControleRepository ficheControleRepository;
    @Autowired private AvisParentRepository avisParentRepository;
    @Autowired private PointageRepository pointageRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private RetenueRepository retenueRepository;
    @Autowired private RetardRepository retardRepository;
    @Autowired private ProgrammeRepository programmeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private holyflame.administration.service.PlanComptableService planComptableService;

    @Override
    public void run(String... args) {

        // 1. SUPER_ADMIN (aucun établissement)
        saveAdmin("Super", "Admin", "SUPER_ADMIN_EMAIL", "superadmin@holyflame.com",
            "SUPER_ADMIN_PASSWORD", "SUPER_ADMIN", null);

        // 2. Établissement par défaut
        Etablissement defEtab = etablissementRepository.findByCodeAcces("HF-DEMO-001")
            .orElseGet(() -> {
                Etablissement e = new Etablissement();
                e.setNom("HolyFlame");
                e.setVille("Abidjan");
                e.setCodeAcces("HF-DEMO-001");
                e.setStatut("ACTIF");
                e.setDateCreation(LocalDate.now());
                e.setAnneeScolaire("2025-2026");
                e.setTypeEtablissement("COLLEGE");
                e.setMonnaie("FCFA");
                return etablissementRepository.save(e);
            });
        Long etabId = defEtab.getId();

        // 3. Utilisateurs démo liés à l'établissement par défaut (chaque vérification est indépendante)
        saveAdmin("Admin", "HolyFlame", "ADMIN_EMAIL", "admin@holyflame.com",
            "ADMIN_PASSWORD", "ADMIN", defEtab);
        if (utilisateurRepository.findByEmail("directeur@holyflame.com").isEmpty())
            saveUser("Kone",    "Ibrahim",   "directeur@holyflame.com",  "direct123", "DIRECTEUR",  defEtab);
        if (utilisateurRepository.findByEmail("secretaire@holyflame.com").isEmpty())
            saveUser("Dupont",  "Marie",     "secretaire@holyflame.com", "secret123", "SECRETAIRE", defEtab);
        if (utilisateurRepository.findByEmail("tresorier@holyflame.com").isEmpty())
            saveUser("Martin",  "Paul",      "tresorier@holyflame.com",  "tresor123", "TRESORIER",  defEtab);
        if (utilisateurRepository.findByEmail("comptable@holyflame.com").isEmpty())
            saveUser("Kouame",  "Solange",   "comptable@holyflame.com",  "compta123", "COMPTABLE",  defEtab);
        if (utilisateurRepository.findByEmail("enseignant@holyflame.com").isEmpty())
            saveUser("Leclerc", "Jean",      "enseignant@holyflame.com", "ens123",    "ENSEIGNANT", defEtab);
        if (utilisateurRepository.findByEmail("coordonnateur@holyflame.com").isEmpty())
            saveUser("Nguessan", "Alain",    "coordonnateur@holyflame.com", "coord123", "COORDONNATEUR", defEtab);
        if (utilisateurRepository.findByEmail("eleve@holyflame.com").isEmpty())
            saveUser("KONAN",   "Amara",     "eleve@holyflame.com",      "eleve123",  "ELEVE",      defEtab);
        if (utilisateurRepository.findByEmail("surveillant@holyflame.com").isEmpty())
            saveUser("Demba",   "Oumar",     "surveillant@holyflame.com","surv123",   "SURVEILLANT",defEtab);
        if (utilisateurRepository.findByEmail("infirmerie@holyflame.com").isEmpty())
            saveUser("Kouassi", "Ines",      "infirmerie@holyflame.com", "infirm123", "INFIRMIER",  defEtab);
        if (utilisateurRepository.findByEmail("marketing@holyflame.com").isEmpty())
            saveUser("Kacou",   "Estelle",   "marketing@holyflame.com",  "market123", "MARKETING",  defEtab);

        // 4. Classes
        if (classeRepository.count() == 0) {
            saveClasse("6ème A", "6ème", "2025-2026", etabId);
            saveClasse("5ème A", "5ème", "2025-2026", etabId);
            saveClasse("4ème A", "4ème", "2025-2026", etabId);
            saveClasse("3ème A", "3ème", "2025-2026", etabId);
            saveClasse("2nde A", "2nde", "2025-2026", etabId);
        }

        // 5. Matières
        if (matiereRepository.count() == 0) {
            saveMatiere("Mathématiques", 4.0, "Algèbre, géométrie, statistiques",     etabId);
            saveMatiere("Français",      4.0, "Littérature, grammaire, expression",   etabId);
            saveMatiere("Sciences",      3.0, "SVT, physique-chimie",                 etabId);
            saveMatiere("Histoire-Géo",  3.0, "Histoire et géographie",               etabId);
            saveMatiere("Anglais",       3.0, "Langue vivante 1",                     etabId);
            saveMatiere("EPS",           2.0, "Éducation physique et sportive",       etabId);
            saveMatiere("Informatique",  2.0, "Algorithmique, bureautique",           etabId);
        }

        // 6. Élèves
        if (eleveRepository.count() == 0) {
            Classe c6A = classeRepository.findAll().get(0);
            Classe c5A = classeRepository.findAll().get(1);
            saveEleve("HF-2026-001","KONAN",    "Amara",   LocalDate.of(2012,3,15),"0701234567","konan@email.com",    c6A, etabId);
            saveEleve("HF-2026-002","DIALLO",   "Fatima",  LocalDate.of(2012,7,22),"0702345678","diallo@email.com",   c6A, etabId);
            saveEleve("HF-2026-003","COULIBALY","Ibrahim", LocalDate.of(2011,11,5),"0703456789","coulibaly@email.com",c5A, etabId);
            saveEleve("HF-2026-004","TRAORE",   "Mariama", LocalDate.of(2011,4,18),"0704567890","traore@email.com",   c5A, etabId);
            saveEleve("HF-2026-005","BAMBA",    "Ousmane", LocalDate.of(2012,9,30),"0705678901","bamba@email.com",    c6A, etabId);
        }

        // 7. Paramètres de base
        if (parametreRepository.count() == 0) {
            saveParam("ANNEE_SCOLAIRE",   "2025-2026",   "Année scolaire en cours",         "GENERAL",     etabId);
            saveParam("TRIMESTRE_ACTUEL", "1",           "Trimestre actuel",                "GENERAL",     etabId);
            saveParam("NOM_ETABLISSEMENT","HolyFlame",   "Nom de l'établissement",          "GENERAL",     etabId);
            saveParam("VILLE",            "Abidjan",     "Ville de l'établissement",        "GENERAL",     etabId);
            saveParam("T1_DEBUT",         "01/10/2025",  "Début du 1er trimestre",          "CALENDRIER",  etabId);
            saveParam("T1_FIN",           "31/12/2025",  "Fin du 1er trimestre",            "CALENDRIER",  etabId);
            saveParam("T2_DEBUT",         "06/01/2026",  "Début du 2e trimestre",           "CALENDRIER",  etabId);
            saveParam("T2_FIN",           "31/03/2026",  "Fin du 2e trimestre",             "CALENDRIER",  etabId);
            saveParam("T3_DEBUT",         "14/04/2026",  "Début du 3e trimestre",           "CALENDRIER",  etabId);
            saveParam("T3_FIN",           "30/06/2026",  "Fin du 3e trimestre",             "CALENDRIER",  etabId);
            saveParam("EXAMEN_T1",        "15/12/2025",  "Date des examens T1",             "CALENDRIER",  etabId);
            saveParam("EXAMEN_T2",        "20/03/2026",  "Date des examens T2",             "CALENDRIER",  etabId);
            saveParam("EXAMEN_FINAL",     "15/06/2026",  "Date des examens de fin d'année", "CALENDRIER",  etabId);
        }

        // 8. Paramètres identité école (idempotents)
        upsertParam("ADRESSE",            "",           "Adresse de l'établissement",    "ETABLISSEMENT", etabId);
        upsertParam("TELEPHONE_ECOLE",    "",           "Téléphone",                     "ETABLISSEMENT", etabId);
        upsertParam("EMAIL_ECOLE",        "",           "Email institutionnel",          "ETABLISSEMENT", etabId);
        upsertParam("CONTACT_PRINCIPAL",  "",           "Directeur / Contact principal", "ETABLISSEMENT", etabId);
        upsertParam("DEVISE",             "",           "Devise / Credo de l'école",     "ETABLISSEMENT", etabId);
        upsertParam("MONNAIE",            "FCFA",       "Monnaie utilisée",              "ETABLISSEMENT", etabId);
        upsertParam("TYPE_MATERNELLE",    "false",      "Type Maternelle",               "ETABLISSEMENT", etabId);
        upsertParam("TYPE_PRIMAIRE",      "false",      "Type Primaire",                 "ETABLISSEMENT", etabId);
        upsertParam("TYPE_COLLEGE",       "true",       "Type Collège",                  "ETABLISSEMENT", etabId);
        upsertParam("TYPE_LYCEE",         "false",      "Type Lycée",                    "ETABLISSEMENT", etabId);
        upsertParam("TYPE_LYCEE_GENERAL", "false",      "Type Lycée Général",            "ETABLISSEMENT", etabId);
        upsertParam("DECOUPAGE",          "TRIMESTRES", "Découpage calendaire",          "CALENDRIER",    etabId);

        // 8bis. Taux de cotisations sur salaire (idempotents, modifiables par le comptable)
        upsertParam("TAUX_CNPS_EMPLOYE",              "4",  "CNPS employe (% du brut, retenue salariale)",                 "PAIE", etabId);
        upsertParam("TAUX_IRPP",                      "10", "IRPP (% du net soumis, retenue salariale)",                   "PAIE", etabId);
        upsertParam("TAUX_TAXE_APPRENTISSAGE",        "1",  "Taxe d'apprentissage (% du net soumis, charge patronale)",    "PAIE", etabId);
        upsertParam("TAUX_TAXE_FORFAITAIRE",          "8",  "Taxe forfaitaire (% du net soumis, charge patronale)",        "PAIE", etabId);
        upsertParam("TAUX_CNPS_ACCIDENT_TRAVAIL",     "4",  "CNPS accident de travail (% du brut, charge patronale)",      "PAIE", etabId);
        upsertParam("TAUX_CNPS_ALLOCATIONS_FAMILIALES","8", "CNPS allocations familiales (% du brut, charge patronale)",   "PAIE", etabId);
        upsertParam("TAUX_CNPS_PENSION_VIEILLESSE",   "5",  "CNPS pension vieillesse (% du brut, charge patronale)",       "PAIE", etabId);

        // 9. Frais scolaires
        if (fraisRepository.count() == 0) {
            saveFrais("Frais d'inscription",       "INSCRIPTION", 50000.0, "T1",           null, true,  etabId);
            saveFrais("Scolarité – 1er trimestre", "SCOLARITE",   75000.0, "T1",           null, true,  etabId);
            saveFrais("Scolarité – 2e trimestre",  "SCOLARITE",   75000.0, "T2",           null, true,  etabId);
            saveFrais("Scolarité – 3e trimestre",  "SCOLARITE",   75000.0, "T3",           null, true,  etabId);
            saveFrais("Transport scolaire",        "TRANSPORT",   30000.0, "Par trimestre",null, false, etabId);
        }

        // 10. Zones de l'école
        if (zoneRepository.findByEtablissementIdOrderByNomAsc(etabId).isEmpty()) {
            saveZone("Cour principale", false, etabId);
            saveZone("Réfectoire", false, etabId);
            saveZone("Gymnase", false, etabId);
            saveZone("Bibliothèque", false, etabId);
            saveZone("Terrasse technique (accès interdit)", true, etabId);
        }

        // 10bis. Stock infirmerie
        if (articleInfirmerieRepository.findByEtablissementIdOrderByDesignationAsc(etabId).isEmpty()) {
            saveArticleInfirmerie("Paracétamol (500mg)", 85, 100, "boîtes", etabId);
            saveArticleInfirmerie("Trousses de secours", 12, 20, "trousses", etabId);
            saveArticleInfirmerie("Stylos Adrénaline", 3, 20, "unités", etabId);
        }

        // 12. Plan comptable — desormais seede pour CHAQUE etablissement (defensif, idempotent
        // par etablissement) via PlanComptableService, plutot qu'une seule fois globalement pour
        // le tout premier etablissement : sinon tout etablissement cree ensuite (inscription en
        // ligne ou nouvelle base) se retrouvait sans aucune categorie comptable, rendant tout le
        // module Depenses/Budget inutilisable.
        for (Etablissement e : etablissementRepository.findAll()) {
            planComptableService.seedSiVide(e.getId());
        }

        // 12bis. Groupe fonctionnel des postes de charge (rapport econome trimestriel : Fournitures
        // scolaires / Entretien-Salubrite / Fonctionnement / Investissement). Backfill idempotent —
        // s'execute a chaque demarrage, meme sur une base deja seedee, pour corriger/completer.
        Map<String, String> groupesParCode = new LinkedHashMap<>();
        groupesParCode.put("21820", "INVESTISSEMENT");
        groupesParCode.put("21830", "INVESTISSEMENT");
        groupesParCode.put("21840", "INVESTISSEMENT");
        groupesParCode.put("60110", "FONCTIONNEMENT");
        groupesParCode.put("60450", "FONCTIONNEMENT");
        groupesParCode.put("60460", "ENTRETIEN_SALUBRITE");
        groupesParCode.put("60470", "FOURNITURES_SCOLAIRES");
        groupesParCode.put("60472", "FONCTIONNEMENT");
        groupesParCode.put("60473", "FOURNITURES_SCOLAIRES");
        groupesParCode.put("60530", "FONCTIONNEMENT");
        groupesParCode.put("60531", "FONCTIONNEMENT");
        groupesParCode.put("60500", "FONCTIONNEMENT");
        groupesParCode.put("60510", "INVESTISSEMENT");
        groupesParCode.put("60580", "INVESTISSEMENT");
        groupesParCode.put("61412", "FONCTIONNEMENT");
        groupesParCode.put("62420", "ENTRETIEN_SALUBRITE");
        groupesParCode.put("62430", "INVESTISSEMENT");
        groupesParCode.put("62440", "FONCTIONNEMENT");
        groupesParCode.put("62650", "FOURNITURES_SCOLAIRES");
        groupesParCode.put("62810", "FONCTIONNEMENT");
        groupesParCode.put("63840", "FONCTIONNEMENT");
        groupesParCode.put("65130", "FONCTIONNEMENT");
        groupesParCode.put("65820", "FONCTIONNEMENT");
        groupesParCode.put("66120", "FONCTIONNEMENT");
        groupesParCode.put("66110", "FONCTIONNEMENT");
        groupesParCode.put("66170", "FOURNITURES_SCOLAIRES");
        groupesParCode.put("66160", "FONCTIONNEMENT");
        groupesParCode.put("66161", "FONCTIONNEMENT");
        groupesParCode.put("66180", "FONCTIONNEMENT");
        groupesParCode.put("66181", "FONCTIONNEMENT");
        groupesParCode.put("66182", "FONCTIONNEMENT");
        groupesParCode.put("66183", "FONCTIONNEMENT");
        groupesParCode.put("66420", "FONCTIONNEMENT");
        groupesParCode.put("66421", "FONCTIONNEMENT");
        groupesParCode.put("66430", "FONCTIONNEMENT");
        groupesParCode.put("66431", "FONCTIONNEMENT");
        groupesParCode.put("66840", "FONCTIONNEMENT");
        groupesParCode.put("66841", "FONCTIONNEMENT");
        groupesParCode.put("66850", "FOURNITURES_SCOLAIRES");
        groupesParCode.put("66881", "FONCTIONNEMENT");
        groupesParCode.put("67500", "FONCTIONNEMENT");
        groupesParCode.put("67600", "INVESTISSEMENT");
        groupesParCode.forEach((code, groupe) ->
            categorieComptableRepository.findByCodeAndEtablissementId(code, etabId).ifPresent(c -> {
                if (c.getGroupe() == null || c.getGroupe().isBlank()) {
                    c.setGroupe(groupe);
                    categorieComptableRepository.save(c);
                }
            }));

        // 12ter. Backfill idempotent : fige l'annee scolaire des notes deja saisies avant l'ajout du
        // champ Note.anneeScolaire (indispensable pour que le passage de classe ne mélange pas les
        // notes de plusieurs annees dans un bulletin). Sans historique de classe, on suppose que ces
        // notes anciennes concernent l'annee de la classe actuelle de l'eleve (aucun passage n'a encore
        // eu lieu au moment ou ce backfill s'execute pour la premiere fois).
        for (Note n : noteRepository.findByAnneeScolaireIsNullWithEleveEtClasse()) {
            n.setAnneeScolaire(n.getEleve().getClasse().getAnneeScolaire());
            noteRepository.save(n);
        }

        // 12quinquies. Purge idempotente des notes orphelines : l'annee scolaire et la classe
        // sont desormais obligatoires a la saisie (cf. NoteController), donc toute note qui en
        // serait encore depourvue apres le backfill ci-dessus est forcement une donnee ancienne
        // impossible a rattacher a un bulletin — elle serait invisible ou fausserait les moyennes
        // si on la laissait trainer. Sans effet une fois la base assainie (listes vides).
        List<Note> notesSansAnnee = noteRepository.findByAnneeScolaireIsNull();
        if (!notesSansAnnee.isEmpty())
            noteRepository.deleteAll(notesSansAnnee);
        List<Note> notesSansClasse = noteRepository.findByEleveClasseIsNull();
        if (!notesSansClasse.isEmpty())
            noteRepository.deleteAll(notesSansClasse);

        // 11. Lier compte élève KONAN Amara
        if (eleveRepository.findByCompteEmail("eleve@holyflame.com").isEmpty()) {
            eleveRepository.findAll().stream()
                .filter(e -> "KONAN".equals(e.getNom()) && "Amara".equals(e.getPrenom()))
                .findFirst()
                .ifPresent(e -> { e.setCompteEmail("eleve@holyflame.com"); eleveRepository.save(e); });
        }

        // 12quater. Backfill idempotent : fige l'annee scolaire des paiements et salaires deja
        // enregistres avant l'ajout de ces champs, deduite de leur date (necessaire pour que la
        // cloture d'annee puisse verrouiller ces enregistrements correctement).
        for (Paiement p : paiementRepository.findAll()) {
            if (p.getAnneeScolaire() == null && p.getDatePaiement() != null) {
                p.setAnneeScolaire(AnneeScolaireUtil.pour(p.getDatePaiement().toLocalDate()));
                paiementRepository.save(p);
            }
        }
        for (SalaireMensuel s : salaireMensuelRepository.findAll()) {
            if (s.getAnneeScolaire() == null && s.getPeriodeDebut() != null) {
                s.setAnneeScolaire(AnneeScolaireUtil.pour(s.getPeriodeDebut()));
                salaireMensuelRepository.save(s);
            }
        }
        for (Absence a : absenceRepository.findAll()) {
            if (a.getAnneeScolaire() == null && a.getDate() != null) {
                a.setAnneeScolaire(AnneeScolaireUtil.pour(a.getDate()));
                absenceRepository.save(a);
            }
        }
        for (FicheControle f : ficheControleRepository.findAll()) {
            if (f.getAnneeScolaire() == null && f.getDateVisite() != null) {
                f.setAnneeScolaire(AnneeScolaireUtil.pour(f.getDateVisite()));
                ficheControleRepository.save(f);
            }
        }
        for (AvisParent av : avisParentRepository.findAll()) {
            if (av.getAnneeScolaire() == null && av.getDateSignalement() != null) {
                av.setAnneeScolaire(AnneeScolaireUtil.pour(av.getDateSignalement().toLocalDate()));
                avisParentRepository.save(av);
            }
        }
        for (Pointage p : pointageRepository.findAll()) {
            if (p.getAnneeScolaire() == null && p.getDateHeure() != null) {
                p.setAnneeScolaire(AnneeScolaireUtil.pour(p.getDateHeure().toLocalDate()));
                pointageRepository.save(p);
            }
        }
        for (Incident i : incidentRepository.findAll()) {
            if (i.getAnneeScolaire() == null && i.getDateHeure() != null) {
                i.setAnneeScolaire(AnneeScolaireUtil.pour(i.getDateHeure().toLocalDate()));
                incidentRepository.save(i);
            }
        }
        for (Retenue r : retenueRepository.findAll()) {
            if (r.getAnneeScolaire() == null && r.getDateRetenue() != null) {
                r.setAnneeScolaire(AnneeScolaireUtil.pour(r.getDateRetenue()));
                retenueRepository.save(r);
            }
        }
        for (Retard r : retardRepository.findAll()) {
            if (r.getAnneeScolaire() == null && r.getDate() != null) {
                r.setAnneeScolaire(AnneeScolaireUtil.pour(r.getDate()));
                retardRepository.save(r);
            }
        }
        for (Programme pr : programmeRepository.findAll()) {
            if (pr.getAnneeScolaire() == null && pr.getDateDebut() != null) {
                pr.setAnneeScolaire(AnneeScolaireUtil.pour(pr.getDateDebut()));
                programmeRepository.save(pr);
            }
        }

        // 13. Backfill idempotent : chaque etablissement doit avoir une AnneeScolaire ACTIVE
        // correspondant a son annee courante (necessaire pour cloturer/dupliquer les annees).
        for (Etablissement etab : etablissementRepository.findAll()) {
            if (anneeScolaireRepository.findByEtablissementIdAndStatut(etab.getId(), "ACTIVE").isEmpty()) {
                String annee = etab.getAnneeScolaire() != null ? etab.getAnneeScolaire() : "2025-2026";
                anneeScolaireRepository.findByEtablissementIdAndLibelle(etab.getId(), annee)
                    .ifPresentOrElse(
                        a -> { a.setStatut("ACTIVE"); anneeScolaireRepository.save(a); },
                        () -> {
                            AnneeScolaire a = new AnneeScolaire();
                            a.setLibelle(annee);
                            a.setEtablissementId(etab.getId());
                            a.setStatut("ACTIVE");
                            a.setDateCreation(LocalDate.now());
                            a.setDateActivation(LocalDate.now());
                            anneeScolaireRepository.save(a);
                        });
            }
        }

        // 14. Backfill idempotent : recree les PeriodeCalendrier de type trimestre a partir des
        // Parametre T{n}_DEBUT/T{n}_FIN seedes historiquement, pour que /parametres/calendrier
        // (qui lit desormais PeriodeCalendrier comme source de verite) ne demarre pas vide alors
        // que ces dates existent deja dans les Parametre.
        java.time.format.DateTimeFormatter formatFr = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (Etablissement etab : etablissementRepository.findAll()) {
            Long etabId2 = etab.getId();
            String anneeEtab = etab.getAnneeScolaire() != null ? etab.getAnneeScolaire() : "2025-2026";
            List<PeriodeCalendrier> periodesExistantes = periodeCalendrierRepository.findByEtablissementIdOrderByDateDebutAsc(etabId2);
            for (int n = 1; n <= 3; n++) {
                String type = "TRIMESTRE" + n;
                boolean dejaPresent = periodesExistantes.stream().anyMatch(p -> type.equals(p.getType()));
                if (dejaPresent) continue;
                var debutParam = parametreRepository.findByCleAndEtablissementId("T" + n + "_DEBUT", etabId2);
                var finParam = parametreRepository.findByCleAndEtablissementId("T" + n + "_FIN", etabId2);
                if (debutParam.isEmpty() || finParam.isEmpty()) continue;
                try {
                    LocalDate debut = LocalDate.parse(debutParam.get().getValeur(), formatFr);
                    LocalDate fin = LocalDate.parse(finParam.get().getValeur(), formatFr);
                    PeriodeCalendrier p = new PeriodeCalendrier();
                    p.setNom("Trimestre " + n);
                    p.setType(type);
                    p.setAnneeScolaire(anneeEtab);
                    p.setDateDebut(debut);
                    p.setDateFin(fin);
                    p.setEtablissementId(etabId2);
                    periodeCalendrierRepository.save(p);
                } catch (Exception ignored) {
                    // Format de date inattendu dans un Parametre saisi manuellement : on ignore ce trimestre,
                    // l'admin le redeclarera depuis /parametres/calendrier si besoin.
                }
            }
        }

        migrerLInventaire();
    }

    /**
     * Reprise des inventaires saisis avant que le lot ne sache compter ses unites indisponibles.
     *
     * Ces deux reprises tournaient auparavant a chaque affichage de la page d'inventaire. Le
     * rattachement des articles orphelins y etait particulierement dangereux : il donnait le
     * materiel sans etablissement a la premiere ecole dont un responsable ouvrait l'ecran. Il
     * n'a lieu ici que s'il n'existe qu'un seul etablissement — au-dela, aucun moyen honnete de
     * deviner le proprietaire, et un article laisse de cote vaut mieux qu'un article vole.
     */
    private void migrerLInventaire() {
        int enReparation = articleInventaireRepository.migrerEtatEnReparation();
        int horsService = articleInventaireRepository.migrerEtatHorsService();
        if (enReparation + horsService > 0) {
            System.out.println("Inventaire : " + (enReparation + horsService)
                + " article(s) repris sur les compteurs d'unites indisponibles.");
        }

        long orphelins = articleInventaireRepository.countByEtablissementIdIsNull();
        if (orphelins == 0) return;

        List<Etablissement> etablissements = etablissementRepository.findAll();
        if (etablissements.size() == 1) {
            int repris = articleInventaireRepository.migrateNullEtablissementId(etablissements.get(0).getId());
            System.out.println("Inventaire : " + repris + " article(s) rattache(s) a "
                + etablissements.get(0).getNom() + ".");
        } else {
            System.out.println("Inventaire : " + orphelins + " article(s) sans etablissement laisse(s)"
                + " de cote (" + etablissements.size() + " etablissements en base, rattachement"
                + " a faire a la main).");
        }
    }

    /**
     * Adresse et mot de passe d'un compte d'administration, lus dans l'environnement.
     *
     * Les comptes de demonstration livres avec l'application ont des mots de passe ecrits dans
     * ce fichier. C'est commode pour decouvrir le logiciel sur sa propre machine ; c'est
     * inacceptable des que l'adresse devient publique, puisque le code est lisible par
     * quiconque ouvre le depot.
     *
     * Les deux comptes qui donnent tout pouvoir — le super-administrateur et l'administrateur
     * de l'etablissement — se reglent donc par variables d'environnement. Et si l'on n'en
     * fournit aucune, le mot de passe n'est pas un defaut connu : il est tire au hasard et
     * affiche une seule fois dans le journal de demarrage. Personne ne peut deviner un compte
     * qui n'existait pas avant le premier lancement.
     */
    private String valeurOuDefaut(String variable, String defaut) {
        String v = System.getenv(variable);
        return v != null && !v.isBlank() ? v.trim() : defaut;
    }

    /** Mot de passe imprevisible, lisible et transcriptible : pas de caracteres ambigus. */
    private String motDePasseTireAuHasard() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789#@%+=?";
        java.security.SecureRandom hasard = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) sb.append(alphabet.charAt(hasard.nextInt(alphabet.length())));
        return sb.toString();
    }

    /**
     * Cree un compte d'administration, et annonce ses identifiants si on ne les a pas choisis.
     *
     * L'annonce tient en plusieurs lignes encadrees : dans un journal de demarrage qui defile,
     * un mot de passe glisse au fil du texte se perd, et il ne sera plus jamais affiche.
     */
    private void saveAdmin(String nom, String prenom, String varEmail, String emailDefaut,
                           String varMotDePasse, String role, Etablissement etab) {
        String email = valeurOuDefaut(varEmail, emailDefaut);
        if (utilisateurRepository.findByEmail(email).isPresent()) return;

        String fourni = System.getenv(varMotDePasse);
        boolean tireAuHasard = fourni == null || fourni.isBlank();
        String motDePasse = tireAuHasard ? motDePasseTireAuHasard() : fourni.trim();

        saveUser(nom, prenom, email, motDePasse, role, etab);

        if (tireAuHasard) {
            System.out.println();
            System.out.println("==========================================================");
            System.out.println("  COMPTE " + role + " CREE");
            System.out.println("  Adresse      : " + email);
            System.out.println("  Mot de passe : " + motDePasse);
            System.out.println();
            System.out.println("  Notez-le maintenant : il ne sera plus jamais affiche.");
            System.out.println("  Pour le choisir vous-meme, definissez " + varMotDePasse + ".");
            System.out.println("==========================================================");
            System.out.println();
        }
    }

    private void saveUser(String nom, String prenom, String email, String mdp, String role, Etablissement etab) {
        Utilisateur u = new Utilisateur();
        u.setNom(nom); u.setPrenom(prenom); u.setEmail(email);
        u.setMotDePasse(passwordEncoder.encode(mdp)); u.setRole(role);
        u.setEtablissement(etab);
        utilisateurRepository.save(u);
    }

    private void saveClasse(String nom, String niveau, String annee, Long etabId) {
        Classe c = new Classe();
        c.setNom(nom); c.setNiveau(niveau); c.setAnneeScolaire(annee);
        c.setEtablissementId(etabId);
        classeRepository.save(c);
    }

    private void saveMatiere(String nom, Double coef, String desc, Long etabId) {
        Matiere m = new Matiere();
        m.setNom(nom); m.setCoefficient(coef); m.setDescription(desc);
        m.setEtablissementId(etabId);
        matiereRepository.save(m);
    }

    private void saveEleve(String mat, String nom, String prenom, LocalDate ddn,
                           String tel, String email, Classe classe, Long etabId) {
        Eleve e = new Eleve();
        e.setMatricule(mat); e.setNom(nom); e.setPrenom(prenom);
        e.setDateNaissance(ddn); e.setTelephoneParent(tel);
        e.setEmailParent(email); e.setStatutInscription("INSCRIT");
        e.setClasse(classe); e.setEtablissementId(etabId);
        eleveRepository.save(e);
    }

    private void saveParam(String cle, String valeur, String description, String categorie, Long etabId) {
        Parametre p = new Parametre();
        p.setCle(cle); p.setValeur(valeur);
        p.setDescription(description); p.setCategorie(categorie);
        p.setEtablissementId(etabId);
        parametreRepository.save(p);
    }

    private void upsertParam(String cle, String defVal, String desc, String cat, Long etabId) {
        if (!parametreRepository.existsByCleAndEtablissementId(cle, etabId)) {
            Parametre p = new Parametre();
            p.setCle(cle); p.setValeur(defVal);
            p.setDescription(desc); p.setCategorie(cat);
            p.setEtablissementId(etabId);
            parametreRepository.save(p);
        }
    }

    private void saveFrais(String designation, String type, Double montant,
                           String echeance, String niveau, boolean obligatoire, Long etabId) {
        FraisScolarite f = new FraisScolarite();
        f.setDesignation(designation); f.setTypeFrais(type);
        f.setMontant(montant); f.setEcheance(echeance);
        f.setNiveauCible(niveau); f.setObligatoire(obligatoire);
        f.setEtablissementId(etabId);
        fraisRepository.save(f);
    }

    private void saveArticleInfirmerie(String designation, int quantiteActuelle, int quantiteMax, String unite, Long etabId) {
        ArticleInfirmerie a = new ArticleInfirmerie();
        a.setDesignation(designation); a.setQuantiteActuelle(quantiteActuelle);
        a.setQuantiteMax(quantiteMax); a.setUnite(unite);
        a.setEtablissementId(etabId);
        articleInfirmerieRepository.save(a);
    }

    private void saveZone(String nom, boolean interdite, Long etabId) {
        Zone z = new Zone();
        z.setNom(nom); z.setZoneInterdite(interdite);
        z.setEtablissementId(etabId);
        zoneRepository.save(z);
    }

}
