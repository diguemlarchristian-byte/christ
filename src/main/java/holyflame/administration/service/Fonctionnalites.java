package holyflame.administration.service;

import holyflame.administration.model.Utilisateur;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le catalogue de tout ce que le logiciel sait faire, en une seule liste.
 *
 * Avant ce registre, les droits se decidaient a trois endroits differents : le role, une
 * liste de « modules optionnels » reservee au Directeur, et une liste de modules financiers
 * reservee au Tresorier et au Comptable. Trois mecanismes, trois logiques d'autorisation
 * separees, et aucun ecran ou lire ce qu'une personne peut reellement faire.
 *
 * Une grande universite ne tient pas dans treize roles figes. Un Doyen, un Secretaire
 * General Academique, un Chef de departement, un President de jury, un agent d'apparitorat
 * n'ont pas les memes attributions d'un etablissement a l'autre — et dans beaucoup d'entre
 * eux, une seule personne porte plusieurs de ces casquettes. Multiplier les roles pour
 * couvrir ces cas reviendrait a multiplier les regles de securite sans jamais tomber juste.
 *
 * Le role ne fait donc plus que proposer un point de depart raisonnable. Ce que la personne
 * peut faire se coche, fonctionnalite par fonctionnalite, dans un tableau unique.
 *
 * Ce fichier est la source de verite : la securite s'y refere pour autoriser, l'ecran des
 * acces s'en sert pour dessiner le tableau, et un test verifie que les deux parlent bien
 * des memes codes.
 */
public final class Fonctionnalites {

    /**
     * Une fonctionnalite du logiciel.
     *
     * @param code        identifiant stable, ecrit en base — ne jamais le renommer
     * @param domaine     regroupement affiche dans le tableau des acces
     * @param libelle     ce que la personne pourra faire, dit en clair
     * @param precision   ce qu'il faut savoir avant de cocher, ou null
     * @param attribuable false pour ce qui ne se delegue pas (voir NON_ATTRIBUABLES)
     */
    public record Fonctionnalite(String code, String domaine, String libelle, String precision,
                                 boolean attribuable) {
    }

    // ── Domaines, dans l'ordre d'affichage du tableau ────────────────────────────
    public static final String D_SCOLARITE = "Scolarité et dossiers";
    public static final String D_PEDAGOGIE = "Pédagogie et évaluations";
    public static final String D_UNIVERSITE = "Enseignement supérieur (LMD)";
    public static final String D_VIE = "Vie scolaire";
    public static final String D_FINANCE = "Finances";
    public static final String D_PERSONNEL = "Personnel";
    public static final String D_COMMUNICATION = "Communication";
    public static final String D_LOGISTIQUE = "Logistique";
    public static final String D_PILOTAGE = "Pilotage et outils";
    public static final String D_ADMINISTRATION = "Administration du logiciel";

    // ── Scolarité ────────────────────────────────────────────────────────────────
    public static final String PREINSCRIPTIONS = "PREINSCRIPTIONS";
    public static final String ELEVES_INSCRIRE = "ELEVES_INSCRIRE";
    public static final String SECRETARIAT = "SECRETARIAT";
    public static final String ARCHIVES = "ARCHIVES";
    public static final String PASSAGE = "PASSAGE";
    public static final String PASSAGE_CLOTURE = "PASSAGE_CLOTURE";

    // ── Pédagogie ────────────────────────────────────────────────────────────────
    public static final String CLASSES = "CLASSES";
    public static final String SALLES = "SALLES";
    public static final String MATIERES = "MATIERES";
    public static final String ACADEMIQUE = "ACADEMIQUE";
    public static final String NOTES = "NOTES";
    public static final String EXAMENS = "EXAMENS";
    public static final String BULLETINS = "BULLETINS";
    public static final String EMPLOI_DU_TEMPS = "EMPLOI_DU_TEMPS";

    // ── Université ───────────────────────────────────────────────────────────────
    public static final String UNIV_MAQUETTE = "UNIV_MAQUETTE";
    public static final String UNIV_RELEVE = "UNIV_RELEVE";
    public static final String COORDINATION = "COORDINATION";

    // ── Vie scolaire ─────────────────────────────────────────────────────────────
    public static final String ABSENCES = "ABSENCES";
    public static final String PROGRAMMES = "PROGRAMMES";
    public static final String INFIRMERIE = "INFIRMERIE";

