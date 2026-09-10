package holyflame.administration;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import holyflame.administration.model.CategorieComptable;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Depense;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.RemiseEleve;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.*;
import holyflame.administration.service.DocumentsComptablesService;
import holyflame.administration.service.DocumentsComptablesService.Balance;
import holyflame.administration.service.DocumentsComptablesService.CompteDetaille;
import holyflame.administration.service.FinanceModules;
import holyflame.administration.service.PlanComptableService;
import holyflame.administration.service.SituationFinanciereService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jeu de test comptable : 25 operations sur une annee scolaire complete.
 *
 * Ce test ne verifie pas seulement que les calculs tombent juste — il produit aussi les
 * documents dans « C:\Holyflame\TEST COMPTABLE », pour qu'il ne soit plus necessaire de
 * ressaisir des donnees a chaque verification. Relancer ce test regenere tout.
 *
 * Les 25 operations couvrent ce qu'une ecole rencontre reellement sur une annee : les
 * encaissements de rentree, les salaires mensuels, les charges de fonctionnement, un
 * investissement, un don, et les paiements etales de familles qui ne reglent pas d'un coup.
 */
@SpringBootTest
@Transactional
class JeuDeTestComptableTest {

    private static final Path DOSSIER = Paths.get("C:", "Holyflame", "TEST COMPTABLE");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Compte comptable livre avec le jeu de test, pour pouvoir ouvrir l'ecole de demonstration. */
    private static final String COMPTE_DEMO = "comptable@demo.td";
    private static final String MOT_DE_PASSE_DEMO = "Demo2026";
    /**
     * Modules financiers confies au compte livre. Ils dessinent la comptable que le guide
     * decrit : elle encaisse, enregistre les depenses et sort les rapports, sans preparer la
     * paie ni arbitrer le budget previsionnel.
     */
    private static final Set<String> MODULES_DEMO =
        Set.of(FinanceModules.CAISSE, FinanceModules.DEPENSES, FinanceModules.RAPPORTS);

    @Autowired private DocumentsComptablesService documents;
    @Autowired private SituationFinanciereService situations;
    @Autowired private PlanComptableService planComptable;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private CategorieComptableRepository categorieRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private FraisScolariteRepository fraisRepository;
    @Autowired private RemiseEleveRepository remiseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Etablissement ecole;
    private final List<Eleve> eleves = new ArrayList<>();
    private final List<String> journal = new ArrayList<>();
    private final String annee = "2026-2027";

    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("25 operations, documents sur un mois et sur un an, dossier de test ecrit")
    void jeuDeTestComplet() throws IOException {
        ouvrirLEcole();
        List<FraisScolarite> frais = definirLesFrais();
        inscrireLesEleves();
        accorderLesRemises();
        int nbOperations = saisirLesOperations();

        assertEquals(25, nbOperations, "le jeu de test compte exactement 25 operations");
        verifierLesDroitsAnnoncesParLeGuide();

        LocalDate debutAnnee = LocalDate.of(2026, 9, 1);
        LocalDate finAnnee = LocalDate.of(2027, 8, 31);
        LocalDate debutMois = LocalDate.of(2026, 10, 1);
        LocalDate finMois = LocalDate.of(2026, 10, 31);

        Balance balanceAnnee = documents.balance(ecole.getId(), debutAnnee, finAnnee);
        Balance balanceMois = documents.balance(ecole.getId(), debutMois, finMois);
        List<CompteDetaille> livreAnnee = documents.grandLivre(ecole.getId(), debutAnnee, finAnnee, false);
        List<CompteDetaille> livreMois = documents.grandLivre(ecole.getId(), debutMois, finMois, false);

        // ── Verifications de coherence ──────────────────────────────────
        verifierCoherence(balanceAnnee, livreAnnee, "annee");
        verifierCoherence(balanceMois, livreMois, "mois");

        assertTrue(balanceMois.totalDebit() <= balanceAnnee.totalDebit(),
            "un mois ne peut pas peser plus que l'annee qui le contient");
        assertTrue(balanceMois.totalCredit() <= balanceAnnee.totalCredit(),
            "idem au credit");
        assertTrue(balanceAnnee.lignes().size() >= balanceMois.lignes().size(),
            "l'annee touche au moins autant de comptes que le mois");
        assertTrue(!livreMois.isEmpty(), "octobre doit contenir des ecritures");

        // ── Production des documents ────────────────────────────────────
        Files.createDirectories(DOSSIER);
        ecrireGuidePdf();
        ecrireJeuDeDonnees(nbOperations, balanceAnnee, balanceMois);
        ecrireLisezMoi(nbOperations, balanceAnnee, balanceMois);
        ecrireBalanceCsv(balanceAnnee, "balance-annee-2026-2027.csv");
        ecrireBalanceCsv(balanceMois, "balance-octobre-2026.csv");
        ecrireGrandLivreCsv(livreAnnee, "grand-livre-annee-2026-2027.csv");
        ecrireGrandLivreCsv(livreMois, "grand-livre-octobre-2026.csv");

        System.out.println("=== Dossier ecrit : " + DOSSIER + " ===");
        System.out.println("Annee : debit " + Math.round(balanceAnnee.totalDebit())
            + " / credit " + Math.round(balanceAnnee.totalCredit())
            + " / resultat " + Math.round(balanceAnnee.resultat()));
        System.out.println("Octobre : debit " + Math.round(balanceMois.totalDebit())
            + " / credit " + Math.round(balanceMois.totalCredit())
            + " / resultat " + Math.round(balanceMois.resultat()));

        // Par defaut la transaction est annulee : verifier les calculs ne doit pas laisser une
        // ecole de demonstration dans la base d'un etablissement en service. Avec
        // -Dcomptable.persister=true, l'ecole est conservee et devient consultable dans
        // l'application, avec le compte indique dans le guide.
        if (Boolean.getBoolean("comptable.persister")) {
            TestTransaction.flagForCommit();
            System.out.println("=== Ecole conservee en base : " + ecole.getNom()
                + " (code " + ecole.getCodeAcces() + ") ===");
            System.out.println("Connexion : " + COMPTE_DEMO + " / " + MOT_DE_PASSE_DEMO);
        } else {
            System.out.println("=== Donnees annulees apres verification "
                + "(ajoutez -Dcomptable.persister=true pour les conserver) ===");
        }
    }

