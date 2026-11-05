/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates BasicPage.java, Copyright (C) 2022-2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.services.ProfileResult;
import dev.neupv.config.PvConfig;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.Level;
import dev.neupv.pv.Prices;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.pv.Weights;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class BasicPage extends PvPage {
	private static final Identifier PV_BASIC = Draw.tex("gui/pv_basic.png");
	private static final String LEVEL_SKULL_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdkODg1YjMyYjBkZDJkNmI3ZjFiNTgyYTM0MTg2ZjhhNTM3M2M0NjU4OWEyNzM0MjMxMzJiNDQ4YjgwMzQ2MiJ9fX0=";
	private static final String[] MEDAL_NAMES = {"§cBronze", "§fSilver", "§6Gold"};
	private static ItemStack levelSkull, homeIcon, levelIcon;
	private static Map<String, ItemStack> skillIcons;

	private RemotePlayer entityPlayer;
	private boolean loadingEntity;
	private boolean clickedLoadStatusButton;
	private double backgroundClickedX = -1;
	private int backgroundClickedButton = -1;
	private String bingoRarity = "§7";

	private final LevelPage levelPage;

	public BasicPage(ProfileViewerScreen screen) {
		super(screen);
		this.levelPage = new LevelPage(screen);
	}

	public static ItemStack homeIcon() {
		initIcons();
		return homeIcon;
	}

	public static ItemStack levelIcon() {
		initIcons();
		return levelIcon;
	}

	private static void initIcons() {
		if (skillIcons != null) return;
		levelSkull = LegacyItems.skull("§7Level", LEVEL_SKULL_TEXTURE);
		homeIcon = LegacyItems.icon(Items.PAPER, "§7Home");
		levelIcon = LegacyItems.skull("§7Level", LEVEL_SKULL_TEXTURE);
		skillIcons = new LinkedHashMap<>();
		skillIcons.put("taming", LegacyItems.icon(Items.WOLF_SPAWN_EGG, "§dTaming"));
		skillIcons.put("mining", LegacyItems.icon(Items.STONE_PICKAXE, "§7Mining"));
		skillIcons.put("foraging", LegacyItems.icon(Items.OAK_SAPLING, "§2Foraging"));
		skillIcons.put("enchanting", LegacyItems.icon(Items.ENCHANTING_TABLE, "§aEnchanting"));
		skillIcons.put("carpentry", LegacyItems.icon(Items.CRAFTING_TABLE, "§4Carpentry"));
		skillIcons.put("farming", LegacyItems.icon(Items.GOLDEN_HOE, "§eFarming"));
		skillIcons.put("combat", LegacyItems.icon(Items.STONE_SWORD, "§cCombat"));
		skillIcons.put("fishing", LegacyItems.icon(Items.FISHING_ROD, "§bFishing"));
		skillIcons.put("alchemy", LegacyItems.icon(Items.BREWING_STAND, "§9Alchemy"));
		skillIcons.put("runecrafting", LegacyItems.icon(Items.MAGMA_CREAM, "§5Runecrafting"));
		skillIcons.put("social", LegacyItems.icon(Items.EMERALD, "§2Social"));
		skillIcons.put("zombie", LegacyItems.icon(Items.ROTTEN_FLESH, "§6Rev Slayer"));
		skillIcons.put("spider", LegacyItems.icon(Items.SPIDER_EYE, "§6Tara Slayer"));
		skillIcons.put("wolf", LegacyItems.icon(Items.BONE, "§6Sven Slayer"));
		skillIcons.put("enderman", LegacyItems.icon(Items.ENDER_PEARL, "§6Ender Slayer"));
		skillIcons.put("blaze", LegacyItems.icon(Items.BLAZE_ROD, "§6Blaze Slayer"));
		skillIcons.put("vampire", LegacyItems.icon(Items.REDSTONE, "§6Vampire Slayer"));
	}

	private static String plain(ItemStack s) {
		return s.getHoverName().getString();
	}

	@Override
	public boolean hasSideButtons() {
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		initIcons();
		SkyblockProfiles profile = screen.getProfile();
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		var font = Draw.font();

		if (ProfileViewerScreen.isOnSecondPage()) {
			levelPage.draw(g, mouseX, mouseY, partialTicks);
			drawSideButtons(g, mouseX, mouseY);
			return;
		}

		String location = null;
		JsonObject status = clickedLoadStatusButton ? profile.getPlayerStatus() : null;
		if (status != null && status.has("mode")) location = status.get("mode").getAsString();

		// Panorama, rotated by dragging (same behaviour as NEU)
		double extraRotation = 0;
		if (backgroundClickedX != -1) extraRotation = mouseX - backgroundClickedX;
		else screen.backgroundRotation += (screen.currentTime - screen.lastTime) / 400f;
		screen.backgroundRotation %= 360;
		Panorama.draw(g, (float) (-screen.backgroundRotation - extraRotation), guiLeft + 23, guiTop + 44, 81, 108,
			location == null ? "unknown" : location, isSkyblockNight() ? "night" : "day");

		Draw.full(g, PV_BASIC, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;

		drawPlayerName(g, profile, selected);
		drawNetworth(g, profile, selected, mouseX, mouseY);
		drawStatus(g, profile, status, location, mouseX, mouseY);
		drawEntity(g, profile, selected, mouseX, mouseY);
		drawActivePet(g, selected);
		drawSkyblockLevel(g, selected, mouseX, mouseY);
		drawSkills(g, selected, mouseX, mouseY);
		drawSideButtons(g, mouseX, mouseY);
		if (PvConfig.get().displayWeight) renderWeight(g, mouseX, mouseY, selected);
	}

	private static boolean isSkyblockNight() {
		// SkyBlock day = 20 real minutes, 24 SkyBlock hours of 50 s; epoch of SkyBlock year 1.
		long elapsed = System.currentTimeMillis() - 1560275700000L;
		long hour = (elapsed % 1_200_000L) / 50_000L;
		return hour <= 6 || hour >= 20;
	}

	private void drawPlayerName(GuiGraphicsExtractor g, SkyblockProfiles profile, SkyblockProfiles.SkyblockProfile selected) {
		JsonObject hp = profile.getHypixelPlayer();
		if (hp == null) return;
		String name = J.str(hp, "displayname", "?");
		String playerName = null;
		if (hp.has("prefix")) {
			playerName = J.str(hp, "prefix", "") + " " + name;
		} else {
			String rank = J.str(hp, "rank", "NORMAL");
			if (rank.equals("NORMAL")) {
				rank = J.str(hp, "monthlyPackageRank", "NONE");
				if (rank.equals("NONE")) rank = J.str(hp, "newPackageRank", "NONE");
			}
			String plusColor = "§c";
			var plusFmt = dev.neupv.util.Fmt.formatting(J.str(hp, "rankPlusColor", "RED"));
			if (plusFmt != null) plusColor = plusFmt.toString();
			JsonObject misc = NeuRepo.misc();
			if (misc != null && misc.has("ranks")) {
				String rankName = J.str(misc, "ranks." + rank + ".tag", null);
				String rankColor = J.str(misc, "ranks." + rank + ".color", "7");
				String rankPlus = J.str(misc, "ranks." + rank + ".plus", "");
				String tagColor = J.str(misc, "ranks." + rank + ".tagColor", rankColor);
				for (JsonElement e : J.arrOrEmpty(misc, "special_bois")) {
					if (e.getAsString().equals(profile.getUuidString())) {
						name = chroma(name);
						break;
					}
				}
				playerName = "§7" + name;
				if (rankName != null) {
					String icon = selected.getGamemode() == null ? "" : getIcon(selected.getGamemode());
					playerName = "§" + rankColor + "[§" + tagColor + rankName + plusColor + rankPlus + "§" + rankColor + "] " + name
						+ (icon.isEmpty() ? "" : " " + icon);
				}
			}
		}
		if (playerName == null) return;
		int w = Draw.font().width(playerName);
		int x = screen.guiLeft() + 63, y = screen.guiTop() + 54;
		g.fill(x - w / 2 - 1, y - 1, x + w / 2 + 1, y + 8, 0x40000000);
		g.text(Draw.font(), playerName, x - w / 2, y, 0xFFFFFFFF, true);
	}

	private static String chroma(String s) {
		String[] colors = {"c", "6", "e", "a", "b", "9", "d"};
		StringBuilder sb = new StringBuilder();
		int offset = (int) (System.currentTimeMillis() / 100 % colors.length);
		for (int i = 0; i < s.length(); i++) sb.append('§').append(colors[(i + offset) % colors.length]).append(s.charAt(i));
		return sb.toString();
	}

	private String getIcon(String gameMode) {
		return switch (gameMode) {
			case "island" -> "§a☀";
			case "bingo" -> bingoRarity + "Ⓑ";
			case "ironman" -> "§7♲";
			default -> "";
		};
	}

	private void drawNetworth(GuiGraphicsExtractor g, SkyblockProfiles profile, SkyblockProfiles.SkyblockProfile selected, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		var font = Draw.font();
		String stateStr = "§cAn error occurred";
		long networth = -2;
		List<Component> categories = new ArrayList<>();
		if (PvConfig.get().useSoopyNetworth) {
			SkyblockProfiles.SoopyNetworth nw = selected.getSoopyNetworth();
			networth = nw.networth;
			if (networth == -1) stateStr = "§eLoading...";
			else if (networth != -2) {
				for (Map.Entry<String, Long> e : nw.categories.entrySet()) {
					categories.add(Component.literal("§a" + capitalize(e.getKey().replace("_", " ")) + ": §6" + Fmt.number(e.getValue())));
				}
				categories.add(Component.empty());
			}
		}
		if (networth == -2) networth = selected.getNetworth();
		String text = "§aNet Worth: " + (networth > 0 ? "§6" + Fmt.number(networth) : stateStr);
		int fontWidth = font.width(text);
		int offset = fontWidth >= 117 ? 63 + (fontWidth - 117) : 63;
		if (fontWidth >= 117) g.text(font, text, guiLeft + 8, guiTop + 38 - font.lineHeight / 2, 0xFFFFFFFF, true);
		else Draw.centered(g, text, guiLeft + 68, guiTop + 38, true);
		if (networth <= 0) return;
		if (mouseX > guiLeft + offset - fontWidth / 2 && mouseX < guiLeft + offset + fontWidth / 2 && mouseY > guiTop + 32 && mouseY < guiTop + 38 + font.lineHeight) {
			List<Component> tt = new ArrayList<>();
			double cookie = Prices.bazaarBuy("BOOSTER_COOKIE");
			Prices.refreshIfNeeded();
			String irl = cookie > 0 ? "§6" + Fmt.number(((networth / cookie) * 325 / 675) * 4.99) : "§cERROR";
			tt.add(Component.literal("§aNet worth in IRL money: §2$" + irl));
			if (PvConfig.get().useSoopyNetworth && profile.getSoopyNetworthLeaderboardPosition() >= 0 && profile.isProfileMaxSoopyWeight(screen.getProfileName())) {
				tt.add(Component.empty());
				tt.add(Component.literal("§2#§6" + Fmt.number(profile.getSoopyNetworthLeaderboardPosition()) + "§a on soopy's networth leaderboard!"));
			}
			if (Minecraft.getInstance().hasShiftDown()) {
				tt.addAll(categories);
				tt.add(Component.literal("§cThe IRL price is calculated using the current"));
				tt.add(Component.literal("§cprice of booster cookies on bazaar and the price"));
				tt.add(Component.literal("§cfor cookies using gems, then the price of gems"));
				tt.add(Component.literal("§cis where we get the amount of IRL money you"));
				tt.add(Component.literal("§ctheoretically have on SkyBlock in net worth."));
			} else {
				tt.add(Component.literal("§7[SHIFT for Info]"));
			}
			tt.add(Component.empty());
			tt.add(Component.literal("§cTHIS IS IN NO WAY ENDORSING IRL TRADING!"));
			screen.tooltipToDisplay = tt;
		}
	}

	private static String capitalize(String s) {
		StringBuilder sb = new StringBuilder();
		for (String w : s.split(" ")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	private void drawStatus(GuiGraphicsExtractor g, SkyblockProfiles profile, JsonObject status, String location, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (status != null) {
			boolean online = J.bool(status, "online", false);
			String statusStr = online ? "§aONLINE" : "§cOFFLINE";
			String locationStr = null;
			if (profile.getUuidString().equals("20934ef9488c465180a78f861586b4cf")) {
				locationStr = "Ignoring DMs";
			} else if (profile.getUuidString().equals("b876ec32e396476ba1158438d83c67d4")) {
				statusStr = "§dLong live Potato King";
				g.item(new ItemStack(Items.POTATO), guiLeft + 35, guiTop + 160);
				PvItem crown = LegacyItems.fromRepo("POTATO_CROWN");
				if (crown != null) g.item(crown.stack(), guiLeft + 50, guiTop + 162);
				g.item(new ItemStack(Items.POTATO), guiLeft + 63, guiTop + 160);
			} else if (online) {
				locationStr = J.str(NeuRepo.misc(), "area_names." + location, location == null ? "Unknown" : location);
			}
			if (locationStr != null) statusStr += "§7 - §a" + locationStr;
			Draw.centered(g, statusStr, guiLeft + 63, guiTop + 160, true);
		} else {
			int bx = guiLeft + 24, by = guiTop + 155;
			g.fill(bx, by, bx + 80, by + 12, 0x64000000);
			g.outline(bx, by, 80, 12, 0x64FFFFFF);
			Draw.centered(g, clickedLoadStatusButton ? "§bLoading..." : "§fLoad Status", guiLeft + 63, guiTop + 161, true);
		}
	}

	private void drawEntity(GuiGraphicsExtractor g, SkyblockProfiles profile, SkyblockProfiles.SkyblockProfile selected, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		if (entityPlayer == null) {
			if (!loadingEntity && mc.level != null) {
				loadingEntity = true;
				UUID uuid = profile.getUuid();
				ClientLevel level = mc.level;
				CompletableFuture.supplyAsync(() -> {
					ProfileResult r = mc.services().sessionService().fetchProfile(uuid, false);
					return r == null ? new GameProfile(uuid, "CoolGuy123") : r.profile();
				}).whenComplete((gp, err) -> mc.execute(() -> {
					GameProfile gameProfile = gp != null ? gp : new GameProfile(uuid, "CoolGuy123");
					entityPlayer = new PvPlayer(level, gameProfile);
				}));
			}
			return;
		}
		boolean hideArmor = backgroundClickedX != -1 && backgroundClickedButton == 1;
		EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
		List<PvItem> armor = selected.getInventoryInfo().get("inv_armor");
		for (int i = 0; i < 4; i++) {
			ItemStack s = ItemStack.EMPTY;
			if (!hideArmor && armor != null && armor.size() == 4 && armor.get(i) != null) s = armor.get(i).stack();
			entityPlayer.setItemSlot(slots[i], s);
		}
		if (profile.getUuidString().equals("ae6193ab494a4719b6e7d50392c8f012")) {
			PvItem bp = LegacyItems.fromRepo("SMALL_BACKPACK");
			if (bp != null) entityPlayer.setItemSlot(EquipmentSlot.HEAD, bp.stack());
		}
		int x = screen.guiLeft() + 63, y = screen.guiTop() + 135;
		InventoryScreen.extractEntityInInventoryFollowsMouse(g, x - 40, y - 66, x + 40, y, 36, 0.0625f, mouseX, mouseY, entityPlayer);
	}

	private void drawActivePet(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile selected) {
		if ("bingo".equals(selected.getGamemode())) {
			bingoRarity = "§7";
			for (JsonObject pet : selected.getPets()) {
				if ("BINGO".equals(J.str(pet, "type", ""))) {
					bingoRarity = switch (J.str(pet, "tier", "")) {
						case "UNCOMMON" -> "§a";
						case "RARE" -> "§9";
						case "EPIC" -> "§5";
						case "LEGENDARY" -> "§6";
						case "MYTHIC" -> "§d";
						default -> "§7";
					};
					break;
				}
			}
		}
		JsonObject active = selected.getActivePet();
		if (active == null) return;
		String type = J.str(active, "type", "");
		for (int i = 0; i < 4; i++) {
			PvItem item = LegacyItems.fromRepo(type + ";" + i);
			if (item == null) continue;
			float y = screen.guiTop() + 82 + 15 * (float) Math.sin(((screen.currentTime - screen.startTime) / 800f) % (2 * Math.PI));
			Draw.item(g, item.stack(), screen.guiLeft() + 20, y, 1.5f);
			break;
		}
	}

	private void drawSkyblockLevel(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile selected, int mouseX, int mouseY) {
		int x = screen.guiLeft() + 162, y = screen.guiTop() + 74;
		double level = selected.getSkyblockLevel();
		Draw.item(g, levelSkull, x, y, 1.5f);
		Draw.centeredScaled(g, selected.getSkyblockLevelColour().toString() + (int) level, x + 9, y - 12, true, 1.5f);
		float progress = (float) (level - (long) level);
		Draw.bar(g, x - 30, y + 30, 80, progress);
		Draw.scaled(g, "§e" + (int) (progress * 100) + "/100", x - 30, y + 20, true, 0.9f);
	}

	private void drawSkills(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile selected, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Map<String, Level> info = selected.getLevelingInfo();
		if (info == null || !selected.skillsApiEnabled()) {
			Draw.centered(g, "§cSkills API not enabled!", guiLeft + 322, guiTop + 101, true);
			return;
		}
		int position = 0;
		for (Map.Entry<String, ItemStack> entry : skillIcons.entrySet()) {
			String key = entry.getKey();
			Level level = info.get(key);
			int x = guiLeft + 237 + 86 * (position / 8);
			int y = guiTop + 24 + 21 * (position % 8);
			if (key.equals("social")) {
				position--;
				x = guiLeft + 132;
				y = guiTop + 124;
			}
			String skillName = plain(entry.getValue());
			String coloured = entry.getValue().getHoverName().getString();
			Draw.aligned(g, colouredName(key, coloured), "§f" + (int) Math.floor(level.level), x + 14, y - 4, 60);
			if (level.maxed) Draw.goldBar(g, x, y + 6, 80, screen.startTime);
			else Draw.bar(g, x, y + 6, 80, level.level % 1);
			if (mouseX > x && mouseX < x + 80 && mouseY > y - 4 && mouseY < y + 13) {
				screen.tooltipToDisplay = skillTooltip(key, colouredName(key, skillName), level, selected);
			}
			Draw.item(g, entry.getValue(), x, y - 6, 0.7f);
			position++;
		}
	}

	private static String colouredName(String key, String name) {
		ItemStack s = skillIcons.get(key);
		return s == null ? name : s.getHoverName().getString().startsWith("§") ? s.getHoverName().getString() : name;
	}

	private List<Component> skillTooltip(String key, String skillName, Level level, SkyblockProfiles.SkyblockProfile selected) {
		List<Component> tt = new ArrayList<>();
		tt.add(Component.literal(skillName));
		if (level.maxed) {
			tt.add(Component.literal("§7Progress: §6MAXED!"));
		} else {
			int maxXp = (int) level.maxXpForLevel;
			float cur = (level.level % 1) * maxXp;
			tt.add(Component.literal("§7Progress: §5" + Fmt.shortNumber(Math.round(cur)) + "/" + Fmt.shortNumber(maxXp)
				+ "§8 (" + Fmt.tenths(cur / maxXp * 100) + "% to " + ((int) level.level + 1) + ")"));
		}
		tt.add(Component.literal("§7Total XP: §5" + Fmt.number((long) level.totalXp) + "§8 (" + Fmt.tenths(PvWidgets.percentage(key, level)) + "% to " + level.maxLevel + ")"));
		JsonObject pj = selected.getProfileJson();
		if (key.equals("farming")) {
			int doubleDrops = J.i(pj, "jacobs_contest.perks.double_drops", 0);
			int pelts = J.i(pj, "quests.trapper_quest.pelt_count", 0);
			tt.add(Component.literal("§7Double Drops: " + (doubleDrops == 15 ? "§6" : "§5") + (doubleDrops * 2) + "%"));
			tt.add(Component.literal("§7Pelts: §e" + pelts));
			JsonObject medals = J.objOrEmpty(pj, "jacobs_contest.medals_inv");
			tt.add(Component.literal(" "));
			for (String medal : MEDAL_NAMES) {
				String k = Fmt.stripColor(medal).toLowerCase(Locale.ROOT);
				tt.add(Component.literal("§7" + medal + ": §f" + (medals.has(k) ? medals.get(k).getAsInt() : 0)));
			}
		}
		if (Weights.SLAYER_NAMES.contains(key)) {
			JsonObject toTier = J.obj(NeuRepo.leveling(), "slayer_to_highest_tier");
			int max = toTier != null && toTier.has(key) ? toTier.get(key).getAsInt() : 5;
			for (int i = 0; i < Math.min(5, max); i++) {
				tt.add(Component.literal("§7T" + (i + 1) + " Kills: §c" + J.i(pj, "slayer.slayer_bosses." + key + ".boss_kills_tier_" + i, 0)));
			}
		}
		return tt;
	}


	private void renderWeight(GuiGraphicsExtractor g, int mouseX, int mouseY, SkyblockProfiles.SkyblockProfile selected) {
		if (!selected.skillsApiEnabled()) return;
		Map<String, Level> info = selected.getLevelingInfo();
		if (info == null) return;
		Weights.Result senither = Weights.senither(info);
		Weights.Result lily = Weights.lily(info, selected.getProfileJson());
		if (senither == null || lily == null) return;
		SkyblockProfiles profile = screen.getProfile();
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		var font = Draw.font();

		String s = "§aSenither Weight: §6" + Fmt.number(Math.round(senither.total()));
		Draw.centered(g, s, guiLeft + 63, guiTop + 18, true);
		int w = font.width(s);
		if (mouseX > guiLeft + 63 - w / 2 && mouseX < guiLeft + 63 + w / 2 && mouseY > guiTop + 12 && mouseY < guiTop + 12 + font.lineHeight) {
			List<Component> tt = weightLines(senither);
			if (PvConfig.get().useSoopyNetworth && profile.isProfileMaxSoopyWeight(screen.getProfileName())) {
				long pos = profile.getSoopyWeightLeaderboardPosition();
				tt.add(Component.empty());
				if (pos > 0) tt.add(Component.literal("§2#§6" + Fmt.number(pos) + "§a on soopy's weight leaderboard!"));
				else tt.add(Component.literal((pos == -2 ? "§eLoading" : "§cAn error occurred") + " on soopy's weight leaderboard"));
			}
			screen.tooltipToDisplay = tt;
		}
		String l = "§aLily Weight: §6" + Fmt.number(Math.round(lily.total()));
		Draw.centered(g, l, guiLeft + 63, guiTop + 28, true);
		int lw = font.width(l);
		if (mouseX > guiLeft + 63 - lw / 2 && mouseX < guiLeft + 63 + lw / 2 && mouseY > guiTop + 22 && mouseY < guiTop + 22 + font.lineHeight) {
			screen.tooltipToDisplay = weightLines(lily);
		}
	}

	private static List<Component> weightLines(Weights.Result r) {
		List<Component> tt = new ArrayList<>();
		tt.add(Component.literal("§aSkills: §6" + Fmt.number(Math.round(r.skills().raw()))));
		tt.add(Component.literal("§aSlayer: §6" + Fmt.number(Math.round(r.slayer().raw()))));
		tt.add(Component.literal("§aDungeons: §6" + Fmt.number(Math.round(r.dungeons().raw()))));
		return tt;
	}

	private void drawSideButtons(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		PvWidgets.sideButtons(screen, g, homeIcon, levelIcon, mouseX, mouseY);
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
		if (ProfileViewerScreen.isOnSecondPage()) return levelPage.mouseClicked(mouseX, mouseY, button);
		if (Draw.within(mouseX, mouseY, guiLeft + 24, guiTop + 155, 80, 12)) {
			clickedLoadStatusButton = true;
			return true;
		}
		if (Draw.within(mouseX, mouseY, guiLeft + 23, guiTop + 44, 81, 108)) {
			backgroundClickedX = mouseX;
			backgroundClickedButton = button;
			return true;
		}
		if (mouseX >= guiLeft + 128 && mouseX <= guiLeft + 216 && mouseY >= guiTop + 49 && mouseY <= guiTop + 113) {
			ProfileViewerScreen.setOnSecondPage(true);
			return true;
		}
		return false;
	}

	@Override
	public boolean wantsRealRelease() {
		return true; // panorama drag
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (backgroundClickedX != -1) {
			screen.backgroundRotation += (float) (mouseX - backgroundClickedX);
			backgroundClickedX = -1;
			backgroundClickedButton = -1;
		}
	}

	@Override
	public void resetCache() {
		levelPage.resetCache();
		entityPlayer = null;
		loadingEntity = false;
	}

	/** Fake player used for the 3D model: renders the real skin without being in the tab list. */
	private static final class PvPlayer extends RemotePlayer {
		private final Supplier<PlayerSkin> skin;

		PvPlayer(ClientLevel level, GameProfile profile) {
			super(level, profile);
			// Never added to the level, but 26.3's renderer reads the entity id (item model seeds).
			this.setId(-0x4E5550);
			this.skin = Minecraft.getInstance().getSkinManager().createLookup(profile, false);
			this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
		}

		/** Hides the floating name tag (NEU: setAlwaysRenderNameTag(false)); the body stays visible. */
		@Override
		public boolean isInvisibleTo(net.minecraft.world.entity.player.Player player) {
			return true;
		}

		@Override
		public PlayerSkin getSkin() {
			return skin.get();
		}
	}
}
