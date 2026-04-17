export const AGENT_DESCRIPTOR_OPTIONS = [
  { label: "OpenClaw", value: "openclaw" },
  { label: "Hermes Agent", value: "hermes-agent" },
] as const;

export const AGENT_SCOPE_OPTIONS = [
  { scope: "users:read", label: "用户读取", description: "读取用户列表与详情" },
  { scope: "users:write", label: "用户写入", description: "创建和更新用户" },
  { scope: "users:delete", label: "用户删除", description: "删除用户" },
  { scope: "user-permissions:read", label: "权限读取", description: "读取用户隧道权限" },
  { scope: "user-permissions:write", label: "权限写入", description: "分配、更新和移除用户隧道权限" },
  { scope: "tunnels:read", label: "隧道读取", description: "读取隧道列表与详情" },
  { scope: "tunnels:write", label: "隧道写入", description: "创建和更新隧道" },
  { scope: "tunnels:delete", label: "隧道删除", description: "删除隧道" },
  { scope: "tunnels:diagnose", label: "隧道诊断", description: "执行隧道诊断" },
  { scope: "forwards:read", label: "转发读取", description: "读取转发列表" },
  { scope: "forwards:write", label: "转发写入", description: "创建和更新转发" },
  { scope: "forwards:delete", label: "转发删除", description: "删除和强制删除转发" },
  { scope: "forwards:control", label: "转发控制", description: "暂停和恢复转发" },
  { scope: "forwards:diagnose", label: "转发诊断", description: "执行转发诊断" },
  { scope: "forwards:reorder", label: "转发排序", description: "更新转发排序" },
  { scope: "stats:read", label: "统计读取", description: "读取统计总览、序列和小时明细" },
  { scope: "descriptors:read", label: "描述导出", description: "读取 Agent descriptor" },
] as const;

export interface AuditFilterFormState {
  clientId?: number;
  success?: "all" | "true" | "false";
  startTime?: number;
  endTime?: number;
}

export interface OneTimeKeyState {
  visible: boolean;
  value: string;
  clientName: string;
}

export function groupScopesByDomain(scopes: string[]) {
  const grouped = new Map<string, string[]>();

  [...scopes]
    .sort((left, right) => left.localeCompare(right))
    .forEach((scope) => {
      const [domain] = scope.split(":");
      const existing = grouped.get(domain) ?? [];
      existing.push(scope);
      grouped.set(domain, existing);
    });

  return [...grouped.entries()].map(([domain, domainScopes]) => ({
    domain,
    scopes: domainScopes,
  }));
}

export function buildAuditFilterPayload(filters: AuditFilterFormState) {
  const payload: Record<string, unknown> = {};

  if (filters.clientId != null) {
    payload.clientId = filters.clientId;
  }

  if (filters.success === "true") {
    payload.success = true;
  } else if (filters.success === "false") {
    payload.success = false;
  }

  if (filters.startTime != null) {
    payload.startTime = filters.startTime;
  }

  if (filters.endTime != null) {
    payload.endTime = filters.endTime;
  }

  return payload;
}

export function initialOneTimeKeyState(): OneTimeKeyState {
  return {
    visible: false,
    value: "",
    clientName: "",
  };
}
