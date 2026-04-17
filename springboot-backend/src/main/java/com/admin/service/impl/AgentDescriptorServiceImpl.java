package com.admin.service.impl;

import com.admin.service.AgentDescriptorService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentDescriptorServiceImpl implements AgentDescriptorService {

    private static final List<DescriptorTool> TOOL_REGISTRY = List.of(
            tool("list_users", "获取用户列表", "POST", "/api/v1/agent/users/list", "users:read"),
            tool("create_user", "创建用户", "POST", "/api/v1/agent/users/create", "users:write"),
            tool("update_user", "更新用户", "POST", "/api/v1/agent/users/update", "users:write"),
            tool("delete_user", "删除用户", "POST", "/api/v1/agent/users/delete", "users:delete"),
            tool("list_user_permissions", "获取用户隧道权限列表", "POST", "/api/v1/agent/user-permissions/list", "user-permissions:read"),
            tool("assign_user_permission", "分配用户隧道权限", "POST", "/api/v1/agent/user-permissions/assign", "user-permissions:write"),
            tool("update_user_permission", "更新用户隧道权限", "POST", "/api/v1/agent/user-permissions/update", "user-permissions:write"),
            tool("remove_user_permission", "移除用户隧道权限", "POST", "/api/v1/agent/user-permissions/remove", "user-permissions:write"),
            tool("list_tunnels", "获取隧道列表", "POST", "/api/v1/agent/tunnels/list", "tunnels:read"),
            tool("get_tunnel", "获取隧道详情", "POST", "/api/v1/agent/tunnels/get", "tunnels:read"),
            tool("create_tunnel", "创建隧道", "POST", "/api/v1/agent/tunnels/create", "tunnels:write"),
            tool("update_tunnel", "更新隧道", "POST", "/api/v1/agent/tunnels/update", "tunnels:write"),
            tool("delete_tunnel", "删除隧道", "POST", "/api/v1/agent/tunnels/delete", "tunnels:delete"),
            tool("diagnose_tunnel", "诊断隧道", "POST", "/api/v1/agent/tunnels/diagnose", "tunnels:diagnose"),
            tool("list_forwards", "获取转发列表", "POST", "/api/v1/agent/forwards/list", "forwards:read"),
            tool("create_forward", "创建转发", "POST", "/api/v1/agent/forwards/create", "forwards:write"),
            tool("update_forward", "更新转发", "POST", "/api/v1/agent/forwards/update", "forwards:write"),
            tool("delete_forward", "删除转发", "POST", "/api/v1/agent/forwards/delete", "forwards:delete"),
            tool("force_delete_forward", "强制删除转发", "POST", "/api/v1/agent/forwards/force-delete", "forwards:delete"),
            tool("pause_forward", "暂停转发", "POST", "/api/v1/agent/forwards/pause", "forwards:control"),
            tool("resume_forward", "恢复转发", "POST", "/api/v1/agent/forwards/resume", "forwards:control"),
            tool("diagnose_forward", "诊断转发", "POST", "/api/v1/agent/forwards/diagnose", "forwards:diagnose"),
            tool("reorder_forwards", "更新转发排序", "POST", "/api/v1/agent/forwards/reorder", "forwards:reorder"),
            tool("query_stats_overview", "查询统计总览", "POST", "/api/v1/agent/stats/overview", "stats:read"),
            tool("query_stats_series", "查询统计序列", "POST", "/api/v1/agent/stats/series", "stats:read"),
            tool("query_stats_hour_detail", "查询小时明细", "POST", "/api/v1/agent/stats/hour-detail", "stats:read")
    );

    @Override
    public Map<String, Object> buildDescriptor(String format, Set<String> scopes) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (DescriptorTool tool : TOOL_REGISTRY) {
            if (scopes.contains(tool.requiredScope)) {
                entries.add(tool.toMap());
            }
        }

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("format", format);
        if ("hermes-agent".equals(format)) {
            descriptor.put("capabilities", entries);
        } else {
            descriptor.put("tools", entries);
        }
        return descriptor;
    }

    private static DescriptorTool tool(String name, String description, String method, String path, String requiredScope) {
        return new DescriptorTool(name, description, method, path, requiredScope);
    }

    private record DescriptorTool(
            String name,
            String description,
            String method,
            String path,
            String requiredScope
    ) {
        private Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("description", description);
            item.put("method", method);
            item.put("path", path);
            item.put("requiredScope", requiredScope);
            item.put("inputSchema", Map.of("type", "object", "properties", Map.of()));
            return item;
        }
    }
}
