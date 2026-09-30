package holyflame.administration;

import holyflame.administration.model.CritereAdmission;
import holyflame.administration.model.DemandeInscription;
import holyflame.administration.model.NoteAdmission;
import holyflame.administration.repository.CritereAdmissionRepository;
import holyflame.administration.repository.DemandeInscriptionRepository;
import holyflame.administration.repository.NoteAdmissionRepository;
import holyflame.administration.service.EtudeDossierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que la commission calcule, et ce qu'elle doit pouvoir expliquer.
 *
 * Une famille refusee demande pourquoi, et une famille placee derriere une autre demande
 * pourquoi elle est derriere. Ces deux questions reviennent chaque annee, et c'est le
 * logiciel qui doit y repondre — pas la memoire de la personne qui etait dans la salle.
 */
@DisplayName("Etude d'un dossier de candidature")
class EtudeDossierServiceTest {

    // ── Depots en memoire : ces regles se verifient sans base ────────────

    private static class DepotCriteres implements CritereAdmissionRepository {
        final List<CritereAdmission> contenu = new ArrayList<>();
        long sequence = 1;
        @Override public CritereAdmission save(CritereAdmission c) {
            if (c.getId() == null) c.setId(sequence++);
            contenu.removeIf(x -> c.getId().equals(x.getId()));
            contenu.add(c);
            return c;
        }
        @Override public List<CritereAdmission> findByEtablissementIdAndActifTrueOrderByOrdreAsc(Long e) {
            return contenu.stream().filter(c -> e.equals(c.getEtablissementId()) && c.isActif())
                .sorted((a, b) -> a.getOrdre() - b.getOrdre()).toList();
        }
        @Override public List<CritereAdmission> findByEtablissementIdOrderByOrdreAsc(Long e) {
            return contenu.stream().filter(c -> e.equals(c.getEtablissementId()))
                .sorted((a, b) -> a.getOrdre() - b.getOrdre()).toList();
        }
        @Override public Optional<CritereAdmission> findById(Long id) {
            return contenu.stream().filter(c -> id.equals(c.getId())).findFirst();
        }
        @Override public List<CritereAdmission> findAll() { return contenu; }
        @Override public <S extends CritereAdmission> List<S> saveAll(Iterable<S> e) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { throw new UnsupportedOperationException(); }
        @Override public List<CritereAdmission> findAllById(Iterable<Long> i) { throw new UnsupportedOperationException(); }
        @Override public long count() { return contenu.size(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(CritereAdmission c) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> i) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends CritereAdmission> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { contenu.clear(); }
        @Override public void flush() { }
        @Override public <S extends CritereAdmission> S saveAndFlush(S e) { return (S) save(e); }
        @Override public <S extends CritereAdmission> List<S> saveAllAndFlush(Iterable<S> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<CritereAdmission> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> i) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { contenu.clear(); }
        @Override public CritereAdmission getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public CritereAdmission getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public CritereAdmission getReferenceById(Long id) { throw new UnsupportedOperationException(); }
        @Override public <S extends CritereAdmission> Optional<S> findOne(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends CritereAdmission> List<S> findAll(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends CritereAdmission> List<S> findAll(Example<S> e, Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends CritereAdmission> Page<S> findAll(Example<S> e, Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends CritereAdmission> long count(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends CritereAdmission> boolean exists(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends CritereAdmission, R> R findBy(Example<S> e, Function<FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
        @Override public List<CritereAdmission> findAll(Sort s) { return contenu; }
        @Override public Page<CritereAdmission> findAll(Pageable p) { throw new UnsupportedOperationException(); }
    }

    private static class DepotNotes implements NoteAdmissionRepository {
        final List<NoteAdmission> contenu = new ArrayList<>();
        long sequence = 1;
        @Override public NoteAdmission save(NoteAdmission n) {
            if (n.getId() == null) n.setId(sequence++);
            contenu.add(n);
            return n;
        }
        @Override public <S extends NoteAdmission> List<S> saveAll(Iterable<S> e) {
            List<S> out = new ArrayList<>();
            for (S n : e) { save(n); out.add(n); }
            return out;
        }
        @Override public List<NoteAdmission> findByDemandeId(Long d) {
            return contenu.stream().filter(n -> d.equals(n.getDemandeId())).toList();
        }
        @Override public List<NoteAdmission> findByDemandeIdIn(List<Long> ids) {
            return contenu.stream().filter(n -> ids.contains(n.getDemandeId())).toList();
        }
        @Override public void deleteByDemandeId(Long d) { contenu.removeIf(n -> d.equals(n.getDemandeId())); }
        @Override public Optional<NoteAdmission> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { throw new UnsupportedOperationException(); }
        @Override public List<NoteAdmission> findAll() { return contenu; }
        @Override public List<NoteAdmission> findAllById(Iterable<Long> i) { throw new UnsupportedOperationException(); }
        @Override public long count() { return contenu.size(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(NoteAdmission n) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> i) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends NoteAdmission> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { contenu.clear(); }
        @Override public void flush() { }
        @Override public <S extends NoteAdmission> S saveAndFlush(S e) { return (S) save(e); }
        @Override public <S extends NoteAdmission> List<S> saveAllAndFlush(Iterable<S> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<NoteAdmission> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> i) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { contenu.clear(); }
        @Override public NoteAdmission getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public NoteAdmission getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public NoteAdmission getReferenceById(Long id) { throw new UnsupportedOperationException(); }
        @Override public <S extends NoteAdmission> Optional<S> findOne(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends NoteAdmission> List<S> findAll(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends NoteAdmission> List<S> findAll(Example<S> e, Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends NoteAdmission> Page<S> findAll(Example<S> e, Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends NoteAdmission> long count(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends NoteAdmission> boolean exists(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends NoteAdmission, R> R findBy(Example<S> e, Function<FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
        @Override public List<NoteAdmission> findAll(Sort s) { return contenu; }
        @Override public Page<NoteAdmission> findAll(Pageable p) { throw new UnsupportedOperationException(); }
    }

    private static class DepotDemandes implements DemandeInscriptionRepository {
        final List<DemandeInscription> contenu = new ArrayList<>();
        @Override public DemandeInscription save(DemandeInscription d) {
            contenu.removeIf(x -> d.getId().equals(x.getId()));
            contenu.add(d);
            return d;
        }
        @Override public List<DemandeInscription> findByEtablissementIdAndStatutOrderByDateDemandeDesc(Long e, String s) {
            return contenu.stream().filter(d -> e.equals(d.getEtablissementId()) && s.equals(d.getStatut())).toList();
        }
        @Override public List<DemandeInscription> findByEtablissementIdOrderByDateDemandeDesc(Long e) { return contenu; }
        @Override public long countByEtablissementIdAndStatutIn(Long e, List<String> s) { return 0; }
        @Override public Optional<DemandeInscription> findByReference(String r) { return Optional.empty(); }
        @Override public boolean existsByReference(String r) { return false; }
        @Override public boolean existsByEtablissementIdAndNomCompletIgnoreCaseAndParentTelephoneAndAnneeScolaire(
            Long e, String n, String t, String a) { return false; }
        @Override public <S extends DemandeInscription> List<S> saveAll(Iterable<S> e) { throw new UnsupportedOperationException(); }
        @Override public Optional<DemandeInscription> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { throw new UnsupportedOperationException(); }
        @Override public List<DemandeInscription> findAll() { return contenu; }
        @Override public List<DemandeInscription> findAllById(Iterable<Long> i) { throw new UnsupportedOperationException(); }
        @Override public long count() { return contenu.size(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(DemandeInscription d) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> i) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends DemandeInscription> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { contenu.clear(); }
        @Override public void flush() { }
        @Override public <S extends DemandeInscription> S saveAndFlush(S e) { return (S) save(e); }
        @Override public <S extends DemandeInscription> List<S> saveAllAndFlush(Iterable<S> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<DemandeInscription> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> i) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { contenu.clear(); }
        @Override public DemandeInscription getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public DemandeInscription getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public DemandeInscription getReferenceById(Long id) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> Optional<S> findOne(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> List<S> findAll(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> List<S> findAll(Example<S> e, Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> Page<S> findAll(Example<S> e, Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> long count(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> boolean exists(Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription, R> R findBy(Example<S> e, Function<FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
        @Override public List<DemandeInscription> findAll(Sort s) { return contenu; }
        @Override public Page<DemandeInscription> findAll(Pageable p) { throw new UnsupportedOperationException(); }
    }

    private DepotCriteres criteres;
    private DepotNotes notes;
    private DepotDemandes demandes;
    private EtudeDossierService service;
    private long sequenceDemande;

    @BeforeEach
    void preparer() {
        criteres = new DepotCriteres();
        notes = new DepotNotes();
        demandes = new DepotDemandes();
        service = new EtudeDossierService(criteres, notes, demandes);
        sequenceDemande = 1;
    }

    private CritereAdmission critere(String libelle, double poids, int ordre) {
        CritereAdmission c = new CritereAdmission();
        c.setEtablissementId(1L);
        c.setLibelle(libelle);
        c.setPoids(poids);
        c.setOrdre(ordre);
        return criteres.save(c);
    }

    private DemandeInscription demande(String nom) {
        DemandeInscription d = new DemandeInscription();
        d.setId(sequenceDemande++);
        d.setEtablissementId(1L);
        d.setNomComplet(nom);
        d.setParentNom("Parent de " + nom);
        d.setParentTelephone("661100" + d.getId());
        d.setAnneeScolaire("2026-2027");
        d.setDateDemande(LocalDateTime.now().minusDays(d.getId()));
        d.setStatut(DemandeInscription.NOUVELLE);
        d.setReference("PRE-2026-000" + d.getId());
        return demandes.save(d);
    }

    @Nested
    @DisplayName("La note du dossier")
    class Note {

        @Test
        void laNoteSuitLesCoefficientsDeLaGrille() {
            var bulletin = critere("Bulletin", 2.0, 0);
            var entretien = critere("Entretien", 1.0, 1);
            var d = demande("DEMBA Aicha");

            Map<Long, Double> saisies = new LinkedHashMap<>();
            saisies.put(bulletin.getId(), 15.0);
            saisies.put(entretien.getId(), 9.0);

            // (15 x 2 + 9 x 1) / 3 = 13
            assertEquals(13.0, service.noter(d, saisies), 0.001,
                "le coefficient de la grille doit peser, pas le nombre de lignes");
        }

        @Test
        void unDossierSansAucuneNoteNaPasDeTotal() {
            critere("Bulletin", 1.0, 0);
            var d = demande("DEMBA Aicha");

            // Poser zero le classerait dernier, alors qu'il n'a pas encore ete regarde.
            assertNull(service.noter(d, Map.of()), "un dossier non etudie n'a pas de note");
        }

        @Test
        void uneNoteHorsDeLEchelleEstIgnoreeEtNonRamenee() {
            var bulletin = critere("Bulletin", 1.0, 0);
            var entretien = critere("Entretien", 1.0, 1);
            var d = demande("DEMBA Aicha");

            Map<Long, Double> saisies = new LinkedHashMap<>();
            saisies.put(bulletin.getId(), 25.0);   // faute de frappe
            saisies.put(entretien.getId(), 12.0);

            // Ramener 25 a 20 inventerait une appreciation que personne n'a portee.
            assertEquals(12.0, service.noter(d, saisies), 0.001,
                "seule la note valable doit compter");
            assertEquals(1, notes.findByDemandeId(d.getId()).size(),
                "et la note fautive ne doit pas etre conservee");
        }

        @Test
        void renoterRemplaceAuLieuDAjouter() {
            var bulletin = critere("Bulletin", 1.0, 0);
            var d = demande("DEMBA Aicha");

            service.noter(d, Map.of(bulletin.getId(), 8.0));
            service.noter(d, Map.of(bulletin.getId(), 14.0));

            // Sans effacement prealable, la meme ligne porterait deux notes et le total
            // dependrait de l'ordre de lecture.
            assertEquals(1, notes.findByDemandeId(d.getId()).size());
            assertEquals(14.0, d.getNoteDossier(), 0.001);
        }

        @Test
        void lesNotesRestentDetailleesCritereParCritere() {
            var bulletin = critere("Bulletin", 1.0, 0);
            var entretien = critere("Entretien", 1.0, 1);
            var d = demande("DEMBA Aicha");

            Map<Long, Double> saisies = new LinkedHashMap<>();
            saisies.put(bulletin.getId(), 8.0);
            saisies.put(entretien.getId(), 15.0);
            service.noter(d, saisies);

            // « 11,5 sur 20 » ne repond pas a une famille qui demande pourquoi. Ce qui
            // repond, c'est de montrer que le bulletin valait 8 et l'entretien 15.
            var dossier = service.dossier(d);
            assertEquals(2, dossier.criteresNotes());
            assertTrue(dossier.estComplet());
            assertEquals(8.0, dossier.grille().get(0).note(), 0.001);
            assertEquals(15.0, dossier.grille().get(1).note(), 0.001);
        }
    }

    @Nested
    @DisplayName("Un critere retire")
    class CritereRetire {

        @Test
        void neCompteplusDansLesNouvellesNotes() {
            var bulletin = critere("Bulletin", 1.0, 0);
            var ancien = critere("Test d'entree", 1.0, 1);
            ancien.setActif(false);
            criteres.save(ancien);

            var d = demande("DEMBA Aicha");
            Map<Long, Double> saisies = new LinkedHashMap<>();
            saisies.put(bulletin.getId(), 10.0);
            saisies.put(ancien.getId(), 20.0);

            assertEquals(10.0, service.noter(d, saisies), 0.001,
                "un critere retire ne doit plus peser dans le total");
        }

        @Test
        void resteVisibleSurLesDossiersQuIlAServiANoter() {
            var ancien = critere("Test d'entree", 1.0, 0);
            var d = demande("DEMBA Aicha");
            service.noter(d, Map.of(ancien.getId(), 16.0));

            ancien.setActif(false);
            criteres.save(ancien);

            // Le faire disparaitre effacerait le raisonnement de la commission, et une
            // decision qu'on ne peut plus expliquer est une decision indefendable.
            var dossier = service.dossier(d);
            assertEquals(1, dossier.grille().size());
            assertFalse(dossier.grille().get(0).critere().isActif());
            assertEquals(16.0, dossier.grille().get(0).note(), 0.001);
        }
    }

    @Nested
    @DisplayName("La liste d'attente")
    class ListeDAttente {

        private DemandeInscription enAttente(String nom, Double note) {
            var d = demande(nom);
            d.setStatut(DemandeInscription.LISTE_ATTENTE);
            d.setNoteDossier(note);
            return demandes.save(d);
        }

        @Test
        void laMeilleureNoteEstRappeleeEnPremier() {
            enAttente("TROISIEME", 9.0);
            enAttente("PREMIER", 16.0);
            enAttente("DEUXIEME", 12.5);

            var rangs = service.listeDAttente(1L, "2026-2027");

            assertEquals("PREMIER", rangs.get(0).demande().getNomComplet());
            assertEquals("DEUXIEME", rangs.get(1).demande().getNomComplet());
            assertEquals("TROISIEME", rangs.get(2).demande().getNomComplet());
            assertEquals(1, rangs.get(0).position());
        }

        @Test
        void unDossierNonNotePasseApresCeuxQuiOntEteEtudies() {
            enAttente("PAS ENCORE VU", null);
            enAttente("NOTE FAIBLE", 6.0);

            var rangs = service.listeDAttente(1L, "2026-2027");

            // Il n'a pas demerite : il n'a pas encore ete regarde. Le placer devant
            // reviendrait a servir celui que personne n'a juge avant celui qu'on a juge.
            assertEquals("NOTE FAIBLE", rangs.get(0).demande().getNomComplet());
            assertEquals("PAS ENCORE VU", rangs.get(1).demande().getNomComplet());
        }

        @Test
        void leClassementNeChangePasEntreDeuxConsultations() {
            enAttente("A", 12.0);
            enAttente("B", 12.0);
            enAttente("C", 12.0);

            var premier = service.listeDAttente(1L, "2026-2027").stream()
                .map(r -> r.demande().getNomComplet()).toList();
            var second = service.listeDAttente(1L, "2026-2027").stream()
                .map(r -> r.demande().getNomComplet()).toList();

            // A note egale, c'est la date de depot qui departage. Un classement qui
            // changerait d'une consultation a l'autre ne serait pas defendable.
            assertEquals(premier, second, "le classement doit etre stable");
        }

        @Test
        void unAdmisNeFigurePasDansLaFileDAttente() {
            enAttente("EN ATTENTE", 10.0);
            var admis = demande("ADMIS");
            admis.setStatut(DemandeInscription.ADMIS);
            admis.setNoteDossier(18.0);
            demandes.save(admis);

            var rangs = service.listeDAttente(1L, "2026-2027");

            assertEquals(1, rangs.size(), "il a sa place : il n'attend plus");
            assertEquals("EN ATTENTE", rangs.get(0).demande().getNomComplet());
        }
    }

    @Test
    void admisNestPasInscrit() {
        var d = demande("DEMBA Aicha");
        service.decider(d, DemandeInscription.ADMIS, "Bon dossier", "La commission");

        assertTrue(d.estAdmise(), "la commission a dit oui");
        assertTrue(d.attendSaPlace(),
            "mais la place reste a prendre : une famille change d'avis, ou ne reunit pas les frais");
        assertNull(d.getEleveId(), "aucun eleve n'a ete cree");
        assertEquals("La commission", d.getDecidePar(), "un jury rend des comptes");
    }
}