    // ── Finances ─────────────────────────────────────────────────────────────────
    public static final String FIN_CAISSE = "FIN_CAISSE";
    public static final String FIN_DEPENSES = "FIN_DEPENSES";
    public static final String FIN_PAIE_PREPARATION = "FIN_PAIE_PREPARATION";
    public static final String FIN_PAIE_PAIEMENT = "FIN_PAIE_PAIEMENT";
    public static final String FIN_BUDGET = "FIN_BUDGET";
    public static final String FIN_RAPPORTS = "FIN_RAPPORTS";
    public static final String FIN_FRAIS = "FIN_FRAIS";
    public static final String FIN_COMPTABILITE = "FIN_COMPTABILITE";
    public static final String FIN_SUIVI_FAMILLES = "FIN_SUIVI_FAMILLES";
    public static final String FIN_RECU = "FIN_RECU";

    // ── Personnel ────────────────────────────────────────────────────────────────
    public static final String PERSONNEL_CONSULTER = "PERSONNEL_CONSULTER";
    public static final String PERSONNEL_ENREGISTRER = "PERSONNEL_ENREGISTRER";
    public static final String PERSONNEL_GERER = "PERSONNEL_GERER";
    public static final String MES_BULLETINS = "MES_BULLETINS";
    public static final String MON_CONGE = "MON_CONGE";

    // ── Communication ────────────────────────────────────────────────────────────
    public static final String MESSAGERIE = "MESSAGERIE";
    public static final String COMMUNICATION = "COMMUNICATION";
    public static final String PUBLICATIONS = "PUBLICATIONS";
    public static final String MARKETING = "MARKETING";

    // ── Logistique ───────────────────────────────────────────────────────────────
    public static final String INVENTAIRE = "INVENTAIRE";

    // ── Pilotage ─────────────────────────────────────────────────────────────────
    public static final String TABLEAU_BORD = "TABLEAU_BORD";
    public static final String RECHERCHE = "RECHERCHE";
    public static final String JOURNAL = "JOURNAL";
    public static final String ASSISTANT = "ASSISTANT";
    public static final String DIRECTION = "DIRECTION";

    // ── Administration ───────────────────────────────────────────────────────────
    public static final String PARAMETRES = "PARAMETRES";

    /**
     * Ce qui ne se coche pas.
     *
     * Les parametres donnent la main sur les comptes et les roles : les deleguer
     * permettrait a n'importe qui de s'attribuer ensuite le reste, y compris la finance.
     * La cloture de fin d'annee est irreversible et affiche le bilan financier de
     * l'etablissement. Ces deux-la restent attaches au role ADMIN, et le tableau les
     * montre grises plutot que de les cacher : mieux vaut voir qu'une porte existe et
     * comprendre pourquoi elle est fermee.
     */
    public static final Set<String> NON_ATTRIBUABLES = Set.of(PARAMETRES, PASSAGE_CLOTURE);

    private static final Map<String, Fonctionnalite> CATALOGUE = new LinkedHashMap<>();

    private static void ajouter(String code, String domaine, String libelle, String precision) {
        CATALOGUE.put(code, new Fonctionnalite(code, domaine, libelle, precision,
            !NON_ATTRIBUABLES.contains(code)));
    }

