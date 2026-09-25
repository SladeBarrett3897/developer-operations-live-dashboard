package devdashboard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class InfraiOperationsClient implements DeveloperSignalService.SignalHandoff {
    static final String CANONICAL_CALL = "infrai.metrics.batch";

    static final class InfraiException extends Exception {
        final int status;
        final Map<String, Object> detail;
        InfraiException(int status, Map<String, Object> detail) {
            super(String.valueOf(detail.getOrDefault("message", "Request rejected")));
            this.status = status;
            this.detail = detail;
        }
    }

    private final DashboardConfig config;
    private final HttpClient http;

    InfraiOperationsClient(DashboardConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    void createChannel() throws Exception {
        request("POST", "/v1/realtime/channel/create", Map.of(
                "channel", config.channel(), "type", "public",
                "idempotency_key", "channel:" + config.channel()));
    }

    Map<String, Object> issueDashboardToken(String clientId) throws Exception {
        return request("POST", "/v1/realtime/token/issue", Map.of(
                "client_id", clientId,
                "channels", List.of(config.channel()),
                "capabilities", List.of("subscribe"),
                "ttl_seconds", 900,
                "idempotency_key", "dashboard-token:" + clientId));
    }

    @Override
    public void deliver(DeveloperSignalService.BuildSignal signal,
                        DeveloperSignalService.Decision decision) throws Exception {
        String key = "developer-signal:" + signal.eventId();
        request("POST", "/v1/metrics/batch", Map.of(
                "points", decision.points(), "idempotency_key", key + ":metrics"));
        request("POST", "/v1/realtime/publish", Map.of(
                "channel", config.channel(),
                "event", "developer.operation",
                "data", decision.dashboardEvent(),
                "account_id", signal.service(),
                "idempotency_key", key + ":realtime"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, Map<String, Object> body) throws Exception {
        String json = MiniJson.write(body);
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", String.valueOf(body.get("idempotency_key")))
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Object parsed = MiniJson.parse(response.body());
            if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalStateException("Expected response envelope");
            Map<String, Object> envelope = (Map<String, Object>) raw;
            boolean ok = Boolean.TRUE.equals(envelope.get("ok"));
            if (!ok) {
                Map<String, Object> detail = envelope.get("error") instanceof Map<?, ?> error
                        ? (Map<String, Object>) error : Map.of("message", "Request rejected");
                if (response.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                throw new InfraiException(response.statusCode(), detail);
            }
            if (response.statusCode() >= 500) throw new IllegalStateException("Upstream transport response");
            return envelope.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of();
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try { return Math.max(0L, Long.parseLong(value) * 1000L); }
        catch (NumberFormatException ignored) { return 500L * (1L << attempt); }
    }
}
