package holyflame.administration.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Reduction accordee a un eleve sur sa scolarite : bourse, fratrie, enfant du personnel.
 *
 * Sans elle, un boursier apparait en impaye permanent dans le tableau des arrieres, et un
 * tableau faux cesse d'etre consulte. Le plan comptable prevoyait deja les postes 65820
 * « Reduction de la scolarite » et 65130 « Retour de scolarite » ; il manquait le moyen de
 * les alimenter.
 */
@Entity
@Table(name = "remises_eleve")
public class RemiseEleve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eleveId;

    /** Frais vise. Null = la remise porte sur l'ensemble des frais obligatoires de l'eleve. */
    private Long fraisScolariteId;

    /** POURCENTAGE (valeur en %) ou MONTANT (valeur en francs). */
    @Column(nullable = false)
    private String type = "POURCENTAGE";

    @Column(nullable = false)
    private Double valeur;

    /** BOURSE, FRATRIE, PERSONNEL, SOCIALE, AUTRE — sert a justifier la reduction. */
    private String motif;

    private String commentaire;

    /** Une remise vaut pour une annee : elle ne se reconduit pas toute seule. */
    @Column(nullable = false)
    private String anneeScolaire;

    private LocalDate dateAccord;

    /** Qui l'a accordee : une reduction engage l'etablissement, elle doit etre imputable. */
    private Long accordeeParId;

    @Column(nullable = false)
    private Long etablissementId;

    public RemiseEleve() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEleveId() { return eleveId; }
    public void setEleveId(Long eleveId) { this.eleveId = eleveId; }
    public Long getFraisScolariteId() { return fraisScolariteId; }
    public void setFraisScolariteId(Long fraisScolariteId) { this.fraisScolariteId = fraisScolariteId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getValeur() { return valeur; }
    public void setValeur(Double valeur) { this.valeur = valeur; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public LocalDate getDateAccord() { return dateAccord; }
    public void setDateAccord(LocalDate dateAccord) { this.dateAccord = dateAccord; }
    public Long getAccordeeParId() { return accordeeParId; }
    public void setAccordeeParId(Long accordeeParId) { this.accordeeParId = accordeeParId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    /** Montant reellement deduit d'un frais donne, plafonne a ce frais. */
    public double montantDeduit(double montantDuFrais) {
        if (valeur == null || valeur <= 0 || montantDuFrais <= 0) return 0;
        double deduit = "MONTANT".equals(type) ? valeur : montantDuFrais * valeur / 100.0;
        // Une remise ne peut pas depasser ce qu'elle reduit : sinon l'ecole devrait de l'argent
        // a la famille, ce qui n'a pas de sens ici.
        return Math.min(deduit, montantDuFrais);
    }
}
