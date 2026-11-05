/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates SacksPage.kt, Copyright (C) 2023-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.Prices;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sub-page of the Storage tab: sack contents with bazaar / NPC values (NEU SacksPage). */
public class InventoriesSacksPage {
	private static final Identifier PV_SACKS = Draw.tex("gui/pv_sacks.png");
	private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
	private static final Pattern SACK_PATTERN = Pattern.compile("^RUNE_(?<name>\\w+)_(?<tier>\\d)$");

	private static final int ARROWS_HEIGHT = 180;
	private static final int ARROWS_X_POS = 110;
	private static final int COLUMNS = 7;
	private static final int ROWS = 4;
	private static final int PAGE_SIZE = COLUMNS * ROWS;
	private static final int SACK_ARRAY_LEFT = 168;
	private static final int SACK_ARRAY_TOP = 20;
	private static final int SACK_GRID_X_SIZE = 37;
	private static final int SACK_GRID_Y_SIZE = 41;
	private static final int ITEM_ICON_SIZE = 20;

	private enum PriceSource { Bazaar, NPC }

	private enum SortMode { Value, Quantity }

	private record SackInfo(int itemCount, double sackValue) {}

	private record SackItem(int amount, double value) {}

	private final ProfileViewerScreen screen;

	private PriceSource currentPriceSource = PriceSource.Bazaar;
	private SortMode currentSortMode = SortMode.Value;
	private String currentSack = "All";
	private int page = 0;
	private int maxPage = 0;

	private SkyblockProfiles.SkyblockProfile currentProfile;
	private long lastDataTime;
	private JsonObject npcPricesSource;
	private final Map<String, Double> npcPrices = new HashMap<>();
	/** sacks.json "sacks", sorted by the current sort mode (NEU sortSackTypesList). */
	private List<Map.Entry<String, JsonElement>> sackTypes = new ArrayList<>();
	private final Map<String, SackInfo> sackContents = new HashMap<>();
	private final Map<String, SackItem> sackItems = new HashMap<>();
	private final List<String> playerRunes = new ArrayList<>();
	private static ItemStack bazaarIcon, npcIcon, valueIcon, quantityIcon;

	InventoriesSacksPage(ProfileViewerScreen screen) {
		this.screen = screen;
	}

	private static void initIcons() {
		if (bazaarIcon != null) return;
		// Bazaar NPC head (NEU ItemUtils.createSkullItemStack with this texture hash)
		String hash = "c232e3820897429157619b0ee099fec0628f602fff12b695de54aef11d923ad7";
		String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}";
		bazaarIcon = LegacyItems.skull("§aBazaar", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
		// ItemUtils.getCoinItemStack(100000.0)
		npcIcon = LegacyItems.skull("§r§6100,000 Coins", "eyJ0aW1lc3RhbXAiOjE2MzU5NTczOTM4MDMsInByb2ZpbGVJZCI6ImJiN2NjYTcxMDQzNDQ0MTI4ZDMwODllMTNiZGZhYjU5IiwicHJvZmlsZU5hbWUiOiJsYXVyZW5jaW8zMDMiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2M5Yjc3OTk5ZmVkM2EyNzU4YmZlYWYwNzkzZTUyMjgzODE3YmVhNjQwNDRiZjQzZWYyOTQzM2Y5NTRiYjUyZjYiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQo=");
		valueIcon = new ItemStack(Items.GOLD_INGOT);
		quantityIcon = new ItemStack(Items.HOPPER);
	}

