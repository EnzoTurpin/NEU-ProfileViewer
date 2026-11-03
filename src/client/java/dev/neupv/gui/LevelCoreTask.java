/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates CoreTaskLevel and ProfileCollectionInfo, Copyright (C) 2022-2024 NotEnoughUpdates contributors,
 * LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LevelCoreTask extends LevelTask {
	static final String COLLECTIONS_URL = "https://api.hypixel.net/v2/resources/skyblock/collections";
	/** misc.json "minionXp" (removed from NEU-REPO on 2026-10-30): SkyBlock XP for crafting a minion of tier 1..12. */
	private static final int[] MINION_XP_FALLBACK = {1, 1, 1, 1, 1, 1, 2, 3, 4, 6, 12, 24};
	private static final Pattern GENERATOR = Pattern.compile("^([^0-9]+)_([0-9]+)$");
	private static final List<String> SKILLS = List.of("taming", "mining", "foraging", "enchanting", "carpentry", "farming",
		"combat", "fishing", "alchemy");
	private static final ItemStack ICON = new ItemStack(Items.NETHER_STAR);

	LevelCoreTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		JsonObject coreTask = levelPage.getConstant().getAsJsonObject("core_task");

		Map<String, Level> skyblockInfo = profile.getLevelingInfo();
		int sbXpGainedSkillLVL = 0;
		if (skyblockInfo != null && profile.skillsApiEnabled()) {
			for (String skill : SKILLS) {
				Level level = skyblockInfo.get(skill);
				if (level == null) continue;
				for (int i = 1; i <= level.level; i++) {
					if (i <= 10) sbXpGainedSkillLVL += 5;
					else if (i <= 25) sbXpGainedSkillLVL += 10;
					else if (i <= 50) sbXpGainedSkillLVL += 20;
					else if (i <= 60) sbXpGainedSkillLVL += 30;
				}
			}
		} else {
			sbXpGainedSkillLVL = -1;
		}

		int sbXpGainedMp = intOf(object, "accessory_bag_storage.highest_magical_power");
		int sbXpPetScore = intOf(object, "leveling.highest_pet_score") * coreTask.get("pet_score_xp").getAsInt();
		int sbXpGainedFairy = (intOf(object, "fairy_soul.total_collected") - intOf(object, "fairy_soul.unspent_souls")) / 5
			* coreTask.get("fairy_souls_xp").getAsInt();

		int sbXpCollection = collectionXp(profile, coreTask.get("collections_xp").getAsInt());
		int sbXpMinionTier = minionXp(profile.getOuterProfileJson());

		int sbXpBankUpgrades = 0;
		int sbXpTravelScroll = 0;
		JsonObject bankUpgradesXp = coreTask.getAsJsonObject("bank_upgrades_xp");
		for (String name : completedTasks(object)) {
			if (name.startsWith("FAST_TRAVEL_") && coreTask.has("fast_travel_unlocked_xp")) {
				sbXpTravelScroll += coreTask.get("fast_travel_unlocked_xp").getAsInt();
			}
			if (bankUpgradesXp != null && bankUpgradesXp.has(name)) sbXpBankUpgrades += bankUpgradesXp.get(name).getAsInt();
		}

		// NEU added the -1 "collection disabled" / not-loaded markers into the total; only real XP counts here.
		int totalXp = Math.max(0, sbXpGainedSkillLVL) + sbXpGainedFairy + Math.max(0, sbXpCollection) + Math.max(0, sbXpMinionTier)
			+ sbXpBankUpgrades + sbXpTravelScroll;

		List<String> lore = new ArrayList<>();
		lore.add(LevelPage.buildLore("Skill Level Up", sbXpGainedSkillLVL, coreTask.get("skill_level_up").getAsInt(), false));
		lore.add(LevelPage.buildLore("Museum Progression", 0, 0, false));
		lore.add(LevelPage.buildLore("Fairy Soul", sbXpGainedFairy, coreTask.get("fairy_souls").getAsInt(), false));
		lore.add(LevelPage.buildLore("Accessory Bag", sbXpGainedMp, 0, true));
		lore.add(LevelPage.buildLore("Pet Score", sbXpPetScore, 0, true));
		lore.add(LevelPage.buildLore("Collections", sbXpCollection, coreTask.get("collections").getAsInt(), false));
		lore.add(LevelPage.buildLore("Craft Minions", sbXpMinionTier, coreTask.get("craft_minions").getAsInt(), false));
		lore.add(LevelPage.buildLore("Bank Upgrade", sbXpBankUpgrades, coreTask.get("bank_upgrades").getAsInt(), false));
		lore.add(LevelPage.buildLore("Fast Travel Scroll", sbXpTravelScroll, coreTask.get("fast_travel_unlocked").getAsInt(), false));

		levelPage.renderLevelBar(g, "Core Task", ICON, guiLeft + 23, guiTop + 25, 110, 0, totalXp,
			levelPage.getConstant().getAsJsonObject("category_xp").get("core_task").getAsInt(), mouseX, mouseY, true, lore);
	}

	/**
	 * XP for unlocked collection tiers, summed over all coop members like NEU's ProfileCollectionInfo.
	 * -1 = collections API disabled, -2 = Hypixel collections resource still loading (or failed).
	 */
	private int collectionXp(SkyblockProfiles.SkyblockProfile profile, int xpPerTier) {
		JsonObject outer = profile.getOuterProfileJson();
		if (J.obj(profile.getProfileJson(), "collection") == null) return -1;
		SkyblockProfiles.Fetch fetch = levelPage.screen().getProfile().fetchUrl(COLLECTIONS_URL);
		if (fetch.state() != SkyblockProfiles.FetchState.OK || fetch.json() == null) return -2;

		Map<String, BigInteger> totals = new HashMap<>();
		for (Map.Entry<String, JsonElement> member : J.objOrEmpty(outer, "members").entrySet()) {
			JsonObject coll = J.obj(member.getValue(), "collection");
			if (coll == null) continue;
			for (Map.Entry<String, JsonElement> e : coll.entrySet()) {
				try {
					totals.merge(e.getKey(), e.getValue().getAsBigInteger(), BigInteger::add);
				} catch (RuntimeException ignored) {
				}
			}
		}
		int xp = 0;
		for (Map.Entry<String, JsonElement> category : J.objOrEmpty(fetch.json(), "collections").entrySet()) {
			for (Map.Entry<String, JsonElement> item : J.objOrEmpty(category.getValue(), "items").entrySet()) {
				BigInteger total = totals.getOrDefault(item.getKey(), BigInteger.ZERO);
				for (JsonElement tier : J.arrOrEmpty(item.getValue(), "tiers")) {
					long required = J.lng(tier, "amountRequired", Long.MAX_VALUE);
					if (BigInteger.valueOf(required).compareTo(total) <= 0) xp += xpPerTier;
				}
			}
		}
		return xp;
	}

	/** Highest crafted tier of each minion type over all coop members; XP for tiers 1..max. */
	private static int minionXp(JsonObject outer) {
		Map<String, Integer> highest = new HashMap<>();
		for (Map.Entry<String, JsonElement> member : J.objOrEmpty(outer, "members").entrySet()) {
			for (JsonElement gen : J.arrOrEmpty(member.getValue(), "player_data.crafted_generators")) {
				if (!gen.isJsonPrimitive()) continue;
				Matcher m = GENERATOR.matcher(gen.getAsString());
				if (!m.matches()) continue;
				highest.merge(m.group(1), Integer.parseInt(m.group(2)), Math::max);
			}
		}
		JsonObject minionXp = J.obj(NeuRepo.misc(), "minionXp");
		int xp = 0;
		for (Map.Entry<String, Integer> e : highest.entrySet()) {
			for (int i = 1; i <= e.getValue(); i++) {
				// NEU starts at -1: the Cobblestone I minion every player starts with gives no XP
				if (i == 1 && e.getKey().equals("COBBLESTONE")) continue;
				if (minionXp != null) xp += J.i(minionXp, String.valueOf(i), 0);
				else if (i <= MINION_XP_FALLBACK.length) xp += MINION_XP_FALLBACK[i - 1];
			}
		}
		return xp;
	}
}
