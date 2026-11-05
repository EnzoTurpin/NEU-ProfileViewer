/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates QuiverInfo.java and PlayerStats.getQuiverInfo,
 * Copyright (C) 2022-2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.item.PvItem;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arrows in the quiver + the favourite arrow, shown when hovering the arrow icon of the Storage tab. */
public final class InventoriesQuiverInfo {
	public String selectedArrow;
	public final Map<String, Integer> arrows = new LinkedHashMap<>();

	/** NEU PlayerStats.getQuiverInfo. */
	public static InventoriesQuiverInfo of(Map<String, List<PvItem>> inventoryInfo, JsonObject profileInfo) {
		if (inventoryInfo == null || !inventoryInfo.containsKey("quiver")) return null;
		InventoriesQuiverInfo info = new InventoriesQuiverInfo();
		for (PvItem item : inventoryInfo.get("quiver")) {
			if (item == null || item.skyblockId == null) continue;
			info.arrows.merge(item.skyblockId, item.count, Integer::sum);
		}
		info.selectedArrow = J.str(profileInfo, "item_data.favorite_arrow", null);
		return info;
	}

	public List<Component> generateProfileViewerTooltip() {
		List<Component> list = new ArrayList<>();
		int totalCount = 0;
		list.add(Component.literal("§bQuiver:"));
		for (Map.Entry<String, Integer> arrow : arrows.entrySet()) {
			JsonObject repoInfo = NeuRepo.item(arrow.getKey());
			if (repoInfo == null) continue;
			list.add(Component.literal("  " + J.str(repoInfo, "displayname", arrow.getKey()) + "§r: §a§l" + arrow.getValue()));
			totalCount += arrow.getValue();
		}
		list.add(Component.empty());
		list.add(Component.literal("§bTotal: §a§l" + totalCount));
		if (selectedArrow != null) {
			JsonObject repoInfo = NeuRepo.item(selectedArrow);
			if (repoInfo == null) list.add(Component.literal("§bSelected Arrow: §cERROR"));
			else list.add(Component.literal("§bSelected Arrow: " + J.str(repoInfo, "displayname", selectedArrow)));
		}
		return list;
	}
}
