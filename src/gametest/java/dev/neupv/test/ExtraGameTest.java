package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;

/** Profile Stats tab (NEU ExtraPage) and its Hoppity sub-page on synthetic v2 data. */
public class ExtraGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				PvConfig.get().showPronounsInPv = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(ExtraGameTest::member, p -> {}), TestData.hypixelPlayer());
				holder[0] = profiles;
				checks(profiles);
			});
			context.runOnClient(mc -> {
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.EXTRA;
				ProfileViewerScreen.setOnSecondPage(false);
				mc.setScreenAndShow(new ProfileViewerScreen(holder[0]));
			});
			context.waitTicks(60);
			context.takeScreenshot("extra-stats");
			context.runOnClient(mc -> ProfileViewerScreen.setOnSecondPage(true));
			context.waitTicks(40);
			context.takeScreenshot("extra-hoppity");
			context.runOnClient(mc -> ProfileViewerScreen.setOnSecondPage(false));
		}
	}

	private static void checks(SkyblockProfiles profiles) {
		SkyblockProfiles.SkyblockProfile p = profiles.getProfile("Apple");
		TestData.check(p != null, "profile Apple loaded");
		TestData.check(J.dbl(p.getOuterProfileJson(), "banking.balance", 0) == 1_000_000_000, "coop bank read from the outer profile");
		TestData.check(J.i(p.getProfileJson(), "fairy_soul.total_collected", 0) == 241, "fairy souls in member json");
		TestData.check(J.obj(p.getProfileJson(), "events.easter") != null, "hoppity data present");
		TestData.check(NeuRepo.constant("hoppity") != null, "hoppity.json repo constant");
		TestData.check(NeuRepo.constant("essenceshops") != null, "essenceshops.json repo constant");
		TestData.check(p.getInventoryInfo().get("talisman_bag").stream().anyMatch(i -> i != null && "RICH_CHOCOLATE_CHUNK".equals(i.skyblockId)),
			"chocolate talisman decoded from the talisman bag");
	}

	private static void member(JsonObject m) {
		JsonObject profile = new JsonObject();
		profile.addProperty("first_join", System.currentTimeMillis() - 412L * 86_400_000L / 2); // 206 days ago
		profile.addProperty("bank_account", 25_000_000);
		profile.addProperty("cookie_buff_active", true);
		profile.addProperty("personal_bank_upgrade", 3);
		m.add("profile", profile);

		m.add("fairy_soul", TestData.obj("total_collected", 241));

		JsonObject currencies = J.obj(m, "currencies");
		JsonObject essence = new JsonObject();
		String[] types = {"WITHER", "SPIDER", "UNDEAD", "DRAGON", "GOLD", "DIAMOND", "ICE", "CRIMSON"};
		int[] amounts = {12_400, 3_100, 1_250_000, 45_600, 98_000, 34_500, 7_800, 210_000};
		for (int i = 0; i < types.length; i++) essence.add(types[i], TestData.obj("current", amounts[i]));
		currencies.add("essence", essence);

		JsonObject perks = new JsonObject();
		perks.addProperty("catacombs_boss_luck", 4);
		perks.addProperty("catacombs_looting", 3);
		J.obj(m, "player_data").add("perks", perks);

		JsonObject stats = new JsonObject();
		JsonObject auctions = new JsonObject();
		auctions.addProperty("bids", 1532);
		auctions.addProperty("highest_bid", 1_250_000_000);
		auctions.addProperty("won", 812);
		auctions.addProperty("created", 4210);
		auctions.addProperty("gold_spent", 9_870_000_000L);
		auctions.addProperty("gold_earned", 12_340_000_000L);
		stats.add("auctions", auctions);
		JsonObject milestone = new JsonObject();
		milestone.addProperty("ores_mined", 254_310);
		milestone.addProperty("sea_creatures_killed", 18_442);
		stats.add("pets", TestData.obj("milestone", milestone));
		JsonObject fished = new JsonObject();
		fished.addProperty("total", 52_118);
		fished.addProperty("treasure", 1_203);
		fished.addProperty("large_treasure", 87);
		stats.add("items_fished", fished);
		JsonObject kills = new JsonObject();
		String[] mobs = {"total", "zealot_enderman", "voidling_extremist", "skeleton_emperor", "zombie", "spider", "crypt_ghoul",
			"enderman", "obsidian_wither", "sea_walker", "night_squid", "lapis_zombie", "blaze"};
		int[] counts = {182_340, 61_204, 3_120, 54, 8_870, 4_512, 2_310, 15_220, 980, 1_404, 620, 5_430, 7_002};
		for (int i = 0; i < mobs.length; i++) kills.addProperty(mobs[i], counts[i]);
		stats.add("kills", kills);
		JsonObject deaths = new JsonObject();
		deaths.addProperty("total", 1_045);
		deaths.addProperty("void", 412);
		deaths.addProperty("fall", 233);
		deaths.addProperty("master_catacombs_floor_seven", 87);
		deaths.addProperty("zealot_enderman", 12);
		deaths.addProperty("lava", 64);
		deaths.addProperty("drowning", 5);
		deaths.addProperty("suffocation", 3);
		stats.add("deaths", deaths);
		m.add("player_stats", stats);

		// Hoppity / Chocolate Factory (events.easter, exact v2 field names)
		JsonObject easter = new JsonObject();
		easter.addProperty("chocolate", 1_284_551_002L);
		easter.addProperty("chocolate_since_prestige", 6_120_400_000L);
		easter.addProperty("total_chocolate", 21_450_900_000L);
		easter.addProperty("click_upgrades", 9);
		easter.addProperty("chocolate_level", 4);
		easter.addProperty("chocolate_multiplier_upgrades", 18);
		easter.addProperty("rabbit_barn_capacity_level", 120);
		easter.addProperty("rabbit_rarity_upgrades", 16);
		easter.addProperty("last_viewed_chocolate_factory", System.currentTimeMillis() - 3L * 3_600_000L);
		JsonObject employees = new JsonObject();
		employees.addProperty("rabbit_bro", 180);
		employees.addProperty("rabbit_cousin", 180);
		employees.addProperty("rabbit_sis", 175);
		employees.addProperty("rabbit_father", 160);
		employees.addProperty("rabbit_grandma", 140);
		employees.addProperty("rabbit_uncle", 100);
		employees.addProperty("rabbit_dog", 0);
		easter.add("employees", employees);
		JsonObject tower = new JsonObject();
		tower.addProperty("level", 12);
		tower.addProperty("charges", 2);
		tower.addProperty("last_charge_time", System.currentTimeMillis() - 5L * 3_600_000L);
		tower.addProperty("activation_time", System.currentTimeMillis() - 26L * 3_600_000L);
		easter.add("time_tower", tower);
		easter.add("shop", TestData.obj("chocolate_spent", 3_400_000_000L));
		JsonObject rabbits = new JsonObject();
		JsonObject hop = J.obj(NeuRepo.constant("hoppity"), "hoppity.rarities");
		int[] take = {150, 90, 60, 30, 14, 5, 2}; // common .. divine
		String[] rar = {"common", "uncommon", "rare", "epic", "legendary", "mythic", "divine"};
		for (int r = 0; r < rar.length; r++) {
			JsonArray list = J.arrOrEmpty(hop, rar[r] + ".rabbits");
			for (int i = 0; i < Math.min(take[r], list.size()); i++) rabbits.addProperty(list.get(i).getAsString(), 1 + (i % 4));
		}
		rabbits.add("collected_locations", new JsonObject()); // object entries must be skipped
		easter.add("rabbits", rabbits);
		m.add("events", TestData.obj("easter", easter));

		// chocolate talisman in the accessory bag
		J.obj(m, "inventory.bag_contents").add("talisman_bag", TestData.obj("data", TestData.encode(List.of(
			TestData.item(397, 3, "§9Rich Chocolate Chunk", List.of("§9§lRARE ACCESSORY"), "RICH_CHOCOLATE_CHUNK", null, TestData.HEAD_TEXTURE),
			TestData.item(397, 3, "§aWolf Ring", List.of("§a§lUNCOMMON ACCESSORY"), "WOLF_RING", null, TestData.HEAD_TEXTURE)))));
	}
}
