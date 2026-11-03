/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates MiscTaskLevel, Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

class LevelMiscTask extends LevelTask {
	/** NEU CrimsonIslePage.apiDojoTestNames keys. */
	private static final String[] DOJO_TESTS = {"mob_kb", "wall_jump", "archer", "sword_swap", "snake", "lock_head", "fireball"};
	/** NEU CrimsonIslePage.dojoPointsToRank keys (None, Yellow, Green, Blue, Brown, Black). */
	private static final int[] DOJO_RANKS = {0, 1000, 2000, 4000, 6000, 7000};
	private static final ItemStack ICON = new ItemStack(Items.FILLED_MAP);

	LevelMiscTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		JsonObject misc = levelPage.getConstant().getAsJsonObject("miscellaneous_task");

		// NEU only counted these when accessory_bag_storage.unlocked_powers existed, and skipped the whole bar when
		// nether_island_player_data was missing; each source is now read on its own.
		int sbXpAccessoryUpgrade = intOf(object, "accessory_bag_storage.bag_upgrades_purchased") * misc.get("accessory_bag_upgrades_xp").getAsInt();
		int sbXpUnlockedPowers = J.arrOrEmpty(object, "accessory_bag_storage.unlocked_powers").size() * misc.get("unlocking_powers_xp").getAsInt();
		int sbXpReaperPeppers = intOf(object, "player_data.reaper_peppers_eaten") * misc.get("reaper_peppers_xp").getAsInt();
		int sbXpRefinedJyrre = intOf(object, "winter_player_data.refined_jyrre_uses") * misc.get("refined_jyrre_xp").getAsInt();
		int sbXpWrigglingLarva = intOf(object, "garden_player_data.larva_consumed") * misc.get("wriggling_larva_xp").getAsInt();
		int sbXpRefinedDarkCacaoTruffle = intOf(object, "events.easter.refined_dark_cacao_truffles") * misc.get("refined_dark_cacao_truffles_xp").getAsInt();

		int sbXpDojo = 0;
		int sbXpRelays = 0;
		int sbXpAbiphone = 0;
		JsonObject nether = J.obj(object, "nether_island_player_data");
		if (nether != null) {
			JsonObject abiphone = J.obj(nether, "abiphone");
			// NEU defaulted repaired_index to 0 and gave 5 XP to players without an operator chip
			if (abiphone != null && J.get(abiphone, "operator_chip.repaired_index") != null) {
				sbXpRelays += (intOf(abiphone, "operator_chip.repaired_index") + 1) * misc.get("unlocking_relays_xp").getAsInt();
			}
			JsonObject dojo = J.obj(nether, "dojo");
			if (dojo != null) {
				int pointsTotal = 0;
				for (String test : DOJO_TESTS) pointsTotal += J.i(dojo, "dojo_points_" + test, 0);
				int index = 0;
				for (int required : DOJO_RANKS) if (pointsTotal > required) index++;
				sbXpDojo = sumFirst(misc.getAsJsonArray("the_dojo_xp"), index);
			}
			if (abiphone != null && abiphone.has("active_contacts")) {
				long contacts = completedTasks(object).stream().filter(s -> s.startsWith("ABIPHONE_")).count();
				sbXpAbiphone = (int) contacts * misc.get("abiphone_contacts_xp").getAsInt();
			}
		}

		// harp
		int sbXpGainedHarp = 0;
		JsonObject harpSongsNames = misc.getAsJsonObject("harp_songs_names");
		for (String name : completedTasks(object)) {
			int idx = name.lastIndexOf('_');
			if (idx < 0) continue; // NEU threw StringIndexOutOfBounds here
			String harpName = name.substring(0, idx);
			if (harpSongsNames.has(harpName)) sbXpGainedHarp += harpSongsNames.get(harpName).getAsInt() / 4;
		}

		// community upgrades
		int sbXpCommunityUpgrade = 0;
		JsonArray upgradeStates = J.arr(profile.getOuterProfileJson(), "community_upgrades.upgrade_states");
		JsonObject upgradesMax = misc.getAsJsonObject("community_shop_upgrades_max");
		int communityShopUpgradesXp = misc.get("community_shop_upgrades_xp").getAsInt();
		if (upgradeStates != null) {
			for (JsonElement state : upgradeStates) {
				if (!state.isJsonObject()) continue;
				String upgrade = J.str(state, "upgrade", "");
				int tier = J.i(state, "tier", 0);
				if (upgradesMax.has(upgrade) && upgradesMax.get(upgrade).getAsInt() >= tier) sbXpCommunityUpgrade += communityShopUpgradesXp;
			}
		}

		int sbXpPersonalBank = sumFirst(misc.getAsJsonArray("personal_bank_upgrades_xp"), intOf(object, "profile.personal_bank_upgrade"));
		int sbXpTimeCharm = J.arrOrEmpty(object, "rift.gallery.secured_trophies").size() * misc.get("timecharm_xp").getAsInt();
		int sbXpBurger = intOf(object, "rift.castle.grubber_stacks") * misc.get("mcgrubber_burger_xp").getAsInt();
		int sbXpSerum = misc.has("metaphysical_serum_xp") ? misc.get("metaphysical_serum_xp").getAsInt() * intOf(object, "experimentation.serums_drank") : 0;

		int xpConsumableItems = sbXpReaperPeppers + sbXpBurger + sbXpSerum + sbXpRefinedJyrre + sbXpWrigglingLarva + sbXpRefinedDarkCacaoTruffle;

		List<String> lore = new ArrayList<>();
		lore.add(LevelPage.buildLore("Accessory Bag Upgrades", sbXpAccessoryUpgrade, 0, true));
		lore.add(LevelPage.buildLore("Consumable Items", xpConsumableItems, misc.get("consumable_items").getAsInt(), false));
		lore.add(LevelPage.buildLore("Timecharms", sbXpTimeCharm, misc.get("timecharm").getAsInt(), false));
		lore.add(LevelPage.buildLore("Unlocking Powers", sbXpUnlockedPowers, 0, true));
		lore.add(LevelPage.buildLore("The Dojo", sbXpDojo, misc.get("the_dojo").getAsInt(), false));
		lore.add(LevelPage.buildLore("Harp Songs", sbXpGainedHarp, misc.get("harp_songs").getAsInt(), false));
		lore.add(LevelPage.buildLore("Abiphone Contacts", sbXpAbiphone, misc.get("abiphone_contacts").getAsInt(), false));
		lore.add(LevelPage.buildLore("Community Shop Upgrades", sbXpCommunityUpgrade, misc.get("community_shop_upgrades").getAsInt(), false));
		lore.add(LevelPage.buildLore("Personal Bank Upgrades", sbXpPersonalBank, misc.get("personal_bank_upgrades").getAsInt(), false));
		lore.add(LevelPage.buildLore("Upgraded Relays", sbXpRelays, misc.get("unlocking_relays").getAsInt(), false));

		// Like NEU, the unlimited categories (accessory bag upgrades, powers) are not part of the capped category total.
		int totalXp = sbXpDojo + sbXpGainedHarp + sbXpAbiphone + sbXpCommunityUpgrade + sbXpPersonalBank + sbXpTimeCharm + sbXpRelays
			+ xpConsumableItems;
		levelPage.renderLevelBar(g, "Misc. Task", ICON, guiLeft + 299, guiTop + 55, 110, 0, totalXp,
			levelPage.getConstant().getAsJsonObject("category_xp").get("miscellaneous_task").getAsInt(), mouseX, mouseY, true, lore);
	}
}
