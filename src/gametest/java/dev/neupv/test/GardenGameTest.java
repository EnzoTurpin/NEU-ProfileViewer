package dev.neupv.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.GardenData;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.net.Mojang;
import dev.neupv.net.UrsaClient;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.Map;

import static dev.neupv.test.TestData.check;

/** Garden tab: pure parsing checks on synthetic garden / Elitebot answers, then screenshots. */
public class GardenGameTest implements FabricClientGameTest {
	private static final String PROFILE_ID = "4ae7a3c1-5b2c-4d8e-9f10-112233445566";
	private static final String PROFILE_ID_UNDASHED = PROFILE_ID.replace("-", "");

	static JsonObject gardenResponse() {
		return JsonParser.parseString("""
			{"success":true,"garden":{"uuid":"%s",
			 "unlocked_plots_ids":["beginner_1","beginner_2","beginner_3","beginner_4","intermediate_1","intermediate_2","advanced_6"],
			 "commission_data":{"visits":{"adventurer":30,"archaeologist":12,"baker":5,"unknown_guy":99},
			                    "completed":{"adventurer":28,"archaeologist":10,"baker":5},"total_completed":43,"unique_npcs_served":3},
			 "resources_collected":{"WHEAT":2000000000,"CARROT_ITEM":1500000,"INK_SACK:3":12345,"MUSHROOM_COLLECTION":50},
			 "garden_experience":25000,
			 "composter_data":{"upgrades":{"speed":25,"multi_drop":10,"fuel_cap":3,"organic_matter_cap":0,"cost_reduction":5}},
			 "selected_barn_skin":"default_1",
			 "crop_upgrade_levels":{"WHEAT":9,"CARROT_ITEM":4,"NETHER_STALK":1}}}""".formatted(PROFILE_ID_UNDASHED)).getAsJsonObject();
	}

	static JsonObject eliteResponse() {
		return JsonParser.parseString("""
			{"profileId":"x","totalWeight":1234.56,"cropWeight":{"Wheat":800.5,"Carrot":12.25,"Cocoa Beans":3.5,"Sunflower":1.0},
			 "bonusWeight":{"Farming Level":250,"Contests":25}}""").getAsJsonObject();
	}

	private static final ProfileViewerScreen[] SCREEN = new ProfileViewerScreen[1];

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		context.runOnClient(mc -> testParsing());
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(
					m -> m.add("garden_player_data", TestData.obj("copper", 123456)),
					p -> p.addProperty("profile_id", PROFILE_ID)), TestData.hypixelPlayer());
				MuseumGameTest.injectFetch(profiles, UrsaClient.garden(PROFILE_ID_UNDASHED), gardenResponse());
				MuseumGameTest.injectFetch(profiles, "https://api.elitebot.dev/weight/" + Mojang.undashed(TestData.NOTCH) + "/" + PROFILE_ID_UNDASHED, eliteResponse());
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.GARDEN;
				SCREEN[0] = new ProfileViewerScreen(profiles);
				mc.setScreenAndShow(SCREEN[0]);
			});
			context.waitTicks(40);
			// hover Wheat's milestone (maxed, overflow tooltip)
			double[] pos = new double[2];
			context.runOnClient(mc -> {
				ProfileViewerScreen s = SCREEN[0];
				pos[0] = s.guiLeft() + 26 + 30;
				pos[1] = s.guiTop() + 10 + 14 + 8;
			});
			context.getInput().setCursorPos(pos[0], pos[1]);
			context.waitTicks(10);
			context.takeScreenshot("garden-garden");
			context.runOnClient(mc -> {
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(), TestData.hypixelPlayer());
				mc.setScreenAndShow(new ProfileViewerScreen(profiles));
			});
			context.waitTicks(40);
			context.takeScreenshot("garden-error");
		}
	}

	private static void testParsing() {
		GardenData.Repo repo = GardenData.Repo.parse(NeuRepo.constant("garden"));
		check(repo != null && repo.plots.size() == 24 && repo.gardenExperience.size() > 10, "garden.json parsed (24 plots)");
		check(repo.cropMilestones.get(GardenData.Crop.WHEAT).size() == 46, "46 wheat milestone tiers");
		check(repo.barn.get("default_1").item().equals("WOOD-5"), "barn skin item");
		check(repo.composterUpgrades.get("speed").get(1).copper() == 100, "composter upgrade cost parsed");
		check(GardenData.Repo.parse(null) == null, "no repo -> null");

		GardenData d = GardenData.parse(gardenResponse());
		check(d != null && d.gardenExperience == 25000, "garden_experience");
		check(d.unlockedPlotIds.size() == 7, "unlocked plots");
		check(d.resourcesCollected.get(GardenData.Crop.CARROT) == 1_500_000L && d.resourcesCollected.get(GardenData.Crop.COCOA_BEANS) == 12345L,
			"API crop names (CARROT_ITEM, INK_SACK:3) mapped");
		check(d.cropUpgradeLevels.get(GardenData.Crop.NETHER_WART) == 1, "NETHER_STALK -> Nether Wart");
		check(d.composterUpgrades.get("speed") == 25 && d.composterUpgrades.get("organic_matter_cap") == 0, "composter levels");
		check("default_1".equals(d.selectedBarnSkin), "barn skin");
		check(GardenData.parse(JsonParser.parseString("{\"success\":true}").getAsJsonObject()) == null, "no garden object -> null");
		GardenData empty = GardenData.parse(JsonParser.parseString("{\"success\":true,\"garden\":{}}").getAsJsonObject());
		check(empty != null && empty.gardenExperience == 0 && empty.composterUpgrades.isEmpty(), "empty garden parses without NPE");

		Map<GardenData.VisitorRarity, int[]> v = d.visitorStats(repo);
		check(v.get(GardenData.VisitorRarity.UNCOMMON)[0] == 30 && v.get(GardenData.VisitorRarity.RARE)[1] == 10
			&& v.get(GardenData.VisitorRarity.LEGENDARY)[0] == 5, "visitors grouped by rarity");
		check(v.get(GardenData.VisitorRarity.TOTAL)[0] == 47 && v.get(GardenData.VisitorRarity.TOTAL)[1] == 43, "visitor totals skip unknown visitors");

		List<Long> tiers = List.of(10L, 20L, 30L);
		GardenData.Milestone m = GardenData.milestone(tiers, 25);
		check(m.level() == 1 && !m.maxed() && m.progress() == 15 && m.needed() == 20 && m.maxTotal() == 60, "milestone mid-way");
		GardenData.Milestone mx = GardenData.milestone(tiers, 130);
		check(mx.maxed() && mx.level() == 3 && mx.overflow() == 100 && mx.overflowLevel() == 5, "milestone overflow (NEU amount + overflow tiers)");
		GardenData.Milestone wheat = GardenData.milestone(repo.cropMilestones.get(GardenData.Crop.WHEAT), 2_000_000_000L);
		check(wheat.maxed() && wheat.overflowLevel() > 46, "2B wheat: maxed with overflow tier " + wheat.overflowLevel());

		GardenData.EliteWeight e = GardenData.parseElite(eliteResponse());
		check(Math.abs(e.totalWeight() - 1234.56) < 1e-9 && e.bonusWeight() == 275, "elite total and bonus weight");
		check(e.cropWeight().get(GardenData.Crop.COCOA_BEANS) == 3.5 && e.cropWeight().get(GardenData.Crop.SUNFLOWER) == 1.0, "elite crop names mapped");
		check(GardenData.parseElite(new JsonObject()) == null, "elite without data -> null");
	}
}
