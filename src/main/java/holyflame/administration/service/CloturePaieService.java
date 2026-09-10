package holyflame.administration.service;

import holyflame.administration.model.CloturePaieMensuelle;
import holyflame.administration.model.SalaireMensuel;
import holyflame.administration.repository.CloturePaieMensuelleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cloture de la paie, mois par mois.
 *
 * A ne pas confondre avec {@link ClotureMensuelleService}, qui arrete un mois <em>comptable</em> :
 * une ecole peut vouloir solder sa paie d'aout tout en continuant d'y imputer des depenses, et
 * l'inverse. Les deux verrous sont donc distincts, et portes par deux tables.
 *
 * Un bulletin paye etait deja fige — ni modifiable, ni supprimable. Ce qui manquait, c'est le
 * niveau au-dessus : rien n'empechait d'ajouter en novembre un bulletin « oublie » pour aout.
 * La liste du personnel sans bulletin ne couvre que le mois affiche a l'ecran, et personne ne
 * revient regarder aout ; le verrou annuel, lui, n'arrive qu'en fin d'annee scolaire.
 */
@Service
public class CloturePaieService {

    @Autowired private CloturePaieMensuelleRepository clotureRepository;

    public boolean estCloture(int mois, int annee, Long etabId) {
        if (etabId == null) return false;
        return clotureRepository.existsByEtablissementIdAndMoisAndAnnee(etabId, mois, annee);
    }

    public CloturePaieMensuelle cloture(int mois, int annee, Long etabId) {
        if (etabId == null) return null;
        return clotureRepository.findByEtablissementIdAndMoisAndAnnee(etabId, mois, annee).orElse(null);
    }

    /** Les mois deja clos de l'annee, pour signaler a l'ecran ceux qu'on ne peut plus toucher. */
    public Set<Integer> moisClos(Long etabId, int annee) {
        if (etabId == null) return Set.of();
        return clotureRepository.findByEtablissementIdAndAnneeOrderByMoisAsc(etabId, annee).stream()
            .map(CloturePaieMensuelle::getMois)
            .collect(Collectors.toSet());
    }

    /**
     * Un mois ne se cloture que s'il n'y a plus rien a y faire : tout le personnel actif a un
     * bulletin, et tous ces bulletins sont payes. Cloturer un mois ou il reste un bulletin en
     * attente reviendrait a enfermer quelqu'un dehors — son salaire ne pourrait plus etre paye
     * sans rouvrir le mois.
     */
    public boolean peutCloturer(List<SalaireMensuel> bulletinsDuMois, int personnelSansBulletin) {
        if (bulletinsDuMois.isEmpty()) return false;
        if (personnelSansBulletin > 0) return false;
        return bulletinsDuMois.stream().allMatch(s -> "PAYE".equals(s.getStatut()));
    }

    /** Ce qui manque pour pouvoir cloturer, dit a l'utilisateur plutot que deduit d'un bouton grise. */
    public String obstacleACloture(List<SalaireMensuel> bulletinsDuMois, int personnelSansBulletin) {
        if (bulletinsDuMois.isEmpty()) {
            return "Aucun bulletin n'a encore ete etabli pour ce mois.";
        }
        if (personnelSansBulletin > 0) {
            return personnelSansBulletin + " membre(s) du personnel actif n'ont pas de bulletin pour ce mois.";
        }
        long enAttente = bulletinsDuMois.stream().filter(s -> !"PAYE".equals(s.getStatut())).count();
        if (enAttente > 0) {
            return enAttente + " bulletin(s) ne sont pas encore payes.";
        }
        return null;
    }

    public CloturePaieMensuelle cloturer(int mois, int annee, Long etabId, String auteur,
                                         List<SalaireMensuel> bulletinsDuMois) {
        CloturePaieMensuelle existante = cloture(mois, annee, etabId);
        if (existante != null) return existante;

        CloturePaieMensuelle c = new CloturePaieMensuelle();
        c.setEtablissementId(etabId);
        c.setMois(mois);
        c.setAnnee(annee);
        c.setDateCloture(LocalDateTime.now());
        c.setClotureePar(auteur);
        c.setNbBulletins(bulletinsDuMois.size());
        c.setTotalNet(bulletinsDuMois.stream()
            .mapToDouble(s -> s.getNetAPayer() != null ? s.getNetAPayer() : 0).sum());
        return clotureRepository.save(c);
    }

    public void rouvrir(int mois, int annee, Long etabId) {
        clotureRepository.findByEtablissementIdAndMoisAndAnnee(etabId, mois, annee)
            .ifPresent(clotureRepository::delete);
    }
}
