import { describe, expect, it } from "vitest";

import {
  buildForwardTableRows,
  formatForwardTotalFlow,
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
    expect(rows[0].isMultiTarget).toBe(true);
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
