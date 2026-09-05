package holyflame.administration;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Paiement;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.PersonnelRepository;
import holyflame.administration.service.AlerteService;
import holyflame.administration.service.HorlogeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * L'alerte "eleves sans versement" du tableau de bord ADMIN comptait tous les eleves de
 * l'etablissement, toutes annees scolaires confondues, contre les seuls paiements de
 * l'annee CIVILE en cours. Les anciens eleves etaient donc signales a perpetuite, et au
 * 1er janvier les versements de septembre a decembre sortaient du comptage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlerteServiceTest {

    @Mock private EleveRepository eleveRepository;
    @Mock private AbsenceRepository absenceRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private PersonnelRepository personnelRepository;
    @Mock private ParametreRepository parametreRepository;
    @Mock private EtablissementRepository etablissementRepository;
    @Mock private HorlogeService horlogeService;

    @InjectMocks private AlerteService alerteService;

    private static final Long ETAB = 1L;
    private static final String ANNEE_ACTIVE = "2025-2026";

    private Eleve eleve(long id, String anneeClasse) {
        Eleve e = new Eleve();
        e.setId(id);
        e.setNom("ELEVE" + id);
        e.setPrenom("Test");
        if (anneeClasse != null) {
            Classe c = new Classe();
            c.setId(id);
            c.setNom("6eme A");
            c.setAnneeScolaire(anneeClasse);
            e.setClasse(c);
        }
        return e;
    }

    private Paiement paiement(Eleve e, String anneeScolaire, LocalDateTime date) {
        Paiement p = new Paiement();
        p.setEleve(e);
        p.setAnneeScolaire(anneeScolaire);
        p.setDatePaiement(date);
        p.setMontantVerse(50_000.0);
        return p;
    }

    @BeforeEach
    void preparerEtablissement() {
        Etablissement etab = new Etablissement();
        etab.setId(ETAB);
        etab.setNom("Ecole Test");
        etab.setAnneeScolaire(ANNEE_ACTIVE);
        when(etablissementRepository.findById(ETAB)).thenReturn(Optional.of(etab));
        // Janvier : le piege de l'ancien calcul, qui basculait d'annee civile a cette date.
        when(horlogeService.aujourdHui(any())).thenReturn(LocalDate.of(2026, 1, 15));
        when(absenceRepository.findByEtablissementId(ETAB)).thenReturn(List.of());
        when(parametreRepository.findByCleAndEtablissementId("T3_FIN", ETAB)).thenReturn(Optional.empty());
    }

    @Test
    void lesElevesDesAnneesPrecedentesNeSontPlusComptesCommeMauvaisPayeurs() {
        Eleve actuel = eleve(1L, ANNEE_ACTIVE);
        Eleve ancien = eleve(2L, "2023-2024");

        when(eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(ETAB))
            .thenReturn(List.of(actuel, ancien));
        when(eleveRepository.countByEtablissementId(ETAB)).thenReturn(2L);
        when(paiementRepository.findByEtablissementId(ETAB)).thenReturn(List.of());

        List<AlerteService.Alerte> alertes = alerteService.getAlertes(ETAB);

        AlerteService.Alerte versements = alertes.stream()
            .filter(a -> a.message().contains("sans versement"))
            .findFirst().orElseThrow();
        assertTrue(versements.message().startsWith("1 élève(s)"),
            "seul l'eleve de l'annee active doit etre compte, pas celui de 2023-2024 : " + versements.message());
        assertTrue(versements.message().contains(ANNEE_ACTIVE),
            "le message nomme l'annee scolaire concernee");
    }

    @Test
    void unVersementDeSeptembreCompteEncoreEnJanvier() {
        Eleve actuel = eleve(1L, ANNEE_ACTIVE);

        when(eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(ETAB))
            .thenReturn(List.of(actuel));
        when(eleveRepository.countByEtablissementId(ETAB)).thenReturn(1L);
        // Verse en septembre 2025, donc dans l'annee civile precedente mais bien dans 2025-2026.
        when(paiementRepository.findByEtablissementId(ETAB))
            .thenReturn(List.of(paiement(actuel, ANNEE_ACTIVE, LocalDateTime.of(2025, 9, 20, 10, 0))));

        List<AlerteService.Alerte> alertes = alerteService.getAlertes(ETAB);

        assertEquals(0, alertes.stream().filter(a -> a.message().contains("sans versement")).count(),
            "un eleve ayant paye en septembre ne doit pas ressortir comme impaye en janvier");
    }

    @Test
    void unPaiementAncienSansAnneeScolaireEstRattacheParSaDate() {
        Eleve actuel = eleve(1L, ANNEE_ACTIVE);

        when(eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(ETAB))
            .thenReturn(List.of(actuel));
        when(eleveRepository.countByEtablissementId(ETAB)).thenReturn(1L);
        // Donnee anterieure a l'ajout du champ anneeScolaire sur Paiement.
        when(paiementRepository.findByEtablissementId(ETAB))
            .thenReturn(List.of(paiement(actuel, null, LocalDateTime.of(2025, 10, 3, 9, 30))));

        List<AlerteService.Alerte> alertes = alerteService.getAlertes(ETAB);

        assertEquals(0, alertes.stream().filter(a -> a.message().contains("sans versement")).count(),
            "l'annee scolaire est deduite de la date quand le champ n'est pas renseigne");
    }
}
