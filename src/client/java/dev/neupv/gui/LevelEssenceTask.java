/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates EssenceTaskLevel, Copyright (C) 2022-2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class LevelEssenceTask extends LevelTask {
	LevelEssenceTask(LevelPage levelPage) {
		super(levelPage);
	}

	@Override
	void drawTask(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile profile, JsonObject object, int mouseX, int mouseY, int guiLeft, int guiTop) {
		JsonObject categoryXp = levelPage.getConstant().getAsJsonObject("category_xp");
		JsonObject essenceShopTask = levelPage.getConstant().getAsJsonObject("essence_shop_task");
		JsonArray essenceSteps = essenceShopTask.getAsJsonArray("essence_shop_xp");

		// shop (ESSENCE_X) -> XP earned. Ordered like sblevels.json (NEU used a HashMap: random order).
		Map<String, Integer> current = new LinkedHashMap<>();
		for (String key : essenceShopTask.keySet()) {
			if (key.endsWith("_shop")) current.put(key.substring(0, key.length() - "_shop".length()).toUpperCase(Locale.ROOT), 0);
		}
		JsonObject shops = NeuRepo.constant("essenceshops");
		if (shops != null) {
			for (Map.Entry<String, JsonElement> shop : shops.entrySet()) {
				if (!shop.getValue().isJsonObject()) continue;
				int earned = 0;
				for (String perk : shop.getValue().getAsJsonObject().keySet()) {
					int essenceAmount = J.i(object, "player_data.perks." + perk, 0);
					// NEU indexed essence_shop_xp without a bound check (perks above 10 levels crashed)
					earned += sumFirst(essenceSteps, essenceAmount);
				}
				current.merge(shop.getKey(), earned, Integer::sum);
			}
		}

		List<String> lore = new ArrayList<>();
		int total = 0;
		for (Map.Entry<String, Integer> e : current.entrySet()) {
			String key = e.getKey();
			String taskKey = key.toLowerCase(Locale.ROOT) + "_shop";
			if (!essenceShopTask.has(taskKey)) continue;
			int max = essenceShopTask.get(taskKey).getAsInt();
			lore.add(LevelPage.buildLore(displayName(key), e.getValue(), max, false));
			total += e.getValue();
		}
		if (shops == null) lore.add("§cMissing repo data (essenceshops.json)");

		levelPage.renderLevelBar(g, "Essence", repoStack("ESSENCE_WITHER"), guiLeft + 299, guiTop + 25, 110, total, total,
			categoryXp.get("essence_shop_task").getAsInt(), mouseX, mouseY, true, lore);
	}

	private static String displayName(String id) {
		String name = J.str(NeuRepo.item(id), "displayname", null);
		if (name != null) return Fmt.stripColor(name);
		// ESSENCE_WITHER -> Wither Essence
		String type = id.startsWith("ESSENCE_") ? id.substring("ESSENCE_".length()) : id;
		return type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT) + " Essence";
	}
}