	private JsonObject sacksJson() {
		return NeuRepo.constant("sacks");
	}

	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		initIcons();
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.full(g, PV_SACKS, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
		List<Component> tooltip = null;

		SkyblockProfiles.SkyblockProfile newProfile = screen.getSelectedProfile();
		if (newProfile == null) {
			Draw.centered(g, "§cMissing Profile Data", guiLeft + 250, guiTop + 101, true);
			return;
		}
		if (sacksJson() == null) {
			Draw.centered(g, "§cMissing Repo Data", guiLeft + 250, guiTop + 101, true);
			return;
		}
		// NPC prices come from Hypixel's public item list (NEU HypixelItemAPI), loaded once per player
		SkyblockProfiles.Fetch items = screen.getProfile().fetchUrl(ITEMS_URL);
		boolean newNpcPrices = items.state() == SkyblockProfiles.FetchState.OK && items.json() != npcPricesSource;
		if (newNpcPrices) loadNpcPrices(items.json());
		// NEU computed the values once; here they are refreshed while the bazaar / NPC prices arrive
		long now = System.currentTimeMillis();
		if (newProfile != currentProfile || newNpcPrices || now - lastDataTime > 2000) {
			getData(newProfile);
			if (newProfile != currentProfile) {
				page = 0;
				maxPage = getPages(currentSack);
			}
			currentProfile = newProfile;
			lastDataTime = now;
		}

		SackInfo currentSackData = sackContents.get(currentSack);
		if (currentSackData == null) {
			Draw.centered(g, "§cApi Info Missing", guiLeft + 250, guiTop + 101, true);
			return;
		}

		String name = currentSack.equals("All") ? "§2All Sacks" : "§2" + currentSack + " Sack";
		InventoriesUi.shadowedString(g, name, guiLeft + 78, guiTop + 74, 105);
		Draw.aligned(g, "§6Value", "§f" + Fmt.number((long) currentSackData.sackValue()), guiLeft + 27, guiTop + 91, 102);
		Draw.aligned(g, "§2Items", "§f" + Fmt.number(currentSackData.itemCount()), guiLeft + 27, guiTop + 108, 102);

		int startIndex = page * PAGE_SIZE;
		int endIndex = (page + 1) * PAGE_SIZE;
		if (currentSack.equals("All")) {
			for (int index = 0; index < sackTypes.size(); index++) {
				if (index < startIndex || index >= endIndex) continue;
				Map.Entry<String, JsonElement> entry = sackTypes.get(index);
				String sackName = entry.getKey();
				int adjustedIndex = index - startIndex;
				int x = guiLeft + SACK_ARRAY_LEFT + (adjustedIndex % COLUMNS) * SACK_GRID_X_SIZE;
				int y = guiTop + SACK_ARRAY_TOP + (adjustedIndex / COLUMNS) * SACK_GRID_Y_SIZE;
				String sackItemName = J.str(entry.getValue(), "item", null);
				if (sackItemName == null) continue;

				InventoriesUi.frame(g, x, y);
				SackInfo sackInfo = sackContents.getOrDefault(sackName, new SackInfo(0, 0.0));
				Draw.centered(g, "§6" + Fmt.shortNumber(Math.round(sackInfo.sackValue())), x + ITEM_ICON_SIZE / 2f, y - 4, true);
				Draw.centered(g, "§7" + Fmt.shortNumber(sackInfo.itemCount()), x + ITEM_ICON_SIZE / 2f, y + 26, true);
				ItemStack itemStack = InventoriesPage.repoIcon(sackItemName);
				if (itemStack != null) {
					g.item(itemStack, x + 2, y + 2);
					if (mouseX > x && mouseX < x + ITEM_ICON_SIZE && mouseY > y && mouseY < y + ITEM_ICON_SIZE) {
						tooltip = createTooltip(sackName + " Sack", sackInfo.sackValue(), sackInfo.itemCount(), true);
					}
				}
			}
		} else {
			List<String> sackItemNames = currentSackItems();
			for (int index = 0; index < sackItemNames.size(); index++) {
				if (index < startIndex || index >= endIndex) continue;
				String itemName = sackItemNames.get(index);
				int adjustedIndex = index - startIndex;
				int x = guiLeft + SACK_ARRAY_LEFT + (adjustedIndex % COLUMNS) * SACK_GRID_X_SIZE;
				int y = guiTop + SACK_ARRAY_TOP + (adjustedIndex / COLUMNS) * SACK_GRID_Y_SIZE;
				SackItem itemInfo = sackItems.getOrDefault(itemName, new SackItem(0, 0.0));

				InventoriesUi.frame(g, x, y);
				Draw.centered(g, "§6" + Fmt.shortNumber(Math.round(itemInfo.value())), x + ITEM_ICON_SIZE / 2f, y - 4, true);
				Draw.centered(g, "§7" + Fmt.shortNumber(itemInfo.amount()), x + 10, y + 26, true);
				ItemStack itemStack = InventoriesPage.repoIcon(itemName);
				if (itemStack != null) {
					g.item(itemStack, x + 2, y + 2);
					if (mouseX > x && mouseX < x + ITEM_ICON_SIZE && mouseY > y && mouseY < y + ITEM_ICON_SIZE) {
						PvItem repo = LegacyItems.fromRepo(itemName);
						String stackName = repo != null ? repo.displayName : itemStack.getHoverName().getString();
						tooltip = createTooltip(stackName, itemInfo.value(), itemInfo.amount(), false);
					}
				}
			}
			int bx = guiLeft + SACK_ARRAY_LEFT, by = guiTop + ARROWS_HEIGHT;
			InventoriesUi.floatingRect(g, bx, by, 80, 15, 100, true);
			InventoriesUi.shadowedString(g, "§2Back", bx + 40, by + 3, 79);
		}

		List<Component> buttonTooltip = renderPriceSourceAndSortButtons(g, guiLeft, guiTop, mouseX, mouseY);
		if (buttonTooltip != null) tooltip = buttonTooltip;

		InventoriesUi.arrowPages(g, guiLeft, guiTop, SACK_ARRAY_LEFT + ARROWS_X_POS, ARROWS_HEIGHT, page, maxPage + 1, mouseX, mouseY);

		if (tooltip != null) {
			List<Component> grey = new ArrayList<>();
			for (Component c : tooltip) grey.add(Component.literal("§7" + c.getString()));
			screen.tooltipToDisplay = grey;
		}
	}

