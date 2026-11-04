/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates GardenPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.net.UrsaClient;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Garden tab. NEU fetched the garden and Elitebot data with blocking .get() calls in a coroutine and showed
 * "Loading Data" until both finished; here both requests are polled every frame, and the garden renders as soon
 * as its own data is there (the farming weight shows "Loading..." until Elitebot answers).
 */
public class GardenPage extends PvPage {
	private static final Identifier BACKGROUND = Draw.tex("gui/profile_viewer/garden/background.png");

	private GardenData.Repo repoData;
	private JsonObject parsedRepo;
	private GardenData gardenData;
	private JsonObject parsedGarden;
	private GardenData.EliteWeight eliteData;
	private JsonObject parsedElite;
	private Map<GardenData.VisitorRarity, int[]> visitorStats;
	private final Map<String, ItemStack> itemCache = new HashMap<>();

	private int mouseX, mouseY;

	public GardenPage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;

		JsonObject repoJson = NeuRepo.constant("garden");
		if (repoJson != parsedRepo) {
			repoData = GardenData.Repo.parse(repoJson);
			parsedRepo = repoJson;
			visitorStats = null;
		}
		if (repoData == null) {
			Draw.centered(g, "§cMissing Repo Data", guiLeft + 220, guiTop + 101, true);
			return;
		}

		String profileId = J.str(selected.getOuterProfileJson(), "profile_id", "").replace("-", "");
		if (profileId.isEmpty()) {
			Draw.centered(g, "§cMissing Profile Data", guiLeft + 220, guiTop + 101, true);
			return;
		}
		SkyblockProfiles.Fetch fetch = screen.getProfile().fetchUrsa(UrsaClient.garden(profileId));
		if (fetch.state() == SkyblockProfiles.FetchState.LOADING) {
			Draw.centered(g, "§eLoading Data", guiLeft + 220, guiTop + 101, true);
			return;
		}
		if (fetch.state() == SkyblockProfiles.FetchState.ERROR) {
			Draw.centered(g, "§cCould not load the garden", guiLeft + 220, guiTop + 95, true);
			Draw.centeredMaxWidth(g, "§c" + fetch.error(), guiLeft + 220, guiTop + 107, true, 380, 0xFFFFFFFF);
			return;
		}
		if (fetch.json() != parsedGarden) {
			gardenData = GardenData.parse(fetch.json());
			parsedGarden = fetch.json();
			visitorStats = null;
		}
		if (gardenData == null || gardenData.gardenExperience == 0) {
			Draw.centered(g, "§cMissing Profile Data", guiLeft + 220, guiTop + 101, true);
			return;
		}
		if (visitorStats == null) visitorStats = gardenData.visitorStats(repoData);

		SkyblockProfiles.Fetch elite = screen.getProfile().fetchUrl("https://api.elitebot.dev/weight/" + screen.getProfile().getUuidString() + "/" + profileId);
		if (elite.state() == SkyblockProfiles.FetchState.OK && elite.json() != parsedElite) {
			eliteData = GardenData.parseElite(elite.json());
			parsedElite = elite.json();
		}

		Draw.full(g, BACKGROUND, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		renderPlots(g);
		renderGardenLevel(g, selected);
		renderFarmingWeight(g, elite);
		renderCropUpgrades(g);
		renderCropMilestones(g);
		renderVisitorStats(g);
		renderCompost(g);
	}

	private ItemStack repoItem(String id) {
		return itemCache.computeIfAbsent(id, k -> {
			PvItem item = LegacyItems.fromRepo(k);
			return item == null ? ItemStack.EMPTY : item.stack();
		});
	}

