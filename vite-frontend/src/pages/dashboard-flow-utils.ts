export interface FlowStatsRangeState {
  start: string;
  end: string;
}

export type FlowStatsPreset = "last24Hours" | "last7Days" | "last30Days" | "custom";
export type FlowStatsGranularity = "hour" | "day";
export type FlowStatsMetric = "flow" | "inFlow" | "outFlow";
export type FlowStatsScope = "self" | "global";
export type FlowStatsRankingMode = "top10" | "all";

export interface FlowStatsFiltersState extends FlowStatsRangeState {
  preset: FlowStatsPreset;
  granularity: FlowStatsGranularity;
  metric: FlowStatsMetric;
}

export interface FlowSeriesPoint {
  bucketTime?: number;
  hourTime?: number;
  time: string;
  sampled?: boolean;
  inFlow: number | null;
  outFlow: number | null;
  flow: number | null;
}

export interface FlowStatsSummary {
  totalInFlow: number;
  totalOutFlow: number;
  totalFlow: number;
}

export interface FlowStatsMeta {
  scope: FlowStatsScope;
  rankingMode?: FlowStatsRankingMode | null;
  totalRuleCount?: number;
  returnedRuleCount?: number;
  hasSamplingGap?: boolean;
  granularity?: FlowStatsGranularity;
  metric?: FlowStatsMetric;
  topRuleCount?: number;
  peakBucketTime?: number | null;
}

export interface FlowChartPoint {
  bucketTime: number;
  hourTime: number;
  label: string;
  sampled: boolean;
  inFlow: number | null;
  outFlow: number | null;
  flow: number | null;
  formattedFlow: string | null;
}

export interface ForwardFlowStat {
  id: number;
  name: string;
  userName?: string;
  tunnelId: number;
  tunnelName: string;
  inAddress: string;
  remoteAddr: string;
  inFlow: number;
  outFlow: number;
  flow: number;
}

export interface TopRuleSeries {
  id: number;
  name: string;
  userName?: string;
  totalInFlow: number;
  totalOutFlow: number;
  totalFlow: number;
  series: FlowSeriesPoint[];
}

export interface TopRuleSeriesMeta {
  key: string;
  id: number;
  name: string;
  userName?: string;
  color: string;
  total: number;
}

export interface TopRuleChartRow {
  bucketTime: number;
  label: string;
  sampled: boolean;
  [key: string]: string | number | boolean | null;
}

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;
const THIRTY_DAYS_MS = 30 * DAY_MS;
const TOP_RULE_COLORS = [
  "#3b82f6",
  "#14b8a6",
  "#f59e0b",
  "#8b5cf6",
  "#ef4444",
  "#06b6d4",
  "#84cc16",
  "#f97316",
  "#ec4899",
  "#6366f1",
  "#0ea5e9",
  "#10b981",
];

export function createDefaultFlowStatsRange(now: Date = new Date()): FlowStatsRangeState {
  const end = new Date(now);
  end.setMinutes(0, 0, 0);

  const start = new Date(end.getTime() - 23 * HOUR_MS);

  return {
    start: toDatetimeLocalValue(start),
    end: toDatetimeLocalValue(end),
  };
}

export function applyPresetRange(preset: FlowStatsPreset, now: Date = new Date()): Omit<FlowStatsFiltersState, "metric"> {
  if (preset === "custom") {
    const range = createDefaultFlowStatsRange(now);
    return {
      preset,
      granularity: "hour",
      start: range.start,
      end: range.end,
    };
  }

  const end = new Date(now);
  end.setMinutes(0, 0, 0);

  const hours = preset === "last24Hours" ? 24 : preset === "last7Days" ? 7 * 24 : 30 * 24;
  const start = new Date(end.getTime() - (hours - 1) * HOUR_MS);

  return {
    preset,
    granularity: preset === "last24Hours" ? "hour" : "day",
    start: toDatetimeLocalValue(start),
    end: toDatetimeLocalValue(end),
  };
}

export function createFlowStatsFilters(now: Date = new Date()): FlowStatsFiltersState {
  return {
    ...applyPresetRange("last24Hours", now),
    metric: "flow",
  };
}

export function shouldShowCustomRangeInputs(preset: FlowStatsPreset): boolean {
  return preset === "custom";
}

export function validateFlowStatsRange(start: string, end: string): string | null {
  if (!start || !end) {
    return "开始时间和结束时间不能为空";
  }

  const startTime = new Date(start).getTime();
  const endTime = new Date(end).getTime();

  if (Number.isNaN(startTime) || Number.isNaN(endTime)) {
    return "时间格式不正确";
  }

  if (endTime <= startTime) {
    return "结束时间必须大于开始时间";
  }

  if (endTime - startTime > THIRTY_DAYS_MS) {
    return "统计时间范围不能超过30天";
  }

  return null;
}

