package holyflame.administration.service;

import holyflame.administration.repository.*;
import holyflame.administration.util.AnneeScolaireUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AlerteService {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private HorlogeService horlogeService;

    public record Alerte(String niveau, String icone, String message, String lien) {}

    public List<Alerte> getAlertes(Long etabId) {
        List<Alerte> alertes = new ArrayList<>();
        if (etabId == null) return alertes;

        // 1. Élèves avec ≥ 5 absences ce mois
        int moisActuel = horlogeService.aujourdHui(etabId).getMonthValue();
        int anneeActuelle = horlogeService.aujourdHui(etabId).getYear();
        long elevesAbsents = absenceRepository.findByEtablissementId(etabId).stream()
            .filter(a -> a.getDate() != null
                && a.getDate().getMonthValue() == moisActuel
                && a.getDate().getYear() == anneeActuelle)
            .collect(java.util.stream.Collectors.groupingBy(
                a -> a.getEleve() != null ? a.getEleve().getId() : -1L,
                java.util.stream.Collectors.counting()))
            .entrySet().stream()
            .filter(e -> e.getValue() >= 5)
            .count();

        if (elevesAbsents > 0) {
            alertes.add(new Alerte("danger", "bi-exclamation-triangle-fill",
                elevesAbsents + " élève(s) avec ≥ 5 absences ce mois", "/surveillance"));
        }

        // 2. Eleves de l'annee scolaire active n'ayant encore rien verse.
        //
        // Ce comptage se faisait auparavant sur l'annee CIVILE et sur l'effectif total de
        // l'etablissement, toutes annees scolaires confondues. Deux consequences : les eleves
        // partis les annees precedentes etaient comptes comme mauvais payeurs a perpetuite, et
        // le 1er janvier l'alerte repartait de zero puisque les versements de septembre a
        // decembre tombaient dans l'annee civile precedente. Le comptage suit desormais
        // l'annee scolaire, des deux cotes.
        String anneeActive = anneeScolaireActive(etabId);
        List<holyflame.administration.model.Eleve> elevesAnneeActive = eleveRepository
            .findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(e -> e.getClasse() == null || anneeActive.equals(e.getClasse().getAnneeScolaire()))
            .toList();
        Set<Long> idsAnneeActive = elevesAnneeActive.stream()
            .map(holyflame.administration.model.Eleve::getId)
            .collect(java.util.stream.Collectors.toSet());

        Set<Long> ontVerse = paiementRepository.findByEtablissementId(etabId).stream()
            .filter(p -> p.getEleve() != null && idsAnneeActive.contains(p.getEleve().getId()))
            .filter(p -> anneeScolaireDuPaiement(p).equals(anneeActive))
            .map(p -> p.getEleve().getId())
            .collect(java.util.stream.Collectors.toSet());

        long sansVersement = idsAnneeActive.size() - ontVerse.size();
        long totalEleves = eleveRepository.countByEtablissementId(etabId);
        if (sansVersement > 0) {
            alertes.add(new Alerte("warning", "bi-cash-coin",
                sansVersement + " élève(s) sans versement pour " + anneeActive, "/finances"));
        }

        // 3. Fin d'année scolaire dans moins de 30 jours
        parametreRepository.findByCleAndEtablissementId("T3_FIN", etabId).ifPresent(p -> {
            try {
                String[] parts = p.getValeur().split("/");
                LocalDate fin = LocalDate.of(Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                long jours = java.time.temporal.ChronoUnit.DAYS.between(horlogeService.aujourdHui(etabId), fin);
                if (jours >= 0 && jours <= 30) {
                    alertes.add(new Alerte("info", "bi-calendar-event",
                        "Fin d'année dans " + jours + " jour(s) — pensez aux bulletins", "/bulletins"));
                }
            } catch (Exception ignored) {}
        });

        // 4. Données vides (nouvel établissement)
        if (totalEleves == 0) {
            alertes.add(new Alerte("info", "bi-info-circle",
                "Aucun élève enregistré — commencez par le Secrétariat", "/secretariat"));
        }

        return alertes;
    }

    /** Annee scolaire de l'etablissement vise, sans dependre de l'utilisateur connecte. */
    private String anneeScolaireActive(Long etabId) {
        return etablissementRepository.findById(etabId)
            .map(holyflame.administration.model.Etablissement::getAnneeScolaire)
            .filter(a -> a != null && !a.isBlank())
            .orElseGet(() -> AnneeScolaireUtil.pour(horlogeService.aujourdHui(etabId)));
    }

    /**
     * Annee scolaire portee par le paiement. Les paiements enregistres avant l'ajout de ce champ
     * ne la renseignent pas : on la deduit alors de la date de versement.
     */
    private String anneeScolaireDuPaiement(holyflame.administration.model.Paiement p) {
        if (p.getAnneeScolaire() != null && !p.getAnneeScolaire().isBlank()) return p.getAnneeScolaire();
        return p.getDatePaiement() != null
            ? AnneeScolaireUtil.pour(p.getDatePaiement().toLocalDate())
            : "";
    }
}
