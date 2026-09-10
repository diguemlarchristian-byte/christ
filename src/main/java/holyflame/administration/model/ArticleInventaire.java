package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "articles_inventaire")
public class ArticleInventaire {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String categorie;    // MOBILIER, INFORMATIQUE, LIVRE, SPORT, MATERIEL_BUREAU, AUTRE
    private int quantite;
    private String etat;         // NEUF, BON_ETAT, USE (qualite du lot ; voir les compteurs ci-dessous)
    private String localisation;
    private LocalDate dateAcquisition;
    private Double valeurUnitaire;
    private String fournisseur;
    private String numSerie;
    @Column(length = 1000)
    private String notes;

    // Un article represente un lot (« 40 tables-bancs »), pas une unite. L'etat ci-dessus est
    // la qualite generale du lot ; ces deux compteurs disent combien d'unites, dans ce lot, sont
    // aujourd'hui indisponibles. Sans eux, trois chaises cassees sur quarante n'etaient pas
    // representables : il fallait basculer les quarante en HORS_SERVICE, ou ne rien dire.
    private int quantiteEnReparation;
    private int quantiteHorsService;

    // Depense qui a paye ce lot, quand elle est connue : l'achat etait saisi deux fois, a
    // l'inventaire et en comptabilite, sans que rien ne rapproche jamais les deux.
    private Long depenseId;
    private Long etablissementId;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MouvementInventaire> mouvements = new ArrayList<>();

    public ArticleInventaire() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public int getQuantite() { return quantite; }
    public void setQuantite(int quantite) { this.quantite = quantite; }
    public String getEtat() { return etat; }
    public void setEtat(String etat) { this.etat = etat; }
    public String getLocalisation() { return localisation; }
    public void setLocalisation(String localisation) { this.localisation = localisation; }
    public LocalDate getDateAcquisition() { return dateAcquisition; }
    public void setDateAcquisition(LocalDate dateAcquisition) { this.dateAcquisition = dateAcquisition; }
    public Double getValeurUnitaire() { return valeurUnitaire; }
    public void setValeurUnitaire(Double valeurUnitaire) { this.valeurUnitaire = valeurUnitaire; }
    public String getFournisseur() { return fournisseur; }
    public void setFournisseur(String fournisseur) { this.fournisseur = fournisseur; }
    public String getNumSerie() { return numSerie; }
    public void setNumSerie(String numSerie) { this.numSerie = numSerie; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public int getQuantiteEnReparation() { return quantiteEnReparation; }
    public void setQuantiteEnReparation(int quantiteEnReparation) { this.quantiteEnReparation = quantiteEnReparation; }
    public int getQuantiteHorsService() { return quantiteHorsService; }
    public void setQuantiteHorsService(int quantiteHorsService) { this.quantiteHorsService = quantiteHorsService; }
    public Long getDepenseId() { return depenseId; }
    public void setDepenseId(Long depenseId) { this.depenseId = depenseId; }

    /** Unites reellement utilisables : ni en reparation, ni reformees. */
    
    public int getQuantiteEnService() {
        return Math.max(0, quantite - quantiteEnReparation - quantiteHorsService);
    }

    /** Valeur du lot encore en service — une chaise cassee ne vaut plus rien a l'inventaire. */
    
    public double getValeurEnService() {
        return valeurUnitaire == null ? 0 : valeurUnitaire * getQuantiteEnService();
    }

    public List<MouvementInventaire> getMouvements() { return mouvements; }
    public void setMouvements(List<MouvementInventaire> mouvements) { this.mouvements = mouvements; }
}
