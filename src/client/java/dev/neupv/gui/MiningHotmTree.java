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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a perk tree (scrollable, with tooltips) from NEU-REPO constants/hotmlayout.json (Heart of the Mountain) or
 * constants/hotflayout.json (Heart of the Forest, same format, not in NEU).
 */
public final class MiningHotmTree {
	private static final Identifier PERK_BACKGROUND = Draw.tex("gui/mining/perk_background.png");
	private static final Identifier PERK_CONNECTION_X = Draw.tex("gui/mining/perk_connection_x.png");
	private static final Identifier PERK_CONNECTION_Y = Draw.tex("gui/mining/perk_connection_y.png");

	/** Parsed layout per repo constant, rebuilt when the constant changes. */
	private static final class LayoutHolder {
		JsonObject source;
		MiningHotmLayout layout;
		boolean failed;
	}

	private static final Map<String, LayoutHolder> LAYOUTS = new HashMap<>();

	private record Rendered(List<Component> tooltip, ItemStack item) {}

	/** "hotm" or "hotf": the constant is {root}layout.json, its tree is under the {root} key. */
	private final String root;
	private final Map<String, Rendered> cache = new LinkedHashMap<>();
	private Object cacheKeyNodes;
	private double cacheKeyHotm = Double.NaN;
	private MiningHotmLayout cacheKeyLayout;
	public int scroll = 0;

	public MiningHotmTree() {
		this("hotm");
	}

	public MiningHotmTree(String root) {
		this.root = root;
	}

	/** The parsed HOTM layout; null if missing or broken. */
	static MiningHotmLayout layout() {
		return layout("hotm");
	}

	/** The parsed layout of constants/{root}layout.json, rebuilt when the repo constant changes; null if missing or broken. */
	static MiningHotmLayout layout(String root) {
		JsonObject src = NeuRepo.constant(root + "layout");
		if (src == null) return null;
		LayoutHolder h = LAYOUTS.computeIfAbsent(root, k -> new LayoutHolder());
		if (src != h.source) {
			h.source = src;
			h.failed = false;
			try {
				h.layout = new MiningHotmLayout(src, root);
			} catch (Exception e) {
				NeuPv.LOGGER.error("Could not parse constants/{}layout.json", root, e);
				h.layout = null;
				h.failed = true;
			}
		}
		return h.failed ? null : h.layout;
	}

	public void resetCache() {
		cache.clear();
		cacheKeyNodes = null;
	}

	/** Number of perks evaluated for the last rendered tree (tests). */
	public int renderedPerks() {
		return cache.size();
	}

	/** NEU MiningPage.renderHotmTree. */
	public void render(GuiGraphicsExtractor g, ProfileViewerScreen screen, int left, int top, int right, int bottom,
		JsonObject nodes, double hotmLevel, int mouseX, int mouseY) {
		MiningHotmLayout l = layout(root);
		if (l == null) {
			Draw.centered(g, "§cMissing repo data", (left + right) / 2f, (top + bottom) / 2f - 5, true);
			Draw.centered(g, "§c(" + root + "layout.json)", (left + right) / 2f, (top + bottom) / 2f + 5, true);
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
				stack = icon(state.itemId());
			} catch (Exception e) {
				NeuPv.LOGGER.error("Could not evaluate {} perk {}", root, perk.key, e);
				tt.add(Component.literal("§c" + perk.name));
				tt.add(Component.literal("§c<lisp-error>"));
				stack = new ItemStack(Items.PAINTING);
			}
			// Addition: Hypixel keeps the level of a perk switched off in the tree menu ("toggle_<perk>": false).
			JsonElement toggle = levels.get("toggle_" + perk.key);
			if (toggle != null && toggle.isJsonPrimitive() && toggle.getAsJsonPrimitive().isBoolean() && !toggle.getAsBoolean()
				&& levels.containsKey(perk.key)) {
				tt.add(Component.empty());
				tt.add(Component.literal("§cDISABLED"));
			}
			cache.put(perk.key, new Rendered(tt, stack));
		}
	}

	/**
	 * NEU repo item, else the vanilla item of that id: hotflayout.json uses modern blocks (PALE_OAK_BUTTON, OAK_SAPLING,
	 * MANGROVE_ROOTS...) that the 1.8.9 repo does not have.
	 */
	private static ItemStack icon(String itemId) {
		if (itemId == null) return new ItemStack(Items.PAINTING);
		PvItem item = LegacyItems.fromRepo(itemId);
		if (item != null) return item.stack();
		Identifier id = Identifier.tryParse(itemId.toLowerCase(Locale.ROOT));
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
		return new ItemStack(Items.PAINTING);
	}

	/** Marks the selected pickaxe/axe ability in its tooltip, as Hypixel does in the tree menu (not in NEU). */
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