	private boolean hover(int x, int y, int w, int h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

	private void renderPlots(GuiGraphicsExtractor g) {
		int top = screen.guiTop() + 79;
		int left = screen.guiLeft() + 192;
		for (Map.Entry<String, GardenData.Plot> e : repoData.plots.entrySet()) {
			GardenData.Plot plot = e.getValue();
			int x = left + plot.x() * 22;
			int y = top + plot.y() * 22;
			Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, x, y, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
			if (!gardenData.unlockedPlotIds.contains(e.getKey())) {
				g.item(new ItemStack(Items.BARRIER), x + 2, y + 2);
				if (hover(x, y, 20, 20)) screen.tooltipToDisplay = PvWidgets.lines("§cLocked " + plot.name());
				continue;
			}
			g.item(new ItemStack(Items.GRASS_BLOCK), x + 2, y + 2);
			if (hover(x, y, 20, 20)) screen.tooltipToDisplay = PvWidgets.lines(plot.name());
		}

		Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, left + 2 * 22, top + 2 * 22, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
		int x = left + 2 * 22 + 2;
		int y = top + 2 * 22 + 2;
		GardenData.BarnSkin skin = gardenData.selectedBarnSkin == null ? null : repoData.barn.get(gardenData.selectedBarnSkin);
		ItemStack barnStack = skin == null || skin.item() == null ? ItemStack.EMPTY : repoItem(skin.item());
		if (skin != null && !barnStack.isEmpty()) {
			g.item(barnStack, x, y);
			// NEU tested x..x+20 from the item corner, 2 px past the slot; the slot is used here
			if (hover(x - 2, y - 2, 20, 20)) screen.tooltipToDisplay = PvWidgets.lines("§7Barn Skin: " + skin.name());
		} else {
			g.item(new ItemStack(Items.BARRIER), x, y);
			if (hover(x - 2, y - 2, 20, 20)) {
				screen.tooltipToDisplay = PvWidgets.lines("§cUnknown barn Skin: " + gardenData.selectedBarnSkin,
					"§cIf you expected it to be there please report it",
					"§cto the NEU repository (constants/garden.json).");
			}
		}
	}

	private void renderCropUpgrades(GuiGraphicsExtractor g) {
		int startHeight = screen.guiTop() + 105;
		int yPos = startHeight;
		int xPos = screen.guiLeft() + 26;
		shadowed(g, "§eCrop Upgrades", xPos + 70, yPos + 5, 105);

		int averageUpgrade = 0;
		List<Integer> costs = repoData.cropUpgrades;
		for (int index = 0; index < GardenData.Crop.GRID.size(); index++) {
			GardenData.Crop crop = GardenData.Crop.GRID.get(index);
			if (index == 5) {
				yPos = startHeight;
				xPos += 70;
			}
			yPos += 14;
			int upgradeLevel = gardenData.cropUpgradeLevels.getOrDefault(crop, 0);
			averageUpgrade += upgradeLevel;
			g.item(repoItem(crop.itemId), xPos + 2, yPos);
			Draw.aligned(g, "§e" + crop.displayName, "§f" + upgradeLevel, xPos + 20, yPos + 5, 50);

			if (hover(xPos + 20, yPos, 50, 20)) {
				List<String> tt = new ArrayList<>();
				tt.add("§a" + crop.displayName);
				tt.add("");
				boolean maxed = upgradeLevel >= costs.size();
				tt.add("§7Current Tier: " + (maxed ? "§a" : "§e") + upgradeLevel + "§7/§a" + costs.size());
				tt.add("§7" + crop.displayName + " Fortune: §6+" + upgradeLevel * 5 + "☘");
				tt.add("");
				if (maxed) {
					tt.add("§6Maxed");
				} else {
					tt.add("§7Cost:");
					tt.add("§c" + Fmt.number(costs.get(upgradeLevel)) + " §7Copper to Upgrade");
					long sum = 0;
					for (int i = upgradeLevel; i < costs.size(); i++) sum += costs.get(i);
					tt.add("§c" + Fmt.number(sum) + " §7Copper to Max");
				}
				screen.tooltipToDisplay = PvWidgets.lines(tt.toArray(String[]::new));
			}
		}
		int x = screen.guiLeft() + 70 + 26 - 42;
		int y = startHeight + 5;
		if (hover(x, y, 80, 13)) {
			// NEU: integer division ("Average Upgrade 4" for 4.9)
			screen.tooltipToDisplay = PvWidgets.lines("§eAverage Upgrade " + Fmt.tenths(averageUpgrade / (double) GardenData.Crop.GRID.size()));
		}
	}

	private void renderCropMilestones(GuiGraphicsExtractor g) {
		int startHeight = screen.guiTop() + 10;
		int yPos = startHeight;
		int xPos = screen.guiLeft() + 26;
		shadowed(g, "§eCrop Milestones", xPos + 70, yPos + 5, 105);

		int averageMilestone = 0;
		for (int index = 0; index < GardenData.Crop.GRID.size(); index++) {
			GardenData.Crop crop = GardenData.Crop.GRID.get(index);
			if (index == 5) {
				yPos = startHeight;
				xPos += 70;
			}
			yPos += 14;
			g.item(repoItem(crop.itemId), xPos + 2, yPos);
			List<Long> tiers = repoData.cropMilestones.get(crop);
			if (tiers == null || tiers.isEmpty()) continue;
			long collected = gardenData.resourcesCollected.getOrDefault(crop, 0L);
			GardenData.Milestone m = GardenData.milestone(tiers, collected);
			String formattedAmount = Fmt.number(collected);

			List<String> tt = new ArrayList<>();
			tt.add("§a" + crop.displayName + " " + m.level());
			tt.add("§7Total: §a" + formattedAmount);
			tt.add("");
			if (!m.maxed()) {
				tt.add("Progress to Tier " + (m.level() + 1) + ": §e" + String.format(Locale.US, "%.2f", m.progress() * 100.0 / m.needed()) + "%");
				tt.add("§e" + Fmt.number(m.progress()) + "§6/§e" + Fmt.number(m.needed()));
				tt.add("");
				tt.add("Progress to Tier " + tiers.size() + ": §e" + String.format(Locale.US, "%.2f", collected * 100.0 / m.maxTotal()) + "%");
				tt.add("§e" + formattedAmount + "§6/§e" + Fmt.number(m.maxTotal()));
			} else {
				tt.add("§7Overflow: §6" + Fmt.number(m.overflow()));
				tt.add("§7Overflow Tier: §6" + m.overflowLevel());
				tt.add("");
				tt.add("§6Max tier reached!");
			}
			alignedWithHover(g, "§e" + crop.displayName, (m.maxed() ? "§6" : "§f") + m.level(), xPos + 20, yPos + 5, 50, tt);
			averageMilestone += m.level();
		}
		int x = screen.guiLeft() + 70 + 26 - 42;
		int y = startHeight + 5;
		if (hover(x, y, 80, 13)) {
			screen.tooltipToDisplay = PvWidgets.lines("§eAverage Milestone " + Fmt.tenths(averageMilestone / (double) GardenData.Crop.GRID.size()));
		}
	}

	private void renderVisitorStats(GuiGraphicsExtractor g) {
		int xPos = screen.guiLeft() + 322;
		int yPos = screen.guiTop() + 17;
		shadowed(g, "§eVisitors", xPos + 40, yPos - 2, 80);
		Draw.aligned(g, "§eUnique Visitors", "§f" + gardenData.uniqueNpcsServed + "/" + repoData.visitors.size(), xPos, yPos + 10, 80);
		yPos += 20;
		for (GardenData.VisitorRarity rarity : GardenData.VisitorRarity.values()) {
			int[] stats = visitorStats.get(rarity);
			String visits = Fmt.number(stats[0]);
			String completed = Fmt.number(stats[1]);
			alignedWithHover(g, rarity.displayName, "§f" + completed + "/" + visits, xPos, yPos, 80,
				List.of("§7Visits: §f" + visits, "§7Completed: §f" + completed));
			yPos += 12;
		}
	}

	private void renderGardenLevel(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile selected) {
		int top = screen.guiTop() + 20;
		int left = screen.guiLeft() + 190;
		com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
		repoData.gardenExperience.forEach(arr::add);
		Level level = Level.of(arr, gardenData.gardenExperience, repoData.gardenExperience.size(), false);
		if (level.maxed) Draw.goldBar(g, left + 16, top + 10, 80, screen.startTime);
		else Draw.bar(g, left + 16, top + 10, 80, level.level % 1);

		int maxXp = (int) level.maxXpForLevel;
		List<String> tt = new ArrayList<>();
		tt.add("§2Garden");
		if (level.maxed) tt.add("§7Progress: §6MAXED!");
		else tt.add("§7Progress: §5" + Fmt.shortNumber(Math.round((level.level % 1) * maxXp)) + "/" + Fmt.shortNumber(maxXp));
		tt.add("§7Total XP: §5" + Fmt.number((long) level.totalXp) + "§8 (" + Fmt.tenths(PvWidgets.percentage("garden", level)) + "% to " + level.maxLevel + ")");
		alignedWithHover(g, "§2Garden", "§f" + (int) level.level, left + 36, top, 60, tt);
		g.item(new ItemStack(Items.GRASS_BLOCK), left + 16, top - 6);

		long copper = J.lng(selected.getProfileJson(), "garden_player_data.copper", 0);
		Draw.aligned(g, "§cCopper", "§f" + Fmt.number(copper), left + 16, top + 20, 80);
	}

	private void renderFarmingWeight(GuiGraphicsExtractor g, SkyblockProfiles.Fetch elite) {
		int top = screen.guiTop() + 51;
		int left = screen.guiLeft() + 190;
		if (eliteData == null) {
			if (elite.state() == SkyblockProfiles.FetchState.LOADING) {
				alignedWithHover(g, "§eFarming Weight", "§eLoading...", left + 16, top, 95, List.of("§eLoading...", "§eTry again soon!"));
			} else {
				// NEU stayed on "Loading..." forever when Elitebot failed or had no data for the profile
				alignedWithHover(g, "§eFarming Weight", "§cError", left + 16, top, 95,
					List.of("§cCould not load the farming weight", "§c" + (elite.error() == null ? "No data on Elitebot" : elite.error())));
			}
			return;
		}
		List<String> tt = new ArrayList<>();
		tt.add("§7Total Weight: §f" + Fmt.number(eliteData.totalWeight()));
		tt.add("§7Bonus Weight: §f" + Fmt.number(eliteData.bonusWeight()));
		for (GardenData.Crop crop : GardenData.Crop.values()) {
			Double w = eliteData.cropWeight().get(crop);
			if (w == null && !GardenData.Crop.GRID.contains(crop)) continue;
			tt.add("§7" + crop.displayName + ": §f" + Fmt.number(w == null ? 0 : w));
		}
		tt.add("");
		tt.add("Data provided by the Elitebot API.");
		tt.add("§eClick to view on the Elitebot website.");
		alignedWithHover(g, "§eFarming Weight", "§f" + Fmt.number((int) eliteData.totalWeight()), left + 11, top, 90, tt);
	}

	private void renderCompost(GuiGraphicsExtractor g) {
		int xPos = screen.guiLeft() + 322;
		int yPos = screen.guiTop() + 122;
		shadowed(g, "§eCompost Upgrades", xPos + 40, yPos - 2, 80);
		yPos += 12;
		if (gardenData.composterUpgrades.isEmpty()) return;
		String[] names = {"§aSpeed", "§aMulti Drop", "§aFuel Cap", "§aOrganic Matter Cap", "§aCost Reduction"};
		for (int i = 0; i < GardenData.COMPOSTER_UPGRADES.size(); i++) {
			String repoName = GardenData.COMPOSTER_UPGRADES.get(i);
			String upgradeName = names[i];
			int upgradeAmount = gardenData.composterUpgrades.getOrDefault(repoName, 0);
			Map<Integer, GardenData.ComposterUpgrade> levels = repoData.composterUpgrades.getOrDefault(repoName, Map.of());
			GardenData.ComposterUpgrade next = levels.get(upgradeAmount + 1);
			GardenData.ComposterUpgrade current = levels.get(upgradeAmount);
			String currentSt = Fmt.number(current == null ? 0 : current.upgrade());
			String template = repoData.composterTooltips.get(repoName);
			List<String> tt = new ArrayList<>();
			tt.add(upgradeName + " " + upgradeAmount);
			if (next != null) {
				if (template != null) tt.add(template.replace("{}", currentSt + " -> " + Fmt.number(next.upgrade())));
				tt.add("");
				tt.add("§7Cost:");
				for (Map.Entry<String, Integer> item : next.items().entrySet()) {
					String id = item.getKey().toUpperCase(Locale.ROOT);
					JsonObject repoItem = NeuRepo.item(id);
					if (repoItem == null) tt.add("§cUnknown Item: " + item.getKey());
					else tt.add("§7" + item.getValue() + "x " + J.str(repoItem, "displayname", id));
				}
				tt.add("§7" + next.copper() + " §cCopper");
			} else {
				if (template != null) tt.add(template.replace("{}", currentSt));
				tt.add("§6Maxed");
			}
			alignedWithHover(g, "§e" + upgradeName, "§f" + upgradeAmount, xPos, yPos, 80, tt);
			yPos += 12;
		}
	}

	/** NEU Utils.renderShadowedString: centred on x, shrunk to maxLength. */
	private static void shadowed(GuiGraphicsExtractor g, String s, int x, int y, int maxLength) {
		Draw.centeredMaxWidth(g, s, x, y + 4, true, maxLength, 0xFFFFFFFF);
	}

	private void alignedWithHover(GuiGraphicsExtractor g, String first, String second, int x, int y, int length, List<String> hover) {
		Draw.aligned(g, first, second, x, y, length);
		if (hover(x, y, length, 13)) {
			List<Component> tt = new ArrayList<>();
			for (String s : hover) tt.add(Component.literal(s));
			screen.tooltipToDisplay = tt;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (button == 0 && mouseX >= guiLeft + 190 && mouseX <= guiLeft + 290 && mouseY >= guiTop + 51 && mouseY <= guiTop + 60) {
			if (eliteData == null) return false;
			String name = screen.getProfile().getDisplayName();
			if (name == null) name = screen.getProfile().getUuidString();
			String profileName = screen.getProfileName() == null ? "" : screen.getProfileName();
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
			ConfirmLinkScreen.confirmLinkNow(screen, URI.create("https://elitebot.dev/@" + name + "/" + profileName.replace(" ", "%20")));
			return true;
		}
		return false;
	}

	@Override
	public void resetCache() {
		gardenData = null;
		parsedGarden = null;
		eliteData = null;
		parsedElite = null;
		visitorStats = null;
	}
}