	private List<String> currentSackItems() {
		List<String> names;
		if (currentSack.equals("Rune")) {
			names = new ArrayList<>(playerRunes);
		} else {
			names = new ArrayList<>();
			for (JsonElement e : J.arrOrEmpty(J.obj(sacksJson(), "sacks." + currentSack), "contents")) names.add(e.getAsString());
		}
		names.sort(Comparator.comparingDouble((String it) -> {
			SackItem info = sackItems.getOrDefault(it, new SackItem(0, 0.0));
			return currentSortMode == SortMode.Value ? info.value() : info.amount();
		}).reversed());
		return names;
	}

	private List<Component> renderPriceSourceAndSortButtons(GuiGraphicsExtractor g, int guiLeft, int guiTop, int mouseX, int mouseY) {
		List<Component> tooltip = null;
		int px = guiLeft + 54, py = guiTop + 155;
		InventoriesUi.itemButton(g, px, py, currentPriceSource == PriceSource.Bazaar ? bazaarIcon : npcIcon);
		if (inRect(mouseX, mouseY, px, py)) {
			tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§6Select price source"));
			tooltip.addAll(enumTooltip(PriceSource.values(), currentPriceSource));
		}
		int sx = guiLeft + 76, sy = guiTop + 155;
		InventoriesUi.itemButton(g, sx, sy, currentSortMode == SortMode.Value ? valueIcon : quantityIcon);
		if (inRect(mouseX, mouseY, sx, sy)) {
			tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§6Select sorting mode"));
			tooltip.addAll(enumTooltip(SortMode.values(), currentSortMode));
		}
		return tooltip;
	}

