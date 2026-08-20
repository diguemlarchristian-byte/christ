package holyflame.administration.service;

import holyflame.administration.model.CategorieComptable;
import holyflame.administration.repository.CategorieComptableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan comptable standard (charges/produits) commun a tous les etablissements. Auparavant seede
 * une seule fois au demarrage pour le tout premier etablissement uniquement (DataInitializer),
 * ce qui laissait tout etablissement cree ensuite — a l'inscription ou via une nouvelle base —
 * sans aucune categorie comptable : impossible d'enregistrer une depense ou une ligne de budget.
 */
@Service
public class PlanComptableService {

    @Autowired private CategorieComptableRepository categorieComptableRepository;

    public record Poste(String code, String libelle, String sens, String groupe) {}

    private static final String CHARGE = "CHARGE";
    private static final String PRODUIT = "PRODUIT";
    private static final String FOURNITURES = "FOURNITURES_SCOLAIRES";
    private static final String ENTRETIEN = "ENTRETIEN_SALUBRITE";
    private static final String FONCTIONNEMENT = "FONCTIONNEMENT";
    private static final String INVESTISSEMENT = "INVESTISSEMENT";

    private static final Poste[] PLAN = {
        new Poste("21820", "Materiels de transports", CHARGE, INVESTISSEMENT),
        new Poste("21830", "Ordinateurs ou Imprimantes", CHARGE, INVESTISSEMENT),
        new Poste("21840", "Equipements", CHARGE, INVESTISSEMENT),
        new Poste("60110", "Electrique (materiel et reparation)", CHARGE, FONCTIONNEMENT),
        new Poste("60450", "Impressions et Informatique", CHARGE, FONCTIONNEMENT),
        new Poste("60460", "Produits d'entretien", CHARGE, ENTRETIEN),
        new Poste("60470", "Fourniture de bureau", CHARGE, FOURNITURES),
        new Poste("60472", "Agios", CHARGE, FONCTIONNEMENT),
        new Poste("60473", "Fournitures scolaires", CHARGE, FOURNITURES),
        new Poste("60530", "Gazoil groupe", CHARGE, FONCTIONNEMENT),
        new Poste("60531", "Eau", CHARGE, FONCTIONNEMENT),
        new Poste("60500", "SNE", CHARGE, FONCTIONNEMENT),
        new Poste("60510", "Plomberie", CHARGE, INVESTISSEMENT),
        new Poste("60580", "Mobilier (achat)", CHARGE, INVESTISSEMENT),
        new Poste("61412", "Deplacement, taxis", CHARGE, FONCTIONNEMENT),
        new Poste("62420", "Entretien mobilier", CHARGE, ENTRETIEN),
        new Poste("62430", "Entretien Batiment", CHARGE, INVESTISSEMENT),
        new Poste("62440", "Entretien groupe", CHARGE, FONCTIONNEMENT),
        new Poste("62650", "Bibliotheque, manuels", CHARGE, FOURNITURES),
        new Poste("62810", "Tel-Corr-Internet", CHARGE, FONCTIONNEMENT),
        new Poste("63840", "Voyages", CHARGE, FONCTIONNEMENT),
        new Poste("65130", "Retour de scolarite", CHARGE, FONCTIONNEMENT),
        new Poste("65820", "Reduction de la scolarite", CHARGE, FONCTIONNEMENT),
        new Poste("66120", "Salaires Vacataires", CHARGE, FONCTIONNEMENT),
        new Poste("66110", "Salaires Permanants", CHARGE, FONCTIONNEMENT),
        new Poste("66170", "Achat de Tenues", CHARGE, FOURNITURES),
        new Poste("66160", "Honoraires Cabinet d'avocat", CHARGE, FONCTIONNEMENT),
        new Poste("66161", "Salaires occasionnels", CHARGE, FONCTIONNEMENT),
        new Poste("66180", "Publicite", CHARGE, FONCTIONNEMENT),
        new Poste("66181", "Loisirs : enseignants", CHARGE, FONCTIONNEMENT),
        new Poste("66182", "Prelevement", CHARGE, FONCTIONNEMENT),
        new Poste("66183", "Loisir : des eleves", CHARGE, FONCTIONNEMENT),
        new Poste("66420", "Charges sociales (CNPS)", CHARGE, FONCTIONNEMENT),
        new Poste("66421", "Impot sur salaire", CHARGE, FONCTIONNEMENT),
        new Poste("66430", "Cotisations", CHARGE, FONCTIONNEMENT),
        new Poste("66431", "Accompagnement Administratif", CHARGE, FONCTIONNEMENT),
        new Poste("66840", "Medicaments, soins medicaux", CHARGE, FONCTIONNEMENT),
        new Poste("66841", "Assurances", CHARGE, FONCTIONNEMENT),
        new Poste("66850", "Depenses Examens", CHARGE, FOURNITURES),
        new Poste("66881", "Formation", CHARGE, FONCTIONNEMENT),
        new Poste("67500", "Loyer", CHARGE, FONCTIONNEMENT),
        new Poste("67600", "Depenses chantier", CHARGE, INVESTISSEMENT),
        new Poste("70110", "Scolarite", PRODUIT, null),
        new Poste("70720", "Subventions diverses", PRODUIT, null),
        new Poste("70730", "Locations : locaux, manuels", PRODUIT, null),
        new Poste("71824", "Dons d'organisme", PRODUIT, null),
        new Poste("71825", "Dons divers recus", PRODUIT, null),
        new Poste("72100", "Vente de Tenues de classe", PRODUIT, null),
        new Poste("75820", "APE", PRODUIT, null),
        new Poste("77100", "Ventes diverses", PRODUIT, null),
        new Poste("77110", "Arrieres scolarites", PRODUIT, null),
        new Poste("77120", "Test d'entree", PRODUIT, null),
        new Poste("77170", "Vente tenue de sport", PRODUIT, null),
        new Poste("77180", "Vente Pull over", PRODUIT, null),
        new Poste("77190", "Somme empruntee", PRODUIT, null),
        new Poste("77160", "Cahiers (Inscriptions + Test)", PRODUIT, null),
    };

