package com.admin.service.impl;

import com.admin.common.dto.AgentClientDto;
import com.admin.common.dto.AgentClientUpdateDto;
import com.admin.common.dto.AgentKeyRotateDto;
import com.admin.common.lang.R;
import com.admin.common.utils.AgentKeyUtil;
import com.admin.entity.AgentApiKey;
import com.admin.entity.AgentClient;
import com.admin.mapper.AgentClientMapper;
import com.admin.service.AgentApiKeyService;
import com.admin.service.AgentClientService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentClientServiceImpl extends ServiceImpl<AgentClientMapper, AgentClient> implements AgentClientService {

    @Resource
    private AgentApiKeyService agentApiKeyService;

    @Override
    public R createClient(AgentClientDto dto) {
        long now = System.currentTimeMillis();
        if (this.count(new QueryWrapper<AgentClient>().eq("name", dto.getName())) > 0) {
            return R.err("服务账号名称已存在");
        }

        AgentClient client = new AgentClient();
        client.setName(dto.getName());
        client.setAgentType(dto.getAgentType());
        client.setDescription(dto.getDescription());
        client.setScopeJson(JSON.toJSONString(dto.getScopes()));
        client.setCreatedTime(now);
        client.setUpdatedTime(now);
        client.setStatus(1);
        this.save(client);

        KeyCreationResult keyCreationResult = createKey(client.getId(), dto.getExpiresTime());
        return R.ok(buildKeyResponse(client.getId(), keyCreationResult));
    }

    @Override
    public R updateClient(AgentClientUpdateDto dto) {
        AgentClient client = this.getById(dto.getId());
        if (client == null) {
            return R.err("服务账号不存在");
        }
        if (this.count(new QueryWrapper<AgentClient>().eq("name", dto.getName()).ne("id", dto.getId())) > 0) {
            return R.err("服务账号名称已存在");
        }

        client.setName(dto.getName());
        client.setDescription(dto.getDescription());
        client.setScopeJson(JSON.toJSONString(dto.getScopes()));
        client.setStatus(dto.getStatus());
        client.setUpdatedTime(System.currentTimeMillis());
        this.updateById(client);
        return R.ok();
    }

    @Override
    public R deleteClient(Long clientId) {
        AgentClient client = this.getById(clientId);
        if (client == null) {
            return R.err("服务账号不存在");
        }

        long now = System.currentTimeMillis();
        client.setStatus(0);
        client.setUpdatedTime(now);
        this.updateById(client);
        revokeClientKeys(clientId, now);
        return R.ok();
    }

    @Override
    public R rotateKey(Long clientId, AgentKeyRotateDto dto) {
        AgentClient client = this.getById(clientId);
        if (client == null) {
            return R.err("服务账号不存在");
        }

        long now = System.currentTimeMillis();
        revokeClientKeys(clientId, now);
        KeyCreationResult keyCreationResult = createKey(clientId, dto.getExpiresTime());
        return R.ok(buildKeyResponse(clientId, keyCreationResult));
    }

    private void revokeClientKeys(Long clientId, long now) {
        agentApiKeyService.update(
                null,
                new UpdateWrapper<AgentApiKey>()
                        .eq("client_id", clientId)
                        .set("status", 0)
                        .set("updated_time", now)
        );
    }

    private KeyCreationResult createKey(Long clientId, Long expiresTime) {
        long now = System.currentTimeMillis();
        String plaintextKey = AgentKeyUtil.generatePlaintextKey();

        AgentApiKey apiKey = new AgentApiKey();
        apiKey.setClientId(clientId);
        apiKey.setKeyPrefix(AgentKeyUtil.extractPrefix(plaintextKey));
        apiKey.setKeyHash(AgentKeyUtil.hashKey(plaintextKey));
        apiKey.setExpiresTime(expiresTime);
        apiKey.setCreatedTime(now);
        apiKey.setUpdatedTime(now);
        apiKey.setStatus(1);
        agentApiKeyService.save(apiKey);

        return new KeyCreationResult(apiKey, plaintextKey);
    }

    private Map<String, Object> buildKeyResponse(Long clientId, KeyCreationResult keyCreationResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientId", clientId);
        result.put("keyId", keyCreationResult.agentApiKey().getId());
        result.put("keyPrefix", keyCreationResult.agentApiKey().getKeyPrefix());
        result.put("plaintextKey", keyCreationResult.plaintextKey());
        result.put("expiresTime", keyCreationResult.agentApiKey().getExpiresTime());
        return result;
    }

    private record KeyCreationResult(AgentApiKey agentApiKey, String plaintextKey) {
    }
}
