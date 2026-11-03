package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import static dev.neupv.test.TestData.obj;

/** SkyBlock Level sub-page of the Skills tab. */
public class LevelGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(LevelGameTest::member, LevelGameTest::outer), TestData.hypixelPlayer());
				holder[0] = profiles;
				SkyblockProfiles.SkyblockProfile p = profiles.getProfile("Apple");
				TestData.check(NeuRepo.sblevels() != null, "sblevels.json available");
				TestData.check(NeuRepo.constant("essenceshops") != null, "essenceshops.json available");
				TestData.check(p.getSkyblockLevel() == 245.5, "skyblock level 245.5");
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.BASIC;
				ProfileViewerScreen.setOnSecondPage(true);
				screen[0] = new ProfileViewerScreen(holder[0]);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitTicks(100);
			context.takeScreenshot("level-second-page");
			// hover the Slaying Task bar to show its tooltip (GUI scale 1: GUI coords = window pixels)
			int[] pos = context.computeOnClient(mc -> {
				ProfileViewerScreen s = screen[0];
				return new int[]{s.guiLeft() + 60, s.guiTop() + 88};
			});
			context.getInput().setCursorPos(pos[0], pos[1]);
			context.waitTicks(10);
			context.takeScreenshot("level-tooltip");
			context.runOnClient(mc -> ProfileViewerScreen.setOnSecondPage(false));
		}
	}

	private static void outer(JsonObject profile) {
		JsonArray states = new JsonArray();
		String[][] ups = {{"minion_slots", "1"}, {"minion_slots", "2"}, {"coins_allowance", "1"}, {"island_size", "1"}};
		for (String[] u : ups) {
			JsonObject o = new JsonObject();
			o.addProperty("upgrade", u[0]);
			o.addProperty("tier", Integer.parseInt(u[1]));
			states.add(o);
		}
		profile.add("community_upgrades", obj("upgrade_states", states));
	}

	private static void member(JsonObject m) {
		JsonObject leveling = m.getAsJsonObject("leveling");
		JsonArray tasks = new JsonArray();
		for (String t : new String[]{"FAST_TRAVEL_HUB_CASTLE", "FAST_TRAVEL_DEEP_CAVERNS", "BANK_UPGRADE_GOLD", "BANK_UPGRADE_DELUXE",
			"SPOOKY_FESTIVAL_WOOD", "SPOOKY_FESTIVAL_STONE", "ABIPHONE_KAT", "ABIPHONE_ELIZABETH", "SONG_HYMN_JOY_COMPLETION",
			"SONG_HYMN_JOY_PERFECT_COMPLETION", "SONG_BRAHMS_COMPLETION"}) tasks.add(t);
		leveling.add("completed_tasks", tasks);
		leveling.addProperty("highest_pet_score", 187);
		leveling.addProperty("mining_fiesta_ores_mined", 312_000);
		leveling.addProperty("fishing_festival_sharks_killed", 900);
		leveling.add("completions", obj("NUCLEUS_RUNS", 23));

		m.add("fairy_soul", obj("total_collected", 247));
		m.getAsJsonObject("fairy_soul").addProperty("unspent_souls", 2);
		JsonObject abs = obj("highest_magical_power", 1234);
		abs.addProperty("bag_upgrades_purchased", 12);
		JsonArray powers = new JsonArray();
		powers.add("silky");
		powers.add("bloody");
		abs.add("unlocked_powers", powers);
		m.add("accessory_bag_storage", abs);

		JsonObject pd = m.getAsJsonObject("player_data");
		pd.addProperty("reaper_peppers_eaten", 5);
		JsonObject perks = new JsonObject();
		perks.addProperty("permanent_health", 5);
		perks.addProperty("permanent_defense", 5);
		perks.addProperty("catacombs_boss_luck", 4);
		perks.addProperty("forbidden_blessing", 10);
		perks.addProperty("empowered_agility", 10);
		perks.addProperty("heart_of_gold", 3);
		pd.add("perks", perks);
		JsonArray gens = new JsonArray();
		for (String gen : new String[]{"COBBLESTONE_1", "COBBLESTONE_2", "COBBLESTONE_11", "WHEAT_9", "SNOW_11", "CLAY_5"}) gens.add(gen);
		pd.add("crafted_generators", gens);

		JsonObject coll = new JsonObject();
		coll.addProperty("COBBLESTONE", 120_000);
		coll.addProperty("WHEAT", 2_500_000);
		coll.addProperty("LOG", 15_000);
		m.add("collection", coll);

		// dungeons: completions on floors 0..7 + master 1..5
		JsonObject cata = m.getAsJsonObject("dungeons").getAsJsonObject("dungeon_types").getAsJsonObject("catacombs");
		JsonObject tc = cata.getAsJsonObject("tier_completions");
		int[] comps = {3, 40, 30, 60, 120, 300, 400, 120};
		for (int i = 0; i < comps.length; i++) tc.addProperty(String.valueOf(i), comps[i]);
		JsonObject mtc = new JsonObject();
		mtc.addProperty("1", 12);
		mtc.addProperty("2", 30);
		mtc.addProperty("5", 260);
		m.getAsJsonObject("dungeons").getAsJsonObject("dungeon_types").add("master_catacombs", obj("tier_completions", mtc));

		// slayers: kills per tier
		JsonObject bosses = m.getAsJsonObject("slayer").getAsJsonObject("slayer_bosses");
		for (String s : new String[]{"zombie", "spider", "wolf"}) {
			for (int i = 0; i < 5; i++) bosses.getAsJsonObject(s).addProperty("boss_kills_tier_" + i, 10 + i);
		}
		bosses.getAsJsonObject("enderman").addProperty("boss_kills_tier_0", 3);

		// bestiary
		JsonObject kills = new JsonObject();
		kills.addProperty("unburried_zombie_30", 3000);
		kills.addProperty("zealot_enderman_55", 15000);
		kills.addProperty("superior_dragon_100", 4);
		kills.addProperty("wise_dragon_100", 2);
		kills.addProperty("arachne_300", 3);
		kills.addProperty("last_killed_mob", "zealot_enderman_55");
		JsonObject bestiary = obj("kills", kills);
		bestiary.add("deaths", new JsonObject());
		bestiary.addProperty("migration", true);
		m.add("bestiary", bestiary);

		JsonObject nether = new JsonObject();
		JsonObject kuudra = new JsonObject();
		kuudra.addProperty("none", 40);
		kuudra.addProperty("hot", 25);
		kuudra.addProperty("burning", 5);
		nether.add("kuudra_completed_tiers", kuudra);
		JsonObject dojo = new JsonObject();
		dojo.addProperty("dojo_points_mob_kb", 1000);
		dojo.addProperty("dojo_points_wall_jump", 800);
		dojo.addProperty("dojo_points_archer", 900);
		nether.add("dojo", dojo);
		JsonObject abiphone = obj("operator_chip", obj("repaired_index", 3));
		abiphone.add("active_contacts", new JsonArray());
		nether.add("abiphone", abiphone);
		m.add("nether_island_player_data", nether);

		JsonObject stats = new JsonObject();
		stats.add("mythos", obj("kills", 2_345));
		JsonObject milestone = obj("ores_mined", 30_000);
		milestone.addProperty("sea_creatures_killed", 1_500);
		stats.add("pets", obj("milestone", milestone));
		m.add("player_stats", stats);

		JsonObject mining = new JsonObject();
		mining.addProperty("experience", 2_000_000);
		mining.addProperty("powder_mithril", 150_000);
		mining.addProperty("powder_spent_mithril", 2_000_000);
		mining.addProperty("powder_gemstone", 300_000);
		mining.addProperty("powder_spent_gemstone", 4_000_000);
		mining.add("nodes", obj("special_0", 3));
		m.add("mining_core", mining);
		JsonArray tutorial = new JsonArray();
		for (int i = 1; i <= 4; i++) tutorial.add("commission_milestone_reward_skyblock_xp_tier_" + i);
		JsonObject objectives = obj("tutorial", tutorial);
		for (String o : new String[]{"explore_hub", "talk_to_lumberjack", "kill_danger_mobs", "rift_accessory_1"}) objectives.add(o, obj("status", "COMPLETE"));
		m.add("objectives", objectives);
		JsonArray fossils = new JsonArray();
		fossils.add("CLAW");
		fossils.add("SPINE");
		m.add("glacite_player_data", obj("fossils_donated", fossils));

		JsonObject trophy = new JsonObject();
		trophy.addProperty("blobfish_bronze", 30);
		trophy.addProperty("blobfish_silver", 4);
		trophy.addProperty("sulphur_skitter_bronze", 10);
		trophy.addProperty("total_caught", 44);
		m.add("trophy_fish", trophy);

		JsonObject jacob = m.getAsJsonObject("jacobs_contest");
		JsonObject brackets = new JsonObject();
		JsonArray gold = new JsonArray();
		gold.add("WHEAT");
		gold.add("CARROT_ITEM");
		JsonArray plat = new JsonArray();
		plat.add("WHEAT");
		brackets.add("gold", gold);
		brackets.add("platinum", plat);
		jacob.add("unique_brackets", brackets);

		m.add("profile", obj("personal_bank_upgrade", 2));
		JsonArray trophies = new JsonArray();
		trophies.add(obj("type", "wyldly_supreme"));
		JsonObject rift = obj("gallery", obj("secured_trophies", trophies));
		rift.add("castle", obj("grubber_stacks", 3));
		m.add("rift", rift);
		m.add("experimentation", obj("serums_drank", 2));
		m.add("winter_player_data", obj("refined_jyrre_uses", 4));
		m.add("garden_player_data", obj("larva_consumed", 1));
	}
}
