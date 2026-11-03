/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates StoryTaskLevel, Copyright (C) 2022-2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
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

class LevelStoryTask extends LevelTask {
	private static final ItemStack ICON = new ItemStack(Items.FILLED_MAP);

	LevelStoryTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		JsonObject storyTask = levelPage.getConstant().getAsJsonObject("story_task");
		JsonObject objectives = J.obj(object, "objectives");

		int sbXpStory = completed(objectives, storyTask.getAsJsonArray("complete_objectives_names")) * storyTask.get("complete_objectives_xp").getAsInt();
		int sbXpRiftGuide = completed(objectives, storyTask.getAsJsonArray("rift_guide_names")) * storyTask.get("rift_guide_xp").getAsInt();

		List<String> lore = new ArrayList<>();
		lore.add(LevelPage.buildLore("Complete Objectives", sbXpStory, storyTask.get("complete_objectives").getAsInt(), false));
		lore.add(LevelPage.buildLore("Rift Guide", sbXpRiftGuide, storyTask.get("rift_guide").getAsInt(), false));

		int totalXp = sbXpStory + sbXpRiftGuide;
		levelPage.renderLevelBar(g, "Story Task", ICON, guiLeft + 299, guiTop + 85, 110, 0, totalXp,
			levelPage.getConstant().getAsJsonObject("category_xp").get("story_task").getAsInt(), mouseX, mouseY, true, lore);
	}

	private static int completed(JsonObject objectives, JsonArray names) {
		if (objectives == null || names == null) return 0;
		int count = 0;
		for (JsonElement name : names) {
			if ("COMPLETE".equals(J.str(objectives, name.getAsString() + ".status", null))) count++;
		}
		return count;
	}
}
