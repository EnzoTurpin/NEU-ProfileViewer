# NEU Profile Viewer — Minecraft 26.3 (Fabric)

Portage du profile viewer (`/pv`) de [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates)
(Forge 1.8.9, archivé) vers Minecraft **26.3** / Fabric. Même interface, mêmes calculs, bugs connus corrigés,
et les données SkyBlock ajoutées par Hypixel depuis la dernière version de NEU.

## Fonctionnalités

- Commandes `/pv [joueur]`, `/neuprofile`, `/cata`
- Tous les onglets de NEU : Skills (+ niveau SkyBlock), Dungeons, Profile Stats (+ Hoppity), Storage (+ sacks),
  Collections, Pets, Mining (HOTM), Bingo, Trophy Fish, Bestiary, Crimson Isle, Museum, Rift, Garden
- Ajouts par rapport à NEU 2.6.0 :
  - onglet **Hunting** (skill Hunting, Attribute Shards, Safari, critters)
  - **Heart of the Forest** dans l'onglet Mining, HOTM lu depuis la nouvelle donnée `skill_tree` (arbre sélectionné)
  - Storage : wardrobe depuis `loadout`, **loadouts**, carnival masks, icônes réelles des backpacks et des sacs
  - Garden : page **Contests** (Jacob, médailles, perks, farming toolkit avec lore complet, greenhouse)
  - Profile Stats : case « Misc » et historique de la banque ; Dungeons : détail des runs ; Pets : règles Autopet
  - nouvelles collections et cap de niveau Foraging
- Interface adaptée à toutes les tailles de fenêtre et tous les GUI scale
- Textures 1.8.9 des items (trophy fish, items devenus des modèles Hypixel) quand on n'est pas sur Hypixel

## Configuration — `config/neupv.json`

```json
{
  "ursaUrl": "https://ton-proxy.example.org",
  "lowestBinUrl": "",
  "useSoopyNetworth": true,
  "displayWeight": true,
  "alwaysShowBingoTab": false
}
```

- **`ursaUrl` (obligatoire)** : un serveur [ursa-minor](https://github.com/NotEnoughUpdates/ursa-minor)
  (Rust, AGPL-3.0) configuré avec **ta** clé API Hypixel. Sans lui, le PV affiche « No data proxy configured ».
- `lowestBinUrl` (optionnel) : JSON `{"ITEM_ID": prix}` pour le networth local (le serveur de prix de NEU,
  moulberry.codes, est hors ligne). Le networth Soopy reste la source par défaut, comme dans NEU.

## Compiler / lancer

Java 25 requis.

```
gradlew build                # jar dans build/libs/
gradlew runClient            # lance Minecraft 26.3 avec le mod
gradlew runClientGameTest    # tests automatisés : vrai client, profils de test, captures dans build/run/clientGameTest/screenshots
```

## Différences volontaires avec NEU 2.6.0

- Magical Power : quand deux paliers d'une même famille sont dans le sac, c'est le palier inférieur qui est écarté (NEU écartait le supérieur).
- Pseudo inexistant : message « Unknown player » (NEU restait bloqué sur Loading).
- Erreurs réseau/proxy affichées (NEU les avalait → chargement infini) ; un 401 du proxy invalide réellement le token.
- Plus de crash volontaire du jeu après 1 h sur l'écran de chargement ; la page de crash ne dépend plus du modèle 3D.
- Networth local calculé en `long` (NEU plafonnait à ~2,1 milliards) et compte la banque du profil.
- Ender chest : les pages gardent leurs 45 slots (NEU coupait la dernière page).
- `weight.json` / `sblevels.json`, supprimés de NEU-REPO le 30/10/2026, sont embarqués.

## Licences

- Code dérivé de NotEnoughUpdates : **LGPL-3.0-or-later** (voir `LICENSE`), en-têtes d'attribution dans chaque fichier.
- Textures et panoramas : NotEnoughUpdates (LGPL-3.0). Données NEU-REPO (MIT, `assets/neupv/data/NEU-REPO-LICENSE.txt`).
- Table d'items 1.8 : générée depuis PrismarineJS minecraft-data (MIT).
