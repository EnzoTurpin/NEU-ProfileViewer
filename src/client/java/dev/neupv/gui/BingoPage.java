/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates BingoPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.net.UrsaClient;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BingoPage extends PvPage {
	private static final Identifier BINGO_GUI_TEXTURE = Draw.tex("gui/pv_bingo_tab.png");
	public static final String BINGO_GOALS_URL = "https://api.hypixel.net/v2/resources/skyblock/bingo";

	public BingoPage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		SkyblockProfiles profile = screen.getProfile();
		SkyblockProfiles.Fetch goalsFetch = profile.fetchUrl(BINGO_GOALS_URL);
		SkyblockProfiles.Fetch bingoFetch = profile.fetchUrsa(UrsaClient.bingo(profile.getUuid()));

		// NEU showed "No Bingo data" while loading; tell loading and errors apart.
		if (bingoFetch.state() == SkyblockProfiles.FetchState.LOADING || goalsFetch.state() == SkyblockProfiles.FetchState.LOADING) {
			Draw.centered(g, "§eLoading...", guiLeft + 431 / 2f, guiTop + 101, true);
			return;
		}
		JsonObject goalsJson = goalsFetch.json();
		if (goalsFetch.state() == SkyblockProfiles.FetchState.ERROR || !J.bool(goalsJson, "success", false) || J.arr(goalsJson, "goals") == null) {
			Draw.centered(g, "§cCould not load the current Bingo goals!", guiLeft + 431 / 2f, guiTop + 101, true);
			return;
		}
		JsonObject bingoInfo = bingoFetch.state() == SkyblockProfiles.FetchState.OK ? bingoFetch.json() : null;
		JsonArray events = J.arr(bingoInfo, "events");
		// Check if the player has created a bingo profile for the current event
		if (events == null || events.isEmpty() || !events.get(events.size() - 1).isJsonObject()) {
			showMissingDataMessage(g, guiLeft, guiTop);
			return;
		}
		JsonObject lastEvent = events.get(events.size() - 1).getAsJsonObject();
		if (J.i(lastEvent, "key", -1) != J.i(goalsJson, "id", -2)) {
			showMissingDataMessage(g, guiLeft, guiTop);
			return;
		}

		Set<String> completedGoals = new HashSet<>();
		for (JsonElement e : J.arrOrEmpty(lastEvent, "completed_goals")) completedGoals.add(e.getAsString());

		Draw.full(g, BINGO_GUI_TEXTURE, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		List<Component> tooltip = null;
		JsonArray goals = J.arr(goalsJson, "goals");
		int initialY = guiTop + 46, initialX = guiLeft + 231;
		for (int i = 0; i < goals.size() && i < 25; i++) {
			if (!goals.get(i).isJsonObject()) continue;
			JsonObject goal = goals.get(i).getAsJsonObject();
			int row = i / 5, col = i % 5;
			boolean completed, communityGoal;
			ItemStack stack;
			if (goal.has("tiers")) {
				stack = new ItemStack(isCommunityGoalFinished(goal) ? Items.EMERALD_BLOCK : Items.IRON_BLOCK);
				completed = true;
				communityGoal = true;
			} else {
				communityGoal = false;
				completed = completedGoals.contains(J.str(goal, "id", ""));
				stack = new ItemStack(completed ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("lime_dye")) : Items.PAPER);
			}
			// NEU's 1px x/y adjustments (never reset, so they leaked to every following goal) are dropped.
			int x = initialX + 24 * col, y = initialY + 24 * row;
			g.item(stack, x, y);
			if (mouseX >= x - 2 && mouseX < x + 18 && mouseY >= y - 2 && mouseY < y + 18) {
				tooltip = getTooltip(goal, completed, communityGoal);
			}
		}

		String totalPoints = "§bCollected Points: §f" + J.i(lastEvent, "points", 0);
		int total = completedGoals.size();
		String personalGoals = total == 20
			? "§bPersonal Goals: §620/20"
			: "§bPersonal Goals: §f" + total + "§6/§f20";
		Draw.text(g, totalPoints, guiLeft + 22, guiTop + 19, true);
		Draw.text(g, personalGoals, guiLeft + 22, guiTop + 31, true);

		if (tooltip != null) screen.tooltipToDisplay = tooltip;
	}

	private static int finalTier(JsonObject goal) {
		JsonArray tiers = J.arrOrEmpty(goal, "tiers");
		long progress = J.lng(goal, "progress", 0);
		int finalTier = 0;
		for (JsonElement tier : tiers) {
			if (progress < tier.getAsLong()) break;
			finalTier++;
		}
		return finalTier;
	}

	private static boolean isCommunityGoalFinished(JsonObject goal) {
		return finalTier(goal) == J.arrOrEmpty(goal, "tiers").size();
	}

	private static String generateProgressIndicator(double progress, double goal) {
		double percentage = goal <= 0 ? 100 : progress / goal * 100;
		int filled = percentage >= 100 ? 20 : (int) Math.round(percentage / 100 * 20);
		StringBuilder sb = new StringBuilder("§2");
		for (int i = 0; i < 20; i++) {
			sb.append('-');
			if (i > filled) sb.append("§7");
		}
		return sb.toString();
	}

	private static List<Component> getTooltip(JsonObject goal, boolean completed, boolean communityGoal) {
		List<String> t = new ArrayList<>();
		String name = J.str(goal, "name", "?");
		if (communityGoal) {
			JsonArray tiers = J.arrOrEmpty(goal, "tiers");
			int totalTiers = tiers.size();
			double progress = J.lng(goal, "progress", 0);
			int finalTier = finalTier(goal);
			// Same as NEU: progress is shown against the last tier.
			double nextTier = totalTiers == 0 ? 0 : tiers.get(totalTiers - 1).getAsLong();
			int progressToNextTier = nextTier <= 0 ? 100 : (int) Math.min(100, Math.round(progress / nextTier * 100));
			int nextTierNum = finalTier < totalTiers ? finalTier + 1 : totalTiers;
			t.add("§a" + name + " " + finalTier);
			t.add("§8Community Goal");
			t.add("");
			t.add("§7Progress to " + name + " " + nextTierNum + ": §e" + progressToNextTier + "§6%");
			t.add(generateProgressIndicator(progress, nextTier) + "§e " + Fmt.shortNumber(progress) + "§6/§e" + Fmt.shortNumber(nextTier));
			t.add("");
			t.add("§8§oCommunity Goals are");
			t.add("§8§ocollaborative - anyone with a");
			t.add("§8§oBingo profile can help to reach");
			t.add("§8§othe goal!");
			t.add("");
			t.add("§8§oThe more you contribute");
			t.add("§8§otowards the goal, the more you");
			t.add("§8§owill be rewarded");
			if (finalTier == totalTiers) {
				t.add("");
				t.add("§aGOAL REACHED");
			}
		} else {
			t.add("§a" + name);
			t.add("§8Personal Goal");
			t.add("");
			for (String line : J.str(goal, "lore", "").split("\n")) t.add(line);
			t.add("");
			t.add("§7Reward");
			t.add("§61 Bingo Point");
			t.add("");
			t.add(completed ? "§aGOAL REACHED" : "§cYou have not reached this goal!");
		}
		List<Component> out = new ArrayList<>(t.size());
		for (String l : t) out.add(Component.literal(l));
		return out;
	}

	private static void showMissingDataMessage(GuiGraphicsExtractor g, int guiLeft, int guiTop) {
		Draw.centered(g, "§cNo Bingo data for current event!", guiLeft + 431 / 2f, guiTop + 101, true);
	}
}
