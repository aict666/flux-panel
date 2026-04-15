package com.admin.service.impl;

import com.admin.common.dto.DiagnosisResult;
import com.admin.common.dto.GostDto;
import com.admin.common.dto.TunnelDetailDto;
import com.admin.common.dto.TunnelDto;
import com.admin.common.dto.TunnelTopologyConfigDto;
import com.admin.common.dto.TunnelTopologyItemDto;
import com.admin.common.dto.TunnelUpdateDto;
import com.admin.common.lang.R;
import com.admin.common.utils.GostUtil;
import com.admin.common.utils.JwtUtil;
import com.admin.common.utils.WebSocketServer;
import com.admin.entity.ChainTunnel;
import com.admin.entity.Forward;
import com.admin.entity.ForwardPort;
import com.admin.entity.Node;
import com.admin.entity.Tunnel;
import com.admin.entity.UserTunnel;
import com.admin.mapper.TunnelMapper;
import com.admin.mapper.UserTunnelMapper;
import com.admin.service.ChainTunnelService;
import com.admin.service.ForwardPortService;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import com.admin.service.SpeedLimitService;
import com.admin.service.TunnelService;
import com.admin.service.UserTunnelService;
import com.admin.service.support.TunnelTopologySupport;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TunnelServiceImpl extends ServiceImpl<TunnelMapper, Tunnel> implements TunnelService {

    @Resource
    UserTunnelMapper userTunnelMapper;

    @Resource
    NodeService nodeService;

    @Resource
    ForwardService forwardService;

    @Resource
    UserTunnelService userTunnelService;

    @Resource
    ChainTunnelService chainTunnelService;

    @Resource
    ForwardPortService forwardPortService;

    @Resource
    SpeedLimitService speedLimitService;

    private final TunnelTopologySupport topologySupport = new TunnelTopologySupport();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R createTunnel(TunnelDto tunnelDto) {
        try {
            ensureTunnelNameUnique(tunnelDto.getName(), null);

            TunnelTopologyConfigDto topology = topologySupport.normalizeTopology(buildTopology(tunnelDto.getInNodeId(), tunnelDto.getChainNodes(), tunnelDto.getOutNodeId()));
            populateReferenceNames(topology, currentTunnelNameMap(null));

            Tunnel tunnel = new Tunnel();
            tunnel.setName(tunnelDto.getName());
            tunnel.setType(tunnelDto.getType());
            tunnel.setFlow(tunnelDto.getFlow());
            tunnel.setTrafficRatio(tunnelDto.getTrafficRatio());
            tunnel.setInIp(normalizeInIpValue(tunnelDto.getInIp()));
            tunnel.setTopologyJson(topologySupport.serializeTopology(topology));
            tunnel.setStatus(1);
            long now = System.currentTimeMillis();
            tunnel.setCreatedTime(now);
            tunnel.setUpdatedTime(now);
            this.save(tunnel);

            rebuildAffectedTunnels(tunnel.getId());
            return R.ok();
        } catch (Exception e) {
            markTransactionForRollback();
            return R.err(e.getMessage());
        }
    }

    @Override
    public R getAllTunnels() {
        List<Tunnel> tunnelList = this.list();
        if (tunnelList.isEmpty()) {
            return R.ok(new ArrayList<TunnelDetailDto>());
        }

        Map<Long, List<ChainTunnel>> compiledTopologyMap = getCompiledTopologyMap(
                chainTunnelService.list(new QueryWrapper<ChainTunnel>().in("tunnel_id",
                        tunnelList.stream().map(Tunnel::getId).collect(Collectors.toList())))
        );
        Map<Long, String> tunnelNameMap = currentTunnelNameMap(tunnelList);

        List<TunnelDetailDto> detailDtoList = tunnelList.stream().map(tunnel -> {
            TunnelDetailDto detailDto = new TunnelDetailDto();
            BeanUtils.copyProperties(tunnel, detailDto);
            TunnelTopologyConfigDto topology = resolveTopology(tunnel, compiledTopologyMap.getOrDefault(tunnel.getId(), new ArrayList<>()));
            populateReferenceNames(topology, tunnelNameMap);
            detailDto.setInNodeId(topology.getInNodeId());
            detailDto.setChainNodes(topology.getChainNodes());
            detailDto.setOutNodeId(topology.getOutNodeId());
            detailDto.setInIp(tunnel.getInIp());
            return detailDto;
        }).collect(Collectors.toList());

        return R.ok(detailDtoList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R updateTunnel(TunnelUpdateDto tunnelUpdateDto) {
        try {
            Tunnel existingTunnel = this.getById(tunnelUpdateDto.getId());
            if (existingTunnel == null) {
                return R.err("隧道不存在");
            }
            ensureTunnelNameUnique(tunnelUpdateDto.getName(), tunnelUpdateDto.getId());

            TunnelTopologyConfigDto topology = topologySupport.normalizeTopology(buildTopology(
                    tunnelUpdateDto.getInNodeId(),
                    tunnelUpdateDto.getChainNodes(),
                    tunnelUpdateDto.getOutNodeId()
            ));
            populateReferenceNames(topology, currentTunnelNameMap(null));

            existingTunnel.setName(tunnelUpdateDto.getName());
            existingTunnel.setType(tunnelUpdateDto.getType());
            existingTunnel.setFlow(tunnelUpdateDto.getFlow());
            existingTunnel.setTrafficRatio(tunnelUpdateDto.getTrafficRatio());
            existingTunnel.setInIp(normalizeInIpValue(tunnelUpdateDto.getInIp()));
            existingTunnel.setTopologyJson(topologySupport.serializeTopology(topology));
            existingTunnel.setUpdatedTime(System.currentTimeMillis());
            this.updateById(existingTunnel);

            rebuildAffectedTunnels(existingTunnel.getId());
            return R.ok();
        } catch (Exception e) {
            markTransactionForRollback();
            return R.err(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R deleteTunnel(Long id) {
        try {
            Tunnel tunnel = this.getById(id);
            if (tunnel == null) {
                return R.err("隧道不存在");
            }

            List<Tunnel> allTunnels = this.list();
            Map<Long, List<ChainTunnel>> compiledTopologyMap = getCompiledTopologyMap(
                    chainTunnelService.list(new QueryWrapper<ChainTunnel>().in("tunnel_id",
                            allTunnels.stream().map(Tunnel::getId).collect(Collectors.toList())))
            );
            Map<Long, TunnelTopologyConfigDto> topologyMap = buildTopologyMap(allTunnels, compiledTopologyMap);
            boolean referenced = topologyMap.entrySet().stream()
                    .filter(entry -> !Objects.equals(entry.getKey(), id))
                    .anyMatch(entry -> topologySupport.collectReferencedTunnelIds(entry.getValue()).contains(id));
            if (referenced) {
                return R.err("该隧道仍被其他隧道引用，无法删除");
            }

            List<ChainTunnel> previousCompiledTopology = compiledTopologyMap.getOrDefault(id, new ArrayList<>());
            List<Forward> forwardList = forwardService.list(new QueryWrapper<Forward>().eq("tunnel_id", id));
            for (Forward forward : forwardList) {
                R deleteResult = forwardService.deleteForward(forward.getId());
                if (deleteResult.getCode() != 0) {
                    throw new IllegalArgumentException(deleteResult.getMsg());
                }
            }

            deleteTunnelRuntimeConfig(id, previousCompiledTopology);
            chainTunnelService.remove(new QueryWrapper<ChainTunnel>().eq("tunnel_id", id));
            speedLimitService.rebuildForTunnel(id, previousCompiledTopology);
            speedLimitService.remove(new QueryWrapper<com.admin.entity.SpeedLimit>().eq("tunnel_id", id));
            userTunnelService.remove(new QueryWrapper<UserTunnel>().eq("tunnel_id", id));
            this.removeById(id);
            return R.ok();
        } catch (Exception e) {
            markTransactionForRollback();
            return R.err(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R deleteTunnelsForNode(Long nodeId) {
        try {
            Set<Long> rootTunnelIds = chainTunnelService.listObjs(
                            new QueryWrapper<ChainTunnel>()
                                    .select("DISTINCT tunnel_id")
                                    .eq("node_id", nodeId))
                    .stream()
                    .filter(Objects::nonNull)
                    .map(this::toLong)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (rootTunnelIds.isEmpty()) {
                return R.ok();
            }

            List<Tunnel> allTunnels = this.list();
            if (allTunnels.isEmpty()) {
                return R.ok();
            }

            Map<Long, List<ChainTunnel>> compiledTopologyMap = getCompiledTopologyMap(
                    chainTunnelService.list(new QueryWrapper<ChainTunnel>().in("tunnel_id",
                            allTunnels.stream().map(Tunnel::getId).collect(Collectors.toList())))
            );
            Map<Long, TunnelTopologyConfigDto> topologyMap = buildTopologyMap(allTunnels, compiledTopologyMap);

            Set<Long> impactedTunnelIds = new LinkedHashSet<>();
            for (Long rootTunnelId : rootTunnelIds) {
                impactedTunnelIds.addAll(topologySupport.collectImpactedTunnelIds(rootTunnelId, topologyMap));
            }
            if (impactedTunnelIds.isEmpty()) {
                return R.ok();
            }

            List<Long> deleteOrder = new ArrayList<>(topologySupport.sortImpactedTunnelIds(impactedTunnelIds, topologyMap));
            Collections.reverse(deleteOrder);

            for (Long tunnelId : deleteOrder) {
                R deleteResult = this.deleteTunnel(tunnelId);
                if (deleteResult.getCode() != 0) {
                    markTransactionForRollback();
                    return deleteResult;
                }
            }

            return R.ok();
        } catch (Exception e) {
            markTransactionForRollback();
            return R.err(e.getMessage());
        }
    }

    @Override
    public R userTunnel() {
        List<Tunnel> tunnelEntities;
        Integer roleId = JwtUtil.getRoleIdFromToken();
        Integer userId = JwtUtil.getUserIdFromToken();
        if (roleId == 0) {
            tunnelEntities = this.list(new QueryWrapper<Tunnel>().eq("status", 1));
        } else {
            tunnelEntities = Collections.emptyList();
            List<UserTunnel> userTunnels = userTunnelMapper.selectList(new QueryWrapper<UserTunnel>().eq("user_id", userId));
            if (!userTunnels.isEmpty()) {
                List<Integer> tunnelIds = userTunnels.stream().map(UserTunnel::getTunnelId).collect(Collectors.toList());
                tunnelEntities = this.list(new QueryWrapper<Tunnel>().in("id", tunnelIds).eq("status", 1));
            }
        }
        return R.ok(tunnelEntities);
    }

    @Override
    public R diagnoseTunnel(Long tunnelId) {
        Tunnel tunnel = this.getById(tunnelId);
        if (tunnel == null) {
            return R.err("隧道不存在");
        }

        List<ChainTunnel> chainTunnels = chainTunnelService.list(new QueryWrapper<ChainTunnel>().eq("tunnel_id", tunnelId));
        if (chainTunnels.isEmpty()) {
            return R.err("隧道配置不完整");
        }

        List<ChainTunnel> inNodes = chainTunnels.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 1))
                .sorted(Comparator.comparing(ChainTunnel::getNodeId))
                .toList();
        Map<Integer, List<ChainTunnel>> chainNodesMap = chainTunnels.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 2))
                .collect(Collectors.groupingBy(
                        ct -> ct.getInx() == null ? 0 : ct.getInx(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> list.stream().sorted(Comparator.comparing(ChainTunnel::getNodeId)).collect(Collectors.toList()))
                ));
        List<List<ChainTunnel>> chainNodesList = new ArrayList<>(chainNodesMap.values());
        List<ChainTunnel> outNodes = chainTunnels.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 3))
                .sorted(Comparator.comparing(ChainTunnel::getNodeId))
                .toList();

        List<DiagnosisResult> results = new ArrayList<>();
        if (tunnel.getType() == 1) {
            for (ChainTunnel inNode : inNodes) {
                Node node = nodeService.getById(inNode.getNodeId());
                if (node != null) {
                    DiagnosisResult result = performTcpPingDiagnosisWithConnectionCheck(node, "www.google.com", 443,
                            "入口(" + node.getName() + ")->外网");
                    result.setFromChainType(1);
                    results.add(result);
                }
            }
        } else if (tunnel.getType() == 2) {
            for (ChainTunnel inNode : inNodes) {
                Node fromNode = nodeService.getById(inNode.getNodeId());
                if (fromNode == null) {
                    continue;
                }
                if (!chainNodesList.isEmpty()) {
                    for (ChainTunnel firstChainNode : chainNodesList.getFirst()) {
                        Node toNode = nodeService.getById(firstChainNode.getNodeId());
                        if (toNode != null) {
                            DiagnosisResult result = performTcpPingDiagnosisWithConnectionCheck(
                                    fromNode,
                                    toNode.getServerIp(),
                                    firstChainNode.getPort(),
                                    "入口(" + fromNode.getName() + ")->第1跳(" + toNode.getName() + ")"
                            );
                            result.setFromChainType(1);
                            result.setToChainType(2);
                            result.setToInx(firstChainNode.getInx());
                            results.add(result);
                        }
                    }
                } else {
                    for (ChainTunnel outNode : outNodes) {
                        Node toNode = nodeService.getById(outNode.getNodeId());
                        if (toNode != null) {
                            DiagnosisResult result = performTcpPingDiagnosisWithConnectionCheck(
                                    fromNode,
                                    toNode.getServerIp(),
                                    outNode.getPort(),
                                    "入口(" + fromNode.getName() + ")->出口(" + toNode.getName() + ")"
                            );
                            result.setFromChainType(1);
                            result.setToChainType(3);
                            results.add(result);
                        }
                    }
                }
            }

            for (int i = 0; i < chainNodesList.size(); i++) {
                List<ChainTunnel> currentHop = chainNodesList.get(i);
                for (ChainTunnel currentNode : currentHop) {
                    Node fromNode = nodeService.getById(currentNode.getNodeId());
                    if (fromNode == null) {
                        continue;
                    }
                    if (i + 1 < chainNodesList.size()) {
                        for (ChainTunnel nextNode : chainNodesList.get(i + 1)) {
                            Node toNode = nodeService.getById(nextNode.getNodeId());
                            if (toNode != null) {
                                DiagnosisResult result = performTcpPingDiagnosisWithConnectionCheck(
                                        fromNode,
                                        toNode.getServerIp(),
                                        nextNode.getPort(),
                                        "第" + (i + 1) + "跳(" + fromNode.getName() + ")->第" + (i + 2) + "跳(" + toNode.getName() + ")"
                                );
                                result.setFromChainType(2);
                                result.setFromInx(currentNode.getInx());
                                result.setToChainType(2);
                                result.setToInx(nextNode.getInx());
                                results.add(result);
                            }
                        }
                    } else {
                        for (ChainTunnel outNode : outNodes) {
                            Node toNode = nodeService.getById(outNode.getNodeId());
                            if (toNode != null) {
                                DiagnosisResult result = performTcpPingDiagnosisWithConnectionCheck(
                                        fromNode,
                                        toNode.getServerIp(),
                                        outNode.getPort(),
                                        "第" + (i + 1) + "跳(" + fromNode.getName() + ")->出口(" + toNode.getName() + ")"
                                );
                                result.setFromChainType(2);
                                result.setFromInx(currentNode.getInx());
                                result.setToChainType(3);
                                results.add(result);
                            }
                        }
                    }
                }
            }

            for (ChainTunnel outNode : outNodes) {
                Node node = nodeService.getById(outNode.getNodeId());
                if (node != null) {
                    DiagnosisResult result = performTcpPingDiagnosisWithConnectionCheck(node, "www.google.com", 443,
                            "出口(" + node.getName() + ")->外网");
                    result.setFromChainType(3);
                    results.add(result);
                }
            }
        }

        Map<String, Object> diagnosisReport = new HashMap<>();
        diagnosisReport.put("tunnelId", tunnelId);
        diagnosisReport.put("tunnelName", tunnel.getName());
        diagnosisReport.put("tunnelType", tunnel.getType() == 1 ? "端口转发" : "隧道转发");
        diagnosisReport.put("results", results);
        diagnosisReport.put("timestamp", System.currentTimeMillis());
        return R.ok(diagnosisReport);
    }

    public Integer getNodePort(Long nodeId) {
        Node node = nodeService.getById(nodeId);
        if (node == null) {
            throw new RuntimeException("节点不存在");
        }

        List<ChainTunnel> chainTunnels = chainTunnelService.list(new QueryWrapper<ChainTunnel>().eq("node_id", nodeId));
        Set<Integer> usedPorts = chainTunnels.stream()
                .map(ChainTunnel::getPort)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ForwardPort> list = forwardPortService.list(new QueryWrapper<ForwardPort>().eq("node_id", nodeId));
        for (ForwardPort forwardPort : list) {
            usedPorts.add(forwardPort.getPort());
        }

        List<Integer> parsedPorts = parsePorts(node.getPort());
        List<Integer> availablePorts = parsedPorts.stream().filter(p -> !usedPorts.contains(p)).toList();
        if (availablePorts.isEmpty()) {
            throw new RuntimeException("节点端口已满，无可用端口");
        }
        return availablePorts.getFirst();
    }

    public static List<Integer> parsePorts(String input) {
        Set<Integer> set = new HashSet<>();
        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                for (int i = start; i <= end; i++) {
                    set.add(i);
                }
            } else {
                set.add(Integer.parseInt(part));
            }
        }
        return set.stream().sorted().collect(Collectors.toList());
    }

    private void rebuildAffectedTunnels(Long changedTunnelId) {
        List<Tunnel> allTunnels = this.list();
        Map<Long, Tunnel> tunnelMap = allTunnels.stream().collect(Collectors.toMap(Tunnel::getId, tunnel -> tunnel));
        List<ChainTunnel> allCompiledTopology = chainTunnelService.list(new QueryWrapper<ChainTunnel>().in("tunnel_id",
                allTunnels.stream().map(Tunnel::getId).collect(Collectors.toList())));
        Map<Long, List<ChainTunnel>> compiledTopologyMap = getCompiledTopologyMap(allCompiledTopology);
        Map<Long, TunnelTopologyConfigDto> topologyMap = buildTopologyMap(allTunnels, compiledTopologyMap);

        Set<Long> impactedTunnelIds = topologySupport.collectImpactedTunnelIds(changedTunnelId, topologyMap);
        List<Long> rebuildOrder = topologySupport.sortImpactedTunnelIds(impactedTunnelIds, topologyMap);

        Map<String, Set<Integer>> usedPortsByScope = buildUsedPortsByScope(impactedTunnelIds, allCompiledTopology);
        List<CompiledTunnelPlan> compiledPlans = new ArrayList<>();
        for (Long tunnelId : rebuildOrder) {
            Tunnel tunnel = tunnelMap.get(tunnelId);
            if (tunnel == null) {
                throw new IllegalArgumentException("隧道不存在: " + tunnelId);
            }
            TunnelTopologyConfigDto rawTopology = topologyMap.getOrDefault(tunnelId, new TunnelTopologyConfigDto());
            compiledPlans.add(compileTunnelPlan(tunnel, rawTopology, tunnelMap, topologyMap, usedPortsByScope));
        }

        Map<Long, List<ChainTunnel>> previousCompiledTopology = compiledTopologyMap.entrySet().stream()
                .filter(entry -> impactedTunnelIds.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new ArrayList<>(entry.getValue())));

        for (Long tunnelId : rebuildOrder) {
            deleteTunnelRuntimeConfig(tunnelId, previousCompiledTopology.getOrDefault(tunnelId, new ArrayList<>()));
        }
        chainTunnelService.remove(new QueryWrapper<ChainTunnel>().in("tunnel_id", impactedTunnelIds));

        for (CompiledTunnelPlan plan : compiledPlans) {
            if (!Objects.equals(plan.getTunnel().getInIp(), plan.getResolvedInIp())) {
                plan.getTunnel().setInIp(plan.getResolvedInIp());
                this.updateById(plan.getTunnel());
            }
            if (!plan.getCompiledChainTunnels().isEmpty()) {
                chainTunnelService.saveBatch(plan.getCompiledChainTunnels());
            }
            createTunnelRuntimeConfig(plan.getTunnel(), plan.getCompiledChainTunnels(), plan.getNodeMap());
        }

        for (Long tunnelId : rebuildOrder) {
            speedLimitService.rebuildForTunnel(tunnelId, previousCompiledTopology.getOrDefault(tunnelId, new ArrayList<>()));
        }
        for (Long tunnelId : rebuildOrder) {
            forwardService.rebuildForTunnel(tunnelId, previousCompiledTopology.getOrDefault(tunnelId, new ArrayList<>()));
        }
    }

    private CompiledTunnelPlan compileTunnelPlan(Tunnel tunnel,
                                                 TunnelTopologyConfigDto rawTopology,
                                                 Map<Long, Tunnel> tunnelMap,
                                                 Map<Long, TunnelTopologyConfigDto> topologyMap,
                                                 Map<String, Set<Integer>> usedPortsByScope) {
        TunnelTopologySupport.ExpandedTopology expanded = topologySupport.expand(
                tunnel.getId(),
                tunnel.getType(),
                rawTopology,
                refTunnelId -> {
                    Tunnel referencedTunnel = tunnelMap.get(refTunnelId);
                    if (referencedTunnel == null) {
                        return null;
                    }
                    return new TunnelTopologySupport.ReferencedTunnel(
                            referencedTunnel.getId(),
                            referencedTunnel.getName(),
                            referencedTunnel.getType(),
                            referencedTunnel.getStatus(),
                            topologyMap.getOrDefault(refTunnelId, new TunnelTopologyConfigDto())
                    );
                }
        );

        Map<Long, Node> nodeMap = loadAndValidateNodes(expanded);
        String resolvedInIp = resolveInIp(tunnel.getInIp(), expanded.getInNodes(), nodeMap);
        List<ChainTunnel> compiled = buildCompiledChainTunnels(tunnel.getId(), tunnel.getType(), expanded, nodeMap, usedPortsByScope);
        return new CompiledTunnelPlan(tunnel, expanded, nodeMap, compiled, resolvedInIp);
    }

    private List<ChainTunnel> buildCompiledChainTunnels(Long tunnelId,
                                                        Integer tunnelType,
                                                        TunnelTopologySupport.ExpandedTopology expanded,
                                                        Map<Long, Node> nodeMap,
                                                        Map<String, Set<Integer>> usedPortsByScope) {
        List<ChainTunnel> compiled = new ArrayList<>();
        for (TunnelTopologyItemDto inNode : expanded.getInNodes()) {
            ChainTunnel chainTunnel = new ChainTunnel();
            chainTunnel.setTunnelId(tunnelId);
            chainTunnel.setChainType(1);
            chainTunnel.setNodeId(inNode.getNodeId());
            compiled.add(chainTunnel);
        }

        int hopIndex = 1;
        for (List<TunnelTopologyItemDto> hop : expanded.getChainNodes()) {
            String hopStrategy = normalizeStrategy(hop);
            for (TunnelTopologyItemDto item : hop) {
                ChainTunnel chainTunnel = new ChainTunnel();
                chainTunnel.setTunnelId(tunnelId);
                chainTunnel.setChainType(2);
                chainTunnel.setNodeId(item.getNodeId());
                chainTunnel.setPort(allocatePort(item.getNodeId(), nodeMap, usedPortsByScope));
                chainTunnel.setStrategy(hopStrategy);
                chainTunnel.setProtocol(normalizeProtocol(item.getProtocol()));
                chainTunnel.setInx(hopIndex);
                compiled.add(chainTunnel);
            }
            hopIndex++;
        }

        if (Objects.equals(tunnelType, 2)) {
            String outStrategy = normalizeStrategy(expanded.getOutNodes());
            for (TunnelTopologyItemDto outNode : expanded.getOutNodes()) {
                ChainTunnel chainTunnel = new ChainTunnel();
                chainTunnel.setTunnelId(tunnelId);
                chainTunnel.setChainType(3);
                chainTunnel.setNodeId(outNode.getNodeId());
                chainTunnel.setPort(allocatePort(outNode.getNodeId(), nodeMap, usedPortsByScope));
                chainTunnel.setStrategy(outStrategy);
                chainTunnel.setProtocol(normalizeProtocol(outNode.getProtocol()));
                compiled.add(chainTunnel);
            }
        }
        return compiled;
    }

    private Map<String, Set<Integer>> buildUsedPortsByScope(Set<Long> impactedTunnelIds, List<ChainTunnel> allCompiledTopology) {
        Map<Long, String> portScopeByNodeId = nodeService.list().stream()
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(Node::getId, this::resolvePortScopeKey));
        Map<String, Set<Integer>> usedPortsByScope = new HashMap<>();

        for (ChainTunnel chainTunnel : allCompiledTopology) {
            if (!impactedTunnelIds.contains(chainTunnel.getTunnelId()) && chainTunnel.getPort() != null) {
                String scopeKey = portScopeByNodeId.getOrDefault(chainTunnel.getNodeId(), "node:" + chainTunnel.getNodeId());
                usedPortsByScope.computeIfAbsent(scopeKey, key -> new LinkedHashSet<>()).add(chainTunnel.getPort());
            }
        }

        List<ForwardPort> forwardPorts = forwardPortService.list();
        for (ForwardPort forwardPort : forwardPorts) {
            if (forwardPort.getPort() != null) {
                String scopeKey = portScopeByNodeId.getOrDefault(forwardPort.getNodeId(), "node:" + forwardPort.getNodeId());
                usedPortsByScope.computeIfAbsent(scopeKey, key -> new LinkedHashSet<>()).add(forwardPort.getPort());
            }
        }
        return usedPortsByScope;
    }

    private Integer allocatePort(Long nodeId, Map<Long, Node> nodeMap, Map<String, Set<Integer>> usedPortsByScope) {
        Node node = nodeMap.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        String scopeKey = resolvePortScopeKey(node);
        Set<Integer> usedPorts = usedPortsByScope.computeIfAbsent(scopeKey, key -> new LinkedHashSet<>());
        List<Integer> parsedPorts = parsePorts(node.getPort());
        Integer availablePort = parsedPorts.stream().filter(port -> !usedPorts.contains(port)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("节点端口已满，无可用端口"));
        usedPorts.add(availablePort);
        return availablePort;
    }

    private String resolvePortScopeKey(Node node) {
        if (node.getServerIp() == null || node.getServerIp().isBlank()) {
            return "node:" + node.getId();
        }
        return "host:" + node.getServerIp().trim();
    }

    private Map<Long, Node> loadAndValidateNodes(TunnelTopologySupport.ExpandedTopology expanded) {
        Set<Long> nodeIds = new LinkedHashSet<>();
        nodeIds.addAll(expanded.getInNodes().stream().map(TunnelTopologyItemDto::getNodeId).collect(Collectors.toSet()));
        nodeIds.addAll(expanded.getChainNodes().stream().flatMap(Collection::stream).map(TunnelTopologyItemDto::getNodeId).collect(Collectors.toSet()));
        nodeIds.addAll(expanded.getOutNodes().stream().map(TunnelTopologyItemDto::getNodeId).collect(Collectors.toSet()));
        if (nodeIds.isEmpty()) {
            throw new IllegalArgumentException("隧道未包含有效节点");
        }

        List<Node> nodes = nodeService.list(new QueryWrapper<Node>().in("id", nodeIds));
        if (nodes.size() != nodeIds.size()) {
            throw new IllegalArgumentException("部分节点不存在");
        }
        for (Node node : nodes) {
            if (!Objects.equals(node.getStatus(), 1)) {
                throw new IllegalArgumentException("部分节点不在线");
            }
        }
        return nodes.stream().collect(Collectors.toMap(Node::getId, node -> node));
    }

    private void createTunnelRuntimeConfig(Tunnel tunnel, List<ChainTunnel> compiledTopology, Map<Long, Node> nodes) {
        if (!Objects.equals(tunnel.getType(), 2)) {
            return;
        }

        List<ChainTunnel> inNodes = compiledTopology.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 1))
                .sorted(Comparator.comparing(ChainTunnel::getNodeId))
                .toList();
        Map<Integer, List<ChainTunnel>> chainGroups = compiledTopology.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 2))
                .collect(Collectors.groupingBy(
                        ct -> ct.getInx() == null ? 0 : ct.getInx(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> list.stream().sorted(Comparator.comparing(ChainTunnel::getNodeId)).collect(Collectors.toList()))
                ));
        List<List<ChainTunnel>> sortedChainGroups = new ArrayList<>(chainGroups.values());
        List<ChainTunnel> outNodes = compiledTopology.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 3))
                .sorted(Comparator.comparing(ChainTunnel::getNodeId))
                .toList();

        if (outNodes.isEmpty()) {
            throw new IllegalArgumentException("隧道转发必须配置出口节点");
        }

        List<JSONObject> createdChains = new ArrayList<>();
        List<JSONObject> createdServices = new ArrayList<>();
        try {
            for (ChainTunnel inNode : inNodes) {
                List<ChainTunnel> targetHop = sortedChainGroups.isEmpty() ? outNodes : sortedChainGroups.getFirst();
                GostDto gostDto = GostUtil.AddChains(inNode.getNodeId(), targetHop, nodes);
                ensureGostOk(gostDto);
                createdChains.add(buildRuntimeMarker(inNode.getNodeId(), "chains_" + tunnel.getId()));
            }

            for (int i = 0; i < sortedChainGroups.size(); i++) {
                List<ChainTunnel> currentHop = sortedChainGroups.get(i);
                List<ChainTunnel> nextHop = i + 1 < sortedChainGroups.size() ? sortedChainGroups.get(i + 1) : outNodes;
                for (ChainTunnel chainTunnel : currentHop) {
                    GostDto chainResult = GostUtil.AddChains(chainTunnel.getNodeId(), nextHop, nodes);
                    ensureGostOk(chainResult);
                    createdChains.add(buildRuntimeMarker(chainTunnel.getNodeId(), "chains_" + tunnel.getId()));

                    GostDto serviceResult = GostUtil.AddChainService(chainTunnel.getNodeId(), chainTunnel, nodes);
                    ensureGostOk(serviceResult);
                    createdServices.add(buildRuntimeMarker(chainTunnel.getNodeId(), tunnel.getId() + "_tls"));
                }
            }

            for (ChainTunnel outNode : outNodes) {
                GostDto gostDto = GostUtil.AddChainService(outNode.getNodeId(), outNode, nodes);
                ensureGostOk(gostDto);
                createdServices.add(buildRuntimeMarker(outNode.getNodeId(), tunnel.getId() + "_tls"));
            }
        } catch (Exception e) {
            cleanupCreatedTunnelRuntime(createdChains, createdServices);
            throw e;
        }
    }

    private void deleteTunnelRuntimeConfig(Long tunnelId, List<ChainTunnel> previousCompiledTopology) {
        for (ChainTunnel chainTunnel : previousCompiledTopology) {
            if (Objects.equals(chainTunnel.getChainType(), 1)) {
                GostUtil.DeleteChains(chainTunnel.getNodeId(), "chains_" + tunnelId);
            } else if (Objects.equals(chainTunnel.getChainType(), 2)) {
                GostUtil.DeleteChains(chainTunnel.getNodeId(), "chains_" + tunnelId);
                JSONArray services = new JSONArray();
                services.add(tunnelId + "_tls");
                GostUtil.DeleteService(chainTunnel.getNodeId(), services);
            } else if (Objects.equals(chainTunnel.getChainType(), 3)) {
                JSONArray services = new JSONArray();
                services.add(tunnelId + "_tls");
                GostUtil.DeleteService(chainTunnel.getNodeId(), services);
            }
        }
    }

    private void cleanupCreatedTunnelRuntime(List<JSONObject> createdChains, List<JSONObject> createdServices) {
        for (JSONObject createdChain : createdChains) {
            GostUtil.DeleteChains(createdChain.getLong("nodeId"), createdChain.getString("name"));
        }
        for (JSONObject createdService : createdServices) {
            JSONArray services = new JSONArray();
            services.add(createdService.getString("name"));
            GostUtil.DeleteService(createdService.getLong("nodeId"), services);
        }
    }

    private JSONObject buildRuntimeMarker(Long nodeId, String name) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("nodeId", nodeId);
        jsonObject.put("name", name);
        return jsonObject;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private void markTransactionForRollback() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Some unit tests invoke service methods directly without a Spring transaction proxy.
        }
    }

    private void ensureGostOk(GostDto gostDto) {
        if (gostDto == null || !Objects.equals(gostDto.getMsg(), "OK")) {
            throw new IllegalArgumentException(gostDto == null ? "节点无响应" : gostDto.getMsg());
        }
    }

    private Map<Long, TunnelTopologyConfigDto> buildTopologyMap(List<Tunnel> tunnelList,
                                                                Map<Long, List<ChainTunnel>> compiledTopologyMap) {
        Map<Long, String> tunnelNameMap = currentTunnelNameMap(tunnelList);
        Map<Long, TunnelTopologyConfigDto> topologyMap = new LinkedHashMap<>();
        for (Tunnel tunnel : tunnelList) {
            TunnelTopologyConfigDto topology = resolveTopology(tunnel, compiledTopologyMap.getOrDefault(tunnel.getId(), new ArrayList<>()));
            populateReferenceNames(topology, tunnelNameMap);
            topologyMap.put(tunnel.getId(), topology);
        }
        return topologyMap;
    }

    private TunnelTopologyConfigDto resolveTopology(Tunnel tunnel, List<ChainTunnel> compiledTopology) {
        TunnelTopologyConfigDto topology = topologySupport.parseTopology(tunnel.getTopologyJson());
        if (topology.getInNodeId().isEmpty()
                && topology.getChainNodes().isEmpty()
                && topology.getOutNodeId().isEmpty()
                && compiledTopology != null
                && !compiledTopology.isEmpty()) {
            topology = topologySupport.buildLegacyTopology(compiledTopology);
        }
        return topologySupport.normalizeTopology(topology);
    }

    private TunnelTopologyConfigDto buildTopology(List<TunnelTopologyItemDto> inNodeId,
                                                  List<List<TunnelTopologyItemDto>> chainNodes,
                                                  List<TunnelTopologyItemDto> outNodeId) {
        TunnelTopologyConfigDto topology = new TunnelTopologyConfigDto();
        topology.setInNodeId(inNodeId == null ? new ArrayList<>() : new ArrayList<>(inNodeId));
        topology.setChainNodes(chainNodes == null ? new ArrayList<>() : new ArrayList<>(chainNodes));
        topology.setOutNodeId(outNodeId == null ? new ArrayList<>() : new ArrayList<>(outNodeId));
        return topology;
    }

    private Map<Long, List<ChainTunnel>> getCompiledTopologyMap(List<ChainTunnel> chainTunnels) {
        return chainTunnels.stream().collect(Collectors.groupingBy(ChainTunnel::getTunnelId));
    }

    private void populateReferenceNames(TunnelTopologyConfigDto topology, Map<Long, String> tunnelNameMap) {
        for (List<TunnelTopologyItemDto> group : topology.getChainNodes()) {
            for (TunnelTopologyItemDto item : group) {
                if ("tunnel".equals(item.getItemType()) && item.getRefTunnelId() != null) {
                    item.setRefTunnelName(tunnelNameMap.getOrDefault(item.getRefTunnelId(), item.getRefTunnelName()));
                }
            }
        }
    }

    private Map<Long, String> currentTunnelNameMap(List<Tunnel> cachedTunnels) {
        List<Tunnel> tunnels = cachedTunnels == null ? this.list() : cachedTunnels;
        return tunnels.stream().collect(Collectors.toMap(Tunnel::getId, Tunnel::getName, (left, right) -> right));
    }

    private void ensureTunnelNameUnique(String name, Long excludeId) {
        QueryWrapper<Tunnel> queryWrapper = new QueryWrapper<Tunnel>().eq("name", name);
        if (excludeId != null) {
            queryWrapper.ne("id", excludeId);
        }
        if (this.count(queryWrapper) > 0) {
            throw new IllegalArgumentException("隧道名称重复");
        }
    }

    private String resolveInIp(String configuredInIp, List<TunnelTopologyItemDto> inNodes, Map<Long, Node> nodeMap) {
        String normalized = normalizeInIpValue(configuredInIp);
        if (StringUtils.isNotBlank(normalized)) {
            return normalized;
        }
        return inNodes.stream()
                .map(TunnelTopologyItemDto::getNodeId)
                .map(nodeMap::get)
                .filter(Objects::nonNull)
                .map(Node::getServerIp)
                .collect(Collectors.joining(","));
    }

    private String normalizeInIpValue(String inIp) {
        if (StringUtils.isBlank(inIp)) {
            return "";
        }
        return Arrays.stream(inIp.split("[\\n,]"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String normalizeProtocol(String protocol) {
        return StringUtils.isBlank(protocol) ? "tls" : protocol;
    }

    private String normalizeStrategy(List<TunnelTopologyItemDto> items) {
        return items.stream()
                .map(TunnelTopologyItemDto::getStrategy)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("round");
    }

    private DiagnosisResult performTcpPingDiagnosis(Node node, String targetIp, int port, String description) {
        try {
            JSONObject tcpPingData = new JSONObject();
            tcpPingData.put("ip", targetIp);
            tcpPingData.put("port", port);
            tcpPingData.put("count", 4);
            tcpPingData.put("timeout", 5000);

            GostDto gostResult = WebSocketServer.send_msg(node.getId(), tcpPingData, "TcpPing");

            DiagnosisResult result = new DiagnosisResult();
            result.setNodeId(node.getId());
            result.setNodeName(node.getName());
            result.setTargetIp(targetIp);
            result.setTargetPort(port);
            result.setDescription(description);
            result.setTimestamp(System.currentTimeMillis());

            if (gostResult != null && "OK".equals(gostResult.getMsg())) {
                try {
                    if (gostResult.getData() != null) {
                        JSONObject tcpPingResponse = (JSONObject) gostResult.getData();
                        boolean success = tcpPingResponse.getBooleanValue("success");
                        result.setSuccess(success);
                        if (success) {
                            result.setMessage("TCP连接成功");
                            result.setAverageTime(tcpPingResponse.getDoubleValue("averageTime"));
                            result.setPacketLoss(tcpPingResponse.getDoubleValue("packetLoss"));
                        } else {
                            result.setMessage(tcpPingResponse.getString("errorMessage"));
                            result.setAverageTime(-1.0);
                            result.setPacketLoss(100.0);
                        }
                    } else {
                        result.setSuccess(true);
                        result.setMessage("TCP连接成功");
                        result.setAverageTime(0.0);
                        result.setPacketLoss(0.0);
                    }
                } catch (Exception e) {
                    result.setSuccess(true);
                    result.setMessage("TCP连接成功，但无法解析详细数据");
                    result.setAverageTime(0.0);
                    result.setPacketLoss(0.0);
                }
            } else {
                result.setSuccess(false);
                result.setMessage(gostResult != null ? gostResult.getMsg() : "节点无响应");
                result.setAverageTime(-1.0);
                result.setPacketLoss(100.0);
            }
            return result;
        } catch (Exception e) {
            DiagnosisResult result = new DiagnosisResult();
            result.setNodeId(node.getId());
            result.setNodeName(node.getName());
            result.setTargetIp(targetIp);
            result.setTargetPort(port);
            result.setDescription(description);
            result.setSuccess(false);
            result.setMessage("诊断执行异常: " + e.getMessage());
            result.setTimestamp(System.currentTimeMillis());
            result.setAverageTime(-1.0);
            result.setPacketLoss(100.0);
            return result;
        }
    }

    private DiagnosisResult performTcpPingDiagnosisWithConnectionCheck(Node node, String targetIp, int port, String description) {
        DiagnosisResult result = new DiagnosisResult();
        result.setNodeId(node.getId());
        result.setNodeName(node.getName());
        result.setTargetIp(targetIp);
        result.setTargetPort(port);
        result.setDescription(description);
        result.setTimestamp(System.currentTimeMillis());
        try {
            return performTcpPingDiagnosis(node, targetIp, port, description);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("连接检查异常: " + e.getMessage());
            result.setAverageTime(-1.0);
            result.setPacketLoss(100.0);
            return result;
        }
    }

    @Data
    @AllArgsConstructor
    private static class CompiledTunnelPlan {
        private Tunnel tunnel;
        private TunnelTopologySupport.ExpandedTopology expandedTopology;
        private Map<Long, Node> nodeMap;
        private List<ChainTunnel> compiledChainTunnels;
        private String resolvedInIp;
    }
}
