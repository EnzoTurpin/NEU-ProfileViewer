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
			// a menu showing Hypixel's "Accessory Bag" item: BagIcons must remember it for the Storage tab
			context.runOnClient(mc -> {
				net.minecraft.world.item.ItemStack bag = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
				dev.neupv.item.LegacyItems.rename(bag, "§aAccessory Bag");
				bag.set(net.minecraft.core.component.DataComponents.PROFILE,
					dev.neupv.item.LegacyItems.skull("x", TestData.HEAD_TEXTURE).get(net.minecraft.core.component.DataComponents.PROFILE));
				mc.player.getInventory().setItem(9, bag);
				mc.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			});
			context.waitTicks(25);
			context.runOnClient(mc -> {
				var icon = dev.neupv.item.BagIcons.orDefault("talisman_bag", net.minecraft.world.item.ItemStack.EMPTY);
				TestData.check(icon.is(net.minecraft.world.item.Items.PLAYER_HEAD), "Hypixel's Accessory Bag item captured from a menu");
				mc.player.getInventory().setItem(9, net.minecraft.world.item.ItemStack.EMPTY);
				mc.setScreenAndShow(null);
			});
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.getWindow().setWindowed(1920, 991);
				mc.options.guiScale().set(4); // the user's setup (1920x991 window, GUI scale 4)
				mc.resizeGui();
				SkyblockProfiles p = new SkyblockProfiles(Mojang.dashless(player.getAsJsonObject("player").get("uuid").getAsString()));
				p.loadFromJson(profiles, player.getAsJsonObject("player"));
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.BASIC;
				ProfileViewerScreen.setOnSecondPage(false);
				screen[0] = new ProfileViewerScreen(p);
				mc.setScreenAndShow(screen[0]);
				var lv = p.getProfile(p.getLatestProfileName()).getLevelingInfo();
				TestData.check(lv.get("hunting") != null && lv.get("hunting").maxed && lv.get("hunting").level == 50, "hunting skill present and capped at 50");
				TestData.check(lv.get("foraging").maxLevel == 54 && lv.get("foraging").level == 54, "foraging cap 50 + 4 extra levels = 54 (" + lv.get("foraging").level + ")");
				TestData.check(lv.get("taming").maxLevel == 60 && lv.get("farming").maxLevel == 60, "taming and farming caps 60");
			});
			context.waitTicks(100); // skin download
			context.takeScreenshot("real-skills");
			context.runOnClient(mc -> ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.INVENTORIES);
			context.waitTicks(20);
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
			// last ender chest page: open the ender chest and go to page 9 with the right arrow key
			context.runOnClient(mc -> {
				try {
					java.lang.reflect.Field pagesField = ProfileViewerScreen.class.getDeclaredField("pages");
					pagesField.setAccessible(true);
					Object page = ((java.util.Map<?, ?>) pagesField.get(screen[0])).get(ProfileViewerScreen.Page.INVENTORIES);
					java.lang.reflect.Field sel = page.getClass().getDeclaredField("selectedInventory");
					sel.setAccessible(true);
					sel.set(page, "ender_chest_contents");
					java.lang.reflect.Field idx = page.getClass().getDeclaredField("currentInventoryIndex");
					idx.setAccessible(true);
					idx.set(page, 8);
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("real-ec-last-page");
			for (ProfileViewerScreen.Page page : new ProfileViewerScreen.Page[]{ProfileViewerScreen.Page.DUNGEON,
				ProfileViewerScreen.Page.EXTRA, ProfileViewerScreen.Page.COLLECTIONS, ProfileViewerScreen.Page.TROPHY_FISH}) {
				context.runOnClient(mc -> ProfileViewerScreen.currentPage = page);
				context.waitTicks(40);
				context.takeScreenshot("real-" + page.name().toLowerCase());
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
