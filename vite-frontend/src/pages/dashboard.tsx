import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Button } from "@heroui/button";
import { Input } from "@heroui/input";
import { Select, SelectItem } from "@heroui/select";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { getUserPackageFlowStats } from "@/api";
import {
  applyPresetRange,
  buildTopRuleChartData,
  createFlowStatsFilters,
  normalizeFlowSeries,
  shouldShowCustomRangeInputs,
  toDatetimeLocalValue,
  validateFlowStatsRange,
  type FlowChartPoint,
  type FlowSeriesPoint,
  type FlowStatsFiltersState,
  type FlowStatsGranularity,
  type FlowStatsMeta,
  type FlowStatsMetric,
  type FlowStatsPreset,
  type FlowStatsSummary,
  type TopRuleSeries,
} from "./dashboard-flow-utils";

interface FlowStatsResponse {
  filters: {
    startTime: number;
    endTime: number;
    granularity: FlowStatsGranularity;
    metric: FlowStatsMetric;
  };
  overviewCards: {
    userCount?: number;
    tunnelCount?: number;
    forwardCount?: number;
    totalInFlow?: number;
    totalOutFlow?: number;
    totalFlow?: number;
    flowLimit?: number;
    usedFlow?: number;
    forwardLimit?: number;
    usedForwardCount?: number;
    peakBucketTime?: number | null;
    peakBucketValue?: number | null;
  };
  rankings: {
    leftTitle: string;
    rightTitle: string;
    left: RankingItem[];
    right: RankingItem[];
  };
  summary: FlowStatsSummary;
  trendSeries: FlowSeriesPoint[];
  topRuleSeries: TopRuleSeries[];
  meta: FlowStatsMeta;
}

interface RankingItem {
  id: number;
  name: string;
  secondaryName?: string;
  inFlow: number;
  outFlow: number;
  flow: number;
}

const PRESET_OPTIONS: Array<{ key: FlowStatsPreset; label: string }> = [
  { key: "last24Hours", label: "近24小时" },
  { key: "last7Days", label: "近7天" },
  { key: "last30Days", label: "近30天" },
  { key: "custom", label: "自定义" },
];

const GRANULARITY_OPTIONS: Array<{ key: FlowStatsGranularity; label: string }> = [
  { key: "hour", label: "按小时" },
  { key: "day", label: "按天" },
];

const METRIC_OPTIONS: Array<{ key: FlowStatsMetric; label: string }> = [
  { key: "flow", label: "总流量" },
  { key: "inFlow", label: "入站" },
  { key: "outFlow", label: "出站" },
];

const METRIC_COLORS: Record<FlowStatsMetric, string> = {
  flow: "#3b82f6",
  inFlow: "#10b981",
  outFlow: "#f97316",
};

const MAX_RANKING_ROWS = 8;

