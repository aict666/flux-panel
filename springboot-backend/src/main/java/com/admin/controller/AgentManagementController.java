package com.admin.controller;

import com.admin.common.context.ActorContext;
import com.admin.common.context.ActorContextHolder;
import com.admin.common.dto.*;
import com.admin.common.lang.R;
import com.admin.common.utils.AgentScopeGuard;
import com.admin.entity.Forward;
import com.admin.entity.ForwardStatisticsFlow;
import com.admin.entity.StatisticsFlow;
import com.admin.entity.Tunnel;
import com.admin.service.*;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/agent")
public class AgentManagementController {

    @Resource
    private UserService userService;

    @Resource
    private UserTunnelService userTunnelService;

    @Resource
    private TunnelService tunnelService;

    @Resource
    private ForwardService forwardService;

    @Resource
    private StatisticsFlowService statisticsFlowService;

    @Resource
    private ForwardStatisticsFlowService forwardStatisticsFlowService;

    @Resource
    private AgentDescriptorService agentDescriptorService;

    private R guard(String scope) {
        return AgentScopeGuard.requireScope(scope);
    }

    @PostMapping("/users/list")
    public R listUsers() {
        R denied = guard("users:read");
        if (denied != null) return denied;
        return userService.getAllUsers();
    }

    @PostMapping("/users/create")
    public R createUser(@Valid @RequestBody UserDto dto) {
        R denied = guard("users:write");
        if (denied != null) return denied;
        return userService.createUser(dto);
    }

    @PostMapping("/users/update")
    public R updateUser(@Valid @RequestBody UserUpdateDto dto) {
        R denied = guard("users:write");
        if (denied != null) return denied;
        return userService.updateUser(dto);
    }

    @PostMapping("/users/delete")
    public R deleteUser(@RequestBody Map<String, Object> params) {
        R denied = guard("users:delete");
        if (denied != null) return denied;
        return userService.deleteUser(Long.valueOf(params.get("id").toString()));
    }

    @PostMapping("/user-permissions/list")
    public R listUserPermissions(@RequestBody @Valid UserTunnelQueryDto dto) {
        R denied = guard("user-permissions:read");
        if (denied != null) return denied;
        return userTunnelService.getUserTunnelList(dto);
    }

    @PostMapping("/user-permissions/assign")
    public R assignUserPermission(@RequestBody @Valid UserTunnelDto dto) {
        R denied = guard("user-permissions:write");
        if (denied != null) return denied;
        return userTunnelService.assignUserTunnel(dto);
    }

    @PostMapping("/user-permissions/update")
    public R updateUserPermission(@RequestBody @Valid UserTunnelUpdateDto dto) {
        R denied = guard("user-permissions:write");
        if (denied != null) return denied;
        return userTunnelService.updateUserTunnel(dto);
    }

    @PostMapping("/user-permissions/remove")
    public R removeUserPermission(@RequestBody Map<String, Object> params) {
        R denied = guard("user-permissions:write");
        if (denied != null) return denied;
        return userTunnelService.removeUserTunnel(Integer.valueOf(params.get("id").toString()));
    }

    @PostMapping("/tunnels/list")
    public R listTunnels() {
        R denied = guard("tunnels:read");
        if (denied != null) return denied;
        return tunnelService.getAllTunnels();
    }

    @PostMapping("/tunnels/get")
    public R getTunnel(@RequestBody Map<String, Object> params) {
        R denied = guard("tunnels:read");
        if (denied != null) return denied;
        Tunnel tunnel = tunnelService.getById(Long.valueOf(params.get("id").toString()));
        if (tunnel == null) {
            return R.err("隧道不存在");
        }
        return R.ok(tunnel);
    }

    @PostMapping("/tunnels/create")
    public R createTunnel(@RequestBody @Valid TunnelDto dto) {
        R denied = guard("tunnels:write");
        if (denied != null) return denied;
        return tunnelService.createTunnel(dto);
    }

    @PostMapping("/tunnels/update")
    public R updateTunnel(@RequestBody @Valid TunnelUpdateDto dto) {
        R denied = guard("tunnels:write");
        if (denied != null) return denied;
        return tunnelService.updateTunnel(dto);
    }

