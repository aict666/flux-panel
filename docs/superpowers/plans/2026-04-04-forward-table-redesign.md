# Forward Table Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the forward page card wall with a searchable, filterable table-first management view aligned with the approved redesign spec.

**Architecture:** Keep the existing forward CRUD and modal workflows, but replace the list rendering path with a derived table view model that supports search, filters, pagination, mobile detail modal, and batch actions. Extract pure data-shaping helpers so the UI refactor has a small, testable core instead of burying all logic inside the page component.

**Tech Stack:** React 18, TypeScript, Vite, HeroUI Table/Modal/Input/Select/Button, react-hot-toast

---

### Task 1: Add table-view data helpers and regression tests

**Files:**
- Create: `vite-frontend/src/pages/forward-table-utils.ts`
- Create: `vite-frontend/src/pages/forward-table-utils.test.ts`
- Modify: `vite-frontend/package.json`

- [x] **Step 1: Write the failing tests for filtering, pagination, and flow formatting**

```ts
import { describe, expect, it } from "vitest";
import {
  formatForwardTotalFlow,
  buildForwardTableRows,
  paginateForwardRows,
} from "./forward-table-utils";

const sampleForwards = [
  {
    id: 1,
    name: "OutMCN - 副本",
    tunnelId: 10,
    tunnelName: "主隧道",
    inIp: "1.1.1.1",
    inPort: 10001,
    remoteAddr: "8.8.8.8:443,9.9.9.9:443",
    strategy: "fifo",
    status: 1,
    inFlow: 1024,
    outFlow: 2048,
    createdTime: "2026-04-04 00:00:00",
    serviceRunning: true,
    userName: "alice",
    userId: 7,
  },
  {
    id: 2,
    name: "Beta",
    tunnelId: 11,
    tunnelName: "备用隧道",
    inIp: "2.2.2.2",
    inPort: 10002,
    remoteAddr: "7.7.7.7:80",
    strategy: "round",
    status: 0,
    inFlow: 0,
    outFlow: 0,
    createdTime: "2026-04-04 00:00:00",
    serviceRunning: false,
    userName: "bob",
    userId: 8,
  },
];

describe("forward-table-utils", () => {
  it("formats total flow from inFlow and outFlow", () => {
    expect(formatForwardTotalFlow(sampleForwards[0])).toBe("3.00 KB");
  });

  it("filters by keyword, status, strategy, and multi-target flag", () => {
    const rows = buildForwardTableRows(sampleForwards, {
      search: "outmcn",
      status: "all",
      strategy: "fifo",
      tunnelId: "all",
      userId: "all",
      multiTargetOnly: true,
    });

    expect(rows).toHaveLength(1);
    expect(rows[0].id).toBe(1);
  });

  it("paginates rows with stable slicing", () => {
    const rows = buildForwardTableRows(sampleForwards, {
      search: "",
      status: "all",
      strategy: "all",
      tunnelId: "all",
      userId: "all",
      multiTargetOnly: false,
    });

    expect(paginateForwardRows(rows, 1, 1).map((row) => row.id)).toEqual([1]);
    expect(paginateForwardRows(rows, 2, 1).map((row) => row.id)).toEqual([2]);
  });
});
```

- [x] **Step 2: Add a runnable test command and test dependencies**

```json
{
  "scripts": {
    "test": "vitest run"
  },
  "devDependencies": {
    "vitest": "^2.1.9"
  }
}
```

Run: `npm install --legacy-peer-deps`
Expected: install completes and `vitest` becomes available in `node_modules`

- [x] **Step 3: Run the tests to verify RED**

Run: `npm test -- src/pages/forward-table-utils.test.ts`
Expected: FAIL because `forward-table-utils.ts` does not exist yet

- [x] **Step 4: Implement the minimal helper module**

