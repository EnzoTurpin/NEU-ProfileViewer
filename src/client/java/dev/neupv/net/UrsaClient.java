/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates UrsaClient.kt, Copyright (C) 2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.net;

import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import dev.neupv.NeuPv;
import dev.neupv.config.PvConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Client for an ursa-minor proxy (same protocol as NEU):
 * <ol>
 *   <li>without a token: Mojang {@code joinServer} with a random serverId, then send
 *   {@code x-ursa-username} + {@code x-ursa-serverid};</li>
 *   <li>the proxy answers with {@code x-ursa-token} (+ {@code x-ursa-expires}), reused until 60 s before expiry.</li>
 * </ol>
 * Fixes compared to NEU: a 401 really discards the token and sets REJECTED, and an empty proxy URL is reported
 * as NOT_CONFIGURED instead of an endless loading screen. All callbacks complete on the client thread.
 */
public final class UrsaClient {
	public enum AuthState { NOT_CONFIGURED, NOT_ATTEMPTED, FAILED_TO_JOINSERVER, REJECTED, SUCCEEDED }

	private record Token(Instant validUntil, String token, String obtainedFrom) {
		boolean isValid() {
			return Instant.now().plusSeconds(60).isBefore(validUntil);
		}
	}

	private record Request(String path, CompletableFuture<JsonObject> consumer) {}

	private static final ConcurrentLinkedQueue<Request> QUEUE = new ConcurrentLinkedQueue<>();
	// Only touched on the client thread.
	private static Token token;
	private static boolean pollingForToken;
	private static AuthState authState = AuthState.NOT_ATTEMPTED;

	private UrsaClient() {}

	public static AuthState getAuthState() {
		if (PvConfig.get().ursaRoot().isEmpty()) return AuthState.NOT_CONFIGURED;
		return authState;
	}

	public static CompletableFuture<JsonObject> get(String path) {
		CompletableFuture<JsonObject> c = new CompletableFuture<>();
		if (PvConfig.get().ursaRoot().isEmpty()) {
			c.completeExceptionally(new IllegalStateException("ursaUrl is not set in config/neupv.json"));
			return c;
		}
		QUEUE.add(new Request(path, c));
		return c;
	}

	/** Called every client tick, on the client thread. */
	public static void tick() {
		while (!QUEUE.isEmpty()) {
			if (pollingForToken) return;
			Request next = QUEUE.poll();
			if (next == null) return;
			String root = PvConfig.get().ursaRoot();
			Token t = token;
			if (t == null || !t.isValid() || !t.obtainedFrom.equals(root)) {
				token = null;
				t = null;
				pollingForToken = true;
			}
			perform(next, root, t);
		}
	}

	private static void perform(Request request, String root, Token t) {
		String url = root + "/" + request.path;
		Map<String, String> headers = new HashMap<>();
		CompletableFuture<Void> auth;
		if (t != null) {
			headers.put("x-ursa-token", t.token);
			auth = CompletableFuture.completedFuture(null);
		} else {
			User user = Minecraft.getInstance().getUser();
			String serverId = UUID.randomUUID().toString();
			headers.put("x-ursa-username", user.getName());
			headers.put("x-ursa-serverid", serverId);
			// joinServer is a blocking call to Mojang: run it off-thread.
			auth = CompletableFuture.runAsync(() -> {
				try {
					Minecraft.getInstance().services().sessionService()
						.joinServer(user.getProfileId(), user.getAccessToken(), serverId);
				} catch (AuthenticationException e) {
					throw new RuntimeException(e);
				}
			});
		}
		auth
			.thenCompose(v -> Http.getCached(url, headers, "ursa:" + url, Http.DEFAULT_MAX_AGE))
			.whenComplete((resp, err) -> Minecraft.getInstance().execute(() -> {
				pollingForToken = false;
				if (err != null) {
					Throwable root0 = err;
					while (root0.getCause() != null && root0 != root0.getCause()) root0 = root0.getCause();
					if (root0 instanceof AuthenticationException) authState = AuthState.FAILED_TO_JOINSERVER;
					if (Http.statusOf(err) == 401) {
						authState = AuthState.REJECTED;
						token = null;
					}
					NeuPv.LOGGER.warn("Ursa request {} failed: {}", request.path, err.toString());
					request.consumer.completeExceptionally(err);
					return;
				}
				String newToken = resp.header("x-ursa-token");
				if (newToken != null) {
					Instant until;
					try {
						until = Instant.ofEpochMilli(Long.parseLong(resp.header("x-ursa-expires")));
					} catch (Exception e) {
						until = Instant.now().plusSeconds(55 * 60);
					}
					token = new Token(until, newToken, root);
					authState = AuthState.SUCCEEDED;
				}
				try {
					request.consumer.complete(resp.json());
				} catch (Exception e) {
					request.consumer.completeExceptionally(e);
				}
			}));
	}

	public static String profiles(UUID uuid) {
		return "v1/hypixel/v2/profiles/" + uuid;
	}

	public static String player(UUID uuid) {
		return "v1/hypixel/v2/player/" + uuid;
	}

	public static String guild(UUID uuid) {
		return "v1/hypixel/v2/guild/" + uuid;
	}

	public static String bingo(UUID uuid) {
		return "v1/hypixel/v2/bingo/" + uuid;
	}

	public static String status(UUID uuid) {
		return "v1/hypixel/v2/status/" + uuid;
	}

	public static String museum(String profileId) {
		return "v1/hypixel/v2/museum/" + profileId;
	}

	public static String garden(String profileId) {
		return "v1/hypixel/v2/garden/" + profileId;
	}
}
