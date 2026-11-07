package dev.neupv.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.HuntingData;
import dev.neupv.gui.HuntingPage;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.net.Mojang;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Hunting tab on the user's real profile (src/gametest/resources/realdata, git-ignored): skips without it. */
public class HuntingGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		JsonObject profiles = read("/realdata/profiles.json");
		JsonObject player = read("/realdata/player.json");
		if (profiles == null || player == null) {
			System.out.println("[neupv-test] SKIPPED hunting test (no realdata/*.json)");
			return;
		}
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(40);
			SkyblockProfiles[] p = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				p[0] = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
				p[0].loadFromJson(profiles, player.getAsJsonObject("player"));
			});
			run(context, p[0], 1920, 991, 4, "gui4");
			run(context, p[0], 1280, 720, 0, "720-auto");
			// partial / locked attributes (the real profile has everything maxed): edit the loaded member json
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				JsonObject stacks = J.obj(p[0].getProfile(p[0].getLatestProfileName()).getProfileJson(), "attributes.stacks");
				stacks.addProperty("lifeline", 20);
				stacks.remove("speed");
				screen[0] = new ProfileViewerScreen(p[0]);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitTicks(10);
			context.runOnClient(mc -> {
				HuntingData.Attribute lifeline = page(screen[0]).info().attributes.stream().filter(a -> a.id.equals("lifeline")).findFirst().orElseThrow();
				TestData.check(lifeline.level == 6 && lifeline.stacksForNext == 22, "lifeline 20 shards = level 6, next at 22 (" + lifeline.level + ")");
				page(screen[0]).mouseScrolled(screen[0].guiLeft() + 330, screen[0].guiTop() + 100, 0, -10);
				for (int i = 0; i < 6; i++) page(screen[0]).mouseScrolled(screen[0].guiLeft() + 330, screen[0].guiTop() + 100, 0, -1);
			});
			context.waitTicks(5);
			context.takeScreenshot("hunting-partial-last-page");
		}
	}

	private static void run(ClientGameTestContext context, SkyblockProfiles profiles, int w, int h, int guiScale, String tag) {
		ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
		context.runOnClient(mc -> {
			mc.getWindow().setWindowed(w, h);
			mc.options.guiScale().set(guiScale);
			mc.resizeGui();
			ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.HUNTING;
			ProfileViewerScreen.setOnSecondPage(false);
			screen[0] = new ProfileViewerScreen(profiles);
			mc.setScreenAndShow(screen[0]);
		});
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(60); // shard skull textures
		HuntingPage page = context.computeOnClient(mc -> page(screen[0]));
		context.runOnClient(mc -> {
			HuntingData.Info info = page.info();
			TestData.check(info != null, tag + ": attribute data computed");
			HuntingData.Attribute lifeline = info.attributes.stream().filter(a -> a.id.equals("lifeline")).findFirst().orElse(null);
			TestData.check(lifeline != null && lifeline.stacks == 48 && lifeline.level == 10 && lifeline.maxed(),
				tag + ": lifeline 48 shards = level 10 maxed");
			TestData.check(lifeline.shardName.equals("Kada Knight") && lifeline.rarity.equals("RARE"), tag + ": lifeline = Kada Knight, rare");
			TestData.check(info.unlocked == 324 && info.possible == 324, tag + ": 324/324 attributes (" + info.unlocked + "/" + info.possible + ")");
			TestData.check(info.fused == 108, tag + ": 108 shards fused");
			HuntingData.Attribute titanoboa = info.attributes.stream().filter(a -> "Titanoboa".equals(a.shardName)).findFirst().orElse(null);
			TestData.check(titanoboa != null && titanoboa.owned == 26, tag + ": 26 Titanoboa shards owned");
			JsonObject member = screen[0].getSelectedProfile().getProfileJson();
			JsonObject captures = J.objOrEmpty(member, "safari.biome_captures");
			int sum = 0;
			for (String k : captures.keySet()) sum += captures.get(k).getAsInt();
			TestData.check(sum == 5099 + 5712 + 5310 + 5367, tag + ": safari captures summed (" + sum + ")");
		});
		context.takeScreenshot("hunting-" + tag);

		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale() * screen[0].uiScale());
		// hover the first attribute slot
		context.getInput().setCursorPos((screen[0].guiLeft() + 250 + 10) * scale, (screen[0].guiTop() + 32 + 10) * scale);
		context.waitTicks(5);
		context.takeScreenshot("hunting-" + tag + "-attribute-tooltip");
		// hover the discovered critters line
		context.getInput().setCursorPos((screen[0].guiLeft() + 180) * scale, (screen[0].guiTop() + 117) * scale);
		context.waitTicks(5);
		context.takeScreenshot("hunting-" + tag + "-critters-tooltip");
		// wheel over the grid -> page 2
		context.getInput().setCursorPos((screen[0].guiLeft() + 330) * scale, (screen[0].guiTop() + 100) * scale);
		context.waitTick();
		context.getInput().scroll(-1);
		context.waitTicks(5);
		context.runOnClient(mc -> TestData.check(page.page() == 1, tag + ": mouse wheel turns the attribute page (" + page.page() + ")"));
		context.takeScreenshot("hunting-" + tag + "-page2");
	}

	private static HuntingPage page(ProfileViewerScreen screen) {
		try {
			java.lang.reflect.Field pagesField = ProfileViewerScreen.class.getDeclaredField("pages");
			pagesField.setAccessible(true);
			return (HuntingPage) ((java.util.Map<?, ?>) pagesField.get(screen)).get(ProfileViewerScreen.Page.HUNTING);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static JsonObject read(String path) {
		try (InputStream in = HuntingGameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
