package holyflame.administration;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.service.Fonctionnalites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le registre decide qui peut quoi. Tout le reste — la securite, les menus, le tableau des
 * acces — se contente de le lire. Ce qui se verifie ici n'est donc pas un detail
 * d'implementation : c'est la porte de l'etablissement.
 */
@DisplayName("Registre des fonctionnalites")
class RegistreFonctionnalitesTest {

    private static final Path CONFIG = Paths.get("src", "main", "java", "holyflame",
        "administration", "config", "SecurityConfig.java");

    private Utilisateur compte(String role) {
        Utilisateur u = new Utilisateur();
        u.setRole(role);
        return u;
    }

    @Nested
    @DisplayName("Le catalogue")
    class Catalogue {

        @Test
        void chaqueFonctionnaliteSeDitEnClair() {
            List<String> muettes = new ArrayList<>();
            for (var f : Fonctionnalites.catalogue().values()) {
                if (f.libelle() == null || f.libelle().isBlank()) muettes.add(f.code());
                if (f.domaine() == null || f.domaine().isBlank()) muettes.add(f.code() + " (sans domaine)");
            }
            // Une ligne sans libelle est une case a cocher dont personne ne sait ce qu'elle
            // ouvre. L'administrateur coche alors au hasard, ou n'y touche pas.
            assertTrue(muettes.isEmpty(), "fonctionnalites sans libelle ou sans domaine : " + muettes);
        }

        @Test
        void chaqueFonctionnaliteAppartientAUnDomaineAffiche() {
            Set<String> affiches = Fonctionnalites.parDomaine().keySet();
            List<String> orphelines = Fonctionnalites.catalogue().values().stream()
                .filter(f -> !affiches.contains(f.domaine()))
                .map(f -> f.code() + " dans « " + f.domaine() + " »").toList();

            // Un domaine absent de parDomaine() ne s'afficherait dans aucun onglet du tableau :
            // la fonctionnalite existerait, serait verifiee par la securite, et resterait
            // impossible a cocher.
            assertTrue(orphelines.isEmpty(),
                "ces fonctionnalites n'apparaitraient dans aucun bloc du tableau : " + orphelines);
        }

        @Test
        void leTableauNAffichePasDeBlocVide() {
            for (var bloc : Fonctionnalites.parDomaine().entrySet()) {
                assertFalse(bloc.getValue().isEmpty(),
                    "le domaine « " + bloc.getKey() + " » serait affiche sans aucune ligne");
            }
        }
    }

    @Nested
    @DisplayName("Ce que chaque role permet par defaut")
    class Defauts {

        @Test
        void leDirecteurNeTouchePasALaFinance() {
            Set<String> acces = Fonctionnalites.defautsPourRole("DIRECTEUR");
            for (String finance : List.of(Fonctionnalites.FIN_CAISSE, Fonctionnalites.FIN_DEPENSES,
                    Fonctionnalites.FIN_BUDGET, Fonctionnalites.FIN_PAIE_PREPARATION,
                    Fonctionnalites.FIN_PAIE_PAIEMENT, Fonctionnalites.FIN_RAPPORTS,
                    Fonctionnalites.FIN_COMPTABILITE, Fonctionnalites.FIN_SUIVI_FAMILLES)) {
                assertFalse(acces.contains(finance),
                    "le Directeur ne dirige jamais la finance, pas meme en lecture : " + finance);
            }
        }

        @Test
        void leComptableNeTientPasLaCaisseEtNeDeclenchePasLesPaiements() {
            Set<String> acces = Fonctionnalites.defautsPourRole("COMPTABLE");

            assertTrue(acces.contains(Fonctionnalites.FIN_DEPENSES), "il enregistre les depenses");
            assertTrue(acces.contains(Fonctionnalites.FIN_PAIE_PREPARATION), "et prepare la paie");
            // Celui qui calcule un bulletin ne doit pas etre celui qui sort l'argent : c'est la
            // separation qui rend une fraude visible plutot que possible.
            assertFalse(acces.contains(Fonctionnalites.FIN_CAISSE), "mais ne tient pas la caisse");
            assertFalse(acces.contains(Fonctionnalites.FIN_PAIE_PAIEMENT),
                "et ne declenche pas le decaissement qu'il a lui-meme calcule");
        }

