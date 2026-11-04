/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates hotm/HotmTreeScreen.kt (HotmTreeRenderer.renderPerks) and MiningPage.renderHotmTree,
 * Copyright (C) 2023-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.NeuPv;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.repo.NeuRepo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders the HOTM perk tree (scrollable, with tooltips) from NEU-REPO constants/hotmlayout.json. */
public final class MiningHotmTree {
	private static final Identifier PERK_BACKGROUND = Draw.tex("gui/mining/perk_background.png");
	private static final Identifier PERK_CONNECTION_X = Draw.tex("gui/mining/perk_connection_x.png");
	private static final Identifier PERK_CONNECTION_Y = Draw.tex("gui/mining/perk_connection_y.png");

	private static JsonObject layoutSource;
	private static MiningHotmLayout layout;
	private static boolean layoutFailed;

	private record Rendered(List<Component> tooltip, ItemStack item) {}

	private final Map<String, Rendered> cache = new LinkedHashMap<>();
	private Object cacheKeyNodes;
	private double cacheKeyHotm = Double.NaN;
	private MiningHotmLayout cacheKeyLayout;
	public int scroll = 0;

	/** The parsed layout, rebuilt when the repo constant changes; null if missing or broken. */
	static MiningHotmLayout layout() {
		JsonObject src = NeuRepo.constant("hotmlayout");
		if (src == null) return null;
		if (src != layoutSource) {
			layoutSource = src;
			layoutFailed = false;
			try {
				layout = new MiningHotmLayout(src);
			} catch (Exception e) {
				NeuPv.LOGGER.error("Could not parse constants/hotmlayout.json", e);
				layout = null;
				layoutFailed = true;
			}
		}
		return layoutFailed ? null : layout;
	}

	public void resetCache() {
		cache.clear();
		cacheKeyNodes = null;
	}

	/** NEU MiningPage.renderHotmTree. */
	public void render(GuiGraphicsExtractor g, ProfileViewerScreen screen, int left, int top, int right, int bottom,
		JsonObject nodes, double hotmLevel, int mouseX, int mouseY) {
		MiningHotmLayout l = layout();
		if (l == null) {
			Draw.centered(g, "§cMissing repo data", (left + right) / 2f, (top + bottom) / 2f - 5, true);
			Draw.centered(g, "§c(hotmlayout.json)", (left + right) / 2f, (top + bottom) / 2f + 5, true);
			return;
		}
		boolean hovered = left < mouseX && mouseX < right && top < mouseY && mouseY < bottom;
		int maxScroll = l.ySize * 24 - (bottom - top) - 24 / 2;
		scroll = Math.min(maxScroll + 4, Math.max(0, scroll));
		g.enableScissor(left, top, right, bottom);
		renderPerks(g, screen, l, nodes, hotmLevel, left + (right - left) / 2 - l.xSize * 24 / 2 + 4 / 2,
			top - maxScroll + scroll, mouseX, mouseY, hovered, 20, 4);
		g.disableScissor();
	}

	private void renderPerks(GuiGraphicsExtractor g, ProfileViewerScreen screen, MiningHotmLayout l, JsonObject nodes,
		double hotmLevel, int x, int y, int mouseX, int mouseY, boolean renderTooltip, int gridSize, int gridSpacing) {
		ensureCache(l, nodes, hotmLevel);
		int relX = mouseX - x, relY = mouseY - y;
		int gridOffset = (gridSize - 16) / 2;
		for (MiningHotmLayout.Perk perk : l.perks.values()) {
			Rendered r = cache.get(perk.key);
			if (r == null) continue;
			Draw.rect(g, PERK_BACKGROUND, perk.x * gridSize + x + gridSpacing / 2, perk.y * gridSize + y + gridSpacing / 2,
				gridSize - gridSpacing, gridSize - gridSpacing, 0, 1, 0, 1, 20, 20);
			if (l.hasNode(perk.x - 1, perk.y)) {
				Draw.rect(g, PERK_CONNECTION_X, perk.x * gridSize + x - gridSpacing / 2, perk.y * gridSize + y,
					gridSpacing, gridSize, 0, 1, 0, 1, 4, 20);
			}
			if (l.hasNode(perk.x, perk.y - 1)) {
				Draw.rect(g, PERK_CONNECTION_Y, perk.x * gridSize + x, perk.y * gridSize + y - gridSpacing / 2,
					gridSize, gridSpacing, 0, 1, 0, 1, 20, 4);
			}
			if (renderTooltip
				&& relX >= perk.x * gridSize + gridSpacing / 2 && relX <= perk.x * gridSize + gridSize - gridSpacing / 2
				&& relY >= perk.y * gridSize + gridSpacing / 2 && relY <= perk.y * gridSize + gridSize - gridSpacing / 2) {
				screen.tooltipToDisplay = r.tooltip();
			}
			g.item(r.item(), perk.x * gridSize + x + gridOffset, perk.y * gridSize + y + gridOffset);
		}
	}

	/** Lisp evaluation is too slow for every frame: evaluate once per profile/level set. */
	private void ensureCache(MiningHotmLayout l, JsonObject nodes, double hotmLevel) {
		if (cacheKeyNodes == nodes && cacheKeyHotm == hotmLevel && cacheKeyLayout == l && !cache.isEmpty()) return;
		cache.clear();
		cacheKeyNodes = nodes;
		cacheKeyHotm = hotmLevel;
		cacheKeyLayout = l;
		Map<String, JsonElement> levels = new HashMap<>();
		if (nodes != null) for (Map.Entry<String, JsonElement> e : nodes.entrySet()) levels.put(e.getKey(), e.getValue());
		for (MiningHotmLayout.Perk perk : l.perks.values()) {
			List<Component> tt = new ArrayList<>();
			ItemStack stack;
			try {
				MiningHotmLayout.PerkState state = l.evaluate(perk, levels, hotmLevel);
				for (String line : state.tooltip()) tt.add(Component.literal(line));
				PvItem item = state.itemId() == null ? null : LegacyItems.fromRepo(state.itemId());
				stack = item != null ? item.stack() : new ItemStack(Items.PAINTING);
			} catch (Exception e) {
				NeuPv.LOGGER.error("Could not evaluate HOTM perk {}", perk.key, e);
				tt.add(Component.literal("§c" + perk.name));
				tt.add(Component.literal("§c<lisp-error>"));
				stack = new ItemStack(Items.PAINTING);
			}
			cache.put(perk.key, new Rendered(tt, stack));
		}
	}

	/** Marks the selected pickaxe ability in its tooltip, as Hypixel does in the HOTM menu (not in NEU). */
	public void markSelectedAbility(String abilityKey) {
		if (abilityKey == null) return;
		Rendered r = cache.get(abilityKey);
		if (r == null || r.tooltip().isEmpty()) return;
		String last = r.tooltip().get(r.tooltip().size() - 1).getString();
		if (last.equals("§aSELECTED")) return;
		List<Component> tt = new ArrayList<>(r.tooltip());
		tt.add(Component.empty());
		tt.add(Component.literal("§aSELECTED"));
		cache.put(abilityKey, new Rendered(tt, r.item()));
	}
}
