package holyflame.administration.repository;

import holyflame.administration.model.JournalAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public interface JournalActionRepository extends JpaRepository<JournalAction, Long> {
    List<JournalAction> findByEtablissementIdOrderByDateDesc(Long etablissementId, PageRequest pr);
    List<JournalAction> findByEtablissementIdAndModuleOrderByDateDesc(Long etablissementId, String module, PageRequest pr);
    long countByEtablissementId(Long etablissementId);
    long countByEtablissementIdAndModule(Long etablissementId, String module);
    void deleteByEtablissementId(Long etablissementId);

    // Journal du super-administrateur : ses actions ne sont rattachees a aucun etablissement,
    // pour rester invisibles dans le journal des ecoles et survivre a la suppression de l'une
    // d'elles — la trace d'une suppression ne doit pas disparaitre avec ce qu'elle documente.
    // Le module est filtre en plus de l'absence d'etablissement : toute action journalisee hors
    // contexte d'ecole (inscription publique, tache systeme) porte elle aussi un etablissement
    // nul, et n'a rien a faire dans le journal du super-administrateur.
    List<JournalAction> findByModuleAndEtablissementIdIsNullOrderByDateDesc(String module, PageRequest pr);
    List<JournalAction> findByModuleAndEtablissementIdIsNullAndActionOrderByDateDesc(String module, String action, PageRequest pr);
    long countByModuleAndEtablissementIdIsNull(String module);
    long countByModuleAndEtablissementIdIsNullAndAction(String module, String action);

    // Vue restreinte a un seul compte (tous les roles non-admin ne voient que leurs propres actions)
    List<JournalAction> findByEtablissementIdAndUtilisateurEmailOrderByDateDesc(Long etablissementId, String utilisateurEmail, PageRequest pr);
    List<JournalAction> findByEtablissementIdAndModuleAndUtilisateurEmailOrderByDateDesc(Long etablissementId, String module, String utilisateurEmail, PageRequest pr);
    long countByEtablissementIdAndUtilisateurEmail(Long etablissementId, String utilisateurEmail);
    long countByEtablissementIdAndModuleAndUtilisateurEmail(Long etablissementId, String module, String utilisateurEmail);
}
