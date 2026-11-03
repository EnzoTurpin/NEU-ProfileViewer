/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates GuiTaskLevel, Copyright (C) 2022 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** One category bar of the SkyBlock Level page (NEU GuiTaskLevel). */
abstract class LevelTask {
	protected final LevelPage levelPage;

	LevelTask(LevelPage levelPage) {
		this.levelPage = levelPage;
	}

	abstract void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object,
		int mouseX, int mouseY, int guiLeft, int guiTop);

	void resetCache() {}

	// ---- small helpers shared by the tasks ----

	static ItemStack repoStack(String id) {
		PvItem item = LegacyItems.fromRepo(id);
		return item == null ? ItemStack.EMPTY : item.stack();
	}

	static int intOf(JsonObject o, String path) {
		return J.i(o, path, 0);
	}

	/** leveling.completed_tasks as strings. */
	static List<String> completedTasks(JsonObject object) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : J.arrOrEmpty(object, "leveling.completed_tasks")) {
			if (e.isJsonPrimitive()) out.add(e.getAsString());
		}
		return out;
	}

	/** Sum of the first {@code count} entries of an XP array (clamped to its size). */
	static int sumFirst(JsonArray arr, int count) {
		int sum = 0;
		if (arr == null) return 0;
		for (int i = 0; i < Math.min(count, arr.size()); i++) sum += arr.get(i).getAsInt();
		return sum;
	}
}
