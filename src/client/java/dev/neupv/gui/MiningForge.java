/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates MiningPage (forge tooltip), ForgeRecipe.getReducedTime, HotmInformation
 * (quick forge multipliers) and Utils.prettyTime, Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tooltip of the forge icon of the HOTM tab: what is in each forge slot and the time remaining. */
public final class MiningForge {
	private static final int[] QUICK_FORGE_MULTIPLIERS = {
		985, 970, 955, 940, 925, 910, 895, 880, 865, 850, 845, 840, 835, 830, 825, 820, 815, 810, 805, 700};

	private MiningForge() {}

	static int quickForgeMultiplier(int level) {
		if (level <= 0) return 1000;
		return level > 20 ? -1 : QUICK_FORGE_MULTIPLIERS[level - 1];
	}

	/** Forge duration in seconds from the repo item's forge recipe, or -1 if there is none. */
	static int forgeDuration(String id) {
		JsonObject item = NeuRepo.item(id);
		if (item == null) return -1;
		for (JsonElement e : J.arrOrEmpty(item, "recipes")) {
			if (!e.isJsonObject()) continue;
			JsonObject r = e.getAsJsonObject();
			if (!"forge".equals(J.str(r, "type", ""))) continue;
			String output = J.str(r, "overrideOutputId", J.str(item, "internalname", id));
			if (!output.equals(id)) continue;
			return J.i(r, "duration", -1);
		}
		return -1;
	}

	/** NEU Utils.prettyTime. */
	static String prettyTime(long millis) {
		long seconds = millis / 1000 % 60;
		long minutes = (millis / 1000 / 60) % 60;
		long hours = (millis / 1000 / 60 / 60) % 24;
		long days = millis / 1000 / 60 / 60 / 24;
		if (millis < 0) return "Ended!";
		if (minutes == 0 && hours == 0 && days == 0) return seconds + "s";
		if (hours == 0 && days == 0) return minutes + "m" + seconds + "s";
		if (days == 0) return hours <= 6 ? hours + "h" + minutes + "m" + seconds + "s" : hours + "h";
		return days + "d" + hours + "h";
	}

	/**
	 * NEU's tooltip. Fixes: shown (with empty slots) even when the API has no forge data instead of no tooltip;
	 * slots above 7 are numbered correctly; Quick Forge is read from "quick_forge" (current key) or "forge_time".
	 */
	static List<Component> tooltip(JsonObject member, int hotmLevel) {
		List<String> tooltip = new ArrayList<>();
		List<String> forgeSlots = new ArrayList<>(List.of("1: §cEmpty", "2: §cEmpty", "3: §7Locked", "4: §7Locked",
			"5: §7Locked", "6: §7Locked", "7: §7Locked"));
		for (int i = 2; i < 7; i++) if (hotmLevel > i) forgeSlots.set(i, (i + 1) + ": §cEmpty");

		JsonObject nodes = dev.neupv.pv.SkillTree.hotmNodes(member);
		int quickForge = 0;
		if (nodes != null) quickForge = J.i(nodes, "quick_forge", J.i(nodes, "forge_time", 0));

		JsonObject forge = J.obj(member, "forge.forge_processes.forge_1");
		long now = System.currentTimeMillis();
		boolean showError = false;
		if (forge != null) {
			for (Map.Entry<String, JsonElement> entry : forge.entrySet()) {
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject value = entry.getValue().getAsJsonObject();
				int slot = J.i(value, "slot", 0);
				if (slot <= 0) continue;
				while (forgeSlots.size() < slot) forgeSlots.add((forgeSlots.size() + 1) + ": §cEmpty");
				String id = J.str(value, "id", "");
				int duration = forgeDuration(id);
				// "just assuming that every forge pet will be legendary"
				if (duration < 0 && "PETS".equals(J.str(value, "type", ""))) {
					id = id + ";4";
					duration = forgeDuration(id);
				}
				if (duration < 0) {
					tooltip.add("§cMissing recipe " + id);
					showError = true;
				}
				JsonObject itemJson = NeuRepo.item(id);
				String displayName = id;
				if (itemJson == null) {
					tooltip.add("§cMissing item " + id);
					showError = true;
				} else {
					displayName = J.str(itemJson, "displayname", id).replace("[Lvl {LVL}] ", "");
				}
				if (duration < 0) {
					forgeSlots.set(slot - 1, slot + ": §4" + displayName + " §7Time Remaining: §cUnknown");
				} else {
					long reduced = (long) quickForgeMultiplier(quickForge) * duration / 1000 * 1000L;
					long startTime = J.lng(value, "startTime", 0) / 1000 * 1000;
					long left = startTime + reduced - now;
					forgeSlots.set(slot - 1, slot + ": §4" + displayName
						+ (left < 0 ? " §aCompleted!" : " §7Time Remaining: §a" + prettyTime(left)));
				}
			}
		}
		if (showError) tooltip.add("§cPlease report this to the NEU Discord");
		tooltip.add("§6Forge");
		tooltip.add("");
		tooltip.addAll(forgeSlots);
		List<Component> out = new ArrayList<>();
		for (String s : tooltip) out.add(Component.literal(s));
		return out;
	}
}
