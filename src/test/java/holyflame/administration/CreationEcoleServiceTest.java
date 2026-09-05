package holyflame.administration;

import holyflame.administration.controller.DemarrageRapideController;
import holyflame.administration.controller.InscriptionEcoleController.DonneesInscription;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.CreationEcoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'assistant complet demande une vingtaine de reglages sur quatre ecrans avant de livrer une
 * ecole. L'ecran de demarrage rapide n'en demande que quatre et laisse le service appliquer les
 * memes valeurs par defaut : ces tests verrouillent cette equivalence.
 */
@SpringBootTest
@Transactional
class CreationEcoleServiceTest {

    @Autowired private CreationEcoleService service;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ClasseRepository classeRepository;

    /** Reproduit ce que le controleur /demarrer construit a partir des quatre champs saisis. */
    private DonneesInscription depuisDemarrageRapide(String nom, String codeType) {
        DemarrageRapideController.TypeEcole type = DemarrageRapideController.TYPES.get(codeType);
        assertNotNull(type, "type d'ecole inconnu : " + codeType);
        DonneesInscription d = new DonneesInscription();
        d.nom = nom;
        d.categorie = codeType;
        d.niveaux = type.libelle();
        d.niveauxSelectionnes = new ArrayList<>(type.niveaux());
        return d;
    }

    @Test
    void unePrimaireEstCreeeAvecSesSixClassesEtLesDefautsDeLAssistant() {
        var resultat = service.creer(depuisDemarrageRapide("Ecole Les Baobabs", "PRIMAIRE"),
            "Moussa SARR", "direction@baobabs.test", "ADMIN");

        Etablissement etab = etablissementRepository.findAll().stream()
            .filter(e -> "Ecole Les Baobabs".equals(e.getNom()))
            .findFirst().orElseThrow();

        // Aucun de ces reglages n'est demande a l'ecran : ils doivent valoir ce que
        // l'assistant complet aurait applique si l'utilisateur avait tout laisse vide.
        assertEquals("ACTIF", etab.getStatut());
        assertEquals("NUMERIQUE", etab.getSystemeNotation());
        assertEquals(75, etab.getSeuilAssiduite());
        assertEquals("#00236f", etab.getCouleurPrimaire());
        assertEquals("Francais", etab.getLangueSysteme());
        assertNotNull(etab.getAnneeScolaire());
        assertTrue(etab.getCodeAcces().startsWith("HF-"), "format du code d'acces conserve");

        // Le choix "Ecole primaire" cree les six niveaux : plus besoin de cocher 22 cases.
        List<Classe> classes = classeRepository.findByEtablissementId(etab.getId());
        assertEquals(6, classes.size(), "CP1 a CM2 doivent etre crees automatiquement");
        assertEquals(6, resultat.nbClassesCreees(), "le compte est remonte a l'ecran de confirmation");
        assertTrue(classes.stream().anyMatch(c -> "CP1 A".equals(c.getNom())));
        assertTrue(classes.stream().anyMatch(c -> "CM2 A".equals(c.getNom())));
        assertTrue(classes.stream().allMatch(c -> etab.getAnneeScolaire().equals(c.getAnneeScolaire())),
            "les classes suivent l'annee scolaire de l'etablissement");

        Utilisateur admin = utilisateurRepository.findByEmail("direction@baobabs.test").orElseThrow();
        assertEquals("ADMIN", admin.getRole());
        assertEquals("SARR", admin.getNom());
        assertEquals("Moussa", admin.getPrenom());
        assertEquals(etab.getId(), admin.getEtablissement().getId());
    }

    @Test
    void leMotDePasseTemporaireExclutLesCaracteresAmbigus() {
        var resultat = service.creer(depuisDemarrageRapide("Ecole Lisible", "COLLEGE"),
            "Awa CISSE", "lisible@test.sn", "ADMIN");

        String mdp = resultat.motDePasse();
        assertEquals(10, mdp.length());
        assertTrue(mdp.chars().noneMatch(c -> "Il1O0".indexOf(c) >= 0),
            "ce mot de passe est lu a l'ecran puis retape : I, l, 1, O et 0 sont exclus");
    }

    @Test
    void unEmailDejaUtiliseEstRefuseSansRienCreer() {
        service.creer(depuisDemarrageRapide("Premiere Ecole", "COLLEGE"),
            "Awa CISSE", "doublon@test.sn", "ADMIN");
        long avant = etablissementRepository.count();

        var refus = assertThrows(CreationEcoleService.CreationRefusee.class, () ->
            service.creer(depuisDemarrageRapide("Seconde Ecole", "LYCEE"),
                "Awa CISSE", "doublon@test.sn", "ADMIN"));

        assertTrue(refus.getMessage().toLowerCase().contains("email"),
            "le message doit designer le champ fautif");
        assertEquals(avant, etablissementRepository.count(),
            "aucun etablissement ne doit rester derriere un refus");
        assertFalse(etablissementRepository.findAll().stream()
            .anyMatch(e -> "Seconde Ecole".equals(e.getNom())));
    }

    @Test
    void leNomDeLEcoleEstObligatoire() {
        DonneesInscription sansNom = depuisDemarrageRapide("Ecole", "PRIMAIRE");
        sansNom.nom = "   ";

        var refus = assertThrows(CreationEcoleService.CreationRefusee.class, () ->
            service.creer(sansNom, "Moussa SARR", "sansnom@test.sn", "ADMIN"));
        assertTrue(refus.getMessage().toLowerCase().contains("nom"));
    }

    @Test
    void leGroupeScolaireCreeToutLeCursus() {
        var resultat = service.creer(depuisDemarrageRapide("Groupe Scolaire Complet", "GROUPE_SCOLAIRE"),
            "Fatou NDIAYE", "gs@test.sn", "ADMIN");

        assertEquals(16, resultat.nbClassesCreees(),
            "maternelle (3) + primaire (6) + college (4) + lycee (3)");
    }

    @Test
    void chaqueTypeProposeALEcranCreeAuMoinsUneClasse() {
        // Un type mal renseigne produirait une ecole sans aucune classe, donc inutilisable
        // immediatement — exactement ce que cet ecran promet d'eviter.
        int i = 0;
        for (var entree : DemarrageRapideController.TYPES.entrySet()) {
            var resultat = service.creer(depuisDemarrageRapide("Ecole " + entree.getKey(), entree.getKey()),
                "Test USER", "type" + (i++) + "@test.sn", "ADMIN");
            assertTrue(resultat.nbClassesCreees() > 0,
                "le type " + entree.getKey() + " doit creer des classes");
            assertEquals(entree.getValue().niveaux().size(), resultat.nbClassesCreees(),
                "toutes les classes annoncees a l'ecran pour " + entree.getKey() + " doivent exister");
        }
    }
}
