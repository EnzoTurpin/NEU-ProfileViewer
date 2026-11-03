/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates SlayingTaskLevel, Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.pv.Weights;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LevelSlayingTask extends LevelTask {
	private static final int[] BOSS_LOW = {25, 50, 100, 150, 250, 1000};
	private static final int[] THORN = {25, 50, 150, 250, 400, 1000};
	private static final int[] BOSS_HIGH = {50, 100, 150, 250, 500, 750, 1000};
	/** NEU CrimsonIslePage.KUUDRA_TIERS. */
	private static final String[] KUUDRA_TIERS = {"none", "hot", "burning", "fiery", "infernal"};
	/** bestiary kill ids of Arachne (tier 1 = level 300, tier 2 = level 500), in defeat_arachne_xp order. */
	private static final String[] ARACHNE_IDS = {"arachne_300", "arachne_500"};
	private static final ItemStack ICON = new ItemStack(Items.GOLDEN_SWORD);

	private JsonObject bestiaryCacheKey;
	private int bestiaryCache;

	LevelSlayingTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void resetCache() {
		bestiaryCacheKey = null;
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		JsonObject slayingTask = levelPage.getConstant().getAsJsonObject("slaying_task");
		JsonArray slayerLevelUpXp = slayingTask.getAsJsonArray("slayer_level_up_xp");

		Map<String, Level> skyblockInfo = profile.getLevelingInfo();
		int sbXpGainedSlayer = 0;
		if (skyblockInfo != null) {
			for (String slayer : Weights.SLAYER_NAMES) {
				Level level = skyblockInfo.get(slayer);
				if (level != null) sbXpGainedSlayer += sumFirst(slayerLevelUpXp, (int) level.level);
			}
		}

		// floor -> completions, master mode counting double
		Map<String, Double> allComps = new HashMap<>();
		JsonObject normal = J.obj(object, "dungeons.dungeon_types.catacombs.tier_completions");
		JsonObject master = J.obj(object, "dungeons.dungeon_types.master_catacombs.tier_completions");
		if (normal != null) {
			for (Map.Entry<String, JsonElement> e : normal.entrySet()) allComps.merge(e.getKey(), num(e.getValue()), Double::sum);
		}
		if (master != null) {
			// NEU only doubled master completions when the floor also had normal completions
			for (Map.Entry<String, JsonElement> e : master.entrySet()) allComps.merge(e.getKey(), num(e.getValue()) * 2, Double::sum);
		}

		int bossCollectionXp = 0;
		JsonArray dungeonCollectionXp = slayingTask.getAsJsonObject("boss_collections_xp").getAsJsonArray("dungeon_collection_xp");
		for (int i = 1; i <= 7; i++) {
			Double value = allComps.get(String.valueOf(i));
			if (value == null) continue;
			int[] reqs = i <= 3 ? BOSS_LOW : i == 4 ? THORN : BOSS_HIGH;
			bossCollectionXp += loopThroughCollection(reqs, value, dungeonCollectionXp);
		}

		// kuudra
		JsonArray defeatKuudraXp = slayingTask.getAsJsonArray("defeat_kuudra_xp");
		int sbXpDefeatKuudra = 0;
		int kuudraBossCollection = 0;
		if (object.has("nether_island_player_data")) {
			JsonObject kuudraCompletedTiers = J.objOrEmpty(object, "nether_island_player_data.kuudra_completed_tiers");
			for (Map.Entry<String, JsonElement> e : kuudraCompletedTiers.entrySet()) {
				for (int i = 0; i < KUUDRA_TIERS.length; i++) {
					if (e.getKey().equals(KUUDRA_TIERS[i])) {
						if (i < defeatKuudraXp.size()) sbXpDefeatKuudra += defeatKuudraXp.get(i).getAsInt();
						kuudraBossCollection += (i + 1) * (int) num(e.getValue());
					}
				}
			}
			if (kuudraBossCollection >= 10) bossCollectionXp += 10;
			if (kuudraBossCollection >= 100) bossCollectionXp += 15;
			if (kuudraBossCollection >= 500) bossCollectionXp += 20;
			if (kuudraBossCollection >= 2000) bossCollectionXp += 25;
			if (kuudraBossCollection >= 5000) bossCollectionXp += 30;
		}

		if (bestiaryCacheKey != object) {
			bestiaryCacheKey = object;
			bestiaryCache = LevelBestiaryXp.calculate(object);
		}
		int sbXpBestiary = bestiaryCache;

		int mythologicalKillsMax = slayingTask.get("mythological_kills").getAsInt();
		int mythologicalKillsXp = Math.min(J.i(object, "player_stats.mythos.kills", 0) / 100, mythologicalKillsMax);

		// dragons: NEU summed slay_dragons_xp unconditionally (everyone had 200/200); now only for dragons killed
		int sbXpFromDragonKills = 0;
		for (Map.Entry<String, JsonElement> e : slayingTask.getAsJsonObject("slay_dragons_xp").entrySet()) {
			if (J.dbl(object, "bestiary.kills." + e.getKey() + "_100", 0) > 0 || J.dbl(object, "player_stats.kills." + e.getKey() + "_100", 0) > 0) {
				sbXpFromDragonKills += e.getValue().getAsInt();
			}
		}

		// slayer kills
		int sbXpFromSlayerDefeat = 0;
		JsonArray defeatSlayersXp = slayingTask.getAsJsonArray("defeat_slayers_xp");
		JsonObject slayerToTier = J.obj(NeuRepo.leveling(), "slayer_to_highest_tier");
		if (slayerToTier != null) {
			for (Map.Entry<String, JsonElement> e : slayerToTier.entrySet()) {
				int maxLevel = e.getValue().getAsInt();
				for (int i = 0; i < 5 && i < maxLevel; i++) {
					if (J.dbl(object, "slayer.slayer_bosses." + e.getKey() + ".boss_kills_tier_" + i, 0) != 0) {
						sbXpFromSlayerDefeat += defeatSlayersXp.get(i).getAsInt();
					}
				}
			}
		}

		// arachne: same NEU bug as the dragons (always full); now from the bestiary kills
		JsonArray defeatArachneXp = slayingTask.getAsJsonArray("defeat_arachne_xp");
		int sbXpGainedArachne = 0;
		for (int i = 0; i < defeatArachneXp.size() && i < ARACHNE_IDS.length; i++) {
			if (J.dbl(object, "bestiary.kills." + ARACHNE_IDS[i], 0) > 0) sbXpGainedArachne += defeatArachneXp.get(i).getAsInt();
		}

		List<String> lore = new ArrayList<>();
		lore.add(LevelPage.buildLore("Slayer Level Up", sbXpGainedSlayer, slayingTask.get("slayer_level_up").getAsInt(), false));
		lore.add(LevelPage.buildLore("Boss Collections", bossCollectionXp, slayingTask.get("boss_collections").getAsInt(), false));
		lore.add(LevelPage.buildLore("Bestiary Progress", sbXpBestiary, slayingTask.get("bestiary_progress").getAsInt(), false));
		lore.add(LevelPage.buildLore("Mythological Kills", mythologicalKillsXp, mythologicalKillsMax, false));
		lore.add(LevelPage.buildLore("Slay Dragons", sbXpFromDragonKills, slayingTask.get("slay_dragons").getAsInt(), false));
		lore.add(LevelPage.buildLore("Defeat Slayers", sbXpFromSlayerDefeat, slayingTask.get("defeat_slayers").getAsInt(), false));
		lore.add(LevelPage.buildLore("Defeat Kuudra", sbXpDefeatKuudra, slayingTask.get("defeat_kuudra").getAsInt(), false));
		lore.add(LevelPage.buildLore("Defeat Arachne", sbXpGainedArachne, slayingTask.get("defeat_arachne").getAsInt(), false));

		int totalXp = sbXpGainedSlayer + bossCollectionXp + mythologicalKillsXp + sbXpFromDragonKills + sbXpFromSlayerDefeat
			+ sbXpDefeatKuudra + sbXpGainedArachne + sbXpBestiary;
		levelPage.renderLevelBar(g, "Slaying Task", ICON, guiLeft + 23, guiTop + 85, 110, 0, totalXp,
			levelPage.getConstant().getAsJsonObject("category_xp").get("slaying_task").getAsInt(), mouseX, mouseY, true, lore);
	}

	private static double num(JsonElement e) {
		try {
			return e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	private static int loopThroughCollection(int[] reqs, double value, JsonArray xp) {
		int gain = 0;
		for (int i = 0; i < reqs.length && i < xp.size(); i++) {
			if (value >= reqs[i]) gain += xp.get(i).getAsInt();
		}
		return gain;
	}
}
