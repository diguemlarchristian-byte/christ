package holyflame.administration.service;

import holyflame.administration.model.Echeance;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.RemiseEleve;
import holyflame.administration.repository.EcheanceRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.RemiseEleveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ce que doit un eleve, une fois ses remises deduites.
 *
 * Cette question se calculait a deux endroits differents dans FinancesController, avec la meme
 * formule recopiee. Toute regle nouvelle — une remise, par exemple — devait donc etre ajoutee
 * deux fois, ou l'un des deux ecrans donnait un chiffre faux. Elle vit desormais ici, une seule
 * fois, et les ecrans la lisent.
 */
@Service
public class SituationFinanciereService {

    @Autowired private FraisScolariteRepository fraisRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private RemiseEleveRepository remiseRepository;
    @Autowired private EcheanceRepository echeanceRepository;

    /** Situation complete d'un eleve pour une annee. */
    public record Situation(Long eleveId, double montantBrut, double remises,
                            double montantDu, double verse,
                            List<Echeance> echeances, List<RemiseEleve> remisesAccordees) {
        public double reste() { return Math.max(0, montantDu - verse); }
        public boolean estSolde() { return reste() <= 0.009; }
        /** Vrai quand l'eleve ne doit rien parce qu'il a ete exonere, non parce qu'il a paye. */
        public boolean estExonere() { return montantDu <= 0.009 && remises > 0; }
        public double tauxReglement() {
            return montantDu <= 0 ? 100.0 : Math.min(100.0, verse * 100.0 / montantDu);
        }
        public List<Echeance> echeancesEnRetard(LocalDate jour) {
            return echeances.stream().filter(e -> e.estEnRetard(jour)).toList();
        }
        public Echeance prochaineEcheance(LocalDate jour) {
            return echeances.stream()
                .filter(e -> !e.estSoldee())
                .min((a, b) -> a.getDatePrevue().compareTo(b.getDatePrevue()))
                .orElse(null);
        }
    }

    // ── Calcul du du ────────────────────────────────────────────────────

    /**
     * Frais obligatoires applicables a un eleve, avant remise.
     *
     * Un frais sans niveau cible concerne toute l'ecole ; sinon il ne vaut que pour les eleves
     * du niveau vise.
     */
    public double montantBrut(Eleve eleve, List<FraisScolarite> fraisObligatoires) {
        return fraisObligatoires.stream()
            .filter(f -> concerne(f, eleve))
            .mapToDouble(f -> f.getMontant() != null ? f.getMontant() : 0)
            .sum();
    }

    /**
     * Total des remises accordees a un eleve.
     *
     * Une remise visant un frais precis ne s'applique qu'a lui ; une remise sans frais vise
     * porte sur l'ensemble de ce que l'eleve doit. Chaque deduction est plafonnee au frais
     * qu'elle reduit, et le total au montant brut : une ecole ne peut pas devoir de l'argent
     * a une famille au titre d'une reduction.
     */
    public double totalRemises(Eleve eleve, List<FraisScolarite> fraisObligatoires,
                               List<RemiseEleve> remisesDeLEleve) {
        if (remisesDeLEleve.isEmpty()) return 0;

        double brut = montantBrut(eleve, fraisObligatoires);
        double total = 0;

        for (RemiseEleve r : remisesDeLEleve) {
            if (r.getFraisScolariteId() == null) {
                total += r.montantDeduit(brut);
            } else {
                double montantDuFrais = fraisObligatoires.stream()
                    .filter(f -> f.getId().equals(r.getFraisScolariteId()) && concerne(f, eleve))
                    .mapToDouble(f -> f.getMontant() != null ? f.getMontant() : 0)
                    .sum();
                total += r.montantDeduit(montantDuFrais);
            }
        }
        return Math.min(total, brut);
    }

    /** Situation d'un eleve : ce qu'il doit, ce qu'il a verse, ses echeances. */
    public Situation situation(Eleve eleve, String anneeScolaire, List<FraisScolarite> fraisObligatoires) {
        List<RemiseEleve> remises = remiseRepository.findByEleveIdAndAnneeScolaire(eleve.getId(), anneeScolaire);
        double brut = montantBrut(eleve, fraisObligatoires);
        double totalRemises = totalRemises(eleve, fraisObligatoires, remises);

        double verse = paiementRepository.findByEtablissementId(eleve.getEtablissementId()).stream()
            .filter(p -> p.getEleve() != null && eleve.getId().equals(p.getEleve().getId()))
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)
            .sum();

