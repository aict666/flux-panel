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

export function toDatetimeLocalValue(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const hours = `${date.getHours()}`.padStart(2, "0");
  const minutes = `${date.getMinutes()}`.padStart(2, "0");
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}
