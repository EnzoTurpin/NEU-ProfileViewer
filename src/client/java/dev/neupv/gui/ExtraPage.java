/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates ExtraPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.neupv.config.PvConfig;
import dev.neupv.item.LegacyItems;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.pv.Weights;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** "Profile Stats" tab (NEU ExtraPage) with the Hoppity / Chocolate Factory sub-page on the second side button. */
public class ExtraPage extends PvPage {
	private static final Identifier PV_EXTRA = Draw.tex("gui/pv_extra.png");
	/** NEU: hardcoded list of skills that count for the skill average. */
	private static final List<String> SKILLS = List.of("taming", "mining", "foraging", "enchanting", "farming", "combat",
		"fishing", "alchemy", "carpentry");
	static final String HOPPITY_SKULL_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvO" +
		"WE4MTUzOThlN2RhODliMWJjMDhmNjQ2Y2FmYzhlN2I4MTNkYTBiZTBlZWMwY2NlNmQzZWZmNTIwNzgwMTAyNiJ9fX0=";

	private static final float X_START = 22, X_OFFSET = 103, Y_START_TOP = 27, Y_START_BOTTOM = 105, Y_OFFSET = 10;

	private static ItemStack statsIcon, hoppityIcon;

	private TreeMap<Integer, Set<String>> topKills = null;
	private TreeMap<Integer, Set<String>> topDeaths = null;
	private int deathScroll = 0;
	private int killScroll = 0;
	private boolean clickedLoadGuildInfoButton = false;
	private String lastSearch = "";
	private double pendingScroll = 0;
	private Event<?> scrollEvent;

	private final ExtraHoppityPage hoppityPage;

	public ExtraPage(ProfileViewerScreen screen) {
		super(screen);
		this.hoppityPage = new ExtraHoppityPage(screen);
	}

	private static void initIcons() {
		if (statsIcon != null) return;
		statsIcon = LegacyItems.icon(Items.BOOK, "§7Stats");
		hoppityIcon = LegacyItems.skull("§7Hoppity", HOPPITY_SKULL_TEXTURE);
	}

	/**
	 * NEU polled Mouse.getDWheel() in drawPage; PvPage has no scroll hook, so listen to the screen's Fabric scroll
	 * event (re-registered when Fabric recreates the screen events on init/resize).
	 */
	private void hookScroll() {
		Event<ScreenMouseEvents.AllowMouseScroll> ev = ScreenMouseEvents.allowMouseScroll(screen);
		if (ev == scrollEvent) return;
		scrollEvent = ev;
		ev.register((s, mx, my, h, v) -> {
			if (ProfileViewerScreen.currentPage != ProfileViewerScreen.Page.EXTRA || ProfileViewerScreen.isOnSecondPage()) return true;
			float killDeathX = screen.guiLeft() + X_START + X_OFFSET * 3;
			if (mx >= killDeathX && mx <= killDeathX + 76) {
				int top = screen.guiTop();
				if ((my >= top + Y_START_TOP && my <= top + Y_START_TOP + 65) || (my >= top + Y_START_BOTTOM && my <= top + Y_START_BOTTOM + 65)) {
					pendingScroll += v;
					return false;
				}
			}
			return true;
		});
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
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
		if (ProfileViewerScreen.isOnSecondPage()) return hoppityPage.mouseClicked(mouseX, mouseY, button);

		// "Load Guild Info" button (NEU checked Mouse.getEventButtonState() inside drawPage)
		if (!clickedLoadGuildInfoButton && Draw.within(mouseX, mouseY, (int) (guiLeft + X_START - 1),
			(int) (guiTop + Y_START_TOP + Y_OFFSET * 3), 78, 12)) {
			clickedLoadGuildInfoButton = true;
			return true;
		}
		return false;
	}

