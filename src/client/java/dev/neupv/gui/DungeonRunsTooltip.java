/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * New in this port (not in NEU): tooltip on the "Total Runs" lines of the Dungeoneering tab.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.util.J;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Daily runs, recent treasure runs/chests ({@code dungeons.treasures}) and the last run. */
final class DungeonRunsTooltip {
	private DungeonRunsTooltip() {}

	private static final String[] NUMBERS = {"ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN"};

	static List<Component> build(JsonObject member) {
		List<String> lines = new ArrayList<>();
		JsonObject dungeons = J.objOrEmpty(member, "dungeons");

		// daily_runs: Hypixel only resets the counter on the next run, so a stale day stamp means 0 runs today
		int dailyCount = J.i(dungeons, "daily_runs.completed_runs_count", 0);
		long dayStamp = J.lng(dungeons, "daily_runs.current_day_stamp", -1);
		long today = LocalDate.now(ZoneId.of("America/New_York")).toEpochDay();
		if (dayStamp < 0) {
			lines.add("§eRuns Today: §f0");
		} else if (dayStamp == today) {
			lines.add("§eRuns Today: §f" + dailyCount);
		} else {
			String date = LocalDate.ofEpochDay(dayStamp).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
			lines.add("§eRuns Today: §f0 §8(" + dailyCount + " on " + date + ")");
		}

		String last = J.str(dungeons, "last_dungeon_run", null);
		if (last != null) lines.add("§eLast Run: §f" + floorName(last));

		// treasures: the last few runs that still have reward chests
		JsonObject treasures = J.objOrEmpty(dungeons, "treasures");
		Map<String, String> runName = new LinkedHashMap<>();
		Map<String, Integer> runsPerType = new TreeMap<>();
		long lastTs = 0;
		for (JsonElement e : J.arrOrEmpty(treasures, "runs")) {
			if (!e.isJsonObject()) continue;
			String name = runName(e.getAsJsonObject());
			runName.put(J.str(e, "run_id", ""), name);
			runsPerType.merge(name, 1, Integer::sum);
			lastTs = Math.max(lastTs, J.lng(e, "completion_ts", 0));
		}
		int chests = 0, opened = 0;
		Map<String, int[]> perChest = new TreeMap<>();
		for (JsonElement e : J.arrOrEmpty(treasures, "chests")) {
			if (!e.isJsonObject()) continue;
			chests++;
			boolean isOpened = J.bool(e, "is_opened", false) || J.bool(e, "paid", false);
			if (isOpened) opened++;
			String type = J.str(e, "treasure_type", J.str(e, "chest_type", "?"));
			int[] c = perChest.computeIfAbsent(ExtraMisc.nice(type), k -> new int[2]);
			c[0]++;
			if (isOpened) c[1]++;
		}
		lines.add("");
		int runs = runName.size();
		lines.add("§6Treasure Runs: §f" + runs + (lastTs > 0 ? " §8(last " + ExtraMisc.ago(lastTs) + ")" : ""));
		for (Map.Entry<String, Integer> e : runsPerType.entrySet()) {
			lines.add("§8 - §7" + e.getKey() + ": §f" + e.getValue());
		}
		lines.add("§6Chests: §f" + opened + "§7/§f" + chests + " §7opened");
		for (Map.Entry<String, int[]> e : perChest.entrySet()) {
			lines.add("§8 - §7" + e.getKey() + ": §f" + e.getValue()[1] + "§7/§f" + e.getValue()[0]);
		}
		return PvWidgets.lines(lines.toArray(new String[0]));
	}

	/** "MASTER_CATACOMBS_FLOOR_FIVE" -> "Master Mode Floor 5" (M5). */
	private static String floorName(String id) {
		String s = id.toUpperCase(Locale.ROOT);
		int idx = s.lastIndexOf("_FLOOR_");
		if (idx < 0) return ExtraMisc.nice(id);
		String num = s.substring(idx + 7);
		int n = -1;
		for (int i = 0; i < NUMBERS.length; i++) if (NUMBERS[i].equals(num)) n = i;
		if (n < 0) {
			try {
				n = Integer.parseInt(num);
			} catch (NumberFormatException ex) {
				return ExtraMisc.nice(id);
			}
		}
		boolean master = s.startsWith("MASTER");
		if (n == 0) return "Entrance";
		return (master ? "Master Mode Floor " : "Floor ") + n + " §8(" + (master ? "M" : "F") + n + ")";
	}

	/** Dungeon runs have dungeon_type/dungeon_tier, Kuudra runs type/tier_id. */
	private static String runName(JsonObject run) {
		String type = J.str(run, "dungeon_type", J.str(run, "type", "?")).toLowerCase(Locale.ROOT);
		JsonElement tierEl = run.has("dungeon_tier") ? run.get("dungeon_tier") : run.get("tier_id");
		String tier = tierEl == null || !tierEl.isJsonPrimitive() ? "" : tierEl.getAsString();
		if (type.contains("catacombs")) {
			boolean master = type.contains("master");
			if (tier.equals("0")) return "Entrance";
			return (master ? "M" : "F") + tier;
		}
		return (ExtraMisc.nice(type) + " " + ExtraMisc.nice(tier)).trim();
	}
}
