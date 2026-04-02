import { useEffect, useState } from "react";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Button } from "@heroui/button";
import { Input, Textarea } from "@heroui/input";
import { Select, SelectItem } from "@heroui/select";
import { Modal, ModalContent, ModalHeader, ModalBody, ModalFooter } from "@heroui/modal";
import { Chip } from "@heroui/chip";
import { Spinner } from "@heroui/spinner";
import { Divider } from "@heroui/divider";
import toast from "react-hot-toast";

import {
  createTunnel,
  deleteTunnel,
  diagnoseTunnel,
  getNodeList,
  getTunnelList,
  updateTunnel,
} from "@/api";

type HopMode = "node" | "tunnel";

interface TunnelTopologyItem {
  itemType: HopMode;
  nodeId?: number;
  refTunnelId?: number;
  refTunnelName?: string;
  protocol?: string;
  strategy?: string;
  hopIndex?: number;
}

interface Tunnel {
  id: number;
  name: string;
  type: number;
  inNodeId: TunnelTopologyItem[];
  outNodeId: TunnelTopologyItem[];
  chainNodes: TunnelTopologyItem[][];
  inIp: string;
  flow: number;
  trafficRatio: number;
  status: number;
  createdTime: string;
}

interface Node {
  id: number;
  name: string;
  status: number;
}

interface TunnelForm {
  id?: number;
  name: string;
  type: number;
  inNodeId: TunnelTopologyItem[];
  outNodeId: TunnelTopologyItem[];
  chainNodes: TunnelTopologyItem[][];
  flow: number;
  trafficRatio: number;
  inIp: string;
  status: number;
}

interface DiagnosisResult {
  tunnelName: string;
  tunnelType: string;
  timestamp: number;
  results: Array<{
    success: boolean;
    description: string;
    nodeName: string;
    nodeId: string;
    targetIp: string;
    targetPort?: number;
    message?: string;
    averageTime?: number;
    packetLoss?: number;
    fromChainType?: number;
    fromInx?: number;
    toChainType?: number;
    toInx?: number;
  }>;
}

const PROTOCOL_OPTIONS = [
  { key: "tls", label: "TLS" },
  { key: "wss", label: "WSS" },
  { key: "tcp", label: "TCP" },
  { key: "mtls", label: "MTLS" },
  { key: "mwss", label: "MWSS" },
  { key: "mtcp", label: "MTCP" },
];

const STRATEGY_OPTIONS = [
  { key: "fifo", label: "主备" },
  { key: "round", label: "轮询" },
  { key: "rand", label: "随机" },
];

const createNodePlaceholder = (): TunnelTopologyItem => ({
  itemType: "node",
  nodeId: -1,
  protocol: "tls",
  strategy: "round",
});

const createTunnelPlaceholder = (): TunnelTopologyItem => ({
  itemType: "tunnel",
  refTunnelId: undefined,
  refTunnelName: "",
});

const normalizeTopologyItem = (item: TunnelTopologyItem): TunnelTopologyItem => ({
  itemType: item.itemType === "tunnel" ? "tunnel" : "node",
  nodeId: item.nodeId,
  refTunnelId: item.refTunnelId,
  refTunnelName: item.refTunnelName,
  protocol: item.protocol || "tls",
  strategy: item.strategy || "round",
  hopIndex: item.hopIndex,
});

const normalizeTunnel = (tunnel: any): Tunnel => ({
  ...tunnel,
  inNodeId: (tunnel.inNodeId || []).map(normalizeTopologyItem),
  outNodeId: (tunnel.outNodeId || []).map(normalizeTopologyItem),
  chainNodes: (tunnel.chainNodes || []).map((group: TunnelTopologyItem[]) => group.map(normalizeTopologyItem)),
});

