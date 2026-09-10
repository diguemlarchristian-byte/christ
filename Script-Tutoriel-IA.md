# Script à donner à une intelligence artificielle

Ce fichier contient un **prompt** à copier-coller dans une IA (Claude, ChatGPT, Gemini…)
pour qu'elle rédige le **tutoriel d'utilisation complet** d'EduSystem Pro.

Le guide de prise en main livré avec le logiciel présente l'outil et son démarrage. Le
tutoriel, lui, décrit chaque écran, geste par geste. C'est un document long : le faire
rédiger par une IA à partir du script ci-dessous évite d'y passer des semaines.

## Comment s'en servir

1. Copiez tout le bloc entre les deux lignes de tirets, depuis `RÔLE` jusqu'à la fin.
2. Collez-le dans l'IA de votre choix.
3. Demandez un chapitre à la fois — un tutoriel complet dépasse ce qu'une IA produit d'un
   seul coup, et la qualité chute quand on lui en demande trop à la fois.
4. **Relisez chaque chapitre en ayant le logiciel ouvert à côté.** Une IA ne voit pas vos
   écrans : elle travaille à partir de ce que le script lui dit. Tout ce qu'elle décrit et
   que vous ne retrouvez pas à l'écran est à corriger.

---

RÔLE

Tu rédiges le tutoriel d'utilisation d'EduSystem Pro, un logiciel de gestion scolaire
utilisé par des établissements en Afrique centrale (Tchad, Cameroun, Côte d'Ivoire).

PUBLIC

Des secrétaires, comptables, directeurs et enseignants. Beaucoup découvrent
l'informatique de gestion. Certains liront le tutoriel imprimé, sans le logiciel sous les
yeux. Écris pour eux : phrases courtes, vocabulaire de leur métier et non du tien.

CE QUE FAIT LE LOGICIEL

Gestion complète d'un établissement : inscriptions et dossiers élèves, classes, notes et
bulletins, frais de scolarité, caisse et encaissements avec reçus numérotés, remises et
échéanciers de paiement, dépenses et comptabilité SYSCOHADA (grand livre, balance),
personnel, contrats, congés et paie, absences et retards, infirmerie, inventaire du
matériel, portail parents, site vitrine public.

Il fonctionne sur le réseau de l'établissement, **sans connexion Internet**.

LES FONCTIONS ET LEUR ÉCRAN D'ACCUEIL

| Fonction | Arrive sur | S'occupe de |
|---|---|---|
| Administrateur | Tableau de bord | Comptes, paramètres, vue d'ensemble |
| Directeur | Tableau de bord | Suivi pédagogique, personnel, résultats |
| Secrétaire | Secrétariat | Inscriptions, dossiers, absences, inventaire |
| Enseignant | Tableau enseignant | Ses classes, ses notes, ses bulletins |
| Trésorier / Comptable | Finances | Caisse, dépenses, frais, documents comptables |
| Surveillant | Surveillance | Présences, retards, incidents |
| Infirmier | Infirmerie | Soins, stock de la pharmacie |
| Coordonnateur | Coordination | Suivi pédagogique délégué |
| Parent | Portail parent | Notes, absences et paiements de son enfant |
| Élève | Portail élève | Ses notes et son emploi du temps |

Précision importante : pour le Trésorier et le Comptable, la fonction ne suffit pas. Des
**modules financiers** (Caisse, Dépenses, Préparation de la paie, Paiement de la paie,
Budget & paramétrage, Rapports) s'attribuent compte par compte par l'administrateur, dans
Paramètres > Rôles > Accès aux interfaces. Deux comptables de deux écoles n'ont donc pas
forcément les mêmes écrans. Dis-le, sans en faire un chapitre entier.

RÈGLES DE FONCTIONNEMENT À RESPECTER (ne les invente pas, elles sont exactes)

- Les numéros de reçu sont attribués automatiquement, au format `HF-2026-2027-001`. Un
  numéro déjà délivré est refusé. Supprimer un paiement ne libère pas son numéro : la
  suite peut présenter des trous, c'est voulu.
- Une remise vaut pour une seule année scolaire et ne se reconduit pas.
- Un échéancier étale ce qui reste dû, remises déduites. Chaque encaissement s'impute
  automatiquement du versement le plus ancien au plus récent.
- Le plan comptable SYSCOHADA (56 postes) est créé automatiquement à l'ouverture de
  l'école. Aucune saisie n'est nécessaire.
- Un bulletin de paie payé ne peut plus être modifié ni supprimé, et il est archivé en PDF
  au moment du paiement, avec un code de vérification imprimé dessus.
- La paie d'un mois peut être clôturée quand tout le personnel actif est payé. Un mois
  clos n'accepte plus aucun bulletin ; seul un administrateur peut le rouvrir.
- À l'inventaire, un article est un **lot** (« 40 tables-bancs »). Les unités en réparation
  ou hors service ne se saisissent pas dans la fiche : elles s'enregistrent en mouvement,
  pour rester tracées. Supprimer un article détruit son historique — pour du matériel
  réformé, enregistrer une *sortie*.
- Le journal d'activité conserve qui a fait quoi.

STRUCTURE ATTENDUE

Un chapitre par métier, dans cet ordre : Secrétariat, Enseignant, Comptabilité et caisse,
Direction, Surveillance, Infirmerie, Inventaire, Portail parents, Administration.

Dans chaque chapitre, pour chaque tâche :
1. **Quand on fait ça** — la situation réelle qui amène à ouvrir cet écran.
2. **Où** — le chemin exact (menu, onglet).
3. **Comment** — les étapes numérotées, en nommant les champs.
4. **Ce qui peut coincer** — le refus le plus fréquent et ce qu'il signifie.

MANIÈRE D'ÉCRIRE

- Vouvoie le lecteur. Une idée par phrase.
- Nomme les écrans et les boutons tels qu'ils apparaissent, entre guillemets.
- Donne des exemples chiffrés en francs CFA, avec des noms d'élèves et d'écoles de la
  région.
- Explique *pourquoi* une règle existe quand elle peut surprendre. Un utilisateur qui
  comprend une contrainte cesse de la contourner.

À NE PAS FAIRE

- Ne liste pas de corrections apportées au logiciel, de failles, de bugs ni de versions.
  Le lecteur veut se servir de l'outil, pas connaître son histoire.
- Ne propose pas d'améliorations et n'écris pas ce qu'il « faudrait » ajouter.
- N'invente aucun écran, aucun bouton, aucun raccourci clavier. Si tu ne sais pas, écris
  « à vérifier » plutôt qu'une phrase plausible : une consigne fausse coûte plus cher
  qu'une absence de consigne.
- Pas de superlatifs publicitaires. La démonstration se fait en montrant le temps gagné.
- Pas de vocabulaire technique inutile : ni « base de données », ni « endpoint », ni
  « transaction », ni « API ».

PREMIÈRE DEMANDE

Commence par le chapitre **Secrétariat** : inscrire un élève, éditer sa fiche, gérer les
documents du dossier, saisir les absences du jour, justifier une absence. Environ 1500
mots. Attends ma relecture avant de passer au chapitre suivant.

---

## Après la rédaction

Quand les chapitres sont écrits et relus, rassemblez-les dans un seul document et
demandez à l'IA une dernière passe : une table des matières, un index des écrans, et une
vérification que le même mot désigne partout la même chose (« reçu » et non « quittance »,
« frais » et non « droits »).
