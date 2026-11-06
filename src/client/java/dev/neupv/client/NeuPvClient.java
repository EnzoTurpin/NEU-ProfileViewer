/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates ProfileViewerCommands.kt, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.net.Mojang;
import dev.neupv.net.UrsaClient;
import dev.neupv.pv.Prices;
import dev.neupv.pv.ProfileViewer;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NeuPvClient implements ClientModInitializer {
	private static Screen pendingScreen;

	@Override
	public void onInitializeClient() {
		PvConfig.load();
		NeuRepo.loadAsync();
		dev.neupv.item.BagIcons.register();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			UrsaClient.tick();
			if (mc.player != null) Mojang.remember(mc.player.getGameProfile().name(), mc.player.getUUID());
			if (pendingScreen != null) {
				mc.setScreenAndShow(pendingScreen);
				pendingScreen = null;
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(pv("pv", null));
			dispatcher.register(pv("neuprofile", null));
			dispatcher.register(pv("cata", ProfileViewerScreen.Page.DUNGEON));
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> pv(String name, ProfileViewerScreen.Page page) {
		return ClientCommands.literal(name)
			.executes(ctx -> openPv(ctx.getSource(), null, page))
			.then(ClientCommands.argument("player", StringArgumentType.word())
				.suggests((ctx, builder) -> {
					var mc = Minecraft.getInstance();
					if (mc.getConnection() != null) mc.getConnection().getOnlinePlayers().forEach(p -> builder.suggest(p.getProfile().name()));
					return builder.buildFuture();
				})
				.executes(ctx -> openPv(ctx.getSource(), StringArgumentType.getString(ctx, "player"), page)));
	}

	private static int openPv(FabricClientCommandSource source, String name, ProfileViewerScreen.Page page) {
		if (!NeuRepo.isLoaded()) {
			source.sendError(Component.literal("NEU repo is still loading, try again in a few seconds."));
			return 0;
		}
		if (PvConfig.get().ursaRoot().isEmpty()) {
			source.sendError(Component.literal("No data proxy configured: set \"ursaUrl\" in config/neupv.json."));
		}
		Prices.refreshIfNeeded();
		Minecraft mc = Minecraft.getInstance();
		String target = name != null ? name : mc.player.getGameProfile().name();
		ProfileViewer.loadPlayerByName(target, result -> {
			switch (result) {
				case ProfileViewer.LoadResult.Found f -> {
					ProfileViewer.saveSearch(name);
					if (page != null) ProfileViewerScreen.currentPage = page;
					pendingScreen = new ProfileViewerScreen(f.profiles());
				}
				case ProfileViewer.LoadResult.NotFound nf -> source.sendError(Component.literal("Unknown player: " + nf.name()));
				case ProfileViewer.LoadResult.Failed fl -> source.sendError(Component.literal("Could not look up the player: " + fl.message()));
			}
		});
		return 1;
	}
}
