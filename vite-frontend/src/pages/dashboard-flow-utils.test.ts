import { describe, expect, it } from "vitest";

import {
  buildHourDetailCacheKey,
  createDefaultFlowStatsRange,
  getHourDetailHeading,
  getForwardStatsHeading,
  normalizeFlowSeries,
  resolveHourTimeFromChartInteraction,
  selectDefaultHourTime,
  shouldCollapseForwardStatsByDefault,
  shouldShowForwardOwner,
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
        { hourTime: 2000, time: "04-07 12:00", flow: 20, inFlow: 12, outFlow: 8, sampled: true },
        { hourTime: 1000, time: "04-07 11:00", flow: 10, inFlow: 7, outFlow: 3, sampled: true },
      ],
      (value) => `${value} B`,
    );

    expect(chartData).toEqual([
      { hourTime: 1000, label: "04-07 11:00", flow: 10, inFlow: 7, outFlow: 3, formattedFlow: "10 B", sampled: true },
      { hourTime: 2000, label: "04-07 12:00", flow: 20, inFlow: 12, outFlow: 8, formattedFlow: "20 B", sampled: true },
    ]);
  });

  it("keeps unsampled hours as chart gaps instead of coercing them to zero", () => {
    const chartData = normalizeFlowSeries(
      [
        { hourTime: 2000, time: "04-07 12:00", flow: null, inFlow: null, outFlow: null, sampled: false },
        { hourTime: 1000, time: "04-07 11:00", flow: 10, inFlow: 7, outFlow: 3, sampled: true },
      ],
      (value) => `${value} B`,
    );

    expect(chartData).toEqual([
      { hourTime: 1000, label: "04-07 11:00", flow: 10, inFlow: 7, outFlow: 3, formattedFlow: "10 B", sampled: true },
      { hourTime: 2000, label: "04-07 12:00", flow: null, inFlow: null, outFlow: null, formattedFlow: null, sampled: false },
    ]);
  });

  it("sorts forward stats by total flow descending", () => {
    const rows = sortForwardStats([
      { id: 2, name: "b", tunnelId: 1, tunnelName: "t1", inAddress: "1.1.1.1:80", remoteAddr: "2.2.2.2:80", inFlow: 1, outFlow: 2, flow: 3 },
      { id: 1, name: "a", tunnelId: 1, tunnelName: "t1", inAddress: "1.1.1.1:81", remoteAddr: "2.2.2.2:81", inFlow: 5, outFlow: 5, flow: 10 },
    ]);

    expect(rows.map((item) => item.id)).toEqual([1, 2]);
  });

  it("selects the latest non-zero hour and falls back when all hours are empty", () => {
    expect(
      selectDefaultHourTime(
        [
          { hourTime: 1000, time: "04-07 10:00", flow: 0, inFlow: 0, outFlow: 0 },
          { hourTime: 2000, time: "04-07 11:00", flow: 12, inFlow: 10, outFlow: 2, sampled: true },
          { hourTime: 3000, time: "04-07 12:00", flow: 0, inFlow: 0, outFlow: 0, sampled: true },
          { hourTime: 4000, time: "04-07 13:00", flow: 8, inFlow: 5, outFlow: 3, sampled: true },
        ],
        9999,
      ),
    ).toBe(4000);

    expect(
      selectDefaultHourTime(
        [
          { hourTime: 1000, time: "04-07 10:00", flow: 0, inFlow: 0, outFlow: 0, sampled: true },
          { hourTime: 2000, time: "04-07 11:00", flow: 0, inFlow: 0, outFlow: 0, sampled: true },
        ],
        9999,
      ),
    ).toBe(9999);
  });

  it("ignores unsampled gaps when choosing the default hour", () => {
    expect(
      selectDefaultHourTime(
        [
          { hourTime: 1000, time: "04-07 10:00", flow: null, inFlow: null, outFlow: null, sampled: false },
          { hourTime: 2000, time: "04-07 11:00", flow: 0, inFlow: 0, outFlow: 0, sampled: true },
          { hourTime: 3000, time: "04-07 12:00", flow: 6, inFlow: 4, outFlow: 2, sampled: true },
        ],
        9999,
      ),
    ).toBe(3000);
  });

  it("builds a stable cache key for hourly detail requests", () => {
    expect(buildHourDetailCacheKey("global", 1000, 2000, 1500)).toBe("global:1000:2000:1500");
  });

  it("returns role-specific rule table labels", () => {
    expect(getForwardStatsHeading("top10")).toBe("当前时间段 Top 10 规则");
    expect(getForwardStatsHeading("all")).toBe("当前时间段全部规则");
    expect(getHourDetailHeading()).toBe("选中小时规则消耗");
    expect(shouldCollapseForwardStatsByDefault("top10")).toBe(false);
    expect(shouldCollapseForwardStatsByDefault("all")).toBe(true);
    expect(shouldShowForwardOwner("self")).toBe(false);
    expect(shouldShowForwardOwner("global")).toBe(true);
  });

  it("resolves the hovered hour from recharts active index", () => {
    const chartData = normalizeFlowSeries(
      [
        { hourTime: 1000, time: "04-07 10:00", flow: 10, inFlow: 7, outFlow: 3, sampled: true },
        { hourTime: 2000, time: "04-07 11:00", flow: 20, inFlow: 12, outFlow: 8, sampled: true },
        { hourTime: 3000, time: "04-07 12:00", flow: 30, inFlow: 18, outFlow: 12, sampled: true },
      ],
      (value) => `${value} B`,
    );

    expect(resolveHourTimeFromChartInteraction(chartData, 1)).toBe(2000);
    expect(resolveHourTimeFromChartInteraction(chartData, "2")).toBe(3000);
    expect(resolveHourTimeFromChartInteraction(chartData, undefined)).toBeNull();
    expect(resolveHourTimeFromChartInteraction(chartData, "bad-index")).toBeNull();
  });
});