export function normalizeFlowSeries(
  series: FlowSeriesPoint[],
  formatFlow: (value: number) => string,
): FlowChartPoint[] {
  return [...series]
    .sort((left, right) => resolvePointTime(left) - resolvePointTime(right))
    .map((item) => {
      const bucketTime = resolvePointTime(item);
      return {
        bucketTime,
        hourTime: bucketTime,
        label: item.time,
        sampled: item.sampled !== false,
        inFlow: item.sampled === false ? null : (item.inFlow ?? 0),
        outFlow: item.sampled === false ? null : (item.outFlow ?? 0),
        flow: item.sampled === false ? null : (item.flow ?? 0),
        formattedFlow: item.sampled === false || item.flow === null ? null : formatFlow(item.flow),
      };
    });
}

export function sortForwardStats(rows: ForwardFlowStat[]): ForwardFlowStat[] {
  return [...rows].sort((left, right) => {
    if (right.flow !== left.flow) {
      return right.flow - left.flow;
    }
    return left.id - right.id;
  });
}

export function selectDefaultHourTime(series: FlowSeriesPoint[], fallbackHourTime: number): number {
  const sorted = [...series].sort((left, right) => resolvePointTime(left) - resolvePointTime(right));
  for (let index = sorted.length - 1; index >= 0; index -= 1) {
    if (sorted[index].sampled === false) {
      continue;
    }
    if ((sorted[index].flow ?? 0) > 0) {
      return resolvePointTime(sorted[index]);
    }
  }
  return fallbackHourTime;
}

export function buildHourDetailCacheKey(
  scope: FlowStatsScope,
  startTime: number,
  endTime: number,
  hourTime: number,
): string {
  return `${scope}:${startTime}:${endTime}:${hourTime}`;
}

export function getForwardStatsHeading(rankingMode: FlowStatsRankingMode | null | undefined): string {
  return rankingMode === "all" ? "当前时间段全部规则" : "当前时间段 Top 10 规则";
}

export function getHourDetailHeading(): string {
  return "选中小时规则消耗";
}

export function shouldShowForwardOwner(scope: FlowStatsScope): boolean {
  return scope === "global";
}

export function shouldCollapseForwardStatsByDefault(
  rankingMode: FlowStatsRankingMode | null | undefined,
): boolean {
  return rankingMode === "all";
}

export function resolveHourTimeFromChartInteraction(
  chartData: Array<{ bucketTime?: number; hourTime?: number }>,
  activeIndex: number | string | null | undefined,
): number | null {
  if (activeIndex === undefined || activeIndex === null) {
    return null;
  }

  const normalizedIndex = typeof activeIndex === "string" ? Number.parseInt(activeIndex, 10) : activeIndex;
  if (!Number.isInteger(normalizedIndex) || normalizedIndex < 0 || normalizedIndex >= chartData.length) {
    return null;
  }

  return chartData[normalizedIndex]?.bucketTime ?? chartData[normalizedIndex]?.hourTime ?? null;
}

export function buildTopRuleChartData(
  topRuleSeries: TopRuleSeries[],
  metric: FlowStatsMetric,
): { chartData: TopRuleChartRow[]; seriesMeta: TopRuleSeriesMeta[] } {
  const seriesMeta = topRuleSeries.map((item, index) => ({
    key: `rule_${item.id}`,
    id: item.id,
    name: item.name,
    userName: item.userName,
    color: TOP_RULE_COLORS[index % TOP_RULE_COLORS.length],
    total: metric === "inFlow" ? item.totalInFlow : metric === "outFlow" ? item.totalOutFlow : item.totalFlow,
  }));

  const rowMap = new Map<number, TopRuleChartRow>();

  for (const meta of seriesMeta) {
    const source = topRuleSeries.find((item) => item.id === meta.id);
    if (!source) {
      continue;
    }

    for (const point of source.series) {
      const bucketTime = resolvePointTime(point);
      const existing = rowMap.get(bucketTime) ?? {
        bucketTime,
        label: point.time,
        sampled: point.sampled !== false,
      };

      if (point.sampled === false) {
        existing.sampled = false;
      } else if (existing.sampled !== false) {
        existing[meta.key] = metric === "inFlow" ? (point.inFlow ?? 0) : metric === "outFlow" ? (point.outFlow ?? 0) : (point.flow ?? 0);
      }

      rowMap.set(bucketTime, existing);
    }
  }

  const chartData = [...rowMap.values()]
    .sort((left, right) => left.bucketTime - right.bucketTime)
    .map((row) => {
      const normalizedRow: TopRuleChartRow = { ...row };
      for (const meta of seriesMeta) {
        if (normalizedRow.sampled === false) {
          normalizedRow[meta.key] = null;
          continue;
        }
        if (!(meta.key in normalizedRow)) {
          normalizedRow[meta.key] = 0;
        }
      }
      return normalizedRow;
    });

  return { chartData, seriesMeta };
}

export function toDatetimeLocalValue(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const hours = `${date.getHours()}`.padStart(2, "0");
  const minutes = `${date.getMinutes()}`.padStart(2, "0");
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

function resolvePointTime(point: Pick<FlowSeriesPoint, "bucketTime" | "hourTime">): number {
  return point.bucketTime ?? point.hourTime ?? 0;
}