    static {
        // ── Scolarité ────────────────────────────────────────────────────────────
        ajouter(PREINSCRIPTIONS, D_SCOLARITE, "Demandes de pré-inscription",
            "Lire et trier les demandes déposées par les familles depuis le site public de l'établissement.");
        ajouter(ELEVES_INSCRIRE, D_SCOLARITE, "Inscrire un nouvel élève",
            "Le parcours d'inscription complet. Souvent confié au guichet qui encaisse aussi le premier versement.");
        ajouter(SECRETARIAT, D_SCOLARITE, "Dossiers des élèves et secrétariat",
            "Consulter, modifier, classer les dossiers déjà enregistrés.");
        ajouter(ARCHIVES, D_SCOLARITE, "Archives et documents administratifs", null);
        ajouter(PASSAGE, D_SCOLARITE, "Passage de classe et réinscriptions", null);
        ajouter(PASSAGE_CLOTURE, D_SCOLARITE, "Assistant de clôture de fin d'année",
            "Irréversible, et affiche le bilan financier de l'établissement. Réservé à l'administrateur.");

        // ── Pédagogie ────────────────────────────────────────────────────────────
        ajouter(CLASSES, D_PEDAGOGIE, "Classes et promotions", null);
        ajouter(SALLES, D_PEDAGOGIE, "Salles", null);
        ajouter(MATIERES, D_PEDAGOGIE, "Matières et coefficients", null);
        ajouter(ACADEMIQUE, D_PEDAGOGIE, "Organisation académique",
            "Années scolaires, périodes, affectation des enseignants.");
        ajouter(NOTES, D_PEDAGOGIE, "Saisie et consultation des notes", null);
        ajouter(EXAMENS, D_PEDAGOGIE, "Examens et compositions", null);
        ajouter(BULLETINS, D_PEDAGOGIE, "Bulletins et conseils de classe", null);
        ajouter(EMPLOI_DU_TEMPS, D_PEDAGOGIE, "Emploi du temps", null);

        // ── Université ───────────────────────────────────────────────────────────
        ajouter(UNIV_MAQUETTE, D_UNIVERSITE, "Maquette de formation",
            "Parcours, unités d'enseignement, crédits, éléments constitutifs. Le travail d'un chef de département ou d'un responsable de parcours.");
        ajouter(UNIV_RELEVE, D_UNIVERSITE, "Relevés semestriels et délibération",
            "Éditer les relevés, le tableau de délibération et le cumul de l'année. Le travail d'un président de jury ou de l'apparitorat.");
        ajouter(COORDINATION, D_UNIVERSITE, "Espace de coordination",
            "Suivi d'une filière : effectifs, enseignants, avancement des programmes.");

        // ── Vie scolaire ─────────────────────────────────────────────────────────
        ajouter(ABSENCES, D_VIE, "Absences et retards", null);
        ajouter(PROGRAMMES, D_VIE, "Avancement des programmes de cours", null);
        ajouter(INFIRMERIE, D_VIE, "Infirmerie",
            "Données de santé des élèves. À ne confier qu'à qui en a réellement la charge.");

        // ── Finances ─────────────────────────────────────────────────────────────
        ajouter(FIN_CAISSE, D_FINANCE, "Caisse et encaissements",
            "Paiements, reçus, arriérés, rappels, comptage de caisse.");
        ajouter(FIN_DEPENSES, D_FINANCE, "Dépenses et décaissements", null);
        ajouter(FIN_PAIE_PREPARATION, D_FINANCE, "Préparer la paie",
            "Calculer les bulletins. Donne à voir tous les salaires de l'établissement.");
        ajouter(FIN_PAIE_PAIEMENT, D_FINANCE, "Payer la paie",
            "Déclencher le décaissement réel. Distinct de la préparation : c'est un acte de caisse.");
        ajouter(FIN_BUDGET, D_FINANCE, "Budget, plan comptable et taux de paie", null);
        ajouter(FIN_RAPPORTS, D_FINANCE, "Rapports et exports financiers", null);
        ajouter(FIN_FRAIS, D_FINANCE, "Frais de scolarité",
            "Les tarifs changent en cours d'année : c'est un travail quotidien, pas un paramétrage.");
        ajouter(FIN_COMPTABILITE, D_FINANCE, "Grand livre et balance",
            "Documents de lecture, sans effet sur les données.");
        ajouter(FIN_SUIVI_FAMILLES, D_FINANCE, "Remises et échéanciers des familles", null);
        ajouter(FIN_RECU, D_FINANCE, "Rééditer un reçu de paiement",
            "Pour rendre un reçu sans ouvrir le reste des finances — utile au secrétariat.");

        // ── Personnel ────────────────────────────────────────────────────────────
        ajouter(PERSONNEL_CONSULTER, D_PERSONNEL, "Consulter les fiches du personnel",
            "Lecture seule, sans les salaires.");
        ajouter(PERSONNEL_ENREGISTRER, D_PERSONNEL, "Enregistrer un nouveau membre du personnel",
            "Création uniquement. Ne donne pas la main sur les fiches existantes.");
        ajouter(PERSONNEL_GERER, D_PERSONNEL, "Gérer le personnel",
            "Fiches, contrats, congés, documents. Hors salaires, qui suivent les droits de la paie.");
        ajouter(MES_BULLETINS, D_PERSONNEL, "Consulter ses propres bulletins de paie",
            "Chacun ne voit que les siens, déduits de son compte.");
        ajouter(MON_CONGE, D_PERSONNEL, "Demander un congé pour soi-même",
            "L'approbation reste à la direction.");

        // ── Communication ────────────────────────────────────────────────────────
        ajouter(MESSAGERIE, D_COMMUNICATION, "Messagerie interne", null);
        ajouter(COMMUNICATION, D_COMMUNICATION, "Annonces et envois aux familles", null);
        ajouter(PUBLICATIONS, D_COMMUNICATION, "Publications et documents diffusés", null);
        ajouter(MARKETING, D_COMMUNICATION, "Site public de l'établissement",
            "Actualités, galerie, événements. N'accède à aucune donnée d'élève, de personnel ni de finances.");

        // ── Logistique ───────────────────────────────────────────────────────────
        ajouter(INVENTAIRE, D_LOGISTIQUE, "Inventaire du matériel",
            "Entrées, sorties, réparations, mises hors service.");

        // ── Pilotage ─────────────────────────────────────────────────────────────
        ajouter(TABLEAU_BORD, D_PILOTAGE, "Tableau de bord",
            "Affiche le total encaissé, le budget et les effectifs de l'établissement.");
        ajouter(RECHERCHE, D_PILOTAGE, "Recherche générale",
            "Parcourt l'annuaire des élèves et du personnel.");
        ajouter(JOURNAL, D_PILOTAGE, "Journal d'activité",
            "Chacun n'y voit que ses propres actions, sauf l'administrateur qui voit tout.");
        ajouter(ASSISTANT, D_PILOTAGE, "Assistant",
            "Ne voit jamais plus que ce que la personne peut déjà consulter ailleurs.");
        ajouter(DIRECTION, D_PILOTAGE, "Espace de direction", null);

        // ── Administration ───────────────────────────────────────────────────────
        ajouter(PARAMETRES, D_ADMINISTRATION, "Paramètres, comptes et rôles",
            "Donne la main sur tous les comptes. Ne se délègue pas : qui l'obtient peut s'attribuer le reste.");
    }