    /**
     * Le guide annonce a la comptable ce qu'elle peut ouvrir et ce qui lui sera refuse. Ces
     * promesses ne tiennent pas a son role mais aux modules financiers de son compte, qu'un
     * ADMIN peut modifier. Les verifier ici evite qu'une retouche de la segmentation ne rende
     * le guide faux en silence : une comptable a qui l'on promet la caisse et qui recoit un
     * 403 ne sait pas si elle s'est trompee d'ecran ou si l'application est en panne.
     */
    private void verifierLesDroitsAnnoncesParLeGuide() {
        Utilisateur comptable = utilisateurRepository.findByEmail(COMPTE_DEMO).orElseThrow(
            () -> new IllegalStateException("le compte livre avec le jeu de test est introuvable"));

        assertTrue(FinanceModules.autorise(comptable, FinanceModules.CAISSE),
            "le guide apprend a encaisser et a delivrer un recu");
        assertTrue(FinanceModules.autorise(comptable, FinanceModules.DEPENSES),
            "le guide apprend a enregistrer une depense");
        assertTrue(FinanceModules.autorise(comptable, FinanceModules.RAPPORTS),
            "le guide demande d'editer et de remettre les documents comptables");

        assertFalse(FinanceModules.autorise(comptable, FinanceModules.PAIE_PREPARATION),
            "le guide annonce que les salaires du personnel lui sont fermes");
        assertFalse(FinanceModules.autorise(comptable, FinanceModules.PAIE_PAIEMENT),
            "idem pour le declenchement de la paie");
        assertFalse(FinanceModules.autorise(comptable, FinanceModules.BUDGET_PARAMETRAGE),
            "le guide annonce que le budget previsionnel et les taux de paie lui sont fermes");
    }

    /**
     * La balance doit etre le recapitulatif exact du grand livre.
     *
     * C'est la verification qui compte : si ces deux documents divergent, l'un des deux est
     * faux et la comptable ne peut se fier a aucun.
     */
    private void verifierCoherence(Balance balance, List<CompteDetaille> livre, String periode) {
        double debitLivre = livre.stream().mapToDouble(CompteDetaille::totalDebit).sum();
        double creditLivre = livre.stream().mapToDouble(CompteDetaille::totalCredit).sum();

        assertEquals(debitLivre, balance.totalDebit(), 0.01,
            "balance et grand livre doivent donner le meme debit sur " + periode);
        assertEquals(creditLivre, balance.totalCredit(), 0.01,
            "meme credit sur " + periode);
        assertEquals(livre.size(), balance.lignes().size(),
            "meme nombre de comptes mouvementes sur " + periode);
        assertEquals(balance.totalCredit() - balance.totalDebit(), balance.resultat(), 0.01,
            "le resultat est l'ecart entre credit et debit sur " + periode);
    }

    // ── Construction du jeu de donnees ──────────────────────────────────

