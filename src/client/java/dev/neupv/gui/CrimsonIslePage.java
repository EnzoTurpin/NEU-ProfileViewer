/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates CrimsonIslePage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CrimsonIslePage extends PvPage {
	private static final Identifier CRIMSON_ISLE = Draw.tex("gui/pv_crimson_isle_page.png");
	private static final String[] DOJO_GRADES = {"F", "D", "C", "B", "A", "S"};
	private static final String[] KUUDRA_TIERS_NAME = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
	/** Names of the tiers in the API. */
	public static final String[] KUUDRA_TIERS = {"none", "hot", "burning", "fiery", "infernal"};
	private static final String[][] KUUDRA_KEY_SKULLS = {
		{"§9Kuudra Key", "ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1MjgzNTU0NCwKICAicHJvZmlsZUlkIiA6ICJkYmQ4MDQ2M2EwMzY0Y2FjYjI3OGNhODBhMDBkZGIxMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ4bG9nMjEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmZkM2U3MTgzOGMwZTc2Zjg5MDIxMzEyMGI0Y2U3NDQ5NTc3NzM2NjA0MzM4YThkMjhiNGM4NmRiMjU0N2U3MSIKICAgIH0KICB9Cn0"},
		{"§5Hot Kuudra Key", "ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1Mjg2NTc1MiwKICAicHJvZmlsZUlkIiA6ICJkMGI4MjE1OThmMTE0NzI1ODBmNmNiZTliOGUxYmU3MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJqYmFydHl5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2MwMjU5ZTg5NjRjM2RlYjk1YjEyMzNiYjJkYzgyYzk4NjE3N2U2M2FlMzZjMTEyNjVjYjM4NTE4MGJiOTFjYzAiCiAgICB9CiAgfQp9"},
		{"§5Burning Kuudra Key", "ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1Mjg4MjI5NSwKICAicHJvZmlsZUlkIiA6ICI1YjY2YzNkZWZhYTI0NWMzYTcwNjM3OTA3NTQ0Yjg3MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWFuX1JhaWNvMDgxNiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zMzBmNmY2ZTYzYjI0NWY4MzllM2NjZGNlNWE1ZjIyMDU2MjAxZDAyNzQ0MTFkZmU1ZDk0YmJlNDQ5YzRlY2UiCiAgICB9CiAgfQp9"},
		{"§5Fiery Kuudra Key", "ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1Mjg5ODM0MSwKICAicHJvZmlsZUlkIiA6ICI5ZDQyNWFiOGFmZjg0MGU1OWM3NzUzZjc5Mjg5YjMyZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJUb21wa2luNDIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ4NTQzOTNiYmY5NDQ0NTQyNTAyNTgyZDRiNWEyM2NjNzM4OTY1MDZlMmZjNzM5ZDU0NWJjMzViYzdiMWMwNiIKICAgIH0KICB9Cn0"},
		{"§6Infernal Kuudra Key", "ewogICJ0aW1lc3RhbXAiIDogMTY0MzY1MjkxMzA5NiwKICAicHJvZmlsZUlkIiA6ICJjNTlkMDFlMDI4MWI0MGNhOTczNjc5ODc4NmRmN2FmNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJvWm9va3hQYXJjY2VyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzgyZWUyNTQxNGFhN2VmYjRhMmI0OTAxYzZlMzNlNWVhYTcwNWE2YWIyMTJlYmViZmQ2YTRkZTk4NDEyNWM3YTAiCiAgICB9CiAgfQp9"},
	};

	public static final LinkedHashMap<String, String> API_DOJO_TEST_NAMES = new LinkedHashMap<>();
	public static final LinkedHashMap<Integer, String> DOJO_POINTS_TO_RANK = new LinkedHashMap<>();
	private static final Map<String, String> FACTIONS = Map.of(
		"mages", "§5Mages",
		"barbarians", "§cBarbarians",
		"N/A", "§7N/A");
	private static final LinkedHashMap<Integer, String> FACTION_THRESHOLDS = new LinkedHashMap<>();

	static {
		API_DOJO_TEST_NAMES.put("mob_kb", "§6Test of Force");
		API_DOJO_TEST_NAMES.put("wall_jump", "§dTest of Stamina");
		API_DOJO_TEST_NAMES.put("archer", "§eTest of Mastery");
		API_DOJO_TEST_NAMES.put("sword_swap", "§cTest of Discipline");
		API_DOJO_TEST_NAMES.put("snake", "§aTest of Swiftness");
		API_DOJO_TEST_NAMES.put("lock_head", "§9Test of Control");
		API_DOJO_TEST_NAMES.put("fireball", "§6Test of Tenacity");

		DOJO_POINTS_TO_RANK.put(0, "§7None");
		DOJO_POINTS_TO_RANK.put(1000, "§eYellow");
		DOJO_POINTS_TO_RANK.put(2000, "§aGreen");
		DOJO_POINTS_TO_RANK.put(4000, "§9Blue");
		DOJO_POINTS_TO_RANK.put(6000, "§6Brown");
		DOJO_POINTS_TO_RANK.put(7000, "§8Black");

		FACTION_THRESHOLDS.put(-3000, "Hostile");
		FACTION_THRESHOLDS.put(-1000, "Unfriendly");
		FACTION_THRESHOLDS.put(0, "Neutral");
		FACTION_THRESHOLDS.put(1000, "Friendly");
		FACTION_THRESHOLDS.put(3000, "Trusted");
		FACTION_THRESHOLDS.put(6000, "Honored");
		FACTION_THRESHOLDS.put(12000, "Hero");
	}

	private static ItemStack[] kuudraKeys;

	public CrimsonIslePage(ProfileViewerScreen screen) {
		super(screen);
	}

	private static ItemStack[] kuudraKeys() {
		if (kuudraKeys == null) {
			kuudraKeys = new ItemStack[KUUDRA_KEY_SKULLS.length];
			for (int i = 0; i < KUUDRA_KEY_SKULLS.length; i++) {
				kuudraKeys[i] = LegacyItems.skull(KUUDRA_KEY_SKULLS[i][0], KUUDRA_KEY_SKULLS[i][1]);
			}
		}
		return kuudraKeys;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;

		JsonObject data = J.obj(selected.getProfileJson(), "nether_island_player_data");
		if (data == null) {
			Draw.centered(g, "§cNo data found for the Crimson Isles", guiLeft + 431 / 2f, guiTop + 101, true);
			return;
		}

		Draw.full(g, CRIMSON_ISLE, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		drawDojoStats(g, data, guiLeft, guiTop, mouseX, mouseY);
		drawKuudraStats(g, data, guiLeft, guiTop, mouseX, mouseY);
		drawLastMatriarchAttempt(g, data, guiLeft, guiTop);
		drawFactionReputation(g, data, guiLeft, guiTop);
	}

	private void drawKuudraStats(GuiGraphicsExtractor g, JsonObject data, int guiLeft, int guiTop, int mouseX, int mouseY) {
		Draw.centered(g, "§cKuudra Stats", guiLeft + 431 * 0.18f, guiTop + 14, true);

		JsonObject tiers = J.obj(data, "kuudra_completed_tiers");
		if (tiers == null) {
			Draw.aligned(g, "§cNo kuudra stats found!", " ", guiLeft + 15, guiTop + 101, 130);
			return;
		}

		ItemStack[] keys = kuudraKeys();
		for (int i = 0; i < 5; i++) {
			int completions = J.i(tiers, KUUDRA_TIERS[i], 0);
			// Only set for tiers completed since Infernal tier was released
			int highestWave = J.i(tiers, "highest_wave_" + KUUDRA_TIERS[i], 0);

			g.item(keys[i], guiLeft + 8, guiTop + 30 + i * 30);
			Draw.aligned(g, "§c" + KUUDRA_TIERS_NAME[i] + ": ", "§f" + completions, guiLeft + 23, guiTop + 30 + i * 30, 110);
			Draw.aligned(g, "§cHighest Wave: ", "§f" + (highestWave != 0 ? String.valueOf(highestWave) : "N/A"),
				guiLeft + 23, guiTop + 42 + i * 30, 110);

			if (highestWave == 0 && mouseX > guiLeft + 23 && mouseX < guiLeft + 133
				&& mouseY < guiTop + 50 + i * 30 && mouseY > guiTop + 42 + i * 30) {
				screen.tooltipToDisplay = PvWidgets.lines(
					"§cN/A will only show for highest wave",
					"§cif you have not completed a run for",
					"§cthis tier since Infernal tier was released.");
			}
		}

		Draw.aligned(g, "§cTotal runs: ", "§f" + totalKuudraRuns(tiers), guiLeft + 23, guiTop + 30 + 5 * 30, 110);
	}

	static int totalKuudraRuns(JsonObject completedRuns) {
		int total = 0;
		for (Map.Entry<String, JsonElement> e : completedRuns.entrySet()) {
			if (e.getKey().startsWith("highest_wave")) continue;
			if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) total += e.getValue().getAsInt();
		}
		return total;
	}

	private void drawDojoStats(GuiGraphicsExtractor g, JsonObject data, int guiLeft, int guiTop, int mouseX, int mouseY) {
		float centre = guiLeft + 431 * 0.49f;
		Draw.centered(g, "§eDojo Stats", centre, guiTop + 14, true);

		int totalPoints = 0;
		int idx = 0;
		for (Map.Entry<String, String> test : API_DOJO_TEST_NAMES.entrySet()) {
			int points = J.i(data, "dojo.dojo_points_" + test.getKey(), 0);
			totalPoints += points;
			int y = guiTop + 30 + idx * 12;
			Draw.aligned(g, test.getValue() + ": ", "§f" + points + " (" + DOJO_GRADES[Math.max(0, Math.min(5, points / 200))] + ")",
				centre - 65, y, 130);
			// Not in NEU: best time of each test on hover (the API stores it in ms as dojo_time_<test>)
			if (mouseX > centre - 65 && mouseX < centre + 65 && mouseY >= y && mouseY < y + 10) {
				long time = J.lng(data, "dojo.dojo_time_" + test.getKey(), -1);
				screen.tooltipToDisplay = PvWidgets.lines(test.getValue(),
					"§7Points: §f" + points + " §7(" + DOJO_GRADES[Math.max(0, Math.min(5, points / 200))] + ")",
					"§7Best time: §f" + (time > 0 ? String.format(Locale.US, "%.2fs", time / 1000.0) : "N/A"));
			}
			idx++;
		}

		int n = API_DOJO_TEST_NAMES.size();
		Draw.aligned(g, "§7Points: ", "§6" + totalPoints, centre - 65, guiTop + 40 + n * 12, 130);
		Draw.aligned(g, "§7Rank: ", rank(totalPoints), centre - 65, guiTop + 52 + n * 12, 130);
		int toNext = pointsToNextRank(totalPoints);
		Draw.aligned(g, "§7Points to next: ", toNext == 0 ? "§6MAXED!" : String.valueOf(toNext), centre - 65, guiTop + 64 + n * 12, 130);
	}

	public static String rank(int points) {
		int lastRank = 0;
		for (Map.Entry<Integer, String> r : DOJO_POINTS_TO_RANK.entrySet()) {
			if (points < r.getKey()) return DOJO_POINTS_TO_RANK.get(lastRank);
			lastRank = r.getKey();
		}
		return DOJO_POINTS_TO_RANK.get(lastRank);
	}

	static int pointsToNextRank(int total) {
		for (int threshold : DOJO_POINTS_TO_RANK.keySet()) {
			if (threshold > total) return threshold - total;
		}
		return 0;
	}

	private void drawLastMatriarchAttempt(GuiGraphicsExtractor g, JsonObject data, int guiLeft, int guiTop) {
		Draw.centered(g, "§6Last Matriarch Attempt", guiLeft + 431 * 0.82f, guiTop + 104, true);

		JsonObject matriarch = J.obj(data, "matriarch");
		if (matriarch != null && !matriarch.entrySet().isEmpty()) {
			Draw.aligned(g, "§6Heavy Pearls Acquired: ", "§f" + J.i(matriarch, "pearls_collected", 0), guiLeft + 290, guiTop + 119, 130);
			Date date = new Date(J.lng(matriarch, "last_attempt", 0));
			Draw.aligned(g, "§6Last Attempt: ", "§f" + new SimpleDateFormat("EEE d MMM yyyy", Locale.ENGLISH).format(date),
				guiLeft + 290, guiTop + 131, 130);
			Draw.aligned(g, "§6 ", "§f" + new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(date), guiLeft + 290, guiTop + 143, 130);
			return;
		}
		Draw.aligned(g, "§cNo attempts found!", " ", guiLeft + 290, guiTop + 119, 130);
	}

	private void drawFactionReputation(GuiGraphicsExtractor g, JsonObject data, int guiLeft, int guiTop) {
		Draw.centered(g, "§5Faction Reputation", guiLeft + 431 * 0.82f, guiTop + 14, true);

		String faction = J.str(data, "selected_faction", "N/A");
		if (!FACTIONS.containsKey(faction)) faction = "N/A"; // NEU printed "null" for an unknown/empty faction
		Draw.aligned(g, "§aFaction: ", FACTIONS.get(faction), guiLeft + 290, guiTop + 30, 130);

		int mageRep = J.i(data, "mages_reputation", 0);
		int barbRep = J.i(data, "barbarians_reputation", 0);

		int mageOffset = faction.equals("mages") || faction.equals("N/A") ? 0 : 24;
		Draw.aligned(g, "§5Mage Reputation: ", "§f" + mageRep, guiLeft + 290, guiTop + 42 + mageOffset, 130);
		Draw.aligned(g, "§5Title: ", "§f" + title(mageRep), guiLeft + 290, guiTop + 54 + mageOffset, 130);

		int barbOffset = faction.equals("barbarians") ? 0 : 24;
		Draw.aligned(g, "§cBarbarian Reputation: ", "§f" + barbRep, guiLeft + 290, guiTop + 42 + barbOffset, 130);
		Draw.aligned(g, "§cTitle: ", "§f" + title(barbRep), guiLeft + 290, guiTop + 54 + barbOffset, 130);
	}

	/** Reputation title; below -3000 NEU showed "Neutral" (its default threshold 0), it is "Hostile" in game. */
	static String title(int reputation) {
		String title = FACTION_THRESHOLDS.get(-3000);
		for (Map.Entry<Integer, String> t : FACTION_THRESHOLDS.entrySet()) {
			if (reputation >= t.getKey()) title = t.getValue();
		}
		return title;
	}
}