    public static Map<String, Fonctionnalite> catalogue() {
        return Collections.unmodifiableMap(CATALOGUE);
    }

    public static Fonctionnalite get(String code) {
        return CATALOGUE.get(code);
    }

    public static boolean existe(String code) {
        return CATALOGUE.containsKey(code);
    }

    /** Le catalogue regroupé par domaine, dans l'ordre d'affichage du tableau des accès. */
    public static Map<String, List<Fonctionnalite>> parDomaine() {
        Map<String, List<Fonctionnalite>> groupes = new LinkedHashMap<>();
        for (String domaine : List.of(D_SCOLARITE, D_PEDAGOGIE, D_UNIVERSITE, D_VIE, D_FINANCE,
                D_PERSONNEL, D_COMMUNICATION, D_LOGISTIQUE, D_PILOTAGE, D_ADMINISTRATION)) {
            groupes.put(domaine, CATALOGUE.values().stream()
                .filter(f -> f.domaine().equals(domaine)).toList());
        }
        return groupes;
    }

    /**
     * Ce que toute personne rattachée à l'établissement peut faire, quel que soit son poste :
     * consulter ses propres bulletins de paie, retrouver ses propres actions, voir le tableau
     * de bord, chercher dans l'annuaire, lire l'emploi du temps.
     *
     * La messagerie n'y figure pas. Elle n'est aujourd'hui ouverte qu'à une partie du
     * personnel, et l'ajouter ici l'aurait ouverte en silence à trois rôles de plus.
     * Étendre un accès sans que personne ne l'ait demandé est une décision, pas un détail :
     * elle se coche, comme le reste.
     */
    private static final Set<String> SOCLE_PERSONNEL = Set.of(
        MES_BULLETINS, JOURNAL, ASSISTANT, TABLEAU_BORD, RECHERCHE, EMPLOI_DU_TEMPS);

    private static final Map<String, Set<String>> DEFAUTS = new LinkedHashMap<>();

    private static void defaut(String role, String... codes) {
        Set<String> ensemble = new LinkedHashSet<>(Arrays.asList(codes));
        DEFAUTS.put(role, Collections.unmodifiableSet(ensemble));
    }

    private static String[] avecSocle(String... codes) {
        Set<String> ensemble = new LinkedHashSet<>(SOCLE_PERSONNEL);
        ensemble.addAll(Arrays.asList(codes));
        return ensemble.toArray(new String[0]);
    }

