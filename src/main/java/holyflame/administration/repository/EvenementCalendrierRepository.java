package holyflame.administration.repository;

import holyflame.administration.model.EvenementCalendrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvenementCalendrierRepository extends JpaRepository<EvenementCalendrier, Long> {
    List<EvenementCalendrier> findByEtablissementIdAndAnneeScolaireOrderByDateAsc(Long etablissementId, String anneeScolaire);
}
