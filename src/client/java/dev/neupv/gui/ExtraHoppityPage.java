/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates HoppityPage.kt, Copyright (C) 2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Hoppity / Chocolate Factory sub-page of the Profile Stats tab (NEU HoppityPage.kt). */
public class ExtraHoppityPage extends PvPage {
	private static final Identifier PV_HOPPITY = Draw.tex("gui/pv_hoppity.png");
	/** NEU StringUtils.formatNumber: grouped, up to 3 decimals. */
	private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.US));

	private SkyblockProfiles.SkyblockProfile currentProfile;
	private boolean dataOk;

	private final List<UpgradeInfo> rabbitFamilyInfo = new ArrayList<>();
	private final List<UpgradeInfo> rabbitFamilyInfo2 = new ArrayList<>();
	private final List<UpgradeInfo> factoryModifiersInfo = new ArrayList<>();
	private final List<UpgradeInfo> otherModifiersInfo = new ArrayList<>();

	private long currentChocolate, prestigeChocolate, allTimeChocolate;
	private int prestigeLevel;
	private double multiplier, chocolatePerSecond;
	private int rawChocolatePerSecond;
	private int talismanChocolate;
	private String talisman;
	private int timeTowerCharges, timeTowerLevel;
	private long lastChargeTime, lastActivationTime, lastViewedChocolateFactory;
	private float muTimeTowerBonus;
	private long chocolateSpent;
	private String uuid, profileId;

	private final Map<String, String> rabbitToRarity = new HashMap<>();
	private final Map<Rarity, RarityStats> rarityStats = new EnumMap<>(Rarity.class);

	private List<String> tooltip = List.of();
	private ExtraHoppityLeaderboardRank.Rank lastRank = new ExtraHoppityLeaderboardRank.Rank(ExtraHoppityLeaderboardRank.Status.LOADING, -1);

	public ExtraHoppityPage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;

		// NEU's APIDataJson always defaulted "easter", so this message never showed; check the real json instead.
		JsonObject easter = J.obj(selected.getProfileJson(), "events.easter");
		if (easter == null) {
			Draw.centered(g, "§cMissing Profile Data", guiLeft + 220, guiTop + 101, true);
			return;
		}
		JsonObject hoppityJson = NeuRepo.constant("hoppity");
		if (hoppityJson == null) {
			Draw.centered(g, "§cMissing Repo Data", guiLeft + 220, guiTop + 101, true);
			return;
		}
		if (selected != currentProfile) {
			dataOk = getData(selected, easter, hoppityJson);
			currentProfile = selected;
		}
		if (!dataOk || rabbitToRarity.isEmpty()) {
			Draw.centered(g, "§cMissing Repo Data", guiLeft + 220, guiTop + 101, true);
			return;
		}

		Draw.full(g, PV_HOPPITY, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		// NEU Utils.renderShadowedString(str, x, y, len): centred on x
		Draw.centeredMaxWidth(g, "§eRabbit Family", guiLeft + 74, guiTop + 15, true, 105, 0xFFFFFFFF);
		Draw.centeredMaxWidth(g, "§eModifiers / Other", guiLeft + 74, guiTop + 109, true, 105, 0xFFFFFFFF);
		Draw.centeredMaxWidth(g, "§eChocolate Factory", guiLeft + 214, guiTop + 16, true, 105, 0xFFFFFFFF);
		Draw.centeredMaxWidth(g, "§eStats", guiLeft + 214, guiTop + 32, true, 105, 0xFFFFFFFF);

		alignedHover(g, "§eChocolate:", "§f" + Fmt.shortNumber(currentChocolate), guiLeft + 160, guiTop + 53, 110, mouseX, mouseY,
			List.of("§7Current Chocolate: §6" + fmt(currentChocolate)));
		alignedHover(g, "§eSince Prestige:", "§f" + Fmt.shortNumber(prestigeChocolate), guiLeft + 160, guiTop + 68, 110, mouseX, mouseY,
			List.of("§7Chocolate Since Prestige: §6" + fmt(prestigeChocolate)));
		alignedHover(g, "§eAll Time:", "§f" + Fmt.shortNumber(allTimeChocolate), guiLeft + 160, guiTop + 83, 110, mouseX, mouseY,
			List.of("§7All Time Chocolate: §6" + fmt(allTimeChocolate)));

		long next = chocolateForNextPrestige();
		String untilPrestige;
		if (next != 0) {
			double amount = next - (double) prestigeChocolate;
			untilPrestige = amount < 0 ? "§f0" : "§f" + Fmt.shortNumber(amount);
		} else {
			untilPrestige = "§f§lMax";
		}
		Draw.aligned(g, "§eUntil Prestige:", untilPrestige, guiLeft + 160, guiTop + 98, 110);
		// NEU divided by 0 when maxed (-> Infinity, clamped to 1): same result, made explicit
		float pct = next == 0 ? 1f : Math.max(0f, Math.min(1f, prestigeChocolate / (float) next));
		if (pct == 1f) Draw.goldBar(g, guiLeft + 160, guiTop + 109, 110, screen.startTime);
		else Draw.bar(g, guiLeft + 160, guiTop + 109, 110, pct);
		int xBar = guiLeft + 160, yBar = guiTop + 109;
		if (mouseX >= xBar && mouseX <= xBar + 110 && mouseY >= yBar && mouseY <= yBar + 5) {
			tooltip = next != 0 ? List.of("§6" + fmt(prestigeChocolate) + "§7/§6" + fmt(next)) : List.of("§6Maxed!");
		}

		Draw.aligned(g, "§eLast Updated:", lastViewedChocolateFactory == 0 ? "§fNever" : "§f" + ExtraPage.timeSinceMillisecond(lastViewedChocolateFactory),
			guiLeft + 160, guiTop + 117, 110);

		alignedHover(g, "§eMultiplier:", "§f" + round(multiplier, 3) + "x", guiLeft + 160, guiTop + 133, 110, mouseX, mouseY, List.of(
			"§7Normal Multiplier: §6" + round(multiplier, 3) + "x",
			"§7Multiplier with Time Tower: §d" + round(multiplier + ((timeTowerLevel * 0.1) + muTimeTowerBonus), 3) + "x"));

		double cps = roundD(chocolatePerSecond, 2);
		alignedHover(g, "§eChocolate/Second:", "§f" + fmt(cps), guiLeft + 160, guiTop + 148, 110, mouseX, mouseY, List.of(
			"§7Raw Chocolate/Second: §6" + fmt(rawChocolatePerSecond),
			"§7Chocolate/Millisecond: §6" + fmt(cps / 1000),
			"§7Chocolate/Second: §6" + fmt(cps),
			"§7Chocolate/Minute: §6" + fmt(cps * 60),
			"§7Chocolate/Hour: §6" + fmt(cps * 3600),
			"§7Chocolate/Day: §6" + fmt(cps * 86400),
			"§7Chocolate/Week: §6" + fmt(cps * 86400 * 7),
			"§7Chocolate/Fortnight: §6" + fmt(cps * 86400 * 14),
			"§7Chocolate/Month: §6" + fmt(cps * 86400 * 28),
			"§7Chocolate/Year: §6" + fmt(cps * 86400 * 365.28),
			"§7Chocolate/Leap Year: §6" + fmt(cps * 86400 * 366.28)));

		alignedHover(g, "§eChocolate Spent:", "§f" + Fmt.shortNumber(chocolateSpent), guiLeft + 160, guiTop + 163, 110, mouseX, mouseY,
			List.of("§7Chocolate Spent: §6" + fmt(chocolateSpent)));

		lastRank = ExtraHoppityLeaderboardRank.get(screen.getProfile(), uuid, profileId);
		alignedHover(g, "§eLeaderboard Rank:", "§f" + lastRank.display(), guiLeft + 160, guiTop + 178, 110, mouseX, mouseY, List.of(
			lastRank.additionalInfo(), "", "Data provided by the Elitebot API.", "§eClick to view on the Elitebot website."));

		displayInfo(g, rabbitFamilyInfo, 31, 32, mouseX, mouseY);
		displayInfo(g, rabbitFamilyInfo2, 42, 66, mouseX, mouseY);
		displayInfo(g, factoryModifiersInfo, 31, 125, mouseX, mouseY);
		displayInfo(g, otherModifiersInfo, 42, 159, mouseX, mouseY);

		drawRabbitStats(g, mouseX, mouseY);

		if (!tooltip.isEmpty()) {
			List<Component> tt = new ArrayList<>();
			for (String s : tooltip) tt.add(Component.literal("§7" + s));
			screen.tooltipToDisplay = tt;
			tooltip = List.of();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (button == 0 && mouseX >= guiLeft + 160 && mouseX <= guiLeft + 270 && mouseY >= guiTop + 178 && mouseY <= guiTop + 190) {
			ExtraHoppityLeaderboardRank.openWebsite(screen, lastRank);
			return true;
		}
		return false;
	}

	@Override
	public void resetCache() {
		currentProfile = null;
	}

	private long chocolateForNextPrestige() {
		return switch (prestigeLevel) {
			case 1 -> 150_000_000L;
			case 2 -> 1_000_000_000L;
			case 3 -> 4_000_000_000L;
			case 4 -> 10_000_000_000L;
			case 5 -> 30_000_000_000L;
			default -> 0L;
		};
	}

	private void alignedHover(GuiGraphicsExtractor g, String first, String second, int x, int y, int length, int mouseX, int mouseY, List<String> hover) {
		Draw.aligned(g, first, second, x, y, length);
		if (mouseX >= x && mouseX <= x + length && mouseY >= y && mouseY <= y + 13) tooltip = hover;
	}

	private void displayInfo(GuiGraphicsExtractor g, List<UpgradeInfo> list, int xPos, int yPos, int mouseX, int mouseY) {
		int x = screen.guiLeft() + xPos;
		int y = screen.guiTop() + yPos;
		for (UpgradeInfo info : list) {
			Draw.centered(g, info.colourCode() + info.level + "§f", x + 10, y + 26, true);
			Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, x, y, 20, 20, 0f, 20 / 256f, 0f, 20 / 256f, 256, 256);
			g.item(info.stack, x + 2, y + 2);
			if (mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + 20) tooltip = upgradeTooltip(info);
			x += 22;
		}
	}

	private List<String> upgradeTooltip(UpgradeInfo info) {
		List<String> tt = new ArrayList<>();
		switch (info.type) {
			case RABBIT_EMPLOYEES -> {
				if (info.level == 0) return fallbackList(info.displayName);
				tt.add(info.colourCode() + info.displayName + " §8- §7[" + info.level + "] " + info.colourCode() + info.suffixName());
				tt.add("");
				tt.add("§7Produces §6+" + fmt(info.level * info.extraCps) + " Chocolate §7per second.");
			}
			case HAND_BAKED_CHOCOLATE -> {
				if (info.level == 0) return fallbackList(info.displayName);
				tt.add("§d" + info.displayName + " " + roman(info.level));
				tt.add("");
				tt.add("§7Chocolate Per Click: §6+" + info.level + " Chocolate");
			}
			case TIME_TOWER -> {
				if (info.level == 0) return fallbackList(info.displayName);
				tt.add("§d" + info.displayName + " " + roman(info.level));
				tt.add("");
				tt.add("§6+" + round((info.level * 0.1) + muTimeTowerBonus, 1) + "x Chocolate §7per second for §a1h§7.");
				tt.add("§7Time Tower charges: §a" + timeTowerCharges + "§7/§a3");
				tt.add(lastActivationTime != 0 ? "§7Last Activation Time: §b" + ExtraPage.timeSinceMillisecond(lastActivationTime) : "§7Last Activation Time: §cNever");
				tt.add(lastChargeTime == 0 ? "§7Last Charge Time: §cNever" : "§7Last Charge Time: §b" + ExtraPage.timeSinceMillisecond(lastChargeTime));
			}
			case RABBIT_SHRINE -> {
				if (info.level == 0) return fallbackList(info.displayName);
				tt.add("§d" + info.displayName + " " + roman(info.level));
				tt.add("");
				tt.add("§7Increases §dodds §7of finding");
				tt.add("§aChocolate Rabbits §7of higher rarity");
				tt.add("§7by §a" + info.level + "% §7during §dHoppity's Hunt§7.");
			}
			case COACH_JACKRABBIT -> {
				if (info.level == 0) return fallbackList(info.displayName);
				tt.add("§d" + info.displayName + " " + roman(info.level));
				tt.add("");
				tt.add("§7Chocolate Multiplier increased by");
				tt.add("§6+" + round(info.level * 0.01, 2) + "x Chocolate §7per second.");
			}
			case CHOCOLATE_FACTORY -> {
				tt.add("§6" + info.displayName + " " + roman(info.level));
				String extra = chocolateFactoryTooltip(info.level);
				if (!extra.isEmpty()) tt.addAll(List.of(extra.split("\n")));
			}
			case RABBIT_BARN -> {
				int total = stats(Rarity.TOTAL).uniques;
				if (info.level == 0) {
					tt.add("§a" + info.displayName + " I");
					tt.add("§7Barn: §a" + total + "§7/§a20");
				} else {
					tt.add("§a" + info.displayName + " " + roman(info.level));
					tt.add("§7Rabbit Barn: §a" + total + "§7/§a" + ((info.level * 2) + 18));
				}
			}
			case TALISMAN -> {
				if (info.level == 0) return fallbackList(info.displayName);
				// NEU: item tooltip without the trailing "Cost" section
				PvItem item = talisman == null ? null : LegacyItems.fromRepo(talisman);
				List<String> raw = new ArrayList<>();
				if (item != null) {
					raw.add(item.displayName);
					raw.addAll(item.lore);
				}
				int end = raw.size();
				while (end > 0 && !Fmt.stripColor(raw.get(end - 1)).startsWith("Cost")) end--;
				return new ArrayList<>(raw.subList(0, Math.max(0, end - 2)));
			}
			case OTHER -> tt.add("§d§l:3");
		}
		return tt;
	}

	private List<String> fallbackList(String name) {
		String player = screen.getProfile().getDisplayName();
		return List.of("§d" + name + " §7hasn't been obtained yet!", "§8" + player + " §8hasn't obtained this yet");
	}

	private void drawRabbitStats(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = screen.guiLeft() + 299;
		int y = screen.guiTop() + 14;
		String player = screen.getProfile().getDisplayName();
		for (Rarity rarity : Rarity.values()) {
			RarityStats s = stats(rarity);
			Draw.aligned(g, rarity.displayName(), "§f" + s.uniques + "/" + s.maximum, x + 30, y + 7, 90);
			Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, x, y, 20, 20, 0f, 20 / 256f, 0f, 20 / 256f, 256, 256);
			if (mouseX >= x && mouseX <= x + 120 && mouseY >= y && mouseY <= y + 20) {
				List<String> tt = new ArrayList<>();
				tt.add("§l" + rarity.displayName() + " Rabbits");
				if (s.uniques < 1) {
					if (rarity == Rarity.TOTAL) tt.add("§8" + player + " hasn't found any rabbits.");
					else tt.add("§8" + player + " hasn't found any " + rarity.apiName + " rabbits.");
				} else {
					tt.add(rarity.apiName + " rabbits found: §a" + s.uniques + "§7/§a" + s.maximum);
					tt.add("Duplicate Rabbits: §a" + s.duplicates);
					tt.add("Total Rabbits Found: §a" + (s.uniques + s.duplicates));
					tt.add("");
					tt.add("§6+" + fmt(s.chocolatePerSecond) + " Chocolate §7per second.");
					tt.add("§6+" + round(s.multiplier, 3) + "x Chocolate §7per second.");
				}
				tooltip = tt;
			}
			g.item(rarity.stack(), x + 2, y + 2);
			y += 22;
		}
	}

	private RarityStats stats(Rarity r) {
		return rarityStats.computeIfAbsent(r, k -> new RarityStats());
	}

	/** NEU getData(): computes everything once per selected profile. */
	private boolean getData(SkyblockProfiles.SkyblockProfile selected, JsonObject easter, JsonObject hoppityJson) {
		JsonObject member = selected.getProfileJson();
		double baseMultiplier = J.bool(member, "profile.cookie_buff_active", false) ? 1.25 : 1.0;
		JsonObject activePet = selected.getActivePet();
		if (activePet != null && "RABBIT".equals(J.str(activePet, "type", "")) && "MYTHIC".equals(J.str(activePet, "tier", ""))) {
			// calculation is 0.01 + 0.0004 per pet level
			baseMultiplier += 0.01 + (petLevel("RABBIT", "MYTHIC", J.dbl(activePet, "exp", 0)) * 0.0004);
		}

		rabbitToRarity.clear();
		rarityStats.clear();
		muTimeTowerBonus = 0; // NEU never reset this between profiles
		profileId = J.str(selected.getOuterProfileJson(), "profile_id", null);
		if (profileId != null) profileId = profileId.replace("-", "");
		uuid = screen.getProfile().getUuidString();

		JsonObject hoppityData = J.obj(hoppityJson, "hoppity");
		JsonObject rabbitRarities = J.obj(hoppityData, "rarities");
		JsonObject specialRabbits = J.obj(hoppityData, "special");
		JsonObject prestigeMultipliers = J.obj(hoppityData, "prestigeMultipliers");
		JsonObject talismanChocolateData = J.obj(hoppityData, "talisman");
		if (rabbitRarities == null || specialRabbits == null || prestigeMultipliers == null || talismanChocolateData == null) return false;

		getTalismanTier(selected, talismanChocolateData);

		for (Map.Entry<String, JsonElement> rarity : rabbitRarities.entrySet()) {
			JsonArray rabbits = J.arrOrEmpty(rarity.getValue(), "rabbits");
			for (JsonElement r : rabbits) rabbitToRarity.put(r.getAsString(), rarity.getKey());
			Rarity rr = Rarity.fromApiName(rarity.getKey());
			if (rr != null) stats(rr).maximum = rabbits.size();
		}

		Set<String> foundMythicRabbits = new HashSet<>();
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(easter, "rabbits").entrySet()) {
			if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) continue;
			String rarityName = rabbitToRarity.get(e.getKey());
			Rarity rr = rarityName == null ? null : Rarity.fromApiName(rarityName);
			if (rr == null) continue;
			stats(rr).addAmount(e.getValue().getAsInt());
			if (rr == Rarity.MYTHIC) foundMythicRabbits.add(e.getKey());
		}

		for (Map.Entry<String, JsonElement> rarity : rabbitRarities.entrySet()) {
			Rarity rr = Rarity.fromApiName(rarity.getKey());
			if (rr == null) continue;
			RarityStats s = stats(rr);
			s.chocolatePerSecond = J.i(rarity.getValue(), "chocolate", 0) * s.uniques;
			s.multiplier = J.dbl(rarity.getValue(), "multiplier", 0) * s.uniques;
		}

		RarityStats mythic = stats(Rarity.MYTHIC);
		for (String m : foundMythicRabbits) {
			if (m.equals("sigma")) mythic.chocolatePerSecond += 5 * foundMythicRabbits.size();
			if (m.equals("mu")) muTimeTowerBonus = 0.7f;
			JsonObject special = J.obj(specialRabbits, m);
			if (special == null) continue; // NEU crashed on a mythic rabbit missing from "special"
			mythic.chocolatePerSecond += J.i(special, "chocolate", 0);
			mythic.multiplier += J.dbl(special, "multiplier", 0);
		}

		RarityStats total = stats(Rarity.TOTAL);
		for (Rarity r : Rarity.values()) {
			if (r == Rarity.TOTAL) continue;
			RarityStats s = stats(r);
			total.uniques += s.uniques;
			total.duplicates += s.duplicates;
			total.chocolatePerSecond += s.chocolatePerSecond;
			total.multiplier += s.multiplier;
			total.maximum += s.maximum;
		}

		rabbitFamilyInfo.clear();
		rabbitFamilyInfo2.clear();
		factoryModifiersInfo.clear();
		otherModifiersInfo.clear();

		JsonObject employees = J.objOrEmpty(easter, "employees");
		JsonObject timeTowerInfo = J.objOrEmpty(easter, "time_tower");
		int coachLevel = J.i(easter, "chocolate_multiplier_upgrades", 0);
		int barnLevel = J.i(easter, "rabbit_barn_capacity_level", 1);

		rabbitFamilyInfo.add(new UpgradeInfo(Icons.RABBIT_BRO, J.i(employees, "rabbit_bro", 0), UpgradeType.RABBIT_EMPLOYEES, "Rabbit Bro", 1));
		rabbitFamilyInfo.add(new UpgradeInfo(Icons.RABBIT_COUSIN, J.i(employees, "rabbit_cousin", 0), UpgradeType.RABBIT_EMPLOYEES, "Rabbit Cousin", 2));
		rabbitFamilyInfo.add(new UpgradeInfo(Icons.RABBIT_SIS, J.i(employees, "rabbit_sis", 0), UpgradeType.RABBIT_EMPLOYEES, "Rabbit Sis", 3));
		rabbitFamilyInfo.add(new UpgradeInfo(Icons.RABBIT_DADDY, J.i(employees, "rabbit_father", 0), UpgradeType.RABBIT_EMPLOYEES, "Rabbit Daddy", 4));
		rabbitFamilyInfo2.add(new UpgradeInfo(Icons.RABBIT_GRANNY, J.i(employees, "rabbit_grandma", 0), UpgradeType.RABBIT_EMPLOYEES, "Rabbit Granny", 5));
		rabbitFamilyInfo2.add(new UpgradeInfo(Icons.RABBIT_UNCLE, J.i(employees, "rabbit_uncle", 0), UpgradeType.RABBIT_EMPLOYEES, "Rabbit Uncle", 6));
		rabbitFamilyInfo2.add(new UpgradeInfo(Icons.RABBIT_DOG, J.i(employees, "rabbit_dog", 0), UpgradeType.RABBIT_EMPLOYEES, "Rabbit Dog", 7));

		prestigeLevel = J.i(easter, "chocolate_level", 1);
		timeTowerLevel = J.i(timeTowerInfo, "level", 0);
		if (prestigeLevel > 1) timeTowerLevel = Math.max(timeTowerLevel, 1);

		factoryModifiersInfo.add(new UpgradeInfo(Icons.HAND_BAKED, J.i(easter, "click_upgrades", 0) + 1, UpgradeType.HAND_BAKED_CHOCOLATE, "Hand-Baked Chocolate", 0));
		factoryModifiersInfo.add(new UpgradeInfo(Icons.TIME_TOWER, timeTowerLevel, UpgradeType.TIME_TOWER, "Time Tower", 0));
		factoryModifiersInfo.add(new UpgradeInfo(Icons.RABBIT_SHRINE, J.i(easter, "rabbit_rarity_upgrades", 0), UpgradeType.RABBIT_SHRINE, "Rabbit Shrine", 0));
		factoryModifiersInfo.add(new UpgradeInfo(Icons.COACH_JACKRABBIT, coachLevel, UpgradeType.COACH_JACKRABBIT, "Coach Jackrabbit", 0));
		otherModifiersInfo.add(new UpgradeInfo(Icons.PRESTIGE, prestigeLevel, UpgradeType.CHOCOLATE_FACTORY, "Chocolate Factory", 0));
		otherModifiersInfo.add(new UpgradeInfo(Icons.RABBIT_BARN, barnLevel, UpgradeType.RABBIT_BARN, "Rabbit Barn", 0));
		PvItem talismanItem = talisman == null ? null : LegacyItems.fromRepo(talisman);
		otherModifiersInfo.add(new UpgradeInfo(talismanItem != null ? talismanItem.stack() : Icons.TALISMAN, talismanChocolate / 10,
			UpgradeType.TALISMAN, "Chocolate Talisman", 0));

		currentChocolate = J.lng(easter, "chocolate", 0);
		prestigeChocolate = J.lng(easter, "chocolate_since_prestige", 0);
		allTimeChocolate = J.lng(easter, "total_chocolate", 0);
		timeTowerCharges = J.i(timeTowerInfo, "charges", 0);
		lastChargeTime = J.lng(timeTowerInfo, "last_charge_time", 0);
		lastActivationTime = J.lng(timeTowerInfo, "activation_time", 0);
		lastViewedChocolateFactory = J.lng(easter, "last_viewed_chocolate_factory", 0);

		double prestigeMultiplier = J.dbl(prestigeMultipliers, String.valueOf(prestigeLevel), 0);
		double coachMultiplier = 0.01 * coachLevel;
		multiplier = baseMultiplier + prestigeMultiplier + coachMultiplier + total.multiplier;

		int employeeChocolate = 0;
		for (UpgradeInfo u : rabbitFamilyInfo) employeeChocolate += u.extraCps * u.level;
		for (UpgradeInfo u : rabbitFamilyInfo2) employeeChocolate += u.extraCps * u.level;
		rawChocolatePerSecond = total.chocolatePerSecond + employeeChocolate + talismanChocolate;
		chocolatePerSecond = rawChocolatePerSecond * multiplier;
		chocolateSpent = J.lng(easter, "shop.chocolate_spent", 0);
		return true;
	}

	private void getTalismanTier(SkyblockProfiles.SkyblockProfile selected, JsonObject talismanChocolateData) {
		talisman = null;
		talismanChocolate = 0;
		Map<String, List<PvItem>> items = selected.getInventoryInfo();
		for (String inv : List.of("talisman_bag", "inv_contents")) {
			List<PvItem> list = items.get(inv);
			if (list == null) continue;
			for (PvItem item : list) {
				if (item == null || item.skyblockId == null || !talismanChocolateData.has(item.skyblockId)) continue;
				int cps = talismanChocolateData.get(item.skyblockId).getAsInt();
				if (cps > talismanChocolate) {
					talisman = item.skyblockId;
					talismanChocolate = cps;
				}
			}
		}
	}

	/** NEU PetLeveling.getPetLevelingForPet(...).getPetLevel(exp).currentLevel, from constants/pets.json. */
	static int petLevel(String petId, String rarity, double exp) {
		JsonObject pets = NeuRepo.constant("pets");
		JsonArray levelsArr = J.arr(pets, "pet_levels");
		if (levelsArr == null) return 1;
		List<Long> levels = new ArrayList<>();
		for (JsonElement e : levelsArr) levels.add(e.getAsLong());
		JsonObject offsets = J.obj(pets, "pet_rarity_offset");
		int maxLevel = 100;
		JsonObject custom = J.obj(pets, "custom_pet_leveling." + petId);
		if (custom != null) {
			JsonArray customLevels = J.arr(custom, "pet_levels");
			int type = J.i(custom, "type", 0);
			if (customLevels != null) {
				List<Long> cl = new ArrayList<>();
				for (JsonElement e : customLevels) cl.add(e.getAsLong());
				if (type == 1) levels.addAll(cl);
				else if (type == 2) levels = cl;
			}
			maxLevel = J.i(custom, "max_level", maxLevel);
			JsonObject ro = J.obj(custom, "rarity_offset");
			if (ro != null) offsets = ro;
		}
		int offset = J.i(offsets, rarity, -1);
		if (offset < 0) return 1;
		long cumulative = 0;
		int level = 1;
		for (int i = offset; i < Math.min(levels.size(), offset + maxLevel - 1); i++) {
			cumulative += levels.get(i);
			if (cumulative <= exp) level++;
			else break;
		}
		return level;
	}

	private static String fmt(double n) {
		synchronized (NUMBER_FORMAT) {
			return NUMBER_FORMAT.format(n);
		}
	}

	private static double roundD(double v, int decimals) {
		double m = Math.pow(10, decimals);
		return Math.round(v * m) / m;
	}

	/** Kotlin's Double.toString of roundToDecimals(...) (e.g. "2.5", "3.0"). */
	private static String round(double v, int decimals) {
		return String.valueOf(roundD(v, decimals));
	}

	private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
	private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

	static String roman(int n) {
		if (n <= 0) return "?";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ROMAN_VALUES.length; i++) {
			while (n >= ROMAN_VALUES[i]) {
				sb.append(ROMAN_SYMBOLS[i]);
				n -= ROMAN_VALUES[i];
			}
		}
		return sb.toString();
	}

	private static String chocolateFactoryTooltip(int level) {
		return switch (level) {
			case 1 -> "§7Chocolate Production Multiplier: §61x\n§7Max Rabbit Rarity: §a§lUNCOMMON\n§7Max Chocolate: §6500M\n§7Max Employee: [120] §9Assistant";
			case 2 -> "Chocolate Production Multiplier: §61.1x\nMax Rabbit Rarity: §9§lRARE\nMax Chocolate: §61.2B\nMax Employee: [140] §5Manager";
			case 3 -> "Chocolate Production Multiplier: §61.25x\nMax Rabbit Rarity: §5§lEPIC\nMax Chocolate: §64B\nMax Employee: [160] §5Manager";
			case 4 -> "Chocolate Production Multiplier: §61.5x\nMax Rabbit Rarity: §6§lLEGENDARY\nMax Chocolate: §610B\nMax Employee: [180] §6Director";
			case 5 -> "Chocolate Production Multiplier: §62x\nMax Rabbit Rarity: §d§lMYTHIC\nMax Chocolate: §625B\nMax Employee: [200] §dExecutive";
			case 6 -> "Chocolate Production Multiplier: §62.5x\nMax Rabbit Rarity: §b§lDIVINE\nMax Chocolate: §660B\nMax Employee: [220] §bBoard Member";
			default -> "";
		};
	}

	enum UpgradeType { RABBIT_EMPLOYEES, HAND_BAKED_CHOCOLATE, TIME_TOWER, RABBIT_SHRINE, COACH_JACKRABBIT, CHOCOLATE_FACTORY, RABBIT_BARN, TALISMAN, OTHER }

	record UpgradeInfo(ItemStack stack, int level, UpgradeType type, String displayName, int extraCps) {
		String colourCode() {
			return switch (type) {
				case RABBIT_EMPLOYEES -> level <= 9 ? "§f" : level <= 74 ? "§a" : level <= 124 ? "§9" : level <= 174 ? "§5"
					: level <= 199 ? "§6" : level <= 219 ? "§d" : level <= 225 ? "§b" : "§7";
				case HAND_BAKED_CHOCOLATE -> level >= 10 ? "§d" : "§7";
				case TIME_TOWER -> level >= 15 ? "§d" : "§7";
				case RABBIT_SHRINE, COACH_JACKRABBIT -> level >= 20 ? "§d" : "§7";
				case CHOCOLATE_FACTORY -> level >= 6 ? "§d" : "§7";
				case RABBIT_BARN -> level >= 222 ? "§d" : "§7";
				case TALISMAN -> level >= 5 ? "§d" : "§7";
				default -> "§7";
			};
		}

		String suffixName() {
			if (type != UpgradeType.RABBIT_EMPLOYEES) return "";
			return level <= 9 ? "Intern" : level <= 74 ? "Employee" : level <= 124 ? "Assistant" : level <= 174 ? "Manager"
				: level <= 199 ? "Director" : level <= 219 ? "Executive" : level <= 225 ? "Board Member" : "";
		}
	}

	private static final class RarityStats {
		int uniques, duplicates, chocolatePerSecond, maximum;
		double multiplier;

		void addAmount(int amount) {
			if (amount == 0) return;
			uniques++;
			duplicates += amount - 1;
		}
	}

	private enum Rarity {
		TOTAL("Total", "§c", "ewogICJ0aW1lc3RhbXAiIDogMTcxMTYzNDM5MTg3OCwKICAicHJvZmlsZUlkIiA6ICIxNmQ4NjI4NzYzMWY0NDY2OGQ0NDM2ZTJlY2IwNTllNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZXphVG91cm5leSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iNzllN2YzMzQxYjY3MmQ5ZGU2NTY0Y2JhY2EwNTJhNmE3MjNlYTQ2NmEyZTY2YWYzNWJhMWJhODU1ZjBkNjkyIgogICAgfQogIH0KfQ=="),
		COMMON("Common", "§f", "ewogICJ0aW1lc3RhbXAiIDogMTcxMTYzNTExNDE5OCwKICAicHJvZmlsZUlkIiA6ICI5ZDE1OGM1YjNiN2U0ZGNlOWU0OTA5MTdjNmJlYmM5MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTbm9uX1NTIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE1ZDBmMGMzNjhlNTRkMjBlM2M1ZTU1MGEwNGE0NjlkMDE2MWIxZmVjZjI2YzhlNTE4MzE4YzA5ZTExMzRmNmIiCiAgICB9CiAgfQp9"),
		UNCOMMON("Uncommon", "§a", "ewogICJ0aW1lc3RhbXAiIDogMTcxMTYzNTM2MzM0NCwKICAicHJvZmlsZUlkIiA6ICI0MzFhMmRlYTQ4YTE0NTMxYjEyZDU5MzY0NDUxNmIyNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJpQ2FwdGFpbk5lbW8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjgxMDdjNTIwMWVhMjFiYWE4OTU1MTc1MTBiMDA3ZjVmNjE1ZTNjNjYxNWRmNjk2YjkwNmFiOThlNmY5ZjA2IgogICAgfQogIH0KfQ"),
		RARE("Rare", "§9", "ewogICJ0aW1lc3RhbXAiIDogMTcxMTYzNTI0MDkwNCwKICAicHJvZmlsZUlkIiA6ICJmY2ZhYTg0MzA0YjE0NDUxOThkNWYxNzQ3ZjI0Y2Q5MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMYXJzVGhlV29sZiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80NTZhMjA5ODAzZTFlOGViOTQxMTc3ZTJjYzhhMDFiY2VhODg0ZDk0ZGM3N2MzOGUyMmY1Y2QxYTg2MmY4OWNhIgogICAgfQogIH0KfQ"),
		EPIC("Epic", "§5", "ewogICJ0aW1lc3RhbXAiIDogMTcxMTYzNDg4OTY5NywKICAicHJvZmlsZUlkIiA6ICIzYmFlMTVhMWU0Zjg0ZTc5OWE3N2QwZDBhZTNlZDc5NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJiYXlyb25fZ2FtZXJfMjU0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzMzNmYwYTUxY2ZiMTBiODE5ZGUxZmNkZjM0NzBmM2QzMzZkYjI2MWQxZmZiYTk0M2E3ODU2NTQwODA5ZGI0ZWUiCiAgICB9CiAgfQp9"),
		LEGENDARY("Legendary", "§6", "ewogICJ0aW1lc3RhbXAiIDogMTcxMTYzNDc1NTM1NCwKICAicHJvZmlsZUlkIiA6ICI5YzM5OTdhMjVjNWY0NmY0OWZlMWFhY2RlZjRiMmMwNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLaWxsZXJmcmVkZHk4OTQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGUxYzE3MGI0ZjZjMzc2MTRlZTk2MDk2MDE2NDg1NWFiNzQyNmNlZmI0NDA5N2Y3OTU3ZmEzMGE2N2I5MzVlZiIKICAgIH0KICB9Cn0"),
		MYTHIC("Mythic", "§d", "ewogICJ0aW1lc3RhbXAiIDogMTcxNTEwMzM0ODczNywKICAicHJvZmlsZUlkIiA6ICI3OTZjMDBhNmY0MDA0Mjg2OWMyMTIyNjc0ZmI0MWNiZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTZWRvbnlhIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzUwZGU1NDVmN2M0MGE2ZWM4ODhhZTNjZjlhZTk4M2Q5Mjc4OWRmNzc4Y2Y1NWQ5ZjVhM2QzY2IzYzZkMWI3NjgiCiAgICB9CiAgfQp9"),
		DIVINE("Divine", "§b", "ewogICJ0aW1lc3RhbXAiIDogMTcxNTEwMzA0NzIwNywKICAicHJvZmlsZUlkIiA6ICJlZTg4M2RmMjM0ZWI0YWM1YTFlNDEwODhhYzZkZWIxNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUdW5lc0Jsb2NrIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2JmYzQ3MWUwMThiZjQzZjMxNWIwMGVjNDE5YWU3YTdmOWRlNjY5MDQ0NGIzMGU5ZTVkMzRjZjc5YTMxNmMwMjMiCiAgICB9CiAgfQp9");

		final String apiName;
		final String colourCode;
		private final String texture;
		private ItemStack stack;

		Rarity(String apiName, String colourCode, String texture) {
			this.apiName = apiName;
			this.colourCode = colourCode;
			this.texture = texture;
		}

		String displayName() {
			return colourCode + apiName;
		}

		ItemStack stack() {
			if (stack == null) stack = LegacyItems.skull(displayName(), texture);
			return stack;
		}

		static Rarity fromApiName(String apiName) {
			for (Rarity r : values()) if (r.apiName.toLowerCase(Locale.ROOT).equals(apiName)) return r;
			return null;
		}
	}

	/** Upgrade icons (NEU skulls by ThatGravyBoat / catgirlseraid, vanilla items). */
	private static final class Icons {
		static final ItemStack RABBIT_BRO = LegacyItems.skull("§fRabbit Bro", "ewogICJ0aW1lc3RhbXAiIDogMTcxMjU5NDI0NjM2MywKICAicHJvZmlsZUlkIiA6ICJjZjc4YzFkZjE3ZTI0Y2Q5YTIxYmU4NWQ0NDk5ZWE4ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYXR0c0FybW9yU3RhbmRzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzI4NzkzNGJkZDlkZjI3MDViMjUxYmI5OTdlMDI5YjE4YzFlOTRkZjEyOTkyYjgxMDdlNzQ0OTdiMjA1Y2E3ZTgiCiAgICB9CiAgfQp9");
		static final ItemStack RABBIT_COUSIN = LegacyItems.skull("§fRabbit Cousin", "ewogICJ0aW1lc3RhbXAiIDogMTcxMjU5NDI2ODkxNCwKICAicHJvZmlsZUlkIiA6ICJlMjc5NjliODYyNWY0NDg1YjkyNmM5NTBhMDljMWMwMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLRVZJTktFTE9LRSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hOTgyODI1YzAxYjY1OGYzNDhhMDk5YjQ1NzkwMjlhMTgwZDJlNDE1MTgzOTUxYjJlNmU1ZTI3MjU3ZGY0MjU0IgogICAgfQogIH0KfQ==");
		static final ItemStack RABBIT_SIS = LegacyItems.skull("§fRabbit Sis", "ewogICJ0aW1lc3RhbXAiIDogMTcxMjg0NzA5MzAxMSwKICAicHJvZmlsZUlkIiA6ICIyMWNjMzkxZmNkMjc0NzY5OTg5Y2M3M2VjYWRiNTE3YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJHT1NUTFk5NyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZDA3NmUwZTNkNDA3MmQwZmZmZWUwYTg3YTVkNzI2ZmMzNGIyYmNlYzM4YzI2NGZiOWI2Nzg3MWE4ZWFkNjMzIgogICAgfQogIH0KfQ==");
		static final ItemStack RABBIT_DADDY = LegacyItems.skull("§fRabbit Daddy", "ewogICJ0aW1lc3RhbXAiIDogMTcxMjg0NzA0NzAwNSwKICAicHJvZmlsZUlkIiA6ICIzOThiZGM3NWVhYzQ0ZjMzYWEyMDBiMTYyNTRmMDhlOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJJa2h3YW4wNTEwIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU3Y2FiMGMzNGQ3ZGRjZjcyZGI1NmZmMzZmMjg4M2Y1NTRjZmY3NmViNWQzYjNlMDU2MjMzODAzNmM5NzYwNDMiCiAgICB9CiAgfQp9");
		static final ItemStack RABBIT_GRANNY = LegacyItems.skull("§fRabbit Granny", "ewogICJ0aW1lc3RhbXAiIDogMTcxMjU5NDIyNDA2NCwKICAicHJvZmlsZUlkIiA6ICI2OGVmMmM5NTc5NjM0MjE4YjYwNTM5YWVlOTU3NWJiNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVNdWx0aUFjb3VudCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kNmViMmQ4NWVlOGUzYWYxYzJlYzkzNGJlYjcwYTM5YzVlNzY2YjIzYmRhYjYzMjEwYmQyYWFjZDczY2JiZmM4IgogICAgfQogIH0KfQ==");
		static final ItemStack RABBIT_UNCLE = LegacyItems.skull("§fRabbit Uncle", "ewogICJ0aW1lc3RhbXAiIDogMTcxNDc3NjY2NDI1NSwKICAicHJvZmlsZUlkIiA6ICIzZWUxYWRlMzljZDI0ZjFkOWYwODliYjA2ZTkzNTY5YSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSdXNvR01SIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzc2MDkyY2FmMDkxMGEzMzhmZTRmMTIxODI2MzFiNWZjYTY1NTQ5OTcwMmUwMmM0MTQ0MTNjNzU1Y2VjOGY5YTEiCiAgICB9CiAgfQp9");
		static final ItemStack RABBIT_DOG = LegacyItems.skull("§fRabbit Dog", "ewogICJ0aW1lc3RhbXAiIDogMTcxNDk1OTAyNzAyNCwKICAicHJvZmlsZUlkIiA6ICJiZDNhNWRmY2ZkZjg0NDczOTViZDJiZmUwNGY0YzAzMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJwcmVja3Jhc25vIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzM1Y2E5OGJlZGUzODY1ZGQxMjA1ZTRkMDkxMDM2Y2Q5ZGMzNjc5MWI4M2VhNGUwZmY0YTk5YWQ2MWI3MWU4OTgiCiAgICB9CiAgfQp9");
		static final ItemStack HAND_BAKED = LegacyItems.icon(Items.COOKIE, "§dHand-Baked Chocolate");
		static final ItemStack TIME_TOWER = LegacyItems.icon(Items.CLOCK, "§dTime Tower");
		static final ItemStack RABBIT_SHRINE = LegacyItems.icon(Items.RABBIT_FOOT, "§dRabbit Shrine");
		static final ItemStack COACH_JACKRABBIT = LegacyItems.skull("§dCoach Jackrabbit", "ewogICJ0aW1lc3RhbXAiIDogMTcxMzAyMjkyOTYwNCwKICAicHJvZmlsZUlkIiA6ICI2NGY0MGFiNzFmM2E0NGZiYjg0N2I5ZWFhOWZjNDRlNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJvZGF2aWRjZXNhciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iYzBjYzY3ZTc5YzIyOGU1NDFlNjhhZWIxZDgxZWQ3YWY1MTE2NjYyMmFkNGRiOTQxN2Q3YTI5ZDFiODlhZjk1IgogICAgfQogIH0KfQ==");
		static final ItemStack PRESTIGE = LegacyItems.icon(Items.DROPPER, "§6Chocolate Factory");
		static final ItemStack RABBIT_BARN = LegacyItems.icon(Items.OAK_FENCE, "§aRabbit Barn");
		static final ItemStack TALISMAN = LegacyItems.icon(Items.DYE.gray(), "§dChocolate Talisman");
	}
}
