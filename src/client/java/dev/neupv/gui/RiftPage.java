/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates RiftPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.Fmt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RiftPage extends PvPage {
	private static final Identifier PV_RIFT = Draw.tex("gui/pv_rift.png");
	private static final Identifier ARROW_LEFT = Identifier.withDefaultNamespace("transferable_list/unselect");
	private static final Identifier ARROW_LEFT_HL = Identifier.withDefaultNamespace("transferable_list/unselect_highlighted");
	private static final Identifier ARROW_RIGHT = Identifier.withDefaultNamespace("transferable_list/select");
	private static final Identifier ARROW_RIGHT_HL = Identifier.withDefaultNamespace("transferable_list/select_highlighted");
	private static final int ARROWS_X = 190, ARROWS_Y = 77;
	private static final Map<String, Optional<PvItem>> REPO_ITEMS = new HashMap<>();

	private boolean inInventory = true; // false = in ender chest
	private int pages = 0;
	private int onPage = 0;

	public RiftPage(ProfileViewerScreen screen) {
		super(screen);
	}

	private static PvItem repoItem(String id) {
		Optional<PvItem> o = REPO_ITEMS.get(id);
		if (o == null || o.isEmpty()) {
			// retried until the repo has it (it may still be downloading)
			o = Optional.ofNullable(LegacyItems.fromRepo(id));
			REPO_ITEMS.put(id, o);
		}
		return o.orElse(null);
	}

	private void renderRepoItem(GuiGraphicsExtractor g, String id, int x, int y) {
		PvItem item = repoItem(id);
		if (item != null) g.item(item.stack(), screen.guiLeft() + x, screen.guiTop() + y);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.full(g, PV_RIFT, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null || !selected.getProfileJson().has("rift") || !selected.getProfileJson().get("rift").isJsonObject()) {
			drawErrorMessage(g);
			return;
		}
		RiftData rift = RiftData.of(selected.getProfileJson());

		if (rift.armor != null) drawArmorAndEquipment(g, rift.armor, 27, 64, mouseX, mouseY, true);
		if (rift.equipment != null) drawArmorAndEquipment(g, rift.equipment, 46, 64, mouseX, mouseY, false);

		// pet
		Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, guiLeft + 35, guiTop + 156, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
		drawMontezuma(g, rift, mouseX, mouseY);

		Draw.centeredMaxWidth(g, "§dMotes: §f" + Fmt.shortNumber(rift.motes), guiLeft + 45, guiTop + 16, true, 84, 0xFFFFFFFF);
		if (mouseX > guiLeft + 3 && mouseX < guiLeft + 90 && mouseY > guiTop + 3 && mouseY < guiTop + 25) {
			screen.tooltipToDisplay = PvWidgets.lines("§dLifetime Motes: §f" + Fmt.shortNumber(rift.lifetimeMotes));
		}

		// Timecharms
		if (rift.securedTrophies != null) {
			Draw.aligned(g, "§cTimecharms:", "§f" + rift.securedTrophies.size() + "/8", guiLeft + 336, guiTop + 39, 83);
			if (mouseX > guiLeft + 336 && mouseX < guiLeft + 336 + 80 && mouseY > guiTop + 39 && mouseY < guiTop + 39 + 15) {
				List<Component> tt = new ArrayList<>();
				for (String type : rift.securedTrophies) {
					PvItem charm = repoItem("RIFT_TROPHY_" + type.toUpperCase(java.util.Locale.ROOT));
					if (charm != null) {
						tt.add(Component.literal(charm.displayName + "§7: §a✔"));
					} else {
						tt.add(Component.literal("§c§lERROR LOADING TIMECHARM!"));
						tt.add(Component.literal("§c§lUnknown timecharm: §b" + type));
					}
				}
				screen.tooltipToDisplay = tt;
			}
			renderRepoItem(g, "GLASS", 316, 36);
		}

		Draw.aligned(g, "§6Burger:", "§f" + rift.grubberStacks + "/5", guiLeft + 331, guiTop + 87, 83);
		renderRepoItem(g, "MCGRUBBER_BURGER", 314, 84);

		drawVampire(g, selected, mouseX, mouseY);

		if (hasFoundSouls(selected)) {
			Draw.aligned(g, "§5Enigma Souls:", "§f" + rift.foundSouls + "/52", guiLeft + 331, guiTop + 110, 83);
			renderRepoItem(g, "SKYBLOCK_ENIGMA_SOUL", 314, 106);
		}

		if (!selected.getProfileJson().getAsJsonObject("rift").has("inventory")) {
			Draw.centered(g, "§cInventory API not enabled!", guiLeft + 431 / 2f, guiTop + 61, true);
			return;
		}

		addInventoryButton(g, 156, 16, mouseX, mouseY, "§7Inventory", "CHEST", inInventory);
		addInventoryButton(g, 222, 16, mouseX, mouseY, "§7Ender Chest", "ENDER_CHEST", !inInventory);

		int rows = inInventory ? 4 : 5;
		int invSizeY = rows * 18 + 17 + 7;
		int inventoryX = guiLeft + 203 - 176 / 2;
		int inventoryY = guiTop + 130 - invSizeY / 2;
		InventoriesUi.pvChest(g, inventoryX, inventoryY, rows);
		g.text(Draw.font(), inInventory ? "Inventory" : "Ender Chest", guiLeft + 122, inInventory ? guiTop + 88 : guiTop + 79, 0xFFFFFFFF, true);

		if (!inInventory) {
			if (rift.enderChest == null) {
				drawErrorMessage(g);
				return;
			}
			List<PvItem> ec = rift.enderChest;
			pages = Math.max(1, (int) Math.ceil(ec.size() / 45d));
			onPage = Math.min(onPage, pages - 1);
			drawArrows(g, mouseX, mouseY);
			// NEU's sub-list maths only worked for 2 pages; plain 45-slot pages here
			int start = onPage * 45;
			for (int j = start; j < Math.min(ec.size(), start + 45); j++) {
				int x = inventoryX - guiLeft + 8 + ((j - start) % 9) * 18;
				int y = 91 + ((j - start) / 9) * 18;
				drawSlot(g, ec.get(j), x, y, mouseX, mouseY);
			}
		} else {
			if (rift.inventory == null) {
				drawErrorMessage(g);
				return;
			}
			List<PvItem> inv = rift.inventory;
			// hotbar = first 9 slots, drawn in the bottom row
			for (int i = 0; i < Math.min(9, inv.size()); i++) {
				drawSlot(g, inv.get(i), 123 + i * 18, 154, mouseX, mouseY);
			}
			// NEU removed the hotbar with removeAll(), which also removed every empty slot of the main inventory
			// and shifted the items; the slots keep their positions here.
			for (int i = 9; i < inv.size(); i++) {
				int x = inventoryX - guiLeft + ((i - 9) % 9) * 18 + 8;
				int y = inventoryY - guiTop + (1 + (i - 9) / 9) * 18;
				drawSlot(g, inv.get(i), x, y, mouseX, mouseY);
			}
		}
	}

	private static boolean hasFoundSouls(SkyblockProfiles.SkyblockProfile selected) {
		return dev.neupv.util.J.get(selected.getProfileJson(), "rift.enigma.found_souls") != null;
	}

	private void drawSlot(GuiGraphicsExtractor g, PvItem item, int x, int y, int mouseX, int mouseY) {
		if (item == null) return;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		ItemStack stack = item.stack();
		g.item(stack, guiLeft + x, guiTop + y);
		g.itemDecorations(Draw.font(), stack, guiLeft + x, guiTop + y);
		if (mouseX >= guiLeft + x && mouseX <= guiLeft + x + 16 && mouseY >= guiTop + y && mouseY <= guiTop + y + 16) {
			screen.tooltipToDisplay = PvWidgets.itemTooltip(item);
		}
	}

	private void drawMontezuma(GuiGraphicsExtractor g, RiftData rift, int mouseX, int mouseY) {
		if (rift.foundCats == null) return;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int size = rift.foundCats.size();
		int riftTime = 10 + size * 15;
		int manaRegen = size * 2;
		PvItem pet = null;
		if (rift.montezumaType != null) {
			int rarity = -1;
			for (int i = 0; i < LegacyItems.RARITIES.length; i++) if (LegacyItems.RARITIES[i].equals(rift.montezumaTier)) rarity = i;
			if (rarity >= 0) pet = repoItem(rift.montezumaType + ";" + rarity);
		}
		if (pet != null) {
			g.item(pet.stack(), guiLeft + 37, guiTop + 158);
			if (mouseX > guiLeft + 37 && mouseX < guiLeft + 37 + 20 && mouseY > guiTop + 158 && mouseY < guiTop + 158 + 20) {
				List<Component> tt = new ArrayList<>();
				tt.add(Component.literal(pet.displayName));
				for (String line : pet.lore) {
					if (line.startsWith("§7Found: §9")) line = "§7Found: §9" + size + "/9 Soul Pieces";
					else if (line.startsWith("§7Rift Time: §a+")) line = "§7Rift Time: §a+" + riftTime + "s";
					else if (line.startsWith("§7Mana Regen: §a") && "EPIC".equals(rift.montezumaTier)) line = "§7Mana Regen: §a+" + manaRegen + "%";
					tt.add(Component.literal(line));
				}
				screen.tooltipToDisplay = tt;
			}
		} else if (size > 0) {
			Draw.centered(g, "§cCould not get pet", guiLeft + 91 / 2f, guiTop + 158 + 10, true);
		}
	}

	private void drawVampire(GuiGraphicsExtractor g, SkyblockProfiles.SkyblockProfile selected, int mouseX, int mouseY) {
		Map<String, Level> info = selected.getLevelingInfo();
		Level vampire = info == null ? null : info.get("vampire");
		if (vampire == null) return;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.aligned(g, "§6Vampire", "§f" + (int) vampire.level, guiLeft + 336, guiTop + 61, 60);
		if (vampire.maxed) Draw.goldBar(g, guiLeft + 320, guiTop + 71, 90, screen.startTime);
		else Draw.bar(g, guiLeft + 320, guiTop + 71, 90, vampire.level % 1);

		if (mouseX > guiLeft + 300 && mouseX < guiLeft + 410 && mouseY > guiTop + 58 && mouseY < guiTop + 80) {
			List<Component> tt = new ArrayList<>();
			tt.add(Component.literal("§6Vampire Slayer"));
			if (vampire.maxed) {
				tt.add(Component.literal("§7Progress: §6MAXED!"));
			} else {
				int maxXp = (int) vampire.maxXpForLevel;
				tt.add(Component.literal("§7Progress: §5" + Fmt.shortNumber(Math.round((vampire.level % 1) * maxXp)) + "/" + Fmt.shortNumber(maxXp)));
			}
			screen.tooltipToDisplay = tt;
		}
	}

	private void drawArmorAndEquipment(GuiGraphicsExtractor g, List<PvItem> items, int x, int y, int mouseX, int mouseY, boolean reverse) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		List<PvItem> list = new ArrayList<>(items);
		if (reverse) java.util.Collections.reverse(list); // API order is boots..helmet
		for (int i = 0; i < Math.min(4, list.size()); i++) {
			PvItem item = list.get(i);
			if (item == null) continue;
			int iy = y + i * 18;
			g.item(item.stack(), guiLeft + x, guiTop + iy);
			g.itemDecorations(Draw.font(), item.stack(), guiLeft + x, guiTop + iy);
			if (mouseX >= guiLeft + x - 1 && mouseX <= guiLeft + x + 17 && mouseY >= guiTop + iy && mouseY <= guiTop + iy + 16) {
				screen.tooltipToDisplay = PvWidgets.itemTooltip(item);
			}
		}
	}

	private void addInventoryButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, String title, String itemId, boolean selected) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (selected) {
			// NEU draws the selected button with flipped UVs (20 -> 0): the bevel is inverted, it looks pressed
			g.blit(ProfileViewerScreen.PV_ELEMENTS, guiLeft + x, guiTop + y, guiLeft + x + 20, guiTop + y + 20, 20 / 256f, 0, 20 / 256f, 0);
		} else {
			Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, guiLeft + x, guiTop + y, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
		}
		renderRepoItem(g, itemId, x + 2, y + 2);
		if (mouseX >= guiLeft + x - 1 && mouseX <= guiLeft + x + 20 && mouseY >= guiTop + y && mouseY <= guiTop + y + 20) {
			screen.tooltipToDisplay = PvWidgets.lines(title);
		}
	}

	/** NEU ArrowPagesUtils.onDraw: arrows from the resource-pack screen, "page/total" in between. */
	private void drawArrows(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (pages < 2) return;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int leftX = guiLeft + ARROWS_X, rightX = leftX + 37, y = guiTop + ARROWS_Y;
		boolean leftSel = Draw.within(mouseX, mouseY, leftX, y, 7, 11);
		boolean rightSel = Draw.within(mouseX, mouseY, rightX, y, 7, 11);
		// 1.8 resource_packs.png regions (34..48, 5..27) and (10..24, 5..27) = these 32x32 sprites, drawn at half size
		if (onPage != 0) arrow(g, leftSel ? ARROW_LEFT_HL : ARROW_LEFT, 2, leftX, y);
		if (onPage != pages - 1) arrow(g, rightSel ? ARROW_RIGHT_HL : ARROW_RIGHT, 10, rightX, y);
		Draw.centeredMaxWidth(g, (onPage + 1) + "/" + pages, leftX + 22, y + 6, false, 28, 0xFF000000);
	}

	private static void arrow(GuiGraphicsExtractor g, Identifier sprite, int u, int x, int y) {
		g.enableScissor(x, y, x + 7, y + 11);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.5f, 0.5f);
		g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, -u, -5, 32, 32);
		g.pose().popMatrix();
		g.disableScissor();
	}

	private void drawErrorMessage(GuiGraphicsExtractor g) {
		Draw.centered(g, "§cNo Rift data available!", screen.guiLeft() + 431 / 2f, screen.guiTop() + 61, true);
	}

	private static void playPressSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (mouseX >= guiLeft + 156 - 1 && mouseX <= guiLeft + 156 + 20 && mouseY >= guiTop + 16 && mouseY <= guiTop + 16 + 20) {
			inInventory = true;
			playPressSound();
			return true;
		}
		if (mouseX >= guiLeft + 222 - 1 && mouseX <= guiLeft + 222 + 20 && mouseY >= guiTop + 16 && mouseY <= guiTop + 16 + 20) {
			inInventory = false;
			playPressSound();
			return true;
		}
		if (!inInventory && pages > 1) {
			int y = guiTop + ARROWS_Y;
			if (Draw.within(mouseX, mouseY, guiLeft + ARROWS_X, y, 7, 11) && onPage > 0) {
				onPage--;
				playPressSound();
				return true;
			}
			if (Draw.within(mouseX, mouseY, guiLeft + ARROWS_X + 37, y, 7, 11) && onPage < pages - 1) {
				onPage++;
				playPressSound();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode) {
		switch (keyCode) {
			case 49 -> { // GLFW_KEY_1
				inInventory = true;
				playPressSound();
				return true;
			}
			case 50 -> { // GLFW_KEY_2
				inInventory = false;
				playPressSound();
				return true;
			}
			case 263 -> { // GLFW_KEY_LEFT
				if (!inInventory && onPage > 0) {
					onPage--;
					return true;
				}
			}
			case 262 -> { // GLFW_KEY_RIGHT
				if (!inInventory && onPage < pages - 1) {
					onPage++;
					return true;
				}
			}
			default -> {
			}
		}
		return false;
	}

	@Override
	public void resetCache() {
		onPage = 0;
	}

}
