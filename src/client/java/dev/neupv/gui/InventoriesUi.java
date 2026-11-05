/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Small drawing helpers used by the Storage tab: NEU Utils.renderShadowedString, RenderUtils.drawFloatingRectWithAlpha,
 * KotlinRenderUtils.renderItemStackButton, ArrowPagesUtils and the resource_packs.png arrows,
 * Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntConsumer;

final class InventoriesUi {
	/*
	 * 1.8.9's textures/gui/resource_packs.png was a 256x256 sheet of 32x32 icons ("select" = right arrow at u 0,
	 * "unselect" = left arrow at u 32, highlighted versions one row lower). 26.3 split it into these sprites.
	 */
	private static final Identifier SELECT = Identifier.withDefaultNamespace("transferable_list/select");
	private static final Identifier SELECT_HL = Identifier.withDefaultNamespace("transferable_list/select_highlighted");
	private static final Identifier UNSELECT = Identifier.withDefaultNamespace("transferable_list/unselect");
	private static final Identifier UNSELECT_HL = Identifier.withDefaultNamespace("transferable_list/unselect_highlighted");

	static final int PAGE_BUTTON_RIGHT_OFFSET_X = 37;
	static final int PAGE_STRING_OFFSET_X = 22;
	static final int PAGE_STRING_OFFSET_Y = 6;
	static final int PAGE_BUTTON_WIDTH = 7;
	static final int PAGE_BUTTON_HEIGHT = 11;

	private InventoriesUi() {}

	static void playPressSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	/** pv_elements 20x20 slot frame (NEU: drawTexturedRect(x, y, 20, 20, 0, 20/256f, 0, 20/256f)). */
	static void frame(GuiGraphicsExtractor g, int x, int y) {
		Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, x, y, 20, 20, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
	}

	/** Same frame flipped on both axes (NEU passes uMin = 20/256, uMax = 0 for the "pressed" look). */
	static void pressedFrame(GuiGraphicsExtractor g, int x, int y) {
		g.blit(RenderPipelines.GUI_TEXTURED, ProfileViewerScreen.PV_ELEMENTS, x, y, 20f, 20f, 20, 20, -20, -20, 256, 256);
	}

	/** KotlinRenderUtils.renderItemStackButton. */
	static void itemButton(GuiGraphicsExtractor g, int x, int y, ItemStack stack) {
		frame(g, x, y);
		if (stack != null) g.item(stack, x + 2, y + 2);
	}

	/** RenderUtils.drawFloatingRectWithAlpha. */
	static void floatingRect(GuiGraphicsExtractor g, int x, int y, int width, int height, int alpha, boolean shadow) {
		int main = (alpha << 24) | 0xc0c0c0;
		int light = (alpha << 24) | 0xf0f0f0;
		int dark = (alpha << 24) | 0x909090;
		g.fill(x, y, x + 1, y + height, light);
		g.fill(x + 1, y, x + width, y + 1, light);
		g.fill(x + width - 1, y + 1, x + width, y + height, dark);
		g.fill(x + 1, y + height - 1, x + width - 1, y + height, dark);
		g.fill(x + 1, y + 1, x + width - 1, y + height - 1, main);
		if (shadow) {
			g.fill(x + width, y + 2, x + width + 2, y + height + 2, (alpha * 3 / 5) << 24);
			g.fill(x + 2, y + height, x + width, y + height + 2, (alpha * 3 / 5) << 24);
		}
	}

	/** Utils.renderShadowedString: centred on x, top at y, soft black outline, shrunk to maxLength. */
	static void shadowedString(GuiGraphicsExtractor g, String str, float x, float y, int maxLength) {
		int strLen = Draw.font().width(str);
		float factor = maxLength < 0 ? 1 : Math.min(1, maxLength / (float) strLen);
		String clean = str.replaceAll("(?i)§[0-9a-f]", "");
		for (int xOff = -2; xOff <= 2; xOff++) {
			for (int yOff = -2; yOff <= 2; yOff++) {
				if (Math.abs(xOff) == Math.abs(yOff)) continue;
				int alpha = 200 / Math.max(Math.abs(xOff), Math.abs(yOff));
				Draw.centeredMaxWidth(g, clean, x + xOff / 2f * factor, y + 4 + yOff / 2f * factor, false,
					maxLength < 0 ? Integer.MAX_VALUE : maxLength, alpha << 24);
			}
		}
		Draw.centeredMaxWidth(g, str, x, y + 4, false, maxLength < 0 ? Integer.MAX_VALUE : maxLength, 0xFF066CD3);
	}

