package holyflame.administration.service;

import holyflame.administration.model.CategorieComptable;
import holyflame.administration.model.Depense;
import holyflame.administration.model.Paiement;
import holyflame.administration.repository.CategorieComptableRepository;
import holyflame.administration.repository.DepenseRepository;
import holyflame.administration.repository.PaiementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Grand livre et balance generale.
 *
 * Le logiciel tenait une caisse : des entrees, des sorties, un solde. Il lui manquait les deux
 * documents qu'un expert-comptable demande en premier — le detail des ecritures compte par
 * compte, et leur recapitulatif chiffre.
 *
 * Rien n'est recalcule autrement ici : les montants viennent des memes depenses et des memes
 * paiements que l'ecran Finances. Un chiffre qui differerait entre les deux serait un defaut,
 * pas une nuance de presentation.
 *
 * Les encaissements de scolarite n'ont pas de categorie comptable propre en base — ils sont
 * rattaches a un eleve et a un frais. Ils sont donc imputes au poste 70110 « Scolarite » du
 * plan SYSCOHADA, celui-la meme que le plan cree a l'ouverture de chaque etablissement.
 */
@Service
public class DocumentsComptablesService {

    /** Poste d'imputation des encaissements de scolarite dans le plan SYSCOHADA livre. */
    public static final String POSTE_SCOLARITE = "70110";

    @Autowired private CategorieComptableRepository categorieRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private PaiementRepository paiementRepository;

    /** Une ligne du grand livre : une ecriture datee, imputee a un poste. */
    public record Ecriture(LocalDate date, String libelle, String tiers,
                           double debit, double credit, double soldeCumule,
                           String justificatifPath) {
        /** Vrai quand la piece justificative est consultable depuis le grand livre. */
        public boolean aUnJustificatif() { return justificatifPath != null && !justificatifPath.isBlank(); }
    }

    /** Un compte du grand livre, avec ses ecritures et ses totaux. */
    public record CompteDetaille(String code, String libelle, String sens,
                                 List<Ecriture> ecritures,
                                 double totalDebit, double totalCredit) {
        public double solde() { return totalDebit - totalCredit; }
        public boolean estMouvemente() { return !ecritures.isEmpty(); }
    }

    /** Une ligne de la balance : un compte reduit a ses totaux. */
    public record LigneBalance(String code, String libelle, String sens,
                               int nbEcritures, double totalDebit, double totalCredit) {
        public double solde() { return totalDebit - totalCredit; }
    }

    /** La balance complete, avec ses totaux de controle. */
    public record Balance(List<LigneBalance> lignes, double totalDebit, double totalCredit,
                          LocalDate debut, LocalDate fin) {
        /**
         * En partie double, les deux colonnes s'equilibrent. Ici les ecritures sont saisies en
         * partie simple : l'ecart n'est pas une erreur, c'est le resultat de la periode.
         */
        public double resultat() { return totalCredit - totalDebit; }
        public boolean estBeneficiaire() { return resultat() >= 0; }
    }

    // ── Grand livre ─────────────────────────────────────────────────────

