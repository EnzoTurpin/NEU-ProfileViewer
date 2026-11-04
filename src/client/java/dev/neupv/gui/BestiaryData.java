/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates BestiaryData.kt, Copyright (C) 2023-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bestiary parsing (categories from the repo's constants/bestiary.json). */
public final class BestiaryData {
	/** NEU 2.6.0's category order. Categories added to the repo later are appended after these. */
	private static final List<String> CATEGORIES_TO_PARSE = List.of(
		"dynamic", "hub", "farming_1", "garden", "combat_1", "combat_3", "crimson_isle", "mining_2", "mining_3",
		"crystal_hollows", "foraging_1", "spooky_festival", "mythological_creatures", "jerry", "kuudra", "catacombs",
		"fishing");
	private static final Set<String> NON_CATEGORY_KEYS = Set.of("brackets", "bracketSets");

	public record Mob(String name, ItemStack icon, double kills, double deaths, MobLevelData mobLevelData) {}

	public record Category(String id, String name, ItemStack icon, List<Mob> mobs, List<Category> subCategories, FamilyData familyData) {}

	public record MobLevelData(int level, boolean maxLevel, double progress, double totalProgress, MobKillData mobKillData) {}

	public record MobKillData(double tierKills, double tierReq, double cappedKills, double cap) {}

	public record FamilyData(int found, int completed, int total) {}

	private BestiaryData() {}

	public static int calculateTotalBestiaryTiers(List<Category> categories) {
		int tiers = 0;
		for (Category c : categories) tiers += countTotalLevels(c);
		return tiers;
	}

	private static int countTotalLevels(Category category) {
		int levels = 0;
		for (Mob m : category.mobs()) levels += m.mobLevelData().level();
		for (Category c : category.subCategories()) levels += countTotalLevels(c);
		return levels;
	}

	/** Profiles not yet migrated to the new bestiary format have bestiary.migration == false. */
	public static boolean hasMigrated(JsonObject profileInfo) {
		JsonObject bestiary = J.obj(profileInfo, "bestiary");
		if (bestiary == null) return false;
		return !bestiary.has("migration") || J.bool(bestiary, "migration", false);
	}

	public static List<Category> parseBestiaryData(JsonObject profileInfo) {
		JsonObject constant = NeuRepo.constant("bestiary");
		if (!hasMigrated(profileInfo) || constant == null) return new ArrayList<>();
		JsonObject apiKills = J.obj(profileInfo, "bestiary.kills");
		JsonObject apiDeaths = J.obj(profileInfo, "bestiary.deaths");
		if (apiKills == null || apiDeaths == null) return new ArrayList<>();
		Map<String, Integer> kills = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : apiKills.entrySet()) {
			if (e.getKey().equals("last_killed_mob")) continue;
			kills.put(e.getKey(), toInt(e.getValue()));
		}
		Map<String, Integer> deaths = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : apiDeaths.entrySet()) deaths.put(e.getKey(), toInt(e.getValue()));

		List<String> ids = new ArrayList<>(CATEGORIES_TO_PARSE);
		for (Map.Entry<String, JsonElement> e : constant.entrySet()) {
			if (!NON_CATEGORY_KEYS.contains(e.getKey()) && !ids.contains(e.getKey()) && e.getValue().isJsonObject()
				&& e.getValue().getAsJsonObject().has("name")) ids.add(e.getKey());
		}
		List<Category> out = new ArrayList<>();
		for (String id : ids) {
			JsonObject data = J.obj(constant, id);
			if (data == null) continue;
			try {
				out.add(parseCategory(constant, data, id, kills, deaths));
			} catch (RuntimeException ignored) {
				// malformed / outdated category in the repo: skip it rather than breaking the whole page
			}
		}
		return out;
	}

	private static int toInt(JsonElement e) {
		try {
			return Integer.parseInt(e.getAsString());
		} catch (Exception ex) {
			try {
				return (int) e.getAsDouble();
			} catch (Exception ex2) {
				return -1;
			}
		}
	}

	private static ItemStack icon(JsonObject data, String name) {
		if (data.has("skullOwner") && data.has("texture")) return LegacyItems.skull(name, cleanBase64(J.str(data, "texture", "")));
		String id = J.str(data, "item", "stone").replace("minecraft:", "");
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
		if (item == null || item == Items.AIR) item = Items.BARRIER;
		return LegacyItems.icon(item, name);
	}

	/**
	 * Some repo textures are unpadded or have a stray trailing char; 1.8 tolerated them, but authlib's strict
	 * Base64 decoder throws (and the head stays blank). Re-encode them canonically.
	 */
	static String cleanBase64(String texture) {
		String s = texture.replace("=", "").trim();
		if (s.length() % 4 == 1) s = s.substring(0, s.length() - 1);
		try {
			byte[] bytes = java.util.Base64.getDecoder().decode(s);
			return java.util.Base64.getEncoder().encodeToString(bytes);
		} catch (IllegalArgumentException e) {
			return texture;
		}
	}

	private static Category parseCategory(JsonObject constant, JsonObject data, String id, Map<String, Integer> kills, Map<String, Integer> deaths) {
		String name = J.str(data, "name", id);
		ItemStack catIcon = icon(J.objOrEmpty(data, "icon"), name);
		if (data.has("hasSubcategories")) {
			List<Category> subs = new ArrayList<>();
			for (Map.Entry<String, JsonElement> e : data.entrySet()) {
				String k = e.getKey();
				if (k.equals("name") || k.equals("icon") || k.equals("hasSubcategories") || !e.getValue().isJsonObject()) continue;
				subs.add(parseCategory(constant, e.getValue().getAsJsonObject(), id + "_" + k, kills, deaths));
			}
			return new Category(id, name, catIcon, List.of(), subs, familyDataOfSubcategories(subs));
		}
		List<Mob> mobs = new ArrayList<>();
		for (JsonElement el : J.arrOrEmpty(data, "mobs")) {
			JsonObject mob = el.getAsJsonObject();
			String mobName = J.str(mob, "name", "?");
			double cap = J.dbl(mob, "cap", 1);
			int bracket = J.i(mob, "bracket", 1);
			String bracketType = J.str(mob, "bracketType", null);
			double k = 0, d = 0;
			for (JsonElement s : J.arrOrEmpty(mob, "mobs")) {
				k += kills.getOrDefault(s.getAsString(), 0);
				d += deaths.getOrDefault(s.getAsString(), 0);
			}
			mobs.add(new Mob(mobName, icon(mob, mobName), k, d, calculateLevel(constant, bracketType, bracket, k, cap)));
		}
		return new Category(id, name, catIcon, mobs, List.of(), familyData(mobs));
	}

	private static MobLevelData calculateLevel(JsonObject constant, String bracketType, int bracket, double kills, double cap) {
		// Newer repo categories (Critter Safari) take their brackets from bracketSets.<type>; NEU only knew "brackets".
		JsonArray arr = bracketType != null
			? J.arr(constant, "bracketSets." + bracketType + "." + bracket)
			: J.arr(constant, "brackets." + bracket);
		List<Double> bracketData = new ArrayList<>();
		if (arr != null) for (JsonElement e : arr) bracketData.add(e.getAsDouble());

		boolean maxLevel = false;
		double effectiveKills = kills;
		if (kills >= cap) {
			maxLevel = true;
			effectiveKills = cap;
		}
		double totalProgress = round1(effectiveKills / cap * 100);
		double progress = 0, effKills = 0, effReq = 0;
		int level = 0;
		for (double required : bracketData) {
			if (effectiveKills >= required) {
				level++;
			} else {
				int prevTierKills = level != 0 ? (int) (double) bracketData.get(level - 1) : 0;
				effKills = kills - prevTierKills;
				effReq = required - prevTierKills;
				progress = round1(effKills / effReq * 100);
				break;
			}
		}
		return new MobLevelData(level, maxLevel, progress, totalProgress, new MobKillData(effKills, effReq, effectiveKills, cap));
	}

	private static double round1(double v) {
		return Math.round(v * 10) / 10.0;
	}

	private static FamilyData familyData(List<Mob> mobs) {
		int found = 0, completed = 0;
		for (Mob m : mobs) {
			if (m.kills() > 0) found++;
			if (m.mobLevelData().maxLevel()) completed++;
		}
		return new FamilyData(found, completed, mobs.size());
	}

	private static FamilyData familyDataOfSubcategories(List<Category> subs) {
		int found = 0, completed = 0, total = 0;
		for (Category c : subs) {
			found += c.familyData().found();
			completed += c.familyData().completed();
			total += c.familyData().total();
		}
		return new FamilyData(found, completed, total);
	}
}