```ts
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
  tunnelName: string;
  userName: string;
  status: number;
  strategy: string;
  inAddress: string;
  remoteAddress: string;
  totalFlowDisplay: string;
  speedDisplay: string;
  isMultiTarget: boolean;
}

export function formatForwardTotalFlow(forward: {
  inFlow: number;
  outFlow: number;
}): string {
  const total = (forward.inFlow || 0) + (forward.outFlow || 0);
  if (total === 0) return "0 B";
  if (total < 1024) return `${total} B`;
  if (total < 1024 * 1024) return `${(total / 1024).toFixed(2)} KB`;
  if (total < 1024 * 1024 * 1024) return `${(total / (1024 * 1024)).toFixed(2)} MB`;
  return `${(total / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function paginateForwardRows<T>(rows: T[], page: number, pageSize: number): T[] {
  const start = (page - 1) * pageSize;
  return rows.slice(start, start + pageSize);
}
```

- [x] **Step 5: Run the helper tests to verify GREEN**

Run: `npm test -- src/pages/forward-table-utils.test.ts`
Expected: PASS

- [x] **Step 6: Commit**

```bash
git add vite-frontend/package.json vite-frontend/src/pages/forward-table-utils.ts vite-frontend/src/pages/forward-table-utils.test.ts
git commit -m "test: add forward table view helpers"
```

### Task 2: Replace the forward page card wall with a table-first management UI

**Files:**
- Modify: `vite-frontend/src/pages/forward.tsx`

- [x] **Step 1: Add the failing UI assertion via the helper-backed workflow**

Use the new helper functions in `forward.tsx` before wiring the table, then run:

Run: `npm run build`
Expected: FAIL if `forward.tsx` still references removed grouped/direct drag structures or missing helper imports

- [x] **Step 2: Remove obsolete list-mode state and drag-and-drop behavior**

Delete:

```ts
const [viewMode, setViewMode] = useState<'grouped' | 'direct'>(...);
const [forwardOrder, setForwardOrder] = useState<number[]>([]);
const handleViewModeChange = () => { ... };
const handleDragEnd = async (event: DragEndEvent) => { ... };
const sensors = useSensors(...);
const groupForwardsByUserAndTunnel = (): UserGroup[] => { ... };
const SortableForwardCard = ({ forward }: { forward: Forward }) => { ... };
const renderForwardCard = (forward: Forward, listeners?: any) => { ... };
```

Also remove the now-unused imports from `@dnd-kit/*`, `CardHeader`, `Accordion`, and any grouped-mode-only types.

- [x] **Step 3: Add the new table-state layer**

Add page state shaped like:

```ts
const PAGE_SIZE = 10;

const [searchValue, setSearchValue] = useState("");
const [statusFilter, setStatusFilter] = useState<ForwardTableFilterState["status"]>("all");
const [strategyFilter, setStrategyFilter] = useState<ForwardTableFilterState["strategy"]>("all");
const [tunnelFilter, setTunnelFilter] = useState<"all" | number>("all");
const [userFilter, setUserFilter] = useState<"all" | number>("all");
const [multiTargetOnly, setMultiTargetOnly] = useState(false);
const [currentPage, setCurrentPage] = useState(1);
const [selectedForwardIds, setSelectedForwardIds] = useState<Set<number>>(new Set());
const [detailForward, setDetailForward] = useState<Forward | null>(null);
```

Derive rows with:

```ts
const filterState = {
  search: searchValue,
  status: statusFilter,
  strategy: strategyFilter,
  tunnelId: tunnelFilter,
  userId: userFilter,
  multiTargetOnly,
};

const filteredRows = buildForwardTableRows(forwards, filterState);
const pagedRows = paginateForwardRows(filteredRows, currentPage, PAGE_SIZE);
```

- [x] **Step 4: Replace the page header and table body**

Render:

```tsx
<div className="space-y-4">
  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
    <div>
      <h1 className="text-2xl font-semibold text-foreground">转发管理</h1>
      <p className="text-sm text-default-500">用表格统一管理规则、状态和批量操作</p>
    </div>
    <div className="flex gap-2">
      <Button color="primary" variant="shadow" onPress={handleImport}>批量导入</Button>
      <Button color="primary" onPress={handleAdd}>添加转发</Button>
    </div>
  </div>

  <Card className="border border-divider shadow-sm">
    <CardBody className="gap-4">
      <Input value={searchValue} onValueChange={setSearchValue} placeholder="搜索转发规则..." />
      {/* status / strategy / more filters */}
      {/* batch toolbar when selectedForwardIds.size > 0 */}
      <Table aria-label="转发规则表格" removeWrapper>
        {/* desktop columns + mobile compact cells */}
      </Table>
      <Pagination page={currentPage} total={Math.max(1, Math.ceil(filteredRows.length / PAGE_SIZE))} onChange={setCurrentPage} />
    </CardBody>
  </Card>
</div>
```

The table must include:

- selection checkbox column
- enable switch column
- rule name pill
- flow statistics dual-line cell
- status chip
- icon action group

- [x] **Step 5: Wire batch actions and mobile detail modal**

Implement:

```ts
const selectedForwards = forwards.filter((forward) => selectedForwardIds.has(forward.id));

const handleBatchDelete = async () => { /* iterate selected IDs with deleteForward / forceDeleteForward */ };
const handleBatchToggle = async (target: "start" | "pause") => { /* iterate selected IDs with existing pause/resume APIs */ };
const handleBatchExport = () => { /* format selected rows into export modal text */ };
```

On mobile:

```tsx
<Modal isOpen={!!detailForward} onOpenChange={(open) => !open && setDetailForward(null)}>
  {/* tunnel, inAddress, remoteAddress, strategy, status, createdTime */}
</Modal>
```

- [x] **Step 6: Run the app build to verify GREEN**

Run: `npm run build`
Expected: PASS

- [x] **Step 7: Commit**

```bash
git add vite-frontend/src/pages/forward.tsx
git commit -m "feat: redesign forward management as table view"
```

### Task 3: Final regression pass and documentation sync

**Files:**
- Modify: `docs/superpowers/plans/2026-04-04-forward-table-redesign.md`

- [x] **Step 1: Re-run focused tests**

Run: `npm test -- src/pages/forward-table-utils.test.ts`
Expected: PASS

- [x] **Step 2: Re-run production build**

Run: `npm run build`
Expected: PASS with only pre-existing non-blocking Vite warnings

- [x] **Step 3: Mark this plan complete in-place**

Update the checkbox states in this plan file to reflect the executed steps.

- [x] **Step 4: Commit**

```bash
git add docs/superpowers/plans/2026-04-04-forward-table-redesign.md
git commit -m "docs: mark forward table redesign plan complete"
```
