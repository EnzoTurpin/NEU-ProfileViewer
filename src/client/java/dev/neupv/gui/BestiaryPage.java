/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates BestiaryPage.kt, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

public class BestiaryPage extends PvPage {
	private static final Identifier BESTIARY_TEXTURE = Draw.tex("gui/pv_bestiary_tab.png");
	private static final int MOB_LIST_X_COUNT = 9;
	private static final int MOB_LIST_Y_COUNT = 5;
	private static final float MOB_LIST_X_PADDING = (240 - MOB_LIST_X_COUNT * 20) / (float) (MOB_LIST_X_COUNT + 1);
	private static final float MOB_LIST_Y_PADDING = (202 - MOB_LIST_Y_COUNT * 20) / (float) (MOB_LIST_Y_COUNT + 1);

	private String selectedCategory = "dynamic";
	private String selectedSubCategory = "";
	private double bestiaryLevel;
	private List<BestiaryData.Category> computedCategories = new ArrayList<>();
	private JsonObject lastProfileJson;

	public BestiaryPage(ProfileViewerScreen screen) {
		super(screen);
	}

	/** Category tab layout: NEU's spacing while the tabs fit, a compact one when the repo has more islands. */
	private record TabLayout(int startX, float step, int size) {
		int x(int i) {
			return startX + (int) (step * i);
		}
	}

	private TabLayout tabLayout() {
		int n = computedCategories.size();
		int neuStep = (int) (350f / (n - 1 + 0.0000001f));
		if (neuStep >= 21) return new TabLayout(30, neuStep, 20);
		float step = 415f / n;
		return new TabLayout(8, step, Math.min(20, (int) step - 1));
	}

