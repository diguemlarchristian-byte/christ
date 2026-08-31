package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Verrouillage comptable d'un mois civil : une fois cloture, aucune depense datee dans ce mois
 * ne peut plus etre ajoutee, modifiee ou supprimee (voir ClotureMensuelleService), evitant qu'une
 * ecriture d'un mois deja arrete au comptable soit modifiee apres coup.
 */
@Entity
@Table(name = "clotures_mensuelles")
public class ClotureMensuelle {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer mois;

    @Column(nullable = false)
    private Integer anneeCivile;

    private Long etablissementId;

    private LocalDate dateCloture;

    private Long clotureParId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getMois() { return mois; }
    public void setMois(Integer mois) { this.mois = mois; }
    public Integer getAnneeCivile() { return anneeCivile; }
    public void setAnneeCivile(Integer anneeCivile) { this.anneeCivile = anneeCivile; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public LocalDate getDateCloture() { return dateCloture; }
    public void setDateCloture(LocalDate dateCloture) { this.dateCloture = dateCloture; }
    public Long getClotureParId() { return clotureParId; }
    public void setClotureParId(Long clotureParId) { this.clotureParId = clotureParId; }
}
