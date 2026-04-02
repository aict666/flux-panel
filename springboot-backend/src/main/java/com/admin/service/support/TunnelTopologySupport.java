package com.admin.service.support;

import com.admin.common.dto.TunnelTopologyConfigDto;
import com.admin.common.dto.TunnelTopologyItemDto;
import com.admin.entity.ChainTunnel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TunnelTopologySupport {

    public interface TunnelResolver {
        ReferencedTunnel resolve(Long tunnelId);
    }

    @Data
    @AllArgsConstructor
    public static class ReferencedTunnel {
        private Long id;
        private String name;
        private Integer type;
        private Integer status;
        private TunnelTopologyConfigDto topology;
    }

    @Data
    public static class ExpandedTopology {
        private List<TunnelTopologyItemDto> inNodes = new ArrayList<>();
        private List<List<TunnelTopologyItemDto>> chainNodes = new ArrayList<>();
        private List<TunnelTopologyItemDto> outNodes = new ArrayList<>();
        private Set<Long> referencedTunnelIds = new LinkedHashSet<>();
    }

    public TunnelTopologyConfigDto parseTopology(String topologyJson) {
        if (topologyJson == null || topologyJson.trim().isEmpty()) {
            return new TunnelTopologyConfigDto();
        }
        TunnelTopologyConfigDto topology = JSON.parseObject(topologyJson, TunnelTopologyConfigDto.class);
        return normalizeTopology(topology);
    }

    public String serializeTopology(TunnelTopologyConfigDto topology) {
        return JSON.toJSONString(normalizeTopology(topology));
    }

    public TunnelTopologyConfigDto normalizeTopology(TunnelTopologyConfigDto topology) {
        TunnelTopologyConfigDto normalized = topology == null ? new TunnelTopologyConfigDto() : topology;

        if (normalized.getInNodeId() == null) {
            normalized.setInNodeId(new ArrayList<>());
        }
        if (normalized.getChainNodes() == null) {
            normalized.setChainNodes(new ArrayList<>());
        }
        if (normalized.getOutNodeId() == null) {
            normalized.setOutNodeId(new ArrayList<>());
        }

        normalized.setInNodeId(normalized.getInNodeId().stream().map(this::normalizeItem).collect(Collectors.toList()));
        normalized.setChainNodes(normalized.getChainNodes().stream()
                .filter(Objects::nonNull)
                .map(group -> group.stream().map(this::normalizeItem).collect(Collectors.toList()))
                .collect(Collectors.toList()));
        normalized.setOutNodeId(normalized.getOutNodeId().stream().map(this::normalizeItem).collect(Collectors.toList()));
        return normalized;
    }

    public TunnelTopologyConfigDto buildLegacyTopology(List<ChainTunnel> chainTunnels) {
        TunnelTopologyConfigDto topology = new TunnelTopologyConfigDto();
        if (chainTunnels == null || chainTunnels.isEmpty()) {
            return topology;
        }

        List<ChainTunnel> sorted = chainTunnels.stream()
                .sorted(Comparator
                        .comparing(ChainTunnel::getChainType, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ct -> ct.getInx() == null ? 0 : ct.getInx())
                        .thenComparing(ChainTunnel::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());

        topology.setInNodeId(sorted.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 1))
                .map(this::toNodeItem)
                .collect(Collectors.toList()));

        Map<Integer, List<ChainTunnel>> chainGroups = sorted.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 2))
                .collect(Collectors.groupingBy(
                        ct -> ct.getInx() == null ? 0 : ct.getInx(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        topology.setChainNodes(chainGroups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().stream()
                        .sorted(Comparator.comparing(ChainTunnel::getNodeId))
                        .map(this::toNodeItem)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList()));

        topology.setOutNodeId(sorted.stream()
                .filter(ct -> Objects.equals(ct.getChainType(), 3))
                .map(this::toNodeItem)
                .collect(Collectors.toList()));
        return normalizeTopology(topology);
    }

    public ExpandedTopology expand(Long tunnelId,
                                   Integer tunnelType,
                                   TunnelTopologyConfigDto topology,
                                   TunnelResolver resolver) {
        TunnelTopologyConfigDto normalized = normalizeTopology(topology);
        validateRootTopology(tunnelId, tunnelType, normalized);

        ExpandedTopology expanded = new ExpandedTopology();
        expanded.setInNodes(normalizeNodeSection(normalized.getInNodeId(), "入口"));
        expanded.setOutNodes(normalizeNodeSection(normalized.getOutNodeId(), "出口"));

        Deque<Long> stack = new ArrayDeque<>();
        stack.push(tunnelId);
        for (List<TunnelTopologyItemDto> hop : normalized.getChainNodes()) {
            expanded.getChainNodes().addAll(expandHopGroup(tunnelId, hop, resolver, stack, expanded.getReferencedTunnelIds()));
        }
        stack.pop();

        validateNoDuplicateNodes(expanded);
        return expanded;
    }

    public Set<Long> collectReferencedTunnelIds(TunnelTopologyConfigDto topology) {
        TunnelTopologyConfigDto normalized = normalizeTopology(topology);
        Set<Long> ids = new LinkedHashSet<>();
        for (List<TunnelTopologyItemDto> group : normalized.getChainNodes()) {
            for (TunnelTopologyItemDto item : group) {
                if ("tunnel".equals(item.getItemType()) && item.getRefTunnelId() != null) {
                    ids.add(item.getRefTunnelId());
                }
            }
        }
        return ids;
    }

    public Set<Long> collectImpactedTunnelIds(Long changedTunnelId, Map<Long, TunnelTopologyConfigDto> topologyMap) {
        Map<Long, Set<Long>> reverseGraph = new HashMap<>();
        for (Map.Entry<Long, TunnelTopologyConfigDto> entry : topologyMap.entrySet()) {
            for (Long referencedId : collectReferencedTunnelIds(entry.getValue())) {
                reverseGraph.computeIfAbsent(referencedId, key -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }

        Set<Long> impacted = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(changedTunnelId);
        impacted.add(changedTunnelId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            for (Long dependentId : reverseGraph.getOrDefault(current, Collections.emptySet())) {
                if (impacted.add(dependentId)) {
                    queue.addLast(dependentId);
                }
            }
        }
        return impacted;
    }

    public List<Long> sortImpactedTunnelIds(Set<Long> impactedTunnelIds, Map<Long, TunnelTopologyConfigDto> topologyMap) {
        List<Long> order = new ArrayList<>();
        Set<Long> visiting = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        List<Long> sortedIds = impactedTunnelIds.stream().sorted().collect(Collectors.toList());
        for (Long tunnelId : sortedIds) {
            dfsSort(tunnelId, impactedTunnelIds, topologyMap, visiting, visited, order);
        }
        return order;
    }

    private void dfsSort(Long tunnelId,
                         Set<Long> impactedTunnelIds,
                         Map<Long, TunnelTopologyConfigDto> topologyMap,
                         Set<Long> visiting,
                         Set<Long> visited,
                         List<Long> order) {
        if (visited.contains(tunnelId)) {
            return;
        }
        if (!visiting.add(tunnelId)) {
            throw new IllegalArgumentException("检测到隧道引用循环，无法排序");
        }
        for (Long dependencyId : collectReferencedTunnelIds(topologyMap.getOrDefault(tunnelId, new TunnelTopologyConfigDto()))) {
            if (impactedTunnelIds.contains(dependencyId)) {
                dfsSort(dependencyId, impactedTunnelIds, topologyMap, visiting, visited, order);
            }
        }
        visiting.remove(tunnelId);
        visited.add(tunnelId);
        order.add(tunnelId);
    }

    private List<TunnelTopologyItemDto> normalizeNodeSection(List<TunnelTopologyItemDto> items, String label) {
        List<TunnelTopologyItemDto> normalizedItems = new ArrayList<>();
        for (TunnelTopologyItemDto item : items) {
            TunnelTopologyItemDto normalized = normalizeItem(item);
            if (!"node".equals(normalized.getItemType()) || normalized.getNodeId() == null) {
                throw new IllegalArgumentException(label + "只允许选择节点");
            }
            normalizedItems.add(normalized);
        }
        return normalizedItems;
    }

    private List<List<TunnelTopologyItemDto>> expandHopGroup(Long rootTunnelId,
                                                             List<TunnelTopologyItemDto> rawGroup,
                                                             TunnelResolver resolver,
                                                             Deque<Long> stack,
                                                             Set<Long> referencedTunnelIds) {
        if (rawGroup == null || rawGroup.isEmpty()) {
            return new ArrayList<>();
        }
        List<TunnelTopologyItemDto> group = rawGroup.stream().map(this::normalizeItem).collect(Collectors.toList());
        Set<String> itemTypes = group.stream().map(TunnelTopologyItemDto::getItemType).collect(Collectors.toSet());
        if (itemTypes.size() > 1) {
            throw new IllegalArgumentException("同一跳不能混合节点和引用隧道");
        }

        String itemType = group.getFirst().getItemType();
        if ("node".equals(itemType)) {
            for (TunnelTopologyItemDto item : group) {
                if (item.getNodeId() == null) {
                    throw new IllegalArgumentException("节点跳缺少节点ID");
                }
            }
            return List.of(group);
        }

        if (!"tunnel".equals(itemType)) {
            throw new IllegalArgumentException("不支持的 hop 类型: " + itemType);
        }
        if (group.size() != 1) {
            throw new IllegalArgumentException("引用隧道跳只能包含一个隧道");
        }

        TunnelTopologyItemDto refItem = group.getFirst();
        if (refItem.getRefTunnelId() == null) {
            throw new IllegalArgumentException("引用隧道缺少隧道ID");
        }
        if (Objects.equals(rootTunnelId, refItem.getRefTunnelId()) && stack.size() == 1) {
            throw new IllegalArgumentException("隧道不能引用自身");
        }
        if (stack.contains(refItem.getRefTunnelId())) {
            throw new IllegalArgumentException("检测到隧道引用循环");
        }

        ReferencedTunnel referencedTunnel = resolver.resolve(refItem.getRefTunnelId());
        if (referencedTunnel == null) {
            throw new IllegalArgumentException("引用的隧道不存在: " + refItem.getRefTunnelId());
        }
        if (!Objects.equals(referencedTunnel.getStatus(), 1)) {
            throw new IllegalArgumentException("引用的隧道已禁用: " + referencedTunnel.getName());
        }
        if (!Objects.equals(referencedTunnel.getType(), 2)) {
            throw new IllegalArgumentException("仅支持引用隧道转发类型的隧道");
        }

        referencedTunnelIds.add(referencedTunnel.getId());
        stack.push(referencedTunnel.getId());

        TunnelTopologyConfigDto nestedTopology = normalizeTopology(referencedTunnel.getTopology());
        ExpandedTopology nestedExpanded = new ExpandedTopology();
        nestedExpanded.setInNodes(normalizeNodeSection(nestedTopology.getInNodeId(), "入口"));
        nestedExpanded.setOutNodes(normalizeNodeSection(nestedTopology.getOutNodeId(), "出口"));

        List<List<TunnelTopologyItemDto>> result = new ArrayList<>();
        for (List<TunnelTopologyItemDto> nestedGroup : nestedTopology.getChainNodes()) {
            result.addAll(expandHopGroup(rootTunnelId, nestedGroup, resolver, stack, referencedTunnelIds));
        }
        if (!nestedExpanded.getOutNodes().isEmpty()) {
            result.add(nestedExpanded.getOutNodes());
        }

        stack.pop();

        if (result.isEmpty()) {
            throw new IllegalArgumentException("引用隧道缺少可展开的链路");
        }
        return result;
    }

    private void validateRootTopology(Long tunnelId, Integer tunnelType, TunnelTopologyConfigDto topology) {
        if (tunnelId == null) {
            throw new IllegalArgumentException("隧道ID不能为空");
        }
        if (tunnelType == null) {
            throw new IllegalArgumentException("隧道类型不能为空");
        }
        if (topology.getInNodeId().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个入口节点");
        }
        if (Objects.equals(tunnelType, 1) && (!topology.getChainNodes().isEmpty() || !topology.getOutNodeId().isEmpty())) {
            throw new IllegalArgumentException("端口转发不允许配置转发链或出口节点");
        }
        if (Objects.equals(tunnelType, 2) && topology.getOutNodeId().isEmpty()) {
            throw new IllegalArgumentException("隧道转发必须配置出口节点");
        }
    }

    private void validateNoDuplicateNodes(ExpandedTopology expanded) {
        List<Long> nodeIds = new ArrayList<>();
        nodeIds.addAll(expanded.getInNodes().stream().map(TunnelTopologyItemDto::getNodeId).toList());
        nodeIds.addAll(expanded.getChainNodes().stream().flatMap(Collection::stream).map(TunnelTopologyItemDto::getNodeId).toList());
        nodeIds.addAll(expanded.getOutNodes().stream().map(TunnelTopologyItemDto::getNodeId).toList());

        Set<Long> distinct = new HashSet<>(nodeIds);
        if (distinct.size() != nodeIds.size()) {
            throw new IllegalArgumentException("展开后节点重复");
        }
    }

    private TunnelTopologyItemDto toNodeItem(ChainTunnel chainTunnel) {
        TunnelTopologyItemDto item = new TunnelTopologyItemDto();
        item.setItemType("node");
        item.setNodeId(chainTunnel.getNodeId());
        item.setProtocol(chainTunnel.getProtocol());
        item.setStrategy(chainTunnel.getStrategy());
        item.setHopIndex(chainTunnel.getInx());
        return item;
    }

    private TunnelTopologyItemDto normalizeItem(TunnelTopologyItemDto item) {
        TunnelTopologyItemDto normalized = item == null ? new TunnelTopologyItemDto() : item;
        if (normalized.getItemType() == null || normalized.getItemType().isBlank()) {
            normalized.setItemType(normalized.getRefTunnelId() != null ? "tunnel" : "node");
        }
        return normalized;
    }

    public JSONObject toJsonObject(TunnelTopologyConfigDto topology) {
        return JSON.parseObject(serializeTopology(topology));
    }
}
