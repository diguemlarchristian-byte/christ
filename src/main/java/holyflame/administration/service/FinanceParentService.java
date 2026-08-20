package holyflame.administration.service;

import holyflame.administration.model.ArriereEleve;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.Parametre;
import holyflame.administration.repository.ArriereEleveRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.ParametreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcule le solde de scolarite restant a regler pour les enfants d'un parent.
 * Logique partagee entre le suivi financier detaille et le resume du tableau de bord.
 */
@Service
public class FinanceParentService {

    @Autowired private HorlogeService horlogeService;

    private static final Map<String, Integer> MOIS_FR = Map.ofEntries(
        Map.entry("janvier", 1), Map.entry("fevrier", 2), Map.entry("février", 2), Map.entry("mars", 3),
        Map.entry("avril", 4), Map.entry("mai", 5), Map.entry("juin", 6), Map.entry("juillet", 7),
        Map.entry("aout", 8), Map.entry("août", 8), Map.entry("septembre", 9), Map.entry("octobre", 10),
        Map.entry("novembre", 11), Map.entry("decembre", 12), Map.entry("décembre", 12)
    );

    private static final DateTimeFormatter FMT_PARAM_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private FraisScolariteRepository fraisScolariteRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private ArriereEleveRepository arriereEleveRepository;
    @Autowired private ParametreRepository parametreRepository;

    public static class ResumeSolde {
        public List<Map<String, Object>> lignes = new ArrayList<>();
        public double soldeTotalARegler = 0;
        public int nbEnRetard = 0;
        public LocalDate prochaineEcheance = null;
        public Map<String, Object> prochaineLigne = null;
    }

    public ResumeSolde calculerResume(List<Eleve> enfants, Long etabId) {
        ResumeSolde resume = new ResumeSolde();

        List<FraisScolarite> tousFrais = etabId != null
            ? fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId)
            : List.of();

        List<Paiement> tousPaiements = new ArrayList<>();
        for (Eleve e : enfants) tousPaiements.addAll(paiementRepository.findByEleveId(e.getId()));

        LocalDate aujourdHui = horlogeService.aujourdHui(etabId);

        for (Eleve enfant : enfants) {
            if (enfant.getClasse() == null) continue;
            String niveau = enfant.getClasse().getNiveau();
            String anneeEnfant = enfant.getClasse().getAnneeScolaire();
            List<FraisScolarite> applicables = tousFrais.stream()
                .filter(f -> f.getNiveauCible() == null || f.getNiveauCible().isBlank()
                    || f.getNiveauCible().equalsIgnoreCase(niveau))
                .toList();

            // Tous les paiements de cet eleve. NB : on ne filtre plus ici par egalite stricte sur
            // Paiement.anneeScolaire, car ce champ est derive de la date calendaire du versement
            // (voir AnneeScolaireUtil.pour) alors que l'annee "active" d'un etablissement peut deja
            // avoir ete avancee manuellement (ex: classes 2026-2027 preparees des aout 2026) — un
            // versement pourtant bien de l'annee en cours pouvait donc etre ecarte a tort et le
            // solde de l'eleve semblait fige malgre un paiement reel.
            List<Paiement> paiementsEleve = tousPaiements.stream()
                .filter(p -> p.getEleve() != null && p.getEleve().getId().equals(enfant.getId()))
                .toList();

            // Solde des versements enregistres sans frais precis selectionne (cas normal d'un
            // encaissement enregistre depuis le Journal de caisse) : sans imputation explicite,
            // ce montant restait jusqu'ici totalement ignore et le solde de l'eleve ne bougeait
            // jamais, meme apres un paiement bien enregistre. On l'impute desormais sur les frais
            // les plus anciennement echus d'abord, comme un versement generique.
            double soldeLibre = paiementsEleve.stream()
                .filter(p -> p.getFraisScolarite() == null)
                .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)
                .sum();

            List<Map<String, Object>> lignesEnfant = new ArrayList<>();
            for (FraisScolarite f : applicables) {
                double montant = f.getMontant() != null ? f.getMontant() : 0;
                double verseAffecte = paiementsEleve.stream()
                    .filter(p -> p.getFraisScolarite() != null && p.getFraisScolarite().getId().equals(f.getId()))
                    .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)
                    .sum();
                LocalDate echeanceDate = calculerEcheance(f.getEcheance(), anneeEnfant, etabId);