        @Test
        void lesFamillesNeVoientRienDeLEtablissement() {
            for (String famille : List.of("PARENT", "ELEVE")) {
                Set<String> acces = Fonctionnalites.defautsPourRole(famille);
                assertFalse(acces.contains(Fonctionnalites.TABLEAU_BORD), famille + " et le budget");
                assertFalse(acces.contains(Fonctionnalites.RECHERCHE), famille + " et l'annuaire");
                assertFalse(acces.contains(Fonctionnalites.SECRETARIAT), famille + " et les dossiers");
                assertFalse(acces.contains(Fonctionnalites.PERSONNEL_CONSULTER), famille + " et le personnel");
            }
        }

        @Test
        void chaqueRoleDuPersonnelConsulteSesProprePaieEtSonJournal() {
            for (String role : List.of("DIRECTEUR", "SECRETAIRE", "ENSEIGNANT", "TRESORIER",
                    "COMPTABLE", "COORDONNATEUR", "SURVEILLANT", "INFIRMIER", "MARKETING")) {
                Set<String> acces = Fonctionnalites.defautsPourRole(role);
                assertTrue(acces.contains(Fonctionnalites.MES_BULLETINS),
                    role + " doit pouvoir consulter ses propres bulletins de paie");
                assertTrue(acces.contains(Fonctionnalites.JOURNAL),
                    role + " doit pouvoir retrouver ses propres actions");
            }
        }

        @Test
        void leCoordonnateurEstLePointDeDepartDesPostesUniversitaires() {
            Set<String> acces = Fonctionnalites.defautsPourRole("COORDONNATEUR");
            // Chef de departement, responsable de parcours, president de jury : aucun de ces
            // postes n'est un role du logiciel. Ils se composent a partir de celui-ci.
            assertTrue(acces.contains(Fonctionnalites.UNIV_MAQUETTE), "il tient la maquette");
            assertTrue(acces.contains(Fonctionnalites.UNIV_RELEVE), "et edite les releves");
        }
    }

    @Nested
    @DisplayName("Ce qu'une personne peut reellement faire")
    class Effectives {

        @Test
        void sansPersonnalisationCEstLeRoleQuiDecide() {
            Utilisateur secretaire = compte("SECRETAIRE");
            assertEquals(Fonctionnalites.defautsPourRole("SECRETAIRE"),
                Fonctionnalites.effectives(secretaire));
        }

        @Test
        void unePersonnalisationRemplaceLesDefautsAuLieuDeSAjouter() {
            Utilisateur secretaire = compte("SECRETAIRE");
            secretaire.setFonctionnalitesActives(new LinkedHashSet<>(List.of(Fonctionnalites.ARCHIVES)));

            Set<String> effectives = Fonctionnalites.effectives(secretaire);
            assertTrue(effectives.contains(Fonctionnalites.ARCHIVES));
            // Retirer un droit doit marcher aussi bien qu'en ajouter un. Si la selection
            // s'ajoutait aux defauts, on ne pourrait jamais restreindre personne.
            assertFalse(effectives.contains(Fonctionnalites.SECRETARIAT),
                "ce qui n'est pas coche n'est pas accorde, meme si le role le donnait");
        }

        @Test
        void uneSelectionVideNeRouvrePasLesDroitsDuRole() {
            Utilisateur secretaire = compte("SECRETAIRE");
            secretaire.setFonctionnalitesActives(Set.of());

            // « Rien de coche » veut dire « cette personne ne peut rien faire », et non
            // « revenir aux defauts » — sinon vider le tableau rendrait tous les droits.
            assertTrue(Fonctionnalites.effectives(secretaire).isEmpty(),
                "tout decocher doit tout retirer, pas tout rendre");
        }

        @Test
        void reinitialiserRamenneAuxDroitsDuRole() {
            Utilisateur secretaire = compte("SECRETAIRE");
            secretaire.setFonctionnalitesActives(Set.of());
            secretaire.reinitialiserAcces();

            assertFalse(secretaire.isAccesPersonnalise());
            assertEquals(Fonctionnalites.defautsPourRole("SECRETAIRE"),
                Fonctionnalites.effectives(secretaire));
        }

