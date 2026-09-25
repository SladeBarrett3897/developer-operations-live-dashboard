package devdashboard;

import java.util.ArrayList;
import java.util.List;

public final class DeveloperSignalServiceTest {
    public static void main(String[] args) throws Exception {
        List<DeveloperSignalService.Decision> delivered = new ArrayList<>();
        DeveloperSignalService service = new DeveloperSignalService((signal, decision) -> delivered.add(decision));
        DeveloperSignalService.Decision decision = service.accept(new DeveloperSignalService.BuildSignal(
                "diag-203", "diagnostic", "release-ledger", "failed", 0,
                "error", "deploy rejected token=private-value trace=7f2"));

        check("attention".equals(decision.status()), "failed diagnostic must require attention");
        check(decision.points().size() == 1, "diagnostic must emit one metric");
        check("counter".equals(decision.points().get(0).get("type")), "diagnostic metric must be a counter");
        check("deploy rejected token=[redacted] trace=7f2".equals(decision.dashboardEvent().get("diagnostic")),
                "dashboard diagnostic must redact credentials");
        check(delivered.size() == 1, "accepted decision must be handed off once");
        System.out.println("DeveloperSignalServiceTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