                Map<String, Object> ligne = new LinkedHashMap<>();
                ligne.put("designation", f.getDesignation());
                ligne.put("categorie", f.getTypeFrais());
                ligne.put("enfant", enfant);
                ligne.put("montant", montant);
                ligne.put("resteAPayer", Math.max(0, montant - verseAffecte));
                ligne.put("echeanceDate", echeanceDate);
                ligne.put("fraisId", f.getId());
                ligne.put("eleveId", enfant.getId());
                // Un frais non-obligatoire (facultatif) ne doit jamais empecher un eleve d'etre
                // considere a jour/solde : il reste visible et payable, mais son impaye ne compte
                // ni dans le solde total a regler, ni dans le statut EN_ATTENTE/EN_RETARD.
                ligne.put("obligatoire", f.isObligatoire());
                lignesEnfant.add(ligne);
            }

            // Un versement libre (non affecte a un frais precis) s'impute d'abord sur les frais
            // obligatoires (les plus anciennement echus en premier), puis seulement s'il en reste
            // sur les frais facultatifs — jamais l'inverse.
            lignesEnfant.sort(Comparator
                .comparing((Map<String, Object> l) -> Boolean.TRUE.equals(l.get("obligatoire")) ? 0 : 1)
                .thenComparing(l -> (LocalDate) l.get("echeanceDate"), Comparator.nullsLast(Comparator.naturalOrder())));

            for (Map<String, Object> ligne : lignesEnfant) {
                double montant = (double) ligne.get("montant");
                double reste = (double) ligne.get("resteAPayer");
                if (soldeLibre > 0 && reste > 0) {
                    double imputation = Math.min(soldeLibre, reste);
                    reste -= imputation;
                    soldeLibre -= imputation;
                    ligne.put("resteAPayer", reste);
                }
                boolean obligatoire = (boolean) ligne.get("obligatoire");
                LocalDate echeanceDate = (LocalDate) ligne.get("echeanceDate");
                String statut;
                if (montant > 0 && reste <= 0) statut = "PAYE";
                else if (!obligatoire) statut = "OPTIONNEL";
                else if (echeanceDate != null && echeanceDate.isBefore(aujourdHui)) statut = "EN_RETARD";
                else statut = "EN_ATTENTE";
                ligne.put("statut", statut);
                resume.lignes.add(ligne);

                if (obligatoire && !"PAYE".equals(statut)) {
                    resume.soldeTotalARegler += reste;
                    if ("EN_RETARD".equals(statut)) resume.nbEnRetard++;
                    if (echeanceDate != null && (resume.prochaineEcheance == null || echeanceDate.isBefore(resume.prochaineEcheance))) {
                        resume.prochaineEcheance = echeanceDate;
                        resume.prochaineLigne = ligne;
                    } else if (resume.prochaineLigne == null) {
                        resume.prochaineLigne = ligne;
                    }
                }
            }

            for (ArriereEleve a : arriereEleveRepository.findByEleveIdOrderByAnneeScolaireOrigineDesc(enfant.getId())) {
                double du = a.getMontant() != null ? a.getMontant() : 0;
                double regle = a.getMontantRegle() != null ? a.getMontantRegle() : 0;
                double reste = du - regle;
                if (reste <= 0) continue;

                Map<String, Object> ligne = new LinkedHashMap<>();
                ligne.put("designation", "Arriérés " + a.getAnneeScolaireOrigine());
                ligne.put("categorie", "ARRIERES");
                ligne.put("enfant", enfant);
                ligne.put("montant", du);
                ligne.put("resteAPayer", reste);
                ligne.put("echeanceDate", null);
                ligne.put("statut", "EN_RETARD");
                ligne.put("arriereId", a.getId());
                ligne.put("eleveId", enfant.getId());
                ligne.put("obligatoire", true);
                resume.lignes.add(ligne);
                resume.soldeTotalARegler += reste;
                resume.nbEnRetard++;
            }
        }

        resume.lignes.sort(Comparator.comparing(
            (Map<String, Object> l) -> "PAYE".equals(l.get("statut")) ? 1 : 0
        ).thenComparing(l -> (LocalDate) l.get("echeanceDate"), Comparator.nullsLast(Comparator.naturalOrder())));

        return resume;
    }

    public LocalDate calculerEcheance(String echeance, String anneeScolaire, Long etabId) {
        if (echeance == null) return null;
        String cle = echeance.trim().toUpperCase();

        // T1 / T2 / T3 : echeance derivee de la date de debut du trimestre configuree dans Parametres
        if (etabId != null && ("T1".equals(cle) || "T2".equals(cle) || "T3".equals(cle))) {
            return parametreRepository.findByCleAndEtablissementId(cle + "_DEBUT", etabId)
                .map(Parametre::getValeur)
                .map(v -> { try { return LocalDate.parse(v, FMT_PARAM_DATE); } catch (Exception e) { return null; } })
                .orElse(null);
        }

        // Retro-compatibilite : echeance donnee comme un nom de mois en toutes lettres
        if (anneeScolaire == null) return null;
        Integer mois = MOIS_FR.get(echeance.trim().toLowerCase());
        if (mois == null) return null;
        int anneeDebut;
        try {
            anneeDebut = Integer.parseInt(anneeScolaire.split("-")[0].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        int annee = mois >= 9 ? anneeDebut : anneeDebut + 1;
        return LocalDate.of(annee, mois, 5);
    }
}
