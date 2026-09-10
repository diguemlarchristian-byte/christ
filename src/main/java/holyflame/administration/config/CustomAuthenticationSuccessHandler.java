package holyflame.administration.config;

import holyflame.administration.model.Personnel;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.PersonnelRepository;
import holyflame.administration.repository.UtilisateurRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ParametreRepository parametreRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String email = authentication.getName();
        Utilisateur user = utilisateurRepository.findByEmail(email).orElse(null);

        if (user == null) {
            response.sendRedirect("/dashboard");
            return;
        }

        if (user.getTentativesEchouees() != 0 || user.getVerrouilleJusqua() != null) {
            user.setTentativesEchouees(0);
            user.setVerrouilleJusqua(null);
            utilisateurRepository.save(user);
        }

        String role = user.getRole();
        Long etabId = user.getEtablissement() != null ? user.getEtablissement().getId() : null;

        switch (role == null ? "" : role) {
            case "SUPER_ADMIN" -> response.sendRedirect("/super-admin");
            case "DIRECTEUR"   -> response.sendRedirect("/dashboard");
            case "ENSEIGNANT"  -> response.sendRedirect("/tableau-enseignant");
            case "SECRETAIRE"  -> response.sendRedirect("/secretariat");
            case "SURVEILLANT" -> response.sendRedirect("/surveillant");
            case "INFIRMIER"   -> response.sendRedirect("/infirmerie");
            case "TRESORIER", "COMPTABLE" -> response.sendRedirect("/finances");
            case "COORDONNATEUR" -> response.sendRedirect("/coordination");
            // MARKETING etait le seul role sans destination declaree : il tombait sur le cas par
            // defaut et arrivait au tableau de bord de l'administration, ou figurent le total
            // encaisse et le budget, alors que son espace — le site vitrine — existe depuis
            // toujours a /marketing avec ses sept ecrans.
            case "MARKETING" -> response.sendRedirect("/marketing");
            case "ELEVE"       -> response.sendRedirect("/portail");
            case "PARENT"      -> response.sendRedirect("/portail-parent");
            case "ADMIN" -> {
                // Vérifier si cet admin est aussi le directeur avec délégation active
                boolean delegue = etabId != null && parametreRepository
                    .findByCleAndEtablissementId("DELEGATION_DIRECTION", etabId)
                    .map(p -> "true".equals(p.getValeur())).orElse(false);

                if (delegue && etabId != null) {
                    Personnel perso = personnelRepository
                        .findByEmailAndEtablissementId(email, etabId).orElse(null);
                    if (perso != null && "DIRECTEUR".equals(perso.getFonction())) {
                        response.sendRedirect("/direction/suivi");
                        return;
                    }
                }
                response.sendRedirect("/dashboard");
            }
            default -> response.sendRedirect("/dashboard");
        }
    }
}