    private void ouvrirLEcole() {
        Etablissement e = new Etablissement();
        e.setNom("Ecole de Demonstration Comptable");
        e.setStatut("ACTIF");
        e.setDateCreation(LocalDate.of(2026, 9, 1));
        e.setAnneeScolaire(annee);
        e.setCodeAcces("DEMO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        e.setTelephone("+235 66 00 00 00");
        e.setAdresse("Quartier Paris Congo, N'Djamena");
        ecole = etablissementRepository.saveAndFlush(e);
        planComptable.seedSiVide(ecole.getId());
        creerLeCompteComptable();
    }

    /**
     * Compte livre avec le jeu de test.
     *
     * Sans lui, une ecole conservee en base resterait inaccessible : personne ne pourrait s'y
     * connecter pour verifier a l'ecran ce que les documents annoncent.
     */
    private void creerLeCompteComptable() {
        // Hibernate reordonne les ecritures : sans ce vidage explicite, la suppression
        // passerait apres l'insertion et la contrainte d'unicite sur l'email echouerait.
        utilisateurRepository.findByEmail(COMPTE_DEMO).ifPresent(utilisateurRepository::delete);
        utilisateurRepository.flush();

        Utilisateur u = new Utilisateur();
        u.setNom("SARR");
        u.setPrenom("Marie");
        u.setEmail(COMPTE_DEMO);
        u.setMotDePasse(passwordEncoder.encode(MOT_DE_PASSE_DEMO));
        u.setRole("COMPTABLE");
        u.setEtablissement(ecole);
        // Les droits par defaut du role COMPTABLE (voir FinanceModules) excluent la caisse et
        // ouvrent au contraire la paie et le budget previsionnel. Ce n'est pas la comptable que
        // le guide decrit : celle-ci encaisse, enregistre les depenses et edite les documents,
        // sans approcher les salaires ni arbitrer le budget. Le compte livre recoit donc
        // exactement ces trois modules — ce qu'un ADMIN fait depuis Parametres > Roles > Acces
        // aux interfaces. Sans cette ligne, le guide promettrait a la comptable des ecrans qui
        // lui repondraient 403, et lui cacherait ceux qu'elle peut reellement ouvrir.
        u.setModulesFinanceActifs(MODULES_DEMO);
        utilisateurRepository.saveAndFlush(u);
    }

    private List<FraisScolarite> definirLesFrais() {
        List<FraisScolarite> liste = new ArrayList<>();
        liste.add(frais("Frais d'inscription", "INSCRIPTION", 25_000, "UNIQUE", null, true));
        liste.add(frais("Scolarite annuelle CP1", "SCOLARITE", 150_000, "ANNUEL", "CP1", true));
        liste.add(frais("Scolarite annuelle CM2", "SCOLARITE", 180_000, "ANNUEL", "CM2", true));
        liste.add(frais("Cantine", "CANTINE", 60_000, "TRIMESTRIEL", null, false));
        return liste;
    }

    private FraisScolarite frais(String designation, String type, double montant,
                                 String echeance, String niveau, boolean obligatoire) {
        FraisScolarite f = new FraisScolarite();
        f.setDesignation(designation);
        f.setTypeFrais(type);
        f.setMontant(montant);
        f.setEcheance(echeance);
        f.setNiveauCible(niveau);
        f.setObligatoire(obligatoire);
        f.setEtablissementId(ecole.getId());
        return fraisRepository.saveAndFlush(f);
    }

    private void inscrireLesEleves() {
        Classe cp1 = classe("CP1 A", "CP1");
        Classe cm2 = classe("CM2 A", "CM2");
        eleves.add(eleve("DEMO-001", "NGARTA", "Amina", cp1));
        eleves.add(eleve("DEMO-002", "MBAIRAMADJI", "Josue", cp1));
        eleves.add(eleve("DEMO-003", "DJIMET", "Fatime", cm2));
        eleves.add(eleve("DEMO-004", "TOLLI", "Abakar", cm2));
        eleves.add(eleve("DEMO-005", "HAROUN", "Zara", cp1));
    }

    private Classe classe(String nom, String niveau) {
        Classe c = new Classe();
        c.setNom(nom);
        c.setNiveau(niveau);
        c.setAnneeScolaire(annee);
        c.setEtablissementId(ecole.getId());
        return classeRepository.saveAndFlush(c);
    }

    private Eleve eleve(String matricule, String nom, String prenom, Classe classe) {
        Eleve e = new Eleve();
        e.setMatricule(matricule);
        e.setNom(nom);
        e.setPrenom(prenom);
        e.setClasse(classe);
        e.setEtablissementId(ecole.getId());
        return eleveRepository.saveAndFlush(e);
    }

    /** Deux cas frequents : l'enfant d'un employe, et une fratrie. */
    private void accorderLesRemises() {
        remise(eleves.get(4), "POURCENTAGE", 100, "PERSONNEL", "Enfant d'une enseignante");
        remise(eleves.get(1), "POURCENTAGE", 25, "FRATRIE", "Deuxieme enfant inscrit");
    }

    private void remise(Eleve eleve, String type, double valeur, String motif, String commentaire) {
        RemiseEleve r = new RemiseEleve();
        r.setEleveId(eleve.getId());
        r.setType(type);
        r.setValeur(valeur);
        r.setMotif(motif);
        r.setCommentaire(commentaire);
        r.setAnneeScolaire(annee);
        r.setDateAccord(LocalDate.of(2026, 9, 15));
        r.setEtablissementId(ecole.getId());
        remiseRepository.saveAndFlush(r);
    }

    /** Les 25 operations de l'annee, dans l'ordre ou une ecole les vit. */
    private int saisirLesOperations() {
        int n = 0;

        // Septembre — rentree : inscriptions et premiers versements
        n += encaisser(eleves.get(0), 25_000, "2026-09-10", "ESPECES", "Inscription");
        n += encaisser(eleves.get(1), 25_000, "2026-09-10", "ESPECES", "Inscription");
        n += encaisser(eleves.get(2), 25_000, "2026-09-12", "MOBILE_MONEY", "Inscription");
        n += encaisser(eleves.get(3), 25_000, "2026-09-12", "ESPECES", "Inscription");
        n += depenser("60473", "Fournitures scolaires de rentree", 320_000, "2026-09-05", "Librairie Centrale");
        n += depenser("66170", "Tenues de classe", 180_000, "2026-09-08", "Atelier Couture Amina");

        // Octobre — le mois de reference du controle mensuel
        n += encaisser(eleves.get(0), 50_000, "2026-10-05", "ESPECES", "Scolarite 1er versement");
        n += encaisser(eleves.get(2), 60_000, "2026-10-06", "VIREMENT", "Scolarite 1er versement");
        n += encaisser(eleves.get(3), 60_000, "2026-10-08", "MOBILE_MONEY", "Scolarite 1er versement");
        n += depenser("66110", "Salaires permanents octobre", 850_000, "2026-10-31", "Personnel enseignant");
        n += depenser("60531", "Facture d'eau", 45_000, "2026-10-15", "SNE");
        n += depenser("60110", "Reparation electrique", 75_000, "2026-10-18", "Electricien Moussa");
        n += depenser("62810", "Internet et telephone", 60_000, "2026-10-20", "Airtel Tchad");

        // Novembre a janvier — le rythme s'installe
        n += encaisser(eleves.get(1), 40_000, "2026-11-04", "ESPECES", "Scolarite 1er versement");
        n += depenser("66110", "Salaires permanents novembre", 850_000, "2026-11-30", "Personnel enseignant");
        n += depenser("60470", "Fournitures de bureau", 35_000, "2026-11-12", "Papeterie du Centre");
        n += encaisser(eleves.get(0), 50_000, "2026-12-06", "ESPECES", "Scolarite 2e versement");
        n += depenser("66110", "Salaires permanents decembre", 850_000, "2026-12-31", "Personnel enseignant");
        n += recevoir("71824", "Don d'un partenaire", 500_000, "2027-01-15", "Association des Amis de l'Ecole");
        n += depenser("21830", "Achat de deux ordinateurs", 800_000, "2027-01-20", "Informatique Plus");

        // Fevrier a juin — fin d'annee
        n += encaisser(eleves.get(2), 60_000, "2027-02-10", "VIREMENT", "Scolarite 2e versement");
        n += depenser("62430", "Refection des salles", 250_000, "2027-03-14", "Entreprise Batir");
        n += encaisser(eleves.get(3), 60_000, "2027-04-08", "ESPECES", "Scolarite 2e versement");
        n += depenser("63840", "Sortie pedagogique", 120_000, "2027-05-20", "Transport Sahel");
        n += encaisser(eleves.get(0), 50_000, "2027-06-05", "ESPECES", "Scolarite solde");

        return n;
    }

    private int encaisser(Eleve eleve, double montant, String date, String mode, String libelle) {
        LocalDate jour = LocalDate.parse(date);
        Paiement p = new Paiement();
        p.setEleve(eleve);
        p.setMontantVerse(montant);
        p.setDatePaiement(jour.atStartOfDay());
        p.setTypePaiement("SCOLARITE");
        p.setModePaiement(mode);
        p.setRecuNumero(String.format("HF-%s-%03d", annee, journal.size() + 1));
        p.setDescription(libelle);
        p.setAnneeScolaire(annee);
        paiementRepository.saveAndFlush(p);

        journal.add(String.join(";", jour.format(JOUR), "ENCAISSEMENT", libelle,
            eleve.getNom() + " " + eleve.getPrenom(), mode, String.valueOf(Math.round(montant))));
        return 1;
    }

    private int depenser(String code, String designation, double montant, String date, String beneficiaire) {
        return ecrireDepense(code, designation, montant, date, beneficiaire, "CHARGE", "DEPENSE");
    }

    private int recevoir(String code, String designation, double montant, String date, String origine) {
        return ecrireDepense(code, designation, montant, date, origine, "PRODUIT", "RECETTE");
    }

    private int ecrireDepense(String code, String designation, double montant, String date,
                              String tiers, String sens, String libelleJournal) {
        LocalDate jour = LocalDate.parse(date);
        CategorieComptable poste = categorieRepository
            .findByEtablissementIdAndActifTrueOrderByCodeAsc(ecole.getId()).stream()
            .filter(c -> code.equals(c.getCode())).findFirst().orElseThrow();

        Depense d = new Depense();
        d.setDesignation(designation);
        d.setCategorieComptable(poste);
        d.setMontant(montant);
        d.setDateDepense(jour);
        d.setSens(sens);
        d.setBeneficiaire(tiers);
        d.setStatut("PAYE");
        d.setAnneeScolaire(annee);
        d.setEtablissementId(ecole.getId());
        depenseRepository.saveAndFlush(d);

        journal.add(String.join(";", jour.format(JOUR), libelleJournal + " " + code,
            designation, tiers, "—", String.valueOf(Math.round(montant))));
        return 1;
    }

    // ── Production des fichiers ─────────────────────────────────────────

    private void ecrireJeuDeDonnees(int nbOperations, Balance annuelle, Balance mensuelle) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("JEU DE TEST COMPTABLE — EduSystem Pro\n");
        sb.append("=".repeat(72)).append("\n\n");
        sb.append("Etablissement  : ").append(ecole.getNom()).append("\n");
        sb.append("Code d'acces   : ").append(ecole.getCodeAcces()).append("\n");
        sb.append("Annee scolaire : ").append(annee).append("\n");
        sb.append("Operations     : ").append(nbOperations).append("\n");
        sb.append("Eleves         : ").append(eleves.size()).append(" (dont 2 avec remise)\n\n");

        sb.append("CONTROLE ANNUEL (01/09/2026 au 31/08/2027)\n");
        sb.append("-".repeat(72)).append("\n");
        sb.append(ligneControle(annuelle)).append("\n\n");

        sb.append("CONTROLE MENSUEL (octobre 2026)\n");
        sb.append("-".repeat(72)).append("\n");
        sb.append(ligneControle(mensuelle)).append("\n\n");

        sb.append("JOURNAL DES ").append(nbOperations).append(" OPERATIONS\n");
        sb.append("-".repeat(72)).append("\n");
        sb.append("Date;Nature;Libelle;Tiers;Mode;Montant\n");
        journal.forEach(l -> sb.append(l).append("\n"));

        sb.append("\nCe fichier est regenere a chaque execution de JeuDeTestComptableTest.\n");
        sb.append("Relancez ce test plutot que de ressaisir des donnees a la main.\n");

        Files.writeString(DOSSIER.resolve("jeu-de-donnees.txt"), sb.toString(), StandardCharsets.UTF_8);
    }

