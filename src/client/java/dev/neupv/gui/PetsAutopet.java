/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * New in this port (not in NEU): Autopet rules summary on the Pets tab.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** {@code pets_data.autopet.rules}: one tooltip line (wrapped) per pet, listing its triggers. */
final class PetsAutopet {
	private PetsAutopet() {}

	private static final int WRAP = 210;

	static JsonArray rules(JsonObject member) {
		return J.arrOrEmpty(member, "pets_data.autopet.rules");
	}

	static List<Component> tooltip(JsonObject member) {
		JsonArray rules = rules(member);
		int enabled = 0;
		// pet name -> trigger descriptions (insertion order = rule order); catacombs floors are merged per pet
		Map<String, List<String>> perPet = new LinkedHashMap<>();
		Map<String, TreeSet<Integer>[]> floors = new LinkedHashMap<>();
		for (JsonElement e : rules) {
			if (!e.isJsonObject()) continue;
			JsonObject rule = e.getAsJsonObject();
			boolean disabled = J.bool(rule, "disabled", false);
			if (!disabled) enabled++;
			String pet = J.str(rule, "name", "?").replaceFirst("^(§.)*\\[Lvl \\d+] ", "");
			List<String> list = perPet.computeIfAbsent(pet, k -> new ArrayList<>());
			String id = J.str(rule, "id", "?");
			JsonObject data = J.objOrEmpty(rule, "data");
			if (id.equals("START_CATACOMBS_FLOOR") && !disabled && J.arrOrEmpty(rule, "exceptions").isEmpty()) {
				@SuppressWarnings("unchecked")
				TreeSet<Integer>[] f = floors.computeIfAbsent(pet, k -> new TreeSet[]{new TreeSet<>(), new TreeSet<>()});
				boolean master = "true".equals(J.str(data, "master", "false"));
				int floor;
				try {
					floor = Integer.parseInt(J.str(data, "floor", "-1"));
				} catch (NumberFormatException ex) {
					floor = -1;
				}
				if (floor >= 0) {
					if (f[0].isEmpty() && f[1].isEmpty()) list.add("\u0000floors"); // placeholder keeps the rule order
					f[master ? 1 : 0].add(floor);
					continue;
				}
			}
			// rules only carry the display name: "Black Cat ✦" -> BLACK_CAT
			String petId = Fmt.stripColor(pet).replaceAll("[^A-Za-z ]", "").trim().replace(' ', '_');
			String text = trigger(id, data) + exceptions(J.arrOrEmpty(rule, "exceptions"), petId);
			list.add(disabled ? "§8" + Fmt.stripColor(text) + " off" : "§f" + text);
		}

		List<String> lines = new ArrayList<>();
		lines.add("§aAutopet Rules: §f" + rules.size() + " §7(" + enabled + " enabled, limit "
			+ J.i(member, "pets_data.autopet.rules_limit", rules.size()) + ")");
		lines.add("");
		for (Map.Entry<String, List<String>> entry : perPet.entrySet()) {
			List<String> parts = new ArrayList<>();
			for (String p : entry.getValue()) {
				if (p.equals("\u0000floors")) {
					TreeSet<Integer>[] f = floors.get(entry.getKey());
					StringBuilder sb = new StringBuilder("§fStart ");
					if (!f[0].isEmpty()) sb.append(ranges("F", f[0]));
					if (!f[1].isEmpty()) sb.append(f[0].isEmpty() ? "" : ", ").append(ranges("M", f[1]));
					parts.add(sb.toString());
				} else {
					parts.add(p);
				}
			}
			wrap(lines, entry.getKey() + "§7: ", parts);
		}
		if (rules.isEmpty()) lines.add("§7No rules");
		return PvWidgets.lines(lines.toArray(new String[0]));
	}

