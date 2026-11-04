/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates GardenDataJson.kt / GardenPage.kt, Copyright (C) 2024 NotEnoughUpdates contributors,
 * LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.util.J;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Garden data (NEU GardenDataJson.kt). Every parser is a pure function of a JSON object, so it can be checked
 * without network; NEU used Gson data classes and crashed (NPE in Kotlin non-null fields) on missing keys.
 */
public final class GardenData {

	public enum Crop {
		WHEAT("ENCHANTED_HAY_BLOCK", "WHEAT", "Wheat"),
		NETHER_WART("ENCHANTED_NETHER_STALK", "NETHER_STALK", "Nether Wart"),
		SUGAR_CANE("ENCHANTED_SUGAR", "SUGAR_CANE", "Sugar Cane"),
		CARROT("ENCHANTED_CARROT", "CARROT_ITEM", "Carrot"),
		POTATO("ENCHANTED_POTATO", "POTATO_ITEM", "Potato"),
		COCOA_BEANS("ENCHANTED_COCOA", "INK_SACK:3", "Cocoa Beans"),
		PUMPKIN("ENCHANTED_PUMPKIN", "PUMPKIN", "Pumpkin"),
		MELON("ENCHANTED_MELON", "MELON", "Melon"),
		CACTUS("ENCHANTED_CACTUS_GREEN", "CACTUS", "Cactus"),
		MUSHROOM("ENCHANTED_BROWN_MUSHROOM", "MUSHROOM_COLLECTION", "Mushroom"),
		// 2025 crops: in the repo/Elitebot data, not in NEU 2.6.0's enum (and not in its 2x5 grid)
		SUNFLOWER("SUNFLOWER", "DOUBLE_PLANT", "Sunflower"),
		MOONFLOWER("MOONFLOWER", "MOONFLOWER", "Moonflower"),
		WILD_ROSE("WILD_ROSE", "WILD_ROSE", "Wild Rose");

		public static final List<Crop> GRID = List.of(WHEAT, NETHER_WART, SUGAR_CANE, CARROT, POTATO, COCOA_BEANS, PUMPKIN, MELON, CACTUS, MUSHROOM);

		public final String itemId, apiName, displayName;

		Crop(String itemId, String apiName, String displayName) {
			this.itemId = itemId;
			this.apiName = apiName;
			this.displayName = displayName;
		}

		/** NEU CropType.fromApiName: API name ("INK_SACK:3"), then enum name ("Cocoa Beans" -> COCOA_BEANS). */
		public static Crop fromApiName(String apiName) {
			String fixed = apiName.toUpperCase(Locale.ROOT).replace(" ", "_");
			for (Crop c : values()) if (c.apiName.equals(fixed)) return c;
			for (Crop c : values()) if (c.name().equals(fixed)) return c;
			return null;
		}
	}

	public enum VisitorRarity {
		UNCOMMON("§aUncommon"), RARE("§9Rare"), LEGENDARY("§6Legendary"), MYTHIC("§dMythic"), SPECIAL("§cSpecial"), TOTAL("§7Total");

		public final String displayName;

		VisitorRarity(String displayName) {
			this.displayName = displayName;
		}
	}

	// ---------------- API data (/v2/skyblock/garden) ----------------

	public final List<String> unlockedPlotIds = new ArrayList<>();
	public final Map<String, Integer> visits = new LinkedHashMap<>();
	public final Map<String, Integer> completed = new LinkedHashMap<>();
	public int totalCompleted, uniqueNpcsServed;
	public final Map<Crop, Long> resourcesCollected = new EnumMap<>(Crop.class);
	public long gardenExperience;
	/** speed, multi_drop, fuel_cap, organic_matter_cap, cost_reduction -> level. */
	public final Map<String, Integer> composterUpgrades = new LinkedHashMap<>();
	public String selectedBarnSkin;
	public final Map<Crop, Integer> cropUpgradeLevels = new EnumMap<>(Crop.class);

	public static final List<String> COMPOSTER_UPGRADES = List.of("speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction");

