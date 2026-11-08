# NEU Profile Viewer — Minecraft 26.3 (Fabric)

A port of the [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates) profile viewer (`/pv`)
(Forge 1.8.9, archived) to Minecraft **26.3** / Fabric. Same interface, same calculations, known bugs fixed,
plus the SkyBlock data Hypixel added since the last NEU release.

## Features

- Commands: `/pv [player]`, `/neuprofile`, `/cata`
- Every NEU tab: Skills (+ SkyBlock level), Dungeons, Profile Stats (+ Hoppity), Storage (+ sacks),
  Collections, Pets, Mining (HOTM), Bingo, Trophy Fish, Bestiary, Crimson Isle, Museum, Rift, Garden
- Additions over NEU 2.6.0:
  - **Hunting** tab (Hunting skill, Attribute Shards, Safari, critters)
  - **Heart of the Forest** in the Mining tab; HOTM read from the new `skill_tree` data (selected tree)
  - Storage: wardrobe from `loadout`, **loadouts**, carnival masks, real backpack and bag icons
  - Garden: **Contests** page (Jacob's contests, medals, perks, farming toolkit with full lore, greenhouse)
  - Profile Stats: "Misc" box and bank history; Dungeons: run details; Pets: Autopet rules
  - New collections and the Foraging level cap
- Layout that adapts to every window size and GUI scale
- 1.8.9 item textures (trophy fish, items that became Hypixel models) when you are not on Hypixel

## Configuration — `config/neupv.json`

```json
{
  "ursaUrl": "https://your-proxy.example.org",
  "lowestBinUrl": "",
  "useSoopyNetworth": true,
  "displayWeight": true,
  "alwaysShowBingoTab": false
}
```

- **`ursaUrl` (required)**: an [ursa-minor](https://github.com/NotEnoughUpdates/ursa-minor) server
  (Rust, AGPL-3.0) set up with **your own** Hypixel API key. Without it, the PV shows "No data proxy configured".
- `lowestBinUrl` (optional): a JSON `{"ITEM_ID": price}` used for the local networth (NEU's price server,
  moulberry.codes, is offline). Soopy's networth stays the default source, as in NEU.

## Build / run

Requires Java 25.

```
gradlew build                # jar in build/libs/
gradlew runClient            # starts Minecraft 26.3 with the mod
gradlew runClientGameTest    # automated tests: real client, test profiles, screenshots in build/run/clientGameTest/screenshots
```

## Intentional differences from NEU 2.6.0

- Magical Power: when two tiers of the same accessory family are in the bag, the lower tier is dropped (NEU dropped the higher one).
- Unknown player name: shows "Unknown player" (NEU stayed stuck on Loading).
- Network/proxy errors are shown (NEU swallowed them, so it loaded forever); a 401 from the proxy really invalidates the token.
- No more intentional game crash after 1 hour on the loading screen; the crash page no longer depends on the 3D model.
- Local networth computed as a `long` (NEU capped it at ~2.1 billion) and includes the profile bank.
- Ender chest: pages keep all 45 slots (NEU cut the last page short).
- `weight.json` / `sblevels.json`, removed from NEU-REPO on 2026-10-30, are bundled.

## Licenses

- Code derived from NotEnoughUpdates: **LGPL-3.0-or-later** (see `LICENSE`), attribution headers in every file.
- Textures and panoramas: NotEnoughUpdates (LGPL-3.0). NEU-REPO data (MIT, `assets/neupv/data/NEU-REPO-LICENSE.txt`).
- 1.8 item table: generated from PrismarineJS minecraft-data (MIT).

*Not affiliated with NotEnoughUpdates or Hypixel.*
