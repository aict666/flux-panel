import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Input, Textarea } from "@heroui/input";
import { Modal, ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { Select, SelectItem } from "@heroui/select";
import { Spinner } from "@heroui/spinner";
import { Switch } from "@heroui/switch";
import { Table, TableBody, TableCell, TableColumn, TableHeader, TableRow } from "@heroui/table";
import { Chip } from "@heroui/chip";
import toast from "react-hot-toast";

import {
  createAgentClient,
  deleteAgentClient,
  getAgentDescriptor,
  listAgentAudits,
  listAgentClients,
  revokeAgentKey,
  rotateAgentClientKey,
  updateAgentClient,
} from "@/api";
import { DeleteIcon, EditIcon, PlusIcon, SearchIcon } from "@/components/icons";
import {
  AGENT_DESCRIPTOR_OPTIONS,
  AGENT_SCOPE_OPTIONS,
  buildAuditFilterPayload,
  groupScopesByDomain,
  initialOneTimeKeyState,
  type AuditFilterFormState,
} from "@/pages/api-management-utils";
import type { AgentAuditLogRecord, AgentClientRecord } from "@/types";
import { isAdmin } from "@/utils/auth";

type AgentType = "openclaw" | "hermes-agent";

interface ClientFormState {
  name: string;
  agentType: AgentType;
  description: string;
  status: number;
  expiresAtLocal: string;
  scopes: string[];
}

const EMPTY_FORM: ClientFormState = {
  name: "",
  agentType: "openclaw",
  description: "",
  status: 1,
  expiresAtLocal: "",
  scopes: ["forwards:read", "stats:read", "descriptors:read"],
};

const scopeDetailMap = new Map<string, (typeof AGENT_SCOPE_OPTIONS)[number]>(
  AGENT_SCOPE_OPTIONS.map((item) => [item.scope, item]),
);

function toDatetimeLocal(timestamp?: number | null) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60_000);
  return local.toISOString().slice(0, 16);
}

function fromDatetimeLocal(value: string) {
  return value ? new Date(value).getTime() : Date.now() + 30 * 24 * 60 * 60 * 1000;
}

function formatDateTime(timestamp?: number | null) {
  if (!timestamp) return "未设置";
  return new Date(timestamp).toLocaleString();
}

