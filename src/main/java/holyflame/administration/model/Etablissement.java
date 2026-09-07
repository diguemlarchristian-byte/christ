package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "etablissements")
public class Etablissement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String ville;
    private String adresse;
    private String telephone;
    private String email;

    @Column(unique = true, nullable = false)
    private String codeAcces; // code unique d'accès (ex: HF-2025-001)

    private String statut = "ACTIF"; // ACTIF, SUSPENDU
    private LocalDate dateCreation;
    private String anneeScolaire;
    private String contact;      // nom du directeur
    private String typeEtablissement; // COLLEGE, PRIMAIRE, LYCEE...
    private String monnaie = "FCFA";
    private String planAbonnement = "GRATUIT"; // GRATUIT, STANDARD, PREMIUM

    // Calendrier academique (issu de l'assistant d'inscription)
    private LocalDate dateDebutSession;
    private LocalDate dateFinSession;

    // Systeme de notation et assiduite
    private String systemeNotation = "NUMERIQUE"; // NUMERIQUE, LETTRES
    private Integer seuilAssiduite = 75;
    private String alerteAbsences;
    private String calculRetard;
    private boolean statutsIncompletAbandon = false;
    private boolean rangAutomatique = true;

    // Identite visuelle
    private String logoPath;
    private String couleurPrimaire = "#00236f";
    private String langueSysteme = "Francais";
    private String fuseauHoraire = "(GMT+00:00) Abidjan";

    // ── Regime academique ────────────────────────────────────────────────
    // SCOLAIRE : trimestres, matieres a coefficient, bulletin trimestriel (comportement historique).
    // LMD      : semestres, unites d'enseignement a credits, releve de notes semestriel.
    // La valeur par defaut vaut pour tous les etablissements deja en base : basculer un
    // etablissement en LMD est un acte explicite de l'administrateur, jamais une migration.
    private String regimeAcademique = "SCOLAIRE";

    // Regles de deliberation LMD. Elles varient d'un pays et d'un etablissement a l'autre —
    // notamment la compensation et la note eliminatoire — donc rien n'est fige dans le code.
    private Double seuilValidationUE = 10.0;
    private boolean compensationSemestrielle = true;
    /** Sous cette note, une UE ne peut plus etre compensee. Null = pas de note eliminatoire. */
    private Double noteEliminatoire;
    private Integer creditsParSemestre = 30;

    public Etablissement() {}

    /** Vrai lorsque l'etablissement fonctionne en unites d'enseignement et credits. */
    public boolean estRegimeLMD() { return "LMD".equals(regimeAcademique); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCodeAcces() { return codeAcces; }
    public void setCodeAcces(String codeAcces) { this.codeAcces = codeAcces; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDate getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getTypeEtablissement() { return typeEtablissement; }
    public void setTypeEtablissement(String typeEtablissement) { this.typeEtablissement = typeEtablissement; }
    public String getMonnaie() { return monnaie; }
    public void setMonnaie(String monnaie) { this.monnaie = monnaie; }
    public String getPlanAbonnement() { return planAbonnement; }
    public void setPlanAbonnement(String planAbonnement) { this.planAbonnement = planAbonnement; }
    public LocalDate getDateDebutSession() { return dateDebutSession; }
    public void setDateDebutSession(LocalDate dateDebutSession) { this.dateDebutSession = dateDebutSession; }
    public LocalDate getDateFinSession() { return dateFinSession; }
    public void setDateFinSession(LocalDate dateFinSession) { this.dateFinSession = dateFinSession; }
    public String getSystemeNotation() { return systemeNotation; }
    public void setSystemeNotation(String systemeNotation) { this.systemeNotation = systemeNotation; }
    public Integer getSeuilAssiduite() { return seuilAssiduite; }
    public void setSeuilAssiduite(Integer seuilAssiduite) { this.seuilAssiduite = seuilAssiduite; }
    public String getAlerteAbsences() { return alerteAbsences; }
    public void setAlerteAbsences(String alerteAbsences) { this.alerteAbsences = alerteAbsences; }
    public String getCalculRetard() { return calculRetard; }
    public void setCalculRetard(String calculRetard) { this.calculRetard = calculRetard; }
    public boolean isStatutsIncompletAbandon() { return statutsIncompletAbandon; }
    public void setStatutsIncompletAbandon(boolean statutsIncompletAbandon) { this.statutsIncompletAbandon = statutsIncompletAbandon; }
    public boolean isRangAutomatique() { return rangAutomatique; }
    public void setRangAutomatique(boolean rangAutomatique) { this.rangAutomatique = rangAutomatique; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    public String getCouleurPrimaire() { return couleurPrimaire; }
    public void setCouleurPrimaire(String couleurPrimaire) { this.couleurPrimaire = couleurPrimaire; }
    public String getLangueSysteme() { return langueSysteme; }
    public void setLangueSysteme(String langueSysteme) { this.langueSysteme = langueSysteme; }
    public String getFuseauHoraire() { return fuseauHoraire; }
    public void setFuseauHoraire(String fuseauHoraire) { this.fuseauHoraire = fuseauHoraire; }
    public String getRegimeAcademique() { return regimeAcademique; }
    public void setRegimeAcademique(String regimeAcademique) { this.regimeAcademique = regimeAcademique; }
    public Double getSeuilValidationUE() { return seuilValidationUE; }
    public void setSeuilValidationUE(Double seuilValidationUE) { this.seuilValidationUE = seuilValidationUE; }
    public boolean isCompensationSemestrielle() { return compensationSemestrielle; }
    public void setCompensationSemestrielle(boolean compensationSemestrielle) { this.compensationSemestrielle = compensationSemestrielle; }
    public Double getNoteEliminatoire() { return noteEliminatoire; }
    public void setNoteEliminatoire(Double noteEliminatoire) { this.noteEliminatoire = noteEliminatoire; }
    public Integer getCreditsParSemestre() { return creditsParSemestre; }
    public void setCreditsParSemestre(Integer creditsParSemestre) { this.creditsParSemestre = creditsParSemestre; }
}
