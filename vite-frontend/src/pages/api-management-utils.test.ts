import { describe, expect, it } from "vitest";

import {
  AGENT_DESCRIPTOR_OPTIONS,
  buildAuditFilterPayload,
  groupScopesByDomain,
  initialOneTimeKeyState,
} from "@/pages/api-management-utils";

describe("api-management-utils", () => {
  it("groups scopes by top-level domain", () => {
    const groups = groupScopesByDomain([
      "users:read",
      "users:write",
      "forwards:read",
      "stats:read",
    ]);

    expect(groups).toEqual([
      { domain: "forwards", scopes: ["forwards:read"] },
      { domain: "stats", scopes: ["stats:read"] },
      { domain: "users", scopes: ["users:read", "users:write"] },
    ]);
  });

  it("builds audit filter payload with only defined filters", () => {
    expect(
      buildAuditFilterPayload({
        clientId: 8,
        success: "true",
        startTime: 1000,
        endTime: undefined,
      }),
    ).toEqual({
      clientId: 8,
      success: true,
      startTime: 1000,
    });
  });

  it("exposes descriptor options for both supported agent types", () => {
    expect(AGENT_DESCRIPTOR_OPTIONS).toEqual([
      { label: "OpenClaw", value: "openclaw" },
      { label: "Hermes Agent", value: "hermes-agent" },
    ]);
  });

  it("starts one-time key state as hidden and empty", () => {
    expect(initialOneTimeKeyState()).toEqual({
      visible: false,
      value: "",
      clientName: "",
    });
  });
});
