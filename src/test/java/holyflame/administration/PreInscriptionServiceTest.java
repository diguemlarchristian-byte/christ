package holyflame.administration;

import holyflame.administration.model.DemandeInscription;
import holyflame.administration.repository.DemandeInscriptionRepository;
import holyflame.administration.service.PreInscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que le formulaire public accepte, et ce qu'il refuse.
 *
 * C'est la seule porte du logiciel ouverte a des inconnus. Tout ce qui la franchit finit
 * sous les yeux d'une secretaire : une demande inexploitable lui fera perdre du temps, un
 * doublon lui fera rappeler deux fois la meme famille, et un envoi automatique repete lui
 * noiera les vraies demandes.
 */
@DisplayName("Demandes de pre-inscription")
class PreInscriptionServiceTest {

    /** Un depot en memoire : ces regles se verifient sans base. */
    private static class DepotFactice implements DemandeInscriptionRepository {
        final List<DemandeInscription> contenu = new ArrayList<>();
        long sequence = 1;

        @Override public DemandeInscription save(DemandeInscription d) {
            if (d.getId() == null) d.setId(sequence++);
            contenu.removeIf(x -> d.getId().equals(x.getId()));
            contenu.add(d);
            return d;
        }
        @Override public boolean existsByReference(String reference) {
            return contenu.stream().anyMatch(d -> reference.equals(d.getReference()));
        }
        @Override public boolean existsByEtablissementIdAndNomCompletIgnoreCaseAndParentTelephoneAndAnneeScolaire(
                Long etab, String nom, String tel, String annee) {
            return contenu.stream().anyMatch(d -> etab.equals(d.getEtablissementId())
                && nom.equalsIgnoreCase(d.getNomComplet())
                && tel.equals(d.getParentTelephone())
                && annee.equals(d.getAnneeScolaire()));
        }
        @Override public long countByEtablissementIdAndStatutIn(Long etab, List<String> statuts) {
            return contenu.stream().filter(d -> etab.equals(d.getEtablissementId())
                && statuts.contains(d.getStatut())).count();
        }
        @Override public List<DemandeInscription> findByEtablissementIdOrderByDateDemandeDesc(Long e) { return contenu; }
        @Override public List<DemandeInscription> findByEtablissementIdAndStatutOrderByDateDemandeDesc(Long e, String s) { return contenu; }
        @Override public Optional<DemandeInscription> findByReference(String r) { return Optional.empty(); }

