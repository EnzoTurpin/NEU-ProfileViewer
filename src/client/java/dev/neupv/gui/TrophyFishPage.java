/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates TrophyFishPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrophyFishPage extends PvPage {
	private static final Identifier TROPHY_FISH_TEXTURE = Draw.tex("gui/pv_trophy_fish_tab.png");

	/** Known trophy fish (API name -> name colour), in a stable order (NEU used a HashMap). */
	private static final Map<String, String> FISH = new LinkedHashMap<>();
	static {
		FISH.put("gusher", "§f");
		FISH.put("flyfish", "§a");
		FISH.put("moldfin", "§5");
		FISH.put("vanille", "§9");
		FISH.put("blobfish", "§f");
		FISH.put("mana_ray", "§9");
		FISH.put("slugfish", "§a");
		FISH.put("soul_fish", "§5");
		FISH.put("lava_horse", "§9");
		FISH.put("golden_fish", "§6");
		FISH.put("karate_fish", "§5");
		FISH.put("skeleton_fish", "§5");
		FISH.put("sulphur_skitter", "§f");
		FISH.put("obfuscated_fish_1", "§f");
		FISH.put("obfuscated_fish_2", "§a");
		FISH.put("obfuscated_fish_3", "§9");
		FISH.put("volcanic_stonefish", "§9");
		FISH.put("steaming_hot_flounder", "§f");
	}

	private static final String[] HELMET_IDS = {"BRONZE_HUNTER_HELMET", "SILVER_HUNTER_HELMET", "GOLD_HUNTER_HELMET", "DIAMOND_HUNTER_HELMET"};
	private static final String[] HELMET_TITLES = {"§aNovice Fisher", "§9Adept Fisher", "§5Expert Fisher", "§6Master Fisher"};

	private static final int[][] SLOT_LOCATIONS = {
		{277, 46}, {253, 58}, {301, 58}, {229, 70}, {325, 70}, {277, 70}, {253, 82}, {301, 82}, {229, 94},
		{325, 94}, {253, 106}, {301, 106}, {277, 118}, {229, 118}, {325, 118}, {253, 130}, {301, 130}, {277, 142},
	};

	private static final String CHECK_X = "§c✖";
	private static final String CHECK = "§a✔";

	public TrophyFishPage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		var selected = getSelectedProfile();
		if (selected == null) return;
		JsonObject pj = selected.getProfileJson();
		JsonObject trophyObject = J.obj(pj, "trophy_fish");
		if (trophyObject == null) {
			Draw.centered(g, "§cNo data found", guiLeft + 431 / 2f, guiTop + 101, true);
			return;
		}

		Map<String, TrophyFish> caught = new LinkedHashMap<>();
		long totalCount = loadTrophyInformation(trophyObject, caught);

		Draw.full(g, TROPHY_FISH_TEXTURE, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
		List<Component> tooltip = null;

		// Sea creature kills
		int thunderKills = J.i(pj, "bestiary.kills.thunder_400", 0);
		repoItem(g, "THUNDER_SC", guiLeft + 16, guiTop + 108);
		Draw.text(g, "§bThunder Kills: §f" + thunderKills, guiLeft + 36, guiTop + 112, true);
		int jawbusKills = J.i(pj, "bestiary.kills.lord_jawbus_600", 0);
		repoItem(g, "LORD_JAWBUS_SC", guiLeft + 16, guiTop + 120);
		Draw.text(g, "§bLord Jawbus Kills: §f" + jawbusKills, guiLeft + 36, guiTop + 124, true);

		PvItem rod = LegacyItems.fromRepo("FISHING_ROD");
		g.item(rod != null ? rod.stack() : new ItemStack(Items.FISHING_ROD), guiLeft + 20, guiTop + 21);
		Draw.text(g, "§bTotal Caught: §f" + totalCount, guiLeft + 38, guiTop + 25, true);

		// Discovered fish, most caught first; then the undiscovered ones
		List<TrophyFish> sorted = new ArrayList<>(caught.values());
		sorted.sort((a, b) -> Integer.compare(b.total, a.total));
		List<String> missing = new ArrayList<>();
		for (String name : FISH.keySet()) if (!caught.containsKey(name)) missing.add(name);

		int slot = 0;
		for (TrophyFish fish : sorted) {
			int x = guiLeft + SLOT_LOCATIONS[slot][0], y = guiTop + SLOT_LOCATIONS[slot][1];
			TrophyFish.Rarity highest = fish.highest();
			int tint = switch (highest) {
				case BRONZE -> 0xFFFF8200;
				case SILVER -> 0xFFC0C0C0;
				case GOLD -> 0xFFFFD100;
				case DIAMOND -> 0xFF1FD8F1;
				case null -> 0xFFFFFFFF;
			};
			g.blit(RenderPipelines.GUI_TEXTURED, ProfileViewerScreen.PV_ELEMENTS, x - 2, y - 2, 0, 0, 20, 20, 20, 20, 256, 256, tint);
			ItemStack icon = fishItem(fish.internalName, highest);
			if (icon != null) g.item(icon, x, y);
			if (hovered(mouseX, mouseY, x, y)) tooltip = tooltip(fish.internalName, fish);
			slot++;
		}
		for (String name : missing) {
			if (slot >= SLOT_LOCATIONS.length) break;
			int x = guiLeft + SLOT_LOCATIONS[slot][0], y = guiTop + SLOT_LOCATIONS[slot][1];
			Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, x - 2, y - 2, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
			g.item(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("gray_dye"))), x, y);
			if (hovered(mouseX, mouseY, x, y)) tooltip = tooltip(name, null);
			slot++;
		}

		// Fisher titles (Trophy Hunter armor rewards)
		int[] trophiesPerTier = getTrophiesPerTier(trophyObject);
		JsonArray rewards = J.arrOrEmpty(pj, "trophy_fish.rewards");
		for (int i = 0; i < 4; i++) {
			int x = guiLeft + 15, y = guiTop + 50 + i * 10;
			PvItem helmet = LegacyItems.fromRepo(HELMET_IDS[i]);
			if (helmet != null) g.item(helmet.stack(), x, y);
			Draw.text(g, HELMET_TITLES[i], x + 20, y + 4, true);
			int has = trophiesPerTier[i];
			int needed = i == 0 ? 15 : 18;
			String neededText = (has == needed ? "§a" : "§c") + has + "/" + needed;
			JsonElement reward = i < rewards.size() ? rewards.get(i) : null;
			if (reward != null && !reward.isJsonNull()) Draw.text(g, CHECK, x + 100, y + 2, true);
			else Draw.text(g, neededText, x + 100, y + 4, true);
		}

		if (tooltip != null) screen.tooltipToDisplay = tooltip;
	}

	private static boolean hovered(int mouseX, int mouseY, int x, int y) {
		// NEU used a 24x24 box from the icon's corner, which overlapped the neighbouring staggered slots.
		return mouseX >= x - 2 && mouseX < x + 18 && mouseY >= y - 2 && mouseY < y + 18;
	}

	private static void repoItem(GuiGraphicsExtractor g, String id, int x, int y) {
		PvItem item = LegacyItems.fromRepo(id);
		if (item != null) g.item(item.stack(), x, y);
	}

	/** Parses the trophy_fish object into {@code out}; returns total_caught. */
	static long loadTrophyInformation(JsonObject trophyObject, Map<String, TrophyFish> out) {
		long totalCount = 0;
		Map<String, Integer> totals = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : trophyObject.entrySet()) {
			String key = e.getKey();
			if (key.equalsIgnoreCase("total_caught")) {
				totalCount = asInt(e.getValue());
				continue;
			}
			if (key.equalsIgnoreCase("rewards") || key.equalsIgnoreCase("last_caught")) continue;
			int under = key.lastIndexOf('_');
			TrophyFish.Rarity rarity = null;
			if (under > 0) {
				try {
					rarity = TrophyFish.Rarity.valueOf(key.substring(under + 1).toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException ignored) {
				}
			}
			if (rarity == null) {
				totals.put(key, asInt(e.getValue()));
				continue;
			}
			String name = key.substring(0, under);
			// Unknown (future) fish types have no slot on NEU's 18-slot layout.
			if (!FISH.containsKey(name)) continue;
			out.computeIfAbsent(name, TrophyFish::new).add(rarity, asInt(e.getValue()));
		}
		for (TrophyFish fish : out.values()) {
			// NEU crashed (unboxing null) when the per-fish total key was missing.
			Integer t = totals.get(fish.internalName);
			fish.total = t != null ? t : fish.sumOfRarities();
		}
		return totalCount;
	}

	private static int asInt(JsonElement e) {
		try {
			return e.getAsInt();
		} catch (Exception ex) {
			return -1;
		}
	}

	static int[] getTrophiesPerTier(JsonObject trophyFish) {
		int[] perTier = {0, 0, 0, 0};
		for (String fishType : FISH.keySet()) {
			int highestTier = 0;
			if (trophyFish.has(fishType + "_bronze")) highestTier = 1;
			if (trophyFish.has(fishType + "_silver")) highestTier = 2;
			if (trophyFish.has(fishType + "_gold")) highestTier = 3;
			if (trophyFish.has(fishType + "_diamond")) highestTier = 4;
			for (int i = 0; i < highestTier; i++) perTier[i]++;
		}
		return perTier;
	}

	private static String displayName(String internalName) {
		StringBuilder sb = new StringBuilder();
		for (String w : internalName.split("_")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	private static ItemStack fishItem(String internalName, TrophyFish.Rarity highest) {
		String repoName = internalName.toUpperCase(Locale.ROOT) + "_" + (highest == null ? "BRONZE" : highest.name());
		PvItem item = LegacyItems.fromRepo(repoName);
		return item == null ? null : item.stack();
	}

	private static List<Component> tooltip(String internalName, TrophyFish fish) {
		List<String> lines = new ArrayList<>();
		lines.add(FISH.getOrDefault(internalName, "§f") + displayName(internalName));
		lines.addAll(descriptionFromRepo(internalName));
		lines.add(" ");
		Map<TrophyFish.Rarity, Integer> map = fish == null ? null : fish.rarities;
		if (map == null) {
			lines.add("§c" + CHECK_X + " Not Discovered");
			lines.add(" ");
		}
		lines.add(display(map, TrophyFish.Rarity.DIAMOND, "§b"));
		lines.add(display(map, TrophyFish.Rarity.GOLD, "§6"));
		lines.add(display(map, TrophyFish.Rarity.SILVER, "§7"));
		lines.add(display(map, TrophyFish.Rarity.BRONZE, "§8"));
		if (fish != null) {
			lines.add(" ");
			lines.add("§7Total: §6" + Fmt.number(fish.total));
		}
		List<Component> out = new ArrayList<>(lines.size());
		for (String l : lines) out.add(Component.literal(l));
		return out;
	}

	private static String display(Map<TrophyFish.Rarity, Integer> map, TrophyFish.Rarity rarity, String color) {
		String name = rarity.name().charAt(0) + rarity.name().substring(1).toLowerCase(Locale.ROOT);
		if (map == null || !map.containsKey(rarity)) return color + name + ": " + CHECK_X;
		return color + name + ": §6" + Fmt.number(map.get(rarity));
	}

	/** The grey description lines of the repo item (from the first §7 line up to the first empty line). */
	private static List<String> descriptionFromRepo(String internalName) {
		List<String> description = new ArrayList<>();
		JsonObject item = NeuRepo.item(internalName.toUpperCase(Locale.ROOT) + "_BRONZE");
		boolean found = false;
		for (JsonElement e : J.arrOrEmpty(item, "lore")) {
			String line = e.getAsString();
			if (!found && line.startsWith("§7")) found = true;
			if (found && line.isEmpty()) break;
			if (found) description.add(line);
		}
		return description;
	}
}
