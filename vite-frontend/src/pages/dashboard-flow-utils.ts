export interface FlowStatsRangeState {
  start: string;
  end: string;
}

export interface FlowSeriesPoint {
  hourTime: number;
  time: string;
  inFlow: number;
  outFlow: number;
  flow: number;
}

export type FlowStatsScope = "self" | "global";
export type FlowStatsRankingMode = "top10" | "all";

export interface FlowStatsSummary {
  totalInFlow: number;
  totalOutFlow: number;
  totalFlow: number;
}

export interface FlowStatsMeta {
  scope: FlowStatsScope;
  rankingMode?: FlowStatsRankingMode | null;
  totalRuleCount: number;
  returnedRuleCount: number;
}

export interface FlowChartPoint {
  hourTime: number;
  label: string;
  inFlow: number;
  outFlow: number;
  flow: number;
  formattedFlow: string;
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

const HOUR_MS = 60 * 60 * 1000;
const THIRTY_DAYS_MS = 30 * 24 * HOUR_MS;

export function createDefaultFlowStatsRange(now: Date = new Date()): FlowStatsRangeState {
  const end = new Date(now);
  end.setMinutes(0, 0, 0);

  const start = new Date(end.getTime() - 23 * HOUR_MS);

  return {
    start: toDatetimeLocalValue(start),
    end: toDatetimeLocalValue(end),
  };
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
    .sort((left, right) => left.hourTime - right.hourTime)
    .map((item) => ({
      hourTime: item.hourTime,
      label: item.time,
      inFlow: item.inFlow ?? 0,
      outFlow: item.outFlow ?? 0,
      flow: item.flow ?? 0,
      formattedFlow: formatFlow(item.flow ?? 0),
    }));
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
  const sorted = [...series].sort((left, right) => left.hourTime - right.hourTime);
  for (let index = sorted.length - 1; index >= 0; index -= 1) {
    if ((sorted[index].flow ?? 0) > 0) {
      return sorted[index].hourTime;
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

export function shouldShowForwardOwner(scope: FlowStatsScope): boolean {
  return scope === "global";
}

export function shouldCollapseForwardStatsByDefault(
  rankingMode: FlowStatsRankingMode | null | undefined,
): boolean {
  return rankingMode === "all";
}

export function resolveHourTimeFromChartInteraction(
  chartData: FlowChartPoint[],
  activeIndex: number | string | null | undefined,
): number | null {
  if (activeIndex === undefined || activeIndex === null) {
    return null;
  }

  const normalizedIndex = typeof activeIndex === "string" ? Number.parseInt(activeIndex, 10) : activeIndex;
  if (!Number.isInteger(normalizedIndex) || normalizedIndex < 0 || normalizedIndex >= chartData.length) {
    return null;
  }

  return chartData[normalizedIndex]?.hourTime ?? null;
}

export function toDatetimeLocalValue(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const hours = `${date.getHours()}`.padStart(2, "0");
  const minutes = `${date.getMinutes()}`.padStart(2, "0");
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}
