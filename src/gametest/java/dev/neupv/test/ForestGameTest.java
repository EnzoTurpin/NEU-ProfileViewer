package dev.neupv.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.MiningPage;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.net.Mojang;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkillTree;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Heart of the Forest sub-page of the HOTM tab, on the user's real profile (src/gametest/resources/realdata, git-ignored):
 * HOTM screenshot, real click on side button 2, HOTF screenshot, perk tooltip, selected ability on tree 2.
 */
public class ForestGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		JsonObject profiles = read("/realdata/profiles.json");
		JsonObject player = read("/realdata/player.json");
		if (profiles == null || player == null) {
			System.out.println("[neupv-test] SKIPPED forest test (no realdata/*.json)");
			return;
		}
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			JsonObject[] member = new JsonObject[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.getWindow().setWindowed(1920, 991);
				mc.options.guiScale().set(4);
				mc.resizeGui();
				SkyblockProfiles p = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
				p.loadFromJson(profiles, player.getAsJsonObject("player"));
				member[0] = p.getProfile(p.getLatestProfileName()).getProfileJson();
				Level hotf = SkillTree.hotfLevel(member[0], NeuRepo.leveling());
				TestData.check(SkillTree.hotfExperience(member[0]) == 547000, "HOTF xp 547000 (" + SkillTree.hotfExperience(member[0]) + ")");
				TestData.check(hotf.level == 8 && hotf.maxed && hotf.maxLevel == 8, "HOTF level 8 (maxed) from 547000 xp (" + hotf.level + ")");
				TestData.check(SkillTree.hotfNodes(member[0]).has("center_of_the_forest"), "HOTF nodes of the selected tree found");
				TestData.check(NeuRepo.constant("hotflayout") != null, "hotflayout.json available");
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.MINING;
				ProfileViewerScreen.setOnSecondPage(false);
				screen[0] = new ProfileViewerScreen(p);
				mc.setScreenAndShow(screen[0]);
			});
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(60);
			context.takeScreenshot("forest-hotm");

			int[] gui = new int[2];
			double[] scale = new double[1];
			context.runOnClient(mc -> {
				gui[0] = screen[0].guiLeft();
				gui[1] = screen[0].guiTop();
				scale[0] = screen[0].pixelScale();
			});
			// real click on side button 2 (left of the panel, second slot)
			context.getInput().setCursorPos((gui[0] - 14) * scale[0], (gui[1] + 28 + 14) * scale[0]);
			context.waitTick();
			context.getInput().pressMouse(0);
			context.waitTicks(10);
			TestData.check(context.computeOnClient(mc -> ProfileViewerScreen.isOnSecondPage()), "side button 2 opens Heart of the Forest");
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("forest-hotf");
			context.runOnClient(mc -> {
				try {
					java.lang.reflect.Field f = ProfileViewerScreen.class.getDeclaredField("pages");
					f.setAccessible(true);
					MiningPage page = (MiningPage) ((java.util.Map<?, ?>) f.get(screen[0])).get(ProfileViewerScreen.Page.MINING);
					TestData.check(page.forestPerksRendered() == 36, "36 HOTF perks rendered (" + page.forestPerksRendered() + ")");
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			});

			// perk tooltip: Center of the Forest (3,3); tree origin = (guiLeft + 260, guiTop + 20)
			context.getInput().setCursorPos((gui[0] + 330) * scale[0], (gui[1] + 90) * scale[0]);
			context.waitTicks(5);
			context.takeScreenshot("forest-perk-tooltip");

			// whispers / Galatea tooltips
			context.getInput().setCursorPos((gui[0] + 80) * scale[0], (gui[1] + 72) * scale[0]);
			context.waitTicks(5);
			context.takeScreenshot("forest-whispers-tooltip");
			context.getInput().setCursorPos((gui[0] + 40) * scale[0], (gui[1] + 115) * scale[0]);
			context.waitTicks(5);
			context.takeScreenshot("forest-fish-tooltip");

			// tree 2 has Damage Boost (1,6) selected
			context.runOnClient(mc -> member[0].getAsJsonObject("skill_tree").getAsJsonObject("selected_skill_tree_slot").addProperty("foraging", 2));
			context.getInput().setCursorPos((gui[0] + 290) * scale[0], (gui[1] + 150) * scale[0]);
			context.waitTicks(5);
			context.takeScreenshot("forest-ability-tooltip");
			context.getInput().setCursorPos((gui[0] + 157) * scale[0], (gui[1] + 28) * scale[0]);
			context.waitTicks(5);
			context.takeScreenshot("forest-loadout-tooltip");
		}
	}

	private static JsonObject read(String path) {
		try (InputStream in = ForestGameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
