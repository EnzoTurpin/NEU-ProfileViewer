package dev.neupv.test;

import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.Map;

/** Dungeoneering tab: catacombs (normal) and master mode pages, plus the "Until Cata" tooltip. */
public class DungeonGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(DungeonGameTest::dungeons, p -> {}), TestData.hypixelPlayer());
				holder[0] = profiles;
				Map<String, Level> info = profiles.getProfile("Apple").getLevelingInfo();
				Level cata = info.get("cosmetic_catacombs");
				TestData.check(cata.level > 41 && cata.level < 42, "catacombs level 41.x (" + cata.level + ")");
				TestData.check(info.get("cosmetic_mage").level >= 50, "mage class over 50 (" + info.get("cosmetic_mage").level + ")");
				TestData.check(info.get("cosmetic_tank").level < 40, "tank class below 40 (" + info.get("cosmetic_tank").level + ")");
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.DUNGEON;
				ProfileViewerScreen.setOnSecondPage(false);
				mc.setScreenAndShow(new ProfileViewerScreen(profiles));
			});
			context.waitTicks(60);
			context.takeScreenshot("dungeon-catacombs");

			// Hover the "Until Cata" line for the runs calculator tooltip.
			int[] origin = new int[2];
			context.runOnClient(mc -> {
				origin[0] = (mc.getWindow().getGuiScaledWidth() - ProfileViewerScreen.SIZE_X) / 2;
				origin[1] = (mc.getWindow().getGuiScaledHeight() - ProfileViewerScreen.SIZE_Y) / 2;
			});
			context.getInput().setCursorPos(origin[0] + 60, origin[1] + 45);
			context.waitTicks(10);
			context.takeScreenshot("dungeon-catacombs-tooltip");

			context.runOnClient(mc -> {
				ProfileViewerScreen.setOnSecondPage(true);
				mc.setScreenAndShow(new ProfileViewerScreen(holder[0]));
			});
			context.waitTicks(40);
			context.takeScreenshot("dungeon-master");
			context.runOnClient(mc -> ProfileViewerScreen.setOnSecondPage(false));
		}
	}

	private static void dungeons(JsonObject member) {
		JsonObject dungeons = new JsonObject();
		JsonObject types = new JsonObject();

		JsonObject cata = new JsonObject();
		cata.addProperty("experience", 85_000_000);
		cata.add("tier_completions", floors(new double[]{1, 12, 20, 35, 60, 150, 240, 420}));
		cata.add("mobs_killed", floors(new double[]{40, 900, 1500, 2600, 4100, 9000, 15000, 36000}));
		cata.add("fastest_time", floors(new double[]{0, 98_000, 125_000, 160_000, 240_000, 155_000, 200_000, 290_000}));
		cata.add("fastest_time_s", floors(new double[]{0, 110_000, 140_000, 190_000, 280_000, 170_000, 230_000, 320_000}));
		cata.add("fastest_time_s_plus", floors(new double[]{0, 0, 0, 230_000, 0, 182_000, 245_000, 335_000}));
		types.add("catacombs", cata);

		JsonObject master = new JsonObject();
		master.add("tier_completions", floors(new double[]{0, 25, 18, 30, 44, 61, 80, 33}));
		master.add("mobs_killed", floors(new double[]{0, 1200, 900, 1500, 2100, 3000, 4200, 2800}));
		master.add("fastest_time", floors(new double[]{0, 140_000, 150_000, 175_000, 190_000, 165_000, 210_000, 360_000}));
		master.add("fastest_time_s", floors(new double[]{0, 150_000, 165_000, 190_000, 210_000, 175_000, 230_000, 390_000}));
		master.add("fastest_time_s_plus", floors(new double[]{0, 165_000, 180_000, 205_000, 0, 186_000, 246_000, 412_000}));
		types.add("master_catacombs", master);
		dungeons.add("dungeon_types", types);

		JsonObject classes = new JsonObject();
		classes.add("healer", TestData.obj("experience", 12_500_000));
		classes.add("mage", TestData.obj("experience", 620_000_000));
		classes.add("berserk", TestData.obj("experience", 45_000_000));
		classes.add("archer", TestData.obj("experience", 31_000_000));
		classes.add("tank", TestData.obj("experience", 4_300_000));
		dungeons.add("player_classes", classes);
		dungeons.addProperty("selected_dungeon_class", "mage");
		dungeons.addProperty("secrets", 18_432);
		member.add("dungeons", dungeons);
	}

	private static JsonObject floors(double[] values) {
		JsonObject o = new JsonObject();
		for (int i = 0; i < values.length; i++) if (values[i] > 0) o.addProperty(String.valueOf(i), values[i]);
		return o;
	}
}