	/** Parses the garden endpoint answer ({"success":true,"garden":{...}}); null when there is no "garden" object. */
	public static GardenData parse(JsonObject response) {
		JsonObject garden = J.obj(response, "garden");
		if (garden == null) return null;
		GardenData d = new GardenData();
		for (JsonElement e : J.arrOrEmpty(garden, "unlocked_plots_ids")) if (e.isJsonPrimitive()) d.unlockedPlotIds.add(e.getAsString());
		JsonObject commissions = J.objOrEmpty(garden, "commission_data");
		intMap(J.obj(commissions, "visits"), d.visits);
		intMap(J.obj(commissions, "completed"), d.completed);
		d.totalCompleted = J.i(commissions, "total_completed", 0);
		d.uniqueNpcsServed = J.i(commissions, "unique_npcs_served", 0);
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(garden, "resources_collected").entrySet()) {
			Crop c = Crop.fromApiName(e.getKey());
			if (c != null && e.getValue().isJsonPrimitive()) d.resourcesCollected.put(c, e.getValue().getAsLong());
		}
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(garden, "crop_upgrade_levels").entrySet()) {
			Crop c = Crop.fromApiName(e.getKey());
			if (c != null && e.getValue().isJsonPrimitive()) d.cropUpgradeLevels.put(c, e.getValue().getAsInt());
		}
		d.gardenExperience = (long) J.dbl(garden, "garden_experience", 0);
		JsonObject upgrades = J.obj(garden, "composter_data.upgrades");
		if (upgrades != null) for (String u : COMPOSTER_UPGRADES) d.composterUpgrades.put(u, J.i(upgrades, u, 0));
		d.selectedBarnSkin = J.str(garden, "selected_barn_skin", null);
		return d;
	}

	private static void intMap(JsonObject o, Map<String, Integer> out) {
		if (o == null) return;
		for (Map.Entry<String, JsonElement> e : o.entrySet()) if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsInt());
	}

	/** NEU getVisitorData: visits and completions per visitor rarity (+ TOTAL); unknown visitors are skipped. */
	public Map<VisitorRarity, int[]> visitorStats(Repo repo) {
		Map<VisitorRarity, int[]> out = new EnumMap<>(VisitorRarity.class);
		for (VisitorRarity r : VisitorRarity.values()) out.put(r, new int[2]);
		for (Map.Entry<String, Integer> e : visits.entrySet()) {
			VisitorRarity r = repo.visitors.get(e.getKey());
			if (r == null) continue;
			out.get(r)[0] += e.getValue();
			out.get(VisitorRarity.TOTAL)[0] += e.getValue();
		}
		for (Map.Entry<String, Integer> e : completed.entrySet()) {
			VisitorRarity r = repo.visitors.get(e.getKey());
			if (r == null) continue;
			out.get(r)[1] += e.getValue();
			out.get(VisitorRarity.TOTAL)[1] += e.getValue();
		}
		return out;
	}

	// ---------------- Repo data (constants/garden.json) ----------------

	public record Plot(String name, int x, int y) {}

	public record BarnSkin(String name, String item) {}

	public record ComposterUpgrade(int upgrade, Map<String, Integer> items, int copper) {}

	public static final class Repo {
		public final List<Long> gardenExperience = new ArrayList<>();
		public final Map<Crop, List<Long>> cropMilestones = new EnumMap<>(Crop.class);
		public final Map<String, VisitorRarity> visitors = new HashMap<>();
		public final Map<String, Plot> plots = new LinkedHashMap<>();
		public final Map<String, BarnSkin> barn = new HashMap<>();
		public final List<Integer> cropUpgrades = new ArrayList<>();
		public final Map<String, Map<Integer, ComposterUpgrade>> composterUpgrades = new HashMap<>();
		public final Map<String, String> composterTooltips = new HashMap<>();

		/** Parses constants/garden.json; null when it is missing or has no garden_exp. */
		public static Repo parse(JsonObject json) {
			if (json == null || J.arr(json, "garden_exp") == null) return null;
			Repo r = new Repo();
			for (JsonElement e : J.arrOrEmpty(json, "garden_exp")) r.gardenExperience.add(e.getAsLong());
			for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "crop_milestones").entrySet()) {
				Crop c = Crop.fromApiName(e.getKey());
				if (c == null || !e.getValue().isJsonArray()) continue;
				List<Long> l = new ArrayList<>();
				for (JsonElement x : e.getValue().getAsJsonArray()) l.add(x.getAsLong());
				r.cropMilestones.put(c, l);
			}
			for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "visitors").entrySet()) {
				try {
					r.visitors.put(e.getKey(), VisitorRarity.valueOf(e.getValue().getAsString().toUpperCase(Locale.ROOT)));
				} catch (RuntimeException ignored) {
				}
			}
			for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "plots").entrySet()) {
				JsonElement v = e.getValue();
				r.plots.put(e.getKey(), new Plot(J.str(v, "name", e.getKey()), J.i(v, "x", 0), J.i(v, "y", 0)));
			}
			for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "barn").entrySet()) {
				JsonElement v = e.getValue();
				r.barn.put(e.getKey(), new BarnSkin(J.str(v, "name", e.getKey()), J.str(v, "item", null)));
			}
			for (JsonElement e : J.arrOrEmpty(json, "crop_upgrades")) r.cropUpgrades.add(e.getAsInt());
			for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "composter_upgrades").entrySet()) {
				Map<Integer, ComposterUpgrade> levels = new HashMap<>();
				if (e.getValue().isJsonObject()) {
					for (Map.Entry<String, JsonElement> l : e.getValue().getAsJsonObject().entrySet()) {
						try {
							Map<String, Integer> items = new LinkedHashMap<>();
							for (Map.Entry<String, JsonElement> it : J.objOrEmpty(l.getValue(), "items").entrySet()) items.put(it.getKey(), it.getValue().getAsInt());
							levels.put(Integer.parseInt(l.getKey()), new ComposterUpgrade(J.i(l.getValue(), "upgrade", 0), items, J.i(l.getValue(), "copper", 0)));
						} catch (RuntimeException ignored) {
						}
					}
				}
				r.composterUpgrades.put(e.getKey(), levels);
			}
			for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "composter_tooltips").entrySet()) {
				if (e.getValue().isJsonPrimitive()) r.composterTooltips.put(e.getKey(), e.getValue().getAsString());
			}
			return r;
		}
	}

	// ---------------- Crop milestones ----------------

	/**
	 * Milestone of one crop (NEU renderCropMilestones), in long arithmetic: NEU summed the tiers in Int.
	 * The overflow tier counts the tiers above 46 at the price of the last one, like Hypixel's overflow milestones.
	 */
	public record Milestone(int level, boolean maxed, long collected, long progress, long needed, long maxTotal, long overflow, int overflowLevel) {}

	public static Milestone milestone(List<Long> tiers, long collected) {
		long remaining = collected;
		long total = 0;
		for (long t : tiers) total += t;
		int level = 0;
		for (long t : tiers) {
			if (remaining < t) return new Milestone(level, false, collected, remaining, t, total, 0, level);
			remaining -= t;
			level++;
		}
		long last = tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1);
		// NEU: (collection - sum of all tiers) + last tier = amount collected since tier 45
		long overflow = remaining + last;
		int overflowLevel = level + (last > 0 ? (int) Math.min(Integer.MAX_VALUE - level, remaining / last) : 0);
		return new Milestone(level, true, collected, remaining, last, total, overflow, overflowLevel);
	}

	// ---------------- Elitebot farming weight ----------------

	public record EliteWeight(double totalWeight, Map<Crop, Double> cropWeight, double bonusWeight) {}

	/** api.elitebot.dev/weight/{uuid}/{profile}: {"totalWeight":..,"cropWeight":{"Wheat":..},"bonusWeight":{..}}. */
	public static EliteWeight parseElite(JsonObject json) {
		if (json == null || J.get(json, "totalWeight") == null) return null;
		Map<Crop, Double> crops = new EnumMap<>(Crop.class);
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "cropWeight").entrySet()) {
			Crop c = Crop.fromApiName(e.getKey());
			if (c != null && e.getValue().isJsonPrimitive()) crops.put(c, e.getValue().getAsDouble());
		}
		double bonus = 0;
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(json, "bonusWeight").entrySet()) {
			if (e.getValue().isJsonPrimitive()) bonus += e.getValue().getAsDouble();
		}
		return new EliteWeight(J.dbl(json, "totalWeight", 0), crops, bonus);
	}
}
