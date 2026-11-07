package dev.neupv.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.GardenContests;
import dev.neupv.gui.GardenData;
import dev.neupv.gui.GardenPage;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.net.Mojang;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static dev.neupv.test.TestData.check;

/**
 * Garden tab, "Contests" side page, on the user's real profile (src/gametest/resources/realdata, git-ignored).
 * The garden endpoint is not reachable in tests; this page only needs the member json.
 */
public class GardenContestsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		JsonObject profiles = read("/realdata/profiles.json");
		JsonObject player = read("/realdata/player.json");
		if (profiles == null || player == null) {
			System.out.println("[neupv-test] SKIPPED garden contests test (no realdata/*.json)");
			return;
		}
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.getWindow().setWindowed(1920, 991);
				mc.options.guiScale().set(4); // the user's setup
				mc.resizeGui();
				SkyblockProfiles p = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
				p.loadFromJson(profiles, player.getAsJsonObject("player"));
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.GARDEN;
				ProfileViewerScreen.setOnSecondPage(false);
				screen[0] = new ProfileViewerScreen(p);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitTicks(40);
			context.takeScreenshot("garden-contests-0-garden");

			// real click on the second side button
			double scale = context.computeOnClient(mc -> screen[0].pixelScale());
			context.getInput().setCursorPos((screen[0].guiLeft() - 14) * scale, (screen[0].guiTop() + 28 + 14) * scale);
			context.waitTick();
			context.getInput().pressMouse(0);
			context.waitTicks(10);
			context.runOnClient(mc -> check(ProfileViewerScreen.isOnSecondPage(), "side button 2 opens the Contests page"));
			// move the cursor off the PV so the screenshot has no tooltip
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(20);
			context.takeScreenshot("garden-contests-1-page");

			context.runOnClient(mc -> {
				GardenContests d = gardenPage(screen[0]).contestsPage().data();
				check(d != null, "contests data parsed");
				check(d.crops.get(GardenData.Crop.NETHER_WART).personalBest == 6554040L, "NETHER_STALK personal best 6,554,040");
				check(d.crops.get(GardenData.Crop.COCOA_BEANS).personalBest == 6501195L, "INK_SACK:3 personal best 6,501,195");
				check(d.totalContests == 1546, "1546 contests (" + d.totalContests + ")");
				check(d.crops.get(GardenData.Crop.CARROT).participated == 208, "208 carrot contests (" + d.crops.get(GardenData.Crop.CARROT).participated + ")");
				check(d.goldMedals == 10 && d.silverMedals == 22 && d.bronzeMedals == 44, "medal inventory 10/22/44");
				check(d.doubleDrops == 15 && d.farmingLevelCap == 10, "perks 15 / 10");
				GardenContests.CropStats rose = d.crops.get(GardenData.Crop.WILD_ROSE);
				check(rose.highestBracket() == GardenContests.Bracket.DIAMOND && !rose.uniqueBrackets[GardenContests.Bracket.GOLD.ordinal()]
					&& rose.uniqueBrackets[GardenContests.Bracket.PLATINUM.ordinal()], "wild rose: diamond highest, platinum yes, gold no (unique_brackets)");
				check(d.crops.get(GardenData.Crop.SUNFLOWER).highestBracket() == GardenContests.Bracket.DIAMOND, "sunflower: diamond highest bracket");
				int medals = 0;
				for (GardenContests.CropStats s : d.crops.values()) medals += s.totalMedals();
				check(medals == 316 + 173 + 72 + 62 + 42, "claimed medals summed (" + medals + ")");
				check(d.analyzedGreenhouse.size() == 40 && d.discoveredGreenhouse.size() == 43, "greenhouse 40 analyzed / 43 discovered");
				check(d.toolkitUnlocked && d.toolkit.size() == 12, "farming toolkit: 12 tools (" + d.toolkit.size() + ")");
				check(d.crops.get(GardenData.Crop.NETHER_WART).bestPosition > 0, "best placement found");
			});

			// hover Nether Wart (2nd row of the left column)
			int[][] hovers = {
				{25 + 6 + 30, 10 + 18 + 16 + 8},   // Nether Wart
				{29 + 2 * 18 + 8, 149 + 18 + 8}, // a toolkit tool
				{258 + 30, 10 + 37 + 4 + 11},     // brackets reached
				{258 + 30, 104 + 16 + 4},         // discovered
				{258 + 5 + 3 * 14 + 4, 104 + 40 + 2 * 11 + 4} // a mutation icon
			};
			for (int i = 0; i < hovers.length; i++) {
				context.getInput().setCursorPos((screen[0].guiLeft() + hovers[i][0]) * scale, (screen[0].guiTop() + hovers[i][1]) * scale);
				context.waitTicks(5);
				context.takeScreenshot("garden-contests-hover-" + i);
			}

			// back to the garden page with side button 1
			context.getInput().setCursorPos((screen[0].guiLeft() - 14) * scale, (screen[0].guiTop() + 14) * scale);
			context.waitTick();
			context.getInput().pressMouse(0);
			context.waitTicks(10);
			context.runOnClient(mc -> check(!ProfileViewerScreen.isOnSecondPage(), "side button 1 returns to the Garden page"));
			context.runOnClient(mc -> ProfileViewerScreen.setOnSecondPage(false));
		}
	}

	private static GardenPage gardenPage(ProfileViewerScreen screen) {
		try {
			java.lang.reflect.Field pagesField = ProfileViewerScreen.class.getDeclaredField("pages");
			pagesField.setAccessible(true);
			return (GardenPage) ((java.util.Map<?, ?>) pagesField.get(screen)).get(ProfileViewerScreen.Page.GARDEN);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static JsonObject read(String path) {
		try (InputStream in = GardenContestsGameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