    @PostMapping("/tunnels/delete")
    public R deleteTunnel(@RequestBody Map<String, Object> params) {
        R denied = guard("tunnels:delete");
        if (denied != null) return denied;
        return tunnelService.deleteTunnel(Long.valueOf(params.get("id").toString()));
    }

    @PostMapping("/tunnels/diagnose")
    public R diagnoseTunnel(@RequestBody Map<String, Object> params) {
        R denied = guard("tunnels:diagnose");
        if (denied != null) return denied;
        return tunnelService.diagnoseTunnel(Long.valueOf(params.get("tunnelId").toString()));
    }

    @PostMapping("/forwards/list")
    public R listForwards() {
        R denied = guard("forwards:read");
        if (denied != null) return denied;
        return forwardService.getAllForwards();
    }

    @PostMapping("/forwards/create")
    public R createForward(@RequestBody @Valid AgentForwardCreateDto dto) {
        R denied = guard("forwards:write");
        if (denied != null) return denied;
        ForwardDto forwardDto = new ForwardDto();
        BeanUtils.copyProperties(dto, forwardDto);
        return forwardService.createForwardForUser(dto.getUserId(), forwardDto);
    }

    @PostMapping("/forwards/update")
    public R updateForward(@RequestBody @Valid ForwardUpdateDto dto) {
        R denied = guard("forwards:write");
        if (denied != null) return denied;
        return forwardService.updateForward(dto);
    }

    @PostMapping("/forwards/delete")
    public R deleteForward(@RequestBody Map<String, Object> params) {
        R denied = guard("forwards:delete");
        if (denied != null) return denied;
        return forwardService.deleteForward(Long.valueOf(params.get("id").toString()));
    }

    @PostMapping("/forwards/force-delete")
    public R forceDeleteForward(@RequestBody Map<String, Object> params) {
        R denied = guard("forwards:delete");
        if (denied != null) return denied;
        return forwardService.forceDeleteForward(Long.valueOf(params.get("id").toString()));
    }

    @PostMapping("/forwards/pause")
    public R pauseForward(@RequestBody Map<String, Object> params) {
        R denied = guard("forwards:control");
        if (denied != null) return denied;
        return forwardService.pauseForward(Long.valueOf(params.get("id").toString()));
    }

    @PostMapping("/forwards/resume")
    public R resumeForward(@RequestBody Map<String, Object> params) {
        R denied = guard("forwards:control");
        if (denied != null) return denied;
        return forwardService.resumeForward(Long.valueOf(params.get("id").toString()));
    }

    @PostMapping("/forwards/diagnose")
    public R diagnoseForward(@RequestBody Map<String, Object> params) {
        R denied = guard("forwards:diagnose");
        if (denied != null) return denied;
        return forwardService.diagnoseForward(Long.valueOf(params.get("id").toString()));
    }

    @PostMapping("/forwards/reorder")
    public R reorderForwards(@RequestBody Map<String, Object> params) {
        R denied = guard("forwards:reorder");
        if (denied != null) return denied;
        return forwardService.updateForwardOrder(params);
    }

    @PostMapping("/stats/overview")
    public R statsOverview(@RequestBody @Valid AgentStatsQueryDto dto) {
        R denied = guard("stats:read");
        if (denied != null) return denied;
        return R.ok(buildOverview(dto));
    }

    @PostMapping("/stats/series")
    public R statsSeries(@RequestBody @Valid AgentStatsQueryDto dto) {
        R denied = guard("stats:read");
        if (denied != null) return denied;
        return R.ok(buildSeries(dto));
    }

    @PostMapping("/stats/hour-detail")
    public R statsHourDetail(@RequestBody @Valid AgentStatsQueryDto dto) {
        R denied = guard("stats:read");
        if (denied != null) return denied;
        QueryWrapper<ForwardStatisticsFlow> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("hour_time", dto.getHourTime());
        if (dto.getUserId() != null) {
            queryWrapper.eq("user_id", dto.getUserId());
        }
        if (dto.getTunnelId() != null) {
            queryWrapper.eq("tunnel_id", dto.getTunnelId());
        }
        if (dto.getForwardId() != null) {
            queryWrapper.eq("forward_id", dto.getForwardId());
        }
        queryWrapper.orderByDesc("flow");
        return R.ok(forwardStatisticsFlowService.list(queryWrapper));
    }

