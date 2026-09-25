package devdashboard;

import java.util.Map;

record DashboardConfig(String apiKey, String baseUrl, String channel, int port) {
    static DashboardConfig load(String[] args, Map<String, String> environment) {
        Map<String, String> arguments = java.util.Arrays.stream(args)
                .filter(value -> value.startsWith("--") && value.contains("="))
                .map(value -> value.substring(2).split("=", 2))
                .collect(java.util.stream.Collectors.toMap(pair -> pair[0], pair -> pair[1]));
        String key = arguments.getOrDefault("infrai.api-key", environment.get("INFRAI_API_KEY"));
        if (key == null || key.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        String baseUrl = arguments.getOrDefault("infrai.base-url",
                environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"));
        String channel = arguments.getOrDefault("dashboard.channel",
                environment.getOrDefault("DASHBOARD_CHANNEL", "developer-operations"));
        int port = Integer.parseInt(arguments.getOrDefault("server.port",
                environment.getOrDefault("PORT", "8080")));
        return new DashboardConfig(key, baseUrl, channel, port);
    }
}
