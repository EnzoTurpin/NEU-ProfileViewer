package dev.neupv.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
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

/**
 * Real profile (realdata, git-ignored): Profile Stats misc box + bank tooltip, Dungeoneering "Total Runs" tooltip and
 * the Pets "Autopet" line, at the user's setup (1920x991, GUI scale 4).
 */
public class ExtrasGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		JsonObject profiles = read("/realdata/profiles.json");
		JsonObject player = read("/realdata/player.json");
		if (profiles == null || player == null) {
			System.out.println("[neupv-test] SKIPPED extras test (no realdata/*.json)");
			return;
		}
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.getWindow().setWindowed(1920, 991);
				mc.options.guiScale().set(4);
				mc.resizeGui();
				SkyblockProfiles p = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
				p.loadFromJson(profiles, player.getAsJsonObject("player"));
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.EXTRA;
				ProfileViewerScreen.setOnSecondPage(false);
				screen[0] = new ProfileViewerScreen(p);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitTicks(40);
			// Profile Stats: col3 bottom box at (guiLeft + 228, guiTop + 105), 8 px per line
			hover(context, screen[0], 230, 50);
			context.takeScreenshot("extras-stats");
			hover(context, screen[0], 230, 108);
			context.takeScreenshot("extras-stats-damage");
			hover(context, screen[0], 230, 108 + 8 * 5);
			context.takeScreenshot("extras-stats-races");
			hover(context, screen[0], 230, 108 + 8 * 7);
			context.takeScreenshot("extras-stats-corpses");
			hover(context, screen[0], 30, 30);
			context.takeScreenshot("extras-stats-bank");

			context.runOnClient(mc -> ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.DUNGEON);
			hover(context, screen[0], 30, 83);
			context.takeScreenshot("extras-dungeon-runs");

			context.runOnClient(mc -> ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.PETS);
			hover(context, screen[0], 200, 200);
			context.takeScreenshot("extras-pets");
			hover(context, screen[0], 330, 107);
			context.takeScreenshot("extras-pets-autopet");
		}
	}

	/** Moves the real cursor to PV coordinates (guiLeft + dx, guiTop + dy). */
	private static void hover(ClientGameTestContext context, ProfileViewerScreen screen, int dx, int dy) {
		double px = context.computeOnClient(mc -> screen.pixelScale());
		context.getInput().setCursorPos((screen.guiLeft() + dx) * px, (screen.guiTop() + dy) * px);
		context.waitTicks(10);
	}

	private static JsonObject read(String path) {
		try (InputStream in = ExtrasGameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
