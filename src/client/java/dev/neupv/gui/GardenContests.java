/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * New in this port (not in NEU 2.6.0): Jacob's contests / greenhouse data of the Garden tab's second page.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.util.J;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jacob's contests, Anita perks, greenhouse and farming toolkit, read from the profile member json only
 * (no garden endpoint needed). Pure function of the json, so it is testable without network.
 */
public final class GardenContests {

	/** Contest brackets, lowest first (Hypixel's colours). */
	public enum Bracket {
		BRONZE("§c", "Bronze"), SILVER("§f", "Silver"), GOLD("§6", "Gold"), PLATINUM("§3", "Platinum"), DIAMOND("§b", "Diamond");

		public final String color, displayName;

		Bracket(String color, String displayName) {
			this.color = color;
			this.displayName = displayName;
		}

		public String colored() {
			return color + displayName;
		}

		public static Bracket of(String name) {
			if (name == null) return null;
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	public static final class CropStats {
		public long personalBest;
		public int participated, claimed;
		/** Best (lowest) claimed position, 1-based; 0 = never ranked. */
		public int bestPosition, bestParticipants;
		public String bestPositionDate;
		public long bestCollected;
		public final int[] medals = new int[Bracket.values().length];
		/** Brackets reached at least once (jacobs_contest.unique_brackets). */
		public final boolean[] uniqueBrackets = new boolean[Bracket.values().length];

		public Bracket highestBracket() {
			for (int i = uniqueBrackets.length - 1; i >= 0; i--) if (uniqueBrackets[i]) return Bracket.values()[i];
			return null;
		}

		public int totalMedals() {
			int n = 0;
			for (int m : medals) n += m;
			return n;
		}
	}

	/** One tool of the farming toolkit: its crop slot and its ExtraAttributes (the API stores no display data). */
	public record Tool(GardenData.Crop crop, String id, CompoundTag attributes) {}

	public final Map<GardenData.Crop, CropStats> crops = new EnumMap<>(GardenData.Crop.class);
	/** Medals in the medal inventory (spendable at Jacob's shop): gold, silver, bronze. */
	public int goldMedals, silverMedals, bronzeMedals;
	public int doubleDrops, farmingLevelCap;
	public boolean personalBestsUnlocked;
	public int totalContests, totalClaimed;
	public final List<String> discoveredGreenhouse = new ArrayList<>();
	public final List<String> analyzedGreenhouse = new ArrayList<>();
	public boolean toolkitUnlocked;
	public final List<Tool> toolkit = new ArrayList<>();

	public static final int MAX_DOUBLE_DROPS = 15, MAX_LEVEL_CAP = 10;

	private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
		"Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

	/**
	 * Greenhouse mutations known when this was written (NEU repo items whose lore ends with "MUTATION"); the
	 * player's own lists are merged in, so newer ones still show up.
	 */
	public static final List<String> MUTATIONS = List.of(
		"ASHWREATH", "CHOCONUT", "DUSTGRAIN", "GLOOMGOURD", "LONELILY", "SCOURROOT", "SHADEVINE", "VEILSHROOM", "WITHERBLOOM",
		"CHOCOBERRY", "CINDERSHADE", "COALROOT", "CREAMBLOOM", "DUSKBLOOM", "THORNSHADE",
		"BLASTBERRY", "CHEESEBITE", "CHLORONITE", "DO_NOT_EAT_SHROOM", "FLESHTRAP", "MAGIC_JELLYBEAN", "NOCTILUME", "SNOOZLING", "SOGGYBUD", "TURTLELLINI",
		"CHORUS_FRUIT", "PLANTBOY_ADVANCE", "PUFFERCLOUD", "SHELLFRUIT", "STARTLEVINE", "STOPLIGHT_PETAL", "THUNDERLING", "ZOMBUD",
		"ALL_IN_ALOE", "DEVOURER", "GLASSCORN", "GODSEED", "JERRYFLOWER", "PHANTOMLEAF", "TIMESTALK");

	public static GardenContests parse(JsonObject member) {
		GardenContests d = new GardenContests();
		for (GardenData.Crop c : GardenData.Crop.values()) d.crops.put(c, new CropStats());
		JsonObject jacob = J.objOrEmpty(member, "jacobs_contest");

		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(jacob, "personal_bests").entrySet()) {
			GardenData.Crop c = GardenData.Crop.fromApiName(e.getKey());
			if (c != null && e.getValue().isJsonPrimitive()) d.crops.get(c).personalBest = e.getValue().getAsLong();
		}

		// "<year>:<month>_<day>:<CROP API NAME>" (the crop may contain ':' itself, e.g. INK_SACK:3)
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(jacob, "contests").entrySet()) {
			String[] parts = e.getKey().split(":", 3);
			if (parts.length < 3 || !e.getValue().isJsonObject()) continue;
			GardenData.Crop c = GardenData.Crop.fromApiName(parts[2]);
			if (c == null) continue;
			JsonObject contest = e.getValue().getAsJsonObject();
			CropStats s = d.crops.get(c);
			s.participated++;
			d.totalContests++;
			s.bestCollected = Math.max(s.bestCollected, J.lng(contest, "collected", 0));
			if (J.bool(contest, "claimed_rewards", false)) {
				s.claimed++;
				d.totalClaimed++;
			}
			Bracket medal = Bracket.of(J.str(contest, "claimed_medal", null));
			if (medal != null) s.medals[medal.ordinal()]++;
			// claimed_position is 0-based; negative values (-1/-2) are "not ranked"
			int pos = J.i(contest, "claimed_position", -1);
			if (pos >= 0 && (s.bestPosition == 0 || pos + 1 < s.bestPosition)) {
				s.bestPosition = pos + 1;
				s.bestParticipants = J.i(contest, "claimed_participants", 0);
				s.bestPositionDate = date(parts[0], parts[1]);
			}
		}

		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(jacob, "unique_brackets").entrySet()) {
			Bracket b = Bracket.of(e.getKey());
			if (b == null || !e.getValue().isJsonArray()) continue;
			for (JsonElement crop : e.getValue().getAsJsonArray()) {
				if (!crop.isJsonPrimitive()) continue;
				GardenData.Crop c = GardenData.Crop.fromApiName(crop.getAsString());
				if (c != null) d.crops.get(c).uniqueBrackets[b.ordinal()] = true;
			}
		}