	private boolean ensureParsed() {
		var selected = getSelectedProfile();
		if (selected == null) return false;
		JsonObject profileInfo = selected.getProfileJson();
		if (!BestiaryData.hasMigrated(profileInfo) || NeuRepo.constant("bestiary") == null) return false;
		// Parse once per profile (NEU: once or on profile switch)
		if (computedCategories.isEmpty() || lastProfileJson != profileInfo) {
			computedCategories = BestiaryData.parseBestiaryData(profileInfo);
			bestiaryLevel = BestiaryData.calculateTotalBestiaryTiers(computedCategories);
			lastProfileJson = profileInfo;
			if (computedCategories.stream().noneMatch(c -> c.id().equals(selectedCategory)) && !computedCategories.isEmpty()) {
				selectedCategory = computedCategories.getFirst().id();
				selectedSubCategory = "";
			}
		}
		return !computedCategories.isEmpty();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (!ensureParsed()) {
			Draw.centered(g, "§cNo valid bestiary data!", guiLeft + 431 / 2f, guiTop + 101, true);
			return;
		}
		List<String> tooltip = new ArrayList<>();

		// Category list
		TabLayout tabs = tabLayout();
		for (int i = 0; i < computedCategories.size(); i++) {
			BestiaryData.Category category = computedCategories.get(i);
			int x = guiLeft + tabs.x(i), y = guiTop + 10;
			if (mouseX > x && mouseX < x + tabs.size() && mouseY > y && mouseY < y + 20) tooltip.add("§7" + category.name());
			drawSlot(g, x, y, tabs.size(), category.id().equals(selectedCategory));
			float scale = (tabs.size() - 4) / 16f;
			Draw.item(g, category.icon(), x + 2, y + 2 + (20 - tabs.size()) / 2f, scale);
		}

		Draw.full(g, BESTIARY_TEXTURE, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
		Draw.aligned(g, "§cMilestone: ", "§7" + (bestiaryLevel / 10), guiLeft + 280, guiTop + 50, 110);

		BestiaryData.Category category = selectedCategoryData();
		// Subcategories in the bottom right corner, if applicable
		if (!category.subCategories().isEmpty()) {
			if (selectedSubCategory.isEmpty() || subCategoryData(category) == null) {
				selectedSubCategory = category.subCategories().getFirst().id();
			}
			Draw.centered(g, "§cSubcategories", guiLeft + 317, guiTop + 169, true);
			int xStart = guiLeft + 280, y = guiTop + 175;
			for (int i = 0; i < category.subCategories().size(); i++) {
				BestiaryData.Category sub = category.subCategories().get(i);
				int x = xStart + 24 * i;
				drawSlot(g, x, y, 20, sub.id().equals(selectedSubCategory));
				g.item(sub.icon(), x + 2, y + 2);
				if (mouseX > x && mouseX < x + 24 && mouseY > y && mouseY < y + 16) tooltip.add(sub.name());
			}
		} else {
			selectedSubCategory = "";
		}

		// Family information
		drawFamilyData(g, category.familyData(), guiLeft + 280, guiTop + 70);
		BestiaryData.Category sub = subCategoryData(category);
		if (sub != null) drawFamilyData(g, sub.familyData(), guiLeft + 280, guiTop + 120);

		// Mob list
		List<BestiaryData.Mob> mobs = sub != null ? sub.mobs() : category.mobs();
		for (int i = 0; i < mobs.size() && i < MOB_LIST_X_COUNT * MOB_LIST_Y_COUNT; i++) {
			BestiaryData.Mob mob = mobs.get(i);
			int xIndex = i % MOB_LIST_X_COUNT, yIndex = i / MOB_LIST_X_COUNT;
			float fx = 23 + MOB_LIST_X_PADDING + (MOB_LIST_X_PADDING + 20) * xIndex;
			float fy = 30 + MOB_LIST_Y_PADDING + (MOB_LIST_Y_PADDING + 20) * yIndex;
			int x = guiLeft + (int) fx, y = guiTop + (int) fy;
			drawSlot(g, x, y, 20, false);
			g.item(mob.icon(), x + 2, y + 2);
			if (mouseX > x + 2 && mouseX < x + 18 && mouseY > y + 2 && mouseY < y + 18) {
				tooltip = mobTooltip(mob);
			}
			BestiaryData.MobLevelData ld = mob.mobLevelData();
			String level = ld.maxLevel() ? "§6" + ld.level() : String.valueOf(ld.level());
			int w = Draw.font().width(level);
			g.text(Draw.font(), level, Math.round(guiLeft + fx + 10 - w / 2f), Math.round(guiTop + fy + 26 - 4.5f), 0xFF808080, true);
		}

		if (!tooltip.isEmpty()) {
			List<Component> lines = new ArrayList<>(tooltip.size());
			for (String l : tooltip) lines.add(Component.literal("§7" + l));
			screen.tooltipToDisplay = lines;
		}
	}

	private static List<String> mobTooltip(BestiaryData.Mob mob) {
		BestiaryData.MobLevelData ld = mob.mobLevelData();
		List<String> t = new ArrayList<>();
		t.add(mob.name() + " " + ld.level());
		t.add("§7Kills: §a" + Fmt.number(mob.kills()));
		t.add("§7Deaths: §a" + Fmt.number(mob.deaths()));
		t.add("");
		if (!ld.maxLevel()) {
			t.add("§7Progress to Tier " + (ld.level() + 1) + ": §b" + ld.progress() + "%");
			t.add(progressBar(ld.progress()) + "§r§b " + Fmt.number(ld.mobKillData().tierKills()) + "/" + Fmt.number(ld.mobKillData().tierReq()));
			t.add("");
		}
		t.add("§7Overall Progress: §b" + ld.totalProgress() + "%" + (ld.maxLevel() ? " §7(§c§lMAX!§r§7)" : ""));
		t.add(progressBar(ld.totalProgress()) + "§r§b " + Fmt.number(ld.mobKillData().cappedKills()) + "/" + Fmt.number(ld.mobKillData().cap()));
		return t;
	}

	private static String progressBar(double progress) {
		StringBuilder bar = new StringBuilder("§3§l§m");
		for (int j = 1; j <= 14; j++) {
			if (progress < j * (100 / 14)) bar.append("§f§l§m");
			bar.append(' ');
		}
		return bar.toString();
	}

	private void drawFamilyData(GuiGraphicsExtractor g, BestiaryData.FamilyData data, int x, int y) {
		Draw.aligned(g, "§cFamilies Found:", (data.found() == data.total() ? "§6" : "§7") + data.found() + "/" + data.total(), x, y, 110);
		if (data.found() == data.total()) Draw.goldBar(g, x, y + 10, 112, screen.startTime);
		else Draw.bar(g, x, y + 10, 112, data.total() == 0 ? 0 : data.found() / (float) data.total());
		Draw.aligned(g, "§cFamilies Completed:", (data.completed() == data.total() ? "§6" : "§7") + data.completed() + "/" + data.total(), x, y + 20, 110);
		if (data.completed() == data.total()) Draw.goldBar(g, x, y + 30, 112, screen.startTime);
		else Draw.bar(g, x, y + 30, 112, data.total() == 0 ? 0 : data.completed() / (float) data.total());
	}

	/** pv_elements slot; the selected state is NEU's 180°-rotated (pressed-looking) copy of the same sprite. */
	private static void drawSlot(GuiGraphicsExtractor g, int x, int y, int size, boolean selected) {
		if (!selected) {
			g.blit(RenderPipelines.GUI_TEXTURED, ProfileViewerScreen.PV_ELEMENTS, x, y, 0, 0, size, size, 20, 20, 256, 256);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x + size, y + size);
		g.pose().scale(-1, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, ProfileViewerScreen.PV_ELEMENTS, 0, 0, 0, 0, size, size, 20, 20, 256, 256);
		g.pose().popMatrix();
	}

	private BestiaryData.Category selectedCategoryData() {
		for (BestiaryData.Category c : computedCategories) if (c.id().equals(selectedCategory)) return c;
		BestiaryData.Category first = computedCategories.getFirst();
		selectedCategory = first.id();
		return first;
	}

	private BestiaryData.Category subCategoryData(BestiaryData.Category category) {
		if (selectedSubCategory.isEmpty()) return null;
		for (BestiaryData.Category s : category.subCategories()) if (s.id().equals(selectedSubCategory)) return s;
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (computedCategories.isEmpty()) return false;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		TabLayout tabs = tabLayout();
		for (int i = 0; i < computedCategories.size(); i++) {
			int x = guiLeft + tabs.x(i), y = guiTop + 10;
			if (mouseX > x && mouseX < x + tabs.size() && mouseY > y && mouseY < y + 20) {
				String id = computedCategories.get(i).id();
				if (!id.equals(selectedCategory)) {
					selectedCategory = id;
					// NEU kept the old subcategory id here, which crashed (!!) when switching between two
					// categories that both have subcategories.
					selectedSubCategory = "";
					playPressSound();
				}
				return true;
			}
		}
		BestiaryData.Category category = selectedCategoryData();
		int xStart = guiLeft + 280, y = guiTop + 175;
		for (int i = 0; i < category.subCategories().size(); i++) {
			int x = xStart + 24 * i;
			if (mouseX > x && mouseX < x + 24 && mouseY > y && mouseY < y + 20) {
				String id = category.subCategories().get(i).id();
				if (!id.equals(selectedSubCategory)) {
					selectedSubCategory = id;
					playPressSound();
				}
				return true;
			}
		}
		return false;
	}

	private static void playPressSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	@Override
	public void resetCache() {
		computedCategories = new ArrayList<>();
		lastProfileJson = null;
	}
}
