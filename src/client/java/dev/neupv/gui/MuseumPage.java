/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates MuseumPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.net.UrsaClient;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MuseumPage extends PvPage {
	private static final Identifier PV_MUSEUM = Draw.tex("gui/pv_museum.png");
	private static final Identifier PV_INVENTORIES = Draw.tex("gui/pv_inventories.png");
	private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	// 1.8's resource_packs.png arrows are separate sprites since 1.20.2
	private static final Identifier ARROW_LEFT = Identifier.withDefaultNamespace("transferable_list/unselect");
	private static final Identifier ARROW_LEFT_HL = Identifier.withDefaultNamespace("transferable_list/unselect_highlighted");
	private static final Identifier ARROW_RIGHT = Identifier.withDefaultNamespace("transferable_list/select");
	private static final Identifier ARROW_RIGHT_HL = Identifier.withDefaultNamespace("transferable_list/select_highlighted");
	private static final String RARITIES_SKULL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODZhZGRiZDVkZWRhZDQwOTk5NDczYmU0YTdmNDhmNjIzNmE3OWEwZGNlOTcxYjVkYmQ3MzcyMDE0YWUzOTRkIn19fQ";
	private static final int PAGE_ARROWS_HEIGHT = 34;
	private static final int SEARCH_W = 120, SEARCH_H = 20;
	private static final int OVERLAY = 0x64000000; // new Color(0, 0, 0, 100)

	private static String selectedMuseumCategory = "weapons";
	private static Map<String, ItemStack> categoryIcons;

	private int pages = 0;
	private int onPage = 1;
	private String currentItemSelected;
	private List<PvItem> selectedItem;

	private MuseumData data;
	private JsonObject parsedFrom, parsedRepo;
	private final Map<String, ItemStack> placeholderStacks = new HashMap<>();
	/** Clickable slots of the last frame (NEU selected on Mouse.isButtonDown while drawing). */
	private final List<Slot> slots = new ArrayList<>();
	private final List<int[]> overlays = new ArrayList<>();

	private record Slot(int x, int y, String id, List<PvItem> items) {}

	public MuseumPage(ProfileViewerScreen screen) {
		super(screen);
	}

	private static ItemStack categoryIcon(String key) {
		if (categoryIcons == null) {
			categoryIcons = new HashMap<>();
			categoryIcons.put("weapons", LegacyItems.icon(Items.DIAMOND_SWORD, "§6Weapons"));
			categoryIcons.put("armor", LegacyItems.icon(Items.DIAMOND_CHESTPLATE, "§6Armor Sets"));
			categoryIcons.put("rarities", LegacyItems.skull("§6Rarities", RARITIES_SKULL));
			categoryIcons.put("special", LegacyItems.icon(Items.CAKE, "§6Special Items"));
			// categories of the current museum.json (Hypixel's 2025 museum rework)
			categoryIcons.put("combat", LegacyItems.icon(Items.DIAMOND_SWORD, "§6Combat"));
			categoryIcons.put("farming", LegacyItems.icon(Items.GOLDEN_HOE, "§6Farming"));
			categoryIcons.put("mining", LegacyItems.icon(Items.STONE_PICKAXE, "§6Mining"));
			categoryIcons.put("fishing", LegacyItems.icon(Items.FISHING_ROD, "§6Fishing"));
			categoryIcons.put("foraging", LegacyItems.icon(Items.OAK_SAPLING, "§6Foraging"));
			categoryIcons.put("dungeoneering", LegacyItems.icon(Items.WITHER_SKELETON_SKULL, "§6Dungeoneering"));
			categoryIcons.put("hunting", LegacyItems.icon(Items.LEAD, "§6Hunting"));
		}
		return categoryIcons.computeIfAbsent(key, k -> LegacyItems.icon(Items.PAPER, "§6" + capitalize(k)));
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		slots.clear();
		overlays.clear();
		SkyblockProfiles.SkyblockProfile selectedProfile = getSelectedProfile();
		if (selectedProfile == null) return;

		Draw.full(g, PV_MUSEUM, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		String profileId = J.str(selectedProfile.getOuterProfileJson(), "profile_id", null);
		if (profileId == null) {
			Draw.centered(g, "§cMuseum API Disabled!", guiLeft + 250, guiTop + 101, true);
			return;
		}
		SkyblockProfiles.Fetch fetch = screen.getProfile().fetchUrsa(UrsaClient.museum(profileId));
		if (fetch.state() == SkyblockProfiles.FetchState.LOADING) {
			Draw.centered(g, "§eMuseum Data Loading!", guiLeft + 250, guiTop + 101, true);
			return;
		}
		if (fetch.state() == SkyblockProfiles.FetchState.ERROR) {
			Draw.centered(g, "§cCould not load the museum", guiLeft + 250, guiTop + 95, true);
			Draw.centeredMaxWidth(g, "§c" + fetch.error(), guiLeft + 250, guiTop + 107, true, 190, 0xFFFFFFFF);
			return;
		}
		JsonObject repo = NeuRepo.constant("museum");
		if (data == null || parsedFrom != fetch.json() || parsedRepo != repo) {
			data = MuseumData.parse(fetch.json(), screen.getProfile().getUuidString(), repo);
			parsedFrom = fetch.json();
			parsedRepo = repo;
			pages = 0;
		}
		if (data.value == MuseumData.API_DISABLED) {
			Draw.centered(g, "§cMuseum API Disabled!", guiLeft + 250, guiTop + 101, true);
			return;
		}
		if (data.value == MuseumData.MISSING_REPO) {
			Draw.centered(g, "§cMissing Repo Data!", guiLeft + 250, guiTop + 101, true);
			return;
		}

		EditBox search = screen.pageField(this, "museum_search", guiLeft + 251 - SEARCH_W / 2, guiTop + ProfileViewerScreen.SIZE_Y - 26 - 20, SEARCH_W, SEARCH_H);
		String searchText = search.getValue().trim().toLowerCase(Locale.ROOT);

		List<String> cats = allCategories();
		if (!cats.contains(selectedMuseumCategory)) setPage(cats.get(0));
		drawCategoryButtons(g, cats);
		drawStats(g, mouseX, mouseY);

		Draw.centered(g, categoryIcon(selectedMuseumCategory).getHoverName().getString(), guiLeft + 251, guiTop + 14, true);
		if (pages == 0) setPage(selectedMuseumCategory);
		drawArrows(g, mouseX, mouseY);

		int inventoryRows = 4;
		int invSizeY = inventoryRows * 18 + 17 + 7;
		int inventoryX = guiLeft + 251 - 176 / 2;
		int inventoryY = guiTop + 101 - invSizeY / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, CHEST_GUI_TEXTURE, inventoryX, inventoryY, 0, 0, 176, inventoryRows * 18 + 17, 256, 256);
		g.blit(RenderPipelines.GUI_TEXTURED, CHEST_GUI_TEXTURE, inventoryX, inventoryY + inventoryRows * 18 + 17, 0, 215, 176, 7, 256, 256);

		int startIndex = (onPage - 1) * 28;
		if (selectedMuseumCategory.equals("special")) {
			List<MuseumData.Donation> special = data.special;
			int endIndex = Math.min(startIndex + 28, special.size());
			for (int i = startIndex; i < endIndex; i++) {
				MuseumData.Donation d = special.get(i);
				int slot = i - startIndex;
				int x = inventoryX + 8 + (slot % 7) * 18 + 18;
				int y = guiTop + 71 + (slot / 7) * 18;
				PvItem item = d.items().get(0);
				g.item(item.stack(), x, y);
				if (mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16) screen.tooltipToDisplay = PvWidgets.itemTooltip(item);
				slots.add(new Slot(x, y, d.id(), d.items()));
				if (!searchText.isEmpty() && !matches(item.displayName, item.lore, d.id(), searchText)) overlays.add(new int[]{x, y});
			}
		} else {
			List<String> ids = data.categoryItems.get(selectedMuseumCategory);
			Map<String, MuseumData.Donation> donated = data.donated.get(selectedMuseumCategory);
			int endIndex = Math.min(startIndex + 28, ids.size());
			for (int i = startIndex; i < endIndex; i++) {
				String itemID = ids.get(i);
				int slot = i - startIndex;
				int x = inventoryX + 8 + (slot % 7) * 18 + 18;
				int y = guiTop + 71 + (slot / 7) * 18;
				MuseumData.Donation d = donated.get(itemID);
				String name;
				List<String> lore;
				ItemStack stack;
				List<Component> tooltip;
				if (d != null && !d.higherTier() && !d.items().isEmpty()) {
					PvItem item = d.items().get(0);
					stack = item.stack();
					name = item.displayName;
					lore = item.lore;
					tooltip = PvWidgets.itemTooltip(item);
					slots.add(new Slot(x, y, itemID, d.items()));
				} else {
					boolean higher = d != null;
					name = displayName(data.itemIdFor(itemID));
					lore = List.of(higher ? "§7Donated as higher tier" : "§cMissing");
					stack = placeholder(name, higher);
					tooltip = PvWidgets.lines(name, lore.get(0));
				}
				g.item(stack, x, y);
				if (mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16) screen.tooltipToDisplay = tooltip;
				if (!searchText.isEmpty() && !matches(name, lore, itemID, searchText)) overlays.add(new int[]{x, y});
			}
		}

		if (currentItemSelected != null && selectedItem != null) drawSelectedItem(g, mouseX, mouseY);

		if (!overlays.isEmpty()) {
			g.nextStratum(); // above the item models, like NEU's translate(0, 0, 50)
			for (int[] o : overlays) g.fill(o[0], o[1], o[0] + 16, o[1] + 16, OVERLAY);
		}
	}

	private List<String> allCategories() {
		List<String> cats = new ArrayList<>(data.categories);
		cats.add("special");
		return cats;
	}

	private void drawCategoryButtons(GuiGraphicsExtractor g, List<String> cats) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		var font = Draw.font();
		boolean compact = cats.size() > 4;
		for (int xIndex = 0; xIndex < cats.size(); xIndex++) {
			String key = cats.get(xIndex);
			boolean sel = key.equals(selectedMuseumCategory);
			ItemStack icon = categoryIcon(key);
			if (!compact) {
				// NEU layout: 4 buttons of 20 px, 34 px apart
				int bx = guiLeft + 16 + 34 * xIndex, by = guiTop + 172;
				if (sel) g.blit(ProfileViewerScreen.PV_ELEMENTS, bx, by, bx + 20, by + 20, 20 / 256f, 0, 20 / 256f, 0); // NEU: flipped UVs = pressed
				else Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, bx, by, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
				int ix = bx + (sel ? 3 : 2), iy = by + (sel ? 3 : 2);
				g.item(icon, ix, iy);
				g.itemDecorations(font, icon, ix, iy, String.valueOf(xIndex + 1));
			} else {
				// 8 categories since the 2025 museum rework: 17 px buttons so they fit under the stats panel
				int bx = guiLeft + 8 + 17 * xIndex, by = guiTop + 174;
				if (sel) g.blit(ProfileViewerScreen.PV_ELEMENTS, bx, by, bx + 16, by + 16, 20 / 256f, 0, 20 / 256f, 0);
				else Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, bx, by, 16, 16, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
				Draw.item(g, icon, bx + 2 + (sel ? 0.5f : 0), by + 2 + (sel ? 0.5f : 0), 0.75f);
			}
		}
	}

	private void drawStats(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.aligned(g, "§6Museum Value", "§f" + Fmt.shortNumber(data.value), guiLeft + 21, guiTop + 25, 114);
		int donated = data.totalDonated();
		Draw.aligned(g, "§9Total Donations", "§f" + donated, guiLeft + 21, guiTop + 45, 114);
		Draw.bar(g, guiLeft + 20, guiTop + 55, 116, (float) donated / data.maximum("total"));

		List<String> cats = data.categories;
		if (cats.size() <= 3) {
			// NEU layout (weapons / armor / rarities)
			int y = guiTop + 70;
			for (String cat : cats) {
				int n = data.donatedCount(cat);
				Draw.aligned(g, "§9" + statName(cat) + " Donated", "§f" + n, guiLeft + 21, y, 114);
				Draw.bar(g, guiLeft + 20, y + 10, 116, (float) n / data.maximum(cat));
				y += 25;
			}
			Draw.aligned(g, "§9Special Items Donated", "§f" + data.special.size(), guiLeft + 21, y, 114);
			return;
		}
		// One line per category: too many categories for NEU's 25 px rows with a bar each
		int y = guiTop + 66;
		List<String> all = new ArrayList<>(cats);
		all.add("special");
		for (String cat : all) {
			int n = data.donatedCount(cat);
			int max = data.maximum(cat);
			boolean full = n >= max;
			Draw.aligned(g, "§9" + statName(cat), (full ? "§6" : "§f") + n + "§7/" + max, guiLeft + 21, y, 114);
			if (Draw.within(mouseX, mouseY, guiLeft + 21, y - 1, 114, 10)) {
				screen.tooltipToDisplay = PvWidgets.lines("§9" + statName(cat), "§7Donated: §f" + n + "§7/§f" + max
					+ " §8(" + Fmt.tenths(100.0 * n / Math.max(1, max)) + "%)");
			}
			y += 12;
		}
	}

	private static String statName(String cat) {
		return switch (cat) {
			case "weapons" -> "Weapons";
			case "armor" -> "Armor";
			case "rarities" -> "Rarities";
			case "special" -> "Special Items";
			default -> capitalize(cat);
		};
	}

	private void drawArrows(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		boolean leftHovered = false, rightHovered = false;
		if ((Minecraft.getInstance().mouseHandler.isLeftPressed() || Minecraft.getInstance().mouseHandler.isRightPressed())
			&& mouseY > guiTop + PAGE_ARROWS_HEIGHT && mouseY < guiTop + PAGE_ARROWS_HEIGHT + 16
			&& mouseX > guiLeft + 251 - 12 && mouseX < guiLeft + 251 + 12) {
			if (mouseX < guiLeft + 251) leftHovered = true;
			else rightHovered = true;
		}
		// NEU drew a 24x32 region of the 1.8 arrows at half size (12x16)
		if (onPage > 1) arrow(g, leftHovered ? ARROW_LEFT_HL : ARROW_LEFT, 0, guiLeft + 251 - 12, guiTop + PAGE_ARROWS_HEIGHT);
		if (onPage < pages && pages != 1) arrow(g, rightHovered ? ARROW_RIGHT_HL : ARROW_RIGHT, 5, guiLeft + 251, guiTop + PAGE_ARROWS_HEIGHT);
	}

	private static void arrow(GuiGraphicsExtractor g, Identifier sprite, int u, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.5f, 0.5f);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 32, 32, u, 0, 0, 0, 24, 32);
		g.pose().popMatrix();
	}

	private void drawSelectedItem(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int size = Math.min(selectedItem.size(), 8);
		int startX = guiLeft + 375 + 5;
		switch (size) {
			case 1 -> Draw.rect(g, PV_INVENTORIES, guiLeft + 375, guiTop + 100, 26, 32, 75 / 101f, 1, 69 / 101f, 1, 101, 101);
			case 3 -> Draw.rect(g, PV_INVENTORIES, guiLeft + 375, guiTop + 100, 26, 68, 75 / 101f, 1, 0, 68 / 101f, 101, 101);
			case 4 -> Draw.rect(g, PV_INVENTORIES, guiLeft + 375, guiTop + 100, 26, 86, 47 / 101f, 73 / 101f, 0, 86 / 101f, 101, 101);
			default -> {
				Draw.rect(g, PV_INVENTORIES, guiLeft + 365, guiTop + 100, 45, 86, 0, 45 / 101f, 0, 86 / 101f, 101, 101);
				startX = guiLeft + 365 + 5;
			}
		}
		int startY = guiTop + 100 + 8;
		boolean isFive = size == 5;
		int slotsToDraw = isFive ? 8 : size;
		for (int i = 0; i < slotsToDraw; i++) {
			int column = i / 4, row = i % 4;
			int x = startX + column * 19;
			int y = startY + row * 18;
			if (i < size) {
				PvItem item = selectedItem.get(i);
				g.item(item.stack(), x, y);
				if (mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16) screen.tooltipToDisplay = PvWidgets.itemTooltip(item);
			} else {
				g.item(new ItemStack(Items.BARRIER), x, y);
			}
		}

		MuseumData.Saved itemData = data.saved.get(currentItemSelected);
		if (itemData == null) return;
		String donationStatus = itemData.borrowing() ? "§eBorrowing" : "§aIn Museum";
		Draw.centered(g, "§9Donated", guiLeft + 391, guiTop + 35, true);
		Draw.centered(g, "§f" + timeSince(itemData.donatedTime()), guiLeft + 391, guiTop + 47, true);
		Draw.centered(g, "§9Currently", guiLeft + 391, guiTop + 70, true);
		Draw.centered(g, donationStatus, guiLeft + 391, guiTop + 82, true);
	}

	/** NEU Utils.timeSinceMillisecond, minus its off-by-one on days (it used the day-of-year of the difference). */
	static String timeSince(long time) {
		long diff = System.currentTimeMillis() - time;
		if (diff < 60_000L) return Math.max(0, diff / 1000) + " seconds ago";
		if (diff < 3_600_000L) return diff / 60_000 + " minutes ago";
		if (diff < 86_400_000L) return diff / 3_600_000 + " hours ago";
		if (diff < 31_556_952_000L) return diff / 86_400_000 + " days ago";
		return DateTimeFormatter.ofPattern("dd-MM-yyyy").format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
	}

	private static String displayName(String id) {
		return J.str(NeuRepo.item(id), "displayname", id);
	}

	private ItemStack placeholder(String name, boolean higherTier) {
		return placeholderStacks.computeIfAbsent((higherTier ? "+" : "-") + name, k -> {
			var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(higherTier ? "lime_dye" : "gray_dye"));
			return LegacyItems.icon(item, name);
		});
	}

	/** Simplified NEU doesStackMatchSearch: every word must appear in the name, lore or id. */
	private static boolean matches(String name, List<String> lore, String id, String search) {
		StringBuilder sb = new StringBuilder();
		sb.append(Fmt.stripColor(name == null ? "" : name)).append('\n').append(id).append('\n');
		for (String l : lore) sb.append(Fmt.stripColor(l)).append('\n');
		String hay = sb.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
		for (String word : search.replace('_', ' ').split("\\s+")) if (!word.isEmpty() && !hay.contains(word)) return false;
		return true;
	}

	private void setPage(String pageName) {
		selectedMuseumCategory = pageName;
		onPage = 1;
		if (data == null) {
			pages = 0;
			return;
		}
		int count = pageName.equals("special") ? data.special.size() : data.categoryItems.getOrDefault(pageName, List.of()).size();
		pages = Math.max(1, (int) Math.ceil(count / 28.0));
	}

	private static void playPressSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (data == null || !data.ok()) return false;
		if (button == 0 || button == 1) {
			for (Slot s : slots) {
				if (mouseX >= s.x && mouseX <= s.x + 16 && mouseY >= s.y && mouseY <= s.y + 16 && data.saved.containsKey(s.id)) {
					currentItemSelected = s.id;
					selectedItem = s.items;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (data == null || !data.ok()) return;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		List<String> cats = allCategories();
		boolean compact = cats.size() > 4;
		for (int xIndex = 0; xIndex < cats.size(); xIndex++) {
			int bx = compact ? guiLeft + 8 + 17 * xIndex : guiLeft + 16 + 34 * xIndex;
			int by = compact ? guiTop + 174 : guiTop + 172;
			int bs = compact ? 16 : 20;
			if (mouseX > bx && mouseX < bx + bs && mouseY > by && mouseY < by + bs) {
				setPage(cats.get(xIndex));
				playPressSound();
				return;
			}
		}
		if (mouseY > guiTop + PAGE_ARROWS_HEIGHT && mouseY < guiTop + PAGE_ARROWS_HEIGHT + 16
			&& mouseX > guiLeft + 251 - 12 && mouseX < guiLeft + 251 + 12) {
			if (mouseX < guiLeft + 251) {
				if (onPage > 1) onPage--;
			} else if (onPage < pages) {
				onPage++;
			}
		}
	}

	@Override
	public boolean keyPressed(int keyCode) {
		// Only reached when no text field is focused (NEU also typed the digit into the search bar).
		if (data == null || !data.ok()) return false;
		List<String> cats = allCategories();
		int idx = -1;
		if (keyCode >= 49 && keyCode <= 57) idx = keyCode - 49;
		else if (keyCode >= 321 && keyCode <= 329) idx = keyCode - 321;
		if (idx < 0 || idx >= cats.size()) return false;
		setPage(cats.get(idx));
		playPressSound();
		return true;
	}

	@Override
	public void resetCache() {
		data = null;
		parsedFrom = null;
		parsedRepo = null;
		pages = 0;
		onPage = 1;
		currentItemSelected = null;
		selectedItem = null;
	}
}
