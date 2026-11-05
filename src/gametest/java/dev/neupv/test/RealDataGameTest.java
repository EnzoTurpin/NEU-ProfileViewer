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
 * Optional: replays a real profile saved in src/gametest/resources/realdata (git-ignored, personal data).
 * Clicks every Storage inventory icon at the user's GUI scale and screenshots each one.
 */
public class RealDataGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		JsonObject profiles = read("/realdata/profiles.json");
		JsonObject player = read("/realdata/player.json");
		if (profiles == null || player == null) {
			System.out.println("[neupv-test] SKIPPED real data test (no realdata/*.json)");
			return;
		}
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.getWindow().setWindowed(1920, 991);
				mc.options.guiScale().set(4); // the user's setup (1920x991 window, GUI scale 4)
				mc.resizeGui();
				SkyblockProfiles p = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
				p.loadFromJson(profiles, player.getAsJsonObject("player"));
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.INVENTORIES;
				ProfileViewerScreen.setOnSecondPage(false);
				screen[0] = new ProfileViewerScreen(p);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitTicks(40);
			for (int i = 0; i < 7; i++) {
				double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
				int x = screen[0].guiLeft() + 19 + 34 * (i % 3) + 8;
				int y = screen[0].guiTop() + 26 + 34 * (i / 3) + 8;
				context.getInput().setCursorPos(x * scale, y * scale);
				context.waitTick();
				context.getInput().pressMouse(0);
				context.waitTicks(10);
				context.takeScreenshot("real-inv-" + i);
			}
		}
	}

	private static JsonObject read(String path) {
		try (InputStream in = RealDataGameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
