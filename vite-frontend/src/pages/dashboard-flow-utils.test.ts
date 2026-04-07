import { describe, expect, it } from "vitest";

import {
  createDefaultFlowStatsRange,
  normalizeFlowSeries,
  sortForwardStats,
  validateFlowStatsRange,
} from "./dashboard-flow-utils";

describe("dashboard-flow-utils", () => {
  it("creates a default last-24-hours range", () => {
    const range = createDefaultFlowStatsRange(new Date("2026-04-07T12:34:56"));

    expect(range.start).toBe("2026-04-06T13:00");
    expect(range.end).toBe("2026-04-07T12:00");
  });

  it("validates range order and thirty-day limit", () => {
    expect(validateFlowStatsRange("2026-04-07T12:00", "2026-04-07T11:00")).toBe("结束时间必须大于开始时间");
    expect(validateFlowStatsRange("2026-03-01T00:00", "2026-04-07T12:00")).toBe("统计时间范围不能超过30天");
    expect(validateFlowStatsRange("2026-04-06T13:00", "2026-04-07T12:00")).toBeNull();
  });

  it("normalizes series order and keeps formatted values", () => {
    const chartData = normalizeFlowSeries(
      [
        { hourTime: 2000, time: "04-07 12:00", flow: 20, inFlow: 12, outFlow: 8 },
        { hourTime: 1000, time: "04-07 11:00", flow: 10, inFlow: 7, outFlow: 3 },
      ],
      (value) => `${value} B`,
    );

    expect(chartData).toEqual([
      { hourTime: 1000, label: "04-07 11:00", flow: 10, inFlow: 7, outFlow: 3, formattedFlow: "10 B" },
      { hourTime: 2000, label: "04-07 12:00", flow: 20, inFlow: 12, outFlow: 8, formattedFlow: "20 B" },
    ]);
  });

  it("sorts forward stats by total flow descending", () => {
    const rows = sortForwardStats([
      { id: 2, name: "b", tunnelId: 1, tunnelName: "t1", inAddress: "1.1.1.1:80", remoteAddr: "2.2.2.2:80", inFlow: 1, outFlow: 2, flow: 3 },
      { id: 1, name: "a", tunnelId: 1, tunnelName: "t1", inAddress: "1.1.1.1:81", remoteAddr: "2.2.2.2:81", inFlow: 5, outFlow: 5, flow: 10 },
    ]);

    expect(rows.map((item) => item.id)).toEqual([1, 2]);
  });
});