    /** A appeler a la creation de tout etablissement, et defensivement avant tout affichage
        du module Finances : sans effet si l'etablissement a deja son plan comptable. */
    public void seedSiVide(Long etabId) {
        if (etabId == null || !categorieComptableRepository.findByEtablissementIdAndActifTrueOrderByCodeAsc(etabId).isEmpty()) {
            return;
        }
        importerPostes(etabId, java.util.Arrays.asList(PLAN));
    }

    // ── Modele "SYSCOHADA simplifie — etablissement scolaire" ────────────────────────────
    // Codes alignes sur la nomenclature officielle SYSCOHADA revise (postes a 3-4 chiffres,
    // niveau usuel pour une petite structure), a la difference du plan par defaut ci-dessus qui
    // utilise une numerotation maison a 5 chiffres. Propose comme modele optionnel, applicable a
    // la demande depuis Finances > Parametrage, sans jamais toucher aux categories deja en place —
    // seuls les codes absents sont ajoutes (voir importerPostes).
    private static final Poste[] PLAN_SYSCOHADA_SCOLAIRE = {
        new Poste("604", "Achats stockes — Fournitures scolaires et pedagogiques", CHARGE, FOURNITURES),
        new Poste("6051", "Fournitures non stockables — Eau", CHARGE, FONCTIONNEMENT),
        new Poste("6052", "Fournitures non stockables — Electricite", CHARGE, FONCTIONNEMENT),
        new Poste("6055", "Fournitures d'entretien", CHARGE, ENTRETIEN),
        new Poste("6081", "Achats de consommables (cantine)", CHARGE, FONCTIONNEMENT),
        new Poste("6141", "Transport scolaire", CHARGE, FONCTIONNEMENT),
        new Poste("6224", "Locations de materiel", CHARGE, FONCTIONNEMENT),
        new Poste("6241", "Entretien et reparations des batiments", CHARGE, ENTRETIEN),
        new Poste("6242", "Entretien et reparations du materiel", CHARGE, ENTRETIEN),
        new Poste("6250", "Primes d'assurance", CHARGE, FONCTIONNEMENT),
        new Poste("6281", "Frais de telephone", CHARGE, FONCTIONNEMENT),
        new Poste("6282", "Frais internet", CHARGE, FONCTIONNEMENT),
        new Poste("6311", "Frais bancaires", CHARGE, FONCTIONNEMENT),
        new Poste("6411", "Impots et taxes directs", CHARGE, FONCTIONNEMENT),
        new Poste("6611", "Remunerations — personnel enseignant", CHARGE, FONCTIONNEMENT),
        new Poste("6612", "Remunerations — personnel administratif", CHARGE, FONCTIONNEMENT),
        new Poste("6641", "Charges sociales (CNPS)", CHARGE, FONCTIONNEMENT),
        new Poste("6811", "Dotations aux amortissements", CHARGE, INVESTISSEMENT),
        new Poste("2183", "Acquisitions de materiel informatique/mobilier", CHARGE, INVESTISSEMENT),
        new Poste("7061", "Prestations de services — Frais de scolarite", PRODUIT, null),
        new Poste("7062", "Prestations de services — Frais d'inscription", PRODUIT, null),
        new Poste("7063", "Prestations de services — Frais de cantine", PRODUIT, null),
        new Poste("7064", "Prestations de services — Frais de transport", PRODUIT, null),
        new Poste("7071", "Produits accessoires — Vente de tenues/fournitures", PRODUIT, null),
        new Poste("7581", "Produits divers de gestion courante", PRODUIT, null),
        new Poste("7582", "Dons et subventions recus", PRODUIT, null),
        new Poste("7710", "Produits financiers", PRODUIT, null),
    };

    public List<Poste> postesModeleSyscohadaScolaire() {
        return java.util.List.of(PLAN_SYSCOHADA_SCOLAIRE);
    }

    /** Insere chaque poste absent du plan comptable de l'etablissement (par code, insensible a la
        casse) ; ignore silencieusement ceux deja presents pour ne jamais ecraser une categorie deja
        utilisee par des depenses/lignes de budget existantes. Renvoie le nombre reellement cree. */
    public int importerPostes(Long etabId, List<Poste> postes) {
        int crees = 0;
        for (Poste p : postes) {
            if (etabId != null && categorieComptableRepository.findByCodeAndEtablissementId(p.code(), etabId).isPresent()) {
                continue;
            }
            CategorieComptable c = new CategorieComptable();
            c.setCode(p.code());
            c.setLibelle(p.libelle());
            c.setSens(p.sens());
            c.setGroupe(p.groupe());
            c.setEtablissementId(etabId);
            categorieComptableRepository.save(c);
            crees++;
        }
        return crees;
    }
}