		d.goldMedals = J.i(jacob, "medals_inv.gold", 0);
		d.silverMedals = J.i(jacob, "medals_inv.silver", 0);
		d.bronzeMedals = J.i(jacob, "medals_inv.bronze", 0);
		d.doubleDrops = J.i(jacob, "perks.double_drops", 0);
		d.farmingLevelCap = J.i(jacob, "perks.farming_level_cap", 0);
		d.personalBestsUnlocked = J.bool(jacob, "perks.personal_bests", false);

		JsonObject garden = J.objOrEmpty(member, "garden_player_data");
		for (JsonElement e : J.arrOrEmpty(garden, "discovered_greenhouse_crops")) if (e.isJsonPrimitive()) d.discoveredGreenhouse.add(e.getAsString());
		for (JsonElement e : J.arrOrEmpty(garden, "analyzed_greenhouse_crops")) if (e.isJsonPrimitive()) d.analyzedGreenhouse.add(e.getAsString());

		JsonObject toolkit = J.objOrEmpty(garden, "farming_toolkit");
		d.toolkitUnlocked = J.bool(toolkit, "IS_UNLOCKED", false);
		Map<GardenData.Crop, Tool> tools = new EnumMap<>(GardenData.Crop.class);
		for (Map.Entry<String, JsonElement> e : toolkit.entrySet()) {
			if (!e.getValue().isJsonArray()) continue;
			GardenData.Crop c = GardenData.Crop.fromApiName(e.getKey());
			if (c == null) continue;
			for (JsonElement slot : e.getValue().getAsJsonArray()) {
				String data = J.str(slot, "data", null);
				CompoundTag tag = decode(data);
				String id = tag == null ? null : tag.getStringOr("id", null);
				if (id != null) {
					tools.put(c, new Tool(c, id, tag));
					break;
				}
			}
		}
		d.toolkit.addAll(tools.values());
		return d;
	}

	private static CompoundTag decode(String base64) {
		if (base64 == null || base64.isEmpty()) return null;
		try {
			return NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(base64)), NbtAccounter.unlimitedHeap());
		} catch (Exception e) {
			return null;
		}
	}

	/** "241", "12_30" -> "Late Winter 30th, Year 242" (API years are 0-based). */
	static String date(String year, String monthDay) {
		try {
			String[] md = monthDay.split("_");
			int month = Integer.parseInt(md[0]), day = Integer.parseInt(md[1]);
			String m = month >= 1 && month <= 12 ? MONTHS[month - 1] : "Month " + month;
			return m + " " + day + ordinal(day) + ", Year " + (Integer.parseInt(year) + 1);
		} catch (RuntimeException e) {
			return null;
		}
	}

	static String ordinal(int n) {
		if (n % 100 >= 11 && n % 100 <= 13) return "th";
		return switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}

	/** "do_not_eat_shroom" -> "Do Not Eat Shroom" (fallback when the repo has no item). */
	static String prettyId(String id) {
		StringBuilder sb = new StringBuilder();
		for (String w : id.toLowerCase(Locale.ROOT).split("_")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}
}
