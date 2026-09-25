package devdashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DeveloperOperationsServer {
    private DeveloperOperationsServer() {}

    public static void main(String[] args) throws Exception {
        DashboardConfig config = DashboardConfig.load(args, System.getenv());
        InfraiOperationsClient client = new InfraiOperationsClient(config);
        client.createChannel();
        DeveloperSignalService service = new DeveloperSignalService(client);
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/operations", exchange -> handleOperation(exchange, service));
        server.createContext("/dashboard-token", exchange -> handleToken(exchange, client));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Developer operations service listening on http://127.0.0.1:" + config.port());
    }

    @SuppressWarnings("unchecked")
    private static void handleOperation(HttpExchange exchange, DeveloperSignalService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "POST required")); return; }
        try {
            Map<String, Object> input = (Map<String, Object>) MiniJson.parse(read(exchange));
            DeveloperSignalService.BuildSignal signal = new DeveloperSignalService.BuildSignal(
                    text(input, "event_id"), text(input, "kind"), text(input, "service"),
                    text(input, "outcome"), number(input, "duration_ms"),
                    text(input, "severity"), text(input, "diagnostic"));
            DeveloperSignalService.Decision decision = service.accept(signal);
            send(exchange, 202, Map.of("event_id", signal.eventId(), "status", decision.status(),
                    "metrics_written", decision.points().size(), "published", true));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("error", e.getMessage()));
        } catch (InfraiOperationsClient.InfraiException e) {
            int status = e.status >= 400 && e.status < 500 ? e.status : 502;
            send(exchange, status, Map.of("error", e.detail));
        } catch (Exception e) {
            send(exchange, 502, Map.of("error", "Operation handoff did not complete"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void handleToken(HttpExchange exchange, InfraiOperationsClient client) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "POST required")); return; }
        try {
            Map<String, Object> input = (Map<String, Object>) MiniJson.parse(read(exchange));
            String clientId = text(input, "client_id");
            if (clientId.isBlank()) throw new IllegalArgumentException("client_id is required");
            send(exchange, 200, client.issueDashboardToken(clientId));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("error", e.getMessage()));
        } catch (InfraiOperationsClient.InfraiException e) {
            int status = e.status >= 400 && e.status < 500 ? e.status : 502;
            send(exchange, status, Map.of("error", e.detail));
        } catch (Exception e) {
            send(exchange, 502, Map.of("error", "Token request did not complete"));
        }
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String text(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = MiniJson.write(new LinkedHashMap<>(body)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