    private String ligneControle(Balance b) {
        return "Comptes mouvementes : " + b.lignes().size() + "\n"
             + "Total debit         : " + fmt(b.totalDebit()) + " FCFA\n"
             + "Total credit        : " + fmt(b.totalCredit()) + " FCFA\n"
             + "Resultat            : " + fmt(b.resultat()) + " FCFA ("
             + (b.estBeneficiaire() ? "excedent" : "deficit") + ")";
    }

    private void ecrireBalanceCsv(Balance b, String nom) throws IOException {
        StringBuilder sb = new StringBuilder("Compte;Intitule;Sens;Ecritures;Debit;Credit;Solde\n");
        b.lignes().forEach(l -> sb.append(String.join(";",
            l.code(), l.libelle(), l.sens(), String.valueOf(l.nbEcritures()),
            String.valueOf(Math.round(l.totalDebit())),
            String.valueOf(Math.round(l.totalCredit())),
            String.valueOf(Math.round(l.solde())))).append("\n"));
        sb.append(String.join(";", "", "TOTAUX", "", "",
            String.valueOf(Math.round(b.totalDebit())),
            String.valueOf(Math.round(b.totalCredit())),
            String.valueOf(Math.round(b.resultat())))).append("\n");
        Files.writeString(DOSSIER.resolve(nom), sb.toString(), StandardCharsets.UTF_8);
    }

