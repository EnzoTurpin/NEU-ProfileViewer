/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates PetsPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PetsPage extends PvPage {
	private static final Identifier PV_PETS = Draw.tex("gui/pv_pets.png");
	private static final int COLLS_XCOUNT = 5;
	private static final int COLLS_YCOUNT = 4;
	private static final float COLLS_XPADDING = (190 - COLLS_XCOUNT * 20) / (float) (COLLS_XCOUNT + 1);
	private static final float COLLS_YPADDING = (202 - COLLS_YCOUNT * 20) / (float) (COLLS_YCOUNT + 1);
	private static final int SORT_X = 180, SORT_Y = 8, SORT_SIZE = 16;
	private static final ItemStack HOPPER = new ItemStack(Items.HOPPER);

	private boolean sortPetsAlphabetically = false;
	private List<PetsData.PetDisplay> sortedPets = null;
	private int selectedPet = -1;
	private int petsPage = 0;

	public PetsPage(ProfileViewerScreen screen) {
		super(screen);
	}

	private static boolean isSkyblockNight() {
		long elapsed = System.currentTimeMillis() - 1560275700000L;
		long hour = (elapsed % 1_200_000L) / 50_000L;
		return hour <= 6 || hour >= 20;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (NeuRepo.constant("pets") == null) {
			Draw.full(g, PV_PETS, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
			Draw.centered(g, "§cMissing repo data", guiLeft + 100, guiTop + 101, true);
			return;
		}
		if (selected == null) return;
		List<JsonObject> pets = selected.getPets();

		String location = null;
		JsonObject status = screen.getProfile().getPlayerStatus();
		if (status != null && status.has("mode")) location = status.get("mode").getAsString();

		screen.backgroundRotation += (screen.currentTime - screen.lastTime) / 400f;
		screen.backgroundRotation %= 360;

		if (sortedPets == null) sortPets(pets);

		renderSortingButton(g, guiLeft, guiTop, mouseX, mouseY);

		Panorama.draw(g, -screen.backgroundRotation, guiLeft + 212, guiTop + 44, 81, 108,
			location == null ? "dynamic" : location, isSkyblockNight() ? "night" : "day");

		Draw.full(g, PV_PETS, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
		Draw.centered(g, "§5Pets", guiLeft + 100, guiTop + 14, true);

		if (pets.isEmpty()) {
			Draw.centered(g, "§cThis profile has no pets", guiLeft + 100, guiTop + 101, true);
			return;
		}

		if (selectedPet == -1) {
			for (int i = 0; i < sortedPets.size(); i++) {
				if (J.bool(sortedPets.get(i).json(), "active", false)) {
					selectedPet = i;
					break;
				}
			}
		}

		boolean leftHovered = false, rightHovered = false;
		if (CollectionsPage.leftMouseDown() && mouseY > guiTop + 6 && mouseY < guiTop + 22) {
			if (mouseX > guiLeft + 100 - 20 - 12 && mouseX < guiLeft + 100 - 20) leftHovered = true;
			else if (mouseX > guiLeft + 100 + 20 && mouseX < guiLeft + 100 + 20 + 12) rightHovered = true;
		}
		if (petsPage > 0) CollectionsPage.drawArrow(g, guiLeft + 100 - 15 - 12, guiTop + 6, false, leftHovered);
		if (petsPage < Math.ceil(sortedPets.size() / 20f) - 1) CollectionsPage.drawArrow(g, guiLeft + 100 + 15, guiTop + 6, true, rightHovered);

		for (int i = petsPage * 20; i < Math.min(petsPage * 20 + 20, sortedPets.size()); i++) {
			PetsData.PetDisplay pet = sortedPets.get(i);
			int xIndex = (i % 20) % COLLS_XCOUNT;
			int yIndex = (i % 20) / COLLS_XCOUNT;
			float x = 5 + COLLS_XPADDING + (COLLS_XPADDING + 20) * xIndex;
			float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * yIndex;
			int ix = guiLeft + (int) x, iy = guiTop + (int) y;
			CollectionsPage.drawProgressSlot(g, ix, iy, i == selectedPet ? 1 : 0);
			g.item(pet.stack(), ix + 2, iy + 2);
			if (mouseX > guiLeft + x && mouseX < guiLeft + x + 20 && mouseY > guiTop + y && mouseY < guiTop + y + 20) {
				screen.tooltipToDisplay = pet.tooltip();
			}
		}

		if (selectedPet >= 0 && selectedPet < sortedPets.size()) drawSelectedPet(g, sortedPets.get(selectedPet), guiLeft, guiTop);
	}

	private void drawSelectedPet(GuiGraphicsExtractor g, PetsData.PetDisplay pet, int guiLeft, int guiTop) {
		String display = pet.name();
		var font = Draw.font();
		int x = guiLeft + 280;
		float y = guiTop + 67 + 15 * (float) Math.sin(((screen.currentTime - screen.startTime) / 800f) % (2 * Math.PI));
		int halfDisplayLen = font.width(display) / 2;

		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.fill(-halfDisplayLen - 1 - 28, -1, halfDisplayLen + 1 - 28, 8, 0x64000000);
		g.text(font, display, -halfDisplayLen - 28, 0, 0xFFFFFFFF, true);
		g.pose().translate(-55, 0);
		g.pose().scale(3.5f, 3.5f);
		g.item(pet.stack(), 0, 0);
		g.pose().popMatrix();

		PetsData.PetLevel lvl = pet.level();
		double level = lvl.currentLevel();
		double currentLevelRequirement = lvl.expRequiredForNextLevel();
		// at max level NEU showed the overflow xp as a progress > 100 %
		double percentageToNextLevel = lvl.maxed() ? 1 : Math.min(1, lvl.percentageToNextLevel());
		double exp = lvl.expTotal();
		double maxXP = lvl.expRequiredForMaxLevel();

		String[] split = display.split("] ");
		String colouredName = split[split.length - 1];

		Draw.aligned(g, colouredName, "§fLevel " + (int) Math.floor(level), guiLeft + 319, guiTop + 28, 98);
		// NEU divided by 100 even for pets that go to level 200 (Golden Dragon...)
		Draw.bar(g, guiLeft + 319, guiTop + 38, 98, (float) (Math.floor(level) / lvl.maxLevel()));

		Draw.aligned(g, "§eTo Next LVL", "§f" + (int) (percentageToNextLevel * 100) + "%", guiLeft + 319, guiTop + 46, 98);
		Draw.bar(g, guiLeft + 319, guiTop + 56, 98, (float) percentageToNextLevel);

		double toMax = maxXP <= 0 ? 1 : Math.min(1, exp / maxXP);
		Draw.aligned(g, "§eTo Max LVL", "§f" + Math.min(100, (int) (exp / maxXP * 100)) + "%", guiLeft + 319, guiTop + 64, 98);
		Draw.bar(g, guiLeft + 319, guiTop + 74, 98, (float) toMax);

		Draw.aligned(g, "§eTotal XP", "§f" + Fmt.shortNumber(exp), guiLeft + 319, guiTop + 125, 98);
		Draw.aligned(g, "§eCurrent LVL XP", "§f" + Fmt.shortNumber(lvl.maxed() ? currentLevelRequirement : percentageToNextLevel * currentLevelRequirement),
			guiLeft + 319, guiTop + 143, 98);
		Draw.aligned(g, "§eRequired LVL XP", "§f" + Fmt.shortNumber(currentLevelRequirement), guiLeft + 319, guiTop + 161, 98);
	}

	private void renderSortingButton(GuiGraphicsExtractor g, int guiLeft, int guiTop, int mouseX, int mouseY) {
		CollectionsPage.drawFlippedButton(g, SORT_X + guiLeft - 2, SORT_Y + guiTop - 2);
		g.item(HOPPER, SORT_X + guiLeft, SORT_Y + guiTop);
		int mx = mouseX - guiLeft, my = mouseY - guiTop;
		if (mx >= SORT_X && mx < SORT_X + SORT_SIZE && my >= SORT_Y && my < SORT_Y + SORT_SIZE) {
			screen.tooltipToDisplay = PvWidgets.lines(
				"§aSort",
				"",
				(sortPetsAlphabetically ? "§7 " : "§3▶") + " Pet Exp",
				(!sortPetsAlphabetically ? "§7 " : "§3▶") + " Alphabetical",
				"",
				"§eClick to switch!");
		}
	}

	private void sortPets(List<JsonObject> pets) {
		List<JsonObject> sorted = new ArrayList<>(pets);
		Comparator<JsonObject> byTier = Comparator.comparingInt(p -> {
			int r = PetsData.effectiveRarity(p);
			return r < 0 ? Integer.MAX_VALUE : -r; // unknown rarities last
		});
		Comparator<JsonObject> cmp = byTier;
		if (sortPetsAlphabetically) cmp = cmp.thenComparing(p -> J.str(p, "type", ""));
		cmp = cmp.thenComparing(Comparator.comparingDouble((JsonObject p) -> J.dbl(p, "exp", 0)).reversed());
		sorted.sort(cmp);
		sortedPets = new ArrayList<>();
		for (JsonObject p : sorted) sortedPets.add(PetsData.create(p));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (sortedPets == null) return false;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		for (int i = petsPage * 20; i < Math.min(petsPage * 20 + 20, sortedPets.size()); i++) {
			int xIndex = (i % 20) % COLLS_XCOUNT;
			int yIndex = (i % 20) / COLLS_XCOUNT;
			float x = 5 + COLLS_XPADDING + (COLLS_XPADDING + 20) * xIndex;
			float y = 7 + COLLS_YPADDING + (COLLS_YPADDING + 20) * yIndex;
			if (mouseX > guiLeft + x && mouseX < guiLeft + x + 20 && mouseY > guiTop + y && mouseY < guiTop + y + 20) {
				selectedPet = i;
				return true;
			}
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (mouseY > guiTop + 6 && mouseY < guiTop + 22) {
			if (mouseX > guiLeft + 100 - 15 - 12 && mouseX < guiLeft + 100 - 20) {
				if (petsPage > 0) petsPage--;
			} else if (mouseX > guiLeft + 100 + 15 && mouseX < guiLeft + 100 + 20 + 12) {
				if (sortedPets != null && petsPage < Math.ceil(sortedPets.size() / 20f) - 1) petsPage++;
			}
		}
		double mx = mouseX - guiLeft, my = mouseY - guiTop;
		if (mx >= SORT_X && mx < SORT_X + SORT_SIZE && my >= SORT_Y && my < SORT_Y + SORT_SIZE) {
			sortPetsAlphabetically = !sortPetsAlphabetically;
			resetCache();
			CollectionsPage.playPressSound();
		}
	}

	@Override
	public void resetCache() {
		petsPage = 0;
		sortedPets = null;
		selectedPet = -1;
	}

	/** For tests. */
	public void setPetsPage(int page) {
		petsPage = page;
	}
}
