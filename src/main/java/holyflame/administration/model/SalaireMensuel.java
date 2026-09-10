package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "salaires_mensuels")
public class SalaireMensuel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personnel_id", nullable = false)
    private Personnel personnel;

    private int mois;
    private int annee;
    private String anneeScolaire;
    private LocalDate periodeDebut;
    private LocalDate periodeFin;

    private Double totalBrut;
    private Double totalRetenuesSalariales;
    private Double totalChargesPatronales;
    private Double netAPayer;

    private String statut = "EN_ATTENTE"; // EN_ATTENTE, PAYE
    private LocalDate datePaiement;

    // Archive du bulletin au moment exact du paiement. Le bulletin s'imprimait depuis le
    // navigateur, a partir des taux en vigueur ce jour-la : reimprime six mois plus tard, apres
    // un changement de taux, il ne montrait plus ce qui avait ete remis. Ces trois champs figent
    // la preuve — le PDF tel qu'il a ete edite, l'instant de son edition, et un code qui permet
    // de verifier qu'un papier presente correspond bien a cette archive.
    private String archiveChemin;
    private LocalDateTime archiveHorodatage;
    @Column(unique = true)
    private String codeVerification;

    @OneToMany(mappedBy = "salaireMensuel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LigneSalaire> lignes = new ArrayList<>();

    public SalaireMensuel() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Personnel getPersonnel() { return personnel; }
    public void setPersonnel(Personnel personnel) { this.personnel = personnel; }
    public int getMois() { return mois; }
    public void setMois(int mois) { this.mois = mois; }
    public int getAnnee() { return annee; }
    public void setAnnee(int annee) { this.annee = annee; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public LocalDate getPeriodeDebut() { return periodeDebut; }
    public void setPeriodeDebut(LocalDate periodeDebut) { this.periodeDebut = periodeDebut; }
    public LocalDate getPeriodeFin() { return periodeFin; }
    public void setPeriodeFin(LocalDate periodeFin) { this.periodeFin = periodeFin; }
    public Double getTotalBrut() { return totalBrut; }
    public void setTotalBrut(Double totalBrut) { this.totalBrut = totalBrut; }
    public Double getTotalRetenuesSalariales() { return totalRetenuesSalariales; }
    public void setTotalRetenuesSalariales(Double totalRetenuesSalariales) { this.totalRetenuesSalariales = totalRetenuesSalariales; }
    public Double getTotalChargesPatronales() { return totalChargesPatronales; }
    public void setTotalChargesPatronales(Double totalChargesPatronales) { this.totalChargesPatronales = totalChargesPatronales; }
    public Double getNetAPayer() { return netAPayer; }
    public void setNetAPayer(Double netAPayer) { this.netAPayer = netAPayer; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDate getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDate datePaiement) { this.datePaiement = datePaiement; }
    public String getArchiveChemin() { return archiveChemin; }
    public void setArchiveChemin(String archiveChemin) { this.archiveChemin = archiveChemin; }
    public LocalDateTime getArchiveHorodatage() { return archiveHorodatage; }
    public void setArchiveHorodatage(LocalDateTime archiveHorodatage) { this.archiveHorodatage = archiveHorodatage; }
    public String getCodeVerification() { return codeVerification; }
    public void setCodeVerification(String codeVerification) { this.codeVerification = codeVerification; }

    /** Un bulletin dont la preuve a bien ete produite et rangee au moment du paiement. */
    @Transient
    public boolean isArchive() {
        return archiveChemin != null && !archiveChemin.isBlank();
    }

    public List<LigneSalaire> getLignes() { return lignes; }
    public void setLignes(List<LigneSalaire> lignes) { this.lignes = lignes; }
}