    private void ecrireGrandLivreCsv(List<CompteDetaille> comptes, String nom) throws IOException {
        StringBuilder sb = new StringBuilder("Compte;Intitule;Date;Libelle;Tiers;Debit;Credit;Solde cumule\n");
        for (CompteDetaille c : comptes) {
            for (var e : c.ecritures()) {
                sb.append(String.join(";",
                    c.code(), c.libelle(),
                    e.date() != null ? e.date().format(JOUR) : "",
                    e.libelle() != null ? e.libelle() : "",
                    e.tiers() != null ? e.tiers() : "",
                    String.valueOf(Math.round(e.debit())),
                    String.valueOf(Math.round(e.credit())),
                    String.valueOf(Math.round(e.soldeCumule())))).append("\n");
            }
        }
        Files.writeString(DOSSIER.resolve(nom), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String fmt(double montant) {
        return String.format("%,d", Math.round(montant)).replace(',', ' ');
    }

    // ── Mode d'emploi du dossier ────────────────────────────────────────

    /**
     * Le mode d'emploi du dossier etait tenu a la main a cote des fichiers produits. Ses
     * chiffres de controle cessaient d'etre vrais des que le jeu de donnees bougeait, sans
     * que rien ne le signale : celui qui comparait ses totaux a ceux des CSV pouvait croire
     * a une erreur de calcul la ou seul le mode d'emploi avait vieilli. Il est desormais
     * reecrit a chaque execution, avec les totaux et le chemin du projet en cours.
     */
    private void ecrireLisezMoi(int nbOperations, Balance annuelle, Balance mensuelle) throws IOException {
        String projet = Paths.get("").toAbsolutePath().toString();
        String texte = """
            DOSSIER TEST COMPTABLE — EduSystem Pro
            ========================================================================

            A quoi sert ce dossier
            ------------------------------------------------------------------------
            Il evite de ressaisir des donnees a chaque fois qu'il faut verifier la
            comptabilite. Il contient une ecole de demonstration deja remplie de
            %d operations reparties sur une annee scolaire complete, le guide destine
            a la comptable, et les documents que ces operations produisent.


            Ce que contient le dossier
            ------------------------------------------------------------------------
            LISEZ-MOI.txt                    Ce mode d'emploi, reecrit a chaque execution.
            guide-comptable.pdf              Le guide, 4 pages, a imprimer ou a remettre
                                             a la comptable.
            guide-comptable.html             La meme chose, consultable au navigateur.

            jeu-de-donnees.txt               Les %d operations et les totaux de controle.

            balance-annee-%s.csv      Balance generale sur l'annee complete.
            balance-octobre-2026.csv         Balance du seul mois d'octobre.
            grand-livre-annee-%s.csv  Detail des ecritures sur l'annee.
            grand-livre-octobre-2026.csv     Detail des ecritures d'octobre.

            Les fichiers CSV s'ouvrent dans Excel ou LibreOffice (separateur : ;).


            Regenerer le dossier
            ------------------------------------------------------------------------
            Depuis « %s » :

                mvnw test -Dtest=JeuDeTestComptableTest

            Le test recree les %d operations, verifie que la balance correspond bien
            au grand livre sur le mois et sur l'annee, puis reecrit tous les fichiers
            de ce dossier. Les donnees sont ensuite annulees : la base reste propre.


            Consulter le jeu de test dans l'application
            ------------------------------------------------------------------------
            Pour retrouver ces donnees a l'ecran plutot que dans des fichiers :

                mvnw test -Dtest=JeuDeTestComptableTest -Dcomptable.persister=true

            L'ecole de demonstration est alors conservee en base, avec son compte :

                Adresse       %s
                Mot de passe  %s
                Role          COMPTABLE

            Ce compte ouvre Finances, Frais de scolarite, Suivi des familles, Grand
            livre, Balance generale, et l'inscription d'un eleve. Il ne donne acces
            ni aux salaires du personnel, ni au budget previsionnel, ni aux
            parametres de l'etablissement : ces trois adresses repondent 403.

            Ces droits ne viennent pas du role mais des modules financiers du compte,
            fixes ici a : %s.

            Les droits par defaut du role COMPTABLE sont differents (pas de caisse,
            mais la paie et le budget) : un compte cree a la main dans Parametres >
            Roles doit donc etre regle avant qu'on lui remette le guide.

            Attention : chaque execution avec -Dcomptable.persister=true ajoute une
            nouvelle ecole de demonstration. Supprimez la precedente depuis l'espace
            Super-admin si vous ne voulez pas les accumuler.


            Ce que le jeu de test valide
            ------------------------------------------------------------------------
            1. La balance est le recapitulatif exact du grand livre : memes totaux,
               memes comptes mouvementes. Si ces deux documents divergeaient, aucun
               ne serait fiable.
            2. Le mois est contenu dans l'annee : ses totaux ne peuvent pas la depasser.
            3. Le resultat est bien l'ecart entre credit et debit.
            4. Les %d operations couvrent les cas reels d'une ecole : encaissements de
               rentree, versements etales, salaires mensuels, charges de fonctionnement,
               un investissement, un don, deux remises (enfant du personnel, fratrie).

            Chiffres releves a la derniere execution :

                Annee %s   %2d comptes   debit %9s   credit %9s
                Octobre 2026      %2d comptes   debit %9s   credit %9s

            Resultat de l'annee : %s FCFA (%s).

            Les salaires d'une annee pesent plus que les encaissements de cinq
            eleves : ce deficit est celui du jeu de donnees, pas un defaut de calcul.
            """.formatted(
                nbOperations, nbOperations, annee, annee, projet, nbOperations,
                COMPTE_DEMO, MOT_DE_PASSE_DEMO,
                MODULES_DEMO.stream().sorted().collect(java.util.stream.Collectors.joining(", ")),
                nbOperations,
                annee, annuelle.lignes().size(), fmt(annuelle.totalDebit()), fmt(annuelle.totalCredit()),
                mensuelle.lignes().size(), fmt(mensuelle.totalDebit()), fmt(mensuelle.totalCredit()),
                fmt(annuelle.resultat()), annuelle.estBeneficiaire() ? "excedent" : "deficit");

        Files.writeString(DOSSIER.resolve("LISEZ-MOI.txt"), texte, StandardCharsets.UTF_8);
    }

    // ── Guide PDF ───────────────────────────────────────────────────────

    private void ecrireGuidePdf() throws IOException {
        String html = construireGuideHtml();
        Files.writeString(DOSSIER.resolve("guide-comptable.html"), html, StandardCharsets.UTF_8);

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(sortie);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Le guide PDF n'a pas pu etre produit.", e);
        }
        Files.write(DOSSIER.resolve("guide-comptable.pdf"), sortie.toByteArray());
    }

    /**
     * Le guide s'adresse a la comptable, pas au developpeur : il ne contient donc plus le
     * compte rendu du jeu de test — les 25 operations, les comptes mouvementes et les totaux
     * de controle. Ces chiffres restent dans jeu-de-donnees.txt et dans les exports CSV, ou
     * ils servent a verifier ; ils n'apprenaient rien a qui doit tenir une caisse.
     */
    private String construireGuideHtml() {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"/><style>
            @page { size: A4; margin: 18mm 16mm; }
            body { font-family: Helvetica, Arial, sans-serif; font-size: 10pt; color: #1a1d2b; line-height: 1.45; }
            h1 { font-size: 21pt; color: #00236f; margin: 0 0 4pt; }
            h2 { font-size: 13pt; color: #00236f; margin: 18pt 0 6pt; border-bottom: 1px solid #00236f; padding-bottom: 3pt; }
            h3 { font-size: 10.5pt; margin: 12pt 0 4pt; }
            .sous { color: #5b6478; font-size: 10pt; margin: 0 0 14pt; }
            .encadre { background: #eef1fa; border-left: 3px solid #00236f; padding: 8pt 10pt; margin: 10pt 0; }
            .alerte { background: #fbf0d9; border-left: 3px solid #8a5a00; padding: 8pt 10pt; margin: 10pt 0; }
            table { width: 100%%; border-collapse: collapse; margin: 6pt 0 12pt; font-size: 8.5pt; }
            th { text-align: left; background: #00236f; color: #fff; padding: 4pt 5pt; font-size: 8pt; }
            td { padding: 3.5pt 5pt; border-bottom: 0.5pt solid #d9dde8; }
            .m { text-align: right; white-space: nowrap; }
            .n, .code { color: #5b6478; }
            .g { color: #5b6478; }
            ol { margin: 4pt 0 10pt 14pt; padding: 0; }
            li { margin-bottom: 3pt; }
            .pied { margin-top: 16pt; padding-top: 6pt; border-top: 0.5pt solid #d9dde8; color: #5b6478; font-size: 8pt; }
            .saut { page-break-before: always; }
            .cle { display: inline-block; background: #eef1fa; padding: 1pt 4pt; font-family: monospace; font-size: 8.5pt; }
            </style></head><body>

            <h1>Guide de la comptable</h1>
            <p class="sous">EduSystem Pro — %s, annee %s</p>

            <div class="encadre">
              <b>Ce guide est le votre.</b> Il decrit, dans l'ordre ou vous les rencontrerez,
              les gestes de la comptabilite d'une ecole : ouvrir l'annee, encaisser, enregistrer
              une depense, et sortir les documents que l'on vous demandera.
            </div>

            <h2>1. Se connecter et s'y retrouver</h2>
            <p>La comptable recoit un compte cree depuis <b>Personnel</b> avec la fonction
            <b>COMPTABLE</b>. La fonction seule ne decide de rien : ce sont les
            <b>modules financiers</b> du compte, regles par l'administrateur dans
            <b>Parametres &gt; Roles &gt; Acces aux interfaces</b>, qui ouvrent ou ferment
            chaque ecran. Le compte livre avec ce jeu de test en a recu trois — <b>Caisse</b>,
            <b>Depenses</b>, <b>Rapports</b> — et c'est cette comptable-la que le guide decrit.</p>
            <div class="alerte">
              Un compte COMPTABLE cree sans reglage part avec l'inverse : pas de caisse, mais la
              preparation de la paie et le budget previsionnel. Le tableau ci-dessous ne vaut
              donc que pour les trois modules indiques. Avant de remettre ce guide, verifiez
              dans Parametres &gt; Roles que le compte porte bien Caisse, Depenses et Rapports.
            </div>
            <p>A la connexion, elle arrive directement sur <span class="cle">/finances</span>.
            Son menu comprend : Tableau de bord, Eleves, Comptabilite, Frais de scolarite,
            Suivi des familles, Grand livre, Balance generale, Messagerie, Personnel et
            Journal d'activite. L'entree <b>Personnel</b> donne les fiches en lecture seule :
            les salaires, eux, restent fermes.</p>

            <table>
              <tr><th>Elle peut</th><th>Elle ne peut pas</th></tr>
              <tr><td>Creer et modifier les frais</td><td>Consulter les salaires du personnel</td></tr>
              <tr><td>Inscrire un eleve</td><td>Modifier ou supprimer un dossier eleve</td></tr>
              <tr><td>Encaisser, delivrer un recu</td><td>Arbitrer le budget previsionnel</td></tr>
              <tr><td>Accorder une remise, planifier un echeancier</td><td>Regler les taux de paie</td></tr>
              <tr><td>Editer le grand livre et la balance</td><td>Acceder aux parametres de l'etablissement</td></tr>
            </table>

            <h2>2. Ouvrir l'annee</h2>
            <ol>
              <li><b>Saisir le solde de caisse d'ouverture</b> — Finances, onglet Parametrage.
                  Sans lui, tous les soldes affiches partent de zero et sont faux des le premier jour.</li>
              <li><b>Definir les frais</b> — ecran <span class="cle">/frais</span>. Un frais marque
                  obligatoire alimente le tableau des impayes et declenche les rappels ; un frais
                  facultatif (la cantine, par exemple) n'y figure pas.</li>
              <li><b>Le plan comptable est deja pret</b> — les 56 postes SYSCOHADA sont crees a
                  l'ouverture de l'ecole. Aucune saisie n'est necessaire, et l'ecran qui les
                  modifie releve du module Budget &amp; parametrage, qu'elle n'a pas. Elle
                  retrouve ces postes la ou elle s'en sert : au moment de rattacher une
                  depense, puis dans le grand livre et la balance.</li>
            </ol>

            <h2>3. Les gestes du quotidien</h2>
            <h3>Encaisser</h3>
            <p>Finances, onglet Scolarite. Choisir l'eleve, le montant, le mode de reglement.
            <b>Laisser le numero de recu vide</b> : le suivant est attribue automatiquement au
            format <span class="cle">HF-2026-2027-001</span>. Un numero deja delivre est refuse.</p>
            <div class="alerte">
              La suppression d'un paiement ne libere pas son numero. La sequence peut donc
              presenter des trous : c'est voulu, un numero remis a une famille reste consomme.
            </div>

            <h3>Accorder une remise</h3>
            <p>Ecran <b>Suivi des familles</b>. Pourcentage ou montant fixe, sur un frais precis ou
            sur l'ensemble, avec un motif. Un eleve exonere a 100 %% porte la mention
            <b>Exonere</b> et ne recoit plus de rappel.</p>
            <div class="alerte">
              Une remise vaut pour une seule annee scolaire. Elle ne se reconduit pas : il faut
              la reaccorder a chaque rentree.
            </div>

            <h3>Planifier un echeancier</h3>
            <p>Meme ecran. Indiquer le nombre de versements et la date du premier ; laisser le
            montant vide pour etaler ce qui reste du, remises deduites. Les versements sont
            mensuels. Chaque encaissement s'impute ensuite tout seul, du plus ancien au plus
            recent — rien a pointer a la main.</p>

            <h3>Enregistrer une depense</h3>
            <p>Finances, onglet Depenses. Rattacher chaque sortie a un poste du plan comptable et
            <b>joindre la piece justificative</b> : elle sera consultable depuis le grand livre,
            a la ligne de l'ecriture.</p>

            <h2>4. Les controles</h2>
            <ol>
              <li><b>Chaque semaine</b> — comptage de caisse : le comptage physique se compare au
                  solde theorique.</li>
              <li><b>Chaque mois</b> — editer la balance du mois, verifier que le total des charges
                  correspond aux depenses engagees.</li>
              <li><b>Chaque trimestre</b> — parcourir le suivi des familles : les eleves en retard
                  d'echeance remontent en tete de liste.</li>
              <li><b>En fin d'annee</b> — editer le grand livre et la balance de l'annee complete,
                  et les remettre a l'expert-comptable.</li>
            </ol>


            <div class="pied">
              EduSystem Pro — guide de la comptable. Le detail de chaque ecran figure dans le
              tutoriel d'utilisation du logiciel.
            </div>
            </body></html>
            """.formatted(echapper(ecole.getNom()), annee);
    }

    private static String echapper(String texte) {
        if (texte == null) return "";
        return texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