        List<Echeance> echeances = echeanceRepository
            .findByEleveIdAndAnneeScolaireOrderByRangAsc(eleve.getId(), anneeScolaire);

        return new Situation(eleve.getId(), brut, totalRemises,
            Math.max(0, brut - totalRemises), verse, echeances, remises);
    }

    /** Situations de plusieurs eleves d'un coup, pour les tableaux de suivi. */
    public Map<Long, Situation> situations(List<Eleve> eleves, Long etabId, String anneeScolaire) {
        List<FraisScolarite> fraisObligatoires = fraisObligatoires(etabId);
        return eleves.stream().collect(Collectors.toMap(
            Eleve::getId, e -> situation(e, anneeScolaire, fraisObligatoires), (a, b) -> a));
    }

    public List<FraisScolarite> fraisObligatoires(Long etabId) {
        return fraisRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId).stream()
            .filter(FraisScolarite::isObligatoire)
            .toList();
    }

    // ── Echeancier ──────────────────────────────────────────────────────

    /**
     * Etale ce que doit un eleve sur plusieurs versements mensuels.
     *
     * Le dernier versement absorbe l'arrondi : trois versements sur 100 000 F donnent
     * 33 333 + 33 333 + 33 334, et la somme retombe juste. Sans cela, il resterait un franc
     * du a la fin de l'annee, et l'eleve n'apparaitrait jamais comme solde.
     */
    public List<Echeance> planifier(Eleve eleve, String anneeScolaire, int nbVersements,
                                    LocalDate premiereEcheance, double montantTotal, Long etabId) {
        if (nbVersements < 1) throw new IllegalArgumentException("Au moins un versement est necessaire.");
        if (montantTotal <= 0) throw new IllegalArgumentException("Le montant a etaler doit etre positif.");

        echeanceRepository.deleteByEleveIdAndAnneeScolaire(eleve.getId(), anneeScolaire);

        long centimes = Math.round(montantTotal * 100);
        long part = centimes / nbVersements;
        long reste = centimes - part * nbVersements;

        List<Echeance> creees = new ArrayList<>(nbVersements);
        for (int i = 1; i <= nbVersements; i++) {
            Echeance e = new Echeance();
            e.setEleveId(eleve.getId());
            e.setRang(i);
            e.setMontantPrevu((part + (i == nbVersements ? reste : 0)) / 100.0);
            e.setDatePrevue(premiereEcheance.plusMonths(i - 1L));
            e.setAnneeScolaire(anneeScolaire);
            e.setEtablissementId(etabId);
            creees.add(echeanceRepository.save(e));
        }
        return creees;
    }

    /**
     * Impute un encaissement sur les echeances, de la plus ancienne a la plus recente.
     *
     * C'est l'usage courant : une famille qui verse solde d'abord son retard. Le surplus qui
     * depasse le dernier versement n'est pas perdu — il reste visible dans le total verse.
     */
    public void imputer(Long eleveId, String anneeScolaire, double montant, LocalDate date) {
        double restant = montant;
        for (Echeance e : echeanceRepository.findByEleveIdAndAnneeScolaireOrderByRangAsc(eleveId, anneeScolaire)) {
            if (restant <= 0) break;
            double aCombler = e.resteDu();
            if (aCombler <= 0) continue;

            double impute = Math.min(restant, aCombler);
            e.setMontantRegle((e.getMontantRegle() != null ? e.getMontantRegle() : 0) + impute);
            if (e.estSoldee()) e.setDateReglement(date);
            echeanceRepository.save(e);
            restant -= impute;
        }
    }

    /** Echeances depassees et non soldees, tous eleves confondus. */
    public List<Echeance> echeancesEnRetard(Long etabId, String anneeScolaire, LocalDate jour) {
        return echeanceRepository.findByEtablissementIdAndAnneeScolaireOrderByDatePrevueAsc(etabId, anneeScolaire)
            .stream()
            .filter(e -> e.estEnRetard(jour))
            .toList();
    }

    private boolean concerne(FraisScolarite f, Eleve eleve) {
        if (f.getNiveauCible() == null || f.getNiveauCible().isBlank()) return true;
        return eleve.getClasse() != null
            && f.getNiveauCible().equalsIgnoreCase(eleve.getClasse().getNiveau());
    }
}