export default function TunnelPage() {
  const [loading, setLoading] = useState(true);
  const [tunnels, setTunnels] = useState<Tunnel[]>([]);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [diagnosisModalOpen, setDiagnosisModalOpen] = useState(false);
  const [isEdit, setIsEdit] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [diagnosisLoading, setDiagnosisLoading] = useState(false);
  const [tunnelToDelete, setTunnelToDelete] = useState<Tunnel | null>(null);
  const [currentDiagnosisTunnel, setCurrentDiagnosisTunnel] = useState<Tunnel | null>(null);
  const [diagnosisResult, setDiagnosisResult] = useState<DiagnosisResult | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [form, setForm] = useState<TunnelForm>({
    name: "",
    type: 1,
    inNodeId: [],
    outNodeId: [],
    chainNodes: [],
    flow: 1,
    trafficRatio: 1,
    inIp: "",
    status: 1,
  });

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [tunnelsRes, nodesRes] = await Promise.all([getTunnelList(), getNodeList()]);
      if (tunnelsRes.code === 0) {
        setTunnels((tunnelsRes.data || []).map(normalizeTunnel));
      } else {
        toast.error(tunnelsRes.msg || "获取隧道列表失败");
      }

      if (nodesRes.code === 0) {
        setNodes(nodesRes.data || []);
      } else {
        console.warn("获取节点列表失败:", nodesRes.msg);
      }
    } catch (error) {
      console.error("加载数据失败:", error);
      toast.error("加载数据失败");
    } finally {
      setLoading(false);
    }
  };

  const getHopMode = (group: TunnelTopologyItem[]): HopMode =>
    group.find((item) => item.itemType === "tunnel") ? "tunnel" : "node";

  const getRealNodeItems = (items: TunnelTopologyItem[]) =>
    items.filter((item) => item.itemType === "node" && item.nodeId !== undefined && item.nodeId > 0);

  const getSelectedChainNodeIds = (excludeGroupIndex?: number): number[] =>
    (form.chainNodes || [])
      .flatMap((group, index) => (index === excludeGroupIndex ? [] : getRealNodeItems(group).map((item) => item.nodeId!)));

  const getSelectedReferenceTunnelIds = (excludeGroupIndex?: number): number[] =>
    (form.chainNodes || [])
      .flatMap((group, index) =>
        index === excludeGroupIndex
          ? []
          : group
              .filter((item) => item.itemType === "tunnel" && item.refTunnelId)
              .map((item) => item.refTunnelId as number),
      );

  const tunnelReferencesTarget = (startTunnelId: number, targetTunnelId: number, visited = new Set<number>()): boolean => {
    if (startTunnelId === targetTunnelId) {
      return true;
    }
    if (visited.has(startTunnelId)) {
      return false;
    }
    visited.add(startTunnelId);

    const tunnel = tunnels.find((item) => item.id === startTunnelId);
    if (!tunnel) {
      return false;
    }

    const referencedTunnelIds = (tunnel.chainNodes || [])
      .flatMap((group) => group.filter((item) => item.itemType === "tunnel" && item.refTunnelId))
      .map((item) => item.refTunnelId as number);

    return referencedTunnelIds.some((refTunnelId) => tunnelReferencesTarget(refTunnelId, targetTunnelId, visited));
  };

  const getReferenceCandidates = (groupIndex: number) =>
    tunnels.filter((tunnel) => {
      if (tunnel.status !== 1 || tunnel.type !== 2) {
        return false;
      }
      if (form.id && tunnel.id === form.id) {
        return false;
      }
      if (form.id && tunnelReferencesTarget(tunnel.id, form.id)) {
        return false;
      }
      if (getSelectedReferenceTunnelIds(groupIndex).includes(tunnel.id)) {
        return false;
      }
      return true;
    });

  const getReferenceSummary = (tunnel: Tunnel) => {
    const refs = (tunnel.chainNodes || [])
      .flatMap((group) => group)
      .filter((item) => item.itemType === "tunnel" && item.refTunnelName)
      .map((item) => item.refTunnelName as string);
    return refs.length > 0 ? refs.join(" / ") : "";
  };

  const validateForm = () => {
    const nextErrors: Record<string, string> = {};

    if (!form.name.trim()) {
      nextErrors.name = "请输入隧道名称";
    } else if (form.name.length < 2 || form.name.length > 50) {
      nextErrors.name = "隧道名称长度应在2-50个字符之间";
    }

    if (form.inNodeId.length === 0) {
      nextErrors.inNodeId = "请至少选择一个入口节点";
    } else {
      const offlineInNodes = getRealNodeItems(form.inNodeId).filter((item) => {
        const node = nodes.find((nodeItem) => nodeItem.id === item.nodeId);
        return node && node.status !== 1;
      });
      if (offlineInNodes.length > 0) {
        nextErrors.inNodeId = "所有入口节点必须在线";
      }
    }

    if (form.trafficRatio <= 0 || form.trafficRatio > 100) {
      nextErrors.trafficRatio = "流量倍率必须在 0-100 之间";
    }

    if (form.type === 2) {
      if (form.outNodeId.length === 0) {
        nextErrors.outNodeId = "请至少选择一个出口节点";
      }

      const duplicateNodeIds = new Set<number>();
      const touchedNodeIds: number[] = [...getRealNodeItems(form.inNodeId).map((item) => item.nodeId!)];

      form.chainNodes.forEach((group, groupIndex) => {
        const mode = getHopMode(group);
        if (mode === "node") {
          const realNodes = getRealNodeItems(group);
          if (realNodes.length === 0) {
            nextErrors[`chainNodes.${groupIndex}`] = `第 ${groupIndex + 1} 跳至少选择一个节点`;
            return;
          }

          const offlineNodes = realNodes.filter((item) => {
            const node = nodes.find((nodeItem) => nodeItem.id === item.nodeId);
            return node && node.status !== 1;
          });
          if (offlineNodes.length > 0) {
            nextErrors[`chainNodes.${groupIndex}`] = `第 ${groupIndex + 1} 跳存在离线节点`;
            return;
          }

          realNodes.forEach((item) => {
            if (touchedNodeIds.includes(item.nodeId!)) {
              duplicateNodeIds.add(item.nodeId!);
            } else {
              touchedNodeIds.push(item.nodeId!);
            }
          });
        } else {
          const refTunnel = group.find((item) => item.itemType === "tunnel" && item.refTunnelId);
          if (!refTunnel?.refTunnelId) {
            nextErrors[`chainNodes.${groupIndex}`] = `第 ${groupIndex + 1} 跳请选择引用隧道`;
            return;
          }
          if (!getReferenceCandidates(groupIndex).some((item) => item.id === refTunnel.refTunnelId)) {
            nextErrors[`chainNodes.${groupIndex}`] = `第 ${groupIndex + 1} 跳引用隧道无效`;
          }
        }
      });

      getRealNodeItems(form.outNodeId).forEach((item) => {
        if (touchedNodeIds.includes(item.nodeId!)) {
          duplicateNodeIds.add(item.nodeId!);
        } else {
          touchedNodeIds.push(item.nodeId!);
        }
      });

      if (duplicateNodeIds.size > 0) {
        nextErrors.outNodeId = "入口、节点跳和出口之间不能重复选择相同节点";
      }
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleAdd = () => {
    setIsEdit(false);
    setErrors({});
    setForm({
      name: "",
      type: 1,
      inNodeId: [],
      outNodeId: [],
      chainNodes: [],
      flow: 1,
      trafficRatio: 1,
      inIp: "",
      status: 1,
    });
    setModalOpen(true);
  };

  const handleEdit = (tunnel: Tunnel) => {
    setIsEdit(true);
    setErrors({});
    setForm({
      id: tunnel.id,
      name: tunnel.name,
      type: tunnel.type,
      inNodeId: (tunnel.inNodeId || []).map(normalizeTopologyItem),
      outNodeId: (tunnel.outNodeId || []).map(normalizeTopologyItem),
      chainNodes: (tunnel.chainNodes || []).map((group) => group.map(normalizeTopologyItem)),
      flow: tunnel.flow,
      trafficRatio: tunnel.trafficRatio,
      inIp: tunnel.inIp ? tunnel.inIp.split(",").map((ip) => ip.trim()).join("\n") : "",
      status: tunnel.status,
    });
    setModalOpen(true);
  };

  const handleDelete = (tunnel: Tunnel) => {
    setTunnelToDelete(tunnel);
    setDeleteModalOpen(true);
  };

  const confirmDelete = async () => {
    if (!tunnelToDelete) return;

    setDeleteLoading(true);
    try {
      const response = await deleteTunnel(tunnelToDelete.id);
      if (response.code === 0) {
        toast.success("删除成功");
        setDeleteModalOpen(false);
        setTunnelToDelete(null);
        loadData();
      } else {
        toast.error(response.msg || "删除失败");
      }
    } catch (error) {
      console.error("删除失败:", error);
      toast.error("删除失败");
    } finally {
      setDeleteLoading(false);
    }
  };

  const handleTypeChange = (type: number) => {
    setForm((prev) => ({
      ...prev,
      type,
      chainNodes: type === 1 ? [] : prev.chainNodes,
      outNodeId: type === 1 ? [] : prev.outNodeId,
    }));
  };

  const addChainGroup = () => {
    setForm((prev) => ({
      ...prev,
      chainNodes: [...prev.chainNodes, [createNodePlaceholder()]],
    }));
  };

  const removeChainGroup = (groupIndex: number) => {
    setForm((prev) => ({
      ...prev,
      chainNodes: prev.chainNodes.filter((_, index) => index !== groupIndex),
    }));
  };

  const setChainGroupMode = (groupIndex: number, mode: HopMode) => {
    setForm((prev) => {
      const nextChainNodes = [...prev.chainNodes];
      nextChainNodes[groupIndex] = mode === "node" ? [createNodePlaceholder()] : [createTunnelPlaceholder()];
      return { ...prev, chainNodes: nextChainNodes };
    });
  };

  const setChainGroupNodes = (groupIndex: number, selectedNodeIds: number[]) => {
    setForm((prev) => {
      const nextChainNodes = [...prev.chainNodes];
      const currentGroup = nextChainNodes[groupIndex] || [createNodePlaceholder()];
      const protocol = currentGroup[0]?.protocol || "tls";
      const strategy = currentGroup[0]?.strategy || "round";
      nextChainNodes[groupIndex] = selectedNodeIds.map((nodeId) => ({
        itemType: "node",
        nodeId,
        protocol,
        strategy,
      }));
      return { ...prev, chainNodes: nextChainNodes };
    });
  };

  const updateChainGroupProtocol = (groupIndex: number, protocol: string) => {
    setForm((prev) => {
      const nextChainNodes = [...prev.chainNodes];
      nextChainNodes[groupIndex] = (nextChainNodes[groupIndex] || []).map((item) => ({
        ...item,
        protocol,
      }));
      return { ...prev, chainNodes: nextChainNodes };
    });
  };

  const updateChainGroupStrategy = (groupIndex: number, strategy: string) => {
    setForm((prev) => {
      const nextChainNodes = [...prev.chainNodes];
      nextChainNodes[groupIndex] = (nextChainNodes[groupIndex] || []).map((item) => ({
        ...item,
        strategy,
      }));
      return { ...prev, chainNodes: nextChainNodes };
    });
  };

  const setChainGroupReference = (groupIndex: number, refTunnelId?: number) => {
    setForm((prev) => {
      const nextChainNodes = [...prev.chainNodes];
      const referencedTunnel = tunnels.find((item) => item.id === refTunnelId);
      nextChainNodes[groupIndex] = [
        {
          itemType: "tunnel",
          refTunnelId,
          refTunnelName: referencedTunnel?.name || "",
        },
      ];
      return { ...prev, chainNodes: nextChainNodes };
    });
  };

  const cleanChainNodes = () =>
    form.chainNodes
      .map((group) => {
        const mode = getHopMode(group);
        if (mode === "node") {
          return getRealNodeItems(group).map((item) => ({
            itemType: "node" as const,
            nodeId: item.nodeId,
            protocol: item.protocol || "tls",
            strategy: item.strategy || "round",
          }));
        }
        const ref = group.find((item) => item.itemType === "tunnel" && item.refTunnelId);
        return ref
          ? [
              {
                itemType: "tunnel" as const,
                refTunnelId: ref.refTunnelId,
                refTunnelName: ref.refTunnelName || "",
              },
            ]
          : [];
      })
      .filter((group) => group.length > 0);

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    setSubmitLoading(true);
    try {
      const payload = {
        ...form,
        inIp: form.inIp
          .split("\n")
          .map((ip) => ip.trim())
          .filter(Boolean)
          .join(","),
        inNodeId: getRealNodeItems(form.inNodeId).map((item) => ({
          itemType: "node" as const,
          nodeId: item.nodeId,
        })),
        outNodeId: form.type === 2 ? getRealNodeItems(form.outNodeId).map((item) => ({
          itemType: "node" as const,
          nodeId: item.nodeId,
          protocol: item.protocol || "tls",
          strategy: item.strategy || "round",
        })) : [],
        chainNodes: form.type === 2 ? cleanChainNodes() : [],
      };

      const response = isEdit ? await updateTunnel(payload) : await createTunnel(payload);
      if (response.code === 0) {
        toast.success(isEdit ? "更新成功" : "创建成功");
        setModalOpen(false);
        loadData();
      } else {
        toast.error(response.msg || (isEdit ? "更新失败" : "创建失败"));
      }
    } catch (error) {
      console.error("提交失败:", error);
      toast.error("网络错误，请重试");
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleDiagnose = async (tunnel: Tunnel) => {
    setCurrentDiagnosisTunnel(tunnel);
    setDiagnosisModalOpen(true);
    setDiagnosisLoading(true);
    setDiagnosisResult(null);

    try {
      const response = await diagnoseTunnel(tunnel.id);
      if (response.code === 0) {
        setDiagnosisResult(response.data);
      } else {
        toast.error(response.msg || "诊断失败");
      }
    } catch (error) {
      console.error("诊断失败:", error);
      toast.error("网络错误，请重试");
    } finally {
      setDiagnosisLoading(false);
    }
  };

  const getTypeDisplay = (type: number) => {
    switch (type) {
      case 1:
        return { text: "端口转发", color: "primary" };
      case 2:
        return { text: "隧道转发", color: "secondary" };
      default:
        return { text: "未知", color: "default" };
    }
  };

  const getFlowDisplay = (flow: number) => {
    switch (flow) {
      case 1:
        return "单向计算";
      case 2:
        return "双向计算";
      default:
        return "未知";
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center gap-3">
          <Spinner size="sm" />
          <span className="text-default-600">正在加载...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 lg:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div />
        <Button size="sm" variant="flat" color="primary" onPress={handleAdd}>
          新增
        </Button>
      </div>

      {tunnels.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
          {tunnels.map((tunnel) => {
            const typeDisplay = getTypeDisplay(tunnel.type);
            const referenceSummary = getReferenceSummary(tunnel);

            return (
              <Card key={tunnel.id} className="shadow-sm border border-divider hover:shadow-md transition-shadow duration-200">
                <CardHeader className="pb-2">
                  <div className="flex justify-between items-start w-full">
                    <div className="flex-1 min-w-0">
                      <h3 className="font-semibold text-foreground truncate text-sm">{tunnel.name}</h3>
                      <div className="flex items-center gap-1.5 mt-1">
                        <Chip color={typeDisplay.color as any} variant="flat" size="sm" className="text-xs">
                          {typeDisplay.text}
                        </Chip>
                      </div>
                    </div>
                  </div>
                </CardHeader>
                <CardBody className="pt-0 pb-3">
                  <div className="space-y-3">
                    <div className="pt-2 border-t border-divider">
                      <div className="flex items-center justify-center gap-2 text-xs">
                        <Chip size="sm" variant="flat" color="primary">
                          {tunnel.inNodeId.length} 入口
                        </Chip>
                        <span className="text-default-400">→</span>
                        <Chip size="sm" variant="flat" color="secondary">
                          {tunnel.type === 2 ? tunnel.chainNodes.length : 0} 跳
                        </Chip>
                        <span className="text-default-400">→</span>
                        <Chip size="sm" variant="flat" color="success">
                          {tunnel.type === 2 ? tunnel.outNodeId.length : tunnel.inNodeId.length} 出口
                        </Chip>
                      </div>
                      {referenceSummary && (
                        <p className="text-xs text-default-500 mt-2 line-clamp-2">引用隧道: {referenceSummary}</p>
                      )}
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <div className="text-center p-1.5 bg-default-50 dark:bg-default-100/30 rounded">
                        <div className="text-xs text-default-500">流量计算</div>
                        <div className="text-sm font-semibold text-foreground mt-0.5">{getFlowDisplay(tunnel.flow)}</div>
                      </div>
                      <div className="text-center p-1.5 bg-default-50 dark:bg-default-100/30 rounded">
                        <div className="text-xs text-default-500">流量倍率</div>
                        <div className="text-sm font-semibold text-foreground mt-0.5">{tunnel.trafficRatio}x</div>
                      </div>
                    </div>
                  </div>

                  <div className="flex gap-1.5 mt-3">
                    <Button size="sm" variant="flat" color="primary" className="flex-1 min-h-8" onPress={() => handleEdit(tunnel)}>
                      编辑
                    </Button>
                    <Button size="sm" variant="flat" color="warning" className="flex-1 min-h-8" onPress={() => handleDiagnose(tunnel)}>
                      诊断
                    </Button>
                    <Button size="sm" variant="flat" color="danger" className="flex-1 min-h-8" onPress={() => handleDelete(tunnel)}>
                      删除
                    </Button>
                  </div>
                </CardBody>
              </Card>
            );
          })}
        </div>
      ) : (
        <Card className="shadow-sm border border-gray-200 dark:border-gray-700">
          <CardBody className="text-center py-16">
            <div className="flex flex-col items-center gap-4">
              <div className="w-16 h-16 bg-default-100 rounded-full flex items-center justify-center">
                <svg className="w-8 h-8 text-default-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                </svg>
              </div>
              <div>
                <h3 className="text-lg font-semibold text-foreground">暂无隧道配置</h3>
                <p className="text-default-500 text-sm mt-1">还没有创建任何隧道配置，点击上方按钮开始创建</p>
              </div>
            </div>
          </CardBody>
        </Card>
      )}

      <Modal isOpen={modalOpen} onOpenChange={setModalOpen} size="3xl" scrollBehavior="outside" backdrop="blur" placement="center">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader className="flex flex-col gap-1">
                <h2 className="text-xl font-bold">{isEdit ? "编辑隧道" : "新增隧道"}</h2>
                <p className="text-small text-default-500">
                  {isEdit ? "修改隧道拓扑后会立即重配现有转发" : "创建新的隧道配置"}
                </p>
              </ModalHeader>
              <ModalBody>
                <div className="space-y-4">
                  <Input
                    label="隧道名称"
                    placeholder="请输入隧道名称"
                    value={form.name}
                    onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                    isInvalid={!!errors.name}
                    errorMessage={errors.name}
                    variant="bordered"
                  />

                  <Select
                    label="隧道类型"
                    placeholder="请选择隧道类型"
                    selectedKeys={[form.type.toString()]}
                    onSelectionChange={(keys) => {
                      const selectedKey = Array.from(keys)[0] as string;
                      if (selectedKey) {
                        handleTypeChange(parseInt(selectedKey, 10));
                      }
                    }}
                    variant="bordered"
                  >
                    <SelectItem key="1">端口转发</SelectItem>
                    <SelectItem key="2">隧道转发</SelectItem>
                  </Select>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <Select
                      label="流量计算"
                      placeholder="请选择流量计算方式"
                      selectedKeys={[form.flow.toString()]}
                      onSelectionChange={(keys) => {
                        const selectedKey = Array.from(keys)[0] as string;
                        if (selectedKey) {
                          setForm((prev) => ({ ...prev, flow: parseInt(selectedKey, 10) }));
                        }
                      }}
                      variant="bordered"
                    >
                      <SelectItem key="1">单向计算（仅上传）</SelectItem>
                      <SelectItem key="2">双向计算（上传+下载）</SelectItem>
                    </Select>

                    <Input
                      label="流量倍率"
                      placeholder="请输入流量倍率"
                      type="number"
                      value={form.trafficRatio.toString()}
                      onChange={(e) => setForm((prev) => ({ ...prev, trafficRatio: parseFloat(e.target.value) || 0 }))}
                      isInvalid={!!errors.trafficRatio}
                      errorMessage={errors.trafficRatio}
                      variant="bordered"
                    />
                  </div>

                  <Textarea
                    label="入口IP"
                    placeholder={"一行一个 IP 或域名，例如:\n192.168.1.100\nexample.com"}
                    value={form.inIp}
                    onChange={(e) => setForm((prev) => ({ ...prev, inIp: e.target.value }))}
                    variant="bordered"
                    minRows={3}
                    maxRows={5}
                    description="为空时自动使用入口节点 IP"
                  />

                  <Divider />
                  <h3 className="text-lg font-semibold">入口配置</h3>
                  <Select
                    label="入口节点"
                    placeholder="请选择入口节点（可多选）"
                    selectionMode="multiple"
                    selectedKeys={getRealNodeItems(form.inNodeId).map((item) => item.nodeId!.toString())}
                    disabledKeys={[
                      ...nodes.filter((node) => node.status !== 1).map((node) => node.id.toString()),
                      ...getRealNodeItems(form.outNodeId).map((item) => item.nodeId!.toString()),
                      ...getSelectedChainNodeIds().map((id) => id.toString()),
                    ]}
                    onSelectionChange={(keys) => {
                      const selectedIds = Array.from(keys).map((key) => parseInt(key as string, 10));
                      setForm((prev) => ({
                        ...prev,
                        inNodeId: selectedIds.map((nodeId) => ({ itemType: "node", nodeId })),
                      }));
                    }}
                    isInvalid={!!errors.inNodeId}
                    errorMessage={errors.inNodeId}
                    variant="bordered"
                  >
                    {nodes.map((node) => (
                      <SelectItem key={node.id} textValue={node.name}>
                        {node.name}
                      </SelectItem>
                    ))}
                  </Select>

                  {form.type === 2 && (
                    <>
                      <Divider />
                      <div className="flex items-center justify-between">
                        <h3 className="text-lg font-semibold">转发链配置</h3>
                        <Button size="sm" color="primary" variant="flat" onPress={addChainGroup}>
                          添加一跳
                        </Button>
                      </div>

                      {form.chainNodes.length === 0 ? (
                        <div className="text-center py-8 bg-default-50 dark:bg-default-100/50 rounded border border-dashed border-default-300">
                          <p className="text-sm text-default-500">还没有添加转发链，点击上方按钮开始添加</p>
                        </div>
                      ) : (
                        <div className="space-y-3">
                          {form.chainNodes.map((group, groupIndex) => {
                            const mode = getHopMode(group);
                            const nodeItems = getRealNodeItems(group);
                            const protocol = group[0]?.protocol || "tls";
                            const strategy = group[0]?.strategy || "round";
                            const currentReferenceId = group.find((item) => item.itemType === "tunnel")?.refTunnelId;

                            return (
                              <div key={groupIndex} className="border border-default-200 rounded-lg p-3 space-y-3">
                                <div className="flex items-center justify-between">
                                  <span className="text-sm font-medium text-default-600">第 {groupIndex + 1} 跳</span>
                                  <Button size="sm" color="danger" variant="light" isIconOnly onPress={() => removeChainGroup(groupIndex)}>
                                    ×
                                  </Button>
                                </div>

                                <Select
                                  label="跳类型"
                                  selectedKeys={[mode]}
                                  onSelectionChange={(keys) => {
                                    const selectedKey = Array.from(keys)[0] as HopMode;
                                    if (selectedKey) {
                                      setChainGroupMode(groupIndex, selectedKey);
                                    }
                                  }}
                                  variant="bordered"
                                  size="sm"
                                >
                                  <SelectItem key="node">节点跳</SelectItem>
                                  <SelectItem key="tunnel">引用隧道</SelectItem>
                                </Select>

                                {mode === "node" ? (
                                  <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                                    <div className="col-span-1 md:col-span-2">
                                      <Select
                                        label="节点"
                                        placeholder="选择节点（可多选）"
                                        selectionMode="multiple"
                                        selectedKeys={nodeItems.map((item) => item.nodeId!.toString())}
                                        disabledKeys={[
                                          ...nodes.filter((node) => node.status !== 1).map((node) => node.id.toString()),
                                          ...getRealNodeItems(form.inNodeId).map((item) => item.nodeId!.toString()),
                                          ...getRealNodeItems(form.outNodeId).map((item) => item.nodeId!.toString()),
                                          ...getSelectedChainNodeIds(groupIndex).map((id) => id.toString()),
                                        ]}
                                        onSelectionChange={(keys) => {
                                          const selectedIds = Array.from(keys).map((key) => parseInt(key as string, 10));
                                          setChainGroupNodes(groupIndex, selectedIds);
                                        }}
                                        variant="bordered"
                                        size="sm"
                                        isInvalid={!!errors[`chainNodes.${groupIndex}`]}
                                        errorMessage={errors[`chainNodes.${groupIndex}`]}
                                      >
                                        {nodes.map((node) => (
                                          <SelectItem key={node.id} textValue={node.name}>
                                            {node.name}
                                          </SelectItem>
                                        ))}
                                      </Select>
                                    </div>

                                    <Select
                                      label="协议"
                                      selectedKeys={[protocol]}
                                      onSelectionChange={(keys) => {
                                        const selectedKey = Array.from(keys)[0] as string;
                                        if (selectedKey) {
                                          updateChainGroupProtocol(groupIndex, selectedKey);
                                        }
                                      }}
                                      variant="bordered"
                                      size="sm"
                                    >
                                      {PROTOCOL_OPTIONS.map((option) => (
                                        <SelectItem key={option.key}>{option.label}</SelectItem>
                                      ))}
                                    </Select>

                                    <Select
                                      label="负载策略"
                                      selectedKeys={[strategy]}
                                      onSelectionChange={(keys) => {
                                        const selectedKey = Array.from(keys)[0] as string;
                                        if (selectedKey) {
                                          updateChainGroupStrategy(groupIndex, selectedKey);
                                        }
                                      }}
                                      variant="bordered"
                                      size="sm"
                                    >
                                      {STRATEGY_OPTIONS.map((option) => (
                                        <SelectItem key={option.key}>{option.label}</SelectItem>
                                      ))}
                                    </Select>
                                  </div>
                                ) : (
                                  <Select
                                    label="引用隧道"
                                    placeholder="请选择要引用的隧道"
                                    selectedKeys={currentReferenceId ? [currentReferenceId.toString()] : []}
                                    onSelectionChange={(keys) => {
                                      const selectedKey = Array.from(keys)[0] as string;
                                      setChainGroupReference(groupIndex, selectedKey ? parseInt(selectedKey, 10) : undefined);
                                    }}
                                    variant="bordered"
                                    size="sm"
                                    isInvalid={!!errors[`chainNodes.${groupIndex}`]}
                                    errorMessage={errors[`chainNodes.${groupIndex}`]}
                                    description="仅可引用启用中的隧道转发，并自动过滤会形成循环依赖的候选"
                                  >
                                    {getReferenceCandidates(groupIndex).map((tunnel) => (
                                      <SelectItem key={tunnel.id} textValue={tunnel.name}>
                                        {tunnel.name}
                                      </SelectItem>
                                    ))}
                                  </Select>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )}

                      <Divider />
                      <h3 className="text-lg font-semibold">出口配置</h3>
                      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                        <div className="col-span-1 md:col-span-2">
                          <Select
                            label="出口节点"
                            placeholder="请选择出口节点（可多选）"
                            selectionMode="multiple"
                            selectedKeys={getRealNodeItems(form.outNodeId).map((item) => item.nodeId!.toString())}
                            disabledKeys={[
                              ...nodes.filter((node) => node.status !== 1).map((node) => node.id.toString()),
                              ...getRealNodeItems(form.inNodeId).map((item) => item.nodeId!.toString()),
                              ...getSelectedChainNodeIds().map((id) => id.toString()),
                            ]}
                            onSelectionChange={(keys) => {
                              const selectedIds = Array.from(keys).map((key) => parseInt(key as string, 10));
                              const protocol = form.outNodeId[0]?.protocol || "tls";
                              const strategy = form.outNodeId[0]?.strategy || "round";
                              setForm((prev) => ({
                                ...prev,
                                outNodeId: selectedIds.map((nodeId) => ({
                                  itemType: "node",
                                  nodeId,
                                  protocol,
                                  strategy,
                                })),
                              }));
                            }}
                            isInvalid={!!errors.outNodeId}
                            errorMessage={errors.outNodeId}
                            variant="bordered"
                          >
                            {nodes.map((node) => (
                              <SelectItem key={node.id} textValue={node.name}>
                                {node.name}
                              </SelectItem>
                            ))}
                          </Select>
                        </div>

                        <Select
                          label="协议"
                          selectedKeys={[form.outNodeId[0]?.protocol || "tls"]}
                          onSelectionChange={(keys) => {
                            const selectedKey = Array.from(keys)[0] as string;
                            if (selectedKey) {
                              setForm((prev) => ({
                                ...prev,
                                outNodeId: prev.outNodeId.map((item) => ({ ...item, protocol: selectedKey })),
                              }));
                            }
                          }}
                          variant="bordered"
                        >
                          {PROTOCOL_OPTIONS.map((option) => (
                            <SelectItem key={option.key}>{option.label}</SelectItem>
                          ))}
                        </Select>

                        <Select
                          label="负载策略"
                          selectedKeys={[form.outNodeId[0]?.strategy || "round"]}
                          onSelectionChange={(keys) => {
                            const selectedKey = Array.from(keys)[0] as string;
                            if (selectedKey) {
                              setForm((prev) => ({
                                ...prev,
                                outNodeId: prev.outNodeId.map((item) => ({ ...item, strategy: selectedKey })),
                              }));
                            }
                          }}
                          variant="bordered"
                        >
                          {STRATEGY_OPTIONS.map((option) => (
                            <SelectItem key={option.key}>{option.label}</SelectItem>
                          ))}
                        </Select>
                      </div>
                    </>
                  )}
                </div>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>
                  取消
                </Button>
                <Button color="primary" onPress={handleSubmit} isLoading={submitLoading}>
                  {isEdit ? "保存修改" : "创建隧道"}
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      <Modal isOpen={deleteModalOpen} onOpenChange={setDeleteModalOpen} backdrop="blur">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader>确认删除</ModalHeader>
              <ModalBody>
                <p>
                  确定要删除隧道 <strong>{tunnelToDelete?.name}</strong> 吗？
                </p>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>
                  取消
                </Button>
                <Button color="danger" onPress={confirmDelete} isLoading={deleteLoading}>
                  删除
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      <Modal isOpen={diagnosisModalOpen} onOpenChange={setDiagnosisModalOpen} size="4xl" scrollBehavior="outside" backdrop="blur">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader className="flex flex-col gap-1">
                <h2 className="text-xl font-bold">隧道诊断结果</h2>
                <p className="text-small text-default-500">{currentDiagnosisTunnel?.name}</p>
              </ModalHeader>
              <ModalBody>
                {diagnosisLoading ? (
                  <div className="flex items-center justify-center py-12">
                    <Spinner />
                  </div>
                ) : diagnosisResult ? (
                  <div className="space-y-3">
                    {diagnosisResult.results.map((result, index) => (
                      <Card key={index} shadow="sm">
                        <CardBody className="space-y-2">
                          <div className="flex items-center justify-between">
                            <div className="font-medium text-sm">{result.description}</div>
                            <Chip color={result.success ? "success" : "danger"} variant="flat" size="sm">
                              {result.success ? "成功" : "失败"}
                            </Chip>
                          </div>
                          <div className="text-xs text-default-500">
                            {result.nodeName} ({result.nodeId}) → {result.targetIp}
                            {result.targetPort ? `:${result.targetPort}` : ""}
                          </div>
                          <div className="text-sm">{result.message || "-"}</div>
                          {result.success && (
                            <div className="text-xs text-default-500">
                              平均延迟: {result.averageTime ?? 0} ms，丢包率: {result.packetLoss ?? 0}%
                            </div>
                          )}
                        </CardBody>
                      </Card>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-12 text-default-500">暂无诊断数据</div>
                )}
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>
                  关闭
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>
    </div>
  );
}
