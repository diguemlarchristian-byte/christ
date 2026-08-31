package holyflame.administration.service;

import holyflame.administration.model.ClotureMensuelle;
import holyflame.administration.repository.ClotureMensuelleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Verrouillage comptable mois par mois, independant de la cloture d'annee scolaire (qui verrouille
 * tout d'un coup en fin d'annee) : permet a un Comptable d'arreter un mois deja verifie sans
 * attendre la fin de l'annee, evitant qu'une depense de septembre soit encore modifiable en juin.
 */
@Service
public class ClotureMensuelleService {

    @Autowired private ClotureMensuelleRepository clotureMensuelleRepository;

    public boolean estCloture(int mois, int anneeCivile, Long etabId) {
        if (etabId == null) return false;
        return clotureMensuelleRepository.findByEtablissementIdAndMoisAndAnneeCivile(etabId, mois, anneeCivile).isPresent();
    }

    /** A appeler en tete de toute creation/modification/suppression d'une depense datee. */
    public void verifierModifiable(LocalDate date, Long etabId) {
        if (date == null) return;
        if (estCloture(date.getMonthValue(), date.getYear(), etabId)) {
            throw new MoisClotureException(date.getMonthValue(), date.getYear());
        }
    }

    public List<ClotureMensuelle> lister(Long etabId) {
        return clotureMensuelleRepository.findByEtablissementIdOrderByAnneeCivileDescMoisDesc(etabId);
    }

    public void cloturer(int mois, int anneeCivile, Long etabId, Long utilisateurId) {
        if (estCloture(mois, anneeCivile, etabId)) return;
        ClotureMensuelle c = new ClotureMensuelle();
        c.setMois(mois);
        c.setAnneeCivile(anneeCivile);
        c.setEtablissementId(etabId);
        c.setDateCloture(LocalDate.now());
        c.setClotureParId(utilisateurId);
        clotureMensuelleRepository.save(c);
    }

    public void reouvrir(int mois, int anneeCivile, Long etabId) {
        clotureMensuelleRepository.findByEtablissementIdAndMoisAndAnneeCivile(etabId, mois, anneeCivile)
            .ifPresent(clotureMensuelleRepository::delete);
    }
}