    static {
        // L'administrateur a tout, y compris ce qui ne se délègue pas.
        defaut("ADMIN", CATALOGUE.keySet().toArray(new String[0]));

        // Le Directeur dirige la pédagogie et le personnel. Jamais la finance : ni caisse,
        // ni dépenses, ni budget, ni salaires — pas même en lecture.
        defaut("DIRECTEUR", avecSocle(
            PREINSCRIPTIONS, PASSAGE, ACADEMIQUE, CLASSES, SALLES, MATIERES, NOTES, EXAMENS, BULLETINS,
            UNIV_RELEVE, PERSONNEL_CONSULTER, PERSONNEL_ENREGISTRER, PERSONNEL_GERER,
            MON_CONGE, MESSAGERIE, COMMUNICATION, PUBLICATIONS, ARCHIVES, DIRECTION));

        defaut("SECRETAIRE", avecSocle(
            PREINSCRIPTIONS, ELEVES_INSCRIRE, SECRETARIAT, ARCHIVES, PASSAGE, NOTES, EXAMENS, BULLETINS,
            UNIV_RELEVE, PERSONNEL_CONSULTER, PERSONNEL_ENREGISTRER, MESSAGERIE,
            COMMUNICATION, PUBLICATIONS, INVENTAIRE, FIN_RECU));

        defaut("ENSEIGNANT", avecSocle(
            NOTES, EXAMENS, BULLETINS, ABSENCES, PROGRAMMES, PERSONNEL_CONSULTER, MON_CONGE,
            MESSAGERIE));

        // Le Trésorier avait accès à toute la finance avant la segmentation : ce défaut
        // préserve le comportement des comptes déjà créés.
        defaut("TRESORIER", avecSocle(
            FIN_CAISSE, FIN_DEPENSES, FIN_PAIE_PREPARATION, FIN_PAIE_PAIEMENT, FIN_BUDGET,
            FIN_RAPPORTS, FIN_FRAIS, FIN_COMPTABILITE, FIN_SUIVI_FAMILLES, FIN_RECU,
            PERSONNEL_CONSULTER, MESSAGERIE));

        // Le Comptable enregistre et classe, prépare la paie, tient le plan comptable —
        // sans toucher à la caisse ni déclencher un décaissement.
        defaut("COMPTABLE", avecSocle(
            FIN_DEPENSES, FIN_PAIE_PREPARATION, FIN_BUDGET, FIN_RAPPORTS, FIN_FRAIS,
            FIN_COMPTABILITE, FIN_SUIVI_FAMILLES, FIN_RECU, PERSONNEL_CONSULTER, MESSAGERIE));

        // Le Coordonnateur suit une filière et sa maquette. C'est le rôle de départ le plus
        // proche d'un chef de département ou d'un responsable de parcours à l'université.
        defaut("COORDONNATEUR", avecSocle(
            COORDINATION, UNIV_MAQUETTE, UNIV_RELEVE, PASSAGE, PERSONNEL_CONSULTER, MESSAGERIE));

        defaut("SURVEILLANT", avecSocle(ABSENCES));
        defaut("INFIRMIER", avecSocle(INFIRMERIE));
        defaut("MARKETING", avecSocle(MARKETING));

        // Élèves et parents n'ont pas de socle personnel : leur espace leur est propre et
        // ne passe pas par ce registre.
        defaut("ELEVE");
        defaut("PARENT", BULLETINS, ASSISTANT);
        defaut("SUPER_ADMIN");
    }

    /** Ce que ce rôle permet quand personne n'a rien coché pour ce compte. */
    public static Set<String> defautsPourRole(String role) {
        return DEFAUTS.getOrDefault(role, Set.of());
    }

    public static Set<String> rolesConnus() {
        return Collections.unmodifiableSet(DEFAUTS.keySet());
    }

    /**
     * Ce que cette personne peut faire, réellement.
     *
     * Tant que personne n'a rien coché pour elle, c'est son rôle qui décide. Dès que
     * l'administrateur ouvre son tableau et enregistre, c'est la sélection cochée qui fait
     * foi — y compris si elle retire des droits que le rôle donnait.
     *
     * L'ADMIN échappe à la règle : il lui faut de quoi rouvrir un tableau qu'il aurait
     * lui-même vidé, sans quoi un établissement pourrait se fermer sa propre porte.
     */
    public static Set<String> effectives(Utilisateur u) {
        if (u == null) return Set.of();
        if ("ADMIN".equals(u.getRole()) || "SUPER_ADMIN".equals(u.getRole())) {
            return defautsPourRole(u.getRole());
        }
        if (u.isAccesPersonnalise()) {
            Set<String> choisies = new LinkedHashSet<>(u.getFonctionnalitesActives());
            // Ce qui ne se délègue pas ne s'obtient pas non plus par une case cochée.
            choisies.removeAll(NON_ATTRIBUABLES);
            return Collections.unmodifiableSet(choisies);
        }
        return defautsPourRole(u.getRole());
    }

    public static boolean autorise(Utilisateur u, String code) {
        return effectives(u).contains(code);
    }

    private Fonctionnalites() {
    }
}