	/** java.awt.Rectangle.contains for a 20x20 button. */
	private static boolean inRect(double mx, double my, int x, int y) {
		return mx >= x && mx < x + 20 && my >= y && my < y + 20;
	}

	private static <T extends Enum<T>> List<Component> enumTooltip(T[] values, T current) {
		List<Component> tooltip = new ArrayList<>();
		for (T v : values) tooltip.add(Component.literal(" " + (v == current ? "§2> " + v.name() : v.name())));
		tooltip.add(Component.empty());
		tooltip.add(Component.literal("§eClick to switch!"));
		return tooltip;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (sacksJson() == null || sackContents.isEmpty()) return false;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();

		if (currentSack.equals("All")) {
			int startIndex = page * PAGE_SIZE;
			int endIndex = (page + 1) * PAGE_SIZE;
			for (int index = 0; index < sackTypes.size(); index++) {
				if (index < startIndex || index >= endIndex) continue;
				int adjustedIndex = index - startIndex;
				int x = guiLeft + SACK_ARRAY_LEFT + (adjustedIndex % COLUMNS) * SACK_GRID_X_SIZE;
				int y = guiTop + SACK_ARRAY_TOP + (adjustedIndex / COLUMNS) * SACK_GRID_Y_SIZE;
				if (mouseX > x && mouseX < x + ITEM_ICON_SIZE && mouseY > y && mouseY < y + ITEM_ICON_SIZE) {
					currentSack = sackTypes.get(index).getKey();
					InventoriesUi.playPressSound();
					page = 0;
					maxPage = getPages(currentSack);
					return true;
				}
			}
		} else {
			int bx = guiLeft + SACK_ARRAY_LEFT, by = guiTop + ARROWS_HEIGHT;
			if (mouseX >= bx && mouseX < bx + 80 && mouseY >= by && mouseY < by + 15) {
				currentSack = "All";
				InventoriesUi.playPressSound();
				page = 0;
				maxPage = getPages(currentSack);
				return true;
			}
		}

		if (inRect(mouseX, mouseY, guiLeft + 54, guiTop + 155)) {
			currentPriceSource = PriceSource.values()[(currentPriceSource.ordinal() + 1) % PriceSource.values().length];
			InventoriesUi.playPressSound();
			getData(screen.getSelectedProfile());
			return true;
		}
		if (inRect(mouseX, mouseY, guiLeft + 76, guiTop + 155)) {
			currentSortMode = SortMode.values()[(currentSortMode.ordinal() + 1) % SortMode.values().length];
			InventoriesUi.playPressSound();
			getData(screen.getSelectedProfile());
			return true;
		}
		return InventoriesUi.arrowPagesClick(guiLeft, guiTop, SACK_ARRAY_LEFT + ARROWS_X_POS, ARROWS_HEIGHT, page, maxPage + 1,
			mouseX, mouseY, p -> page = p);
	}

	private static List<Component> createTooltip(String name, double value, int amount, boolean isSack) {
		List<Component> list = new ArrayList<>();
		list.add(Component.literal("§2" + name));
		list.add(Component.literal("Items Stored: §a" + Fmt.number(amount)));
		list.add(Component.literal("Total Value: §6" + Fmt.number((long) value)));
		if (isSack) list.add(Component.literal("§eClick for more details"));
		return list;
	}

	/** Number of pages - 1. NEU used size / pageSize, which showed an empty extra page for exactly 28 entries. */
	private int getPages(String pageName) {
		int size;
		if (pageName.equals("All")) size = sackTypes.size();
		else if (pageName.equals("Rune")) size = playerRunes.size();
		else size = J.arrOrEmpty(J.obj(sacksJson(), "sacks." + pageName), "contents").size();
		return Math.max(0, (size - 1) / PAGE_SIZE);
	}