        // Le reste de JpaRepository n'est pas sollicite par ces regles.
        @Override public <S extends DemandeInscription> List<S> saveAll(Iterable<S> e) { throw new UnsupportedOperationException(); }
        @Override public Optional<DemandeInscription> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { throw new UnsupportedOperationException(); }
        @Override public List<DemandeInscription> findAll() { return contenu; }
        @Override public List<DemandeInscription> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return contenu.size(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(DemandeInscription d) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends DemandeInscription> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { contenu.clear(); }
        @Override public void flush() { }
        @Override public <S extends DemandeInscription> S saveAndFlush(S e) { return (S) save(e); }
        @Override public <S extends DemandeInscription> List<S> saveAllAndFlush(Iterable<S> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<DemandeInscription> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { contenu.clear(); }
        @Override public DemandeInscription getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public DemandeInscription getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public DemandeInscription getReferenceById(Long id) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> Optional<S> findOne(org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> List<S> findAll(org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> List<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> long count(org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription> boolean exists(org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends DemandeInscription, R> R findBy(org.springframework.data.domain.Example<S> e, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
        @Override public List<DemandeInscription> findAll(org.springframework.data.domain.Sort s) { return contenu; }
        @Override public org.springframework.data.domain.Page<DemandeInscription> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
    }

    private DepotFactice depot;
    private PreInscriptionService service;

    @BeforeEach
    void preparer() {
        depot = new DepotFactice();
        service = new PreInscriptionService(depot);
    }

    private DemandeInscription demande(String enfant, String parent, String telephone) {
        DemandeInscription d = new DemandeInscription();
        d.setEtablissementId(1L);
        d.setNomComplet(enfant);
        d.setParentNom(parent);
        d.setParentTelephone(telephone);
        return d;
    }

    @Nested
    @DisplayName("Ce qui est accepte")
    class Accepte {

        @Test
        void uneDemandeCompleteEstEnregistreeAvecSaReference() {
            var r = service.enregistrer(demande("DEMBA Aicha", "DEMBA Moussa", "+235 66 11 22 33"),
                "2026-2027", null);

            assertTrue(r.accepte());
            assertNotNull(r.demande().getReference(), "la famille doit repartir avec une reference");
            assertTrue(r.demande().getReference().startsWith("PRE-"),
                "la reference doit se reconnaitre au telephone : " + r.demande().getReference());
            assertEquals(DemandeInscription.NOUVELLE, r.demande().getStatut());
            assertEquals("2026-2027", r.demande().getAnneeScolaire());
        }

        @Test
        void lesEspacesEnTropSontNettoyes() {
            var r = service.enregistrer(
                demande("  DEMBA   Aicha  ", "  DEMBA Moussa ", " 66112233 "), "2026-2027", null);

            // Sans ce nettoyage, deux envois de la meme famille passeraient pour deux enfants
            // differents, et la liste du secretariat se remplirait de faux doublons.
            assertEquals("DEMBA Aicha", r.demande().getNomComplet());
            assertEquals("DEMBA Moussa", r.demande().getParentNom());
        }

        @Test
        void lesReferencesNeSeRepetentPas() {
            var vues = new java.util.HashSet<String>();
            for (int i = 0; i < 40; i++) {
                var r = service.enregistrer(demande("Enfant " + i, "Parent " + i, "6611223" + i),
                    "2026-2027", null);
                assertTrue(vues.add(r.demande().getReference()),
                    "deux familles ne doivent jamais porter la meme reference");
            }
        }
    }

    @Nested
    @DisplayName("Ce qui est refuse")
    class Refuse {

        @Test
        void sansNomDEnfantIlNYARienAtraiter() {
            var r = service.enregistrer(demande("  ", "DEMBA Moussa", "66112233"), "2026-2027", null);
            assertFalse(r.accepte());
            assertNotNull(r.message(), "la famille doit lire ce qui manque");
        }

        @Test
        void sansNumeroLEcoleNePeutPasRappeler() {
            var r = service.enregistrer(demande("DEMBA Aicha", "DEMBA Moussa", "12"), "2026-2027", null);
            assertFalse(r.accepte());
            assertTrue(r.message().contains("téléphone"));
        }

        @Test
        void uneNaissanceDansLeFuturEstUneFauteDeSaisie() {
            var d = demande("DEMBA Aicha", "DEMBA Moussa", "66112233");
            d.setDateNaissance(LocalDate.now().plusDays(1));

            // La refuser tout de suite evite une fiche a corriger apres coup, quand plus
            // personne ne sait ce que la famille avait voulu ecrire.
            var r = service.enregistrer(d, "2026-2027", null);
            assertFalse(r.accepte());
        }

        @Test
        void laMemeFamilleNePeutPasRedeposerDeuxFois() {
            service.enregistrer(demande("DEMBA Aicha", "DEMBA Moussa", "66112233"), "2026-2027", null);
            var second = service.enregistrer(demande("demba aicha", "DEMBA Moussa", "66112233"),
                "2026-2027", null);

            // Une famille qui doute que son envoi soit parti recommence. Sans ce controle,
            // le secretariat rappellerait trois fois le meme numero.
            assertFalse(second.accepte());
            assertTrue(second.message().contains("déjà"));
            assertEquals(1, depot.contenu.size());
        }

        @Test
        void lAnneeSuivanteLaMemeFamillePeutRevenir() {
            service.enregistrer(demande("DEMBA Aicha", "DEMBA Moussa", "66112233"), "2026-2027", null);
            var suivante = service.enregistrer(demande("DEMBA Aicha", "DEMBA Moussa", "66112233"),
                "2027-2028", null);

            assertTrue(suivante.accepte(),
                "un refus une annee ne doit pas fermer la porte a la suivante");
        }
    }

    @Nested
    @DisplayName("Le piege a robots")
    class Piege {

        @Test
        void unChampInvisibleRempliNEnregistreRien() {
            var r = service.enregistrer(demande("Robot", "Robot", "66112233"), "2026-2027", "http://spam");

            assertFalse(r.accepte());
            assertTrue(depot.contenu.isEmpty(), "rien ne doit atteindre le secretariat");
        }

        @Test
        void leRobotNApprendPasQuIlAEteRepere() {
            var r = service.enregistrer(demande("Robot", "Robot", "66112233"), "2026-2027", "x");

            // Un message d'erreur explicite apprendrait a contourner le piege. La page de
            // confirmation s'affiche donc normalement, sans que rien ne soit enregistre.
            assertNull(r.message(), "aucun message ne doit trahir le piege");
        }
    }

    @Test
    void leCompteurNeCompteQueCeQuiAttendUneDecision() {
        service.enregistrer(demande("ABDEL Ali", "ABDEL Oumar", "66110001"), "2026-2027", null);
        service.enregistrer(demande("BINTA Sara", "BINTA Idriss", "66110002"), "2026-2027", null);
        var convertie = service.enregistrer(demande("CHOUA Nadia", "CHOUA Hassan", "66110003"), "2026-2027", null);
        convertie.demande().setStatut(DemandeInscription.CONVERTIE);
        depot.save(convertie.demande());

        // Le chiffre du menu doit dire ce qu'il reste a faire, pas ce qui est deja fait.
        assertEquals(2, service.enAttente(1L));
    }
}
