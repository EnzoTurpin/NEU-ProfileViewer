/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates BestiaryData.calculateBestiarySkyblockXp, Copyright (C) 2023 NotEnoughUpdates contributors,
 * LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SkyBlock XP given by bestiary tiers (self-contained: does not depend on the Bestiary tab). */
final class LevelBestiaryXp {
	/** Same categories as NEU's BestiaryData.categoriesToParse. */
	private static final List<String> CATEGORIES = List.of("dynamic", "hub", "farming_1", "garden", "combat_1", "combat_3",
		"crimson_isle", "mining_2", "mining_3", "crystal_hollows", "foraging_1", "spooky_festival", "mythological_creatures",
		"jerry", "kuudra", "catacombs", "fishing");
	private static final Set<String> RESERVED = Set.of("name", "icon", "hasSubcategories");

	private LevelBestiaryXp() {}

	/** @return the bestiary SkyBlock XP, capped at slaying_task.bestiary_progress (0 if data is missing). */
	static int calculate(JsonObject profileInfo) {
		JsonObject slayingTask = J.obj(NeuRepo.sblevels(), "slaying_task");
		if (slayingTask == null || !slayingTask.has("bestiary_family_xp")) return 0;
		int xpPerTier = slayingTask.get("bestiary_family_xp").getAsInt();
		int xpPerMilestone = J.i(slayingTask, "bestiary_milestone_xp", 0);
		int maxXp = J.i(slayingTask, "bestiary_progress", Integer.MAX_VALUE);

		int totalTiers = totalTiers(profileInfo);
		int skyblockXp = totalTiers * xpPerTier + (totalTiers / 100) * xpPerMilestone;
		return Math.min(skyblockXp, maxXp);
	}

	static int totalTiers(JsonObject profileInfo) {
		JsonObject bestiary = NeuRepo.constant("bestiary");
		if (bestiary == null || !hasMigrated(profileInfo)) return 0;
		JsonObject apiKills = J.obj(profileInfo, "bestiary.kills");
		if (apiKills == null || J.obj(profileInfo, "bestiary.deaths") == null) return 0;
		Map<String, Integer> kills = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : apiKills.entrySet()) {
			if (e.getKey().equals("last_killed_mob")) continue;
			try {
				kills.put(e.getKey(), Integer.parseInt(e.getValue().getAsString()));
			} catch (RuntimeException ex) {
				kills.put(e.getKey(), -1);
			}
		}
		int tiers = 0;
		for (String categoryId : CATEGORIES) {
			JsonObject category = J.obj(bestiary, categoryId);
			if (category != null) tiers += countCategory(bestiary, category, kills);
		}
		return tiers;
	}

	private static boolean hasMigrated(JsonObject profileInfo) {
		JsonObject bestiary = J.obj(profileInfo, "bestiary");
		if (bestiary == null) return false;
		// accounts without the field are new and already use the new format
		return !bestiary.has("migration") || J.bool(bestiary, "migration", false);
	}

	private static int countCategory(JsonObject bestiary, JsonObject category, Map<String, Integer> kills) {
		int levels = 0;
		if (category.has("hasSubcategories")) {
			for (Map.Entry<String, JsonElement> e : category.entrySet()) {
				if (RESERVED.contains(e.getKey()) || !e.getValue().isJsonObject()) continue;
				levels += countCategory(bestiary, e.getValue().getAsJsonObject(), kills);
			}
			return levels;
		}
		for (JsonElement mobEl : J.arrOrEmpty(category, "mobs")) {
			JsonObject mob = mobEl.getAsJsonObject();
			double cap = J.dbl(mob, "cap", 0);
			double mobKills = 0;
			for (JsonElement id : J.arrOrEmpty(mob, "mobs")) mobKills += kills.getOrDefault(id.getAsString(), 0);
			String bracketType = J.str(mob, "bracketType", null);
			JsonArray bracket = bracketType != null
				? J.arr(bestiary, "bracketSets." + bracketType + "." + J.i(mob, "bracket", 0))
				: J.arr(bestiary, "brackets." + J.i(mob, "bracket", 0));
			levels += level(bracket, mobKills, cap);
		}
		return levels;
	}

	/** NEU BestiaryData.calculateLevel (only the level is needed here). */
	private static int level(JsonArray bracket, double kills, double cap) {
		if (bracket == null) return 0;
		double effectiveKills = Math.min(kills, cap);
		int level = 0;
		for (JsonElement required : bracket) {
			if (effectiveKills >= required.getAsDouble()) level++;
			else break;
		}
		return level;
	}
}
