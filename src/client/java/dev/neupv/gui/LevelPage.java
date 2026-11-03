/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates LevelPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** "SkyBlock Level" sub-page of the Skills tab: main level bar + one bar per sblevels.json task category. */
public class LevelPage extends PvPage {
	private static final Identifier PV_LEVELS = Draw.tex("gui/pv_levels.png");

	private final List<LevelTask> tasks = new ArrayList<>();

	public LevelPage(ProfileViewerScreen screen) {
		super(screen);
		tasks.add(new LevelCoreTask(this));
		tasks.add(new LevelDungeonTask(this));
		tasks.add(new LevelEssenceTask(this));
		tasks.add(new LevelMiscTask(this));
		tasks.add(new LevelSkillRelatedTask(this));
		tasks.add(new LevelSlayingTask(this));
		tasks.add(new LevelStoryTask(this));
		tasks.add(new LevelEventTask(this));
	}

	ProfileViewerScreen screen() {
		return screen;
	}

	/** sblevels.json (an embedded copy ships with the mod). */
	JsonObject getConstant() {
		return NeuRepo.sblevels();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		// side buttons are drawn by BasicPage
		if (getConstant() == null) {
			Draw.centered(g, "§cMissing repo data (sblevels.json)", guiLeft + ProfileViewerScreen.SIZE_X / 2f, guiTop + 101, true);
			return;
		}
		Draw.full(g, PV_LEVELS, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;

		double skyblockLevel = selected.getSkyblockLevel();
		JsonObject profileInfo = selected.getProfileJson();

		drawMainBar(g, skyblockLevel, mouseX, mouseY, guiLeft, guiTop);
		for (LevelTask task : tasks) {
			try {
				task.drawTask(g, selected, profileInfo, mouseX, mouseY, guiLeft, guiTop);
			} catch (RuntimeException e) {
				// malformed API / repo data must not take the whole viewer down (NEU would crash the GUI)
				dev.neupv.NeuPv.LOGGER.warn("SkyBlock level task {} failed", task.getClass().getSimpleName(), e);
			}
		}
	}

	/** Port of NEU LevelPage.renderLevelBar. */
	void renderLevelBar(GuiGraphicsExtractor g, String name, ItemStack stack, int x, int y, int xSize, double level, double xp,
		double max, int mouseX, int mouseY, boolean percentage, List<String> tooltip) {
		if (xp < 0) xp = 0;
		double ratio = max <= 0 ? 0 : xp / max;

		String second = "§f" + (int) level;
		if (percentage) second = "§f" + (int) (ratio * 100) + "%";
		Draw.aligned(g, "§c" + name, second, x + 14, y - 4, xSize - 20);

		if (xp >= max) Draw.goldBar(g, x, y + 6, xSize, screen.startTime);
		else Draw.bar(g, x, y + 6, xSize, (float) ratio);

		if (mouseX > x && mouseX < x + 120 && mouseY > y - 4 && mouseY < y + 13) {
			String levelStr = "§7Progress: §5" + (int) (ratio * 100) + "% §8(" + Fmt.number((int) xp) + "/" + Fmt.number((int) max) + " XP)";
			List<Component> tt = new ArrayList<>();
			if (tooltip != null && !tooltip.isEmpty()) {
				for (String s : tooltip) tt.add(Component.literal(s));
				tt.add(Component.empty());
			}
			tt.add(Component.literal(levelStr));
			screen.tooltipToDisplay = tt;
		}
		Draw.item(g, stack, x, y - 6, 0.7f);
	}

	private void drawMainBar(GuiGraphicsExtractor g, double skyblockLevel, int mouseX, int mouseY, int guiLeft, int guiTop) {
		renderLevelBar(g, "Level", BasicPage.levelIcon(), guiLeft + 163, guiTop + 30, 110, skyblockLevel,
			Math.round((skyblockLevel - (long) skyblockLevel) * 100), 100, mouseX, mouseY, false, List.of());
	}

	/** Port of NEU LevelPage.buildLore. */
	static String buildLore(String name, double xpGotten, double xpGainful, boolean hasNoLimit) {
		String gotten = Fmt.number((int) xpGotten);
		String gainful = Fmt.number((int) xpGainful);
		if (xpGainful == 0 && xpGotten == 0 && !hasNoLimit) return "§6" + name + ": §c§lNOT DETECTABLE!";
		if (hasNoLimit) return "§6" + name + ": §e" + gotten + " XP";
		int percentage = (int) ((xpGotten / xpGainful) * 100);
		if (xpGotten >= xpGainful) return "§6" + name + ": §a" + percentage + "% §8(" + gotten + "/" + gainful + " XP)";
		if (xpGotten == -1) return "§6" + name + ": §c§lCOLLECTION DISABLED!";
		if (xpGotten == -2) return "§6" + name + ": §eLoading...";
		return "§6" + name + ": §e" + percentage + "% §8(" + gotten + "/" + gainful + " XP)";
	}

	@Override
	public void resetCache() {
		for (LevelTask task : tasks) task.resetCache();
	}
}
