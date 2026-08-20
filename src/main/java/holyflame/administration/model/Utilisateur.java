package holyflame.administration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateurs")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String prenom;

    @Column(unique = true, nullable = false)
    private String email;

    private String motDePasse;
    private String role;
    private boolean actif = true;

    // Modules operationnels supplementaires actives pour ce compte, en plus de ceux
    // deja lies a son role (codes CSV : SECRETARIAT, SURVEILLANCE, INFIRMERIE,
    // MARKETING, INVENTAIRE, COORDINATION). Sert uniquement au role DIRECTEUR, pour
    // permettre a l'ADMIN de lui confier des taches operationnelles supplementaires
    // dans les etablissements sous-effectif — jamais utilise pour etendre l'acces
    // aux modules financiers (finances/tresorerie/budget/salaires), volontairement
    // absents de cette liste.
    @Column(length = 500)
    private String modulesOptionnels;

    // Personnalisation des acces financiers pour un compte TRESORIER ou COMPTABLE
    // (codes CSV : CAISSE, DEPENSES, PAIE_PREPARATION, PAIE_PAIEMENT, BUDGET_PARAMETRAGE,
    // RAPPORTS). null = pas encore personnalise, le compte utilise les droits par defaut
    // de son role (voir FinanceModules) ; une valeur non-nulle (meme vide) signifie que
    // l'ADMIN a explicitement restreint ce compte a exactement ces modules.
    @Column(length = 500)
    private String modulesFinance;
    private boolean modulesFinancePersonnalises = false;

    @Column(unique = true)
    private String resetToken;
    private LocalDateTime resetTokenExpiration;

    private int tentativesEchouees = 0;
    private LocalDateTime verrouilleJusqua;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etablissement_id", nullable = true)
    private Etablissement etablissement;

    public Utilisateur() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public java.util.Set<String> getModulesOptionnelsActifs() {
        if (modulesOptionnels == null || modulesOptionnels.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(modulesOptionnels.split(","))
            .map(String::trim).filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toSet());
    }

    public void setModulesOptionnelsActifs(java.util.Set<String> modules) {
        this.modulesOptionnels = (modules == null || modules.isEmpty()) ? null : String.join(",", modules);
    }

    /** null = pas encore personnalise (le compte utilise les droits par defaut de son role). */
    public java.util.Set<String> getModulesFinanceActifs() {
        if (!modulesFinancePersonnalises) return null;
        if (modulesFinance == null || modulesFinance.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(modulesFinance.split(","))
            .map(String::trim).filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toSet());
    }

    /** Marque le compte comme personnalise avec exactement cet ensemble (peut etre vide). */
    public void setModulesFinanceActifs(java.util.Set<String> modules) {
        this.modulesFinancePersonnalises = true;
        this.modulesFinance = (modules == null || modules.isEmpty()) ? "" : String.join(",", modules);
    }

    /** Revient aux droits par defaut du role (annule toute personnalisation). */
    public void reinitialiserModulesFinance() {
        this.modulesFinancePersonnalises = false;
        this.modulesFinance = null;
    }

    public boolean isModulesFinancePersonnalises() {
        return modulesFinancePersonnalises;
    }

    public Etablissement getEtablissement() {
        return etablissement;
    }

    public void setEtablissement(Etablissement etablissement) {
        this.etablissement = etablissement;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiration() {
        return resetTokenExpiration;
    }

    public void setResetTokenExpiration(LocalDateTime resetTokenExpiration) {
        this.resetTokenExpiration = resetTokenExpiration;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public int getTentativesEchouees() {
        return tentativesEchouees;
    }

    public void setTentativesEchouees(int tentativesEchouees) {
        this.tentativesEchouees = tentativesEchouees;
    }

    public LocalDateTime getVerrouilleJusqua() {
        return verrouilleJusqua;
    }

    public void setVerrouilleJusqua(LocalDateTime verrouilleJusqua) {
        this.verrouilleJusqua = verrouilleJusqua;
    }
}