    /**
     * Detail des ecritures, compte par compte, sur une periode.
     *
     * @param avecComptesVides inclut les comptes sans aucun mouvement — utile pour verifier que
     *                         le plan est complet, encombrant pour une lecture courante.
     */
    public List<CompteDetaille> grandLivre(Long etabId, LocalDate debut, LocalDate fin,
                                           boolean avecComptesVides) {
        List<CompteDetaille> comptes = new ArrayList<>();
        List<Depense> depenses = depensesDeLaPeriode(etabId, debut, fin);
        List<Paiement> paiements = paiementsDeLaPeriode(etabId, debut, fin);

        for (CategorieComptable cat : categorieRepository.findByEtablissementIdAndActifTrueOrderByCodeAsc(etabId)) {
            List<Ecriture> ecritures = new ArrayList<>();

            for (Depense d : depenses) {
                if (d.getCategorieComptable() == null
                    || !cat.getId().equals(d.getCategorieComptable().getId())) continue;
                double montant = d.getMontant() != null ? d.getMontant() : 0;
                // Une depense de sens PRODUIT est une recette diverse (don, subvention) :
                // elle se porte au credit, comme un encaissement.
                boolean estRecette = "PRODUIT".equals(d.getSens());
                ecritures.add(new Ecriture(
                    d.getDateDepense(),
                    d.getDesignation(),
                    d.getBeneficiaire(),
                    estRecette ? 0 : montant,
                    estRecette ? montant : 0,
                     0, d.getJustificatifPath()));
            }

            if (POSTE_SCOLARITE.equals(cat.getCode())) {
                for (Paiement p : paiements) {
                    double montant = p.getMontantVerse() != null ? p.getMontantVerse() : 0;
                    String eleve = p.getEleve() != null
                        ? p.getEleve().getNom() + " " + p.getEleve().getPrenom() : "Eleve supprime";
                    String libelle = p.getRecuNumero() != null && !p.getRecuNumero().isBlank()
                        ? "Recu " + p.getRecuNumero() : "Encaissement scolarite";
                    ecritures.add(new Ecriture(
                        p.getDatePaiement() != null ? p.getDatePaiement().toLocalDate() : null,
                        libelle, eleve, 0, montant, 0, null));
                }
            }

            ecritures.sort(Comparator.comparing(Ecriture::date,
                Comparator.nullsLast(Comparator.naturalOrder())));

            double cumul = 0, totalDebit = 0, totalCredit = 0;
            List<Ecriture> avecCumul = new ArrayList<>(ecritures.size());
            for (Ecriture e : ecritures) {
                cumul += e.debit() - e.credit();
                totalDebit += e.debit();
                totalCredit += e.credit();
                avecCumul.add(new Ecriture(e.date(), e.libelle(), e.tiers(), e.debit(), e.credit(),
                    cumul, e.justificatifPath()));
            }

            if (avecComptesVides || !avecCumul.isEmpty()) {
                comptes.add(new CompteDetaille(cat.getCode(), cat.getLibelle(), cat.getSens(),
                    avecCumul, totalDebit, totalCredit));
            }
        }
        return comptes;
    }

    // ── Balance ─────────────────────────────────────────────────────────

    /** Recapitulatif chiffre du grand livre : un compte par ligne, ses totaux, son solde. */
    public Balance balance(Long etabId, LocalDate debut, LocalDate fin) {
        List<LigneBalance> lignes = new ArrayList<>();
        double totalDebit = 0, totalCredit = 0;

        for (CompteDetaille c : grandLivre(etabId, debut, fin, false)) {
            lignes.add(new LigneBalance(c.code(), c.libelle(), c.sens(),
                c.ecritures().size(), c.totalDebit(), c.totalCredit()));
            totalDebit += c.totalDebit();
            totalCredit += c.totalCredit();
        }
        return new Balance(lignes, totalDebit, totalCredit, debut, fin);
    }

    // ── Selection des mouvements ────────────────────────────────────────

    private List<Depense> depensesDeLaPeriode(Long etabId, LocalDate debut, LocalDate fin) {
        return depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId).stream()
            .filter(d -> d.getDateDepense() != null)
            .filter(d -> !d.getDateDepense().isBefore(debut) && !d.getDateDepense().isAfter(fin))
            .toList();
    }

    private List<Paiement> paiementsDeLaPeriode(Long etabId, LocalDate debut, LocalDate fin) {
        return paiementRepository.findByEtablissementId(etabId).stream()
            .filter(p -> p.getDatePaiement() != null)
            .filter(p -> {
                LocalDate d = p.getDatePaiement().toLocalDate();
                return !d.isBefore(debut) && !d.isAfter(fin);
            })
            .toList();
    }
}
