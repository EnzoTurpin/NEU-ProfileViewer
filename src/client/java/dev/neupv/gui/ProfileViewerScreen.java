/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates GuiProfileViewer.java, Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import dev.neupv.config.PvConfig;
import dev.neupv.item.LegacyItems;
import dev.neupv.net.UrsaClient;
import dev.neupv.pv.ProfileViewer;
import dev.neupv.pv.SkyblockProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ProfileViewerScreen extends Screen {
	public static final int SIZE_X = 431;
	public static final int SIZE_Y = 202;
	public static final Identifier PV_DROPDOWN = Draw.tex("gui/pv_dropdown.png");
	public static final Identifier PV_BG = Draw.tex("gui/pv_bg.png");
	public static final Identifier PV_ELEMENTS = Draw.tex("gui/pv_elements.png");
	private static final Map<String, Identifier> GAMEMODE_ICONS = Map.of(
		"ironman", Draw.tex("gui/pv_ironman.png"),
		"bingo", Draw.tex("gui/pv_bingo.png"),
		"island", Draw.tex("gui/pv_stranded.png"));
	private static final Identifier GAMEMODE_UNKNOWN = Draw.tex("gui/pv_unknown.png");
	private static final Map<String, ItemStack> HEADS = new HashMap<>();

	public enum Page {
		BASIC(0, Items.PAPER, "§9Skills"),
		DUNGEON(1, Items.DEAD_BUSH, "§eDungeoneering"),
		EXTRA(2, Items.BOOK, "§7Profile Stats"),
		INVENTORIES(3, Items.ENDER_CHEST, "§bStorage"),
		COLLECTIONS(4, Items.PAINTING, "§6Collections"),
		PETS(5, Items.BONE, "§aPets"),
		MINING(6, Items.IRON_PICKAXE, "§5Heart of the Mountain"),
		BINGO(7, Items.FILLED_MAP, "§bBingo"),
		TROPHY_FISH(8, Items.FISHING_ROD, "§3Trophy Fish"),
		BESTIARY(9, Items.IRON_SWORD, "§cBestiary"),
		CRIMSON_ISLE(10, Items.NETHERRACK, "§4Crimson Isle"),
		MUSEUM(11, Items.LEATHER_CHESTPLATE, "§6Museum"),
		RIFT(12, Items.ENDER_EYE, "§5Rift"),
		GARDEN(13, Items.GRASS_BLOCK, "§2Garden");

		public final int id;
		public final String name;
		private final Item item;
		private ItemStack stack;

		Page(int id, Item item, String name) {
			this.id = id;
			this.item = item;
			this.name = name;
		}

		public ItemStack stack() {
			if (stack == null) stack = LegacyItems.icon(item, name);
			return stack;
		}

		static Page byId(int id) {
			for (Page p : values()) if (p.id == id) return p;
			return null;
		}
	}

	/** Remembered between openings, like NEU. */
	public static Page currentPage = Page.BASIC;
	private static boolean onSecondPage;

	private final SkyblockProfiles profile;
	private String profileName;
	private final Map<Page, PvPage> pages = new EnumMap<>(Page.class);
	private CrashPage crashPage;
	private EditBox searchBox;
	private String searchMessage;
	private boolean searching;
	private boolean profileDropdownSelected;
	private boolean showBingoPage;
	private int guiLeft, guiTop;
	public final long startTime = System.currentTimeMillis();
	public float backgroundRotation = 0;
	public long currentTime, lastTime;
	public List<Component> tooltipToDisplay;

	public ProfileViewerScreen(SkyblockProfiles profile) {
		super(Component.literal("Profile Viewer"));
		this.profile = profile;
		this.profileName = profile.getLatestProfileName();
		try {
			pages.put(Page.BASIC, new BasicPage(this));
			pages.put(Page.DUNGEON, new DungeonPage(this));
			pages.put(Page.EXTRA, new ExtraPage(this));
			pages.put(Page.INVENTORIES, new InventoriesPage(this));
			pages.put(Page.COLLECTIONS, new CollectionsPage(this));
			pages.put(Page.PETS, new PetsPage(this));
			pages.put(Page.MINING, new MiningPage(this));
			pages.put(Page.BINGO, new BingoPage(this));
			pages.put(Page.TROPHY_FISH, new TrophyFishPage(this));
			pages.put(Page.BESTIARY, new BestiaryPage(this));
			pages.put(Page.CRIMSON_ISLE, new CrimsonIslePage(this));
			pages.put(Page.MUSEUM, new MuseumPage(this));
			pages.put(Page.RIFT, new RiftPage(this));
			pages.put(Page.GARDEN, new GardenPage(this));
			for (Page p : Page.values()) pages.putIfAbsent(p, new NotPortedPage(this, p.name));
		} catch (Exception e) {
			crashPage = new CrashPage(this, e, currentPage.name());
		}
	}

	public SkyblockProfiles getProfile() {
		return profile;
	}

	public String getProfileName() {
		return profileName;
	}

	public SkyblockProfiles.SkyblockProfile getSelectedProfile() {
		return profile.getProfile(profileName);
	}

	public int guiLeft() {
		return guiLeft;
	}

	public int guiTop() {
		return guiTop;
	}

	public static boolean isOnSecondPage() {
		return onSecondPage;
	}

	public static void setOnSecondPage(boolean v) {
		onSecondPage = v;
	}

	private final Map<PvPage, Map<String, EditBox>> pageFields = new HashMap<>();

	/**
	 * Text field owned by a page (NEU: inventoryTextField, museumTextField, killDeathSearchTextField).
	 * Created on first call, shown only while its page is the current one; call it every frame with the
	 * position (it follows window resizes).
	 */
	public EditBox pageField(PvPage owner, String id, int x, int y, int w, int h) {
		EditBox box = pageFields.computeIfAbsent(owner, k -> new HashMap<>()).get(id);
		if (box == null) {
			box = new EditBox(font, x, y, w, h, Component.literal(id));
			box.setMaxLength(64);
			pageFields.get(owner).put(id, box);
			addRenderableWidget(box);
		}
		box.setX(x);
		box.setY(y);
		box.setWidth(w);
		return box;
	}

	private void updatePageFieldVisibility() {
		PvPage current = crashPage == null && profile.getState() == SkyblockProfiles.State.LOADED ? pages.get(currentPage) : null;
		for (Map.Entry<PvPage, Map<String, EditBox>> e : pageFields.entrySet()) {
			for (EditBox b : e.getValue().values()) {
				b.visible = e.getKey() == current;
				if (!b.visible && b.isFocused()) b.setFocused(false);
			}
		}
	}

	String playerNameForReport() {
		String n = profile.getDisplayName();
		return n == null ? "?" : n;
	}

	String uuidForReport() {
		return profile.getUuidString();
	}

	@Override
	protected void init() {
		guiLeft = (width - SIZE_X) / 2;
		guiTop = (height - SIZE_Y) / 2;
		for (Map<String, EditBox> m : pageFields.values()) m.values().forEach(this::addRenderableWidget);
		String initial = searchBox != null ? searchBox.getValue() : Objects.requireNonNullElse(profile.getDisplayName(), "");
		searchBox = new EditBox(font, guiLeft + SIZE_X - 100, guiTop + SIZE_Y + 5, 100, 20, Component.literal("Player"));
		searchBox.setMaxLength(16);
		searchBox.setValue(initial);
		addRenderableWidget(searchBox);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		currentTime = System.currentTimeMillis();
		if (lastTime == 0) lastTime = currentTime;
		profile.tickLoad();
		if (profileName == null && profile.getLatestProfileName() != null) {
			profileName = profile.getLatestProfileName();
			if (searchBox != null && searchBox.getValue().isEmpty() && profile.getDisplayName() != null) searchBox.setValue(profile.getDisplayName());
		}
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		showBingoPage = PvConfig.get().alwaysShowBingoTab || (selected != null && "bingo".equals(selected.getGamemode()));
		if (!showBingoPage && currentPage == Page.BINGO) currentPage = Page.BASIC;

		boolean loaded = profile.getState() == SkyblockProfiles.State.LOADED && crashPage == null;
		if (loaded) renderTabs(g, false);
		renderRecentPlayers(g, false);
		drawPanel(g, loaded);
		if (loaded) renderTabs(g, true);
		renderRecentPlayers(g, true);

		if (loaded) renderProfileButtons(g);

		if (crashPage != null) {
			crashPage.draw(g, mouseX, mouseY, partialTicks);
		} else if (loaded) {
			PvPage page = pages.get(currentPage);
			try {
				page.draw(g, mouseX, mouseY, partialTicks);
			} catch (Exception e) {
				crashPage = new CrashPage(this, e, currentPage.name());
			}
		} else {
			renderStateMessage(g);
		}
		if (searchMessage != null) Draw.centered(g, searchMessage, guiLeft + SIZE_X - 50, guiTop + SIZE_Y + 32, true);
		updatePageFieldVisibility();
		lastTime = currentTime;

		super.extractRenderState(g, mouseX, mouseY, partialTicks);
		collectChromeTooltips(mouseX, mouseY, loaded);
		if (tooltipToDisplay != null) {
			g.setComponentTooltipForNextFrame(font, tooltipToDisplay, mouseX, mouseY);
			tooltipToDisplay = null;
		}
	}

	private void renderStateMessage(GuiGraphicsExtractor g) {
		float cx = guiLeft + SIZE_X / 2f;
		int y = guiTop + 101;
		switch (profile.getState()) {
			case NO_SKYBLOCK -> Draw.centered(g, "§cNo SkyBlock data found!", cx, y, true);
			case ERROR -> {
				Draw.centered(g, "§cCould not load this player's profiles.", cx, y, true);
				Draw.centeredMaxWidth(g, "§7" + profile.getError(), cx, y + 10, true, SIZE_X - 20, -1);
				authHint(g, cx, y + 20);
			}
			default -> {
				long mod = System.currentTimeMillis() % 1000;
				Draw.centered(g, "§eLoading player profiles" + (mod > 666 ? "..." : mod > 333 ? ".." : "."), cx, y, true);
				if (!authHint(g, cx, y + 10)) loadingJokes(g, cx, y + 10);
			}
		}
	}

	/** @return true if a hint was shown */
	private boolean authHint(GuiGraphicsExtractor g, float cx, int y) {
		switch (UrsaClient.getAuthState()) {
			case NOT_CONFIGURED -> {
				Draw.centered(g, "§cNo data proxy configured.", cx, y, true);
				Draw.centeredMaxWidth(g, "§cSet \"ursaUrl\" in config/neupv.json (an ursa-minor server).", cx, y + 10, true, SIZE_X - 20, -1);
				return true;
			}
			case FAILED_TO_JOINSERVER -> {
				Draw.centered(g, "§cLooks like we cannot authenticate with Mojang.", cx, y, true);
				Draw.centered(g, "§cIs your game open for more than 24 hours?", cx, y + 10, true);
				return true;
			}
			case REJECTED -> {
				Draw.centered(g, "§cThe proxy rejected the authentication.", cx, y, true);
				Draw.centered(g, "§cCheck the ursaUrl in config/neupv.json.", cx, y + 10, true);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** NEU's escalating loading messages, minus the deliberate game crash after one hour. */
	private void loadingJokes(GuiGraphicsExtractor g, float cx, int y) {
		long d = System.currentTimeMillis() - startTime;
		String[] msgs = {"§eIts taking a while...", "§eMight be hypixel's fault.", "§eMight be our fault :/.", "§eWow you're still here?"};
		long[] at = {20000, 20000, 60000, 180000};
		for (int i = 0; i < msgs.length; i++) {
			if (d > at[i]) {
				Draw.centered(g, msgs[i], cx, y, true);
				y += 10;
			}
		}
		if (d > 360000) {
			Draw.centered(g, String.format(Locale.ROOT, "§eYou've wasted your time here for: %02d:%02d:%02d", (d / 3600000) % 24, (d / 60000) % 60, (d / 1000) % 60), cx, y, true);
			Draw.centered(g, "§e§lWhat are you doing with your life?", cx, y + 10, true);
		}
		if (d > 600000) Draw.centered(g, "§c§lManiac", cx, y + 20, true);
	}

	/**
	 * Draws pv_bg with its border opened under the pressed tab / pressed side button, so they merge with the panel.
	 * NEU got this from the GL depth buffer (the pressed tab was drawn in front, then the panel behind it); the
	 * 26.3 GUI has no depth test, and the bottom of the tab texture is translucent, so the border must not be drawn.
	 */
	private void drawPanel(GuiGraphicsExtractor g, boolean loaded) {
		int tabGap = Integer.MIN_VALUE;
		int sideGap = Integer.MIN_VALUE;
		if (loaded) {
			int idx = visibleTabs().indexOf(currentPage);
			if (idx >= 0) tabGap = idx * 28;
			PvPage page = pages.get(currentPage);
			if (page != null && page.hasSideButtons()) sideGap = (onSecondPage ? 1 : 0) * 28;
		}
		final int border = 4;
		// top border strip, minus the pressed tab
		bgPart(g, 0, 0, SIZE_X, border, tabGap, 28, true);
		// left border strip, minus the pressed side button
		bgPart(g, 0, border, border, SIZE_Y - border, sideGap == Integer.MIN_VALUE ? Integer.MIN_VALUE : sideGap - border, 28, false);
		// everything else
		Draw.rect(g, PV_BG, guiLeft + border, guiTop + border, SIZE_X - border, SIZE_Y - border,
			border / (float) SIZE_X, 1f, border / (float) SIZE_Y, 1f, SIZE_X, SIZE_Y);
	}

	/** Draws the pv_bg region (x, y, w, h) except a gap of gapLen pixels starting at gapStart along the strip. */
	private void bgPart(GuiGraphicsExtractor g, int x, int y, int w, int h, int gapStart, int gapLen, boolean horizontal) {
		int len = horizontal ? w : h;
		int[][] segments = gapStart == Integer.MIN_VALUE ? new int[][]{{0, len}} : new int[][]{{0, gapStart}, {gapStart + gapLen, len}};
		for (int[] seg : segments) {
			int a = Math.max(0, seg[0]), b = Math.min(len, seg[1]);
			if (b <= a) continue;
			int px = horizontal ? x + a : x, py = horizontal ? y : y + a;
			int pw = horizontal ? b - a : w, ph = horizontal ? h : b - a;
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PV_BG, guiLeft + px, guiTop + py, px, py, pw, ph, pw, ph, SIZE_X, SIZE_Y);
		}
	}

	private List<Page> visibleTabs() {
		List<Page> out = new ArrayList<>();
		for (int id : PvConfig.get().pageLayout) {
			Page p = Page.byId(id);
			if (p == null || (p == Page.BINGO && !showBingoPage)) continue;
			out.add(p);
		}
		return out;
	}

	private void renderTabs(GuiGraphicsExtractor g, boolean pressedPass) {
		List<Page> tabs = visibleTabs();
		for (int i = 0; i < tabs.size(); i++) {
			Page p = tabs.get(i);
			boolean pressed = p == currentPage;
			if (pressed != pressedPass) continue;
			int x = guiLeft + i * 28, y = guiTop - 28;
			if (pressed) {
				float uMin = i == 0 ? 0 : 28 / 256f, uMax = i == 0 ? 28 / 256f : 56 / 256f;
				Draw.rect(g, PV_ELEMENTS, x, y, 28, 32, uMin, uMax, 52 / 256f, 84 / 256f, 256, 256);
			} else {
				Draw.rect(g, PV_ELEMENTS, x, y, 28, 31, 0, 28 / 256f, 20 / 256f, 51 / 256f, 256, 256);
			}
			g.item(p.stack(), x + 6, y + 9);
		}
	}

	private static ItemStack head(String name) {
		return HEADS.computeIfAbsent(name.toLowerCase(Locale.ROOT), n -> {
			ItemStack s = new ItemStack(Items.PLAYER_HEAD);
			s.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(n));
			return s;
		});
	}

	private String selfName() {
		return minecraft.player != null ? minecraft.player.getGameProfile().name() : minecraft.getUser().getName();
	}

	private void renderRecentPlayers(GuiGraphicsExtractor g, boolean selectedPass) {
		String shown = profile.getDisplayName() == null ? "" : profile.getDisplayName().toLowerCase(Locale.ROOT);
		List<String> names = new ArrayList<>();
		names.add(selfName().toLowerCase(Locale.ROOT));
		names.addAll(PvConfig.get().previousProfileSearches);
		for (int i = 0; i < names.size(); i++) {
			boolean selected = names.get(i).equals(shown);
			if (selected != selectedPass) continue;
			int x = guiLeft + SIZE_X, y = guiTop + i * 28;
			float vMin = i == 0 ? 144 / 256f : 172 / 256f, vMax = i == 0 ? 172 / 256f : 200 / 256f;
			float uMin = selected ? 196 / 256f : 226 / 256f, uMax = selected ? 226 / 256f : 1f;
			Draw.rect(g, PV_ELEMENTS, x - 3, y, selected ? 30 : 30, 28, uMin, uMax, vMin, vMax, 256, 256);
			g.item(head(names.get(i)), x + 3, y + 6);
		}
	}

	private void renderProfileButtons(GuiGraphicsExtractor g) {
		int y = guiTop + SIZE_Y + 3;
		Draw.rect(g, PV_DROPDOWN, guiLeft, y, 100, 20, 0, 100 / 200f, 0, 20 / 185f, 200, 185);
		Draw.centeredMaxWidth(g, String.valueOf(profileName), guiLeft + 50, y + 10, true, 90, 0xFF3FE0D0);
		SkyblockProfiles.SkyblockProfile sel = getSelectedProfile();
		if (sel != null && sel.getGamemode() != null) {
			Draw.full(g, GAMEMODE_ICONS.getOrDefault(sel.getGamemode(), GAMEMODE_UNKNOWN), guiLeft - 21, guiTop + SIZE_Y + 5, 16, 16);
		}
		Draw.rect(g, PV_DROPDOWN, guiLeft + 106, y, 100, 20, 0, 100 / 200f, 0, 20 / 185f, 200, 185);
		Draw.centeredMaxWidth(g, "Open in SkyCrypt", guiLeft + 156, y + 10, true, 90, 0xFF3FE0D0);

		List<String> names = profile.getProfileNames();
		int scale = guiScale();
		if (profileDropdownSelected && !names.isEmpty() && scale < 4) {
			int opt = scale == 3 ? 10 : 20;
			int h = names.size() * opt;
			int top = guiTop + SIZE_Y + 23;
			Draw.rect(g, PV_DROPDOWN, guiLeft, top - 3, 100, 3, 0.5f, 1, 0, 3 / 185f, 200, 185);
			Draw.rect(g, PV_DROPDOWN, guiLeft, top + h - 4, 100, 4, 0.5f, 1, 181 / 185f, 1, 200, 185);
			Draw.rect(g, PV_DROPDOWN, guiLeft, top, 100, h - 4, 0.5f, 1, (181 - h) / 185f, 181 / 185f, 200, 185);
			for (int i = 0; i < names.size(); i++) {
				Draw.centeredMaxWidth(g, names.get(i), guiLeft + 50, top + opt / 2f + opt * i, true, 90, 0xFF217068);
				SkyblockProfiles.SkyblockProfile p = profile.getProfile(names.get(i));
				if (p != null && p.getGamemode() != null) {
					Draw.full(g, GAMEMODE_ICONS.getOrDefault(p.getGamemode(), GAMEMODE_UNKNOWN), guiLeft - 21, top + 2 + opt * i, 16, 16);
				}
			}
		}
	}

	private int guiScale() {
		return (int) Math.round(minecraft.getWindow().getGuiScale());
	}

	private void collectChromeTooltips(int mouseX, int mouseY, boolean loaded) {
		if (loaded) {
			List<Page> tabs = visibleTabs();
			for (int i = 0; i < tabs.size(); i++) {
				if (Draw.within(mouseX, mouseY, guiLeft + i * 28, guiTop - 28, 28, 32)) tooltipToDisplay = List.of(Component.literal(tabs.get(i).name));
			}
		}
		List<String> names = new ArrayList<>();
		names.add(selfName());
		names.addAll(PvConfig.get().previousProfileSearches);
		for (int i = 0; i < names.size(); i++) {
			if (Draw.within(mouseX, mouseY, guiLeft + SIZE_X, guiTop + 28 * i, 28, 28)) tooltipToDisplay = List.of(Component.literal("§7" + names.get(i)));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int button = event.button();
		boolean loaded = profile.getState() == SkyblockProfiles.State.LOADED;
		if (crashPage != null) {
			crashPage.mouseClicked(mx, my, button);
			return super.mouseClicked(event, doubleClick);
		}
		if (loaded) {
			List<Page> tabs = visibleTabs();
			for (int i = 0; i < tabs.size(); i++) {
				if (Draw.within(mx, my, guiLeft + i * 28, guiTop - 28, 28, 32)) {
					currentPage = tabs.get(i);
					onSecondPage = false;
					return true;
				}
			}
			try {
				PvPage page = pages.get(currentPage);
				if (page.mouseClicked(mx, my, button)) return true;
				if (!page.wantsRealRelease()) page.mouseReleased(mx, my, button);
			} catch (Exception e) {
				crashPage = new CrashPage(this, e, currentPage.name());
				return true;
			}
		}
		List<String> names = new ArrayList<>();
		names.add(selfName());
		names.addAll(PvConfig.get().previousProfileSearches);
		for (int i = 0; i < names.size(); i++) {
			if (Draw.within(mx, my, guiLeft + SIZE_X, guiTop + 28 * i, 28, 28)) {
				String n = names.get(i);
				if (!n.equalsIgnoreCase(Objects.requireNonNullElse(profile.getDisplayName(), ""))) search(n);
				return true;
			}
		}
		if (loaded && profileName != null && Draw.within(mx, my, guiLeft + 106, guiTop + SIZE_Y + 3, 100, 20)) {
			String name = Objects.requireNonNullElse(profile.getDisplayName(), profile.getUuidString());
			net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, URI.create("https://sky.shiiyu.moe/stats/" + name + "/" + profileName.replace(" ", "%20")));
			return true;
		}
		List<String> profNames = profile.getProfileNames();
		if (loaded && !profNames.isEmpty() && mx > guiLeft && mx < guiLeft + 100) {
			int scale = guiScale();
			if (my > guiTop + SIZE_Y + 3 && my < guiTop + SIZE_Y + 23) {
				if (scale >= 4) {
					int idx = Math.max(0, profNames.indexOf(profileName)) + (button == 0 ? 1 : -1);
					if (idx >= profNames.size()) idx = 0;
					if (idx < 0) idx = profNames.size() - 1;
					selectProfile(profNames.get(idx));
				} else {
					profileDropdownSelected = !profileDropdownSelected;
				}
				return true;
			} else if (scale < 4 && profileDropdownSelected) {
				int opt = scale == 3 ? 10 : 20;
				int idx = (int) ((my - (guiTop + SIZE_Y + 23)) / opt);
				if (idx >= 0 && idx < profNames.size()) selectProfile(profNames.get(idx));
				profileDropdownSelected = false;
				return true;
			}
		}
		profileDropdownSelected = false;
		return super.mouseClicked(event, doubleClick);
	}

	private void selectProfile(String name) {
		if (!Objects.equals(name, profileName)) pages.values().forEach(PvPage::resetCache);
		profileName = name;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		PvPage page = pages.get(currentPage);
		if (page != null && page.wantsRealRelease()) page.mouseReleased(event.x(), event.y(), event.button());
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchBox != null && searchBox.isFocused() && (event.key() == 257 || event.key() == 335)) {
			search(searchBox.getValue().trim());
			return true;
		}
		if (crashPage == null && !(searchBox != null && searchBox.isFocused()) && pageFields.values().stream().flatMap(m -> m.values().stream()).noneMatch(b -> b.visible && b.isFocused())) {
			PvPage page = pages.get(currentPage);
			if (page != null && page.keyPressed(event.key())) return true;
		}
		return super.keyPressed(event);
	}

	private void search(String name) {
		if (name.isEmpty() || searching) return;
		searching = true;
		searchMessage = "§eSearching...";
		ProfileViewer.loadPlayerByName(name, result -> {
			searching = false;
			switch (result) {
				case ProfileViewer.LoadResult.Found f -> {
					ProfileViewer.saveSearch(name);
					searchMessage = null;
					Minecraft.getInstance().setScreenAndShow(new ProfileViewerScreen(f.profiles()));
				}
				case ProfileViewer.LoadResult.NotFound nf -> searchMessage = "§cUnknown player: " + nf.name();
				case ProfileViewer.LoadResult.Failed fl -> searchMessage = "§c" + fl.message();
			}
		});
	}
}
