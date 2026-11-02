/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates (ApiUtil / ApiCache), Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.NeuPv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * HTTP layer. Same caching policy as NEU's ApiCache (GET responses kept 500 s, concurrent identical requests
 * merged), but unlike NEU errors are NOT swallowed: a failed request completes exceptionally with an
 * {@link HttpException} carrying the status code, so callers can tell "not found" from "server down".
 */
public final class Http {
	public static final Duration DEFAULT_MAX_AGE = Duration.ofSeconds(500);
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final String USER_AGENT = "NeuPv-Port/" + NeuPv.VERSION;

	private record CacheEntry(long firedAt, CompletableFuture<Response> future) {}

	private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

	private Http() {}

	public record Response(int status, Map<String, List<String>> headers, byte[] body) {
		public String text() {
			return new String(body, StandardCharsets.UTF_8);
		}

		public JsonObject json() {
			JsonElement e = JsonParser.parseString(text());
			if (!e.isJsonObject()) throw new IllegalStateException("Expected a JSON object");
			return e.getAsJsonObject();
		}

		public String header(String name) {
			for (Map.Entry<String, List<String>> h : headers.entrySet()) {
				if (h.getKey() != null && h.getKey().equalsIgnoreCase(name) && !h.getValue().isEmpty()) return h.getValue().get(0);
			}
			return null;
		}
	}

	public static class HttpException extends RuntimeException {
		public final int status;

		public HttpException(String url, int status, String body) {
			super("HTTP " + status + " for " + url + (body == null ? "" : ": " + body.substring(0, Math.min(200, body.length()))));
			this.status = status;
		}
	}

	/** Raw request, never cached. Non-2xx statuses complete exceptionally with HttpException. */
	public static CompletableFuture<Response> send(String method, String url, Map<String, String> headers, String jsonBody) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", USER_AGENT)
			.header("Accept-Encoding", "gzip");
		if (headers != null) headers.forEach(b::header);
		if (jsonBody != null) {
			b.header("Content-Type", "application/json");
			b.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
		} else {
			b.method(method, HttpRequest.BodyPublishers.noBody());
		}
		return CLIENT.sendAsync(b.build(), HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
			byte[] body = r.body();
			if ("gzip".equalsIgnoreCase(r.headers().firstValue("Content-Encoding").orElse(""))) {
				body = gunzip(body);
			}
			if (r.statusCode() >= 400) {
				throw new HttpException(url, r.statusCode(), new String(body, StandardCharsets.UTF_8));
			}
			return new Response(r.statusCode(), r.headers().map(), body);
		});
	}

	/** Cached GET. A failed request is evicted immediately so the next call retries. */
	public static CompletableFuture<Response> getCached(String url, Map<String, String> headers, String cacheKey, Duration maxAge) {
		long now = System.currentTimeMillis();
		CacheEntry e = CACHE.get(cacheKey);
		if (e != null && now - e.firedAt < maxAge.toMillis() && !e.future.isCompletedExceptionally()) {
			return e.future;
		}
		CompletableFuture<Response> f = send("GET", url, headers, null);
		CACHE.put(cacheKey, new CacheEntry(now, f));
		f.whenComplete((r, t) -> {
			if (t != null) CACHE.remove(cacheKey);
		});
		return f;
	}

	public static CompletableFuture<JsonObject> getJson(String url) {
		return getCached(url, null, url, DEFAULT_MAX_AGE).thenApply(Response::json);
	}

	public static void clearCache() {
		CACHE.clear();
	}

	public static int statusOf(Throwable t) {
		while (t != null) {
			if (t instanceof HttpException he) return he.status;
			t = t.getCause();
		}
		return -1;
	}

	private static byte[] gunzip(byte[] data) {
		try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(data));
				 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			in.transferTo(out);
			return out.toByteArray();
		} catch (IOException e) {
			return data;
		}
	}
}
