package holyflame.administration.config;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.UtilisateurDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.csrf.CsrfFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @Autowired
    private CustomAuthenticationFailureHandler failureHandler;

    @Autowired
    private UtilisateurDetailsService utilisateurDetailsService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    /**
     * Autorise les roles classiques passes en parametre, OU un compte DIRECTEUR
     * auquel l'ADMIN a explicitement active le module operationnel donne depuis
     * Parametres > Roles (cf. Utilisateur.modulesOptionnels). Permet a un
     * etablissement sous-effectif de confier ponctuellement un module operationnel
     * (secretariat, surveillance, infirmerie, marketing, inventaire, coordination)
     * a son Directeur, sans jamais toucher aux modules financiers.
     */
    private AuthorizationManager<RequestAuthorizationContext> roleOuModuleDirecteur(String moduleCode, String... rolesClassiques) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (auth == null || !auth.isAuthenticated()) return new AuthorizationDecision(false);
            boolean roleOk = Arrays.stream(rolesClassiques).anyMatch(r -> hasRole(auth, r));
            if (roleOk) return new AuthorizationDecision(true);
            if (!hasRole(auth, "DIRECTEUR")) return new AuthorizationDecision(false);
            Utilisateur u = utilisateurRepository.findByEmail(auth.getName()).orElse(null);
            boolean autorise = u != null && u.getModulesOptionnelsActifs().contains(moduleCode);
            return new AuthorizationDecision(autorise);
        };
    }

    /**
     * Autorise l'ADMIN, ou un compte TRESORIER/COMPTABLE dont les modules financiers
     * effectifs (personnalises par l'ADMIN depuis Parametres > Roles > Acces aux
     * interfaces, ou par defaut de son role sinon) incluent le module donne.
     * Voir holyflame.administration.service.FinanceModules.
     */
    private AuthorizationManager<RequestAuthorizationContext> financeAccess(String moduleCode) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (auth == null || !auth.isAuthenticated()) return new AuthorizationDecision(false);
            if (hasRole(auth, "ADMIN")) return new AuthorizationDecision(true);
            if (!hasRole(auth, "TRESORIER") && !hasRole(auth, "COMPTABLE")) return new AuthorizationDecision(false);
            Utilisateur u = utilisateurRepository.findByEmail(auth.getName()).orElse(null);
            boolean autorise = u != null && holyflame.administration.service.FinanceModules.autorise(u, moduleCode);
            return new AuthorizationDecision(autorise);
        };
    }

    /**
     * Inscription d'un nouvel eleve : Secretariat classique (ou Directeur avec le module
     * SECRETARIAT), OU Tresorier/Comptable avec le module CAISSE — l'inscription et le premier
     * versement se font souvent au meme guichet, sans repasser par le Secretariat.
     */
    private AuthorizationManager<RequestAuthorizationContext> inscriptionEleveAccess() {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (auth == null || !auth.isAuthenticated()) return new AuthorizationDecision(false);
            if (hasRole(auth, "ADMIN") || hasRole(auth, "SECRETAIRE")) return new AuthorizationDecision(true);
            Utilisateur u = null;
            if (hasRole(auth, "DIRECTEUR") || hasRole(auth, "TRESORIER") || hasRole(auth, "COMPTABLE")) {
                u = utilisateurRepository.findByEmail(auth.getName()).orElse(null);
            }
            if (u != null && hasRole(auth, "DIRECTEUR") && u.getModulesOptionnelsActifs().contains("SECRETARIAT")) {
                return new AuthorizationDecision(true);
            }
            if (u != null && (hasRole(auth, "TRESORIER") || hasRole(auth, "COMPTABLE"))
                    && holyflame.administration.service.FinanceModules.autorise(u, holyflame.administration.service.FinanceModules.CAISSE)) {
                return new AuthorizationDecision(true);
            }
            return new AuthorizationDecision(false);
        };
    }

    private boolean hasRole(Authentication auth, String role) {
        String authorite = "ROLE_" + role;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (authorite.equals(a.getAuthority())) return true;
        }
        return false;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login", "/error", "/inscription-ecole", "/inscription-ecole/**",
                    // Creation d'une ecole en un seul ecran : meme porte d'entree publique que
                    // l'assistant detaille /inscription-ecole, dont elle est la version courte.
                    "/demarrer",
                    "/inscription-parent", "/inscription-parent/**",
                    "/mot-de-passe-oublie", "/reinitialiser-mot-de-passe",
                    // Webhook CinetPay : appele serveur a serveur par CinetPay, sans session
                    // utilisateur. Aucune donnee sensible n'est lue depuis la requete elle-meme
                    // (voir PaiementMobileWebhookController) — seul un identifiant de transaction
                    // est accepte, et le vrai statut est toujours revérifié aupres de CinetPay.
                    "/paiements/mobile/notification",
                    // Site vitrine public d'un etablissement (active depuis l'espace MARKETING) —
                    // accessible sans compte, comme n'importe quel site web d'ecole.
                    "/ecole/**",
                    "/h2-console/**", "/css/**", "/js/**", "/fonts/**", "/images/**", "/uploads/**", "/webjars/**", "/assets/**").permitAll()
                .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                // Assistant de cloture de fin d'annee : reserve a l'ADMIN (action irreversible +
                // bilan financier affiche a l'etape finale — le Directeur ne doit jamais voir de
                // donnees financieres, meme en lecture, donc pas d'acces plus large ici).
                .requestMatchers("/passage/assistant/**").hasRole("ADMIN")
                // Inscription d'un nouvel eleve : ouverte au Secretariat classique, mais aussi au
                // Tresorier/Comptable ayant le module CAISSE — l'inscription et le premier versement
                // se font souvent au meme guichet, sans repasser par le Secretariat.
                .requestMatchers("/secretariat/eleves/nouveau", "/secretariat/eleves/nouveau/**")
                    .access(inscriptionEleveAccess())
                // Secretariat : role classique, ou DIRECTEUR si l'ADMIN lui a active le module SECRETARIAT
                .requestMatchers("/secretariat/**").access(roleOuModuleDirecteur("SECRETARIAT", "ADMIN", "SECRETAIRE"))
                .requestMatchers("/passage/**").hasAnyRole("ADMIN", "DIRECTEUR", "SECRETAIRE", "COORDONNATEUR")
                // Le Directeur ne dirige jamais la finance : ni tresorerie, ni budget, ni depenses.
                // Simple coquille de redirection vers /finances (voir TresorerieController) : la
                // vraie segmentation Tresorier/Comptable se joue sur les routes /finances/** ci-dessous.
                .requestMatchers("/tresorerie/**").hasAnyRole("ADMIN", "TRESORIER", "COMPTABLE")
                // Les frais de scolarite quittent Parametres, qui est reserve a l'ADMIN : c'est
                // le travail quotidien de la comptable, et les tarifs changent en cours d'annee.
                .requestMatchers("/frais/**").hasAnyRole("ADMIN", "TRESORIER")
                // Grand livre et balance : documents de lecture, sans effet sur les donnees.
                .requestMatchers("/comptabilite/**").hasAnyRole("ADMIN", "TRESORIER")
                // Remises et echeanciers : suivi financier des familles, coeur du poste comptable.
                .requestMatchers("/suivi-familles/**").hasAnyRole("ADMIN", "TRESORIER")
                .requestMatchers("/gestion-academique/**").hasAnyRole("ADMIN", "DIRECTEUR")
                .requestMatchers("/gestion-classes/**").hasAnyRole("ADMIN", "DIRECTEUR")
                .requestMatchers("/gestion-salles/**").hasAnyRole("ADMIN", "DIRECTEUR")
                .requestMatchers("/matieres/**").hasAnyRole("ADMIN", "DIRECTEUR")
                // Le secretariat peut enregistrer un nouveau personnel (creation uniquement,
                // pas de modification/suppression/documents/comptes des fiches existantes)
                .requestMatchers("/personnel/nouveau", "/personnel/nouveau/**").hasAnyRole("ADMIN", "DIRECTEUR", "SECRETAIRE")
                // Le tresorier et le secretariat consultent les fiches personnel (lecture seule),
                // sans acces a la modification/documents/comptes du personnel
                .requestMatchers(HttpMethod.GET, "/personnel", "/personnel/*").hasAnyRole("ADMIN", "DIRECTEUR", "ENSEIGNANT", "TRESORIER", "COMPTABLE", "SECRETAIRE", "COORDONNATEUR")
                // Gestion complete du personnel (fiches, contrats, conges) pour le Directeur — hors salaires (regle dediee ci-dessous)
                .requestMatchers("/personnel/**").hasAnyRole("ADMIN", "DIRECTEUR")
                // Le surveillant peut signaler/consulter des absences, mais pas gerer les programmes de cours
                .requestMatchers("/surveillance/programmes/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/surveillance", "/surveillance/absences/**").access(roleOuModuleDirecteur("SURVEILLANCE", "ADMIN", "ENSEIGNANT", "SURVEILLANT"))
                .requestMatchers("/surveillance/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/surveillant/**").access(roleOuModuleDirecteur("SURVEILLANCE", "ADMIN", "SURVEILLANT"))
                .requestMatchers("/infirmerie/**").access(roleOuModuleDirecteur("INFIRMERIE", "ADMIN", "INFIRMIER"))
                .requestMatchers("/notes/**").hasAnyRole("ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE")
                .requestMatchers("/examens/**").hasAnyRole("ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE")
                .requestMatchers("/bulletins/**").hasAnyRole("ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE", "PARENT")
                .requestMatchers("/portail-parent/**").hasAnyRole("ADMIN", "PARENT")
                .requestMatchers("/messagerie/**").hasAnyRole("ADMIN", "DIRECTEUR", "SECRETAIRE", "ENSEIGNANT", "TRESORIER", "COMPTABLE", "COORDONNATEUR")
                // Chaque export est restreint aux roles qui l'utilisent reellement dans l'interface :
                // l'export des paiements suit le module financier RAPPORTS (Tresorier/Comptable selon
                // ce que l'ADMIN leur a confie), notes/eleves sont aussi utilises depuis les pages
                // Examens (enseignant) et Secretariat (secretaire).
                .requestMatchers("/export/paiements/excel").access(financeAccess(holyflame.administration.service.FinanceModules.RAPPORTS))
                .requestMatchers("/export/rapports/excel").access(financeAccess(holyflame.administration.service.FinanceModules.RAPPORTS))
                .requestMatchers("/export/eleves/excel").hasAnyRole("ADMIN", "DIRECTEUR", "TRESORIER", "COMPTABLE", "SECRETAIRE")
                .requestMatchers("/export/notes/excel").hasAnyRole("ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE")
                .requestMatchers("/export/**").hasAnyRole("ADMIN", "TRESORIER", "COMPTABLE")
                // Comptes/roles et securite restent strictement reserves a l'ADMIN, y compris pour le
                // Directeur : sinon un Directeur pourrait s'auto-attribuer l'acces finance.
                .requestMatchers("/parametres/**").hasRole("ADMIN")
                .requestMatchers("/tableau-enseignant/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/tableau-eleve/**").hasAnyRole("ADMIN", "ELEVE")
                // Simple coquille de redirection vers /finances?tab=budget (voir BudgetController) :
                // la vraie segmentation se joue sur /finances/budget/** ci-dessous.
                .requestMatchers("/budget/**").hasAnyRole("ADMIN", "TRESORIER", "COMPTABLE")
                .requestMatchers("/inventaire/**").access(roleOuModuleDirecteur("INVENTAIRE", "ADMIN"))
                // Paie : preparer/calculer un bulletin (module PAIE_PREPARATION, typiquement Comptable)
                // est distinct de declencher son paiement reel — un acte de caisse qui comptabilise
                // automatiquement une Depense (module PAIE_PAIEMENT, typiquement Tresorier). Le
                // Directeur en est exclu comme le reste de la finance.
                .requestMatchers("/rh/salaires/*/payer").access(financeAccess(holyflame.administration.service.FinanceModules.PAIE_PAIEMENT))
                .requestMatchers("/rh/salaires/**").access(financeAccess(holyflame.administration.service.FinanceModules.PAIE_PREPARATION))
                // Auto-service : chaque membre du personnel consulte uniquement ses propres bulletins
                // (fiche deduite de son compte connecte, jamais transmise par le client)
                .requestMatchers("/rh/mes-bulletins/**").hasAnyRole(
                    "ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COMPTABLE", "COORDONNATEUR", "SURVEILLANT", "INFIRMIER", "MARKETING")
                // Un enseignant peut demander/annuler son propre conge (fiche deduite de son compte,
                // jamais transmise par le client) ; l'approbation reste reservee a l'ADMIN/DIRECTEUR
                .requestMatchers("/rh/conges/demander", "/rh/conges/*/annuler").hasAnyRole("ADMIN", "DIRECTEUR", "ENSEIGNANT")
                .requestMatchers("/rh/**").hasAnyRole("ADMIN", "DIRECTEUR")
                .requestMatchers("/communication/**").hasAnyRole("ADMIN", "DIRECTEUR", "SECRETAIRE")
                // /direction/** ouvert à tout authentifié — contrôle fin dans le controller
                .requestMatchers("/direction/**").authenticated()
                .requestMatchers("/archives/**").hasAnyRole("ADMIN", "DIRECTEUR", "SECRETAIRE")
                .requestMatchers("/publications/**").hasAnyRole("ADMIN", "DIRECTEUR", "SECRETAIRE")
                .requestMatchers("/portail/**").hasAnyRole("ADMIN", "ELEVE", "SECRETAIRE")
                // Le secretariat peut consulter/renvoyer le recu d'un paiement qu'il vient d'enregistrer
                // (ex: frais d'inscription lors de la creation d'un eleve), sans acces au reste du module Finances
                .requestMatchers("/finances/paiements/*/recu", "/finances/paiements/*/renvoyer-email")
                    .hasAnyRole("ADMIN", "TRESORIER", "COMPTABLE", "SECRETAIRE")
                // Segmentation Tresorier/Comptable : chaque groupe d'actions financieres suit son
                // propre module (voir FinanceModules), personnalisable par compte depuis
                // Parametres > Roles > Acces aux interfaces. Le module DEPENSES couvre aussi
                // /finances/depenses/** (DepenseController, prefixe sous /finances).
                .requestMatchers("/finances/paiements/**", "/finances/arrieres/**", "/finances/rappels", "/finances/comptages/**",
                    "/finances/parametres/solde-initial", "/finances/frais-solde")
                    .access(financeAccess(holyflame.administration.service.FinanceModules.CAISSE))
                .requestMatchers("/finances/depenses/**")
                    .access(financeAccess(holyflame.administration.service.FinanceModules.DEPENSES))
                .requestMatchers("/finances/budget/**", "/finances/categories/**", "/finances/parametres/taux-paie")
                    .access(financeAccess(holyflame.administration.service.FinanceModules.BUDGET_PARAMETRAGE))
                // Page-onglets et redirections restantes : accessibles a tout Tresorier/Comptable,
                // la visibilite fine de chaque onglet est geree cote controleur/template selon les
                // modules effectifs de l'utilisateur (FinancesController.index()).
                .requestMatchers("/finances/**").hasAnyRole("ADMIN", "TRESORIER", "COMPTABLE")
                .requestMatchers("/coordination/**").access(roleOuModuleDirecteur("COORDINATION", "ADMIN", "COORDONNATEUR"))
                // Espace MARKETING : pilote le site vitrine public de l'etablissement
                // (actualites, galerie, evenements, page a propos) — n'a acces a aucune donnee
                // d'eleve, de personnel ou de finances.
                .requestMatchers("/marketing/**").access(roleOuModuleDirecteur("MARKETING", "ADMIN", "MARKETING"))
                // Assistant conversationnel : ouvert a tous les roles rattaches a un
                // etablissement, SAUF ELEVE. Chaque role ne voit que le sous-ensemble d'outils
                // (donc de donnees) que ce role peut deja consulter ailleurs dans l'appli — voir
                // la map OUTILS_PAR_ROLE dans AssistantService. Le PARENT a ses propres outils,
                // strictement limites a ses enfants (jamais l'etablissement entier).
                .requestMatchers("/assistant/**").hasAnyRole(
                    "ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COMPTABLE", "COORDONNATEUR",
                    "SURVEILLANT", "INFIRMIER", "PARENT", "SUPER_ADMIN", "MARKETING")
                // Journal d'activite : reserve au personnel (chacun n'y voit que ses propres actions,
                // sauf ADMIN qui voit tout) — un eleve ou un parent n'a aucune raison d'y acceder.
                .requestMatchers("/journal/**").hasAnyRole("ADMIN", "DIRECTEUR", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COMPTABLE", "COORDONNATEUR", "SURVEILLANT", "INFIRMIER", "MARKETING")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .rememberMe(rm -> rm
                .key("holyflame-edusystem-remember-me-key")
                .tokenValiditySeconds(14 * 24 * 60 * 60) // 14 jours
                .userDetailsService(utilisateurDetailsService)
                .rememberMeParameter("remember-me")
            )
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/paiements/mobile/notification"))
            .addFilterAfter(new CsrfTokenEagerLoadFilter(), CsrfFilter.class)
            .addFilterAfter(new FontPreloadFilter(), CsrfTokenEagerLoadFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