	private void getData(SkyblockProfiles.SkyblockProfile profile) {
		sackContents.clear();
		sackItems.clear();
		playerRunes.clear();
		Prices.refreshIfNeeded();
		JsonObject types = J.obj(sacksJson(), "sacks");
		if (types == null || profile == null) return;
		JsonObject sacksInfo = J.objOrEmpty(profile.getProfileJson(), "inventory.sacks_counts");

		double totalValue = 0.0;
		int totalItems = 0;
		for (Map.Entry<String, JsonElement> e : types.entrySet()) {
			String sackName = e.getKey();
			if (!e.getValue().isJsonObject()) return;
			JsonObject data = e.getValue().getAsJsonObject();
			if (sackName.equals("Rune")) {
				totalItems += getRuneData(sacksInfo);
				continue;
			}
			JsonArray contents = J.arr(data, "contents");
			if (contents == null) return;
			double sackValue = 0.0;
			int sackItemCount = 0;
			for (JsonElement item : contents) {
				if (!item.isJsonPrimitive()) return;
				String sackItem = item.getAsString();
				String adjustedName = sackItem.replace("-", ":");
				int itemCount = sacksInfo.has(adjustedName) ? sacksInfo.get(adjustedName).getAsInt() : 0;
				double itemValue = itemCount * getPrice(sackItem);
				if (!sackItems.containsKey(sackItem)) {
					totalValue += itemValue;
					totalItems += itemCount;
				}
				sackItems.put(sackItem, new SackItem(itemCount, itemValue));
				sackValue += itemValue;
				sackItemCount += itemCount;
			}
			sackContents.put(sackName, new SackInfo(sackItemCount, sackValue));
		}

		// NEU sortSackTypesList
		List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(types.entrySet());
		sorted.sort(Comparator.comparingDouble((Map.Entry<String, JsonElement> en) -> {
			SackInfo sack = sackContents.getOrDefault(en.getKey(), new SackInfo(0, 0.0));
			return currentSortMode == SortMode.Value ? sack.sackValue() : sack.itemCount();
		}).reversed());
		sackTypes = sorted;

		sackContents.put("All", new SackInfo(totalItems, totalValue));
	}

	/** NEU used bazaar ids with "-" (INK_SACK-3) while Hypixel's bazaar and item list use ":" (INK_SACK:3). */
	private double getPrice(String itemName) {
		String apiId = itemName.replace("-", ":");
		double npcPrice = npcPrices.getOrDefault(apiId, 0.0);
		if (currentPriceSource == PriceSource.NPC) return npcPrice;
		double buy = Prices.bazaarBuy(apiId);
		if (buy < 0) return npcPrice;
		return Math.max(buy, Prices.price(apiId));
	}

	private void loadNpcPrices(JsonObject json) {
		npcPricesSource = json;
		npcPrices.clear();
		for (JsonElement e : J.arrOrEmpty(json, "items")) {
			if (!e.isJsonObject()) continue;
			JsonObject o = e.getAsJsonObject();
			if (o.has("id") && o.has("npc_sell_price")) npcPrices.put(o.get("id").getAsString(), o.get("npc_sell_price").getAsDouble());
		}
	}

	private int getRuneData(JsonObject sacksInfo) {
		int sackItemCount = 0;
		for (Map.Entry<String, JsonElement> e : sacksInfo.entrySet()) {
			if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) continue;
			Matcher m = SACK_PATTERN.matcher(e.getKey());
			if (!m.matches()) continue;
			int itemAmount = e.getValue().getAsInt();
			String neuInternalName = m.group("name") + "_RUNE;" + m.group("tier");
			sackItemCount += itemAmount;
			sackItems.put(neuInternalName, new SackItem(itemAmount, itemAmount));
			playerRunes.add(neuInternalName);
		}
		sackContents.put("Rune", new SackInfo(sackItemCount, sackItemCount));
		return sackItemCount;
	}

	public void resetCache() {
		currentProfile = null;
		currentSack = "All";
		page = 0;
		maxPage = 0;
	}

}