export default function ApiManagementPage() {
  const navigate = useNavigate();
  const [clients, setClients] = useState<AgentClientRecord[]>([]);
  const [audits, setAudits] = useState<AgentAuditLogRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [auditLoading, setAuditLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingClient, setEditingClient] = useState<AgentClientRecord | null>(null);
  const [descriptorFormat, setDescriptorFormat] = useState<AgentType>("openclaw");
  const [form, setForm] = useState<ClientFormState>({
    ...EMPTY_FORM,
    expiresAtLocal: toDatetimeLocal(Date.now() + 30 * 24 * 60 * 60 * 1000),
  });
  const [oneTimeKey, setOneTimeKey] = useState(initialOneTimeKeyState());
  const [auditFilters, setAuditFilters] = useState<AuditFilterFormState>({
    success: "all",
  });

  const scopeGroups = useMemo(
    () => groupScopesByDomain(AGENT_SCOPE_OPTIONS.map((item) => item.scope)),
    [],
  );

  useEffect(() => {
    if (!isAdmin()) {
      toast.error("权限不足，只有管理员可以访问此页面");
      navigate("/dashboard", { replace: true });
      return;
    }

    void Promise.all([loadClients(), loadAudits()]);
  }, [navigate]);

  const loadClients = async () => {
    setLoading(true);
    try {
      const response = await listAgentClients();
      if (response.code === 0) {
        setClients((response.data || []) as AgentClientRecord[]);
      } else {
        toast.error(response.msg || "加载服务账号失败");
      }
    } catch (error) {
      toast.error("加载服务账号失败");
    } finally {
      setLoading(false);
    }
  };

  const loadAudits = async (filters: AuditFilterFormState = auditFilters) => {
    setAuditLoading(true);
    try {
      const response = await listAgentAudits(buildAuditFilterPayload(filters));
      if (response.code === 0) {
        setAudits((response.data || []) as AgentAuditLogRecord[]);
      } else {
        toast.error(response.msg || "加载调用日志失败");
      }
    } catch (error) {
      toast.error("加载调用日志失败");
    } finally {
      setAuditLoading(false);
    }
  };

  const resetForm = () => {
    setForm({
      ...EMPTY_FORM,
      expiresAtLocal: toDatetimeLocal(Date.now() + 30 * 24 * 60 * 60 * 1000),
    });
    setEditingClient(null);
  };

  const openCreateModal = () => {
    resetForm();
    setModalOpen(true);
  };

  const openEditModal = (client: AgentClientRecord) => {
    setEditingClient(client);
    setForm({
      name: client.name,
      agentType: client.agentType,
      description: client.description || "",
      status: client.status,
      expiresAtLocal: toDatetimeLocal(client.expiresTime || Date.now() + 30 * 24 * 60 * 60 * 1000),
      scopes: client.scopes || [],
    });
    setModalOpen(true);
  };

  const toggleScope = (scope: string, enabled: boolean) => {
    setForm((current) => {
      const scopes = new Set(current.scopes);
      if (enabled) {
        scopes.add(scope);
      } else {
        scopes.delete(scope);
      }

      return {
        ...current,
        scopes: [...scopes].sort((left, right) => left.localeCompare(right)),
      };
    });
  };

  const handleSubmit = async () => {
    if (!form.name.trim()) {
      toast.error("请输入服务账号名称");
      return;
    }
    if (form.scopes.length === 0) {
      toast.error("至少选择一个 scope");
      return;
    }

    setSubmitting(true);
    try {
      if (editingClient) {
        const response = await updateAgentClient({
          id: editingClient.id,
          name: form.name.trim(),
          description: form.description.trim(),
          status: form.status,
          scopes: form.scopes,
        });

        if (response.code !== 0) {
          toast.error(response.msg || "更新服务账号失败");
          return;
        }

        toast.success("服务账号已更新");
      } else {
        const response = await createAgentClient({
          name: form.name.trim(),
          agentType: form.agentType,
          description: form.description.trim(),
          scopes: form.scopes,
          expiresTime: fromDatetimeLocal(form.expiresAtLocal),
        });

        if (response.code !== 0) {
          toast.error(response.msg || "创建服务账号失败");
          return;
        }

        setOneTimeKey({
          visible: true,
          value: response.data?.plaintextKey || "",
          clientName: form.name.trim(),
        });
        toast.success("服务账号已创建");
      }

      setModalOpen(false);
      resetForm();
      await Promise.all([loadClients(), loadAudits()]);
    } catch (error) {
      toast.error("提交服务账号失败");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (client: AgentClientRecord) => {
    if (!window.confirm(`确认停用服务账号 ${client.name} 吗？其所有 key 会一并失效。`)) {
      return;
    }

    const response = await deleteAgentClient(client.id);
    if (response.code === 0) {
      toast.success("服务账号已停用");
      await Promise.all([loadClients(), loadAudits()]);
    } else {
      toast.error(response.msg || "停用服务账号失败");
    }
  };

  const handleRotateKey = async (client: AgentClientRecord) => {
    const response = await rotateAgentClientKey(client.id, {
      expiresTime: client.expiresTime || Date.now() + 30 * 24 * 60 * 60 * 1000,
    });

    if (response.code === 0) {
      setOneTimeKey({
        visible: true,
        value: response.data?.plaintextKey || "",
        clientName: client.name,
      });
      toast.success("密钥已轮换");
      await Promise.all([loadClients(), loadAudits()]);
    } else {
      toast.error(response.msg || "轮换密钥失败");
    }
  };

  const handleRevokeKey = async (client: AgentClientRecord) => {
    if (!client.keyId) {
      toast.error("当前服务账号没有可吊销的 key");
      return;
    }

    const response = await revokeAgentKey(client.keyId);
    if (response.code === 0) {
      toast.success("密钥已吊销");
      await Promise.all([loadClients(), loadAudits()]);
    } else {
      toast.error(response.msg || "吊销密钥失败");
    }
  };

  const handleDescriptorDownload = async (client: AgentClientRecord) => {
    const response = await getAgentDescriptor(client.id, descriptorFormat);
    if (response.code !== 0) {
      toast.error(response.msg || "获取 descriptor 失败");
      return;
    }

    const blob = new Blob([JSON.stringify(response.data, null, 2)], { type: "application/json" });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${client.name}-${descriptorFormat}-descriptor.json`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.URL.revokeObjectURL(url);
  };

  const copyOneTimeKey = async () => {
    if (!oneTimeKey.value) return;
    await navigator.clipboard.writeText(oneTimeKey.value);
    toast.success("密钥已复制");
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold">API 管理</h1>
            <p className="text-sm text-default-500">管理 Agent 服务账号、API Key、descriptor 与调用日志。</p>
          </div>
          <div className="flex items-center gap-3">
            <Select
              className="w-44"
              label="Descriptor 格式"
              selectedKeys={[descriptorFormat]}
              onSelectionChange={(keys) => {
                const [value] = Array.from(keys);
                if (value) {
                  setDescriptorFormat(String(value) as AgentType);
                }
              }}
            >
              {AGENT_DESCRIPTOR_OPTIONS.map((item) => (
                <SelectItem key={item.value}>{item.label}</SelectItem>
              ))}
            </Select>
            <Button color="primary" startContent={<PlusIcon size={18} />} onPress={openCreateModal}>
              新建服务账号
            </Button>
          </div>
        </CardHeader>
      </Card>

      {oneTimeKey.visible && (
        <Card className="border border-warning/30 bg-warning/5">
          <CardHeader className="flex items-center justify-between">
            <div>
              <h2 className="font-semibold">一次性密钥</h2>
              <p className="text-sm text-default-500">{oneTimeKey.clientName} 的新 key 只会显示这一回。</p>
            </div>
            <Button size="sm" variant="flat" color="warning" onPress={copyOneTimeKey}>
              复制
            </Button>
          </CardHeader>
          <CardBody>
            <div className="rounded-xl bg-black px-4 py-3 font-mono text-sm text-success-300 break-all">
              {oneTimeKey.value}
            </div>
          </CardBody>
        </Card>
      )}

      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-semibold">服务账号列表</h2>
            <p className="text-sm text-default-500">创建后会自动生成首个 API Key，后续可轮换、吊销并下载 descriptor。</p>
          </div>
          {loading && <Spinner size="sm" />}
        </CardHeader>
        <CardBody className="space-y-4">
          {clients.length === 0 && !loading ? (
            <div className="rounded-xl border border-dashed border-default-300 px-4 py-8 text-center text-default-500">
              暂无服务账号
            </div>
          ) : (
            clients.map((client) => (
              <div key={client.id} className="rounded-2xl border border-default-200 p-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <h3 className="text-lg font-semibold">{client.name}</h3>
                      <Chip size="sm" color={client.status === 1 ? "success" : "danger"} variant="flat">
                        {client.status === 1 ? "启用" : "停用"}
                      </Chip>
                      <Chip size="sm" variant="flat">
                        {client.agentType}
                      </Chip>
                    </div>
                    <p className="text-sm text-default-500">{client.description || "暂无描述"}</p>
                    <div className="flex flex-wrap gap-2">
                      {client.scopes?.map((scope) => (
                        <Chip key={scope} size="sm" variant="flat" color="primary">
                          {scope}
                        </Chip>
                      ))}
                    </div>
                    <div className="text-xs text-default-400">
                      <div>Key 前缀：{client.keyPrefix || "未生成"}</div>
                      <div>过期时间：{formatDateTime(client.expiresTime)}</div>
                      <div>最近使用：{formatDateTime(client.lastUsedTime)} {client.lastUsedIp ? `· ${client.lastUsedIp}` : ""}</div>
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="flat" startContent={<EditIcon size={16} />} onPress={() => openEditModal(client)}>
                      编辑
                    </Button>
                    <Button size="sm" variant="flat" color="primary" onPress={() => handleRotateKey(client)}>
                      轮换 Key
                    </Button>
                    <Button size="sm" variant="flat" color="warning" onPress={() => handleDescriptorDownload(client)}>
                      下载 Descriptor
                    </Button>
                    <Button size="sm" variant="flat" color="secondary" onPress={() => handleRevokeKey(client)}>
                      吊销 Key
                    </Button>
                    <Button size="sm" variant="flat" color="danger" startContent={<DeleteIcon size={16} />} onPress={() => handleDelete(client)}>
                      停用
                    </Button>
                  </div>
                </div>
              </div>
            ))
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-semibold">调用日志</h2>
            <p className="text-sm text-default-500">按服务账号、成功状态和时间范围筛选最近的 Agent 请求。</p>
          </div>
          {auditLoading && <Spinner size="sm" />}
        </CardHeader>
        <CardBody className="space-y-4">
          <div className="grid gap-4 md:grid-cols-4">
            <Select
              label="服务账号"
              selectedKeys={auditFilters.clientId != null ? [String(auditFilters.clientId)] : []}
              onSelectionChange={(keys) => {
                const [value] = Array.from(keys);
                setAuditFilters((current) => ({
                  ...current,
                  clientId: value ? Number(value) : undefined,
                }));
              }}
            >
              {clients.map((client) => (
                <SelectItem key={String(client.id)}>{client.name}</SelectItem>
              ))}
            </Select>
            <Select
              label="结果"
              selectedKeys={[auditFilters.success || "all"]}
              onSelectionChange={(keys) => {
                const [value] = Array.from(keys);
                setAuditFilters((current) => ({
                  ...current,
                  success: (value || "all") as AuditFilterFormState["success"],
                }));
              }}
            >
              <SelectItem key="all">全部</SelectItem>
              <SelectItem key="true">成功</SelectItem>
              <SelectItem key="false">失败</SelectItem>
            </Select>
            <Input
              label="开始时间"
              type="datetime-local"
              value={toDatetimeLocal(auditFilters.startTime)}
              onValueChange={(value) => {
                setAuditFilters((current) => ({
                  ...current,
                  startTime: value ? new Date(value).getTime() : undefined,
                }));
              }}
            />
            <Input
              label="结束时间"
              type="datetime-local"
              value={toDatetimeLocal(auditFilters.endTime)}
              onValueChange={(value) => {
                setAuditFilters((current) => ({
                  ...current,
                  endTime: value ? new Date(value).getTime() : undefined,
                }));
              }}
            />
          </div>

          <div className="flex justify-end">
            <Button color="primary" variant="flat" startContent={<SearchIcon size={16} />} onPress={() => void loadAudits()}>
              查询日志
            </Button>
          </div>

          <Table aria-label="Agent 调用日志表">
            <TableHeader>
              <TableColumn>时间</TableColumn>
              <TableColumn>服务账号</TableColumn>
              <TableColumn>动作</TableColumn>
              <TableColumn>请求</TableColumn>
              <TableColumn>状态</TableColumn>
              <TableColumn>耗时</TableColumn>
              <TableColumn>来源</TableColumn>
            </TableHeader>
            <TableBody emptyContent={auditLoading ? "正在加载..." : "暂无调用日志"}>
              {audits.map((audit) => (
                <TableRow key={audit.id}>
                  <TableCell>{formatDateTime(audit.createdTime)}</TableCell>
                  <TableCell>{audit.clientName || "-"}</TableCell>
                  <TableCell>{audit.action}</TableCell>
                  <TableCell>{audit.httpMethod} {audit.requestPath}</TableCell>
                  <TableCell>
                    <Chip size="sm" color={audit.success ? "success" : "danger"} variant="flat">
                      {audit.statusCode}
                    </Chip>
                  </TableCell>
                  <TableCell>{audit.durationMs} ms</TableCell>
                  <TableCell>{audit.requestIp || "-"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardBody>
      </Card>

      <Modal isOpen={modalOpen} onOpenChange={setModalOpen} size="4xl">
        <ModalContent>
          <ModalHeader>{editingClient ? "编辑服务账号" : "新建服务账号"}</ModalHeader>
          <ModalBody className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <Input
                label="服务账号名称"
                value={form.name}
                onValueChange={(value) => setForm((current) => ({ ...current, name: value }))}
              />
              <Select
                label="Agent 类型"
                isDisabled={Boolean(editingClient)}
                selectedKeys={[form.agentType]}
                onSelectionChange={(keys) => {
                  const [value] = Array.from(keys);
                  if (value) {
                    setForm((current) => ({ ...current, agentType: String(value) as AgentType }));
                  }
                }}
              >
                {AGENT_DESCRIPTOR_OPTIONS.map((item) => (
                  <SelectItem key={item.value}>{item.label}</SelectItem>
                ))}
              </Select>
            </div>

            <Textarea
              label="描述"
              minRows={2}
              value={form.description}
              onValueChange={(value) => setForm((current) => ({ ...current, description: value }))}
            />

            {!editingClient && (
              <Input
                label="Key 过期时间"
                type="datetime-local"
                value={form.expiresAtLocal}
                onValueChange={(value) => setForm((current) => ({ ...current, expiresAtLocal: value }))}
              />
            )}

            {editingClient && (
              <div className="rounded-xl border border-default-200 px-4 py-3">
                <Switch
                  isSelected={form.status === 1}
                  onValueChange={(checked) => setForm((current) => ({ ...current, status: checked ? 1 : 0 }))}
                >
                  服务账号启用
                </Switch>
              </div>
            )}

            <div className="space-y-4">
              <div>
                <h3 className="font-semibold">Scope 权限</h3>
                <p className="text-sm text-default-500">按域勾选给 Agent 开放的能力，descriptor 也会基于这些 scope 自动裁剪。</p>
              </div>
              <div className="space-y-4">
                {scopeGroups.map((group) => (
                  <div key={group.domain} className="rounded-xl border border-default-200 p-4">
                    <h4 className="mb-3 text-sm font-semibold uppercase tracking-wide text-default-500">{group.domain}</h4>
                    <div className="grid gap-3 md:grid-cols-2">
                      {group.scopes.map((scope) => {
                        const detail = scopeDetailMap.get(scope);
                        return (
                          <div key={scope} className="rounded-xl bg-default-50 px-3 py-3">
                            <Switch
                              isSelected={form.scopes.includes(scope)}
                              onValueChange={(checked) => toggleScope(scope, checked)}
                            >
                              {detail?.label || scope}
                            </Switch>
                            <p className="mt-2 text-xs text-default-500">{detail?.description || scope}</p>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </ModalBody>
          <ModalFooter>
            <Button variant="flat" onPress={() => setModalOpen(false)}>
              取消
            </Button>
            <Button color="primary" isLoading={submitting} onPress={handleSubmit}>
              {editingClient ? "保存修改" : "创建账号"}
            </Button>
          </ModalFooter>
        </ModalContent>
      </Modal>
    </div>
  );
}
