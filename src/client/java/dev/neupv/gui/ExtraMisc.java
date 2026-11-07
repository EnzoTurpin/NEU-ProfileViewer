/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * New in this port (not in NEU): "Misc" box and bank transactions tooltip of the Profile Stats tab.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bottom box of the 3rd Profile Stats column (empty in NEU) and the "Bank" hover tooltip. */
final class ExtraMisc {
	private ExtraMisc() {}

	private record Line(String left, String right, List<String> tooltip) {}

	/** Draws the misc lines in the box whose top-left text position is (x, y); same squeeze as the essence box. */
	static void draw(ProfileViewerScreen screen, GuiGraphicsExtractor g, JsonObject member, float x, float y, int mouseX, int mouseY) {
		List<Line> lines = lines(member);
		float spacing = Math.min(9, 72f / Math.max(1, lines.size()));
		float scale = Math.min(1f, spacing / 9f);
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			float ly = y + spacing * i;
			g.pose().pushMatrix();
			g.pose().translate(x, ly);
			g.pose().scale(scale, scale);
			Draw.aligned(g, line.left(), line.right(), 0, 0, Math.round(76 / scale));
			g.pose().popMatrix();
			if (line.tooltip() != null && mouseX >= x && mouseX <= x + 76 && mouseY >= ly && mouseY < ly + spacing) {
				screen.tooltipToDisplay = PvWidgets.lines(line.tooltip().toArray(new String[0]));
			}
		}
	}

	private static List<Line> lines(JsonObject member) {
		List<Line> lines = new ArrayList<>();
		String ps = "player_stats.";

		double dmg = J.dbl(member, ps + "highest_damage", 0), crit = J.dbl(member, ps + "highest_critical_damage", 0);
		lines.add(new Line("§cHighest Damage", "§f" + Fmt.shortNumber(Math.max(dmg, crit)), List.of(
			"§cHighest Damage",
			"§7Non-critical: §f" + Fmt.number(dmg),
			"§7Critical: §f" + Fmt.number(crit))));

		double sc = J.dbl(member, ps + "sea_creature_kills", 0);
		lines.add(new Line("§3Sea Creature Kills", "§f" + Fmt.shortNumber(sc), List.of(
			"§3Sea Creature Kills: §f" + Fmt.number(sc),
			"§7From the profile stats (the line above",
			"§7counts the pet milestone instead).")));

		double given = J.dbl(member, ps + "gifts.total_given", 0), received = J.dbl(member, ps + "gifts.total_received", 0);
		lines.add(new Line("§bGifts Given", "§f" + Fmt.shortNumber(given), null));
		lines.add(new Line("§bGifts Received", "§f" + Fmt.shortNumber(received), null));

		lines.add(candy(J.objOrEmpty(member, ps + "candy_collected")));
		lines.add(races(J.objOrEmpty(member, ps + "races")));

		JsonObject glacite = J.objOrEmpty(member, "glacite_player_data");
		List<String> dustTip = new ArrayList<>();
		dustTip.add("§dFossil Dust: §f" + Fmt.number(J.dbl(glacite, "fossil_dust", 0)));
		JsonArray donated = J.arrOrEmpty(glacite, "fossils_donated");
		dustTip.add("§7Fossils donated: §f" + donated.size());
		for (JsonElement e : donated) dustTip.add("§8 - §7" + nice(e.getAsString()));
		lines.add(new Line("§dFossil Dust", "§f" + Fmt.shortNumber(J.dbl(glacite, "fossil_dust", 0)), dustTip));

		JsonObject corpses = J.objOrEmpty(glacite, "corpses_looted");
		long totalCorpses = 0;
		List<String> corpseTip = new ArrayList<>();
		corpseTip.add("§bCorpses Looted");
		for (Map.Entry<String, JsonElement> e : corpses.entrySet()) {
			long n = e.getValue().getAsLong();
			totalCorpses += n;
			corpseTip.add(corpseColour(e.getKey()) + nice(e.getKey()) + "§7: §f" + Fmt.number(n));
		}
		lines.add(new Line("§bCorpses Looted", "§f" + Fmt.shortNumber(totalCorpses), corpseTip));
		lines.add(new Line("§bMineshafts", "§f" + Fmt.shortNumber(J.dbl(glacite, "mineshafts_entered", 0)), null));
		return lines;
	}

	private static Line candy(JsonObject candy) {
		long total = J.lng(candy, "total", 0);
		List<String> tip = new ArrayList<>();
		tip.add("§6Candy Collected: §f" + Fmt.number(total));
		tip.add("§aGreen Candy: §f" + Fmt.number(J.lng(candy, "green_candy", 0)));
		tip.add("§5Purple Candy: §f" + Fmt.number(J.lng(candy, "purple_candy", 0)));
		int festivals = 0;
		long best = 0;
		for (Map.Entry<String, JsonElement> e : candy.entrySet()) {
			if (!e.getKey().startsWith("spooky_festival_") || !e.getValue().isJsonObject()) continue;
			festivals++;
			best = Math.max(best, J.lng(e.getValue(), "total", 0));
		}
		if (festivals > 0) {
			tip.add("");
			tip.add("§7Spooky Festivals: §f" + festivals);
			tip.add("§7Best festival: §f" + Fmt.number(best));
		}
		return new Line("§6Candy Collected", "§f" + Fmt.shortNumber(total), tip);
	}

	private static Line races(JsonObject races) {
		List<String> tip = new ArrayList<>();
		tip.add("§eRace Best Times");
		int count = 0;
		for (Map.Entry<String, JsonElement> e : races.entrySet()) {
			if (e.getValue().isJsonPrimitive()) {
				count++;
				String name = nice(e.getKey().replaceAll("_best_time(_\\d+)?$", ""));
				tip.add("§7" + name + ": §f" + raceTime(e.getValue().getAsLong()));
			}
		}
		// Dungeon hub: one best time per route/rule; show the fastest per route
		JsonObject hub = J.objOrEmpty(races, "dungeon_hub");
		if (!hub.isEmpty()) {
			tip.add("§7Dungeon Hub §8(" + hub.size() + " times)§7:");
			Map<String, Long> bestPerRoute = new java.util.TreeMap<>();
			for (Map.Entry<String, JsonElement> e : hub.entrySet()) {
				count++;
				String key = e.getKey();
				String route = key.replaceAll("_(anything|no_pearls|no_abilities|nothing)_(no|with)_return_best_time$", "");
				bestPerRoute.merge(route, e.getValue().getAsLong(), Math::min);
			}
			for (Map.Entry<String, Long> e : bestPerRoute.entrySet()) {
				tip.add("§8 - §7" + nice(e.getKey()) + ": §f" + raceTime(e.getValue()));
			}
		}
		return new Line("§eRace Best Times", "§f" + count, tip);
	}

	private static String raceTime(long ms) {
		long min = ms / 60000;
		double sec = (ms % 60000) / 1000.0;
		return min > 0 ? String.format(Locale.ROOT, "%d:%06.3f", min, sec) : String.format(Locale.ROOT, "%.3fs", sec);
	}

	private static String corpseColour(String type) {
		return switch (type) {
			case "lapis" -> "§9";
			case "umber" -> "§6";
			case "tungsten" -> "§7";
			case "vanguard" -> "§b";
			default -> "§f";
		};
	}

	/** "giant_mushroom" / "CLUBBED" -> "Giant Mushroom" / "Clubbed". */
	static String nice(String id) {
		StringBuilder sb = new StringBuilder();
		for (String w : id.toLowerCase(Locale.ROOT).split("_")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	/** "3d ago", "5h ago", "12m ago". */
	static String ago(long ts) {
		long s = Math.max(0, (System.currentTimeMillis() - ts) / 1000);
		if (s < 60) return s + "s ago";
		if (s < 3600) return s / 60 + "m ago";
		if (s < 86400) return s / 3600 + "h ago";
		if (s < 86400L * 365) return s / 86400 + "d ago";
		return s / (86400L * 365) + "y ago";
	}

	/** Last 10 coop bank transactions (outer profile "banking.transactions"), newest first. */
	static List<Component> bankTooltip(JsonObject outerProfile, double coopBank, double personalBank) {
		List<String> lines = new ArrayList<>();
		lines.add("§6Bank: §f" + Fmt.number(coopBank));
		lines.add("§6Personal Bank: §f" + Fmt.number(personalBank));
		JsonArray transactions = J.arr(outerProfile, "banking.transactions");
		if (transactions == null) {
			lines.add("");
			lines.add("§cBanking API disabled");
			return PvWidgets.lines(lines.toArray(new String[0]));
		}
		List<JsonObject> list = new ArrayList<>();
		for (JsonElement e : transactions) if (e.isJsonObject()) list.add(e.getAsJsonObject());
		list.sort(Comparator.comparingLong((JsonObject t) -> J.lng(t, "timestamp", 0)).reversed());
		lines.add("");
		lines.add(list.isEmpty() ? "§7No recent transactions" : "§eLast transactions:");
		for (int i = 0; i < Math.min(10, list.size()); i++) {
			JsonObject t = list.get(i);
			boolean deposit = !"WITHDRAW".equals(J.str(t, "action", ""));
			String amount = (deposit ? "§a+" : "§c-") + Fmt.shortNumber(J.dbl(t, "amount", 0));
			String who = J.str(t, "initiator_name", "?");
			lines.add(amount + " §7" + who + " §8" + ago(J.lng(t, "timestamp", 0)));
		}
		return PvWidgets.lines(lines.toArray(new String[0]));
	}
}
