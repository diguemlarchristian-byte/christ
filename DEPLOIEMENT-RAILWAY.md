# Héberger EduSystem Pro sur Railway

Une fois l'hébergement en place, le logiciel tourne chez Railway. Vous n'avez plus besoin
de votre machine : l'école se connecte à une adresse sur Internet, depuis n'importe où.

Ce document décrit ce qu'il faut faire une seule fois. Le dépôt contient déjà ce que
Railway attend : un `Dockerfile`, qui construit l'image et lance le jar.

Il n'y a **pas** de `Procfile`, et il ne faut pas en remettre un. Railway lui donne la
priorité sur le `Dockerfile` : la commande du `Procfile` s'exécutait alors hors de l'image
construite, où `target/administration.jar` n'existe pas, et le conteneur redémarrait sans
fin sur `Unable to access jarfile`.

## 1. Créer le projet

1. Sur railway.app, créez un projet et choisissez **Deploy from GitHub repo**.
2. Sélectionnez ce dépôt. Railway détecte le `Dockerfile` et construit l'image.

La première construction prend une dizaine de minutes : Maven télécharge ses dépendances,
puis compile. Les suivantes sont plus rapides.

## 1 bis. Vérifier que Railway construit bien depuis le Dockerfile

C'est le point qui a coûté le plus de temps, et il ne se voit pas.

Railway choisit seul un constructeur. S'il retient **Railpack**, il ignore le `Dockerfile`,
devine qu'il a affaire à un projet Maven et fabrique la commande
`java -jar target/administration.jar`. Or `target/` n'existe que pendant la compilation :
l'image finale ne contient que `/app/app.jar`. Le conteneur redémarre alors indéfiniment
sur `Unable to access jarfile`, tout en affichant « Online » — puisque « Online » ne dit
que « le conteneur tourne », pas « le programme fonctionne ».

Dans **Settings → Build**, le chemin du Dockerfile doit être renseigné (`Dockerfile`). Et
dans **Settings → Deploy**, le champ **Custom Start Command** doit rester **vide** :
l'`ENTRYPOINT` de l'image sait démarrer l'application. Un `Procfile` recopié une fois dans
ce champ y reste même après avoir été supprimé du dépôt — le vider est un geste à faire
dans le tableau de bord, le retirer du dépôt n'y suffit pas.

## 2. Ajouter la base de données

Dans le projet Railway : **New → Database → Add MySQL**.

Railway crée la base et expose ses coordonnées. Il ne les injecte pas automatiquement sous
les noms que Spring attend : il faut les recopier dans le service de l'application.

## 3. Renseigner les variables

Dans le service de l'application, onglet **Variables**, ajoutez :

| Variable | Valeur |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `SPRING_DATASOURCE_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `APP_BASE_URL` | l'adresse publique que Railway vous attribue |
| `APP_UPLOAD_DIR` | `/app/donnees/fichiers` — voir le volume, plus bas |
| `SUPER_ADMIN_EMAIL` | l'adresse de la personne qui administre le logiciel |
| `ADMIN_EMAIL` | l'adresse de la direction de l'établissement |

La syntaxe `${{MySQL.…}}` est celle de Railway : elle référence le service MySQL sans
recopier de mot de passe à la main, et suit automatiquement une éventuelle rotation.

`PORT` est fourni par Railway et lu tel quel — `application.properties` déclare
`server.port=${PORT:8085}`, il n'y a rien à régler.

`APP_BASE_URL` sert aux liens de réinitialisation de mot de passe envoyés par courriel.
Renseignée avec une mauvaise adresse, ces liens pointeraient dans le vide.

## 4. Ouvrir l'accès

Onglet **Settings → Networking → Generate Domain**. Railway attribue une adresse publique.
C'est celle que l'école utilisera.

## Ce à quoi il faut penser

**Les fichiers téléversés.** Justificatifs, photos, bulletins de paie archivés sont écrits
sur le disque du conteneur. Railway redéploie à chaque mise à jour du code, et **ce disque
est alors remis à zéro**. Sur le service de l'application : **Settings → Volumes → Add
Volume**, chemin de montage `/app/donnees`, puis la variable `APP_UPLOAD_DIR` à
`/app/donnees/fichiers`. Sans ce volume, les pièces justificatives disparaîtraient à la
première mise à jour — la base, elle, est préservée puisqu'elle vit dans le service MySQL,
qui a son propre volume.

Ce volume est à poser **avant** la première utilisation réelle. Posé plus tard, il masque
le dossier existant : ce qui avait déjà été téléversé ne serait plus visible.

**Les sauvegardes.** Railway sauvegarde la base selon le plan souscrit, mais un
hébergement n'est pas une sauvegarde : gardez l'habitude d'exporter régulièrement, depuis
l'application, la paie du mois et les documents comptables. Un export que vous détenez ne
dépend d'aucun fournisseur.

**La première connexion.** Au premier démarrage, le logiciel crée le compte
super-administrateur et celui de la direction. Leurs mots de passe ne sont plus écrits dans
le dépôt : à défaut de `SUPER_ADMIN_PASSWORD` et `ADMIN_PASSWORD`, chacun est tiré au
hasard et **affiché une seule fois dans les Deploy Logs**, dans un encadré. Relevez-le à ce
moment-là ; il ne réapparaîtra pas.

Définir ces deux variables à la main fonctionne aussi, mais inscrit le mot de passe dans la
configuration Railway. Le laisser tirer au hasard et le relever dans les journaux évite
qu'il traîne quelque part.

## L'installation locale reste possible

Héberger en ligne et installer dans l'école ne s'excluent pas. Le dossier `school`
(hors dépôt) contient le jar, ses scripts et un guide pour faire tourner le logiciel sur
une machine de l'établissement, sans Internet et sans MySQL — voir le profil `local` et
`application-local.properties`.