        @Test
        void ceQuiNeSeDeleguePasNeSObtientPasEnCochant() {
            Utilisateur secretaire = compte("SECRETAIRE");
            secretaire.setFonctionnalitesActives(
                new LinkedHashSet<>(List.of(Fonctionnalites.PARAMETRES, Fonctionnalites.PASSAGE_CLOTURE)));

            Set<String> effectives = Fonctionnalites.effectives(secretaire);
            // Le formulaire vient du navigateur : rien n'empeche d'y ajouter une case. Qui
            // obtiendrait les parametres pourrait s'attribuer tout le reste ensuite.
            assertFalse(effectives.contains(Fonctionnalites.PARAMETRES),
                "les parametres ne se cochent pas, meme en forgeant le formulaire");
            assertFalse(effectives.contains(Fonctionnalites.PASSAGE_CLOTURE),
                "la cloture de fin d'annee non plus : elle est irreversible");
        }

        @Test
        void lAdministrateurNePeutPasSeFermerLaPorte() {
            Utilisateur admin = compte("ADMIN");
            admin.setFonctionnalitesActives(Set.of());

            // Sans cette garantie, un etablissement pourrait se verrouiller hors de ses
            // propres parametres, sans aucun moyen de revenir en arriere.
            assertTrue(Fonctionnalites.effectives(admin).contains(Fonctionnalites.PARAMETRES),
                "un administrateur garde toujours de quoi rouvrir le tableau des acces");
        }
    }

    @Nested
    @DisplayName("Accord avec la securite")
    class AccordSecurite {

        private String securite() throws IOException {
            return Files.readString(CONFIG, StandardCharsets.UTF_8);
        }

        private Set<String> codesCitesParLaSecurite() throws IOException {
            Set<String> codes = new LinkedHashSet<>();
            Matcher m = Pattern.compile("Fonctionnalites\\.([A-Z_]+)").matcher(securite());
            while (m.find()) codes.add(m.group(1));
            return codes;
        }

        @Test
        void laSecuriteNeCiteQueDesFonctionnalitesExistantes() throws IOException {
            List<String> inconnues = new ArrayList<>();
            for (String code : codesCitesParLaSecurite()) {
                // Le registre expose aussi des constantes de domaine (D_FINANCE...) : on ne
                // retient que ce qui pretend etre une fonctionnalite.
                if (code.startsWith("D_") || code.equals("NON_ATTRIBUABLES")) continue;
                if (!Fonctionnalites.existe(code)) inconnues.add(code);
            }
            // Une regle qui cite un code inexistant refuse tout le monde, en silence : l'ecran
            // devient inatteignable sans qu'aucune erreur ne le signale.
            assertTrue(inconnues.isEmpty(),
                "SecurityConfig verrouille des ecrans derriere des codes qui n'existent pas : " + inconnues);
        }

        @Test
        void plusAucuneRegleNeReposeSurUnRoleMetier() throws IOException {
            String source = securite();
            // Les espaces personnels (eleve, parent, enseignant) gardent leur regle de role :
            // ils n'exposent que les donnees de la personne connectee, il n'y a rien a doser.
            for (String role : List.of("TRESORIER", "COMPTABLE", "COORDONNATEUR", "SURVEILLANT",
                    "INFIRMIER", "MARKETING", "DIRECTEUR")) {
                assertFalse(source.contains("hasRole(\"" + role + "\")"),
                    role + " ne doit plus decider seul d'un acces : c'est le tableau qui decide");
            }
        }

        @Test
        void lesEcransSensiblesRestentDerriereLeurPropreFonctionnalite() throws IOException {
            String source = securite();
            assertTrue(source.contains("requestMatchers(\"/parametres/**\").access(peut(Fonctionnalites.PARAMETRES))"),
                "les comptes et les roles ne se delegent pas");
            assertTrue(source.contains("Fonctionnalites.FIN_PAIE_PAIEMENT"),
                "declencher un decaissement de paie garde sa propre porte");
            assertTrue(source.contains("requestMatchers(\"/export/paie/excel\").access(peut(Fonctionnalites.FIN_PAIE_PREPARATION))"),
                "l'export de la paie expose tous les salaires : il suit la paie, pas la regle generique");
        }
    }
}
