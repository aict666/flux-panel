package com.admin.service.impl;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.admin.common.dto.*;
import com.admin.common.lang.R;
import com.admin.common.utils.GostUtil;
import com.admin.common.utils.JwtUtil;
import com.admin.common.utils.Md5Util;
import com.admin.entity.*;
import com.admin.mapper.UserMapper;
import com.admin.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final long HOUR_MILLIS = ChronoUnit.HOURS.getDuration().toMillis();
    private static final long MAX_FLOW_STATS_RANGE_MILLIS = ChronoUnit.DAYS.getDuration().toMillis() * 30;
    private static final DateTimeFormatter LEGACY_HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter RANGE_HOUR_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Resource
    @Lazy
    ForwardService forwardService;

    @Resource
    UserMapper userMapper;

    @Resource
    @Lazy
    TunnelService tunnelService;
    
    @Resource
    @Lazy
    NodeService nodeService;

    @Resource
    UserTunnelService userTunnelService;

    @Resource
    ViteConfigService viteConfigService;

    @Resource
    StatisticsFlowService statisticsFlowService;

    @Resource
    ForwardStatisticsFlowService forwardStatisticsFlowService;

    @Resource
    @Lazy
    ForwardPortService forwardPortService;

    @Resource
    ImageCaptchaApplication application;


    @Override
    public R login(LoginDto loginDto) {
        ViteConfig viteConfig = viteConfigService.getOne(new QueryWrapper<ViteConfig>().eq("name", "captcha_enabled"));
        if (viteConfig != null && Objects.equals(viteConfig.getValue(), "true")) {
            if (StringUtils.isBlank(loginDto.getCaptchaId())) return R.err("验证码校验失败");
            boolean valid = ((SecondaryVerificationApplication) application).secondaryVerification(loginDto.getCaptchaId());
            if (!valid)  return R.err("验证码校验失败");
        }

        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getUser, loginDto.getUsername()));
        if (user == null) return R.err("账号或密码错误");
        if (!user.getPwd().equals(Md5Util.md5(loginDto.getPassword())))  return R.err("账号或密码错误");
        if (user.getStatus() == 0)  return R.err("账号被停用");
        String token = JwtUtil.generateToken(user);
        boolean requirePasswordChange = Objects.equals(loginDto.getUsername(), "admin_user") || Objects.equals(loginDto.getPassword(), "admin_user");
        return R.ok(MapUtil.builder()
                .put("token", token)
                .put("name", user.getUser())
                .put("role_id", user.getRoleId())
                .put("requirePasswordChange", requirePasswordChange)
                .build());
    }

    @Override
    public R createUser(UserDto userDto) {
        int count = this.count(new LambdaQueryWrapper<User>().eq(User::getUser, userDto.getUser()));
        if (count > 0) return R.err("用户名已存在");
        User user = new User();
        BeanUtils.copyProperties(userDto, user);
        user.setPwd(Md5Util.md5(userDto.getPwd()));
        user.setStatus(1);
        user.setRoleId(1);
        long currentTime = System.currentTimeMillis();
        user.setCreatedTime(currentTime);
        user.setUpdatedTime(currentTime);
        this.save(user);
        return R.ok();
    }

    @Override
    public R getAllUsers() {
        List<User> list = this.list(new QueryWrapper<User>().ne("role_id", 0));
        return R.ok(list);
    }

    @Override
    public R updateUser(UserUpdateDto userUpdateDto) {
        User user = this.getById(userUpdateDto.getId());
        if (user == null) return R.err("用户不存在");
        if (user.getRoleId() == 0) return R.err("请不要作死");

        int count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUser, userUpdateDto.getUser())
                .ne(User::getId, userUpdateDto.getId()));
        if (count > 0) return R.err("用户名已存在");


        User updateUser = new User();
        BeanUtils.copyProperties(userUpdateDto, updateUser);
        if (StrUtil.isNotBlank(userUpdateDto.getPwd())) {
            updateUser.setPwd(Md5Util.md5(userUpdateDto.getPwd()));
        } else {
            updateUser.setPwd(null); // 不更新密码字段
        }
        updateUser.setUpdatedTime(System.currentTimeMillis());
        this.updateById(updateUser);
        return R.ok();
    }

    @Override
    public R deleteUser(Long id) {
        User user = this.getById(id);
        if (user == null) return R.err("用户不存在");
        if (user.getRoleId() == 0) return R.err("请不要作死");
        List<Forward> forwardList = forwardService.list(new QueryWrapper<Forward>().eq("user_id", id));
        for (Forward forward : forwardList) {
            forwardService.deleteForward(forward.getId());
        }
        forwardService.remove(new QueryWrapper<Forward>().eq("user_id", id));
        userTunnelService.remove(new QueryWrapper<UserTunnel>().eq("user_id", id));
        statisticsFlowService.remove(new QueryWrapper<StatisticsFlow>().eq("user_id", id));
        forwardStatisticsFlowService.remove(new QueryWrapper<ForwardStatisticsFlow>().eq("user_id", id));
        this.removeById(id);
        return R.ok();
    }

    @Override
    public R getUserPackageInfo() {
        Integer userId = JwtUtil.getUserIdFromToken();
        User user = this.getById(userId);
        if (user == null) return R.err("用户不存在");
        UserPackageDto.UserInfoDto userInfo = buildUserInfoDto(user);
        List<UserPackageDto.UserTunnelDetailDto> tunnelPermissions = userMapper.getUserTunnelDetails(userId);
        List<UserPackageDto.UserForwardDetailDto> forwards = userMapper.getUserForwardDetails(user.getId().intValue());
        fillForwardInIpAndPort(forwards);
        List<StatisticsFlow> statisticsFlows = getLast24HoursFlowStatistics(user.getId());
        UserPackageDto packageDto = new UserPackageDto();
        packageDto.setUserInfo(userInfo);
        packageDto.setTunnelPermissions(tunnelPermissions);
        packageDto.setForwards(forwards);
        packageDto.setStatisticsFlows(statisticsFlows);
        return R.ok(packageDto);
    }

    @Override
    public R getUserPackageFlowStats(FlowStatsQueryDto flowStatsQueryDto) {
        if (flowStatsQueryDto.getEndTime() <= flowStatsQueryDto.getStartTime()) {
            return R.err("结束时间必须大于开始时间");
        }
        if (flowStatsQueryDto.getEndTime() - flowStatsQueryDto.getStartTime() > MAX_FLOW_STATS_RANGE_MILLIS) {
            return R.err("统计时间范围不能超过30天");
        }

        Integer userId = JwtUtil.getUserIdFromToken();
        User user = this.getById(userId);
        if (user == null) {
            return R.err("用户不存在");
        }

        NormalizedRange range = normalizeRange(flowStatsQueryDto.getStartTime(), flowStatsQueryDto.getEndTime());
        List<UserPackageDto.UserForwardDetailDto> forwards = userMapper.getUserForwardDetails(user.getId().intValue());
        fillForwardInIpAndPort(forwards);

        UserPackageFlowStatsDto result = new UserPackageFlowStatsDto();
        UserPackageFlowStatsDto.RangeDto rangeDto = new UserPackageFlowStatsDto.RangeDto();
        rangeDto.setStartTime(range.startTime);
        rangeDto.setEndTime(range.endTime);
        result.setRange(rangeDto);
        result.setSeries(buildSeries(user.getId(), range));
        result.setForwardStats(buildForwardStats(user.getId(), range, forwards));
        return R.ok(result);
    }

    @Override
    public R updatePassword(ChangePasswordDto changePasswordDto) {
        Integer userId = JwtUtil.getUserIdFromToken();
        User user = this.getById(userId);
        if (user == null) return R.err("用户不存在");
        if (!changePasswordDto.getNewPassword().equals(changePasswordDto.getConfirmPassword())) {
            return R.err("新密码和确认密码不匹配");
        }
        String currentPasswordMd5 = Md5Util.md5(changePasswordDto.getCurrentPassword());
        if (!user.getPwd().equals(currentPasswordMd5)) {
            return R.err("当前密码错误");
        }
        if (!user.getUser().equals(changePasswordDto.getNewUsername())) {
            user.setPwd(Md5Util.md5(changePasswordDto.getNewPassword()));
            int count = this.count(new LambdaQueryWrapper<User>()
                    .eq(User::getUser, changePasswordDto.getNewUsername())
                    .ne(User::getId, user.getId()));
            if (count > 0) return R.err("用户名已存在");
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUser(changePasswordDto.getNewUsername());
        updateUser.setPwd(Md5Util.md5(changePasswordDto.getNewPassword()));
        updateUser.setUpdatedTime(System.currentTimeMillis());
        this.updateById(updateUser);
        return R.ok();
    }

    @Override
    public R reset(ResetFlowDto resetFlowDto) {
        if (resetFlowDto.getType() == 1){ // 清零账号流量
            User user = this.getById(resetFlowDto.getId());
            if (user == null) return R.err("用户不存在");
            user.setInFlow(0L);
            user.setOutFlow(0L);
            this.updateById(user);
        }else { // 清零隧道流量
            UserTunnel tunnel = userTunnelService.getById(resetFlowDto.getId());
            if (tunnel == null) return R.err("隧道不存在");
            tunnel.setInFlow(0L);
            tunnel.setOutFlow(0L);
            userTunnelService.updateById(tunnel);
        }
        return R.ok();
    }

    private UserPackageDto.UserInfoDto buildUserInfoDto(User user) {
        UserPackageDto.UserInfoDto userInfo = new UserPackageDto.UserInfoDto();
        userInfo.setId(user.getId());
        userInfo.setUser(user.getUser());
        userInfo.setStatus(user.getStatus());
        userInfo.setFlow(user.getFlow());
        userInfo.setInFlow(user.getInFlow());
        userInfo.setOutFlow(user.getOutFlow());
        userInfo.setNum(user.getNum());
        userInfo.setExpTime(user.getExpTime());
        userInfo.setFlowResetTime(user.getFlowResetTime());
        userInfo.setCreatedTime(user.getCreatedTime());
        userInfo.setUpdatedTime(user.getUpdatedTime());
        return userInfo;
    }

    private List<StatisticsFlow> getLast24HoursFlowStatistics(Long userId) {
        long endTime = truncateToHour(System.currentTimeMillis());
        long startTime = endTime - 23 * HOUR_MILLIS;
        List<StatisticsFlow> recentFlows = statisticsFlowService.list(
                new QueryWrapper<StatisticsFlow>()
                        .eq("user_id", userId)
                        .between("hour_time", startTime, endTime)
                        .orderByAsc("hour_time")
        );

        Map<Long, StatisticsFlow> flowMap = new LinkedHashMap<>();
        for (StatisticsFlow statisticsFlow : recentFlows) {
            flowMap.put(statisticsFlow.getHourTime(), statisticsFlow);
        }

        List<StatisticsFlow> result = new ArrayList<>();
        for (long cursor = startTime; cursor <= endTime; cursor += HOUR_MILLIS) {
            StatisticsFlow statisticsFlow = flowMap.get(cursor);
            if (statisticsFlow == null) {
                statisticsFlow = new StatisticsFlow();
                statisticsFlow.setUserId(userId);
                statisticsFlow.setHourTime(cursor);
                statisticsFlow.setTime(formatHour(cursor, LEGACY_HOUR_FORMATTER));
                statisticsFlow.setInFlow(0L);
                statisticsFlow.setOutFlow(0L);
                statisticsFlow.setFlow(0L);
                statisticsFlow.setTotalInFlow(0L);
                statisticsFlow.setTotalOutFlow(0L);
                statisticsFlow.setTotalFlow(0L);
                statisticsFlow.setCreatedTime(cursor);
            } else if (statisticsFlow.getTime() == null || statisticsFlow.getTime().trim().isEmpty()) {
                statisticsFlow.setTime(formatHour(cursor, LEGACY_HOUR_FORMATTER));
            }
            result.add(statisticsFlow);
        }
        return result;
    }

    private void fillForwardInIpAndPort(List<UserPackageDto.UserForwardDetailDto> forwards) {
        for (UserPackageDto.UserForwardDetailDto forward : forwards) {
            Tunnel tunnel = tunnelService.getById(forward.getTunnelId());
            if (tunnel == null) continue;
            
            List<ForwardPort> forwardPorts = forwardPortService.list(
                    new QueryWrapper<ForwardPort>().eq("forward_id", forward.getId())
            );
            if (forwardPorts.isEmpty()) continue;
            
            boolean useTunnelInIp = tunnel.getInIp() != null && !tunnel.getInIp().trim().isEmpty();
            java.util.Set<String> ipPortSet = new java.util.LinkedHashSet<>();
            
            if (useTunnelInIp) {
                // 使用隧道的inIp（求笛卡尔积）
                List<String> ipList = new ArrayList<>();
                List<Integer> portList = new ArrayList<>();
                
                String[] tunnelInIps = tunnel.getInIp().split(",");
                for (String ip : tunnelInIps) {
                    if (ip != null && !ip.trim().isEmpty()) {
                        ipList.add(ip.trim());
                    }
                }
                
                for (ForwardPort forwardPort : forwardPorts) {
                    if (forwardPort.getPort() != null) {
                        portList.add(forwardPort.getPort());
                    }
                }
                
                List<String> uniqueIps = ipList.stream().distinct().toList();
                List<Integer> uniquePorts = portList.stream().distinct().toList();
                
                for (String ip : uniqueIps) {
                    for (Integer port : uniquePorts) {
                        ipPortSet.add(ip + ":" + port);
                    }
                }
                
                if (!uniquePorts.isEmpty()) {
                    forward.setInPort(uniquePorts.getFirst());
                }
            } else {
                // 使用节点的serverIp（一对一，不求笛卡尔积）
                for (ForwardPort forwardPort : forwardPorts) {
                    Node node = nodeService.getById(forwardPort.getNodeId());
                    if (node != null && node.getServerIp() != null && forwardPort.getPort() != null) {
                        ipPortSet.add(node.getServerIp() + ":" + forwardPort.getPort());
                    }
                }
                
                if (!forwardPorts.isEmpty() && forwardPorts.getFirst().getPort() != null) {
                    forward.setInPort(forwardPorts.getFirst().getPort());
                }
            }
            
            if (!ipPortSet.isEmpty()) {
                forward.setInIp(String.join(",", ipPortSet));
            }
        }
    }

    private List<UserPackageFlowStatsDto.SeriesPointDto> buildSeries(Long userId, NormalizedRange range) {
        List<StatisticsFlow> flowList = statisticsFlowService.list(
                new QueryWrapper<StatisticsFlow>()
                        .eq("user_id", userId)
                        .between("hour_time", range.startTime, range.endTime)
                        .orderByAsc("hour_time")
        );
        Map<Long, StatisticsFlow> flowMap = new LinkedHashMap<>();
        for (StatisticsFlow statisticsFlow : flowList) {
            flowMap.put(statisticsFlow.getHourTime(), statisticsFlow);
        }

        List<UserPackageFlowStatsDto.SeriesPointDto> result = new ArrayList<>();
        for (long cursor = range.startTime; cursor <= range.endTime; cursor += HOUR_MILLIS) {
            StatisticsFlow statisticsFlow = flowMap.get(cursor);
            UserPackageFlowStatsDto.SeriesPointDto point = new UserPackageFlowStatsDto.SeriesPointDto();
            point.setHourTime(cursor);
            point.setTime(formatHour(cursor, RANGE_HOUR_FORMATTER));
            if (statisticsFlow == null) {
                point.setInFlow(0L);
                point.setOutFlow(0L);
                point.setFlow(0L);
            } else {
                point.setInFlow(defaultLong(statisticsFlow.getInFlow()));
                point.setOutFlow(defaultLong(statisticsFlow.getOutFlow()));
                point.setFlow(defaultLong(statisticsFlow.getFlow()));
            }
            result.add(point);
        }
        return result;
    }

    private List<UserPackageFlowStatsDto.ForwardFlowStatsDto> buildForwardStats(Long userId,
                                                                                NormalizedRange range,
                                                                                List<UserPackageDto.UserForwardDetailDto> forwards) {
        Map<Long, UserPackageDto.UserForwardDetailDto> forwardMap = new LinkedHashMap<>();
        for (UserPackageDto.UserForwardDetailDto forward : forwards) {
            forwardMap.put(forward.getId(), forward);
        }

        Map<Long, UserPackageFlowStatsDto.ForwardFlowStatsDto> aggregated = new LinkedHashMap<>();
        List<ForwardStatisticsFlow> flowList = forwardStatisticsFlowService.list(
                new QueryWrapper<ForwardStatisticsFlow>()
                        .eq("user_id", userId)
                        .between("hour_time", range.startTime, range.endTime)
        );

        for (ForwardStatisticsFlow statisticsFlow : flowList) {
            if (statisticsFlow.getForwardId() == null) {
                continue;
            }
            UserPackageDto.UserForwardDetailDto forward = forwardMap.get(statisticsFlow.getForwardId());
            if (forward == null) {
                continue;
            }
            UserPackageFlowStatsDto.ForwardFlowStatsDto dto = aggregated.computeIfAbsent(statisticsFlow.getForwardId(), key -> {
                UserPackageFlowStatsDto.ForwardFlowStatsDto item = new UserPackageFlowStatsDto.ForwardFlowStatsDto();
                item.setId(forward.getId());
                item.setName(forward.getName());
                item.setTunnelId(forward.getTunnelId());
                item.setTunnelName(forward.getTunnelName());
                item.setInAddress(forward.getInIp());
                item.setRemoteAddr(forward.getRemoteAddr());
                item.setInFlow(0L);
                item.setOutFlow(0L);
                item.setFlow(0L);
                return item;
            });
            dto.setInFlow(dto.getInFlow() + defaultLong(statisticsFlow.getInFlow()));
            dto.setOutFlow(dto.getOutFlow() + defaultLong(statisticsFlow.getOutFlow()));
            dto.setFlow(dto.getFlow() + defaultLong(statisticsFlow.getFlow()));
        }

        return aggregated.values().stream()
                .sorted(Comparator.comparing(UserPackageFlowStatsDto.ForwardFlowStatsDto::getFlow).reversed()
                        .thenComparing(UserPackageFlowStatsDto.ForwardFlowStatsDto::getId))
                .toList();
    }

    private NormalizedRange normalizeRange(long startTime, long endTime) {
        return new NormalizedRange(truncateToHour(startTime), truncateToHour(endTime));
    }

    private long truncateToHour(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.HOURS)
                .toInstant()
                .toEpochMilli();
    }

    private String formatHour(long timestamp, DateTimeFormatter formatter) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(formatter);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private static final class NormalizedRange {
        private final long startTime;
        private final long endTime;

        private NormalizedRange(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

}
