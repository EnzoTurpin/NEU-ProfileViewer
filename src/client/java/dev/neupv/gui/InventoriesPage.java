/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates InventoriesPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.Prices;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The "Storage" tab: inventories on the main page, sacks on the second page. */
public class InventoriesPage extends PvPage {
	private static final Identifier PV_INVS = Draw.tex("gui/pv_invs.png");
	private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final Pattern FISHING_SPEED_PATTERN = Pattern.compile("^Fishing Speed: \\+(\\d+)");
	private static final String[] RARITY_ARR = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "VERY SPECIAL", "DIVINE"};

	public static final Map<String, String> API_STAT_NAMES = new LinkedHashMap<>();
	public static final Map<String, Float> TUNING_COEFFICIENTS = new HashMap<>();
	private static final List<String> TUNING_STATS = List.of("health", "defense", "walk_speed", "strength", "critical_damage",
		"critical_chance", "attack_speed", "intelligence");

	static {
		API_STAT_NAMES.put("health", "§c❤ Health");
		API_STAT_NAMES.put("defense", "§a❈ Defense");
		API_STAT_NAMES.put("walk_speed", "§f✦ Speed");
		API_STAT_NAMES.put("strength", "§c❁ Strength");
		API_STAT_NAMES.put("critical_damage", "§9☠ Crit Damage");
		API_STAT_NAMES.put("critical_chance", "§9☣ Crit Chance");
		API_STAT_NAMES.put("attack_speed", "§e⚔ Bonus Attack Speed");
		API_STAT_NAMES.put("intelligence", "§b✎ Intelligence");
		TUNING_COEFFICIENTS.put("health", 5f);
		TUNING_COEFFICIENTS.put("defense", 1f);
		TUNING_COEFFICIENTS.put("walk_speed", 1.5f);
		TUNING_COEFFICIENTS.put("strength", 1f);
		TUNING_COEFFICIENTS.put("critical_damage", 1f);
		TUNING_COEFFICIENTS.put("critical_chance", 0.2f);
		TUNING_COEFFICIENTS.put("attack_speed", 0.3f);
		TUNING_COEFFICIENTS.put("intelligence", 2f);
	}

	/** NEU invNameToDisplayMap order (also the 1-8 hotkeys). */
	private static final String[] INV_KEYS = {"inv_contents", "ender_chest_contents", "backpack_contents",
		"personal_vault_contents", "talisman_bag", "wardrobe_contents", "potion_bag"};
	private static Map<String, ItemStack> invNameToDisplayMap;
	private static ItemStack fillerStack, inventoriesIcon, sacksIcon;

	/** One slot of a displayed inventory: either an API item or NEU's black glass "filler" (slot past the API data). */
	private record Cell(PvItem item, boolean filler) {
		ItemStack stack() {
			return filler ? fillerStack : item.stack();
		}
	}

	private Map<String, Cell[][][]> inventoryItems = new HashMap<>();
	private PvItem[] bestWeapons, bestRods, armorItems, equipmentItems;
	private String selectedInventory = "inv_contents";
	private int currentInventoryIndex = 0;
	private int arrowCount = -1, greenCandyCount = -1, purpleCandyCount = -1;
	private static final Map<String, ItemStack> repoIcons = new HashMap<>();

	private final InventoriesSacksPage sacksPage;

	public InventoriesPage(ProfileViewerScreen screen) {
		super(screen);
		this.sacksPage = new InventoriesSacksPage(screen);
	}

	/** GLFW key codes of "1" and keypad "1". */
	private static final int KEY_1 = 49, KEY_KP_1 = 321;

	private static net.minecraft.world.item.Item byId(String id) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}

	private static ItemStack renamed(String repoId, net.minecraft.world.item.Item fallback, String name) {
		PvItem repo = LegacyItems.fromRepo(repoId);
		ItemStack s = repo != null ? repo.stack().copy() : new ItemStack(fallback);
		// CUSTOM_NAME: a player head's ITEM_NAME is overridden by its profile name ("neupv's Head")
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return s;
	}

	private static void initIcons() {
		if (invNameToDisplayMap != null) return;
		Map<String, ItemStack> m = new LinkedHashMap<>();
		m.put("inv_contents", LegacyItems.icon(Items.CHEST, "§7Inventory"));
		m.put("ender_chest_contents", LegacyItems.icon(Items.ENDER_CHEST, "§7Ender Chest"));
		m.put("backpack_contents", renamed("JUMBO_BACKPACK", Items.PLAYER_HEAD, "§7Backpacks"));
		m.put("personal_vault_contents", renamed("IRON_CHEST", Items.PLAYER_HEAD, "§7Personal Vault"));
		m.put("talisman_bag", LegacyItems.icon(Items.GOLDEN_APPLE, "§7Accessory Bag"));
		m.put("wardrobe_contents", LegacyItems.icon(Items.LEATHER_CHESTPLATE, "§7Wardrobe"));
		m.put("potion_bag", LegacyItems.icon(Items.POTION, "§7Potion Bag"));
		invNameToDisplayMap = m;
		fillerStack = new ItemStack(byId("black_stained_glass_pane"));
		inventoriesIcon = LegacyItems.icon(Items.PAINTING, "§7Inventories");
		sacksIcon = renamed("LARGE_ENCHANTED_MINING_SACK", Items.PLAYER_HEAD, "§7Sacks");
	}

	static ItemStack repoIcon(String id) {
		return repoIcons.computeIfAbsent(id, k -> {
			PvItem item = LegacyItems.fromRepo(k);
			return item == null ? null : item.stack();
		});
	}

	@Override
	public boolean hasSideButtons() {
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		initIcons();
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();

		PvWidgets.sideButtons(screen, g, inventoriesIcon, sacksIcon, mouseX, mouseY);

		if (ProfileViewerScreen.isOnSecondPage()) {
			// the search field belongs to the inventories page only: park it out of sight
			EditBox field = screen.pageField(this, "search", -10000, -10000, 88, 20);
			field.setFocused(false);
			sacksPage.draw(g, mouseX, mouseY, partialTicks);
			return;
		}

		Draw.full(g, PV_INVS, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
		EditBox searchField = screen.pageField(this, "search", guiLeft + 19, guiTop + ProfileViewerScreen.SIZE_Y - 26 - 20, 88, 20);

		SkyblockProfiles.SkyblockProfile selectedProfile = getSelectedProfile();
		if (selectedProfile == null) return;
		Map<String, List<PvItem>> inventoryInfo = selectedProfile.getInventoryInfo();

		drawInventorySelector(g, guiLeft, guiTop, mouseX, mouseY, selectedProfile);

		// armor (boots at the bottom) and equipment
		if (armorItems == null) armorItems = firstFour(inventoryInfo.get("inv_armor"));
		for (int i = 0; i < armorItems.length; i++) {
			drawSideItem(g, armorItems[i], guiLeft + 173, guiTop + 67 - 18 * i, mouseX, mouseY);
		}
		if (equipmentItems == null) equipmentItems = firstFour(inventoryInfo.get("equipment_contents"));
		for (int i = 0; i < equipmentItems.length; i++) {
			drawSideItem(g, equipmentItems[i], guiLeft + 192, guiTop + 13 + 18 * i, mouseX, mouseY);
		}

		Cell[][][] inventories = getItemsForInventory(inventoryInfo, selectedInventory);
		if (currentInventoryIndex >= inventories.length) currentInventoryIndex = inventories.length - 1;
		if (currentInventoryIndex < 0) currentInventoryIndex = 0;
		Cell[][] inventory = inventories[currentInventoryIndex];

		// best weapons / rods
		if (bestWeapons == null) {
			bestWeapons = findBestItems(inventoryInfo, 6, new String[]{"inv_contents", "ender_chest_contents"}, new String[]{"SWORD", "BOW"});
		}
		if (bestRods == null) {
			bestRods = findBestItems(inventoryInfo, 3, new String[]{"inv_contents", "ender_chest_contents"},
				new String[]{"FISHING ROD", "FISHING WEAPON"}, FISHING_SPEED_PATTERN);
		}
		for (int i = 0; i < bestWeapons.length; i++) drawSideItem(g, bestWeapons[i], guiLeft + 143, guiTop + 13 + 18 * i, mouseX, mouseY);
		for (int i = 0; i < bestRods.length; i++) drawSideItem(g, bestRods[i], guiLeft + 143, guiTop + 137 + 18 * i, mouseX, mouseY);

		// quiver arrows and candies
		if (arrowCount == -1) arrowCount = countItemsInInventory("ARROW", inventoryInfo, false, "quiver");
		if (greenCandyCount == -1) greenCandyCount = countItemsInInventory("GREEN_CANDY", inventoryInfo, true, "candy_inventory_contents");
		if (purpleCandyCount == -1) purpleCandyCount = countItemsInInventory("PURPLE_CANDY", inventoryInfo, true, "candy_inventory_contents");
		drawWithText(g, repoIcon("ARROW"), Items.ARROW, guiLeft + 173, guiTop + 101, arrowCount > 999 ? Fmt.shortNumber(arrowCount) : String.valueOf(arrowCount));
		drawWithText(g, repoIcon("GREEN_CANDY"), byId("lime_dye"), guiLeft + 173, guiTop + 119, String.valueOf(greenCandyCount));
		drawWithText(g, repoIcon("PURPLE_CANDY"), byId("purple_dye"), guiLeft + 173, guiTop + 137, String.valueOf(purpleCandyCount));
		if (mouseX > guiLeft + 173 && mouseX < guiLeft + 173 + 16 && mouseY > guiTop + 101 && mouseY < guiTop + 137 + 16) {
			if (mouseY < guiTop + 101 + 17) {
				InventoriesQuiverInfo quiverInfo = InventoriesQuiverInfo.of(inventoryInfo, selectedProfile.getProfileJson());
				screen.tooltipToDisplay = quiverInfo == null ? PvWidgets.lines("§cError checking Quiver") : quiverInfo.generateProfileViewerTooltip();
			} else if (mouseY < guiTop + 119 + 17) {
				screen.tooltipToDisplay = PvWidgets.lines("§aGreen Candy §7x" + greenCandyCount);
			} else {
				screen.tooltipToDisplay = PvWidgets.lines("§5Purple Candy §7x" + purpleCandyCount);
			}
		}

		if (inventory == null) {
			String strToRender = "Inventory API not enabled!";
			// The member json says which inventories Hypixel actually sent: if the inventory API is on but this one
			// is absent (e.g. wardrobe_contents for some profiles), say so instead of blaming the API setting.
			boolean apiOn = J.get(selectedProfile.getProfileJson(), "inventory.inv_contents") != null;
			if (apiOn && !selectedInventory.equals("backpack_contents")) {
				Draw.centered(g, "§cHypixel's API sent no data", guiLeft + 317, guiTop + 101, true);
				Draw.centered(g, "§cfor this inventory.", guiLeft + 317, guiTop + 112, true);
				return;
			}
			if (selectedInventory.equalsIgnoreCase("personal_vault_contents")) {
				strToRender = "Personal Vault API not enabled!";
			} else if (selectedInventory.equalsIgnoreCase("backpack_contents")) {
				strToRender = "Inventory API not enabled";
				Draw.centered(g, "§cOr has no backpacks!", guiLeft + 317, guiTop + 112, true);
			}
			Draw.centered(g, "§c" + strToRender, guiLeft + 317, guiTop + 101, true);
			return;
		}

		int inventoryRows = inventory.length;
		int invSizeY = inventoryRows * 18 + 17 + 7;
		int x = guiLeft + 320 - 176 / 2;
		int y = guiTop + 101 - invSizeY / 2;
		int staticSelectorHeight = guiTop + 177;

		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHEST_GUI_TEXTURE, x, y, 0, 0, 176, inventoryRows * 18 + 17, 256, 256);
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHEST_GUI_TEXTURE, x, y + inventoryRows * 18 + 17, 0, 215, 176, 7, 256, 256);

		boolean leftHovered = false, rightHovered = false;
		if (Minecraft.getInstance().mouseHandler.isLeftPressed()) {
			if (mouseY > staticSelectorHeight && mouseY < staticSelectorHeight + 16 && mouseX > guiLeft + 320 - 12 && mouseX < guiLeft + 320 + 12) {
				if (mouseX < guiLeft + 320) leftHovered = true;
				else rightHovered = true;
			}
		}
		if (currentInventoryIndex > 0) InventoriesUi.bigLeftArrow(g, guiLeft + 320 - 12, staticSelectorHeight, leftHovered);
		if (currentInventoryIndex < inventories.length - 1) InventoriesUi.bigRightArrow(g, guiLeft + 320, staticSelectorHeight, rightHovered);

		if (inventories.length > 1) {
			String text = "Page " + (currentInventoryIndex + 1) + "/" + inventories.length;
			g.text(Draw.font(), text, guiLeft + 320 - Draw.font().width(text) / 2, staticSelectorHeight - 10, 0xFF949494, false);
		}
		g.text(Draw.font(), Fmt.stripColor(invNameToDisplayMap.get(selectedInventory).getHoverName().getString()), x + 8, y + 6, 0xFF404040, false);

		String search = searchField.getValue();
		boolean searching = search != null && !search.isEmpty();
		PvItem stackToRender = null;
		List<int[]> greyed = new ArrayList<>();
		for (int yIndex = 0; yIndex < inventory.length; yIndex++) {
			if (inventory[yIndex] == null) continue;
			for (int xIndex = 0; xIndex < inventory[yIndex].length; xIndex++) {
				Cell cell = inventory[yIndex][xIndex];
				int sx = x + 8 + xIndex * 18, sy = y + 18 + yIndex * 18;
				if (cell != null) {
					ItemStack stack = cell.stack();
					g.item(stack, sx, sy);
					g.itemDecorations(Draw.font(), stack, sx, sy);
				}
				if (searching && (cell == null || cell.filler() || !InventoriesSearch.matches(cell.item(), search))) {
					greyed.add(new int[]{sx, sy});
				}
				if (cell == null || cell.filler()) continue;
				if (mouseX >= sx && mouseX <= sx + 16 && mouseY >= sy && mouseY <= sy + 16) stackToRender = cell.item();
			}
		}
		// NEU translated z +50 then drew the grey; 26.3 draws GUI elements in submission order, over earlier items
		for (int[] p : greyed) g.fill(p[0], p[1], p[0] + 16, p[1] + 16, 0x64000000);
		if (stackToRender != null) screen.tooltipToDisplay = PvWidgets.itemTooltip(stackToRender);
	}

	private void drawInventorySelector(GuiGraphicsExtractor g, int guiLeft, int guiTop, int mouseX, int mouseY, SkyblockProfiles.SkyblockProfile selectedProfile) {
		int invNameIndex = 0;
		for (Map.Entry<String, ItemStack> entry : invNameToDisplayMap.entrySet()) {
			int x = 19 + 34 * (invNameIndex % 3);
			int y = 26 + 34 * (invNameIndex / 3);
			if (entry.getKey().equals(selectedInventory)) {
				InventoriesUi.pressedFrame(g, guiLeft + x - 2, guiTop + y - 2);
				x++;
				y++;
			} else {
				InventoriesUi.frame(g, guiLeft + x - 2, guiTop + y - 2);
			}
			g.item(entry.getValue(), guiLeft + x, guiTop + y);
			g.itemDecorations(Draw.font(), entry.getValue(), guiLeft + x, guiTop + y, String.valueOf(invNameIndex + 1));

			if (mouseX >= guiLeft + x && mouseX <= guiLeft + x + 16 && mouseY >= guiTop + y && mouseY <= guiTop + y + 16) {
				List<Component> tt = new ArrayList<>();
				tt.add(entry.getValue().getHoverName());
				if (entry.getKey().equals("talisman_bag")) addAccessoryBagInfo(tt, selectedProfile);
				screen.tooltipToDisplay = tt;
			}
			invNameIndex++;
		}
	}

	private static void addAccessoryBagInfo(List<Component> tt, SkyblockProfiles.SkyblockProfile profile) {
		int magicalPower = profile.getMagicalPower();
		tt.add(Component.literal("§8Magical Power: " + (magicalPower == -1 ? "§cError while calculating!" : "§6" + Fmt.number(magicalPower))));
		String selectedPower = getSelectedMagicalPower(profile.getProfileJson());
		tt.add(Component.literal("§8Selected Power: " + (selectedPower == null ? "§cNone!" : "§a" + selectedPower)));
		Map<String, Integer> tuningInfo = getTuningInfo(profile.getProfileJson());
		if (tuningInfo != null && !tuningInfo.isEmpty()) {
			tt.add(Component.empty());
			tt.add(Component.literal("§7Tuning:"));
			DecimalFormat df = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.US));
			tuningInfo.forEach((statName, statPoints) -> {
				if (statPoints != 0) {
					tt.add(Component.literal("  " + API_STAT_NAMES.get(statName) + ": +"
						+ df.format(statPoints * TUNING_COEFFICIENTS.getOrDefault(statName, 1.0f))
						+ "§8 (§e" + statPoints + "§8 points)"));
				}
			});
		}
	}

	/** NEU PlayerStats.getSelectedMagicalPower. */
	static String getSelectedMagicalPower(JsonObject profileInfo) {
		String selectedPower = J.str(profileInfo, "accessory_bag_storage.selected_power", null);
		if (selectedPower == null || selectedPower.isEmpty()) return null;
		return selectedPower.substring(0, 1).toUpperCase(Locale.ROOT) + selectedPower.substring(1);
	}

	/** NEU SkyblockProfile.getTuningInfo (missing stats count as 0 instead of throwing). */
	static Map<String, Integer> getTuningInfo(JsonObject profileJson) {
		if (J.get(profileJson, "accessory_bag_storage.tuning") == null) return null;
		JsonObject tuningData = J.objOrEmpty(profileJson, "accessory_bag_storage.tuning.slot_0");
		if (tuningData.entrySet().isEmpty()) return null;
		Map<String, Integer> tuning = new LinkedHashMap<>();
		for (String stat : TUNING_STATS) tuning.put(stat, J.i(tuningData, stat, 0));
		return tuning;
	}

	private void drawSideItem(GuiGraphicsExtractor g, PvItem item, int x, int y, int mouseX, int mouseY) {
		if (item == null) return;
		g.item(item.stack(), x, y);
		g.itemDecorations(Draw.font(), item.stack(), x, y);
		if (mouseX >= x - 1 && mouseX <= x + 16 + 1 && mouseY >= y - 1 && mouseY <= y + 16 + 1) {
			screen.tooltipToDisplay = PvWidgets.itemTooltip(item);
		}
	}

	private static void drawWithText(GuiGraphicsExtractor g, ItemStack stack, net.minecraft.world.item.Item fallback, int x, int y, String text) {
		if (stack == null) stack = new ItemStack(fallback);
		g.item(stack, x, y);
		g.itemDecorations(Draw.font(), stack, x, y, text);
	}

	private static PvItem[] firstFour(List<PvItem> items) {
		PvItem[] out = new PvItem[4];
		if (items == null) return out;
		for (int i = 0; i < Math.min(4, items.size()); i++) out[i] = items.get(i);
		return out;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		switch (PvWidgets.sideButtonClicked(screen, mouseX, mouseY)) {
			case 1 -> {
				ProfileViewerScreen.setOnSecondPage(false);
				return true;
			}
			case 2 -> {
				ProfileViewerScreen.setOnSecondPage(true);
				return true;
			}
			default -> {
			}
		}
		if (ProfileViewerScreen.isOnSecondPage()) return sacksPage.mouseClicked(mouseX, mouseY, button);
		// NEU switched inventories on mouse release; 26.3 does not reliably deliver releases to the page, so act on press.
		return button == 0 && handleMainClick(mouseX, mouseY);
	}

	private boolean handleMainClick(double mouseX, double mouseY) {
		initIcons();
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int i = 0;
		for (String key : invNameToDisplayMap.keySet()) {
			int x = guiLeft + 19 + 34 * (i % 3);
			int y = guiTop + 26 + 34 * (i / 3);
			if (mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16) {
				selectInventory(key);
				return true;
			}
			i++;
		}

		SkyblockProfiles.SkyblockProfile selectedProfile = getSelectedProfile();
		if (selectedProfile == null) return false;
		Cell[][][] inventories = getItemsForInventory(selectedProfile.getInventoryInfo(), selectedInventory);
		if (currentInventoryIndex >= inventories.length) currentInventoryIndex = inventories.length - 1;
		if (currentInventoryIndex < 0) currentInventoryIndex = 0;
		if (inventories[currentInventoryIndex] == null) return false;

		int staticSelectorHeight = guiTop + 177;
		if (mouseY > staticSelectorHeight && mouseY < staticSelectorHeight + 16 && mouseX > guiLeft + 320 - 12 && mouseX < guiLeft + 320 + 12) {
			// NEU changed page even without an arrow shown (index then clamped); only move when there is a page to go to
			if (mouseX < guiLeft + 320) {
				if (currentInventoryIndex > 0) currentInventoryIndex--;
			} else if (currentInventoryIndex < inventories.length - 1) {
				currentInventoryIndex++;
			}
			return true;
		}
		return false;
	}

	private void selectInventory(String key) {
		if (!selectedInventory.equals(key)) {
			InventoriesUi.playPressSound();
			// NEU kept the page index of the previous inventory (e.g. "ender chest page 3" -> backpack 3)
			currentInventoryIndex = 0;
		}
		selectedInventory = key;
	}

	@Override
	public boolean keyPressed(int keyCode) {
		if (ProfileViewerScreen.isOnSecondPage()) return false;
		int idx = -1;
		if (keyCode >= KEY_1 && keyCode < KEY_1 + INV_KEYS.length) idx = keyCode - KEY_1;
		else if (keyCode >= KEY_KP_1 && keyCode < KEY_KP_1 + INV_KEYS.length) idx = keyCode - KEY_KP_1;
		if (idx < 0) return false;
		selectInventory(INV_KEYS[idx]);
		return true;
	}

	@Override
	public void resetCache() {
		inventoryItems = new HashMap<>();
		bestWeapons = null;
		bestRods = null;
		armorItems = null;
		equipmentItems = null;
		currentInventoryIndex = 0;
		arrowCount = -1;
		greenCandyCount = -1;
		purpleCandyCount = -1;
		sacksPage.resetCache();
	}

	private static int countItemsInInventory(String internalname, Map<String, List<PvItem>> inventoryInfo, boolean specific, String... invsToSearch) {
		int count = 0;
		for (String inv : invsToSearch) {
			List<PvItem> items = inventoryInfo.get(inv);
			if (items == null) continue;
			for (PvItem item : items) {
				if (item == null || item.skyblockId == null) continue;
				if (specific ? item.skyblockId.equals(internalname) : item.skyblockId.contains(internalname)) count += item.count;
			}
		}
		return count;
	}

	/** NEU Utils.checkItemType(lore, contains = true, typeMatches). */
	private static int checkItemType(List<String> lore, String... typeMatches) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i).trim();
			for (String rarity : RARITY_ARR) {
				for (int j = 0; j < typeMatches.length; j++) {
					if (line.contains(rarity + " " + typeMatches[j]) || line.contains(rarity + " DUNGEON " + typeMatches[j])) return j;
				}
			}
		}
		return -1;
	}

	private static PvItem[] findBestItems(Map<String, List<PvItem>> inventoryInfo, int numItems, String[] invsToSearch, String[] typeMatches, Pattern... importantPatterns) {
		Prices.refreshIfNeeded();
		PvItem[] bestItems = new PvItem[numItems];
		TreeMap<Long, Set<PvItem>> map = new TreeMap<>();
		for (String inv : invsToSearch) {
			List<PvItem> items = inventoryInfo.get(inv);
			if (items == null) continue;
			for (PvItem item : items) {
				if (item == null || checkItemType(item.lore, typeMatches) < 0) continue;
				long importance = 0;
				if (importantPatterns.length == 0) {
					importance += (long) Prices.price(item.skyblockId);
					importance += 1;
				} else {
					for (String line : item.lore) {
						for (Pattern pattern : importantPatterns) {
							Matcher matcher = pattern.matcher(Fmt.stripColor(line));
							if (matcher.find()) importance += Integer.parseInt(matcher.group(1));
						}
					}
				}
				map.computeIfAbsent(importance, k -> new LinkedHashSet<>()).add(item);
			}
		}
		int i = 0;
		outer:
		for (long key : map.descendingKeySet()) {
			for (PvItem item : map.get(key)) {
				bestItems[i] = item;
				if (++i >= bestItems.length) break outer;
			}
		}
		return bestItems;
	}

	private Cell[][][] getItemsForInventory(Map<String, List<PvItem>> inventoryInfo, String invName) {
		Cell[][][] cached = inventoryItems.get(invName);
		if (cached != null) return cached;

		List<PvItem> jsonInv = inventoryInfo.getOrDefault(invName, List.of());
		if (jsonInv.isEmpty()) return new Cell[1][][];

		int jsonInvSize;
		if (useActualMax(invName)) {
			jsonInvSize = (int) Math.ceil(jsonInv.size() / 9f) * 9;
		} else {
			jsonInvSize = 9 * 4;
			float divideBy = invName.equals("wardrobe_contents") ? 36f : 9f;
			for (int i = 9 * 4; i < jsonInv.size(); i++) {
				if (jsonInv.get(i) != null) jsonInvSize = (int) (Math.ceil((i + 1) / divideBy) * (int) divideBy);
			}
		}

		int rowSize = 9;
		int rows = jsonInvSize / rowSize;
		int maxRowsPerPage = getRowsForInventory(invName);
		int maxInvSize = rowSize * maxRowsPerPage;

		int numInventories = (jsonInvSize - 1) / maxInvSize + 1;
		List<Integer> backpackSizes = getSelectedProfile().getBackpackSizes();
		boolean backpacks = invName.equals("backpack_contents");
		if (backpacks) numInventories = backpackSizes.size();
		if (numInventories == 0) return new Cell[1][][];

		Cell[][][] inventories = new Cell[numInventories][][];
		int startNumberJ = 0;
		for (int i = 0; i < numInventories; i++) {
			int thisRows = Math.min(maxRowsPerPage, rows - maxRowsPerPage * i);
			int invSize;
			if (backpacks) {
				thisRows = backpackSizes.get(i) / 9;
				invSize = startNumberJ + (thisRows * 9);
				maxInvSize = thisRows * 9;
			} else {
				startNumberJ = maxInvSize * i;
				invSize = Math.min(jsonInvSize, maxInvSize + maxInvSize * i);
			}
			if (thisRows <= 0) break;

			Cell[][] items = new Cell[thisRows][rowSize];
			for (int j = startNumberJ; j < invSize; j++) {
				// NEU used j % maxInvSize, which shifts the slots of a backpack whose start offset is not a multiple of its size
				int slot = j - startNumberJ;
				int xIndex = slot % rowSize;
				int yIndex = slot / rowSize;
				if (invName.equals("inv_contents")) {
					yIndex--;
					if (yIndex < 0) yIndex = rows - 1;
				}
				if (yIndex >= thisRows) break;
				if (j >= jsonInv.size()) {
					items[yIndex][xIndex] = new Cell(null, true);
					continue;
				}
				PvItem item = jsonInv.get(j);
				if (item == null) continue;
				items[yIndex][xIndex] = new Cell(item, false);
			}
			inventories[i] = items;
			if (backpacks) startNumberJ += backpackSizes.get(i);
		}
		inventoryItems.put(invName, inventories);
		return inventories;
	}

	private static boolean useActualMax(String invName) {
		return switch (invName) {
			case "talisman_bag", "fishing_bag", "potion_bag", "personal_vault_contents" -> true;
			default -> false;
		};
	}

	private static int getRowsForInventory(String invName) {
		return switch (invName) {
			case "wardrobe_contents" -> 4;
			case "backpack_contents", "ender_chest_contents" -> 5;
			default -> 6;
		};
	}
}
