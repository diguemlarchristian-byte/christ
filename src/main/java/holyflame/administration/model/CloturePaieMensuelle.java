package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Cloture de la paie d'un mois, pour tout un etablissement.
 *
 * Un bulletin paye etait deja fige : il ne pouvait plus etre modifie ni supprime. Mais rien
 * n'empechait d'en etablir un nouveau, en novembre, pour un mois d'aout que tout le monde
 * croyait solde — la liste « personnel actif sans bulletin » ne couvre que le mois affiche a
 * l'ecran, et personne ne revient regarder aout. Le verrou annuel, lui, arrive trop tard.
 *
 * Une ligne ici signifie : ce mois est clos, on n'y ajoute plus rien. Elle porte qui a
 * cloture et quand, parce qu'une cloture est une affirmation — « la paie de ce mois est
 * complete » — et qu'une affirmation doit avoir un auteur.
 */
@Entity
@Table(name = "clotures_paie_mensuelle",
       uniqueConstraints = @UniqueConstraint(columnNames = {"etablissementId", "mois", "annee"}))
public class CloturePaieMensuelle {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long etablissementId;

    @Column(nullable = false)
    private int mois;

    @Column(nullable = false)
    private int annee;

    private LocalDateTime dateCloture;
    private String clotureePar;

    /** Nombre de bulletins payes au moment de la cloture : ce que l'auteur a affirme solder. */
    private int nbBulletins;

    private Double totalNet;

    public CloturePaieMensuelle() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public int getMois() { return mois; }
    public void setMois(int mois) { this.mois = mois; }
    public int getAnnee() { return annee; }
    public void setAnnee(int annee) { this.annee = annee; }
    public LocalDateTime getDateCloture() { return dateCloture; }
    public void setDateCloture(LocalDateTime dateCloture) { this.dateCloture = dateCloture; }
    public String getClotureePar() { return clotureePar; }
    public void setClotureePar(String clotureePar) { this.clotureePar = clotureePar; }
    public int getNbBulletins() { return nbBulletins; }
    public void setNbBulletins(int nbBulletins) { this.nbBulletins = nbBulletins; }
    public Double getTotalNet() { return totalNet; }
    public void setTotalNet(Double totalNet) { this.totalNet = totalNet; }
}
