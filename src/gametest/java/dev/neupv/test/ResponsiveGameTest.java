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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Responsive layout: for many window sizes and GUI scales, checks that the whole PV (tabs, side buttons, recent
 * players, profile buttons) is inside the window, that a real click lands on the right Storage icon, and
 * screenshots the trickiest tabs (3D model, scissored HOTM tree and Rift slots).
 */
public class ResponsiveGameTest implements FabricClientGameTest {
	private record Config(int w, int h, int guiScale) {}

	private static final Config[] CONFIGS = {
		new Config(854, 480, 0), new Config(854, 480, 2), new Config(1280, 720, 2), new Config(1280, 720, 3),
		new Config(1280, 720, 4), new Config(1920, 991, 4), new Config(1920, 1080, 0), new Config(800, 600, 3),
		new Config(640, 400, 2),
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] profiles = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				JsonObject real = read("/realdata/profiles.json"), player = read("/realdata/player.json");
				if (real != null && player != null) {
					profiles[0] = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
					profiles[0].loadFromJson(real, player.getAsJsonObject("player"));
				} else {
					profiles[0] = new SkyblockProfiles(TestData.NOTCH);
					profiles[0].loadFromJson(TestData.profilesResponse(), TestData.hypixelPlayer());
				}
			});
			for (Config c : CONFIGS) runConfig(context, c, profiles[0]);
		}
	}

	private static void runConfig(ClientGameTestContext context, Config c, SkyblockProfiles profiles) {
		String tag = c.w + "x" + c.h + "-gui" + (c.guiScale == 0 ? "auto" : c.guiScale);
		ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
		context.runOnClient(mc -> {
			mc.getWindow().setWindowed(c.w, c.h);
			mc.options.guiScale().set(c.guiScale);
			mc.resizeGui();
			ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.INVENTORIES;
			ProfileViewerScreen.setOnSecondPage(false);
			screen[0] = new ProfileViewerScreen(profiles);
			mc.setScreenAndShow(screen[0]);
		});
		context.waitTicks(15);

		// 1. everything inside the window
		context.runOnClient(mc -> {
			var win = mc.getWindow();
			double px = screen[0].pixelScale();
			double left = (screen[0].guiLeft() - 31) * px, right = (screen[0].guiLeft() + ProfileViewerScreen.SIZE_X + 31) * px;
			double top = (screen[0].guiTop() - 30) * px, bottom = (screen[0].guiTop() + ProfileViewerScreen.SIZE_Y + 30) * px;
			TestData.check(left >= -0.5 && top >= -0.5 && right <= win.getWidth() + 0.5 && bottom <= win.getHeight() + 0.5,
				tag + ": PV fits in the window (" + win.getWidth() + "x" + win.getHeight() + ", guiScale " + win.getGuiScale()
					+ ", PV pixel scale " + String.format("%.2f", px) + ", bounds " + (int) left + "," + (int) top + " -> " + (int) right + "," + (int) bottom + ")");
		});

		// 2. a real click on the Ender Chest icon (#2) must select it
		double guiScale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		double ui = context.computeOnClient(mc -> (double) screen[0].uiScale());
		double vx = screen[0].guiLeft() + 19 + 34 + 8, vy = screen[0].guiTop() + 26 + 8;
		context.getInput().setCursorPos(vx * ui * guiScale, vy * ui * guiScale);
		context.waitTick();
		context.getInput().pressMouse(0);
		context.waitTicks(5);
		String selected = context.computeOnClient(mc -> selectedInventory(screen[0]));
		TestData.check("ender_chest_contents".equals(selected), tag + ": click maps to the Ender Chest icon (selected=" + selected + ")");
		context.takeScreenshot("resp-" + tag + "-storage");

		for (ProfileViewerScreen.Page page : new ProfileViewerScreen.Page[]{ProfileViewerScreen.Page.BASIC,
			ProfileViewerScreen.Page.MINING, ProfileViewerScreen.Page.RIFT}) {
			context.runOnClient(mc -> ProfileViewerScreen.currentPage = page);
			context.waitTicks(page == ProfileViewerScreen.Page.BASIC ? 40 : 15);
			context.takeScreenshot("resp-" + tag + "-" + page.name().toLowerCase());
		}
	}

	private static String selectedInventory(ProfileViewerScreen screen) {
		try {
			Field pagesField = ProfileViewerScreen.class.getDeclaredField("pages");
			pagesField.setAccessible(true);
			Object page = ((Map<?, ?>) pagesField.get(screen)).get(ProfileViewerScreen.Page.INVENTORIES);
			Field f = page.getClass().getDeclaredField("selectedInventory");
			f.setAccessible(true);
			return (String) f.get(page);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static JsonObject read(String path) {
		try (InputStream in = ResponsiveGameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
