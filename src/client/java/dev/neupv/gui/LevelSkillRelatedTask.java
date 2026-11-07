/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates SkillRelatedTaskLevel, Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class LevelSkillRelatedTask extends LevelTask {
	private static final ItemStack ICON = new ItemStack(Items.DIAMOND_SWORD);

	LevelSkillRelatedTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		Map<String, Level> levelingInfo = profile.getLevelingInfo();
		if (levelingInfo == null) return;

		JsonObject skillRelatedTask = levelPage.getConstant().getAsJsonObject("skill_related_task");
		JsonObject miningObj = skillRelatedTask.getAsJsonObject("mining");

		Level hotm = levelingInfo.get("hotm");
		int hotmLevel = hotm == null ? 0 : (int) hotm.level;
		int hotmXP = sumFirst(miningObj.getAsJsonArray("hotm_xp"), hotmLevel);

		// NEU added the unspent powder twice (spent + available + available); total = spent + available.
		double totalMithril = J.dbl(object, "mining_core.powder_spent_mithril", 0) + J.dbl(object, "mining_core.powder_mithril", 0);
		double totalGemstone = J.dbl(object, "mining_core.powder_spent_gemstone", 0) + J.dbl(object, "mining_core.powder_gemstone", 0);
		double totalGlacite = J.dbl(object, "mining_core.powder_spent_glacite", 0) + J.dbl(object, "mining_core.powder_glacite", 0);
		double mithrilUnder = Math.min(350000.0, totalMithril);
		double mithrilOver = Math.max(0, Math.min(totalMithril, 12_500_000.0) - 350000.0);
		double gemstoneUnder = Math.min(350000.0, totalGemstone);
		double gemstoneOver = Math.max(0, Math.min(totalGemstone, 20_000_000.0) - 350000.0);
		double glaciteUnder = Math.min(350000.0, totalGlacite);
		double glaciteOver = Math.max(0, Math.min(totalGlacite, 20_000_000.0) - 350000.0);

		double mithrilXP = Math.floor(mithrilUnder / 2400.0);
		double gemstoneXP = Math.floor(gemstoneUnder / 2500.0);
		double glaciteXP = Math.floor(glaciteUnder / 2500.0);
		double mithrilExcess = Math.floor(3.75 * (Math.sqrt(1 + 8 * Math.sqrt((1758267.0 / 12_500_000.0) * mithrilOver + 9)) - 3));
		double gemstoneExcess = Math.floor(4.25 * (Math.sqrt(1 + 8 * Math.sqrt((1758267.0 / 20_000_000.0) * gemstoneOver + 9)) - 3));
		double glaciteExcess = Math.floor(4.25 * (Math.sqrt(1 + 8 * Math.sqrt((1758267.0 / 20_000_000.0) * glaciteOver + 9)) - 3));
		double sbXpHotmTier = (mithrilXP + mithrilExcess) + (gemstoneXP + gemstoneExcess) + (glaciteXP + glaciteExcess) + hotmXP;

		int potm = dev.neupv.pv.SkillTree.peakOfTheMountain(object);
		int sbXpPotmTier = sumFirst(miningObj.getAsJsonArray("potm_xp"), potm);

		int sbXpCommissionMilestone = 0;
		JsonArray commissionMilestoneXpArray = miningObj.getAsJsonArray("commission_milestone_xp");
		for (JsonElement e : J.arrOrEmpty(object, "objectives.tutorial")) {
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) continue;
			String s = e.getAsString();
			if (!s.startsWith("commission_milestone_reward_skyblock_xp_tier")) continue;
			for (int i = 1; i <= commissionMilestoneXpArray.size(); i++) {
				if (s.equals("commission_milestone_reward_skyblock_xp_tier_" + i)) sbXpCommissionMilestone += commissionMilestoneXpArray.get(i - 1).getAsInt();
			}
		}

		int sbXpFossilResearch = J.arrOrEmpty(object, "glacite_player_data.fossils_donated").size() * miningObj.get("fossil_research_xp").getAsInt();

		double oresMined = J.dbl(object, "player_stats.pets.milestone.ores_mined", 0);
		int sbXpRockPet = 0;
		int rockMilestoneXp = miningObj.get("rock_milestone_xp").getAsInt();
		for (JsonElement e : miningObj.getAsJsonArray("rock_milestone_required")) if (oresMined >= e.getAsInt()) sbXpRockPet += rockMilestoneXp;

		// farming
		JsonObject farmingObj = skillRelatedTask.getAsJsonObject("farming");
		int sbXpGainedByAnita = (intOf(object, "jacobs_contest.perks.double_drops") + intOf(object, "jacobs_contest.perks.farming_level_cap"))
			* farmingObj.get("anita_shop_upgrades_xp").getAsInt();

		// fishing
		JsonObject fishingObj = skillRelatedTask.getAsJsonObject("fishing");
		JsonArray trophyFishXpArr = fishingObj.getAsJsonArray("trophy_fish_xp");
		int sbXpTrophyFish = 0;
		JsonObject trophyFish = J.obj(object, "trophy_fish");
		if (trophyFish != null) {
			for (Map.Entry<String, JsonElement> e : trophyFish.entrySet()) {
				if (!e.getValue().isJsonPrimitive()) continue;
				String key = e.getKey();
				if (key.endsWith("_bronze")) sbXpTrophyFish += trophyFishXpArr.get(0).getAsInt();
				if (key.endsWith("_silver")) sbXpTrophyFish += trophyFishXpArr.get(1).getAsInt();
				if (key.endsWith("_gold")) sbXpTrophyFish += trophyFishXpArr.get(2).getAsInt();
				if (key.endsWith("_diamond")) sbXpTrophyFish += trophyFishXpArr.get(3).getAsInt();
			}
		}
		double seaCreatures = J.dbl(object, "player_stats.pets.milestone.sea_creatures_killed", 0);
		int sbXpDolphinPet = 0;
		int dolphinMilestoneXp = fishingObj.get("dolphin_milestone_xp").getAsInt();
		for (JsonElement e : fishingObj.getAsJsonArray("dolphin_milestone_required")) if (seaCreatures >= e.getAsInt()) sbXpDolphinPet += dolphinMilestoneXp;

		int sbXpNucleus = 0;
		if (miningObj.has("crystal_nucleus_xp")) {
			// NEU did not cap it (50+ runs showed > 100 %)
			sbXpNucleus = Math.min(intOf(object, "leveling.completions.NUCLEUS_RUNS") * miningObj.get("crystal_nucleus_xp").getAsInt(),
				miningObj.get("crystal_nucleus").getAsInt());
		}

		List<String> lore = new ArrayList<>();
		lore.add(LevelPage.buildLore("Heart of the Mountain", sbXpHotmTier, miningObj.get("hotm").getAsInt(), false));
		lore.add(LevelPage.buildLore("Commission Milestones", sbXpCommissionMilestone, miningObj.get("commission_milestone").getAsInt(), false));
		lore.add(LevelPage.buildLore("Crystal Nucleus", sbXpNucleus, miningObj.get("crystal_nucleus").getAsInt(), false));
		lore.add(LevelPage.buildLore("Anita's Shop Upgrade", sbXpGainedByAnita, farmingObj.get("anita_shop_upgrades").getAsInt(), false));
		lore.add(LevelPage.buildLore("Peak of the Mountain", sbXpPotmTier, miningObj.get("potm").getAsInt(), false));
		lore.add(LevelPage.buildLore("Fossil Research", sbXpFossilResearch, miningObj.get("fossil_research").getAsInt(), false));
		lore.add(LevelPage.buildLore("Trophy Fish", sbXpTrophyFish, fishingObj.get("trophy_fish").getAsInt(), false));
		lore.add(LevelPage.buildLore("Rock Milestone", sbXpRockPet, miningObj.get("rock_milestone").getAsInt(), false));
		lore.add(LevelPage.buildLore("Dolphin Milestone", sbXpDolphinPet, fishingObj.get("dolphin_milestone").getAsInt(), false));

		int totalXp = (int) (sbXpHotmTier + sbXpCommissionMilestone + sbXpGainedByAnita + sbXpPotmTier + sbXpTrophyFish + sbXpRockPet
			+ sbXpDolphinPet + sbXpNucleus + sbXpFossilResearch);
		levelPage.renderLevelBar(g, "Skill Related Task", ICON, guiLeft + 23, guiTop + 115, 110, 0, totalXp,
			levelPage.getConstant().getAsJsonObject("category_xp").get("skill_related_task").getAsInt(), mouseX, mouseY, true, lore);
	}
}
