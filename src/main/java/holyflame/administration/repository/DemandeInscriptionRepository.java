package holyflame.administration.repository;

import holyflame.administration.model.DemandeInscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DemandeInscriptionRepository extends JpaRepository<DemandeInscription, Long> {

    List<DemandeInscription> findByEtablissementIdOrderByDateDemandeDesc(Long etablissementId);

    List<DemandeInscription> findByEtablissementIdAndStatutOrderByDateDemandeDesc(
        Long etablissementId, String statut);

    /** Ce qui attend une decision : c'est le seul chiffre qui merite une pastille au menu. */
    long countByEtablissementIdAndStatutIn(Long etablissementId, List<String> statuts);

    Optional<DemandeInscription> findByReference(String reference);

    boolean existsByReference(String reference);

    /**
     * Le meme enfant, deja demande cette annee par le meme numero.
     *
     * Une famille qui n'est pas sure que son envoi est parti recommence. Sans ce controle,
     * le secretariat recevrait trois fois la meme demande et rappellerait trois fois.
     */
    boolean existsByEtablissementIdAndNomCompletIgnoreCaseAndParentTelephoneAndAnneeScolaire(
        Long etablissementId, String nomComplet, String parentTelephone, String anneeScolaire);
}