	/** "F", {0..7} -> "F0-7"; gaps -> "F1, F3-4". */
	private static String ranges(String prefix, TreeSet<Integer> set) {
		List<String> out = new ArrayList<>();
		Integer start = null, prev = null;
		for (int n : set) {
			if (start == null) {
				start = prev = n;
			} else if (n == prev + 1) {
				prev = n;
			} else {
				out.add(prefix + start + (prev.equals(start) ? "" : "-" + prev));
				start = prev = n;
			}
		}
		if (start != null) out.add(prefix + start + (prev.equals(start) ? "" : "-" + prev));
		return String.join(", ", out);
	}

	private static String trigger(String id, JsonObject data) {
		return switch (id) {
			case "ENTER_ISLAND" -> "Enter " + island(J.str(data, "island", "?"));
			case "FISH" -> "Fish";
			case "BOSS_SPAWN" -> {
				String category = J.str(data, "category", "");
				String boss = J.str(data, "boss", "");
				yield category.equals("catacombs") ? "Boss F" + boss : "Boss " + ExtraMisc.nice(category) + " " + ExtraMisc.nice(boss);
			}
			case "START_EXPERIMENT" -> "Experiment";
			case "DIG_BURROW" -> "Dig Burrow";
			case "KILL_ENTITY" -> "Kill " + ExtraMisc.nice(J.str(data, "entity_type", "mob"));
			case "START_CATACOMBS_FLOOR" -> "Start " + ("true".equals(J.str(data, "master", "false")) ? "M" : "F") + J.str(data, "floor", "?");
			default -> {
				StringBuilder sb = new StringBuilder(ExtraMisc.nice(id));
				for (Map.Entry<String, JsonElement> d : data.entrySet()) {
					if (d.getValue().isJsonPrimitive()) sb.append(' ').append(ExtraMisc.nice(d.getValue().getAsString()));
				}
				yield sb.toString();
			}
		};
	}

	/** Exceptions shortened to a count; "unless this same pet is already equipped" is implied and not counted. */
	private static String exceptions(JsonArray exceptions, String petId) {
		int n = 0;
		for (JsonElement e : exceptions) {
			if (!e.isJsonObject()) continue;
			if (J.str(e, "id", "").equals("HAS_EQUIPPED_PET") && J.str(e, "data.pet", "").equalsIgnoreCase(petId)) continue;
			n++;
		}
		return n == 0 ? "" : " §8(+" + n + (n == 1 ? " exception)" : " exceptions)");
	}

	private static String island(String mode) {
		return switch (mode.toLowerCase(Locale.ROOT)) {
			case "dungeon" -> "Dungeons";
			case "dynamic" -> "Private Island";
			case "hub" -> "Hub";
			case "dungeon_hub" -> "Dungeon Hub";
			case "farming_1" -> "The Barn";
			case "garden" -> "Garden";
			case "mining_1" -> "Gold Mine";
			case "mining_2" -> "Deep Caverns";
			case "mining_3" -> "Dwarven Mines";
			case "crystal_hollows" -> "Crystal Hollows";
			case "mineshaft" -> "Glacite Mineshafts";
			case "combat_1" -> "Spider's Den";
			case "combat_3" -> "The End";
			case "crimson_isle" -> "Crimson Isle";
			case "kuudra" -> "Kuudra";
			case "foraging_1" -> "The Park";
			case "foraging_2" -> "Galatea";
			case "winter" -> "Jerry's Workshop";
			case "rift" -> "The Rift";
			case "fishing_1" -> "Backwater Bayou";
			default -> ExtraMisc.nice(mode);
		};
	}

	/** Joins the parts with ", " into lines of at most WRAP px; continuation lines are indented. */
	private static void wrap(List<String> lines, String head, List<String> parts) {
		var font = Draw.font();
		StringBuilder line = new StringBuilder(head);
		boolean first = true;
		for (String p : parts) {
			String piece = (first ? "" : "§7, ") + p;
			if (!first && font.width(line + piece) > WRAP) {
				lines.add(line + "§7,");
				line = new StringBuilder("   " + p);
			} else {
				line.append(piece);
			}
			first = false;
		}
		lines.add(line.toString());
	}
}
