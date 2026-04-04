import { useEffect, useMemo, useState } from "react";
import { Card, CardBody } from "@heroui/card";
import { Button } from "@heroui/button";
import { Input, Textarea } from "@heroui/input";
import { Select, SelectItem } from "@heroui/select";
import { Modal, ModalContent, ModalHeader, ModalBody, ModalFooter } from "@heroui/modal";
import { Chip } from "@heroui/chip";
import { Spinner } from "@heroui/spinner";
import { Switch } from "@heroui/switch";
import { Alert } from "@heroui/alert";
import toast from "react-hot-toast";

import {
  createForward,
  getForwardList,
  updateForward,
  deleteForward,
  forceDeleteForward,
  userTunnel,
  pauseForwardService,
  resumeForwardService,
  diagnoseForward,
} from "@/api";
import {
  buildForwardTableRows,
  type ForwardTableFilterState,
  type ForwardTableRow,
  paginateForwardRows,
} from "@/pages/forward-table-utils";

interface Forward {
  id: number;
  name: string;
  tunnelId: number;
  tunnelName: string;
  inIp: string;
  inPort: number;
  remoteAddr: string;
  interfaceName?: string;
  strategy: string;
  status: number;
  inFlow: number;
  outFlow: number;
  serviceRunning: boolean;
  createdTime: string;
  userName?: string;
  userId?: number;
  inx?: number;
}

interface Tunnel {
  id: number;
  name: string;
  inNodePortSta?: number;
  inNodePortEnd?: number;
}

interface ForwardForm {
  id?: number;
  userId?: number;
  name: string;
  tunnelId: number | null;
  inPort: number | null;
  remoteAddr: string;
  interfaceName?: string;
  strategy: string;
}

interface AddressItem {
  id: number;
  address: string;
  copying: boolean;
}

interface DiagnosisResult {
  forwardName: string;
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

const PAGE_SIZE = 10;

export default function ForwardPage() {
  const [loading, setLoading] = useState(true);
  const [forwards, setForwards] = useState<Forward[]>([]);
  const [tunnels, setTunnels] = useState<Tunnel[]>([]);
  const [isMobile, setIsMobile] = useState(false);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [searchValue, setSearchValue] = useState("");
  const [statusFilter, setStatusFilter] = useState<ForwardTableFilterState["status"]>("all");
  const [strategyFilter, setStrategyFilter] = useState<ForwardTableFilterState["strategy"]>("all");
  const [tunnelFilter, setTunnelFilter] = useState<"all" | number>("all");
  const [userFilter, setUserFilter] = useState<"all" | number>("all");
  const [multiTargetOnly, setMultiTargetOnly] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedForwardIds, setSelectedForwardIds] = useState<Set<number>>(new Set());
  const [detailForward, setDetailForward] = useState<Forward | null>(null);
  const [batchAction, setBatchAction] = useState<null | "delete" | "pause" | "start">(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [addressModalOpen, setAddressModalOpen] = useState(false);
  const [diagnosisModalOpen, setDiagnosisModalOpen] = useState(false);
  const [isEdit, setIsEdit] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [diagnosisLoading, setDiagnosisLoading] = useState(false);
  const [forwardToDelete, setForwardToDelete] = useState<Forward | null>(null);
  const [currentDiagnosisForward, setCurrentDiagnosisForward] = useState<Forward | null>(null);
  const [diagnosisResult, setDiagnosisResult] = useState<DiagnosisResult | null>(null);
  const [addressModalTitle, setAddressModalTitle] = useState("");
  const [addressList, setAddressList] = useState<AddressItem[]>([]);

  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [exportMode, setExportMode] = useState<"tunnel" | "custom">("tunnel");
  const [exportModalTitle, setExportModalTitle] = useState("导出转发数据");
  const [exportData, setExportData] = useState("");
  const [exportLoading, setExportLoading] = useState(false);
  const [selectedTunnelForExport, setSelectedTunnelForExport] = useState<number | null>(null);

  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importData, setImportData] = useState("");
  const [importLoading, setImportLoading] = useState(false);
  const [selectedTunnelForImport, setSelectedTunnelForImport] = useState<number | null>(null);
  const [importResults, setImportResults] = useState<Array<{
    line: string;
    success: boolean;
    message: string;
    forwardName?: string;
  }>>([]);

