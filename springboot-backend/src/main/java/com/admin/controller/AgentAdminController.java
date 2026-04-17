package com.admin.controller;

import com.admin.common.annotation.RequireRole;
import com.admin.common.dto.AgentAuditQueryDto;
import com.admin.common.dto.AgentClientDto;
import com.admin.common.dto.AgentClientUpdateDto;
import com.admin.common.dto.AgentDescriptorDto;
import com.admin.common.dto.AgentKeyRotateDto;
import com.admin.common.lang.R;
import com.admin.entity.AgentApiKey;
import com.admin.entity.AgentClient;
import com.admin.service.AgentApiAuditLogService;
import com.admin.service.AgentApiKeyService;
import com.admin.service.AgentClientService;
import com.admin.service.AgentDescriptorService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/agent-admin")
public class AgentAdminController {

    @Resource
    private AgentClientService agentClientService;

    @Resource
    private AgentApiKeyService agentApiKeyService;

    @Resource
    private AgentApiAuditLogService agentApiAuditLogService;

    @Resource
    private AgentDescriptorService agentDescriptorService;

    @RequireRole
    @PostMapping("/clients/create")
    public R createClient(@Valid @RequestBody AgentClientDto dto) {
        return agentClientService.createClient(dto);
    }

    @RequireRole
    @PostMapping("/clients/update")
    public R updateClient(@Valid @RequestBody AgentClientUpdateDto dto) {
        return agentClientService.updateClient(dto);
    }

    @RequireRole
    @PostMapping("/clients/delete")
    public R deleteClient(@RequestBody Map<String, Object> params) {
        Long clientId = Long.valueOf(params.get("id").toString());
        return agentClientService.deleteClient(clientId);
    }

    @RequireRole
    @PostMapping("/clients/list")
    public R listClients() {
        List<AgentClient> clients = agentClientService.list(new QueryWrapper<AgentClient>().orderByDesc("created_time"));
        if (clients.isEmpty()) {
            return R.ok(List.of());
        }

        List<Long> clientIds = clients.stream().map(AgentClient::getId).toList();
        List<AgentApiKey> keys = agentApiKeyService.list(new QueryWrapper<AgentApiKey>().in("client_id", clientIds).orderByDesc("created_time"));
        Map<Long, AgentApiKey> latestKeyByClientId = keys.stream()
                .collect(Collectors.toMap(
                        AgentApiKey::getClientId,
                        key -> key,
                        (left, right) -> left.getCreatedTime() >= right.getCreatedTime() ? left : right,
                        LinkedHashMap::new
                ));

        List<Map<String, Object>> items = new ArrayList<>();
        for (AgentClient client : clients) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", client.getId());
            item.put("name", client.getName());
            item.put("agentType", client.getAgentType());
            item.put("description", client.getDescription());
            item.put("status", client.getStatus());
            item.put("scopes", JSON.parseArray(client.getScopeJson(), String.class));
            item.put("createdTime", client.getCreatedTime());
            item.put("updatedTime", client.getUpdatedTime());

            AgentApiKey key = latestKeyByClientId.get(client.getId());
            if (key != null) {
                item.put("keyId", key.getId());
                item.put("keyPrefix", key.getKeyPrefix());
                item.put("keyStatus", key.getStatus());
                item.put("expiresTime", key.getExpiresTime());
                item.put("lastUsedTime", key.getLastUsedTime());
                item.put("lastUsedIp", key.getLastUsedIp());
            }
            items.add(item);
        }
        return R.ok(items);
    }

    @RequireRole
    @PostMapping("/clients/{id}/rotate-key")
    public R rotateKey(@PathVariable("id") Long clientId, @Valid @RequestBody AgentKeyRotateDto dto) {
        return agentClientService.rotateKey(clientId, dto);
    }

    @RequireRole
    @PostMapping("/keys/{id}/revoke")
    public R revokeKey(@PathVariable("id") Long keyId) {
        AgentApiKey key = agentApiKeyService.getById(keyId);
        if (key == null) {
            return R.err("密钥不存在");
        }
        agentApiKeyService.update(
                null,
                new UpdateWrapper<AgentApiKey>()
                        .eq("id", keyId)
                        .set("status", 0)
                        .set("updated_time", System.currentTimeMillis())
        );
        return R.ok();
    }

    @RequireRole
    @PostMapping("/audits/list")
    public R listAudits(@RequestBody(required = false) AgentAuditQueryDto queryDto) {
        QueryWrapper<com.admin.entity.AgentApiAuditLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_time");
        if (queryDto != null) {
            if (queryDto.getClientId() != null) {
                queryWrapper.eq("client_id", queryDto.getClientId());
            }
            if (queryDto.getSuccess() != null) {
                queryWrapper.eq("success", queryDto.getSuccess());
            }
            if (queryDto.getStartTime() != null) {
                queryWrapper.ge("created_time", queryDto.getStartTime());
            }
            if (queryDto.getEndTime() != null) {
                queryWrapper.le("created_time", queryDto.getEndTime());
            }
        }
        return R.ok(agentApiAuditLogService.list(queryWrapper));
    }

    @RequireRole
    @GetMapping("/descriptor/{clientId}")
    public R getDescriptor(
            @PathVariable("clientId") Long clientId,
            @Valid AgentDescriptorDto descriptorDto
    ) {
        AgentClient client = agentClientService.getById(clientId);
        if (client == null) {
            return R.err("服务账号不存在");
        }
        Set<String> scopes = Set.copyOf(JSON.parseArray(client.getScopeJson(), String.class));
        return R.ok(agentDescriptorService.buildDescriptor(descriptorDto.getFormat(), scopes));
    }
}
