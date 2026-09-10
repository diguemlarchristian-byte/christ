package holyflame.administration.repository;

import holyflame.administration.model.Depense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long> {
    List<Depense> findByEtablissementIdOrderByDateDepenseDesc(Long etablissementId);

    // Une paie deja comptabilisee ne doit pas l'etre une seconde fois : c'est ce que verifie
    // le paiement avant d'ecrire quoi que ce soit dans les comptes.
    boolean existsBySalaireMensuelId(Long salaireMensuelId);
    List<Depense> findBySalaireMensuelId(Long salaireMensuelId);
}
