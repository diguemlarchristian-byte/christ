package holyflame.administration.config;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.Fonctionnalites;
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
     * Autorise la requete si la personne connectee dispose de cette fonctionnalite.
     *
     * Une seule regle remplace les trois qui coexistaient ici : celle du role, celle des
     * « modules optionnels » du Directeur, et celle des modules financiers du Tresorier et
     * du Comptable. Ce que chacun peut faire se lit desormais au meme endroit — le registre
     * Fonctionnalites — et se modifie au meme endroit : le tableau des acces.
     *
     * Le compte est relu en base a chaque requete plutot que d'etre fige dans la session a
     * la connexion. C'est une requete indexee par courriel, et elle achete une chose qui
     * vaut plus que ce qu'elle coute : un acces retire l'est immediatement, sans attendre
     * que la personne se deconnecte.
     */
    private AuthorizationManager<RequestAuthorizationContext> peut(String code) {
        return peutUnDe(code);
    }

    /**
     * Autorise si la personne dispose d'AU MOINS UNE des fonctionnalites citees.
     *
     * Sert aux ecrans partages : un export d'eleves s'ouvre depuis le secretariat, depuis
     * la direction et depuis la comptabilite, et chacun y arrive par sa propre porte.
     */
    private AuthorizationManager<RequestAuthorizationContext> peutUnDe(String... codes) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (auth == null || !auth.isAuthenticated()) return new AuthorizationDecision(false);
            Utilisateur u = utilisateurRepository.findByEmail(auth.getName()).orElse(null);
            if (u == null) return new AuthorizationDecision(false);
            java.util.Set<String> effectives = Fonctionnalites.effectives(u);
            for (String code : codes) {
                if (effectives.contains(code)) return new AuthorizationDecision(true);
            }
            return new AuthorizationDecision(false);
        };
    }

    /** Les ecrans d'accueil de la finance : il suffit d'avoir une porte d'entree. */
    private AuthorizationManager<RequestAuthorizationContext> peutFinance() {
        return peutUnDe(Fonctionnalites.FIN_CAISSE, Fonctionnalites.FIN_DEPENSES,
            Fonctionnalites.FIN_PAIE_PREPARATION, Fonctionnalites.FIN_PAIE_PAIEMENT,
            Fonctionnalites.FIN_BUDGET, Fonctionnalites.FIN_RAPPORTS, Fonctionnalites.FIN_FRAIS,
            Fonctionnalites.FIN_COMPTABILITE, Fonctionnalites.FIN_SUIVI_FAMILLES);
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

                // ── Espaces personnels ───────────────────────────────────────────────
                // Ils ne passent pas par le registre : un eleve, un parent, un enseignant
                // n'y consultent que leurs propres donnees, deduites de leur compte. Il n'y
                // a rien a doser, donc rien a cocher.
                .requestMatchers("/tableau-enseignant/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/tableau-eleve/**").hasAnyRole("ADMIN", "ELEVE")
                .requestMatchers("/portail-parent/**").hasAnyRole("ADMIN", "PARENT")
                .requestMatchers("/portail/**").hasAnyRole("ADMIN", "ELEVE", "SECRETAIRE")
                // Controle fin cote controleur, selon ce que chaque role doit y voir.
                .requestMatchers("/direction/**").authenticated()

                // ── Scolarite ────────────────────────────────────────────────────────
                .requestMatchers("/passage/assistant/**").access(peut(Fonctionnalites.PASSAGE_CLOTURE))
                // Les demandes de pre-inscription se lisent et se trient sans donner acces au
                // reste du secretariat : une ecole peut confier ce tri a quelqu un qui ne touche
                // pas aux dossiers. Cette regle doit rester avant /secretariat/** pour etre
                // atteinte.
                .requestMatchers("/secretariat/demandes", "/secretariat/demandes/**")
                    .access(peut(Fonctionnalites.PREINSCRIPTIONS))
                .requestMatchers("/secretariat/eleves/nouveau", "/secretariat/eleves/nouveau/**")
                    .access(peut(Fonctionnalites.ELEVES_INSCRIRE))
                .requestMatchers("/secretariat/**").access(peut(Fonctionnalites.SECRETARIAT))
                .requestMatchers("/passage/**").access(peut(Fonctionnalites.PASSAGE))
                .requestMatchers("/archives/**").access(peut(Fonctionnalites.ARCHIVES))

                // ── Pedagogie ────────────────────────────────────────────────────────
                .requestMatchers("/gestion-academique/**").access(peut(Fonctionnalites.ACADEMIQUE))
                .requestMatchers("/gestion-classes/**").access(peut(Fonctionnalites.CLASSES))
                .requestMatchers("/gestion-salles/**").access(peut(Fonctionnalites.SALLES))
                .requestMatchers("/matieres/**").access(peut(Fonctionnalites.MATIERES))
                .requestMatchers("/notes/**").access(peut(Fonctionnalites.NOTES))
                .requestMatchers("/examens/**").access(peut(Fonctionnalites.EXAMENS))
                .requestMatchers("/bulletins/**").access(peut(Fonctionnalites.BULLETINS))
                .requestMatchers("/emploi-du-temps", "/emploi-du-temps/**").access(peut(Fonctionnalites.EMPLOI_DU_TEMPS))

                // ── Enseignement superieur ───────────────────────────────────────────
                // La maquette (parcours, unites, credits) et les releves semestriels sont
                // deux metiers distincts a l'universite : la premiere se decide en conseil
                // de departement, les seconds s'editent a l'apparitorat. Deux cases, donc.
                .requestMatchers("/academique-universite/**").access(peut(Fonctionnalites.UNIV_MAQUETTE))
                .requestMatchers("/releve-notes", "/releve-notes/**").access(peut(Fonctionnalites.UNIV_RELEVE))
                .requestMatchers("/coordination/**").access(peut(Fonctionnalites.COORDINATION))

                // ── Vie scolaire ─────────────────────────────────────────────────────
                .requestMatchers("/surveillance/programmes/**").access(peut(Fonctionnalites.PROGRAMMES))
                .requestMatchers("/surveillance", "/surveillance/absences/**").access(peut(Fonctionnalites.ABSENCES))
                .requestMatchers("/surveillance/**").access(peut(Fonctionnalites.PROGRAMMES))
                .requestMatchers("/surveillant/**").access(peut(Fonctionnalites.ABSENCES))
                .requestMatchers("/infirmerie/**").access(peut(Fonctionnalites.INFIRMERIE))

                // ── Personnel ────────────────────────────────────────────────────────
                .requestMatchers("/personnel/nouveau", "/personnel/nouveau/**").access(peut(Fonctionnalites.PERSONNEL_ENREGISTRER))
                .requestMatchers(HttpMethod.GET, "/personnel", "/personnel/*").access(peut(Fonctionnalites.PERSONNEL_CONSULTER))
                .requestMatchers("/personnel/**").access(peut(Fonctionnalites.PERSONNEL_GERER))
                // Preparer un bulletin de paie et declencher son paiement sont deux actes
                // differents : le second sort de l'argent et comptabilise une depense.
                .requestMatchers("/rh/salaires/*/payer").access(peut(Fonctionnalites.FIN_PAIE_PAIEMENT))
                .requestMatchers("/rh/salaires/**").access(peut(Fonctionnalites.FIN_PAIE_PREPARATION))
                .requestMatchers("/rh/mes-bulletins/**").access(peut(Fonctionnalites.MES_BULLETINS))
                .requestMatchers("/rh/conges/demander", "/rh/conges/*/annuler").access(peut(Fonctionnalites.MON_CONGE))
                .requestMatchers("/rh/**").access(peut(Fonctionnalites.PERSONNEL_GERER))

                // ── Finances ─────────────────────────────────────────────────────────
                .requestMatchers("/frais/**").access(peut(Fonctionnalites.FIN_FRAIS))
                .requestMatchers("/comptabilite/**").access(peut(Fonctionnalites.FIN_COMPTABILITE))
                .requestMatchers("/suivi-familles/**").access(peut(Fonctionnalites.FIN_SUIVI_FAMILLES))
                .requestMatchers("/budget/**").access(peut(Fonctionnalites.FIN_BUDGET))
                // Rendre un recu pour un paiement qu'on vient d'enregistrer, sans ouvrir le
                // reste des finances — le secretariat encaisse parfois l'inscription.
                .requestMatchers("/finances/paiements/*/recu", "/finances/paiements/*/renvoyer-email")
                    .access(peutUnDe(Fonctionnalites.FIN_CAISSE, Fonctionnalites.FIN_RECU))
                .requestMatchers("/finances/paiements/**", "/finances/arrieres/**", "/finances/rappels",
                    "/finances/comptages/**", "/finances/parametres/solde-initial", "/finances/frais-solde")
                    .access(peut(Fonctionnalites.FIN_CAISSE))
                .requestMatchers("/finances/depenses/**").access(peut(Fonctionnalites.FIN_DEPENSES))
                .requestMatchers("/finances/budget/**", "/finances/categories/**", "/finances/parametres/taux-paie")
                    .access(peut(Fonctionnalites.FIN_BUDGET))
                // Pages d'onglets et redirections : la visibilite fine de chaque onglet est
                // geree cote controleur, selon les fonctionnalites effectives du compte.
                .requestMatchers("/finances/**").access(peutFinance())
                .requestMatchers("/tresorerie/**").access(peutFinance())

                // ── Exports ──────────────────────────────────────────────────────────
                // Chaque export suit l'ecran d'ou son bouton est clique, et doit rester
                // avant la regle /export/** generique pour etre atteint.
                .requestMatchers("/export/paiements/excel", "/export/rapports/excel").access(peut(Fonctionnalites.FIN_RAPPORTS))
                .requestMatchers("/export/eleves/excel").access(peutUnDe(Fonctionnalites.SECRETARIAT, Fonctionnalites.ACADEMIQUE, Fonctionnalites.FIN_RAPPORTS))
                .requestMatchers("/export/notes/excel").access(peut(Fonctionnalites.NOTES))
                .requestMatchers("/export/releve-notes/excel").access(peut(Fonctionnalites.UNIV_RELEVE))
                // Cet export expose tous les salaires : il suit la paie, jamais la regle
                // generique qui l'aurait ouvert a qui l'on a justement retire la paie.
                .requestMatchers("/export/paie/excel").access(peut(Fonctionnalites.FIN_PAIE_PREPARATION))
                .requestMatchers("/export/inventaire/excel").access(peut(Fonctionnalites.INVENTAIRE))
                .requestMatchers("/export/**").access(peut(Fonctionnalites.FIN_RAPPORTS))

                // ── Communication, logistique, pilotage ──────────────────────────────
                .requestMatchers("/messagerie/**").access(peut(Fonctionnalites.MESSAGERIE))
                .requestMatchers("/communication/**").access(peut(Fonctionnalites.COMMUNICATION))
                .requestMatchers("/publications/**").access(peut(Fonctionnalites.PUBLICATIONS))
                .requestMatchers("/marketing/**").access(peut(Fonctionnalites.MARKETING))
                .requestMatchers("/inventaire/**").access(peut(Fonctionnalites.INVENTAIRE))
                .requestMatchers("/assistant/**").access(peut(Fonctionnalites.ASSISTANT))
                .requestMatchers("/journal/**").access(peut(Fonctionnalites.JOURNAL))
                .requestMatchers("/dashboard").access(peut(Fonctionnalites.TABLEAU_BORD))
                .requestMatchers("/recherche").access(peut(Fonctionnalites.RECHERCHE))

                // ── Administration ───────────────────────────────────────────────────
                // Ne se delegue pas : qui obtient les parametres peut ensuite s'attribuer
                // tout le reste, finance comprise.
                .requestMatchers("/parametres/**").access(peut(Fonctionnalites.PARAMETRES))

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
