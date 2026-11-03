/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates ProfileViewer.Level / ProfileViewerUtils, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.util.J;

public class Level {
	public float level = 0;
	public float maxXpForLevel = 0;
	public boolean maxed = false;
	public int maxLevel;
	public float totalXp;

	public static int cap(JsonObject leveling, String skillName) {
		JsonObject caps = J.obj(leveling, "leveling_caps");
		return caps != null && caps.has(skillName) ? caps.get(skillName).getAsInt() : 50;
	}

	/** Exact port of ProfileViewerUtils.getLevel. */
	public static Level of(JsonArray levelingArray, float xp, int levelCap, boolean cumulative) {
		Level levelObj = new Level();
		levelObj.totalXp = xp;
		levelObj.maxLevel = levelCap;
		if (levelingArray == null) return levelObj;
		for (int level = 0; level < levelingArray.size(); level++) {
			float levelXp = levelingArray.get(level).getAsFloat();
			if (levelXp > xp) {
				if (cumulative) {
					float previous = level > 0 ? levelingArray.get(level - 1).getAsFloat() : 0;
					levelObj.maxXpForLevel = (levelXp - previous);
					levelObj.level = 1 + level + (xp - levelXp) / levelObj.maxXpForLevel;
				} else {
					levelObj.maxXpForLevel = levelXp;
					levelObj.level = level + xp / levelXp;
				}
				if (levelObj.level >= levelCap) {
					levelObj.level = levelCap;
					levelObj.maxed = true;
				}
				return levelObj;
			} else if (!cumulative) {
				xp -= levelXp;
			}
		}
		levelObj.level = Math.min(levelingArray.size(), levelCap);
		levelObj.maxed = true;
		return levelObj;
	}

	static JsonArray array(JsonElement leveling, String path) {
		return J.arr(leveling, path);
	}
}
