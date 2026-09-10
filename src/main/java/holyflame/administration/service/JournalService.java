package holyflame.administration.service;

import holyflame.administration.model.JournalAction;
import holyflame.administration.repository.JournalActionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class JournalService {

    @Autowired private JournalActionRepository journalRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private HorlogeService horlogeService;

    public void log(String action, String module, String detail) {
        enregistrer(action, module, detail, etablissementService.getCurrentEtablissementId());
    }

    /**
     * Trace une action du super-administrateur dans son propre journal.
     *
     * Le super-admin agit sur les etablissements sans en diriger aucun : suspendre une ecole,
     * reinitialiser le mot de passe de son admin, la supprimer definitivement. Ces actes
     * n'etaient jusqu'ici traces nulle part. Ils ne doivent pas l'etre non plus dans le journal
     * de l'ecole concernee, que son admin consulte : d'une part ce n'est pas lui qui a agi,
     * d'autre part le journal d'un etablissement disparait avec lui — la trace d'une
     * suppression s'effacerait donc avec ce qu'elle documente.
     *
     * L'entree est donc rattachee a aucun etablissement, et l'ecole visee est nommee dans le
     * detail. C'est ce qui la rend lisible depuis « Journal du super-admin » et invisible
     * partout ailleurs, la vue de chaque ecole filtrant sur son propre identifiant.
     */
    public void logSuperAdmin(String action, String detail) {
        enregistrer(action, "SUPER_ADMIN", detail, null);
    }

    private void enregistrer(String action, String module, String detail, Long etablissementId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth != null ? auth.getName() : "système";

            JournalAction entry = new JournalAction();
            entry.setUtilisateurEmail(email);
            entry.setUtilisateurNom(email);
            entry.setAction(action);
            entry.setModule(module);
            entry.setDetail(detail);
            entry.setEtablissementId(etablissementId);
            entry.setDate(horlogeService.maintenant());
            journalRepository.save(entry);
        } catch (Exception ignored) {
            // Ne jamais bloquer l'action principale à cause du journal
        }
    }
}
