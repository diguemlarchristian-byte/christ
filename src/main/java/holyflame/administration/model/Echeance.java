package holyflame.administration.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Un versement convenu avec une famille : « trois fois, en octobre, janvier et avril ».
 *
 * Le paiement fractionne est la norme dans la plupart des ecoles, mais un Paiement ne porte
 * qu'un montant deja verse : rien ne decrivait ce qui reste attendu, ni quand. La comptable
 * suivait donc ses accords a la main, hors du logiciel.
 */
@Entity
@Table(name = "echeances")
public class Echeance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eleveId;

    /** Frais concerne. Null = echeancier portant sur l'ensemble de la scolarite. */
    private Long fraisScolariteId;

    /** Rang du versement dans l'echeancier : 1, 2, 3… */
    @Column(nullable = false)
    private Integer rang;

    @Column(nullable = false)
    private Double montantPrevu;

    @Column(nullable = false)
    private LocalDate datePrevue;

    /** Montant effectivement encaisse sur cette echeance. */
    private Double montantRegle = 0.0;

    private LocalDate dateReglement;

    @Column(nullable = false)
    private String anneeScolaire;

    @Column(nullable = false)
    private Long etablissementId;

    public Echeance() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEleveId() { return eleveId; }
    public void setEleveId(Long eleveId) { this.eleveId = eleveId; }
    public Long getFraisScolariteId() { return fraisScolariteId; }
    public void setFraisScolariteId(Long fraisScolariteId) { this.fraisScolariteId = fraisScolariteId; }
    public Integer getRang() { return rang; }
    public void setRang(Integer rang) { this.rang = rang; }
    public Double getMontantPrevu() { return montantPrevu; }
    public void setMontantPrevu(Double montantPrevu) { this.montantPrevu = montantPrevu; }
    public Double getMontantRegle() { return montantRegle; }
    public void setMontantRegle(Double montantRegle) { this.montantRegle = montantRegle; }
    public LocalDate getDatePrevue() { return datePrevue; }
    public void setDatePrevue(LocalDate datePrevue) { this.datePrevue = datePrevue; }
    public LocalDate getDateReglement() { return dateReglement; }
    public void setDateReglement(LocalDate dateReglement) { this.dateReglement = dateReglement; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public double resteDu() {
        double prevu = montantPrevu != null ? montantPrevu : 0;
        double regle = montantRegle != null ? montantRegle : 0;
        return Math.max(0, prevu - regle);
    }

    public boolean estSoldee() { return resteDu() <= 0.009; }

    /** Une echeance depassee et non soldee : c'est ce qui doit remonter a la comptable. */
    public boolean estEnRetard(LocalDate aujourdhui) {
        return !estSoldee() && datePrevue != null && datePrevue.isBefore(aujourdhui);
    }
}
