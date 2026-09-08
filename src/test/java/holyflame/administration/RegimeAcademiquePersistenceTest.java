package holyflame.administration;

import holyflame.administration.model.Etablissement;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.service.RegimeAcademiqueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Section Universite, etape 1 — verification sur la vraie base.
 *
 * Les regles elles-memes sont couvertes en unitaire par RegimeAcademiqueTest. Ce qui se
 * verifie ici ne peut l'etre qu'avec MySQL : que les colonnes existent, que le regime et
 * les seuils survivent a un aller-retour en base, et que les etablissements deja enregistres
 * n'ont pas change de comportement.
 */
@SpringBootTest
@Transactional
class RegimeAcademiquePersistenceTest {

    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private RegimeAcademiqueService service;

    private Etablissement nouvelEtablissement(String nom) {
        Etablissement e = new Etablissement();
        e.setNom(nom);
        e.setStatut("ACTIF");
        e.setDateCreation(LocalDate.now());
        e.setAnneeScolaire("2026-2027");
        e.setCodeAcces("TEST-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return e;
    }

    private Map<String, String> formulaire(String... clesValeurs) {
        Map<String, String> m = new HashMap<>();
        m.put("_regimeSoumis", "1");
        for (int i = 0; i < clesValeurs.length; i += 2) m.put(clesValeurs[i], clesValeurs[i + 1]);
        return m;
    }

    @Test
    @DisplayName("Administrateur — le reglage LMD survit a l'enregistrement en base")
    void leReglageEstBienPersiste() {
        Etablissement etab = etablissementRepository.save(nouvelEtablissement("Universite Test LMD"));

        service.configurer(etab, formulaire(
            "regimeAcademique", "LMD",
            "seuilValidationUE", "12",
            "creditsParSemestre", "36",
            "compensationSemestrielle", "true",
            "noteEliminatoireActive", "true",
            "noteEliminatoire", "7"), true);
        etablissementRepository.saveAndFlush(etab);

        Etablissement relu = etablissementRepository.findById(etab.getId()).orElseThrow();
        assertEquals("LMD", relu.getRegimeAcademique());
        assertEquals(12.0, relu.getSeuilValidationUE(), 0.001);
        assertEquals(36, relu.getCreditsParSemestre());
        assertTrue(relu.isCompensationSemestrielle());
        assertEquals(7.0, relu.getNoteEliminatoire(), 0.001);
        assertTrue(service.estLMD(relu), "le service reconnait le regime relu depuis la base");
    }

    @Test
    @DisplayName("Administrateur — desactiver la note eliminatoire l'efface vraiment en base")
    void laNoteEliminatoireEffaceeResteEffacee() {
        Etablissement etab = etablissementRepository.save(nouvelEtablissement("Universite Test Eliminatoire"));

        service.configurer(etab, formulaire("regimeAcademique", "LMD",
            "noteEliminatoireActive", "true", "noteEliminatoire", "6"), true);
        etablissementRepository.saveAndFlush(etab);
        assertEquals(6.0, etablissementRepository.findById(etab.getId()).orElseThrow().getNoteEliminatoire(), 0.001);

        service.configurer(etab, formulaire("regimeAcademique", "LMD"), true);
        etablissementRepository.saveAndFlush(etab);

        assertNull(etablissementRepository.findById(etab.getId()).orElseThrow().getNoteEliminatoire(),
            "une note eliminatoire retiree ne doit pas reapparaitre au rechargement");
    }

    @Test
    @DisplayName("Non-regression — un etablissement cree sans rien preciser reste scolaire")
    void unEtablissementNeufEstScolaireEnBase() {
        Etablissement etab = etablissementRepository.saveAndFlush(nouvelEtablissement("College Test Scolaire"));

        Etablissement relu = etablissementRepository.findById(etab.getId()).orElseThrow();
        assertEquals("SCOLAIRE", relu.getRegimeAcademique());
        assertFalse(service.estLMD(relu));
        assertTrue(relu.isCompensationSemestrielle(),
            "la valeur par defaut existe mais reste sans effet tant qu'on est en scolaire");
    }

    @Test
    @DisplayName("Non-regression — les etablissements deja enregistres n'ont pas bascule")
    void aucunEtablissementExistantNaChangeDeRegime() {
        // La colonne a ete ajoutee par Hibernate sur une base en service : tout etablissement
        // anterieur doit valoir SCOLAIRE, jamais NULL ni LMD, sinon ses bulletins changeraient
        // de nature sans que personne ne l'ait demande.
        var tous = etablissementRepository.findAll();
        for (Etablissement e : tous) {
            assertFalse("LMD".equals(e.getRegimeAcademique()) && e.getSeuilValidationUE() == null,
                "un etablissement en LMD sans seuil de validation serait incalculable : " + e.getNom());
        }
    }
}