  const [form, setForm] = useState<ForwardForm>({
    name: "",
    tunnelId: null,
    inPort: null,
    remoteAddr: "",
    interfaceName: "",
    strategy: "fifo",
  });
  const [errors, setErrors] = useState<{ [key: string]: string }>({});

  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 768);
    };

    checkMobile();
    window.addEventListener("resize", checkMobile);

    return () => window.removeEventListener("resize", checkMobile);
  }, []);

  useEffect(() => {
    loadData();
  }, []);

  const filterState = useMemo<ForwardTableFilterState>(
    () => ({
      search: searchValue,
      status: statusFilter,
      strategy: strategyFilter,
      tunnelId: tunnelFilter,
      userId: userFilter,
      multiTargetOnly,
    }),
    [searchValue, statusFilter, strategyFilter, tunnelFilter, userFilter, multiTargetOnly],
  );

  const rawForwardMap = useMemo(
    () => new Map(forwards.map((forward) => [forward.id, forward])),
    [forwards],
  );

  const filteredRows = useMemo(() => buildForwardTableRows(forwards, filterState), [forwards, filterState]);
  const totalPages = Math.max(1, Math.ceil(filteredRows.length / PAGE_SIZE));
  const pagedRows = useMemo(
    () => paginateForwardRows(filteredRows, currentPage, PAGE_SIZE),
    [filteredRows, currentPage],
  );

  const userOptions = useMemo(() => {
    const map = new Map<number, string>();
    forwards.forEach((forward) => {
      if (forward.userId != null && forward.userName) {
        map.set(forward.userId, forward.userName);
      }
    });

    return Array.from(map.entries())
      .map(([id, name]) => ({ id, name }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [forwards]);

  const selectedForwards = useMemo(
    () => forwards.filter((forward) => selectedForwardIds.has(forward.id)),
    [forwards, selectedForwardIds],
  );

  const allFilteredSelected = filteredRows.length > 0 && filteredRows.every((row) => selectedForwardIds.has(row.id));

  useEffect(() => {
    setCurrentPage((previous) => Math.min(previous, totalPages));
  }, [totalPages]);

  useEffect(() => {
    setCurrentPage(1);
  }, [searchValue, statusFilter, strategyFilter, tunnelFilter, userFilter, multiTargetOnly]);

  useEffect(() => {
    setSelectedForwardIds((previous) => {
      const next = new Set<number>();
      previous.forEach((id) => {
        if (rawForwardMap.has(id)) {
          next.add(id);
        }
      });
      return next;
    });
  }, [rawForwardMap]);

  const loadData = async (showLoading = true) => {
    setLoading(showLoading);
    try {
      const [forwardsRes, tunnelsRes] = await Promise.all([getForwardList(), userTunnel()]);

      if (forwardsRes.code === 0) {
        const forwardsData =
          forwardsRes.data?.map((forward: any) => ({
            ...forward,
            serviceRunning: forward.status === 1,
          })) || [];
        setForwards(forwardsData);
      } else {
        toast.error(forwardsRes.msg || "获取转发列表失败");
      }

      if (tunnelsRes.code === 0) {
        setTunnels(tunnelsRes.data || []);
      } else {
        console.warn("获取隧道列表失败:", tunnelsRes.msg);
      }
    } catch (error) {
      console.error("加载数据失败:", error);
      toast.error("加载数据失败");
    } finally {
      setLoading(false);
    }
  };

  const validateForm = (): boolean => {
    const newErrors: { [key: string]: string } = {};

    if (!form.name.trim()) {
      newErrors.name = "请输入转发名称";
    } else if (form.name.length < 2 || form.name.length > 50) {
      newErrors.name = "转发名称长度应在2-50个字符之间";
    }

    if (!form.tunnelId) {
      newErrors.tunnelId = "请选择关联隧道";
    }

    if (form.inPort !== null && form.inPort !== undefined) {
      const port = Number(form.inPort);
      if (Number.isNaN(port) || port < 1 || port > 65535) {
        newErrors.inPort = "端口必须在 1-65535 之间";
      }
    }

    if (!form.remoteAddr.trim()) {
      newErrors.remoteAddr = "请输入远程地址";
    } else {
      const addresses = form.remoteAddr
        .split("\n")
        .map((addr) => addr.trim())
        .filter((addr) => addr);
      const ipv4Pattern =
        /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?):\d+$/;
      const ipv6FullPattern =
        /^\[((([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}|:))|(([0-9a-fA-F]{1,4}:){6}(:[0-9a-fA-F]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9a-fA-F]{1,4}:){5}(((:[0-9a-fA-F]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9a-fA-F]{1,4}:){4}(((:[0-9a-fA-F]{1,4}){1,3})|((:[0-9a-fA-F]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9a-fA-F]{1,4}:){3}(((:[0-9a-fA-F]{1,4}){1,4})|((:[0-9a-fA-F]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9a-fA-F]{1,4}:){2}(((:[0-9a-fA-F]{1,4}){1,5})|((:[0-9a-fA-F]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9a-fA-F]{1,4}:){1}(((:[0-9a-fA-F]{1,4}){1,6})|((:[0-9a-fA-F]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9a-fA-F]{1,4}){1,7})|((:[0-9a-fA-F]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))\]:\d+$/;
      const domainPattern =
        /^[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?)*:\d+$/;

      for (let index = 0; index < addresses.length; index += 1) {
        const address = addresses[index];
        if (!ipv4Pattern.test(address) && !ipv6FullPattern.test(address) && !domainPattern.test(address)) {
          newErrors.remoteAddr = `第${index + 1}行地址格式错误`;
          break;
        }
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleAdd = () => {
    setIsEdit(false);
    setForm({
      name: "",
      tunnelId: null,
      inPort: null,
      remoteAddr: "",
      interfaceName: "",
      strategy: "fifo",
    });
    setErrors({});
    setModalOpen(true);
  };

  const handleEdit = (forward: Forward) => {
    setIsEdit(true);
    setForm({
      id: forward.id,
      userId: forward.userId,
      name: forward.name,
      tunnelId: forward.tunnelId,
      inPort: forward.inPort,
      remoteAddr: forward.remoteAddr.split(",").join("\n"),
      interfaceName: forward.interfaceName || "",
      strategy: forward.strategy || "fifo",
    });
    setErrors({});
    setModalOpen(true);
  };

  const handleDelete = (forward: Forward) => {
    setForwardToDelete(forward);
    setDeleteModalOpen(true);
  };

  const confirmDelete = async () => {
    if (!forwardToDelete) return;

    setDeleteLoading(true);
    try {
      const res = await deleteForward(forwardToDelete.id);
      if (res.code === 0) {
        toast.success("删除成功");
      } else {
        const forceRes = await forceDeleteForward(forwardToDelete.id);
        if (forceRes.code !== 0) {
          toast.error(forceRes.msg || res.msg || "删除失败");
          return;
        }
        toast.success("强制删除成功");
      }

      setDeleteModalOpen(false);
      await loadData(false);
      setSelectedForwardIds((previous) => {
        const next = new Set(previous);
        next.delete(forwardToDelete.id);
        return next;
      });
    } catch (error) {
      console.error("删除失败:", error);
      toast.error("删除失败");
    } finally {
      setDeleteLoading(false);
    }
  };

  const handleTunnelChange = (tunnelId: string) => {
    setForm((previous) => ({ ...previous, tunnelId: Number.parseInt(tunnelId, 10) }));
  };

  const handleSubmit = async () => {
    if (!validateForm()) return;

    setSubmitLoading(true);
    try {
      const processedRemoteAddr = form.remoteAddr
        .split("\n")
        .map((addr) => addr.trim())
        .filter((addr) => addr)
        .join(",");

      const addressCount = processedRemoteAddr.split(",").length;
      const payload = {
        name: form.name,
        tunnelId: form.tunnelId,
        inPort: form.inPort,
        remoteAddr: processedRemoteAddr,
        strategy: addressCount > 1 ? form.strategy : "fifo",
      };

      const response = isEdit
        ? await updateForward({
            id: form.id,
            userId: form.userId,
            ...payload,
          })
        : await createForward(payload);

      if (response.code === 0) {
        toast.success(isEdit ? "修改成功" : "创建成功");
        setModalOpen(false);
        await loadData(false);
      } else {
        toast.error(response.msg || "操作失败");
      }
    } catch (error) {
      console.error("提交失败:", error);
      toast.error("操作失败");
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleServiceToggle = async (forward: Forward) => {
    if (forward.status !== 1 && forward.status !== 0) {
      toast.error("转发状态异常，无法操作");
      return;
    }

    const targetState = !forward.serviceRunning;

    setForwards((previous) =>
      previous.map((item) =>
        item.id === forward.id ? { ...item, serviceRunning: targetState } : item,
      ),
    );

    try {
      const response = targetState
        ? await resumeForwardService(forward.id)
        : await pauseForwardService(forward.id);

      if (response.code === 0) {
        toast.success(targetState ? "服务已启动" : "服务已暂停");
        setForwards((previous) =>
          previous.map((item) =>
            item.id === forward.id ? { ...item, status: targetState ? 1 : 0 } : item,
          ),
        );
      } else {
        setForwards((previous) =>
          previous.map((item) =>
            item.id === forward.id ? { ...item, serviceRunning: !targetState } : item,
          ),
        );
        toast.error(response.msg || "操作失败");
      }
    } catch (error) {
      setForwards((previous) =>
        previous.map((item) =>
          item.id === forward.id ? { ...item, serviceRunning: !targetState } : item,
        ),
      );
      console.error("服务开关操作失败:", error);
      toast.error("网络错误，操作失败");
    }
  };

  const handleDiagnose = async (forward: Forward) => {
    setCurrentDiagnosisForward(forward);
    setDiagnosisModalOpen(true);
    setDiagnosisLoading(true);
    setDiagnosisResult(null);

    try {
      const response = await diagnoseForward(forward.id);
      if (response.code === 0) {
        setDiagnosisResult(response.data);
      } else {
        toast.error(response.msg || "诊断失败");
        setDiagnosisResult({
          forwardName: forward.name,
          timestamp: Date.now(),
          results: [
            {
              success: false,
              description: "诊断失败",
              nodeName: "-",
              nodeId: "-",
              targetIp: forward.remoteAddr.split(",")[0] || "-",
              message: response.msg || "诊断过程中发生错误",
            },
          ],
        });
      }
    } catch (error) {
      console.error("诊断失败:", error);
      toast.error("网络错误，请重试");
      setDiagnosisResult({
        forwardName: forward.name,
        timestamp: Date.now(),
        results: [
          {
            success: false,
            description: "网络错误",
            nodeName: "-",
            nodeId: "-",
            targetIp: forward.remoteAddr.split(",")[0] || "-",
            message: "无法连接到服务器",
          },
        ],
      });
    } finally {
      setDiagnosisLoading(false);
    }
  };

  const getQualityDisplay = (averageTime?: number, packetLoss?: number) => {
    if (averageTime === undefined || packetLoss === undefined) return null;
    if (averageTime < 30 && packetLoss === 0) return { text: "优秀", color: "success" };
    if (averageTime < 50 && packetLoss === 0) return { text: "很好", color: "success" };
    if (averageTime < 100 && packetLoss < 1) return { text: "良好", color: "primary" };
    if (averageTime < 150 && packetLoss < 2) return { text: "一般", color: "warning" };
    if (averageTime < 200 && packetLoss < 5) return { text: "较差", color: "warning" };
    return { text: "很差", color: "danger" };
  };

  const formatFlow = (value: number): string => {
    if (value === 0) return "0 B";
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(2)} KB`;
    if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(2)} MB`;
    return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  };

  const formatInAddress = (ipString: string, port: number): string => {
    if (!ipString) return "";
    const items = ipString.split(",").map((item) => item.trim()).filter(Boolean);
    if (items.length === 0) return "";

    const firstItem = items[0];
    const hasPort = /:\d+$/.test(firstItem);

    if (hasPort) {
      return items.length === 1 ? items[0] : `${items[0]} (+${items.length - 1}个)`;
    }

    const firstIp = items[0];
    const formattedFirstIp = firstIp.includes(":") && !firstIp.startsWith("[") ? `[${firstIp}]` : firstIp;
    return items.length === 1
      ? `${formattedFirstIp}:${port}`
      : `${formattedFirstIp}:${port} (+${items.length - 1}个)`;
  };

  const formatRemoteAddress = (addressString: string): string => {
    if (!addressString) return "";
    const addresses = addressString.split(",").map((addr) => addr.trim()).filter(Boolean);
    if (addresses.length === 0) return "";
    return addresses.length === 1 ? addresses[0] : `${addresses[0]} (+${addresses.length - 1})`;
  };

  const showAddressModal = (addressString: string, port: number | null, title: string) => {
    if (!addressString) return;

    let addresses: string[];
    if (port !== null) {
      const items = addressString.split(",").map((item) => item.trim()).filter(Boolean);
      if (items.length <= 1) {
        copyToClipboard(formatInAddress(addressString, port), title);
        return;
      }

      const hasPort = /:\d+$/.test(items[0]);
      addresses = hasPort
        ? items
        : items.map((ip) => {
            if (ip.includes(":") && !ip.startsWith("[")) {
              return `[${ip}]:${port}`;
            }
            return `${ip}:${port}`;
          });
    } else {
      addresses = addressString.split(",").map((addr) => addr.trim()).filter(Boolean);
      if (addresses.length <= 1) {
        copyToClipboard(addressString, title);
        return;
      }
    }

    setAddressList(
      addresses.map((address, index) => ({
        id: index,
        address,
        copying: false,
      })),
    );
    setAddressModalTitle(`${title} (${addresses.length}个)`);
    setAddressModalOpen(true);
  };

  const copyToClipboard = async (text: string, label = "内容") => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`已复制${label}`);
    } catch (error) {
      toast.error("复制失败");
    }
  };

  const copyAddress = async (addressItem: AddressItem) => {
    try {
      setAddressList((previous) =>
        previous.map((item) => (item.id === addressItem.id ? { ...item, copying: true } : item)),
      );
      await copyToClipboard(addressItem.address, "地址");
    } finally {
      setAddressList((previous) =>
        previous.map((item) => (item.id === addressItem.id ? { ...item, copying: false } : item)),
      );
    }
  };

  const copyAllAddresses = async () => {
    if (addressList.length === 0) return;
    await copyToClipboard(
      addressList.map((item) => item.address).join("\n"),
      "所有地址",
    );
  };

  const getAddressCount = (addressString: string): number =>
    addressString.split("\n").map((addr) => addr.trim()).filter(Boolean).length;

  const getStatusDisplay = (status: number) => {
    switch (status) {
      case 1:
        return { color: "success", text: "正常" };
      case 0:
        return { color: "warning", text: "暂停" };
      case -1:
        return { color: "danger", text: "异常" };
      default:
        return { color: "default", text: "未知" };
    }
  };

  const getStrategyDisplay = (strategy: string) => {
    switch (strategy) {
      case "fifo":
        return { color: "primary", text: "主备" };
      case "round":
        return { color: "success", text: "轮询" };
      case "rand":
        return { color: "warning", text: "随机" };
      case "hash":
        return { color: "secondary", text: "哈希" };
      default:
        return { color: "default", text: "未知" };
    }
  };

  const handleExport = () => {
    setExportMode("tunnel");
    setExportModalTitle("导出转发数据");
    setSelectedTunnelForExport(null);
    setExportData("");
    setExportModalOpen(true);
  };

  const openExportModalWithRows = (rows: Forward[], title: string) => {
    const exportText = rows.map((forward) => `${forward.remoteAddr}|${forward.name}|${forward.inPort ?? ""}`).join("\n");
    setExportMode("custom");
    setExportModalTitle(title);
    setExportData(exportText);
    setExportModalOpen(true);
  };

  const executeExport = () => {
    if (!selectedTunnelForExport) {
      toast.error("请选择要导出的隧道");
      return;
    }

    setExportLoading(true);
    try {
      const rows = forwards.filter((forward) => forward.tunnelId === selectedTunnelForExport);
      if (rows.length === 0) {
        toast.error("所选隧道没有转发数据");
        return;
      }

      setExportData(rows.map((forward) => `${forward.remoteAddr}|${forward.name}|${forward.inPort ?? ""}`).join("\n"));
    } catch (error) {
      console.error("导出失败:", error);
      toast.error("导出失败");
    } finally {
      setExportLoading(false);
    }
  };

  const copyExportData = async () => {
    await copyToClipboard(exportData, "转发数据");
  };

  const handleImport = () => {
    setImportData("");
    setImportResults([]);
    setSelectedTunnelForImport(null);
    setImportModalOpen(true);
  };

  const executeImport = async () => {
    if (!importData.trim()) {
      toast.error("请输入要导入的数据");
      return;
    }

    if (!selectedTunnelForImport) {
      toast.error("请选择要导入的隧道");
      return;
    }

    setImportLoading(true);
    setImportResults([]);

    try {
      const lines = importData.trim().split("\n").filter((line) => line.trim());

      for (let index = 0; index < lines.length; index += 1) {
        const line = lines[index].trim();
        const parts = line.split("|");

        if (parts.length < 2) {
          setImportResults((previous) => [
            {
              line,
              success: false,
              message: "格式错误：需要至少包含目标地址和转发名称",
            },
            ...previous,
          ]);
          continue;
        }

        const [remoteAddr, name, inPort] = parts;
        if (!remoteAddr.trim() || !name.trim()) {
          setImportResults((previous) => [
            {
              line,
              success: false,
              message: "目标地址和转发名称不能为空",
            },
            ...previous,
          ]);
          continue;
        }

        const addresses = remoteAddr.trim().split(",");
        const addressPattern = /^[^:]+:\d+$/;
        const isValidFormat = addresses.every((addr) => addressPattern.test(addr.trim()));
        if (!isValidFormat) {
          setImportResults((previous) => [
            {
              line,
              success: false,
              message: "目标地址格式错误，应为 地址:端口 格式，多个地址用逗号分隔",
            },
            ...previous,
          ]);
          continue;
        }

        let portNumber: number | null = null;
        if (inPort && inPort.trim()) {
          const port = Number.parseInt(inPort.trim(), 10);
          if (Number.isNaN(port) || port < 1 || port > 65535) {
            setImportResults((previous) => [
              {
                line,
                success: false,
                message: "入口端口格式错误，应为1-65535之间的数字",
              },
              ...previous,
            ]);
            continue;
          }
          portNumber = port;
        }

        try {
          const response = await createForward({
            name: name.trim(),
            tunnelId: selectedTunnelForImport,
            inPort: portNumber,
            remoteAddr: remoteAddr.trim(),
            strategy: "fifo",
          });

          setImportResults((previous) => [
            {
              line,
              success: response.code === 0,
              message: response.code === 0 ? "创建成功" : response.msg || "创建失败",
              forwardName: response.code === 0 ? name.trim() : undefined,
            },
            ...previous,
          ]);
        } catch (error) {
          setImportResults((previous) => [
            {
              line,
              success: false,
              message: "网络错误，创建失败",
            },
            ...previous,
          ]);
        }
      }

      toast.success("导入执行完成");
      await loadData(false);
    } catch (error) {
      console.error("导入失败:", error);
      toast.error("导入过程中发生错误");
    } finally {
      setImportLoading(false);
    }
  };

  const toggleRowSelection = (forwardId: number) => {
    setSelectedForwardIds((previous) => {
      const next = new Set(previous);
      if (next.has(forwardId)) {
        next.delete(forwardId);
      } else {
        next.add(forwardId);
      }
      return next;
    });
  };

  const toggleAllFilteredRows = () => {
    setSelectedForwardIds((previous) => {
      if (allFilteredSelected) {
        const next = new Set(previous);
        filteredRows.forEach((row) => next.delete(row.id));
        return next;
      }

      const next = new Set(previous);
      filteredRows.forEach((row) => next.add(row.id));
      return next;
    });
  };

  const handleCopyRule = async (forward: Forward) => {
    await copyToClipboard(`${forward.remoteAddr}|${forward.name}|${forward.inPort ?? ""}`, "规则");
  };

  const handleBatchExport = () => {
    if (selectedForwards.length === 0) {
      toast.error("请先选择要导出的规则");
      return;
    }

    openExportModalWithRows(selectedForwards, `导出已选规则（${selectedForwards.length}条）`);
  };

  const handleBatchDelete = async () => {
    if (selectedForwards.length === 0) {
      toast.error("请先选择要删除的规则");
      return;
    }

    const confirmed = window.confirm(`确定要删除已选中的 ${selectedForwards.length} 条转发吗？`);
    if (!confirmed) return;

    setBatchAction("delete");
    let successCount = 0;
    let failCount = 0;

    for (const forward of selectedForwards) {
      try {
        const response = await deleteForward(forward.id);
        if (response.code === 0) {
          successCount += 1;
        } else {
          const forceResponse = await forceDeleteForward(forward.id);
          if (forceResponse.code === 0) {
            successCount += 1;
          } else {
            failCount += 1;
          }
        }
      } catch (error) {
        failCount += 1;
      }
    }

    setBatchAction(null);
    setSelectedForwardIds(new Set());
    await loadData(false);

    if (successCount > 0) {
      toast.success(`批量删除完成，成功 ${successCount} 条`);
    }
    if (failCount > 0) {
      toast.error(`有 ${failCount} 条删除失败`);
    }
  };

  const handleBatchToggle = async (target: "start" | "pause") => {
    if (selectedForwards.length === 0) {
      toast.error("请先选择规则");
      return;
    }

    setBatchAction(target);
    let successCount = 0;
    let failCount = 0;

    for (const forward of selectedForwards) {
      if (forward.status !== 1 && forward.status !== 0) {
        failCount += 1;
        continue;
      }

      if (target === "start" && forward.serviceRunning) continue;
      if (target === "pause" && !forward.serviceRunning) continue;

      try {
        const response =
          target === "start"
            ? await resumeForwardService(forward.id)
            : await pauseForwardService(forward.id);
        if (response.code === 0) {
          successCount += 1;
        } else {
          failCount += 1;
        }
      } catch (error) {
        failCount += 1;
      }
    }

    setBatchAction(null);
    await loadData(false);

    if (successCount > 0) {
      toast.success(`${target === "start" ? "批量启动" : "批量暂停"}成功 ${successCount} 条`);
    }
    if (failCount > 0) {
      toast.error(`有 ${failCount} 条处理失败`);
    }
  };

  const renderRowName = (row: ForwardTableRow, forward: Forward) => {
    const statusDisplay = getStatusDisplay(row.status);
    const strategyDisplay = getStrategyDisplay(row.strategy);

    return (
      <div className="space-y-2">
        <button
          type="button"
          onClick={() => {
            if (isMobile) {
              setDetailForward(forward);
            }
          }}
          className="inline-flex max-w-full items-center rounded-full bg-sky-100 px-3 py-1 text-sm font-semibold text-sky-700 transition hover:bg-sky-200 dark:bg-sky-500/20 dark:text-sky-200 dark:hover:bg-sky-500/30"
        >
          <span className="truncate">{row.name}</span>
        </button>

        <div className="md:hidden flex flex-wrap items-center gap-2 text-xs text-default-500">
          <span>{row.tunnelName}</span>
          <Chip size="sm" variant="flat" color={statusDisplay.color as any}>
            {statusDisplay.text}
          </Chip>
          <Chip size="sm" variant="flat" color={strategyDisplay.color as any}>
            {strategyDisplay.text}
          </Chip>
        </div>
      </div>
    );
  };

  const renderFlowCell = (row: ForwardTableRow, forward: Forward) => (
    <div className="space-y-1 text-sm">
      <div className="font-semibold text-foreground">{row.totalFlowDisplay}</div>
      <div className="text-xs text-default-500">
        速率: {row.speedDisplay}
      </div>
      <div className="hidden md:flex gap-2 text-xs text-default-400">
        <span>↑ {formatFlow(forward.inFlow || 0)}</span>
        <span>↓ {formatFlow(forward.outFlow || 0)}</span>
      </div>
    </div>
  );

  const renderActionButtons = (forward: Forward) => (
    <div className="flex items-center justify-end gap-1">
      <Button
        isIconOnly
        size="sm"
        variant="light"
        color="warning"
        onPress={() => handleDiagnose(forward)}
        title="诊断"
      >
        <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
          <path
            fillRule="evenodd"
            d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
            clipRule="evenodd"
          />
        </svg>
      </Button>
      <Button
        isIconOnly
        size="sm"
        variant="light"
        color="default"
        onPress={() => handleCopyRule(forward)}
        title="复制"
      >
        <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
          <path d="M4 4a2 2 0 012-2h6a2 2 0 012 2v1h1a2 2 0 012 2v7a2 2 0 01-2 2H9a2 2 0 01-2-2v-1H6a2 2 0 01-2-2V4zm8 1V4H6v9h1V7a2 2 0 012-2h3zm-3 2v7h8V7H9z" />
        </svg>
      </Button>
      <Button
        isIconOnly
        size="sm"
        variant="light"
        color="primary"
        onPress={() => handleEdit(forward)}
        title="编辑"
      >
        <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
          <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
        </svg>
      </Button>
      <Button
        isIconOnly
        size="sm"
        variant="light"
        color="danger"
        onPress={() => handleDelete(forward)}
        title="删除"
      >
        <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
          <path
            fillRule="evenodd"
            d="M6 8a1 1 0 011 1v5a1 1 0 102 0V9a1 1 0 112 0v5a1 1 0 102 0V9a1 1 0 112 0v5a3 3 0 11-6 0V9a3 3 0 11-6 0v5a1 1 0 102 0V9a1 1 0 011-1zm9-3a1 1 0 110 2H5a1 1 0 110-2h2a3 3 0 016 0h2z"
            clipRule="evenodd"
          />
        </svg>
      </Button>
    </div>
  );

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="flex items-center gap-3">
          <Spinner size="sm" />
          <span className="text-default-600">正在加载...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 py-8 lg:px-6">
      <div className="space-y-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-foreground">转发管理</h1>
            <p className="text-sm text-default-500">统一用表格处理规则、筛选和批量操作</p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button color="primary" variant="shadow" onPress={handleImport}>
              批量导入
            </Button>
            <Button color="primary" onPress={handleAdd}>
              添加转发
            </Button>
          </div>
        </div>

        <Card className="border border-divider shadow-sm">
          <CardBody className="space-y-4">
            <Input
              placeholder="搜索转发规则..."
              value={searchValue}
              onValueChange={setSearchValue}
              variant="bordered"
              startContent={
                <svg className="h-4 w-4 text-default-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m21 21-4.35-4.35m1.85-5.15a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              }
            />

            <div className="flex flex-wrap gap-2">
              <Select
                aria-label="状态筛选"
                placeholder="全部"
                selectedKeys={[statusFilter]}
                onSelectionChange={(keys) => {
                  const key = Array.from(keys)[0] as ForwardTableFilterState["status"];
                  setStatusFilter(key || "all");
                }}
                variant="bordered"
                className="max-w-[160px]"
              >
                <SelectItem key="all">全部</SelectItem>
                <SelectItem key="active">正常</SelectItem>
                <SelectItem key="paused">暂停</SelectItem>
                <SelectItem key="error">异常</SelectItem>
              </Select>

              <Select
                aria-label="策略筛选"
                placeholder="类型"
                selectedKeys={[strategyFilter]}
                onSelectionChange={(keys) => {
                  const key = Array.from(keys)[0] as ForwardTableFilterState["strategy"];
                  setStrategyFilter(key || "all");
                }}
                variant="bordered"
                className="max-w-[160px]"
              >
                <SelectItem key="all">类型</SelectItem>
                <SelectItem key="fifo">主备</SelectItem>
                <SelectItem key="round">轮询</SelectItem>
                <SelectItem key="rand">随机</SelectItem>
              </Select>

              <Button
                variant="bordered"
                onPress={() => setFiltersExpanded((previous) => !previous)}
              >
                筛选
              </Button>

              <div className="ml-auto">
                <Button variant="flat" color="default" onPress={handleExport}>
                  导出
                </Button>
              </div>
            </div>

            {filtersExpanded && (
              <div className="grid gap-3 rounded-xl border border-divider bg-default-50/70 p-4 md:grid-cols-3">
                <Select
                  label="按隧道筛选"
                  selectedKeys={[String(tunnelFilter)]}
                  onSelectionChange={(keys) => {
                    const key = Array.from(keys)[0] as string;
                    setTunnelFilter(key === "all" ? "all" : Number.parseInt(key, 10));
                  }}
                  variant="bordered"
                >
                  <>
                    <SelectItem key="all">全部隧道</SelectItem>
                    {tunnels.map((tunnel) => (
                      <SelectItem key={String(tunnel.id)}>{tunnel.name}</SelectItem>
                    ))}
                  </>
                </Select>

                <Select
                  label="按用户筛选"
                  selectedKeys={[String(userFilter)]}
                  onSelectionChange={(keys) => {
                    const key = Array.from(keys)[0] as string;
                    setUserFilter(key === "all" ? "all" : Number.parseInt(key, 10));
                  }}
                  variant="bordered"
                >
                  <>
                    <SelectItem key="all">全部用户</SelectItem>
                    {userOptions.map((user) => (
                      <SelectItem key={String(user.id)}>{user.name}</SelectItem>
                    ))}
                  </>
                </Select>

                <div className="flex items-center justify-between rounded-xl border border-divider bg-white px-4 py-3 dark:bg-content1">
                  <div>
                    <div className="text-sm font-medium text-foreground">只看多目标地址</div>
                    <div className="text-xs text-default-500">快速筛出需要负载策略的规则</div>
                  </div>
                  <Switch isSelected={multiTargetOnly} onValueChange={setMultiTargetOnly} />
                </div>
              </div>
            )}

            {selectedForwardIds.size > 0 && (
              <div className="flex flex-col gap-3 rounded-2xl border border-primary/20 bg-primary/5 p-4 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="text-sm font-semibold text-foreground">已选择 {selectedForwardIds.size} 条规则</div>
                  <div className="text-xs text-default-500">可以直接做批量导出、启停和删除</div>
                </div>

                <div className="flex flex-wrap gap-2">
                  <Button variant="flat" color="default" onPress={handleBatchExport}>
                    批量导出
                  </Button>
                  <Button
                    variant="flat"
                    color="success"
                    onPress={() => handleBatchToggle("start")}
                    isLoading={batchAction === "start"}
                  >
                    批量启动
                  </Button>
                  <Button
                    variant="flat"
                    color="warning"
                    onPress={() => handleBatchToggle("pause")}
                    isLoading={batchAction === "pause"}
                  >
                    批量暂停
                  </Button>
                  <Button
                    variant="flat"
                    color="danger"
                    onPress={handleBatchDelete}
                    isLoading={batchAction === "delete"}
                  >
                    批量删除
                  </Button>
                </div>
              </div>
            )}

            <div className="overflow-hidden rounded-2xl border border-divider">
              {filteredRows.length === 0 ? (
                <div className="flex min-h-[280px] flex-col items-center justify-center gap-3 bg-content1 px-6 py-12 text-center">
                  <div className="rounded-full bg-default-100 p-4">
                    <svg className="h-8 w-8 text-default-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 9l4-4 4 4m0 6l-4 4-4-4" />
                    </svg>
                  </div>
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">暂无转发规则</h3>
                    <p className="mt-1 text-sm text-default-500">试试调整筛选条件，或者先创建一条新的转发规则。</p>
                  </div>
                </div>
              ) : (
                <div className="overflow-x-auto bg-content1">
                  <table className="min-w-full border-collapse">
                    <thead className="bg-default-50">
                      <tr className="text-left text-sm text-default-600">
                        <th className="w-12 px-4 py-3">
                          <input
                            type="checkbox"
                            checked={allFilteredSelected}
                            onChange={toggleAllFilteredRows}
                            className="h-4 w-4 rounded border-default-300 text-primary"
                          />
                        </th>
                        <th className="w-24 px-4 py-3 font-medium">启用</th>
                        <th className="px-4 py-3 font-medium">规则名称</th>
                        <th className="hidden px-4 py-3 font-medium md:table-cell">隧道</th>
                        <th className="hidden px-4 py-3 font-medium lg:table-cell">入口地址</th>
                        <th className="hidden px-4 py-3 font-medium lg:table-cell">目标地址</th>
                        <th className="px-4 py-3 font-medium">流量统计</th>
                        <th className="hidden px-4 py-3 font-medium md:table-cell">状态</th>
                        <th className="px-4 py-3 text-right font-medium">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pagedRows.map((row) => {
                        const forward = rawForwardMap.get(row.id);
                        if (!forward) return null;

                        const statusDisplay = getStatusDisplay(row.status);
                        const strategyDisplay = getStrategyDisplay(row.strategy);

                        return (
                          <tr key={row.id} className="border-t border-divider text-sm">
                            <td className="px-4 py-4 align-top">
                              <input
                                type="checkbox"
                                checked={selectedForwardIds.has(row.id)}
                                onChange={() => toggleRowSelection(row.id)}
                                className="mt-1 h-4 w-4 rounded border-default-300 text-primary"
                              />
                            </td>
                            <td className="px-4 py-4 align-top">
                              <Switch
                                size="sm"
                                isSelected={forward.serviceRunning}
                                onValueChange={() => handleServiceToggle(forward)}
                                isDisabled={forward.status !== 1 && forward.status !== 0}
                              />
                            </td>
                            <td className="px-4 py-4 align-top">
                              {renderRowName(row, forward)}
                            </td>
                            <td className="hidden px-4 py-4 align-top md:table-cell">
                              <div className="space-y-2">
                                <div className="font-medium text-foreground">{row.tunnelName}</div>
                                <div className="text-xs text-default-500">{row.userName}</div>
                              </div>
                            </td>
                            <td className="hidden px-4 py-4 align-top lg:table-cell">
                              <button
                                type="button"
                                className="max-w-[220px] truncate text-left text-default-700 transition hover:text-primary"
                                title={formatInAddress(forward.inIp, forward.inPort)}
                                onClick={() => showAddressModal(forward.inIp, forward.inPort, "入口地址")}
                              >
                                {formatInAddress(forward.inIp, forward.inPort)}
                              </button>
                            </td>
                            <td className="hidden px-4 py-4 align-top lg:table-cell">
                              <button
                                type="button"
                                className="max-w-[240px] truncate text-left text-default-700 transition hover:text-primary"
                                title={formatRemoteAddress(forward.remoteAddr)}
                                onClick={() => showAddressModal(forward.remoteAddr, null, "目标地址")}
                              >
                                {formatRemoteAddress(forward.remoteAddr)}
                              </button>
                            </td>
                            <td className="px-4 py-4 align-top">
                              {renderFlowCell(row, forward)}
                            </td>
                            <td className="hidden px-4 py-4 align-top md:table-cell">
                              <div className="space-y-2">
                                <Chip color={statusDisplay.color as any} size="sm" variant="flat">
                                  {statusDisplay.text}
                                </Chip>
                                <Chip color={strategyDisplay.color as any} size="sm" variant="flat">
                                  {strategyDisplay.text}
                                </Chip>
                              </div>
                            </td>
                            <td className="px-4 py-4 align-top">
                              {renderActionButtons(forward)}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="flex items-center justify-between rounded-2xl border border-divider bg-content1 px-4 py-3">
              <Button
                variant="light"
                isIconOnly
                onPress={() => setCurrentPage((previous) => Math.max(1, previous - 1))}
                isDisabled={currentPage === 1}
              >
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m15 19-7-7 7-7" />
                </svg>
              </Button>

              <div className="text-sm font-medium text-default-600">
                Page {currentPage} of {totalPages}
              </div>

              <Button
                variant="light"
                isIconOnly
                onPress={() => setCurrentPage((previous) => Math.min(totalPages, previous + 1))}
                isDisabled={currentPage === totalPages}
              >
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m9 5 7 7-7 7" />
                </svg>
              </Button>
            </div>
          </CardBody>
        </Card>
      </div>

      <Modal
        isOpen={modalOpen}
        onOpenChange={setModalOpen}
        size="2xl"
        scrollBehavior="outside"
        backdrop="blur"
        placement="center"
      >
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader className="flex flex-col gap-1">
                <h2 className="text-xl font-bold">{isEdit ? "编辑转发" : "新增转发"}</h2>
                <p className="text-small text-default-500">
                  {isEdit ? "修改现有转发配置的信息" : "创建新的转发配置"}
                </p>
              </ModalHeader>
              <ModalBody>
                <div className="space-y-4 pb-4">
                  <Input
                    label="转发名称"
                    placeholder="请输入转发名称"
                    value={form.name}
                    onChange={(event) => setForm((previous) => ({ ...previous, name: event.target.value }))}
                    isInvalid={!!errors.name}
                    errorMessage={errors.name}
                    variant="bordered"
                  />

                  <Select
                    label="选择隧道"
                    placeholder="请选择关联的隧道"
                    selectedKeys={form.tunnelId ? [String(form.tunnelId)] : []}
                    onSelectionChange={(keys) => {
                      const selectedKey = Array.from(keys)[0] as string;
                      if (selectedKey) handleTunnelChange(selectedKey);
                    }}
                    isInvalid={!!errors.tunnelId}
                    errorMessage={errors.tunnelId}
                    variant="bordered"
                    isDisabled={isEdit}
                    description={isEdit ? "编辑时无法修改关联隧道" : undefined}
                  >
                    {tunnels.map((tunnel) => (
                      <SelectItem key={String(tunnel.id)}>{tunnel.name}</SelectItem>
                    ))}
                  </Select>

                  <Input
                    label="入口端口"
                    placeholder="留空则自动分配可用端口"
                    type="number"
                    value={form.inPort !== null ? String(form.inPort) : ""}
                    onChange={(event) => {
                      const value = event.target.value;
                      setForm((previous) => ({ ...previous, inPort: value ? Number.parseInt(value, 10) : null }));
                    }}
                    isInvalid={!!errors.inPort}
                    errorMessage={errors.inPort}
                    variant="bordered"
                    description="指定入口端口，留空则从节点可用端口中自动分配"
                  />

                  <Textarea
                    label="远程地址"
                    placeholder="请输入远程地址，多个地址用换行分隔"
                    value={form.remoteAddr}
                    onChange={(event) => setForm((previous) => ({ ...previous, remoteAddr: event.target.value }))}
                    isInvalid={!!errors.remoteAddr}
                    errorMessage={errors.remoteAddr}
                    variant="bordered"
                    description="格式: IP:端口 或 域名:端口，支持多个地址（每行一个）"
                    minRows={3}
                    maxRows={6}
                  />

                  {getAddressCount(form.remoteAddr) > 1 && (
                    <Select
                      label="负载策略"
                      placeholder="请选择负载均衡策略"
                      selectedKeys={[form.strategy]}
                      onSelectionChange={(keys) => {
                        const selectedKey = Array.from(keys)[0] as string;
                        setForm((previous) => ({ ...previous, strategy: selectedKey }));
                      }}
                      variant="bordered"
                      description="多个目标地址的负载均衡策略"
                    >
                      <SelectItem key="fifo">主备模式 - 自上而下</SelectItem>
                      <SelectItem key="round">轮询模式 - 依次轮换</SelectItem>
                      <SelectItem key="rand">随机模式 - 随机选择</SelectItem>
                      <SelectItem key="hash">哈希模式 - IP哈希</SelectItem>
                    </Select>
                  )}
                </div>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>
                  取消
                </Button>
                <Button color="primary" onPress={handleSubmit} isLoading={submitLoading}>
                  {isEdit ? "保存修改" : "创建转发"}
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      <Modal isOpen={deleteModalOpen} onOpenChange={setDeleteModalOpen} size="2xl" scrollBehavior="outside" backdrop="blur" placement="center">
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader className="flex flex-col gap-1">
                <h2 className="text-lg font-bold text-danger">确认删除</h2>
              </ModalHeader>
              <ModalBody>
                <p className="text-default-600">
                  确定要删除转发 <span className="font-semibold text-foreground">"{forwardToDelete?.name}"</span> 吗？
                </p>
                <p className="mt-2 text-small text-default-500">此操作无法撤销，删除后该转发将永久消失。</p>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>
                  取消
                </Button>
                <Button color="danger" onPress={confirmDelete} isLoading={deleteLoading}>
                  确认删除
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      <Modal isOpen={addressModalOpen} onClose={() => setAddressModalOpen(false)} size="lg" scrollBehavior="outside">
        <ModalContent>
          <ModalHeader className="text-base">{addressModalTitle}</ModalHeader>
          <ModalBody className="pb-6">
            <div className="mb-4 text-right">
              <Button size="sm" onPress={copyAllAddresses}>
                复制
              </Button>
            </div>
            <div className="max-h-60 space-y-2 overflow-y-auto">
              {addressList.map((item) => (
                <div key={item.id} className="flex items-center justify-between rounded-lg border border-default-200 p-3 dark:border-default-100">
                  <code className="mr-3 flex-1 text-sm text-foreground">{item.address}</code>
                  <Button size="sm" variant="light" isLoading={item.copying} onPress={() => copyAddress(item)}>
                    复制
                  </Button>
                </div>
              ))}
            </div>
          </ModalBody>
        </ModalContent>
      </Modal>

      <Modal
        isOpen={exportModalOpen}
        onClose={() => {
          setExportModalOpen(false);
          setSelectedTunnelForExport(null);
          setExportData("");
          setExportMode("tunnel");
          setExportModalTitle("导出转发数据");
        }}
        size="2xl"
        scrollBehavior="outside"
        backdrop="blur"
        placement="center"
      >
        <ModalContent>
          <ModalHeader className="flex flex-col gap-1">
            <h2 className="text-xl font-bold">{exportModalTitle}</h2>
            <p className="text-small text-default-500">格式：目标地址|转发名称|入口端口</p>
          </ModalHeader>
          <ModalBody className="pb-6">
            <div className="space-y-4">
              {exportMode === "tunnel" && (
                <Select
                  label="选择导出隧道"
                  placeholder="请选择要导出的隧道"
                  selectedKeys={selectedTunnelForExport ? [String(selectedTunnelForExport)] : []}
                  onSelectionChange={(keys) => {
                    const selectedKey = Array.from(keys)[0] as string;
                    setSelectedTunnelForExport(selectedKey ? Number.parseInt(selectedKey, 10) : null);
                  }}
                  variant="bordered"
                >
                  {tunnels.map((tunnel) => (
                    <SelectItem key={String(tunnel.id)}>{tunnel.name}</SelectItem>
                  ))}
                </Select>
              )}

              {exportMode === "tunnel" && !exportData && (
                <div className="text-right">
                  <Button color="primary" onPress={executeExport} isLoading={exportLoading} isDisabled={!selectedTunnelForExport}>
                    生成导出数据
                  </Button>
                </div>
              )}

              {exportData && (
                <>
                  <div className="flex justify-between">
                    {exportMode === "tunnel" ? (
                      <Button color="primary" variant="flat" onPress={executeExport} isLoading={exportLoading} isDisabled={!selectedTunnelForExport}>
                        重新生成
                      </Button>
                    ) : (
                      <div />
                    )}
                    <Button color="secondary" variant="flat" onPress={copyExportData}>
                      复制
                    </Button>
                  </div>

                  <Textarea
                    value={exportData}
                    readOnly
                    variant="bordered"
                    minRows={10}
                    maxRows={20}
                    classNames={{ input: "font-mono text-sm" }}
                  />
                </>
              )}
            </div>
          </ModalBody>
          <ModalFooter>
            <Button variant="light" onPress={() => setExportModalOpen(false)}>
              关闭
            </Button>
          </ModalFooter>
        </ModalContent>
      </Modal>

      <Modal isOpen={importModalOpen} onClose={() => setImportModalOpen(false)} size="2xl" scrollBehavior="outside" backdrop="blur" placement="center">
        <ModalContent>
          <ModalHeader className="flex flex-col gap-1">
            <h2 className="text-xl font-bold">导入转发数据</h2>
            <p className="text-small text-default-500">格式：目标地址|转发名称|入口端口，每行一个，入口端口留空将自动分配可用端口</p>
            <p className="text-small text-default-400">目标地址支持单个地址或多个地址用逗号分隔</p>
          </ModalHeader>
          <ModalBody className="pb-6">
            <div className="space-y-4">
              <Select
                label="选择导入隧道"
                placeholder="请选择要导入的隧道"
                selectedKeys={selectedTunnelForImport ? [String(selectedTunnelForImport)] : []}
                onSelectionChange={(keys) => {
                  const selectedKey = Array.from(keys)[0] as string;
                  setSelectedTunnelForImport(selectedKey ? Number.parseInt(selectedKey, 10) : null);
                }}
                variant="bordered"
              >
                {tunnels.map((tunnel) => (
                  <SelectItem key={String(tunnel.id)}>{tunnel.name}</SelectItem>
                ))}
              </Select>

              <Textarea
                label="导入数据"
                placeholder="请输入要导入的转发数据，格式：目标地址|转发名称|入口端口"
                value={importData}
                onChange={(event) => setImportData(event.target.value)}
                variant="flat"
                minRows={8}
                maxRows={12}
                classNames={{ input: "font-mono text-sm" }}
              />

              {importResults.length > 0 && (
                <div>
                  <div className="mb-2 flex items-center justify-between">
                    <h3 className="text-base font-semibold">导入结果</h3>
                    <span className="text-xs text-default-500">
                      成功：{importResults.filter((result) => result.success).length} / 总计：{importResults.length}
                    </span>
                  </div>

                  <div className="max-h-40 space-y-1 overflow-y-auto">
                    {importResults.map((result, index) => (
                      <div
                        key={index}
                        className={`rounded border p-2 ${
                          result.success
                            ? "border-success-200 bg-success-50 dark:border-success-300/20 dark:bg-success-100/10"
                            : "border-danger-200 bg-danger-50 dark:border-danger-300/20 dark:bg-danger-100/10"
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <div className="flex-1 min-w-0">
                            <div className="mb-0.5 flex items-center gap-2">
                              <span className={`text-xs font-medium ${result.success ? "text-success-700 dark:text-success-300" : "text-danger-700 dark:text-danger-300"}`}>
                                {result.success ? "成功" : "失败"}
                              </span>
                              <span className="text-xs text-default-500">|</span>
                              <code className="truncate text-xs text-default-600">{result.line}</code>
                            </div>
                            <div className={`text-xs ${result.success ? "text-success-600 dark:text-success-400" : "text-danger-600 dark:text-danger-400"}`}>
                              {result.message}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </ModalBody>
          <ModalFooter>
            <Button variant="light" onPress={() => setImportModalOpen(false)}>
              关闭
            </Button>
            <Button color="warning" onPress={executeImport} isLoading={importLoading} isDisabled={!importData.trim() || !selectedTunnelForImport}>
              开始导入
            </Button>
          </ModalFooter>
        </ModalContent>
      </Modal>

      <Modal
        isOpen={Boolean(detailForward)}
        onOpenChange={(open) => {
          if (!open) setDetailForward(null);
        }}
        size="lg"
        scrollBehavior="outside"
        backdrop="blur"
        placement="center"
      >
        <ModalContent>
          {detailForward && (
            <>
              <ModalHeader className="flex flex-col gap-1">
                <h2 className="text-xl font-bold">{detailForward.name}</h2>
                <p className="text-small text-default-500">移动端详情</p>
              </ModalHeader>
              <ModalBody>
                <div className="space-y-4 pb-4">
                  <div className="rounded-xl border border-divider p-4">
                    <div className="mb-3 flex items-center gap-2">
                      <Chip color={getStatusDisplay(detailForward.status).color as any} size="sm" variant="flat">
                        {getStatusDisplay(detailForward.status).text}
                      </Chip>
                      <Chip color={getStrategyDisplay(detailForward.strategy).color as any} size="sm" variant="flat">
                        {getStrategyDisplay(detailForward.strategy).text}
                      </Chip>
                    </div>

                    <div className="space-y-3 text-sm">
                      <div>
                        <div className="text-xs text-default-500">隧道</div>
                        <div className="font-medium text-foreground">{detailForward.tunnelName}</div>
                      </div>
                      <div>
                        <div className="text-xs text-default-500">入口地址</div>
                        <button type="button" className="font-medium text-primary" onClick={() => showAddressModal(detailForward.inIp, detailForward.inPort, "入口地址")}>
                          {formatInAddress(detailForward.inIp, detailForward.inPort)}
                        </button>
                      </div>
                      <div>
                        <div className="text-xs text-default-500">目标地址</div>
                        <button type="button" className="font-medium text-primary" onClick={() => showAddressModal(detailForward.remoteAddr, null, "目标地址")}>
                          {formatRemoteAddress(detailForward.remoteAddr)}
                        </button>
                      </div>
                      <div>
                        <div className="text-xs text-default-500">总流量</div>
                        <div className="font-medium text-foreground">{formatFlow((detailForward.inFlow || 0) + (detailForward.outFlow || 0))}</div>
                      </div>
                      <div>
                        <div className="text-xs text-default-500">创建时间</div>
                        <div className="font-medium text-foreground">{detailForward.createdTime || "-"}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={() => setDetailForward(null)}>
                  关闭
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      <Modal
        isOpen={diagnosisModalOpen}
        onOpenChange={setDiagnosisModalOpen}
        size="4xl"
        scrollBehavior="inside"
        backdrop="blur"
        placement="center"
        classNames={{
          base: "rounded-2xl",
          header: "rounded-t-2xl",
          body: "rounded-none",
          footer: "rounded-b-2xl",
        }}
      >
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader className="flex flex-col gap-1 border-b border-divider bg-content1">
                <h2 className="text-xl font-bold">转发诊断结果</h2>
                {currentDiagnosisForward && (
                  <div className="flex min-w-0 items-center gap-2">
                    <span className="min-w-0 flex-1 truncate text-small text-default-500">{currentDiagnosisForward.name}</span>
                    <Chip color="primary" variant="flat" size="sm">
                      转发服务
                    </Chip>
                  </div>
                )}
              </ModalHeader>
              <ModalBody className="bg-content1">
                {diagnosisLoading ? (
                  <div className="flex justify-center py-16">
                    <div className="flex items-center gap-3">
                      <Spinner size="sm" />
                      <span className="text-default-600">正在诊断...</span>
                    </div>
                  </div>
                ) : diagnosisResult ? (
                  <div className="space-y-4">
                    <div className="grid grid-cols-3 gap-3">
                      <div className="rounded-lg border border-divider bg-default-100 p-3 text-center dark:bg-gray-800">
                        <div className="text-2xl font-bold text-foreground">{diagnosisResult.results.length}</div>
                        <div className="mt-1 text-xs text-default-500">总测试数</div>
                      </div>
                      <div className="rounded-lg border border-success-200 bg-success-50 p-3 text-center dark:border-success-700 dark:bg-success-900/20">
                        <div className="text-2xl font-bold text-success-600 dark:text-success-400">
                          {diagnosisResult.results.filter((result) => result.success).length}
                        </div>
                        <div className="mt-1 text-xs text-success-600 dark:text-success-400/80">成功</div>
                      </div>
                      <div className="rounded-lg border border-danger-200 bg-danger-50 p-3 text-center dark:border-danger-700 dark:bg-danger-900/20">
                        <div className="text-2xl font-bold text-danger-600 dark:text-danger-400">
                          {diagnosisResult.results.filter((result) => !result.success).length}
                        </div>
                        <div className="mt-1 text-xs text-danger-600 dark:text-danger-400/80">失败</div>
                      </div>
                    </div>

                    <div className="hidden space-y-3 md:block">
                      {(() => {
                        const groupedResults = {
                          entry: diagnosisResult.results.filter((result) => result.fromChainType === 1),
                          chains: {} as Record<number, typeof diagnosisResult.results>,
                          exit: diagnosisResult.results.filter((result) => result.fromChainType === 3),
                        };

                        diagnosisResult.results.forEach((result) => {
                          if (result.fromChainType === 2 && result.fromInx != null) {
                            if (!groupedResults.chains[result.fromInx]) {
                              groupedResults.chains[result.fromInx] = [];
                            }
                            groupedResults.chains[result.fromInx].push(result);
                          }
                        });

                        const renderTableSection = (title: string, results: typeof diagnosisResult.results) => {
                          if (results.length === 0) return null;

                          return (
                            <div key={title} className="overflow-hidden rounded-lg border border-divider bg-white dark:bg-gray-800">
                              <div className="border-b border-divider bg-primary/10 px-3 py-2 dark:bg-primary/20">
                                <h3 className="text-sm font-semibold text-primary">{title}</h3>
                              </div>
                              <table className="w-full text-sm">
                                <thead className="bg-default-100 dark:bg-gray-700">
                                  <tr>
                                    <th className="px-3 py-2 text-left text-xs font-semibold">路径</th>
                                    <th className="w-20 px-3 py-2 text-center text-xs font-semibold">状态</th>
                                    <th className="w-24 px-3 py-2 text-center text-xs font-semibold">延迟(ms)</th>
                                    <th className="w-24 px-3 py-2 text-center text-xs font-semibold">丢包率</th>
                                    <th className="w-20 px-3 py-2 text-center text-xs font-semibold">质量</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-divider bg-white dark:bg-gray-800">
                                  {results.map((result, index) => {
                                    const quality = getQualityDisplay(result.averageTime, result.packetLoss);

                                    return (
                                      <tr key={index} className={result.success ? "hover:bg-default-50 dark:hover:bg-gray-700/50" : "bg-danger-50 hover:bg-danger-100/70 dark:bg-danger-900/30"}>
                                        <td className="px-3 py-2">
                                          <div className="flex items-center gap-2">
                                            <span className={`flex h-5 w-5 items-center justify-center rounded-full text-xs text-white ${result.success ? "bg-success" : "bg-danger"}`}>
                                              {result.success ? "✓" : "✗"}
                                            </span>
                                            <div className="min-w-0 flex-1">
                                              <div className="truncate font-medium text-foreground">{result.description}</div>
                                              <div className="truncate text-xs text-default-500">
                                                {result.targetIp}:{result.targetPort}
                                              </div>
                                            </div>
                                          </div>
                                        </td>
                                        <td className="px-3 py-2 text-center">
                                          <Chip color={result.success ? "success" : "danger"} variant="flat" size="sm">
                                            {result.success ? "成功" : "失败"}
                                          </Chip>
                                        </td>
                                        <td className="px-3 py-2 text-center">
                                          {result.success ? <span className="font-semibold text-primary">{result.averageTime?.toFixed(0)}</span> : <span className="text-default-400">-</span>}
                                        </td>
                                        <td className="px-3 py-2 text-center">
                                          {result.success ? (
                                            <span className={`font-semibold ${(result.packetLoss || 0) > 0 ? "text-warning" : "text-success"}`}>
                                              {result.packetLoss?.toFixed(1)}%
                                            </span>
                                          ) : (
                                            <span className="text-default-400">-</span>
                                          )}
                                        </td>
                                        <td className="px-3 py-2 text-center">
                                          {result.success && quality ? (
                                            <Chip color={quality.color as any} variant="flat" size="sm" className="text-xs">
                                              {quality.text}
                                            </Chip>
                                          ) : (
                                            <span className="text-default-400">-</span>
                                          )}
                                        </td>
                                      </tr>
                                    );
                                  })}
                                </tbody>
                              </table>
                            </div>
                          );
                        };

                        return (
                          <>
                            {renderTableSection("入口测试", groupedResults.entry)}
                            {Object.keys(groupedResults.chains)
                              .map(Number)
                              .sort((left, right) => left - right)
                              .map((hop) => renderTableSection(`转发链 - 第${hop}跳`, groupedResults.chains[hop]))}
                            {renderTableSection("出口测试", groupedResults.exit)}
                          </>
                        );
                      })()}
                    </div>

                    <div className="space-y-3 md:hidden">
                      {(() => {
                        const groupedResults = {
                          entry: diagnosisResult.results.filter((result) => result.fromChainType === 1),
                          chains: {} as Record<number, typeof diagnosisResult.results>,
                          exit: diagnosisResult.results.filter((result) => result.fromChainType === 3),
                        };

                        diagnosisResult.results.forEach((result) => {
                          if (result.fromChainType === 2 && result.fromInx != null) {
                            if (!groupedResults.chains[result.fromInx]) {
                              groupedResults.chains[result.fromInx] = [];
                            }
                            groupedResults.chains[result.fromInx].push(result);
                          }
                        });

                        const renderCardSection = (title: string, results: typeof diagnosisResult.results) => {
                          if (results.length === 0) return null;

                          return (
                            <div key={title} className="space-y-2">
                              <div className="rounded-lg border border-primary/30 bg-primary/10 px-2 py-1.5 dark:bg-primary/20">
                                <h3 className="text-sm font-semibold text-primary">{title}</h3>
                              </div>
                              {results.map((result, index) => {
                                const quality = getQualityDisplay(result.averageTime, result.packetLoss);

                                return (
                                  <div key={index} className={`rounded-lg border p-3 ${result.success ? "border-divider bg-white dark:bg-gray-800" : "border-danger-200 bg-danger-50 dark:border-danger-300/30 dark:bg-danger-900/30"}`}>
                                    <div className="mb-2 flex items-start gap-2">
                                      <span className={`flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full text-xs text-white ${result.success ? "bg-success" : "bg-danger"}`}>
                                        {result.success ? "✓" : "✗"}
                                      </span>
                                      <div className="min-w-0 flex-1">
                                        <div className="break-words text-sm font-semibold text-foreground">{result.description}</div>
                                        <div className="mt-0.5 break-all text-xs text-default-500">
                                          {result.targetIp}:{result.targetPort}
                                        </div>
                                      </div>
                                      <Chip color={result.success ? "success" : "danger"} variant="flat" size="sm">
                                        {result.success ? "成功" : "失败"}
                                      </Chip>
                                    </div>

                                    {result.success ? (
                                      <div className="mt-2 grid grid-cols-3 gap-2 border-t border-divider pt-2">
                                        <div className="text-center">
                                          <div className="text-lg font-bold text-primary">{result.averageTime?.toFixed(0)}</div>
                                          <div className="text-xs text-default-500">延迟(ms)</div>
                                        </div>
                                        <div className="text-center">
                                          <div className={`text-lg font-bold ${(result.packetLoss || 0) > 0 ? "text-warning" : "text-success"}`}>
                                            {result.packetLoss?.toFixed(1)}%
                                          </div>
                                          <div className="text-xs text-default-500">丢包率</div>
                                        </div>
                                        <div className="text-center">
                                          {quality && (
                                            <>
                                              <Chip color={quality.color as any} variant="flat" size="sm" className="text-xs">
                                                {quality.text}
                                              </Chip>
                                              <div className="mt-0.5 text-xs text-default-500">质量</div>
                                            </>
                                          )}
                                        </div>
                                      </div>
                                    ) : (
                                      <div className="mt-2 border-t border-divider pt-2 text-xs text-danger">
                                        {result.message || "连接失败"}
                                      </div>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          );
                        };

                        return (
                          <>
                            {renderCardSection("入口测试", groupedResults.entry)}
                            {Object.keys(groupedResults.chains)
                              .map(Number)
                              .sort((left, right) => left - right)
                              .map((hop) => renderCardSection(`转发链 - 第${hop}跳`, groupedResults.chains[hop]))}
                            {renderCardSection("出口测试", groupedResults.exit)}
                          </>
                        );
                      })()}
                    </div>

                    {diagnosisResult.results.some((result) => !result.success) && (
                      <div className="hidden space-y-2 md:block">
                        <h4 className="text-sm font-semibold text-danger">失败详情</h4>
                        <div className="space-y-2">
                          {diagnosisResult.results
                            .filter((result) => !result.success)
                            .map((result, index) => (
                              <Alert
                                key={index}
                                color="danger"
                                variant="flat"
                                title={result.description}
                                description={result.message || "连接失败"}
                                className="text-xs"
                              />
                            ))}
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="py-16 text-center">
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-default-100">
                      <svg className="h-8 w-8 text-default-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                    </div>
                    <h3 className="text-lg font-semibold text-foreground">暂无诊断数据</h3>
                  </div>
                )}
              </ModalBody>
              <ModalFooter className="border-t border-divider bg-content1">
                <Button variant="light" onPress={onClose}>
                  关闭
                </Button>
                {currentDiagnosisForward && (
                  <Button color="primary" onPress={() => handleDiagnose(currentDiagnosisForward)} isLoading={diagnosisLoading}>
                    重新诊断
                  </Button>
                )}
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>
    </div>
  );
}
