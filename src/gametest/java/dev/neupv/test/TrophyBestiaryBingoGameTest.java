package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.BestiaryData;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;

/**
 * Trophy Fish, Bestiary and Bingo tabs on synthetic data, with hover/click interactions.
 * Bingo cannot be faked without the network: only checks that the loading/error state renders without crashing.
 */
public class TrophyBestiaryBingoGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			int[] gui = new int[3];
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				PvConfig.get().alwaysShowBingoTab = true;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(TrophyBestiaryBingoGameTest::member, p -> {}), TestData.hypixelPlayer());
				gui[2] = checks(profiles);
				holder[0] = profiles;
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.TROPHY_FISH;
				ProfileViewerScreen s = new ProfileViewerScreen(profiles);
				mc.setScreenAndShow(s);
				gui[0] = s.guiLeft();
				gui[1] = s.guiTop();
			});
			context.getInput().setCursorPos(gui[0] + 200, gui[1] + 190);
			context.waitTicks(60);
			context.takeScreenshot("neupv-tfb-trophy_fish");
			// hover the most caught fish (first slot), then an undiscovered one (last slot)
			context.getInput().setCursorPos(gui[0] + 277 + 8, gui[1] + 46 + 8);
			context.waitTicks(5);
			context.takeScreenshot("neupv-tfb-trophy_fish-hover");
			context.getInput().setCursorPos(gui[0] + 277 + 8, gui[1] + 142 + 8);
			context.waitTicks(5);
			context.takeScreenshot("neupv-tfb-trophy_fish-hover-missing");

			context.runOnClient(mc -> ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.BESTIARY);
			context.getInput().setCursorPos(gui[0] + 200, gui[1] + 190);
			context.waitTicks(20);
			context.takeScreenshot("neupv-tfb-bestiary");
			// click the 2nd category tab (Hub) and hover its first mob
			int n = gui[2];
			int neuStep = (int) (350f / (n - 1 + 0.0000001f));
			int tab1X = neuStep >= 21 ? 30 + neuStep : 8 + (int) (415f / n);
			context.getInput().setCursorPos(gui[0] + tab1X + 8, gui[1] + 20);
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			context.getInput().setCursorPos(gui[0] + 23 + 6 + 10, gui[1] + 30 + 17 + 10);
			context.waitTicks(5);
			context.takeScreenshot("neupv-tfb-bestiary-hub-hover");
			// Fishing (index 16 in NEU's order): has subcategories; click the 2nd subcategory (Lava)
			int fishX = neuStep >= 21 ? 30 + neuStep * 16 : 8 + (int) (415f / n * 16);
			context.getInput().setCursorPos(gui[0] + fishX + 8, gui[1] + 20);
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			context.getInput().setCursorPos(gui[0] + 280 + 24 + 10, gui[1] + 175 + 8);
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			context.takeScreenshot("neupv-tfb-bestiary-fishing-lava");

			context.runOnClient(mc -> ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.BINGO);
			context.getInput().setCursorPos(gui[0] + 200, gui[1] + 190);
			context.waitTicks(100);
			context.takeScreenshot("neupv-tfb-bingo");

			// Inject fake bingo + goals responses into the per-player fetch cache (no network fake available)
			context.runOnClient(mc -> {
				injectBingo(holder[0]);
			});
			context.getInput().setCursorPos(gui[0] + 231 + 24 * 2 + 8, gui[1] + 46 + 8);
			context.waitTicks(10);
			context.takeScreenshot("neupv-tfb-bingo-card-hover-community");
			context.getInput().setCursorPos(gui[0] + 231 + 24 + 8, gui[1] + 46 + 24 + 8);
			context.waitTicks(5);
			context.takeScreenshot("neupv-tfb-bingo-card-hover-personal");
		}
	}

	@SuppressWarnings("unchecked")
	private static void injectBingo(SkyblockProfiles profile) {
		JsonObject goalsJson = new JsonObject();
		goalsJson.addProperty("success", true);
		goalsJson.addProperty("id", 42);
		JsonArray goals = new JsonArray();
		for (int i = 0; i < 25; i++) {
			JsonObject goal = new JsonObject();
			goal.addProperty("id", "goal_" + i);
			if (i == 2 || i == 10 || i == 14 || i == 22 || i == 24) {
				goal.addProperty("name", "Community Goal " + i);
				JsonArray tiers = new JsonArray();
				tiers.add(1_000_000);
				tiers.add(5_000_000);
				tiers.add(10_000_000);
				goal.add("tiers", tiers);
				goal.addProperty("progress", i == 2 ? 6_500_000 : 12_000_000);
			} else {
				goal.addProperty("name", "Personal Goal " + i);
				goal.addProperty("lore", "§7Obtain §a" + (i * 10) + " §7Enchanted Cobblestone.");
			}
			goals.add(goal);
		}
		goalsJson.add("goals", goals);
		JsonObject event = new JsonObject();
		event.addProperty("key", 42);
		event.addProperty("points", 137);
		JsonArray completed = new JsonArray();
		for (int i : new int[]{0, 1, 3, 5, 7, 8, 9, 13}) completed.add("goal_" + i);
		event.add("completed_goals", completed);
		JsonArray events = new JsonArray();
		events.add(event);
		JsonObject bingo = new JsonObject();
		bingo.addProperty("success", true);
		bingo.add("events", events);
		try {
			java.lang.reflect.Field f = SkyblockProfiles.class.getDeclaredField("fetches");
			f.setAccessible(true);
			java.util.Map<String, SkyblockProfiles.Fetch> fetches = (java.util.Map<String, SkyblockProfiles.Fetch>) f.get(profile);
			fetches.put(dev.neupv.gui.BingoPage.BINGO_GOALS_URL, new SkyblockProfiles.Fetch(SkyblockProfiles.FetchState.OK, goalsJson, null));
			fetches.put(dev.neupv.net.UrsaClient.bingo(profile.getUuid()), new SkyblockProfiles.Fetch(SkyblockProfiles.FetchState.OK, bingo, null));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void member(JsonObject member) {
		JsonObject tf = new JsonObject();
		tf.addProperty("total_caught", 1234);
		tf.addProperty("last_caught", "golden_fish/silver");
		Object[][] fish = {
			{"gusher", 120, 80, 30, 9, 1}, {"flyfish", 300, 200, 70, 25, 5}, {"golden_fish", 40, 30, 8, 2, 0},
			{"blobfish", 60, 50, 10, 0, 0}, {"mana_ray", 20, 15, 5, 0, 0}, {"slugfish", 12, 12, 0, 0, 0},
			{"soul_fish", 90, 60, 20, 10, 0}, {"obfuscated_fish_1", 200, 150, 40, 9, 1}, {"steaming_hot_flounder", 7, 7, 0, 0, 0},
			{"lava_horse", 33, 20, 10, 3, 0}, {"karate_fish", 15, 15, 0, 0, 0}, {"vanille", 5, 5, 0, 0, 0},
			{"moldfin", 25, 20, 5, 0, 0}, {"skeleton_fish", 18, 18, 0, 0, 0}, {"sulphur_skitter", 50, 30, 15, 5, 0},
		};
		String[] tiers = {"bronze", "silver", "gold", "diamond"};
		for (Object[] f : fish) {
			String name = (String) f[0];
			tf.addProperty(name, (Integer) f[1]);
			for (int i = 0; i < 4; i++) if ((Integer) f[2 + i] > 0) tf.addProperty(name + "_" + tiers[i], (Integer) f[2 + i]);
		}
		JsonArray rewards = new JsonArray();
		rewards.add(1);
		tf.add("rewards", rewards);
		member.add("trophy_fish", tf);

		JsonObject kills = new JsonObject();
		kills.addProperty("thunder_400", 42);
		kills.addProperty("lord_jawbus_600", 7);
		kills.addProperty("zombie_1", 5000);
		kills.addProperty("skeleton_1", 230);
		kills.addProperty("spider_1", 12);
		kills.addProperty("enderman_private_42", 400);
		kills.addProperty("graveyard_zombie_1", 250);
		kills.addProperty("unburried_zombie_30", 900);
		kills.addProperty("old_wolf_50", 30);
		kills.addProperty("sea_walker_4", 150);
		kills.addProperty("lava_blaze_100", 60);
		kills.addProperty("lava_pigman_100", 1000);
		kills.addProperty("last_killed_mob", "zombie_1");
		JsonObject deaths = new JsonObject();
		deaths.addProperty("zombie_1", 3);
		deaths.addProperty("lord_jawbus_600", 12);
		JsonObject bestiary = new JsonObject();
		bestiary.addProperty("migration", true);
		bestiary.add("kills", kills);
		bestiary.add("deaths", deaths);
		member.add("bestiary", bestiary);
	}

	/** @return number of bestiary categories */
	private static int checks(SkyblockProfiles profiles) {
		JsonObject pj = profiles.getProfile("Apple").getProfileJson();
		List<BestiaryData.Category> cats = BestiaryData.parseBestiaryData(pj);
		TestData.check(cats.size() >= 17, "bestiary categories parsed (" + cats.size() + ")");
		TestData.check("dynamic".equals(cats.getFirst().id()), "first category = Private Island");
		TestData.check("fishing".equals(cats.get(16).id()), "17th category = Fishing (NEU order kept)");
		int tiers = BestiaryData.calculateTotalBestiaryTiers(cats);
		TestData.check(tiers > 0, "bestiary tiers > 0 (" + tiers + ")");
		BestiaryData.Category fishing = cats.get(16);
		TestData.check(fishing.subCategories().size() >= 2, "fishing has subcategories");
		BestiaryData.Category hub = cats.get(1);
		TestData.check(hub.familyData().found() >= 2, "hub families found (" + hub.familyData() + ")");
		BestiaryData.Mob ghoul = hub.mobs().stream().filter(m -> m.name().contains("Crypt Ghoul")).findFirst().orElseThrow();
		TestData.check(ghoul.kills() == 900 && ghoul.mobLevelData().level() > 0, "crypt ghoul 900 kills, level " + ghoul.mobLevelData().level());
		return cats.size();
	}
}