	private void drawEssence(GuiGraphicsExtractor g, JsonObject profileInfo, int mouseX, int mouseY) {
		JsonObject parents = NeuRepo.parents();
		if (parents == null || !parents.has("ESSENCE_WITHER")) {
			Draw.text(g, "§cMissing repo data", screen.guiLeft() + X_START + X_OFFSET, screen.guiTop() + Y_START_BOTTOM, true);
			return;
		}
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		float yStart = Y_START_BOTTOM;

		List<String> essences = new ArrayList<>();
		for (JsonElement e : parents.getAsJsonArray("ESSENCE_WITHER")) essences.add(e.getAsString());
		// wither essence is not part of its own parents array
		essences.add("ESSENCE_WITHER");

		JsonObject essenceShops = NeuRepo.constant("essenceshops");
		for (int i = 0; i < essences.size(); i++) {
			String essenceName = essences.get(i);
			JsonObject item = NeuRepo.item(essenceName);
			if (item == null) return;
			String displayName = J.str(item, "displayname", essenceName);
			int essenceNumber = J.i(profileInfo, "currencies.essence." + essenceName.replace("ESSENCE_", "") + ".current", 0);
			float x = guiLeft + X_START + X_OFFSET;
			// NEU used 9 px per line; with 9 essences that overflows the box, so squeeze to fit 72 px
			float spacing = Math.min(Y_OFFSET - 1, 72f / Math.max(1, essences.size()));
			float y = guiTop + yStart + spacing * i;
			float scale = Math.min(1f, spacing / (Y_OFFSET - 1));
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale, scale);
			Draw.aligned(g, "§6" + displayName, "§f" + Fmt.shortNumber(essenceNumber), 0, 0, Math.round(76 / scale));
			g.pose().popMatrix();

			if (essenceShops == null) return;
			if (mouseX >= x && mouseX <= x + 76 && mouseY >= y && mouseY < y + spacing) {
				List<Component> tt = new ArrayList<>();
				JsonObject shop = J.obj(essenceShops, essenceName);
				if (shop != null) {
					for (Map.Entry<String, JsonElement> entry : shop.entrySet()) {
						int perkTier = J.i(profileInfo, "player_data.perks." + entry.getKey(), 0);
						int max = J.arrOrEmpty(entry.getValue(), "costs").size();
						String formatting = perkTier == max ? "§a" : "§b";
						String name = J.str(entry.getValue(), "name", entry.getKey());
						tt.add(Component.literal("§6" + name + ": " + formatting + perkTier + "/" + max));
					}
				}
				if (!tt.isEmpty()) screen.tooltipToDisplay = tt;
			}
		}
	}

	@Override
	public boolean hasSideButtons() {
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		initIcons();
		hookScroll();

		if (ProfileViewerScreen.isOnSecondPage()) {
			// the screen shows page fields whenever their page is current: park the search box off-screen on the sub-page
			EditBox box = screen.pageField(this, "killDeathSearch", -10000, -10000, 80, 12);
			if (box.isFocused()) box.setFocused(false);
			hoppityPage.draw(g, mouseX, mouseY, partialTicks);
		} else {
			drawStats(g, mouseX, mouseY);
		}
		PvWidgets.sideButtons(screen, g, statsIcon, hoppityIcon, mouseX, mouseY);
	}

	private void drawStats(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.full(g, PV_EXTRA, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		SkyblockProfiles.SkyblockProfile selectedProfile = getSelectedProfile();
		if (selectedProfile == null) return;

		JsonObject profileInfo = selectedProfile.getProfileJson();
		Map<String, Level> skyblockInfo = selectedProfile.getLevelingInfo();

		// Coop bank is on the outer profile, the personal bank in the member's "profile" object.
		double bankBalance = J.dbl(selectedProfile.getOuterProfileJson(), "banking.balance", 0);
		double purseBalance = J.dbl(profileInfo, "currencies.coin_purse", 0);
		double personalBankBalance = J.dbl(profileInfo, "profile.bank_account", 0);

		Draw.aligned(g, "§6Bank", "§f" + Fmt.shortNumber(bankBalance) + "/" + Fmt.shortNumber(personalBankBalance),
			guiLeft + X_START, guiTop + Y_START_TOP, 76);
		Draw.aligned(g, "§6Purse", "§f" + Fmt.shortNumber(purseBalance), guiLeft + X_START, guiTop + Y_START_TOP + Y_OFFSET, 76);

		JsonElement firstJoin = J.get(profileInfo, "profile.first_join");
		if (firstJoin != null && firstJoin.isJsonPrimitive()) {
			Draw.aligned(g, "§bJoined", "§f" + timeSinceMillisecond(firstJoin.getAsLong()),
				guiLeft + X_START, guiTop + Y_START_TOP + Y_OFFSET * 2, 76);
		}

		drawGuild(g);

		// Pronouns (pronoundb.org), only when enabled in the config
		boolean guildShown = guildName() != null;
		String pronouns = pronouns();
		if (pronouns != null) {
			Draw.aligned(g, "§aPronouns", "§f" + pronouns, guiLeft + X_START,
				guiTop + Y_START_TOP + Y_OFFSET * 4 + (guildShown ? 0 : 5), 76);
		}

		int fairySouls = J.i(profileInfo, "fairy_soul.total_collected", 0);
		boolean cookieBuff = J.bool(profileInfo, "profile.cookie_buff_active", false);
		int fairySoulMax = J.i(NeuRepo.constant("fairy_souls"), "Max Souls", 227);
		Draw.aligned(g, "§dFairy Souls", "§f" + fairySouls + "/" + fairySoulMax, guiLeft + X_START, guiTop + Y_START_BOTTOM, 76);

		if (skyblockInfo != null) {
			float totalSkillLVL = 0, totalTrueSkillLVL = 0, totalSlayerLVL = 0, totalSkillCount = 0, totalSlayerCount = 0, totalSlayerXP = 0;
			for (Map.Entry<String, Level> entry : skyblockInfo.entrySet()) {
				if (SKILLS.contains(entry.getKey())) {
					totalSkillLVL += entry.getValue().level;
					totalTrueSkillLVL += (float) Math.floor(entry.getValue().level);
					totalSkillCount++;
				} else if (Weights.SLAYER_NAMES.contains(entry.getKey())) {
					totalSlayerLVL += entry.getValue().level;
					totalSlayerCount++;
					totalSlayerXP += entry.getValue().totalXp;
				}
			}
			float avgSkillLVL = totalSkillLVL / totalSkillCount;
			float avgTrueSkillLVL = totalTrueSkillLVL / totalSkillCount;
			float avgSlayerLVL = totalSlayerLVL / totalSlayerCount;
			boolean api = selectedProfile.skillsApiEnabled();

			Draw.aligned(g, "§cAVG Skill LVL", api ? "§f" + Math.floor(avgSkillLVL * 10) / 10 : "§cAPI OFF!",
				guiLeft + X_START, guiTop + Y_START_BOTTOM + Y_OFFSET, 76);
			Draw.aligned(g, "§cTrue AVG Skill LVL", api ? "§f" + Math.floor(avgTrueSkillLVL * 10) / 10 : "§cAPI OFF!",
				guiLeft + X_START, guiTop + Y_START_BOTTOM + Y_OFFSET * 2, 76);
			Draw.aligned(g, "§cAVG Slayer LVL", "§f" + Math.floor(avgSlayerLVL * 10) / 10,
				guiLeft + X_START, guiTop + Y_START_BOTTOM + Y_OFFSET * 3, 76);
			Draw.aligned(g, "§cTotal Slayer XP", "§f" + Fmt.shortNumber(totalSlayerXP),
				guiLeft + X_START, guiTop + Y_START_BOTTOM + Y_OFFSET * 4, 76);
			Draw.aligned(g, "§6Cookie Buff", cookieBuff ? "§dActive" : "§cInactive",
				guiLeft + X_START, guiTop + Y_START_BOTTOM + Y_OFFSET * 5, 76);
		}

		// Auctions
		String a = "player_stats.auctions.";
		float col2 = guiLeft + X_START + X_OFFSET;
		Draw.aligned(g, "§5Auction Bids", "§f" + (long) J.dbl(profileInfo, a + "bids", 0), col2, guiTop + Y_START_TOP, 76);
		Draw.aligned(g, "§5Highest Bid", "§f" + Fmt.shortNumber(J.dbl(profileInfo, a + "highest_bid", 0)), col2, guiTop + Y_START_TOP + Y_OFFSET, 76);
		Draw.aligned(g, "§5Auctions Won", "§f" + (long) J.dbl(profileInfo, a + "won", 0), col2, guiTop + Y_START_TOP + Y_OFFSET * 2, 76);
		Draw.aligned(g, "§5Auctions Created", "§f" + (long) J.dbl(profileInfo, a + "created", 0), col2, guiTop + Y_START_TOP + Y_OFFSET * 3, 76);
		Draw.aligned(g, "§5Gold Spent", "§f" + Fmt.shortNumber(J.dbl(profileInfo, a + "gold_spent", 0)), col2, guiTop + Y_START_TOP + Y_OFFSET * 4, 76);
		Draw.aligned(g, "§5Gold Earned", "§f" + Fmt.shortNumber(J.dbl(profileInfo, a + "gold_earned", 0)), col2, guiTop + Y_START_TOP + Y_OFFSET * 5, 76);

		// Pet milestones / fishing
		float col3 = guiLeft + X_START + X_OFFSET * 2;
		Draw.aligned(g, "§aOres Mined", "§f" + (long) J.dbl(profileInfo, "player_stats.pets.milestone.ores_mined", 0), col3, guiTop + Y_START_TOP, 76);
		Draw.aligned(g, "§aSea Creatures Killed", "§f" + (long) J.dbl(profileInfo, "player_stats.pets.milestone.sea_creatures_killed", 0),
			col3, guiTop + Y_START_TOP + Y_OFFSET, 76);
		Draw.aligned(g, "§aItems Fished", "§f" + (long) J.dbl(profileInfo, "player_stats.items_fished.total", 0), col3, guiTop + Y_START_TOP + Y_OFFSET * 3, 76);
		Draw.aligned(g, "§aTreasures Fished", "§f" + (long) J.dbl(profileInfo, "player_stats.items_fished.treasure", 0), col3, guiTop + Y_START_TOP + Y_OFFSET * 4, 76);
		Draw.aligned(g, "§aLarge Treasures", "§f" + (long) J.dbl(profileInfo, "player_stats.items_fished.large_treasure", 0), col3, guiTop + Y_START_TOP + Y_OFFSET * 5, 76);

		drawEssence(g, profileInfo, mouseX, mouseY);
		drawKillsDeaths(g, profileInfo, mouseX, mouseY);
	}

	private void drawKillsDeaths(GuiGraphicsExtractor g, JsonObject profileInfo, int mouseX, int mouseY) {
		int guiTop = screen.guiTop();
		if (topKills == null) topKills = topMap(J.objOrEmpty(profileInfo, "player_stats.kills"));
		if (topDeaths == null) topDeaths = topMap(J.objOrEmpty(profileInfo, "player_stats.deaths"));

		float killDeathX = screen.guiLeft() + X_START + X_OFFSET * 3;
		EditBox box = screen.pageField(this, "killDeathSearch", (int) killDeathX, (int) (guiTop + Y_START_BOTTOM + 77), 80, 12);
		String search = box.getValue().toLowerCase(Locale.ROOT);
		if (!search.equals(lastSearch)) {
			// NEU: typing in the search field resets both scrolls
			lastSearch = search;
			killScroll = 0;
			deathScroll = 0;
		}

		// apply the wheel input collected by the scroll hook (NEU: Mouse.getDWheel())
		if (pendingScroll != 0) {
			int step = pendingScroll > 0 ? -1 : 1;
			pendingScroll = 0;
			if (mouseX >= killDeathX && mouseX <= killDeathX + 76) {
				if (mouseY >= guiTop + Y_START_TOP && mouseY <= guiTop + Y_START_TOP + 65) killScroll = Math.max(0, killScroll + step);
				else if (mouseY >= guiTop + Y_START_BOTTOM && mouseY <= guiTop + Y_START_BOTTOM + 65) deathScroll = Math.max(0, deathScroll + step);
			}
		}

		int[] kills = drawList(g, topKills, "K: ", search, killDeathX, guiTop + Y_START_TOP, killScroll);
		int[] deaths = drawList(g, topDeaths, "D: ", search, killDeathX, guiTop + Y_START_BOTTOM, deathScroll);
		killScroll = Math.max(0, Math.min(killScroll, kills[0] - kills[1]));
		deathScroll = Math.max(0, Math.min(deathScroll, deaths[0] - deaths[1]));
	}

	/** @return {matching entries, rendered entries} */
	private int[] drawList(GuiGraphicsExtractor g, TreeMap<Integer, Set<String>> map, String prefix, String search, float x, float yStart, int scroll) {
		int index = 0, rendered = 0;
		for (int count : map.descendingKeySet()) {
			for (String type : map.get(count)) {
				if (!search.isEmpty() && !type.toLowerCase(Locale.ROOT).contains(search)) continue;
				float y = yStart + Y_OFFSET * (index - scroll);
				if (y + 6 < yStart + 75 && y >= yStart) {
					rendered++;
					Draw.aligned(g, "§e" + prefix + type, "§f" + count, x, y, 76);
				}
				index++;
			}
		}
		return new int[]{index, rendered};
	}

	private static TreeMap<Integer, Set<String>> topMap(JsonObject stats) {
		TreeMap<Integer, Set<String>> map = new TreeMap<>();
		for (Map.Entry<String, JsonElement> entry : stats.entrySet()) {
			if (entry.getValue().isJsonPrimitive() && ((JsonPrimitive) entry.getValue()).isNumber()) {
				String name = capitalizeFully(entry.getKey().replace("_", " "));
				map.computeIfAbsent(entry.getValue().getAsInt(), k -> new TreeSet<>()).add(name);
			}
		}
		return map;
	}

	private static String capitalizeFully(String s) {
		StringBuilder sb = new StringBuilder();
		boolean start = true;
		for (char c : s.toCharArray()) {
			if (c == ' ') {
				start = true;
				sb.append(c);
			} else {
				sb.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
				start = false;
			}
		}
		return sb.toString();
	}

	/** Guild name to show, or null while the button must be shown. */
	private String guildName() {
		SkyblockProfiles profile = screen.getProfile();
		if (!profile.isInGuild()) return "N/A";
		if (!clickedLoadGuildInfoButton) return null;
		JsonObject guild = profile.getGuild();
		if (!profile.isInGuild()) return "N/A";
		return guild != null && guild.has("name") ? guild.get("name").getAsString() : null;
	}

	private void drawGuild(GuiGraphicsExtractor g) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		String name = guildName();
		if (name != null) {
			Draw.aligned(g, "§bGuild", "§f" + name, guiLeft + X_START, guiTop + Y_START_TOP + Y_OFFSET * 3, 76);
			return;
		}
		int bx = (int) (guiLeft + X_START - 1), by = (int) (guiTop + Y_START_TOP + Y_OFFSET * 3);
		// NEU RenderUtils.drawFloatingRectWithAlpha(x, y, 78, 12, 100, true)
		g.fill(bx, by, bx + 78, by + 12, 0x64000000);
		g.outline(bx, by, 78, 12, 0x64FFFFFF);
		Draw.centeredMaxWidth(g, clickedLoadGuildInfoButton ? "§bLoading..." : "§fLoad Guild Info",
			guiLeft + X_START + 38, guiTop + Y_START_TOP + Y_OFFSET * 3 + 6, true, 70, 0xFFFFFFFF);
	}

	// ---- pronoundb.org v2 (NEU PronounDB) ----

	private static final String[][] PRONOUNS = {
		{"he", "him", null}, {"it", "its", null}, {"she", "her", null}, {"they", "them", null},
		{"any", null, "Any pronouns"}, {"other", null, "Other"}, {"ask", null, "Ask for pronouns"}, {"avoid", null, "Avoid pronouns"}};

	private String pronouns() {
		if (!PvConfig.get().showPronounsInPv) return null;
		String id = screen.getProfile().getUuid().toString();
		SkyblockProfiles.Fetch f = screen.getProfile().fetchUrl("https://pronoundb.org/api/v2/lookup?platform=minecraft&ids="
			+ URLEncoder.encode(id, StandardCharsets.UTF_8));
		if (f.state() != SkyblockProfiles.FetchState.OK || f.json() == null) return null;
		JsonArray set = J.arr(J.obj(f.json(), id), "sets.en");
		if (set == null) {
			// the API keys the answer by the id as sent; accept an undashed key too
			set = J.arr(J.obj(f.json(), id.replace("-", "")), "sets.en");
		}
		if (set == null) return null;
		List<String[]> chosen = new ArrayList<>();
		for (JsonElement e : set) {
			for (String[] p : PRONOUNS) if (p[0].equals(e.getAsString())) chosen.add(p);
		}
		if (chosen.isEmpty()) return null;
		String[] first = chosen.get(0);
		if (first[2] != null) return first[2];
		if (chosen.size() == 1) return first[0] + "/" + first[1];
		return first[0] + "/" + chosen.get(1)[0];
	}

	/**
	 * NEU Utils.timeSinceMillisecond. NEU used LocalDateTime.getDayOfYear() of the elapsed time, which is 1-based
	 * (a profile joined 1 day ago showed "2 days ago"); fixed here by dividing the duration directly.
	 */
	static String timeSinceMillisecond(long time) {
		long diff = System.currentTimeMillis() - time;
		if (diff < 60_000L) return (diff / 1000) + " seconds ago";
		if (diff < 3_600_000L) return (diff / 60_000) + " minutes ago";
		if (diff < 86_400_000L) return (diff / 3_600_000) + " hours ago";
		if (diff < 31_556_952_000L) return (diff / 86_400_000) + " days ago";
		return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
	}

	@Override
	public void resetCache() {
		topDeaths = null;
		topKills = null;
		hoppityPage.resetCache();
	}
}
