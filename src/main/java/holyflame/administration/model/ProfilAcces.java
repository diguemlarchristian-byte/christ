package holyflame.administration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gabarit d'acces reutilisable ("profil type") : un jeu de modules nomme, defini une fois par
 * l'ADMIN puis applicable en un clic a n'importe quel compte compatible (meme type DIRECTEUR ou
 * FINANCE), au lieu de recocher les memes cases compte par compte.
 */
@Entity
@Table(name = "profils_acces")
public class ProfilAcces {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String type; // DIRECTEUR ou FINANCE — doit correspondre au typeAcces du compte cible

    @Column(length = 500)
    private String modules; // codes CSV (memes codes que Utilisateur.modulesOptionnels/modulesFinance)

    private Long etablissementId;

    public ProfilAcces() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getEtablissementId() {
        return etablissementId;
    }

    public void setEtablissementId(Long etablissementId) {
        this.etablissementId = etablissementId;
    }

    public Set<String> getModulesActifs() {
        if (modules == null || modules.isBlank()) return Set.of();
        return Arrays.stream(modules.split(","))
            .map(String::trim).filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }

    public void setModulesActifs(Set<String> modules) {
        this.modules = (modules == null || modules.isEmpty()) ? "" : String.join(",", modules);
    }
}
