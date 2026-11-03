/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates EventTaskLevel, Copyright (C) 2022-2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class LevelEventTask extends LevelTask {
	private static final ItemStack ICON = new ItemStack(Items.CLOCK);

	LevelEventTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		JsonObject eventTask = levelPage.getConstant().getAsJsonObject("event_task");

		int sbXpMiningFiesta = 0;
		int sbXpFishingFestival = 0;
		int sbXpSpookyFestival = 0;
		if (object.has("leveling")) {
			sbXpMiningFiesta = capOrAmount(intOf(object, "leveling.mining_fiesta_ores_mined"), 1_000_000, 5_000);
			sbXpFishingFestival = capOrAmount(intOf(object, "leveling.fishing_festival_sharks_killed"), 5_000, 50);
			JsonObject spookyFestivalXp = eventTask.getAsJsonObject("spooky_festival_xp");
			for (String name : completedTasks(object)) {
				if (spookyFestivalXp.has(name)) sbXpSpookyFestival += spookyFestivalXp.get(name).getAsInt();
			}
		}

		Set<String> uniqueElements = new HashSet<>();
		for (String bracket : new String[]{"gold", "platinum", "diamond"}) {
			for (JsonElement e : J.arrOrEmpty(object, "jacobs_contest.unique_brackets." + bracket)) uniqueElements.add(e.getAsString());
		}
		int sbXpUniqueMedals = uniqueElements.size() * eventTask.get("jacob_farming_contest_xp").getAsInt();

		List<String> lore = new ArrayList<>();
		lore.add(LevelPage.buildLore("Mining Fiesta", sbXpMiningFiesta, eventTask.get("mining_fiesta").getAsInt(), false));
		lore.add(LevelPage.buildLore("Fishing Festival", sbXpFishingFestival, eventTask.get("fishing_festival").getAsInt(), false));
		lore.add(LevelPage.buildLore("Jacob's Farming Contest", sbXpUniqueMedals, eventTask.get("jacob_farming_contest").getAsInt(), false));
		lore.add(LevelPage.buildLore("Spooky Festival", sbXpSpookyFestival, eventTask.get("spooky_festival").getAsInt(), false));

		int totalXp = sbXpMiningFiesta + sbXpSpookyFestival + sbXpFishingFestival + sbXpUniqueMedals;
		levelPage.renderLevelBar(g, "Event Task", ICON, guiLeft + 299, guiTop + 115, 110, 0, totalXp,
			levelPage.getConstant().getAsJsonObject("category_xp").get("event_task").getAsInt(), mouseX, mouseY, true, lore);
	}

	private static int capOrAmount(int amount, int cap, int per) {
		if (amount <= 0) return 0;
		return Math.min(amount, cap) / per;
	}
}
