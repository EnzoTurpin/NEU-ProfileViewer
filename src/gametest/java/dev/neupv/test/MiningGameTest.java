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

import java.util.function.Consumer;

/** Heart of the Mountain tab: synthetic mining_core / forge data, screenshots with and without tooltips. */
public class MiningGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		long now = System.currentTimeMillis();
		Consumer<JsonObject> member = m -> {
			JsonObject core = new JsonObject();
			JsonObject nodes = new JsonObject();
			Object[][] levels = {
				{"mining_speed", 50}, {"mining_fortune", 50}, {"titanium_insanium", 50}, {"mining_speed_boost", 1},
				{"precision_mining", 1}, {"pickobulus", 1}, {"luck_of_the_cave", 45}, {"efficient_miner", 100},
				{"quick_forge", 14}, {"sky_mall", 1}, {"old_school", 20}, {"professional", 140}, {"mole", 120},
				{"gem_lover", 20}, {"seasoned_mineman", 100}, {"front_loaded", 1}, {"daily_grind", 1},
				{"core_of_the_mountain", 7}, {"daily_powder", 1}, {"blockhead", 20}, {"keep_it_cool", 12},
				{"lonesome_miner", 45}, {"great_explorer", 20}, {"powder_buff", 30}, {"speedy_mineman", 50},
				{"fortunate_mineman", 22}, {"strong_arm", 3}, {"special_0", 2}};
			for (Object[] l : levels) nodes.addProperty((String) l[0], (int) l[1]);
			nodes.addProperty("toggle_sky_mall", false);
			core.add("nodes", nodes);
			core.addProperty("experience", 520_000);
			core.addProperty("powder_mithril", 1_234_567);
			core.addProperty("powder_spent_mithril", 12_850_000);
			core.addProperty("powder_gemstone", 3_456_789);
			core.addProperty("powder_spent_gemstone", 20_410_000);
			core.addProperty("powder_glacite", 245_000);
			core.addProperty("powder_spent_glacite", 1_900_000);
			core.addProperty("selected_pickaxe_ability", "pickobulus");
			JsonObject crystals = new JsonObject();
			String[] names = {"jade", "amethyst", "amber", "sapphire", "topaz", "jasper", "ruby", "opal", "aquamarine", "peridot", "onyx", "citrine"};
			String[] states = {"FOUND", "PLACED", "FOUND", "FOUND", "PLACED", "FOUND", "NOT_FOUND", "FOUND", "NOT_FOUND", "FOUND", "NOT_FOUND", "FOUND"};
			int[] placed = {41, 43, 40, 42, 44, 0, 0, 0, 0, 0, 0, 0};
			for (int i = 0; i < names.length; i++) {
				JsonObject c = new JsonObject();
				c.addProperty("state", states[i]);
				c.addProperty("total_found", placed[i] + 3);
				if (i < 5) c.addProperty("total_placed", placed[i]);
				crystals.add(names[i] + "_crystal", c);
			}
			core.add("crystals", crystals);
			m.add("mining_core", core);

			JsonObject forge1 = new JsonObject();
			forge1.add("1", forgeItem("FORGING", "REFINED_DIAMOND", now - 3_600_000L, 1));
			forge1.add("2", forgeItem("FORGING", "DIVAN_DRILL", now - 3_600_000L, 2));
			forge1.add("3", forgeItem("PETS", "AMMONITE", now - 86_400_000L, 3));
			forge1.add("5", forgeItem("FORGING", "NOT_A_REAL_ITEM", now, 5));
			JsonObject processes = new JsonObject();
			processes.add("forge_1", forge1);
			JsonObject forge = new JsonObject();
			forge.add("forge_processes", processes);
			m.add("forge", forge);
		};

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
				profiles.loadFromJson(TestData.profilesResponse(member, p -> {}), TestData.hypixelPlayer());
				holder[0] = profiles;
				SkyblockProfiles.SkyblockProfile sel = profiles.getProfile(profiles.getLatestProfileName());
				Level hotm = sel.getLevelingInfo().get("hotm");
				TestData.check(hotm != null && hotm.level > 7 && hotm.level < 8, "HOTM level 7.x from 520k xp (" + (hotm == null ? null : hotm.level) + ")");
				TestData.check(NeuRepo.constant("hotmlayout") != null, "hotmlayout.json available");
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.MINING;
				screen[0] = new ProfileViewerScreen(profiles);
				mc.setScreenAndShow(screen[0]);
			});
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(60);
			context.takeScreenshot("mining-mining");

			int[] gui = new int[2];
			context.runOnClient(mc -> {
				gui[0] = screen[0].guiLeft();
				gui[1] = screen[0].guiTop();
			});
			// Core of the Mountain (3,5): tree origin = (guiLeft + 260, guiTop - 19) with scroll 0
			context.getInput().setCursorPos(gui[0] + 330, gui[1] + 91);
			context.waitTicks(5);
			context.takeScreenshot("mining-perk-tooltip");

			context.getInput().setCursorPos(gui[0] + 157, gui[1] + 28);
			context.waitTicks(5);
			context.takeScreenshot("mining-forge-tooltip");

			context.getInput().setCursorPos(gui[0] + 330, gui[1] + 100);
			context.getInput().scroll(5);
			context.waitTicks(5);
			context.getInput().setCursorPos(gui[0] + 290, gui[1] + 30);
			context.waitTicks(5);
			context.takeScreenshot("mining-scrolled");
		}
	}

	private static JsonObject forgeItem(String type, String id, long startTime, int slot) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		o.addProperty("id", id);
		o.addProperty("startTime", startTime);
		o.addProperty("slot", slot);
		o.addProperty("notified", false);
		return o;
	}
}
