package com.couponnumbergenerator.ai;

import com.couponnumbergenerator.dto.response.CouponStatsSnapshot;
import org.springframework.stereotype.Component;

@Component
public class InsightPromptBuilder {

    private static final String SYSTEM_CONTEXT = """
            You are a business analyst. Given coupon statistics, write a short professional summary (2–3 paragraphs).
            Cover: inventory health, weekly trend, any capacity warnings, one recommendation.
            Use only the numbers provided. Plain paragraphs, no bullet points.
            """;

    public String build(CouponStatsSnapshot stats) {
        return SYSTEM_CONTEXT + "\n" + formatStats(stats) + "\nProvide your summary:";
    }

    private String formatStats(CouponStatsSnapshot stats) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== COUPON STATISTICS SNAPSHOT (as of ").append(stats.asOf()).append(") ===\n\n");

        appendOverall(sb, stats);
        appendWeeklyTrend(sb, stats);
        appendFuelTypeBreakdown(sb, stats);
        appendSequenceCapacities(sb, stats);

        sb.append("\n=== END OF DATA ===\n");
        return sb.toString();
    }

    private void appendOverall(StringBuilder sb, CouponStatsSnapshot stats) {
        sb.append("OVERALL INVENTORY:\n");
        sb.append("  Total coupons: ").append(stats.totalCoupons()).append("\n");

        if (stats.totalCoupons() > 0) {
            stats.byStatus().forEach((status, count) ->
                    sb.append("  ").append(status).append(": ").append(count)
                            .append(" (").append(pct(count, stats.totalCoupons())).append("%)\n"));
        }
        sb.append("\n");
    }

    private void appendWeeklyTrend(StringBuilder sb, CouponStatsSnapshot stats) {
        sb.append("WEEKLY ISSUANCE TREND:\n");
        sb.append("  This week : ").append(stats.issuedThisWeek()).append("\n");
        sb.append("  Last week : ").append(stats.issuedLastWeek()).append("\n");

        if (stats.issuedLastWeek() > 0) {
            long delta = stats.issuedThisWeek() - stats.issuedLastWeek();
            double changePct = (delta * 100.0) / stats.issuedLastWeek();
            String direction = delta >= 0 ? "up" : "down";
            sb.append("  Change    : ").append(String.format("%.1f", Math.abs(changePct)))
                    .append("% ").append(direction).append("\n");
        } else if (stats.issuedThisWeek() > 0) {
            sb.append("  Change    : new activity this week (no prior-week baseline)\n");
        } else {
            sb.append("  Change    : no issuances recorded in either week\n");
        }
        sb.append("\n");
    }

    private void appendFuelTypeBreakdown(StringBuilder sb, CouponStatsSnapshot stats) {
        sb.append("FUEL TYPE BREAKDOWN:\n");
        if (stats.fuelTypeBreakdown().isEmpty()) {
            sb.append("  No fuel types configured.\n");
        } else {
            stats.fuelTypeBreakdown().forEach(ft -> {
                sb.append("  ").append(ft.fuelTypeName())
                        .append(" (").append(ft.typeCode()).append(")")
                        .append(" — total: ").append(ft.totalIssued());
                ft.byStatus().forEach((status, count) ->
                        sb.append(", ").append(status.name().toLowerCase()).append(": ").append(count));
                sb.append("\n");
            });
        }
        sb.append("\n");
    }

    private void appendSequenceCapacities(StringBuilder sb, CouponStatsSnapshot stats) {
        sb.append("SEQUENCE CAPACITY (per fuel type):\n");
        if (stats.sequenceCapacities().isEmpty()) {
            sb.append("  No sequence data available.\n");
        } else {
            stats.sequenceCapacities().forEach(sc ->
                    sb.append("  ").append(sc.fuelTypeName())
                            .append(" — current letter: '").append(sc.currentLetter()).append("'")
                            .append(", issued in this letter: ").append(sc.issuedInCurrentLetter())
                            .append(", letter capacity used: ")
                            .append(String.format("%.1f", sc.capacityUsedPercent())).append("%")
                            .append("\n")
            );
        }
        sb.append("\n");
    }

    private long pct(long part, long total) {
        return total == 0 ? 0 : Math.round((part * 100.0) / total);
    }
}