    @GetMapping("/descriptor")
    public R descriptor(@Valid AgentDescriptorDto descriptorDto) {
        R denied = guard("descriptors:read");
        if (denied != null) return denied;
        ActorContext actorContext = ActorContextHolder.get();
        Set<String> scopes = actorContext == null ? Set.of() : actorContext.getScopes();
        return R.ok(agentDescriptorService.buildDescriptor(descriptorDto.getFormat(), scopes));
    }

    private Map<String, Object> buildOverview(AgentStatsQueryDto dto) {
        QueryWrapper<StatisticsFlow> userQuery = new QueryWrapper<StatisticsFlow>()
                .ge("hour_time", dto.getStartTime())
                .le("hour_time", dto.getEndTime());
        if (dto.getUserId() != null) {
            userQuery.eq("user_id", dto.getUserId());
        }
        List<StatisticsFlow> userFlows = statisticsFlowService.list(userQuery);

        QueryWrapper<ForwardStatisticsFlow> forwardQuery = new QueryWrapper<ForwardStatisticsFlow>()
                .ge("hour_time", dto.getStartTime())
                .le("hour_time", dto.getEndTime());
        if (dto.getUserId() != null) {
            forwardQuery.eq("user_id", dto.getUserId());
        }
        if (dto.getTunnelId() != null) {
            forwardQuery.eq("tunnel_id", dto.getTunnelId());
        }
        if (dto.getForwardId() != null) {
            forwardQuery.eq("forward_id", dto.getForwardId());
        }
        List<ForwardStatisticsFlow> forwardFlows = forwardStatisticsFlowService.list(forwardQuery);

        long inFlow = userFlows.stream().mapToLong(item -> item.getInFlow() == null ? 0L : item.getInFlow()).sum();
        long outFlow = userFlows.stream().mapToLong(item -> item.getOutFlow() == null ? 0L : item.getOutFlow()).sum();
        long flow = userFlows.stream().mapToLong(item -> item.getFlow() == null ? 0L : item.getFlow()).sum();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("userCount", userFlows.stream().map(StatisticsFlow::getUserId).distinct().count());
        overview.put("forwardCount", forwardFlows.stream().map(ForwardStatisticsFlow::getForwardId).distinct().count());
        overview.put("inFlow", inFlow);
        overview.put("outFlow", outFlow);
        overview.put("flow", flow);
        return overview;
    }

    private Map<String, Object> buildSeries(AgentStatsQueryDto dto) {
        QueryWrapper<StatisticsFlow> userQuery = new QueryWrapper<StatisticsFlow>()
                .ge("hour_time", dto.getStartTime())
                .le("hour_time", dto.getEndTime())
                .orderByAsc("hour_time");
        if (dto.getUserId() != null) {
            userQuery.eq("user_id", dto.getUserId());
        }

        QueryWrapper<ForwardStatisticsFlow> forwardQuery = new QueryWrapper<ForwardStatisticsFlow>()
                .ge("hour_time", dto.getStartTime())
                .le("hour_time", dto.getEndTime())
                .orderByAsc("hour_time");
        if (dto.getUserId() != null) {
            forwardQuery.eq("user_id", dto.getUserId());
        }
        if (dto.getTunnelId() != null) {
            forwardQuery.eq("tunnel_id", dto.getTunnelId());
        }
        if (dto.getForwardId() != null) {
            forwardQuery.eq("forward_id", dto.getForwardId());
        }

        List<StatisticsFlow> userFlows = statisticsFlowService.list(userQuery);
        List<ForwardStatisticsFlow> forwardFlows = forwardStatisticsFlowService.list(forwardQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userSeries", userFlows);
        result.put("forwardSeries", forwardFlows);
        return result;
    }
}