	/**
	 * Part of a resource_packs.png arrow, drawn at half size like NEU (it maps a 24x32 / 14x22 texel area onto
	 * 12x16 / 7x11 pixels). uFrom/vFrom are relative to the 32x32 icon.
	 */
	static void halfArrow(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int uFrom, int vFrom, int texelsW, int texelsH) {
		int u = Math.max(0, uFrom), shift = u - uFrom;
		g.pose().pushMatrix();
		g.pose().translate(x + shift / 2f, y);
		g.pose().scale(0.5f, 0.5f);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 32, 32, u, vFrom, 0, 0, texelsW - shift, texelsH);
		g.pose().popMatrix();
	}

	/** Big left arrow of the inventory page selector (resource_packs.png u 29..53). */
	static void bigLeftArrow(GuiGraphicsExtractor g, int x, int y, boolean hovered) {
		halfArrow(g, hovered ? UNSELECT_HL : UNSELECT, x, y, 29 - 32, 0, 24, 32);
	}

	/** Big right arrow of the inventory page selector (resource_packs.png u 5..29). */
	static void bigRightArrow(GuiGraphicsExtractor g, int x, int y, boolean hovered) {
		halfArrow(g, hovered ? SELECT_HL : SELECT, x, y, 5, 0, 24, 32);
	}

	/** ArrowPagesUtils.onDraw. */
	static void arrowPages(GuiGraphicsExtractor g, int guiLeft, int guiTop, int leftX, int buttonY, int currentPage, int totalPages, int mouseX, int mouseY) {
		if (totalPages < 2) return;
		int rightX = leftX + PAGE_BUTTON_RIGHT_OFFSET_X;
		boolean leftSel = within(mouseX - guiLeft, mouseY - guiTop, leftX, buttonY);
		boolean rightSel = within(mouseX - guiLeft, mouseY - guiTop, rightX, buttonY);
		if (currentPage != 0) halfArrow(g, leftSel ? UNSELECT_HL : UNSELECT, guiLeft + leftX, guiTop + buttonY, 34 - 32, 5, 14, 22);
		if (currentPage != totalPages - 1) halfArrow(g, rightSel ? SELECT_HL : SELECT, guiLeft + rightX, guiTop + buttonY, 10, 5, 14, 22);
		Draw.centeredMaxWidth(g, (currentPage + 1) + "/" + totalPages, guiLeft + leftX + PAGE_STRING_OFFSET_X,
			guiTop + buttonY + PAGE_STRING_OFFSET_Y, false, 28, 0xFF000000);
	}

	/** ArrowPagesUtils.onPageSwitchMouse. */
	static boolean arrowPagesClick(int guiLeft, int guiTop, int leftX, int buttonY, int currentPage, int totalPages, double mouseX, double mouseY, IntConsumer pageChange) {
		int rightX = leftX + PAGE_BUTTON_RIGHT_OFFSET_X;
		if (within(mouseX - guiLeft, mouseY - guiTop, leftX, buttonY) && currentPage > 0) {
			pageChange.accept(Math.clamp(currentPage - 1, 0, totalPages - 1));
			playPressSound();
			return true;
		}
		if (within(mouseX - guiLeft, mouseY - guiTop, rightX, buttonY) && currentPage < totalPages - 1) {
			pageChange.accept(Math.clamp(currentPage + 1, 0, totalPages - 1));
			playPressSound();
			return true;
		}
		return false;
	}

	private static boolean within(double x, double y, int bx, int by) {
		return x >= bx && x <= bx + PAGE_BUTTON_WIDTH && y >= by && y <= by + PAGE_BUTTON_HEIGHT;
	}
}
