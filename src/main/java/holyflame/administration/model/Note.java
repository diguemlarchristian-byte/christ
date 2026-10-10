package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "notes")
public class Note {

    public static final String TYPE_DEVOIR = "DEVOIR";
    public static final String TYPE_DEVOIR2 = "DEVOIR2";
    public static final String TYPE_DEVOIR3 = "DEVOIR3";
    public static final String TYPE_EXAMEN = "EXAMEN";
    public static final String TYPE_PARTICIPATION = "PARTICIPATION";
    public static final String TYPE_TP = "TP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matiere_id", nullable = false)
    private Matiere matiere;

    private Double valeur;
    private Double coefficient;
    private String type; // DEVOIR, EXAMEN, PARTICIPATION
    private String titre; // Libelle libre de l'evaluation (ex: "Devoir Surveille n°2")
    private Integer trimestre; // 1, 2, 3
    private String anneeScolaire; // figee au moment de la saisie, independante de la classe actuelle de l'eleve
    private LocalDate dateEvaluation;
    private String commentaire;

    /**
     * BROUILLON ou PUBLIE — seules les notes PUBLIE sont visibles des
     * eleves/parents et comptent dans les bulletins
     */
    private String statut = "PUBLIE";

    /** Horodatage automatique de la saisie en base */
    private LocalDateTime saisieAt;

    /** Identifiant du compte utilisateur qui a saisi la note */
    @Column(name = "saisie_par_id")
    private Long saisieParId;

    public Note() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Eleve getEleve() {
        return eleve;
    }

    public void setEleve(Eleve eleve) {
        this.eleve = eleve;
    }

    public Matiere getMatiere() {
        return matiere;
    }

    public void setMatiere(Matiere matiere) {
        this.matiere = matiere;
    }

    public Double getValeur() {
        return valeur;
    }

    public void setValeur(Double valeur) {
        this.valeur = valeur;
    }

    public Double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(Double coefficient) {
        this.coefficient = coefficient;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = normalizeType(type);
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank())
            return type;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("DEV1".equals(normalized) || "DEVOIR1".equals(normalized) || "DEVOIR 1".equals(normalized))
            return TYPE_DEVOIR;
        if ("DEV2".equals(normalized) || "DEVOIR 2".equals(normalized) || "DEVOIR2".equals(normalized))
            return TYPE_DEVOIR2;
        if ("DEV3".equals(normalized) || "DEVOIR 3".equals(normalized) || "DEVOIR3".equals(normalized))
            return TYPE_DEVOIR3;
        return normalized;
    }

    public static boolean isDevoirLikeType(String type) {
        String normalized = normalizeType(type);
        return TYPE_DEVOIR.equals(normalized) || TYPE_DEVOIR2.equals(normalized) || TYPE_DEVOIR3.equals(normalized);
    }

    public static boolean isExamenType(String type) {
        return TYPE_EXAMEN.equals(normalizeType(type));
    }

    public static boolean isControleContinuType(String type) {
        String normalized = normalizeType(type);
        return isDevoirLikeType(normalized) || TYPE_PARTICIPATION.equals(normalized) || TYPE_TP.equals(normalized);
    }

    /**
     * Participation et Travaux Pratiques : n'entrent jamais dans le calcul de la moyenne
     * academique (devoirs/examen), mais peuvent apporter un bonus plafonne — voir
     * BulletinService.bonusParticipationTP.
     */
    public static boolean isBonusType(String type) {
        String normalized = normalizeType(type);
        return TYPE_PARTICIPATION.equals(normalized) || TYPE_TP.equals(normalized);
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public Integer getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Integer trimestre) {
        this.trimestre = trimestre;
    }

    public String getAnneeScolaire() {
        return anneeScolaire;
    }

    public void setAnneeScolaire(String anneeScolaire) {
        this.anneeScolaire = anneeScolaire;
    }

    public LocalDate getDateEvaluation() {
        return dateEvaluation;
    }

    public void setDateEvaluation(LocalDate dateEvaluation) {
        this.dateEvaluation = dateEvaluation;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public LocalDateTime getSaisieAt() {
        return saisieAt;
    }

    public void setSaisieAt(LocalDateTime saisieAt) {
        this.saisieAt = saisieAt;
    }

    public Long getSaisieParId() {
        return saisieParId;
    }

    public void setSaisieParId(Long saisieParId) {
        this.saisieParId = saisieParId;
    }
}
