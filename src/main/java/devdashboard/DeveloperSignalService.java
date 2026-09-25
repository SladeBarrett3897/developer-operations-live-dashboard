package devdashboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DeveloperSignalService {
    record BuildSignal(String eventId, String kind, String service, String outcome,
                       long durationMs, String severity, String diagnostic) {}

    record Decision(String status, List<Map<String, Object>> points, Map<String, Object> dashboardEvent) {}

    interface SignalHandoff {
        void deliver(BuildSignal signal, Decision decision) throws Exception;
    }

    private final SignalHandoff handoff;

    DeveloperSignalService(SignalHandoff handoff) { this.handoff = handoff; }

    Decision accept(BuildSignal signal) throws Exception {
        if (signal.eventId() == null || signal.eventId().isBlank()) throw new IllegalArgumentException("event_id is required");
        if (!List.of("build", "release", "diagnostic").contains(signal.kind()))
            throw new IllegalArgumentException("kind must be build, release, or diagnostic");
        String status = ("failed".equals(signal.outcome()) || "error".equals(signal.severity()))
                ? "attention" : "healthy";
        Map<String, String> tags = Map.of("service", signal.service(), "outcome", signal.outcome());
        List<Map<String, Object>> points = switch (signal.kind()) {
            case "build" -> List.of(
                    metric("devtools.build.completed", 1, "counter", tags),
                    metric("devtools.build.duration_ms", signal.durationMs(), "timing", tags));
            case "release" -> List.of(metric("devtools.release.completed", 1, "counter", tags));
            default -> List.of(metric("devtools.diagnostic.total", 1, "counter",
                    Map.of("service", signal.service(), "severity", signal.severity())));
        };
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", signal.eventId());
        event.put("kind", signal.kind());
        event.put("service", signal.service());
        event.put("status", status);
        event.put("outcome", signal.outcome());
        if ("diagnostic".equals(signal.kind())) event.put("diagnostic", redact(signal.diagnostic()));
        Decision decision = new Decision(status, points, event);
        handoff.deliver(signal, decision);
        return decision;
    }

    private static Map<String, Object> metric(String name, Number value, String type, Map<String, String> tags) {
        return Map.of("name", name, "value", value, "type", type, "tags", tags);
    }

    private static String redact(String diagnostic) {
        if (diagnostic == null) return "";
        return diagnostic.replaceAll("(?i)(token|secret|password)=[^\\s]+", "$1=[redacted]");
    }
}
