package dev.neupv.test;

import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.InventoriesPage;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.item.LegacyItems;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Regression tests for real-play feedback: icon names, and opening Storage inventories with a real mouse click. */
public class ClickFixesGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			context.runOnClient(mc -> {
				TestData.check("Potion Bag".equals(dev.neupv.util.Fmt.stripColor(LegacyItems.icon(Items.POTION, "§7Potion Bag").getHoverName().getString())),
					"potion icon keeps its name (was 'Uncraftable Potion')");
				TestData.check("Master Mode".equals(dev.neupv.util.Fmt.stripColor(LegacyItems.rename(LegacyItems.skull("x", TestData.HEAD_TEXTURE), "§cMaster Mode").getHoverName().getString())),
					"renamed head keeps its name (was 'neupv's Head')");
			});
			ProfileViewerScreen[] screen = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				JsonObject resp = TestData.profilesResponse(member -> {
					JsonObject inv = member.getAsJsonObject("inventory");
					String chest = TestData.encode(List.of(TestData.item(264, 0, "§fDiamond", List.of("§f§lCOMMON"), "DIAMOND", null, null)));
					JsonObject ec = new JsonObject();
					ec.addProperty("data", chest);
					inv.add("ender_chest_contents", ec);
					JsonObject wr = new JsonObject();
					wr.addProperty("data", chest);
					inv.add("wardrobe_contents", wr);
				}, p -> {});
				profiles.loadFromJson(resp, TestData.hypixelPlayer());
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.INVENTORIES;
				ProfileViewerScreen.setOnSecondPage(false);
				screen[0] = new ProfileViewerScreen(profiles);
				mc.setScreenAndShow(screen[0]);
			});
			context.waitTicks(20);
			// icon #2 = Ender Chest, icon #6 = Wardrobe (3 per row, 34 px apart, at guiLeft+19 / guiTop+26)
			clickAndCheck(context, screen[0], 1, "ender_chest_contents");
			context.takeScreenshot("click-enderchest");
			clickAndCheck(context, screen[0], 5, "wardrobe_contents");
			context.takeScreenshot("click-wardrobe");
			// some players click with button 1 (right click / swapped mouse buttons): it must work too
			clickAndCheck(context, screen[0], 2, "backpack_contents", 1);
		}
	}

	private static void clickAndCheck(ClientGameTestContext context, ProfileViewerScreen screen, int index, String expected) {
		clickAndCheck(context, screen, index, expected, 0);
	}

	private static void clickAndCheck(ClientGameTestContext context, ProfileViewerScreen screen, int index, String expected, int mouseButton) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		int x = screen.guiLeft() + 19 + 34 * (index % 3) + 8;
		int y = screen.guiTop() + 26 + 34 * (index / 3) + 8;
		context.getInput().setCursorPos(x * scale, y * scale);
		context.waitTick();
		context.getInput().pressMouse(mouseButton);
		context.waitTicks(5);
		String selected = context.computeOnClient(mc -> {
			try {
				Field pagesField = ProfileViewerScreen.class.getDeclaredField("pages");
				pagesField.setAccessible(true);
				InventoriesPage page = (InventoriesPage) ((Map<?, ?>) pagesField.get(screen)).get(ProfileViewerScreen.Page.INVENTORIES);
				Field f = InventoriesPage.class.getDeclaredField("selectedInventory");
				f.setAccessible(true);
				return (String) f.get(page);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		TestData.check(expected.equals(selected), "real click (button " + mouseButton + ") opens " + expected + " (selected=" + selected + ")");
	}
}
