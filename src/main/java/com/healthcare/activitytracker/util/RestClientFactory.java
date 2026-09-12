package com.healthcare.activitytracker.util;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds a {@link RestClient} with explicit connect/read timeouts — the one piece of HTTP-client
 * setup every outbound integration in this app needs identically (currently {@code OllamaClient}
 * and {@code DeepSeekClient}; a future one should use this too rather than re-deriving it).
 */
public final class RestClientFactory {

  private RestClientFactory() {}

  public static RestClient withTimeouts(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
