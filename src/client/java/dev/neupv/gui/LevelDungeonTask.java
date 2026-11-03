/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates DungeonTaskLevel, Copyright (C) 2022 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.pv.Weights;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class LevelDungeonTask extends LevelTask {
	LevelDungeonTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		JsonObject dungeonTask = levelPage.getConstant().getAsJsonObject("dungeon_task");
		Map<String, Level> skyblockInfo = profile.getLevelingInfo();

		int sbLevelGainedFloor = 0;
		int sbXpGainedClass = 0;
		int sbXpGainedLvl = 0;
		int catacombsLvl = 0;
		if (skyblockInfo != null && skyblockInfo.containsKey("catacombs")) {
			Level catacombs = skyblockInfo.get("catacombs");
			catacombsLvl = (int) catacombs.level;
			for (int i = 1; i <= catacombs.level; i++) sbXpGainedLvl += i < 40 ? 20 : 40;

			int classXp = dungeonTask.get("class_xp").getAsInt();
			for (String dungeonClass : Weights.DUNGEON_CLASS_NAMES) {
				Level level = skyblockInfo.get(dungeonClass);
				if (level == null) continue;
				for (int i = 1; i <= level.level && i <= 50; i++) sbXpGainedClass += classXp;
			}

			JsonObject normal = J.obj(object, "dungeons.dungeon_types.catacombs.tier_completions");
			JsonArray completeCatacombs = dungeonTask.getAsJsonArray("complete_catacombs");
			for (int index = 0; index < completeCatacombs.size(); index++) {
				if (normal != null && normal.has(String.valueOf(index))) sbLevelGainedFloor += completeCatacombs.get(index).getAsInt();
			}
			JsonObject master = J.obj(object, "dungeons.dungeon_types.master_catacombs.tier_completions");
			int masterXp = dungeonTask.get("complete_master_catacombs").getAsInt();
			for (int i = 0; i <= 7; i++) {
				if (master != null && master.has(String.valueOf(i))) sbLevelGainedFloor += masterXp;
			}
		}

		int catacombsLevelUp = dungeonTask.get("catacombs_level_up").getAsInt();
		int classLevelUp = dungeonTask.get("class_level_up").getAsInt();
		int completeDungeon = dungeonTask.get("complete_dungeon").getAsInt();
		int totalGainful = catacombsLevelUp + classLevelUp + completeDungeon;
		double totalXp = sbXpGainedLvl + sbXpGainedClass + sbLevelGainedFloor;

		List<String> lore = new ArrayList<>();
		lore.add(LevelPage.buildLore("Catacombs Level Up", sbXpGainedLvl, catacombsLevelUp, false));
		lore.add(LevelPage.buildLore("Class Level Up", sbXpGainedClass, classLevelUp, false));
		lore.add(LevelPage.buildLore("Complete Dungeons", sbLevelGainedFloor, completeDungeon, false));

		levelPage.renderLevelBar(g, "Dungeon Task", repoStack("WITHER_RELIC"), guiLeft + 23, guiTop + 55, 110, catacombsLvl,
			totalXp, totalGainful, mouseX, mouseY, true, lore);
	}
}
