package dev.neupv.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.CollectionsData;
import dev.neupv.gui.CollectionsPage;
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
import java.util.List;

import static dev.neupv.test.TestData.check;

/**
 * Collections tab built from Hypixel's collections resource, on the user's real profile (src/gametest/resources/realdata,
 * git-ignored): every category is opened with a real click at 1920x991 / GUI scale 4 and screenshotted.
 */
public class CollectionsWave2GameTest implements FabricClientGameTest {
	private static final List<String> NEW_IDS = List.of("MOONFLOWER", "DOUBLE_PLANT", "WILD_ROSE", "SEA_LUMIES", "RUBY_VEILSHROOM",
		"VINESAP", "LUSHLILAC", "MANGROVE_LOG", "HELIX_LOG", "HONEYCOMB", "FIG_LOG", "TENDER_WOOD", "LOTUS");

	@Override
	public void runTest(ClientGameTestContext context) {
		JsonObject profiles = read("/realdata/profiles.json");
		JsonObject player = read("/realdata/player.json");
		if (profiles == null || player == null) {
			System.out.println("[neupv-test] SKIPPED collections wave 2 test (no realdata/*.json)");
			return;
		}
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(40);
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.getWindow().setWindowed(1920, 991);
				mc.options.guiScale().set(4);
				mc.resizeGui();
				SkyblockProfiles p = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
				p.loadFromJson(profiles, player.getAsJsonObject("player"));
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.COLLECTIONS;
				ProfileViewerScreen.setOnSecondPage(false);
				CollectionsPage.selectCategory(1); // the clicks below must move it back to Farming
				screen[0] = new ProfileViewerScreen(p);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitFor(mc -> screen[0].getProfile().fetchUrl(CollectionsData.COLLECTIONS_URL).state() != SkyblockProfiles.FetchState.LOADING, 20 * 60);
			context.waitTicks(20);

			@SuppressWarnings("unchecked")
			List<CollectionsData.Category>[] layout = new List[1];
			context.runOnClient(mc -> {
				SkyblockProfiles.Fetch f = screen[0].getProfile().fetchUrl(CollectionsData.COLLECTIONS_URL);
				check(f.state() == SkyblockProfiles.FetchState.OK, "Hypixel collections resource loaded (" + f.error() + ")");
				layout[0] = CollectionsData.layout(f.json());
				check(layout[0].size() >= 6, "categories from Hypixel: " + layout[0].stream().map(CollectionsData.Category::id).toList());
				for (String id : NEW_IDS) {
					CollectionsData.Category cat = layout[0].stream().filter(c -> c.collections().contains(id)).findFirst().orElse(null);
					check(cat != null && cat.displays().get(id) != null, id + " listed (" + (cat == null ? "-" : cat.id() + ", icon "
						+ cat.displays().get(id).getItem() + " '" + cat.displays().get(id).getHoverName().getString() + "'") + ")");
				}
				CollectionsData.ProfileInfo info = CollectionsData.compute(screen[0].getSelectedProfile().getOuterProfileJson(),
					screen[0].getProfile().getUuidString(), f.json());
				CollectionsData.CollectionInfo moon = info == null ? null : info.collections().get("MOONFLOWER");
				check(moon != null && moon.personal().longValue() == 55821186L && moon.total().longValue() >= 55821186L,
					"Moonflower personal 55,821,186 (total " + (moon == null ? "-" : moon.total()) + ")");
				check(info.collections().get("LOTUS").unlockedTiers() > 0, "Lotus has unlocked tiers");
				for (CollectionsData.Category c : layout[0]) {
					System.out.println("[neupv-test] " + c.id() + " collections=" + c.collections() + " minions=" + c.minions());
				}
				CollectionsData.Category farming = layout[0].stream().filter(c -> c.id().equals("FARMING")).findFirst().orElseThrow();
				CollectionsData.Category fishing = layout[0].stream().filter(c -> c.id().equals("FISHING")).findFirst().orElseThrow();
				check(farming.collections().get(0).equals("WHEAT") && farming.collections().size() == 20, "Farming keeps NEU order, 20 collections");
				check(farming.minions().contains("SUNFLOWER"), "Sunflower minion placed in Farming");
				check(fishing.minions().contains("LILY_PAD"), "Lily Pad minion placed in Fishing");
			});

			double scale = context.computeOnClient(mc -> screen[0].pixelScale());
			int n = layout[0].size();
			int ySize = (int) (162f / (n - 1 + 0.0000001f));
			for (int i = 0; i < n; i++) {
				String id = layout[0].get(i).id();
				click(context, scale, screen[0].guiLeft() + 17, screen[0].guiTop() + 10 + ySize * i + 10);
				context.getInput().setCursorPos(2, 2);
				context.waitTicks(15);
				String sel = context.computeOnClient(mc -> CollectionsPage.selectedCategory());
				check(id.equals(sel), "click on category " + (i + 1) + " selects " + id + " (got " + sel + ")");
				context.takeScreenshot("colw2-" + id.toLowerCase());
				if (layout[0].get(i).collections().size() > 20) {
					// next page arrow (NEU: guiLeft + 100 + 15 + 250, guiTop + 6, 12x16)
					click(context, scale, screen[0].guiLeft() + 100 + 15 + 250 + 6, screen[0].guiTop() + 14);
					context.getInput().setCursorPos(2, 2);
					context.waitTicks(15);
					context.takeScreenshot("colw2-" + id.toLowerCase() + "-page2");
				}
			}
			// number key 4 = Foraging, hover a new collection for its tooltip
			context.getInput().pressKey(52); // GLFW_KEY_4
			context.waitTicks(5);
			check("FORAGING".equals(context.computeOnClient(mc -> CollectionsPage.selectedCategory())), "key 4 selects Foraging");
			float pad = (190 - 5 * 20) / 6f, ypad = (202 - 4 * 20) / 5f;
			int j = 6; // first collection after NEU's six logs
			context.getInput().setCursorPos((screen[0].guiLeft() + 39 + pad + (pad + 20) * (j % 5) + 10) * scale,
				(screen[0].guiTop() + 7 + ypad + (ypad + 20) * (j / 5) + 10) * scale);
			context.waitTicks(10);
			context.takeScreenshot("colw2-foraging-tooltip");
		}
	}

	private static void click(ClientGameTestContext context, double scale, double x, double y) {
		context.getInput().setCursorPos(x * scale, y * scale);
		context.waitTick();
		context.getInput().pressMouse(0);
	}

	private static JsonObject read(String path) {
		try (InputStream in = CollectionsWave2GameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
