export interface ForwardTableSource {
  id: number;
  name: string;
  tunnelId: number;
  tunnelName?: string;
  inIp: string;
  inPort: number;
  remoteAddr: string;
  strategy: string;
  status: number;
  inFlow: number;
  outFlow: number;
  createdTime: string;
  serviceRunning: boolean;
  userName?: string;
  userId?: number;
  inx?: number;
}

export interface ForwardTableFilterState {
  search: string;
  status: "all" | "active" | "paused" | "error";
  strategy: "all" | "fifo" | "round" | "rand";
  tunnelId: "all" | number;
  userId: "all" | number;
  multiTargetOnly: boolean;
}

export interface ForwardTableRow {
  id: number;
  name: string;
  tunnelId: number;
  tunnelName: string;
  userId: number | null;
  userName: string;
  status: number;
  strategy: string;
  inAddress: string;
  remoteAddress: string;
  totalFlowDisplay: string;
  speedDisplay: string;
  isMultiTarget: boolean;
  createdTime: string;
}

export const FORWARD_PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
export const DEFAULT_FORWARD_PAGE_SIZE = FORWARD_PAGE_SIZE_OPTIONS[0];

const FLOW_KB = 1024;
const FLOW_MB = FLOW_KB * 1024;
const FLOW_GB = FLOW_MB * 1024;

export function normalizeForwardPageSize(
  value: number | string | null | undefined,
): number {
  const parsedValue =
    typeof value === "string" ? Number.parseInt(value, 10) : value;

  if (
    typeof parsedValue === "number" &&
    FORWARD_PAGE_SIZE_OPTIONS.includes(
      parsedValue as (typeof FORWARD_PAGE_SIZE_OPTIONS)[number],
    )
  ) {
    return parsedValue;
  }

  return DEFAULT_FORWARD_PAGE_SIZE;
}

export function formatForwardTotalFlow(forward: Pick<ForwardTableSource, "inFlow" | "outFlow">): string {
  const total = (forward.inFlow || 0) + (forward.outFlow || 0);

  if (total === 0) return "0 B";
  if (total < FLOW_KB) return `${total} B`;
  if (total < FLOW_MB) return `${(total / FLOW_KB).toFixed(2)} KB`;
  if (total < FLOW_GB) return `${(total / FLOW_MB).toFixed(2)} MB`;

  return `${(total / FLOW_GB).toFixed(2)} GB`;
}

export function paginateForwardRows<T>(rows: T[], page: number, pageSize: number): T[] {
  const safePage = Math.max(page, 1);
  const safePageSize = Math.max(pageSize, 1);
  const startIndex = (safePage - 1) * safePageSize;

  return rows.slice(startIndex, startIndex + safePageSize);
}

export function retainSelectedForwardIdsOnPage(
  selectedIds: Iterable<number>,
  rows: Array<Pick<ForwardTableRow, "id">>,
): Set<number> {
  const visibleIds = new Set(rows.map((row) => row.id));

  return new Set(
    Array.from(selectedIds).filter((selectedId) => visibleIds.has(selectedId)),
  );
}

function formatInAddress(inIp: string, port: number): string {
  const items = inIp.split(",").map((item) => item.trim()).filter(Boolean);
  if (items.length === 0) return "";

  const first = items[0];
  const firstHasPort = /:\d+$/.test(first);

  if (firstHasPort) {
    return items.length === 1 ? first : `${first} (+${items.length - 1}个)`;
  }

  const normalized = first.includes(":") && !first.startsWith("[") ? `[${first}]` : first;

  return items.length === 1 ? `${normalized}:${port}` : `${normalized}:${port} (+${items.length - 1}个)`;
}

function formatRemoteAddress(remoteAddr: string): string {
  const items = remoteAddr.split(",").map((item) => item.trim()).filter(Boolean);
  if (items.length === 0) return "";
  return items.length === 1 ? items[0] : `${items[0]} (+${items.length - 1})`;
}

function isStatusMatch(status: number, filter: ForwardTableFilterState["status"]): boolean {
  if (filter === "all") return true;
  if (filter === "active") return status === 1;
  if (filter === "paused") return status === 0;
  return status === -1;
}

function matchesSearch(forward: ForwardTableSource, search: string): boolean {
  if (!search.trim()) return true;

  const normalizedSearch = search.trim().toLowerCase();
  const haystacks = [
    forward.name,
    forward.tunnelName || "",
    forward.inIp,
    `${forward.inIp}:${forward.inPort}`,
    forward.remoteAddr,
    forward.userName || "",
  ];

  return haystacks.some((value) => value.toLowerCase().includes(normalizedSearch));
}

export function buildForwardTableRows(
  forwards: ForwardTableSource[],
  filters: ForwardTableFilterState,
): ForwardTableRow[] {
  return [...forwards]
    .sort((left, right) => {
      const leftOrder = left.inx ?? Number.MAX_SAFE_INTEGER;
      const rightOrder = right.inx ?? Number.MAX_SAFE_INTEGER;
      if (leftOrder !== rightOrder) return leftOrder - rightOrder;
      return left.id - right.id;
    })
    .filter((forward) => matchesSearch(forward, filters.search))
    .filter((forward) => isStatusMatch(forward.status, filters.status))
    .filter((forward) => filters.strategy === "all" || forward.strategy === filters.strategy)
    .filter((forward) => filters.tunnelId === "all" || forward.tunnelId === filters.tunnelId)
    .filter((forward) => filters.userId === "all" || (forward.userId ?? null) === filters.userId)
    .map((forward) => {
      const targetCount = forward.remoteAddr.split(",").map((item) => item.trim()).filter(Boolean).length;

      return {
        id: forward.id,
        name: forward.name,
        tunnelId: forward.tunnelId,
        tunnelName: forward.tunnelName || "-",
        userId: forward.userId ?? null,
        userName: forward.userName || "-",
        status: forward.status,
        strategy: forward.strategy,
        inAddress: formatInAddress(forward.inIp, forward.inPort),
        remoteAddress: formatRemoteAddress(forward.remoteAddr),
        totalFlowDisplay: formatForwardTotalFlow(forward),
        speedDisplay: "--",
        isMultiTarget: targetCount > 1,
        createdTime: forward.createdTime,
      };
    })
    .filter((row) => !filters.multiTargetOnly || row.isMultiTarget);
}
