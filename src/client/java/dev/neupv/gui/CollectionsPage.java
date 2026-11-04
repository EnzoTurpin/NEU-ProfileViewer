/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates CollectionsPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.item.PvItem;
import dev.neupv.item.LegacyItems;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class CollectionsPage extends PvPage {
	private static final Identifier PV_COLS = Draw.tex("gui/pv_cols.png");
	private static final Identifier ARROW_RIGHT = Identifier.withDefaultNamespace("transferable_list/select");
	private static final Identifier ARROW_RIGHT_HL = Identifier.withDefaultNamespace("transferable_list/select_highlighted");
	private static final Identifier ARROW_LEFT = Identifier.withDefaultNamespace("transferable_list/unselect");
	private static final Identifier ARROW_LEFT_HL = Identifier.withDefaultNamespace("transferable_list/unselect_highlighted");
	private static final int COLLS_XCOUNT = 5;
	private static final int COLLS_YCOUNT = 4;
	private static final float COLLS_XPADDING = (190 - COLLS_XCOUNT * 20) / (float) (COLLS_XCOUNT + 1);
	private static final float COLLS_YPADDING = (202 - COLLS_YCOUNT * 20) / (float) (COLLS_YCOUNT + 1);
	/** NEU's list skipped "XVIII", so tiers 18+ were off by one. */
	private static final String[] ROMANS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII",
		"XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
	private static final int GREY = 0xFF808080, GOLD = 0xFFFFD700, TINT = 0xFFFFB900;

	private static ItemStack selectedCategory = null;
	private int page = 0;
	private int maxPage = 0;
	private CollectionsData.ProfileInfo info;
	private SkyblockProfiles.SkyblockProfile infoFor;

	public CollectionsPage(ProfileViewerScreen screen) {
		super(screen);
	}

	/** NEU's page arrows (vanilla resource_packs.png in 1.8.9, the transferable_list sprites now), 12x16. */
	static void drawArrow(GuiGraphicsExtractor g, int x, int y, boolean right, boolean pressed) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.5f, 0.5f);
		if (right) g.blitSprite(RenderPipelines.GUI_TEXTURED, pressed ? ARROW_RIGHT_HL : ARROW_RIGHT, 32, 32, 5, 0, 0, 0, 24, 32);
		else g.blitSprite(RenderPipelines.GUI_TEXTURED, pressed ? ARROW_LEFT_HL : ARROW_LEFT, 32, 32, 0, 0, 3, 0, 21, 32);
		g.pose().popMatrix();
	}

	/** NEU drew pv_elements' 20x20 button with uMin > uMax (flipped on both axes) for the "pressed" look. */
	static void drawFlippedButton(GuiGraphicsExtractor g, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x + 20, y + 20);
		g.pose().scale(-1, -1);
		Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, 0, 0, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
		g.pose().popMatrix();
	}

	/** The 20x20 pv_elements slot, the bottom {@code completedness} part tinted orange (NEU's progress fill). */
	static void drawProgressSlot(GuiGraphicsExtractor g, int x, int y, float completedness) {
		int top = Math.round(20 * (1 - completedness));
		if (top > 0) g.blit(RenderPipelines.GUI_TEXTURED, ProfileViewerScreen.PV_ELEMENTS, x, y, 0, 0, 20, top, 20, top, 256, 256);
		if (top < 20) g.blit(RenderPipelines.GUI_TEXTURED, ProfileViewerScreen.PV_ELEMENTS, x, y + top, 0, top, 20, 20 - top, 20, 20 - top, 256, 256, TINT);
	}

	static boolean leftMouseDown() {
		return Minecraft.getInstance().mouseHandler.isLeftPressed();
	}

	static void playPressSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	private static String roman(int tier) {
		return tier > ROMANS.length || tier <= 0 ? String.valueOf(tier) : ROMANS[tier - 1];
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.full(g, PV_COLS, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;

		SkyblockProfiles.Fetch fetch = screen.getProfile().fetchUrl(CollectionsData.COLLECTIONS_URL);
		if (fetch.state() != SkyblockProfiles.FetchState.OK || fetch.json() == null || !fetch.json().has("collections")) {
			Draw.centered(g, fetch.state() == SkyblockProfiles.FetchState.LOADING ? "§eLoading collections..." : "§cCould not load the Hypixel collections data",
				guiLeft + 134, guiTop + 101, true);
			return;
		}
		if (infoFor != selected) {
			info = CollectionsData.compute(selected.getOuterProfileJson(), screen.getProfile().getUuidString(), fetch.json());
			infoFor = selected;
		}
		if (info == null) {
			Draw.centered(g, "§cCollection API not enabled!", guiLeft + 134, guiTop + 101, true);
			return;
		}

		var cats = CollectionsData.catToCollection();
		int collectionCatYSize = (int) (162f / (cats.size() - 1 + 0.0000001f));
		int yIndex = 0;
		for (ItemStack stack : cats.keySet()) {
			if (selectedCategory == null || !cats.containsKey(selectedCategory)) selectedCategory = stack;
			int by = guiTop + 10 + collectionCatYSize * yIndex;
			if (stack == selectedCategory) {
				drawFlippedButton(g, guiLeft + 7, by);
				g.item(stack, guiLeft + 10, by + 3);
				g.itemDecorations(Draw.font(), stack, guiLeft + 10, by + 3, String.valueOf(yIndex + 1));
			} else {
				Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, guiLeft + 7, by, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
				g.item(stack, guiLeft + 9, by + 2);
				g.itemDecorations(Draw.font(), stack, guiLeft + 9, by + 2, String.valueOf(yIndex + 1));
			}
			if (mouseX > guiLeft + 7 && mouseX < guiLeft + 27 && mouseY > by && mouseY < by + 20) {
				screen.tooltipToDisplay = List.of(stack.getHoverName());
			}
			yIndex++;
		}

		List<String> collections = cats.get(selectedCategory);
		List<String> minions = CollectionsData.catToMinion().get(selectedCategory);
		// NEU used size / 20, which adds an empty page when a category has exactly 20 entries
		maxPage = Math.max(collections != null ? (collections.size() - 1) / 20 : 0, minions != null ? (minions.size() - 1) / 20 : 0);

		if (maxPage != 0) {
			boolean leftHovered = false, rightHovered = false;
			if (leftMouseDown() && mouseY > guiTop + 6 && mouseY < guiTop + 22) {
				if (mouseX > guiLeft + 100 - 20 - 12 && mouseX < guiLeft + 100 - 20) leftHovered = true;
				else if (mouseX > guiLeft + 100 + 20 + 250 && mouseX < guiLeft + 100 + 20 + 12 + 250) rightHovered = true;
			}
			if (page > 0) drawArrow(g, guiLeft + 100 - 15 - 12, guiTop + 6, false, leftHovered);
			if (page < maxPage) drawArrow(g, guiLeft + 100 + 15 + 250, guiTop + 6, true, rightHovered);
		}

		String catName = selectedCategory.getHoverName().getString();
		Draw.centered(g, catName + " Collections", guiLeft + 134, guiTop + 14, true);

		if (collections != null) {
			for (int i = page * 20, j = 0; i < Math.min((page + 1) * 20, collections.size()); i++, j++) {
				String collection = collections.get(i);
				if (collection == null) continue;
				CollectionsData.CollectionInfo col = info.collections().get(collection);
				ItemStack item = CollectionsData.collectionDisplay().get(collection);
				if (col == null || item == null) continue;
				float x = 39 + COLLS_XPADDING + (COLLS_XPADDING + 20) * (j % COLLS_XCOUNT);
				float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * (j / COLLS_XCOUNT);

				int tier = col.unlockedTiers();
				String tierString = tier > 20 || tier == 0 ? String.valueOf(tier) : ROMANS[tier - 1];
				BigInteger amount = col.total();
				BigInteger max = BigInteger.valueOf(col.maxAmount());
				float completedness = 0;
				if (max.signum() > 0) completedness = amount.compareTo(max) > 0 ? 1 : amount.floatValue() / max.floatValue();
				int tierColour = completedness >= 1 ? GOLD : GREY;

				int ix = guiLeft + (int) x, iy = guiTop + (int) y;
				drawProgressSlot(g, ix, iy, completedness);
				g.item(item, ix + 2, iy + 2);
				if (mouseX > ix + 2 && mouseX < ix + 18 && mouseY > iy + 2 && mouseY < iy + 18) {
					List<Component> tt = new ArrayList<>();
					tt.add(Component.literal("§7" + item.getHoverName().getString() + " " + (completedness >= 1 ? "§6" : "§7") + tierString));
					tt.add(Component.literal("§7Collected: " + Fmt.number(col.personal().doubleValue())));
					tt.add(Component.literal("§7Total Collected: " + Fmt.number(amount.doubleValue())));
					screen.tooltipToDisplay = tt;
				}
				centeredColour(g, tierString, guiLeft + x + 10, guiTop + y - 4, tierColour);
				centeredColour(g, Fmt.shortNumber(amount.doubleValue()), guiLeft + x + 10, guiTop + y + 26, GREY);
			}
		}

		Draw.centered(g, catName + " Minions", guiLeft + 326, guiTop + 14, true);

		if (minions != null) {
			JsonObject misc = NeuRepo.misc();
			for (int i = page * 20, j = 0; i < Math.min((page + 1) * 20, minions.size()); i++, j++) {
				String minion = minions.get(i);
				if (minion == null) continue;
				float maxTier = (float) J.dbl(misc, "minions." + minion + "_GENERATOR", 11);
				int tier = info.craftedGenerators().getOrDefault(minion, 0);
				PvItem minionItem = LegacyItems.fromRepo(minion + "_GENERATOR_" + Math.max(1, tier));
				if (minionItem == null) continue;
				float x = 231 + COLLS_XPADDING + (COLLS_XPADDING + 20) * (j % COLLS_XCOUNT);
				float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * (j / COLLS_XCOUNT);
				String tierString = roman(tier);
				float completedness = Math.min(1, tier / maxTier);
				int tierColour = completedness >= 1 ? GOLD : GREY;

				int ix = guiLeft + (int) x, iy = guiTop + (int) y;
				drawProgressSlot(g, ix, iy, completedness);
				g.item(minionItem.stack(), ix + 2, iy + 2);
				if (mouseX > ix + 2 && mouseX < ix + 18 && mouseY > iy + 2 && mouseY < iy + 18) {
					List<Component> tt = new ArrayList<>();
					for (Component c : PvWidgets.itemTooltip(minionItem)) tt.add(Component.literal("§7" + c.getString()));
					screen.tooltipToDisplay = tt;
				}
				centeredColour(g, tierString, guiLeft + x + 10, guiTop + y - 4, tierColour);
			}
		}
	}

	private static void centeredColour(GuiGraphicsExtractor g, String s, float x, float y, int colour) {
		int w = Draw.font().width(s);
		g.text(Draw.font(), s, Math.round(x - w / 2f), Math.round(y - 4), colour, true);
	}

	@Override
	public boolean keyPressed(int keyCode) {
		int index = -1;
		if (keyCode >= 49 && keyCode <= 54) index = keyCode - 49; // GLFW_KEY_1..6
		else if (keyCode >= 321 && keyCode <= 326) index = keyCode - 321; // GLFW_KEY_KP_1..6
		if (index < 0) return false;
		List<ItemStack> keys = new ArrayList<>(CollectionsData.catToCollection().keySet());
		if (index >= keys.size()) return false;
		selectedCategory = keys.get(index);
		page = 0;
		playPressSound();
		return true;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (maxPage != 0 && mouseY > guiTop + 6 && mouseY < guiTop + 22) {
			if (mouseX > guiLeft + 100 - 15 - 12 && mouseX < guiLeft + 100 - 20) {
				if (page > 0) {
					page--;
					return;
				}
			} else if (mouseX > guiLeft + 100 + 15 + 250 && mouseX < guiLeft + 100 + 20 + 12 + 250) {
				if (page < maxPage) {
					page++;
					return;
				}
			}
		}
		var cats = CollectionsData.catToCollection();
		int collectionCatYSize = (int) (162f / (cats.size() - 1 + 0.0000001f));
		int yIndex = 0;
		for (ItemStack stack : cats.keySet()) {
			if (mouseX > guiLeft + 7 && mouseX < guiLeft + 7 + 20
				&& mouseY > guiTop + 10 + collectionCatYSize * yIndex && mouseY < guiTop + 10 + collectionCatYSize * yIndex + 20) {
				selectedCategory = stack;
				page = 0;
				playPressSound();
				return;
			}
			yIndex++;
		}
	}

	@Override
	public void resetCache() {
		info = null;
		infoFor = null;
		page = 0;
	}

	/** For tests: select a category by index (0 = Farming ... 5 = Rift). */
	public static void selectCategory(int index) {
		selectedCategory = new ArrayList<>(CollectionsData.catToCollection().keySet()).get(index);
	}
}