export default function DashboardPage() {
  const [filters, setFilters] = useState<FlowStatsFiltersState>(() => createFlowStatsFilters());
  const [data, setData] = useState<FlowStatsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    const initialFilters = createFlowStatsFilters();
    setFilters(initialFilters);
    localStorage.setItem("e", "/dashboard");
    void loadDashboard(initialFilters, true);
  }, []);

  const loadDashboard = async (nextFilters: FlowStatsFiltersState, initialLoad = false) => {
    const validationError = validateFlowStatsRange(nextFilters.start, nextFilters.end);
    if (validationError) {
      toast.error(validationError);
      return;
    }

    if (initialLoad) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }

    try {
      const response = await getUserPackageFlowStats({
        startTime: new Date(nextFilters.start).getTime(),
        endTime: new Date(nextFilters.end).getTime(),
        granularity: nextFilters.granularity,
        metric: nextFilters.metric,
      });

      if (response.code !== 0) {
        toast.error(response.msg || "获取仪表盘数据失败");
        return;
      }

      const nextData = response.data as FlowStatsResponse;
      setData(nextData);
      setFilters((current) => ({
        ...current,
        start: toDatetimeLocalValue(new Date(nextData.filters.startTime)),
        end: toDatetimeLocalValue(new Date(nextData.filters.endTime)),
        granularity: nextData.filters.granularity,
        metric: nextData.filters.metric,
      }));
    } catch (error) {
      console.error("获取仪表盘数据失败:", error);
      toast.error("获取仪表盘数据失败");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handlePresetChange = (preset: FlowStatsPreset) => {
    if (preset === "custom") {
      setFilters((current) => ({ ...current, preset: "custom" }));
      return;
    }

    const nextFilters: FlowStatsFiltersState = {
      ...filters,
      ...applyPresetRange(preset),
      metric: filters.metric,
    };
    setFilters(nextFilters);
    void loadDashboard(nextFilters);
  };

  const handleGranularityChange = (granularity: FlowStatsGranularity) => {
    const nextFilters = { ...filters, granularity };
    setFilters(nextFilters);
    void loadDashboard(nextFilters);
  };

  const handleMetricChange = (metric: FlowStatsMetric) => {
    const nextFilters = { ...filters, metric };
    setFilters(nextFilters);
    void loadDashboard(nextFilters);
  };

  const handleApplyCustomRange = () => {
    void loadDashboard(filters);
  };

  const activeMetric = data?.filters.metric ?? filters.metric;
  const isAdmin = data?.meta.scope === "global";
  const trendChartData = data ? normalizeFlowSeries(data.trendSeries, formatFlow) : [];
  const { chartData: topRuleChartData, seriesMeta } = data
    ? buildTopRuleChartData(data.topRuleSeries, activeMetric)
    : { chartData: [], seriesMeta: [] };

  if (loading) {
    return (
      <div className="px-3 lg:px-6 flex-grow pt-2 lg:pt-4">
        <div className="flex items-center justify-center h-64">
          <div className="flex items-center gap-3">
            <div className="animate-spin h-5 w-5 border-2 border-gray-200 dark:border-gray-700 border-t-gray-600 dark:border-t-gray-300 rounded-full" />
            <span className="text-default-600">正在加载仪表盘...</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 lg:px-6 py-2 lg:py-4 space-y-6">
      <Card className="border border-gray-200 dark:border-default-200 shadow-md">
        <CardBody className="p-4 lg:p-5">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-end">
              <Select
                label="时间范围"
                size="sm"
                selectedKeys={[filters.preset]}
                className="w-full sm:w-48"
                onSelectionChange={(keys) => {
                  const preset = Array.from(keys)[0] as FlowStatsPreset | undefined;
                  if (preset) {
                    handlePresetChange(preset);
                  }
                }}
              >
                {PRESET_OPTIONS.map((option) => (
                  <SelectItem key={option.key}>{option.label}</SelectItem>
                ))}
              </Select>

              {shouldShowCustomRangeInputs(filters.preset) && (
                <>
                  <Input
                    type="datetime-local"
                    label="开始时间"
                    size="sm"
                    className="w-full sm:w-52"
                    value={filters.start}
                    onValueChange={(value) => setFilters((current) => ({ ...current, start: value }))}
                  />
                  <Input
                    type="datetime-local"
                    label="结束时间"
                    size="sm"
                    className="w-full sm:w-52"
                    value={filters.end}
                    onValueChange={(value) => setFilters((current) => ({ ...current, end: value }))}
                  />
                  <Button color="primary" variant="flat" onPress={handleApplyCustomRange}>
                    应用
                  </Button>
                </>
              )}

              <Select
                label="粒度"
                size="sm"
                selectedKeys={[filters.granularity]}
                className="w-full sm:w-32"
                onSelectionChange={(keys) => {
                  const granularity = Array.from(keys)[0] as FlowStatsGranularity | undefined;
                  if (granularity) {
                    handleGranularityChange(granularity);
                  }
                }}
              >
                {GRANULARITY_OPTIONS.map((option) => (
                  <SelectItem key={option.key}>{option.label}</SelectItem>
                ))}
              </Select>

              <Button color="primary" onPress={() => void loadDashboard(filters)} isLoading={refreshing}>
                刷新
              </Button>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {METRIC_OPTIONS.map((option) => (
                <Button
                  key={option.key}
                  size="sm"
                  radius="full"
                  color={activeMetric === option.key ? "primary" : "default"}
                  variant={activeMetric === option.key ? "solid" : "flat"}
                  onPress={() => handleMetricChange(option.key)}
                >
                  {option.label}
                </Button>
              ))}
            </div>
          </div>
        </CardBody>
      </Card>

      {data?.meta.hasSamplingGap && (
        <div className="rounded-xl border border-warning-200 bg-warning-50 px-4 py-3 text-sm text-warning-700">
          当前统计区间存在采样缺口，趋势和排行榜可能偏低，请结合节点状态一起判断。
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {buildSummaryCards(isAdmin, data).map((item) => (
          <Card key={item.title} className="border border-gray-200 dark:border-default-200 shadow-md">
            <CardBody className="p-4">
              <div className="text-sm text-default-500">{item.title}</div>
              <div className="mt-2 text-2xl font-semibold text-foreground">{item.value}</div>
              {item.subtitle && <div className="mt-2 text-xs text-default-500">{item.subtitle}</div>}
            </CardBody>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <RankingCard
          title={data?.rankings.leftTitle || "排行榜"}
          rows={data?.rankings.left || []}
          metric={activeMetric}
          emptyText={isAdmin ? "当前时间范围内暂无用户流量数据" : "当前时间范围内暂无隧道流量数据"}
        />
        <RankingCard
          title={data?.rankings.rightTitle || "排行榜"}
          rows={data?.rankings.right || []}
          metric={activeMetric}
          emptyText={isAdmin ? "当前时间范围内暂无转发流量数据" : "当前时间范围内暂无我的转发流量数据"}
        />
      </div>

      <Card className="border border-gray-200 dark:border-default-200 shadow-md">
        <CardHeader className="pb-0">
          <div className="text-lg font-semibold text-foreground">流量趋势</div>
        </CardHeader>
        <CardBody>
          {trendChartData.length === 0 ? (
            <EmptyState text="当前时间范围内暂无趋势数据" />
          ) : (
            <div className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={trendChartData}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.2} />
                  <XAxis dataKey="label" tick={{ fontSize: 12 }} tickLine={false} />
                  <YAxis tick={{ fontSize: 12 }} tickLine={false} tickFormatter={formatFlowAxisTick} />
                  <Tooltip
                    content={({ active, payload, label }) => {
                      if (!active || !payload || !payload.length) {
                        return null;
                      }
                      const point = payload[0]?.payload as FlowChartPoint;
                      if (point.sampled === false) {
                        return (
                          <TooltipBox
                            title={String(label)}
                            rows={[{ label: "状态", value: "该时段无采样数据" }]}
                          />
                        );
                      }
                      return (
                        <TooltipBox
                          title={String(label)}
                          rows={[
                            { label: "总流量", value: formatFlow(point.flow ?? 0) },
                            { label: "入站", value: formatFlow(point.inFlow ?? 0) },
                            { label: "出站", value: formatFlow(point.outFlow ?? 0) },
                          ]}
                        />
                      );
                    }}
                  />
                  <Line
                    type="monotone"
                    dataKey={activeMetric}
                    stroke={METRIC_COLORS[activeMetric]}
                    strokeWidth={3}
                    dot={false}
                    connectNulls={false}
                    activeDot={{ r: 4, strokeWidth: 2 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardBody>
      </Card>

      <Card className="border border-gray-200 dark:border-default-200 shadow-md">
        <CardHeader className="pb-0">
          <div>
            <div className="text-lg font-semibold text-foreground">Top12 转发规则趋势</div>
          </div>
        </CardHeader>
        <CardBody>
          {topRuleChartData.length === 0 || seriesMeta.length === 0 ? (
            <EmptyState text="当前时间范围内暂无规则趋势数据" />
          ) : (
            <div className="h-96">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={topRuleChartData}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis dataKey="label" tick={{ fontSize: 12 }} tickLine={false} />
                  <YAxis tick={{ fontSize: 12 }} tickLine={false} tickFormatter={formatFlowAxisTick} />
                  <Tooltip
                    content={({ active, payload, label }) => {
                      if (!active || !payload) {
                        return null;
                      }

                      const sampled = (payload[0]?.payload as { sampled?: boolean } | undefined)?.sampled;
                      if (sampled === false) {
                        return (
                          <TooltipBox
                            title={String(label)}
                            rows={[{ label: "状态", value: "该时段无采样数据" }]}
                          />
                        );
                      }

                      const rows = payload
                        .filter((item) => item.value !== null && item.value !== undefined)
                        .map((item) => ({
                          label: String(item.name || ""),
                          value: formatFlow(Number(item.value || 0)),
                        }));

                      return <TooltipBox title={String(label)} rows={rows} />;
                    }}
                  />
                  <Legend />
                  {seriesMeta.map((item) => (
                    <Line
                      key={item.key}
                      type="monotone"
                      dataKey={item.key}
                      name={item.userName ? `${item.name} (${item.userName})` : item.name}
                      stroke={item.color}
                      strokeWidth={2}
                      dot={false}
                      connectNulls={false}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

function RankingCard({
  title,
  rows,
  metric,
  emptyText,
}: {
  title: string;
  rows: RankingItem[];
  metric: FlowStatsMetric;
  emptyText: string;
}) {
  const visibleRows = rows.slice(0, MAX_RANKING_ROWS);
  const maxValue = visibleRows.reduce((current, item) => Math.max(current, metricValue(item, metric)), 0);

  return (
    <Card className="border border-gray-200 dark:border-default-200 shadow-md">
      <CardHeader className="pb-0">
        <div className="text-lg font-semibold text-foreground">{title}</div>
      </CardHeader>
      <CardBody>
        {visibleRows.length === 0 ? (
          <EmptyState text={emptyText} />
        ) : (
          <div className="space-y-4">
            {visibleRows.map((item, index) => {
              const value = metricValue(item, metric);
              const width = maxValue > 0 ? `${(value / maxValue) * 100}%` : "0%";
              return (
                <div key={`${title}-${item.id}`} className="space-y-2">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-foreground truncate">
                        {index + 1}. {item.name}
                      </div>
                      {item.secondaryName && <div className="text-xs text-default-500 truncate">{item.secondaryName}</div>}
                    </div>
                    <div className="text-sm font-semibold text-foreground whitespace-nowrap">{formatFlow(value)}</div>
                  </div>
                  <div className="h-2 rounded-full bg-default-100 overflow-hidden">
                    <div className="h-full rounded-full bg-primary" style={{ width }} />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function EmptyState({ text }: { text: string }) {
  return <div className="py-12 text-center text-sm text-default-500">{text}</div>;
}

function TooltipBox({
  title,
  rows,
}: {
  title: string;
  rows: Array<{ label: string; value: string }>;
}) {
  return (
    <div className="bg-white dark:bg-default-100 border border-default-200 rounded-lg shadow-lg p-3 min-w-[180px]">
      <div className="text-sm font-medium text-foreground mb-2">{title}</div>
      <div className="space-y-1">
        {rows.map((row) => (
          <div key={`${title}-${row.label}`} className="flex items-center justify-between gap-3 text-xs text-default-600">
            <span>{row.label}</span>
            <span>{row.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function buildSummaryCards(isAdmin: boolean, data: FlowStatsResponse | null) {
  if (!data) {
    return [];
  }

  if (isAdmin) {
    return [
      {
        title: "用户总数",
        value: formatCount(data.overviewCards.userCount ?? 0),
      },
      {
        title: "转发总数",
        value: formatCount(data.overviewCards.forwardCount ?? 0),
        subtitle: `隧道数 ${formatCount(data.overviewCards.tunnelCount ?? 0)}`,
      },
      {
        title: "当前周期总流量",
        value: formatFlow(data.summary.totalFlow),
        subtitle: `入站 ${formatFlow(data.summary.totalInFlow)} / 出站 ${formatFlow(data.summary.totalOutFlow)}`,
      },
      {
        title: "峰值时段",
        value: formatPeakTime(data.overviewCards.peakBucketTime),
        subtitle: `峰值 ${formatFlow(data.overviewCards.peakBucketValue ?? 0)}`,
      },
    ];
  }

  return [
    {
      title: "套餐总流量",
      value: formatFlowLimit(data.overviewCards.flowLimit),
    },
    {
      title: "当前周期已用",
      value: formatFlow(data.overviewCards.usedFlow ?? data.summary.totalFlow),
      subtitle: `入站 ${formatFlow(data.summary.totalInFlow)} / 出站 ${formatFlow(data.summary.totalOutFlow)}`,
    },
    {
      title: "转发配额 / 已用",
      value: `${formatCount(data.overviewCards.usedForwardCount ?? 0)} / ${formatForwardLimit(data.overviewCards.forwardLimit)}`,
    },
    {
      title: "峰值时段",
      value: formatPeakTime(data.overviewCards.peakBucketTime),
      subtitle: `峰值 ${formatFlow(data.overviewCards.peakBucketValue ?? 0)}`,
    },
  ];
}

function metricValue(item: RankingItem, metric: FlowStatsMetric): number {
  if (metric === "inFlow") {
    return item.inFlow || 0;
  }
  if (metric === "outFlow") {
    return item.outFlow || 0;
  }
  return item.flow || 0;
}

function formatFlow(value: number): string {
  if (value === 99999) {
    return "无限制";
  }
  if (!value) {
    return "0 B";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(2)} KB`;
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / (1024 * 1024)).toFixed(2)} MB`;
  }
  return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatFlowLimit(value?: number): string {
  if (value === undefined || value === null) {
    return "-";
  }
  if (value === 99999) {
    return "无限制";
  }
  return `${value} GB`;
}

function formatForwardLimit(value?: number): string {
  if (value === undefined || value === null) {
    return "-";
  }
  if (value === 99999) {
    return "无限制";
  }
  return String(value);
}

function formatCount(value: number): string {
  return String(value || 0);
}

function formatPeakTime(value?: number | null): string {
  if (!value) {
    return "暂无峰值";
  }
  return new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

function formatFlowAxisTick(value: number): string {
  if (value === 0) {
    return "0";
  }
  if (value < 1024) {
    return `${value}B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)}K`;
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / (1024 * 1024)).toFixed(1)}M`;
  }
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)}G`;
}
