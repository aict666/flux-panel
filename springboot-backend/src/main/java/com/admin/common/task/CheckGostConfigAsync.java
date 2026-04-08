package com.admin.common.task;

import com.admin.common.dto.*;
import com.admin.common.utils.GostUtil;
import com.admin.entity.*;
import com.admin.service.*;
import com.alibaba.fastjson.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class CheckGostConfigAsync {

    @Resource
    private NodeService nodeService;

    @Resource
    @Lazy
    private ForwardService forwardService;

    @Resource
    @Lazy
    private SpeedLimitService speedLimitService;

    @Resource
    TunnelService tunnelService;



    /**
     * 清理孤立的Gost配置项
     */
    @Async
    public void cleanNodeConfigs(Long nodeId, GostConfigDto gostConfig) {
        if (nodeId == null || gostConfig == null) {
            return;
        }
        Node node = nodeService.getById(nodeId);
        if (node != null) {
            cleanOrphanedServices(gostConfig.getServices(), node);
            cleanOrphanedChains(gostConfig.getChains(), node);
            cleanOrphanedLimiters(gostConfig.getLimiters(), node);
        }
    }

    /**
     * 清理孤立的服务
     */
    private void cleanOrphanedServices(List<ConfigItem> configItems, Node node) {
        if (configItems == null) return;
        for (ConfigItem service : configItems) {
            safeExecute(() -> {
                RuntimeServiceRef serviceRef = parseRuntimeServiceRef(service.getName());
                if (serviceRef == null) {
                    return;
                }

                JSONArray services = new JSONArray();
                if (serviceRef.kind == RuntimeServiceKind.TUNNEL_TLS) {
                    services.add(serviceRef.baseServiceName + "_tls");

                    Tunnel tunnel = tunnelService.getById(serviceRef.targetId);
                    if (tunnel == null) {
                        GostUtil.DeleteService(node.getId(), services);
                        log.info("删除孤立的服务: {} (节点: {})", service.getName(), node.getId());
                    }
                    return;
                }

                services.add(serviceRef.baseServiceName + "_tcp");
                services.add(serviceRef.baseServiceName + "_udp");
                Forward forward = forwardService.getById(serviceRef.targetId);
                if (forward == null) {
                    GostUtil.DeleteService(node.getId(), services);
                    log.info("删除孤立的服务: {} (节点: {})", service.getName(), node.getId());
                }
            }, "清理服务 " + service.getName());
        }

    }

    /**
     * 清理孤立的链
     */
    private void cleanOrphanedChains(List<ConfigItem> configItems, Node node) {
        if (configItems == null) return;
        for (ConfigItem chain : configItems) {
            safeExecute(() -> {
                Long tunnelId = parseChainTunnelId(chain.getName());
                if (tunnelId == null) {
                    return;
                }
                Tunnel tunnel = tunnelService.getById(tunnelId);
                if (tunnel == null) {
                    GostUtil.DeleteChains(node.getId(), chain.getName());
                    log.info("删除孤立的链: {} (节点: {})", chain.getName(), node.getId());
                }
            }, "清理链 " + chain.getName());
        }
    }

    /**
     * 清理孤立的限流器
     */
    private void cleanOrphanedLimiters(List<ConfigItem> configItems, Node node) {
        if (configItems == null) return;
        

        for (ConfigItem limiter : configItems) {
            safeExecute(() -> {
                Long limiterId = parseLongId(limiter.getName(), "限流器", limiter.getName());
                if (limiterId == null) {
                    return;
                }
                SpeedLimit speedLimit = speedLimitService.getById(limiterId);
                if (speedLimit == null) {
                    GostUtil.DeleteLimiters(node.getId(), limiterId);
                    log.info("删除孤立的限流器: {} (节点: {})", limiter.getName(), node.getId());
                }
            }, "清理限流器 " + limiter.getName());
        }
    }


    /**
     * 安全执行操作，捕获异常
     */
    private void safeExecute(Runnable operation, String operationDesc) {
        try {
            operation.run();
        } catch (Exception e) {
            log.info("执行操作失败: {}", operationDesc, e);
        }
    }


    /**
     * 解析服务名称
     */
    private List<String> parseServiceName(String serviceName) {
        String[] split = serviceName.split("_");
        return new ArrayList<>(Arrays.asList(split));
    }

    private RuntimeServiceRef parseRuntimeServiceRef(String serviceName) {
        if (Objects.equals(serviceName, "web_api")) {
            return null;
        }

        List<String> serviceIds = parseServiceName(serviceName);
        if (serviceIds.size() == 2 && Objects.equals(serviceIds.getLast(), "tls")) {
            Long tunnelId = parseLongId(serviceIds.getFirst(), "TLS 服务", serviceName);
            if (tunnelId == null) {
                return null;
            }
            return new RuntimeServiceRef(RuntimeServiceKind.TUNNEL_TLS, tunnelId, tunnelId.toString());
        }

        if (serviceIds.size() == 4 && (Objects.equals(serviceIds.getLast(), "tcp") || Objects.equals(serviceIds.getLast(), "udp"))) {
            Long forwardId = parseLongId(serviceIds.getFirst(), "转发服务", serviceName);
            if (forwardId == null) {
                return null;
            }
            String baseServiceName = forwardId + "_" + serviceIds.get(1) + "_" + serviceIds.get(2);
            return new RuntimeServiceRef(RuntimeServiceKind.FORWARD_STREAM, forwardId, baseServiceName);
        }

        log.info("跳过无法解析的服务配置项: {}", serviceName);
        return null;
    }

    private Long parseChainTunnelId(String chainName) {
        List<String> serviceIds = parseServiceName(chainName);
        if (serviceIds.size() != 2 || !Objects.equals(serviceIds.getFirst(), "chains")) {
            log.info("跳过无法解析的链配置项: {}", chainName);
            return null;
        }
        return parseLongId(serviceIds.getLast(), "链", chainName);
    }

    private Long parseLongId(String rawId, String configType, String originalName) {
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            log.info("跳过无法解析的{}配置项: {}", configType, originalName);
            return null;
        }
    }

    private enum RuntimeServiceKind {
        TUNNEL_TLS,
        FORWARD_STREAM
    }

    private static final class RuntimeServiceRef {
        private final RuntimeServiceKind kind;
        private final Long targetId;
        private final String baseServiceName;

        private RuntimeServiceRef(RuntimeServiceKind kind, Long targetId, String baseServiceName) {
            this.kind = kind;
            this.targetId = targetId;
            this.baseServiceName = baseServiceName;
        }
    }
}
