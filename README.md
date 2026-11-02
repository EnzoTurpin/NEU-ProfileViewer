# NEU Profile Viewer — port Minecraft 26.3 (Fabric)

Portage du profile viewer (`/pv`) de [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates)
(Forge 1.8.9, archivé) vers Minecraft **26.3** / Fabric. Même interface, mêmes calculs, bugs connus corrigés.

## État (étape 1)

| Partie | État |
|---|---|
| Commandes `/pv`, `/neuprofile`, `/cata` | ✅ |
| Réseau : proxy Ursa (auth Mojang `joinServer`, token), cache 500 s, Mojang, Soopy | ✅ |
| Dépôt de données NEU-REPO (téléchargement auto) + `weight.json`/`sblevels.json` embarqués | ✅ |
| Conversion des items 1.8.9 (NBT Hypixel) → 26.3 | ✅ |
| Écran : onglets, profils, SkyCrypt, recherche, têtes récentes, états de chargement/erreur | ✅ |
| Onglet **Skills** (networth, weights, niveau SB, skills, slayers, joueur 3D, pet, panorama, statut) | ✅ |
| Sous-page « Level » du niveau SkyBlock | ⏳ |
| 13 autres onglets (Dungeons, Profile Stats, Storage, Collections, Pets, HOTM, Bingo, Trophy Fish, Bestiary, Crimson Isle, Museum, Rift, Garden) | ⏳ |

## Configuration — `config/neupv.json`

```json
{
  "ursaUrl": "https://ton-proxy.example.org",
  "lowestBinUrl": "",
  "useSoopyNetworth": true,
  "displayWeight": true,
  "alwaysShowBingoTab": false,
  "pageLayout": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]
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
gradlew runClientGameTest    # test automatisé : vrai client, profil synthétique, captures dans build/run/clientGameTest/screenshots
```

## Différences volontaires avec NEU 2.6.0

- Magical Power : quand deux paliers d'une même famille sont dans le sac, c'est le palier inférieur qui est écarté (NEU écartait le supérieur).
- Pseudo inexistant : message « Unknown player » (NEU ne réagissait pas / restait bloqué sur Loading).
- Erreurs réseau/proxy affichées (NEU les avalait → chargement infini) ; un 401 du proxy invalide réellement le token.
- Plus de crash volontaire du jeu après 1 h sur l'écran de chargement ; la page de crash ne dépend plus du modèle 3D.
- Networth local calculé en `long` (NEU plafonnait à ~2,1 milliards) et compte la banque du profil (API v2).
- `weight.json` / `sblevels.json`, supprimés de NEU-REPO le 30/10/2026, sont embarqués.
- Rendu 26.3 : flou vanilla, panorama défilant (au lieu du cube en perspective GL), barres dorées teintées (au lieu du shader).

## Licences

- Code dérivé de NotEnoughUpdates : **LGPL-3.0-or-later** (voir `LICENSE`), en-têtes d'attribution dans chaque fichier.
- Textures et panoramas : NotEnoughUpdates (LGPL-3.0). Données `weight.json`/`sblevels.json` : NEU-REPO (MIT, `assets/neupv/data/NEU-REPO-LICENSE.txt`).
- Table d'items 1.8 : générée depuis PrismarineJS minecraft-data (MIT).
