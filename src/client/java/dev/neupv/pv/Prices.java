/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Replaces NEU's AuctionManager price lookups used by the PV networth.
 */
package dev.neupv.pv;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.net.Http;
import dev.neupv.util.J;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bazaar prices (public Hypixel endpoint) + optional lowest BIN map from a configurable URL. */
public final class Prices {
	private static final Map<String, Double> BAZAAR_SELL = new ConcurrentHashMap<>();
	private static final Map<String, Double> BAZAAR_BUY = new ConcurrentHashMap<>();
	private static final Map<String, Double> LOWEST_BIN = new ConcurrentHashMap<>();
	private static long lastFetch = 0;

	private Prices() {}

	public static void refreshIfNeeded() {
		long now = System.currentTimeMillis();
		if (now - lastFetch < 10 * 60 * 1000) return;
		lastFetch = now;
		Http.getJson("https://api.hypixel.net/v2/skyblock/bazaar").thenAccept(json -> {
			JsonObject products = J.obj(json, "products");
			if (products == null) return;
			for (Map.Entry<String, JsonElement> e : products.entrySet()) {
				BAZAAR_SELL.put(e.getKey(), J.dbl(e.getValue(), "quick_status.sellPrice", 0));
				BAZAAR_BUY.put(e.getKey(), J.dbl(e.getValue(), "quick_status.buyPrice", 0));
			}
		});
		String lbin = PvConfig.get().lowestBinUrl;
		if (lbin != null && !lbin.isBlank()) {
			Http.getJson(lbin.trim()).thenAccept(json -> {
				for (Map.Entry<String, JsonElement> e : json.entrySet()) {
					try {
						LOWEST_BIN.put(e.getKey(), e.getValue().getAsDouble());
					} catch (Exception ignored) {
					}
				}
			});
		}
	}

	/** Bazaar sell price, else lowest BIN, else 0. */
	public static double price(String internalName) {
		if (internalName == null) return 0;
		Double bz = BAZAAR_SELL.get(internalName);
		if (bz != null && bz > 0) return bz;
		return LOWEST_BIN.getOrDefault(internalName, 0d);
	}

	public static double bazaarBuy(String internalName) {
		return BAZAAR_BUY.getOrDefault(internalName, -1d);
	}
}
