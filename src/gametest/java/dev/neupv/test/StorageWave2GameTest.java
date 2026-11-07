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
import java.util.List;
import java.util.Map;

/**
 * Storage tab, wave 2, on the user's real profile (src/gametest/resources/realdata, git-ignored): wardrobe read from
 * loadout.armor, loadouts, carnival masks and backpack icons. Real clicks at 1920x991, GUI scale 4.
 */
public class StorageWave2GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		JsonObject profiles = read("/realdata/profiles.json");
		JsonObject player = read("/realdata/player.json");
		if (profiles == null || player == null) {
			System.out.println("[neupv-test] SKIPPED storage wave 2 test (no realdata/*.json)");
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
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.INVENTORIES;
				ProfileViewerScreen.setOnSecondPage(false);
				screen[0] = new ProfileViewerScreen(p);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitTicks(40);

			// wardrobe (selector 6)
			clickIcon(context, screen[0], 5);
			context.takeScreenshot("storage2-wardrobe-p1");
			context.runOnClient(mc -> {
				Object page = page(screen[0]);
				Object data = field(page, "loadoutData");
				TestData.check(data != null, "loadout data parsed");
				Map<?, ?> armor = (Map<?, ?>) field(data, "armorSets");
				TestData.check(armor.size() == 27, "27 wardrobe sets parsed from loadout.armor (" + armor.size() + ")");
				TestData.check(((Map<?, ?>) field(data, "equipmentSets")).size() == 15, "15 equipment sets parsed");
				TestData.check(((List<?>) field(data, "loadouts")).size() == 23, "23 loadouts parsed");
				Object[] set1 = (Object[]) armor.get(1);
				TestData.check(set1[0] != null && "WITHER_GOGGLES".equals(skyblockId(set1[0])), "set 1 helmet = Wither Goggles");
				Object[] set9 = (Object[]) armor.get(9);
				TestData.check(set9[0] != null && set9[3] != null, "equipped set 9 filled with the worn armor");
				Map<?, ?> items = (Map<?, ?>) field(page, "inventoryItems");
				Object[] pages = (Object[]) items.get("wardrobe_contents");
				TestData.check(pages != null && pages.length == 3, "wardrobe: 3 pages of 9 sets");
			});
			clickArrow(context, screen[0], true);
			context.takeScreenshot("storage2-wardrobe-p2");
			clickArrow(context, screen[0], true);
			context.takeScreenshot("storage2-wardrobe-p3");

			// loadouts (selector 8): page 1, hover the first label, page 2
			clickIcon(context, screen[0], 7);
			context.takeScreenshot("storage2-loadouts-p1");
			hover(context, screen[0], 320 - 88 + 40, 14 + 17 + 4);
			context.takeScreenshot("storage2-loadouts-tooltip");
			hover(context, screen[0], 320 - 88 + 8 + 8 * 18 + 8, 14 + 17 + 10 + 9);
			context.takeScreenshot("storage2-loadouts-pet");
			clickArrow(context, screen[0], true);
			context.takeScreenshot("storage2-loadouts-p2");

			// carnival masks (selector 9)
			clickIcon(context, screen[0], 8);
			context.takeScreenshot("storage2-carnival");
			context.runOnClient(mc -> {
				List<?> masks = (List<?>) field(page(screen[0]), "carnivalItems");
				TestData.check(masks != null && masks.size() == 9, "carnival mask inventory has 9 slots");
				TestData.check(masks.get(0) != null && "SALMON_MASK".equals(skyblockId(masks.get(0))), "first mask = Salmon Mask");
			});

			// backpacks (selector 3): first page, then page 9 (Jumbo Backpack)
			clickIcon(context, screen[0], 2);
			context.takeScreenshot("storage2-backpack-1");
			for (int i = 0; i < 8; i++) clickArrow(context, screen[0], false);
			context.takeScreenshot("storage2-backpack-9");
			context.runOnClient(mc -> {
				List<?> icons = (List<?>) field(page(screen[0]), "backpackIcons");
				TestData.check(icons != null && icons.size() == 18, "18 backpack icons (" + (icons == null ? 0 : icons.size()) + ")");
				TestData.check(icons.get(8) != null && "JUMBO_BACKPACK".equals(skyblockId(icons.get(8))), "backpack 9 icon = Jumbo Backpack");
			});
		}
	}

	private static void clickIcon(ClientGameTestContext context, ProfileViewerScreen screen, int i) {
		click(context, screen, 19 + 34 * (i % 3) + 8, 26 + 34 * (i / 3) + 8);
	}

	private static void clickArrow(ClientGameTestContext context, ProfileViewerScreen screen, boolean wait) {
		click(context, screen, 320 + 6, 177 + 8);
		if (!wait) return;
		context.waitTicks(5);
	}

	private static void click(ClientGameTestContext context, ProfileViewerScreen screen, int px, int py) {
		hover(context, screen, px, py);
		context.getInput().pressMouse(0);
		context.waitTicks(10);
	}

	/** Moves the mouse to PV coordinates (guiLeft/guiTop relative), converted through the PV scale. */
	private static void hover(ClientGameTestContext context, ProfileViewerScreen screen, int px, int py) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		double ui = context.computeOnClient(mc -> (double) screen.uiScale());
		context.getInput().setCursorPos((screen.guiLeft() + px) * ui * scale, (screen.guiTop() + py) * ui * scale);
		context.waitTicks(3);
	}

	private static Object page(ProfileViewerScreen screen) {
		Map<?, ?> pages = (Map<?, ?>) field(screen, "pages");
		return pages.get(ProfileViewerScreen.Page.INVENTORIES);
	}

	private static String skyblockId(Object pvItem) {
		return (String) field(pvItem, "skyblockId");
	}

	private static Object field(Object o, String name) {
		try {
			Class<?> c = o.getClass();
			while (c != null) {
				try {
					Field f = c.getDeclaredField(name);
					f.setAccessible(true);
					return f.get(o);
				} catch (NoSuchFieldException e) {
					c = c.getSuperclass();
				}
			}
			throw new NoSuchFieldException(name);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static JsonObject read(String path) {
		try (InputStream in = StorageWave2GameTest.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
