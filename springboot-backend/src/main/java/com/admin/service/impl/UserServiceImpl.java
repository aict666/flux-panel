package com.admin.service.impl;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.admin.common.context.ActorContext;
import com.admin.common.context.ActorContextHolder;
import com.admin.common.context.ActorType;
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
    private static final DateTimeFormatter RANGE_DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final int TOP_RULE_LIMIT = 12;

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
        Integer userId = getCurrentUserId();
        boolean adminScope = isAdminScope();
        User user = this.getById(userId);
        if (user == null) return R.err("用户不存在");

        List<UserPackageDto.UserTunnelDetailDto> tunnelPermissions = adminScope
                ? userMapper.getAllTunnelsForAdmin()
                : userMapper.getUserTunnelDetails(userId);
        List<UserPackageDto.UserForwardDetailDto> forwards = loadForwardDetails(adminScope, user.getId());
        fillForwardInIpAndPort(forwards);

        UserPackageDto packageDto = new UserPackageDto();
        packageDto.setDashboardMode(adminScope ? "admin" : "user");
        packageDto.setUserInfo(buildUserInfoDto(user));
        packageDto.setTunnelPermissions(tunnelPermissions);
        packageDto.setForwards(forwards);
        packageDto.setStatisticsFlows(adminScope ? new ArrayList<>() : getLast24HoursFlowStatistics(user.getId()));
        if (adminScope) {
            packageDto.setAdminOverview(buildAdminOverview());
        }
        return R.ok(packageDto);
    }

    @Override
    public R getUserPackageFlowStats(FlowStatsQueryDto flowStatsQueryDto) {
        Integer userId = getCurrentUserId();
        boolean adminScope = isAdminScope();
        User user = this.getById(userId);
        if (user == null) {
            return R.err("用户不存在");
        }

        String validationError = validateRange(flowStatsQueryDto.getStartTime(), flowStatsQueryDto.getEndTime());
        if (validationError != null) {
            return R.err(validationError);
        }

        GranularityType granularityType = parseGranularityType(flowStatsQueryDto.getGranularity());
        if (granularityType == null) {
            return R.err("粒度只支持 hour/day");
        }

        MetricType metricType = parseMetricType(flowStatsQueryDto.getMetric());
        if (metricType == null) {
            return R.err("统计指标只支持 flow/inFlow/outFlow");
        }

        NormalizedRange range = normalizeRange(flowStatsQueryDto.getStartTime(), flowStatsQueryDto.getEndTime());
        long currentHour = truncateToHour(System.currentTimeMillis());
        List<UserPackageDto.UserForwardDetailDto> forwards = loadForwardDetails(adminScope, user.getId());
        fillForwardInIpAndPort(forwards);

        Long scopedUserId = adminScope ? null : user.getId();
        List<StatisticsFlow> userBuckets = loadUserBucketsWithRealtime(scopedUserId, range, currentHour);
        List<ForwardStatisticsFlow> forwardBuckets = loadForwardBucketsWithRealtime(scopedUserId, range, forwards, currentHour);

        FlowStatsSeriesSupport.SeriesBuildResult trendResult = buildTrendSeries(userBuckets, range, granularityType);
        List<UserPackageFlowStatsDto.SeriesPointDto> trendSeries = trendResult.getSeries();
        UserPackageFlowStatsDto.SummaryDto summary = buildSummaryFromSeries(trendSeries);
        PeakPoint peakPoint = findPeakPoint(trendSeries, metricType);
        List<UserPackageFlowStatsDto.ForwardFlowStatsDto> forwardRanking = aggregateForwardStats(forwardBuckets, forwards, metricType);
        List<UserPackageFlowStatsDto.TopRuleSeriesDto> topRuleSeries = buildTopRuleSeries(
                forwardBuckets,
                forwardRanking,
                granularityType,
                trendSeries
        );

        UserPackageFlowStatsDto result = new UserPackageFlowStatsDto();

        UserPackageFlowStatsDto.RangeDto rangeDto = new UserPackageFlowStatsDto.RangeDto();
        rangeDto.setStartTime(range.startTime);
        rangeDto.setEndTime(range.endTime);

        result.setFilters(buildFilters(range, granularityType, metricType));
        result.setOverviewCards(buildOverviewCards(adminScope, user, forwards, summary, peakPoint));
        result.setRankings(buildRankings(adminScope, userBuckets, forwardBuckets, forwardRanking, forwards, metricType));
        result.setRange(rangeDto);
        result.setSummary(summary);
        result.setMeta(buildMeta(
                adminScope ? "global" : "self",
                "all",
                forwardRanking.size(),
                forwardRanking.size(),
                trendResult.hasSamplingGap(),
                granularityType,
                metricType,
                topRuleSeries.size(),
                peakPoint == null ? null : peakPoint.bucketTime()
        ));
        result.setDefaultHourTime(selectDefaultHourTime(trendSeries, range.endTime));
        result.setSeries(trendSeries);
        result.setForwardStats(forwardRanking);
        result.setTrendSeries(trendSeries);
        result.setTopRuleSeries(topRuleSeries);
        return R.ok(result);
    }

    @Override
    public R getUserPackageFlowHourDetail(FlowStatsHourDetailQueryDto flowStatsHourDetailQueryDto) {
        Integer userId = getCurrentUserId();
        boolean adminScope = isAdminScope();
        User user = this.getById(userId);
        if (user == null) {
            return R.err("用户不存在");
        }

        String validationError = validateRange(flowStatsHourDetailQueryDto.getStartTime(), flowStatsHourDetailQueryDto.getEndTime());
        if (validationError != null) {
            return R.err(validationError);
        }

        NormalizedRange range = normalizeRange(flowStatsHourDetailQueryDto.getStartTime(), flowStatsHourDetailQueryDto.getEndTime());
        long hourTime = truncateToHour(flowStatsHourDetailQueryDto.getHourTime());
        long currentHour = truncateToHour(System.currentTimeMillis());
        if (hourTime < range.startTime || hourTime > range.endTime) {
            return R.err("小时必须位于统计时间范围内");
        }

        List<UserPackageDto.UserForwardDetailDto> forwards = loadForwardDetails(adminScope, user.getId());
        fillForwardInIpAndPort(forwards);
        List<UserPackageFlowStatsDto.ForwardFlowStatsDto> rows = buildHourDetailRows(adminScope ? null : user.getId(), hourTime, forwards, currentHour);

        UserPackageFlowHourDetailDto result = new UserPackageFlowHourDetailDto();
        UserPackageFlowHourDetailDto.HourDto hourDto = new UserPackageFlowHourDetailDto.HourDto();
        hourDto.setHourTime(hourTime);
        hourDto.setTime(formatHour(hourTime, RANGE_HOUR_FORMATTER));
        result.setHour(hourDto);
        result.setSummary(buildSummaryFromRows(rows));
        result.setMeta(buildMeta(
                adminScope ? "global" : "self",
                null,
                rows.size(),
                rows.size(),
                hourTime != currentHour && !forwards.isEmpty() && rows.isEmpty(),
                GranularityType.HOUR,
                MetricType.FLOW,
                rows.size(),
                hourTime
        ));
        result.setRows(rows);
        return R.ok(result);
    }

    @Override
    public R updatePassword(ChangePasswordDto changePasswordDto) {
        Integer userId = getCurrentUserId();
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

    private UserPackageDto.AdminOverviewDto buildAdminOverview() {
        List<User> users = this.list(new QueryWrapper<User>().gt("id", 0));
        long totalInFlow = users.stream().mapToLong(item -> defaultLong(item.getInFlow())).sum();
        long totalOutFlow = users.stream().mapToLong(item -> defaultLong(item.getOutFlow())).sum();

        UserPackageDto.AdminOverviewDto adminOverview = new UserPackageDto.AdminOverviewDto();
        adminOverview.setUserCount((long) users.size());
        adminOverview.setTunnelCount((long) tunnelService.count(new QueryWrapper<Tunnel>().gt("id", 0)));
        adminOverview.setForwardCount((long) forwardService.count(new QueryWrapper<Forward>().gt("id", 0)));
        adminOverview.setTotalInFlow(totalInFlow);
        adminOverview.setTotalOutFlow(totalOutFlow);
        adminOverview.setTotalFlow(totalInFlow + totalOutFlow);
        return adminOverview;
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

    private List<StatisticsFlow> loadUserBucketsWithRealtime(Long userId, NormalizedRange range, long currentHour) {
        boolean includeRealtimeCurrentHour = rangeContainsHour(range, currentHour);
        List<StatisticsFlow> flowList = new ArrayList<>(loadHistoricalUserBuckets(
                userId,
                range.startTime,
                includeRealtimeCurrentHour ? currentHour - HOUR_MILLIS : range.endTime
        ));
        if (includeRealtimeCurrentHour) {
            flowList.addAll(buildRealtimeCurrentHourUserBuckets(userId, currentHour));
        }
        return flowList;
    }

    private List<ForwardStatisticsFlow> loadForwardBucketsWithRealtime(Long userId,
                                                                       NormalizedRange range,
                                                                       List<UserPackageDto.UserForwardDetailDto> forwards,
                                                                       long currentHour) {
        boolean includeRealtimeCurrentHour = rangeContainsHour(range, currentHour);
        List<ForwardStatisticsFlow> flowList = new ArrayList<>(loadHistoricalForwardBuckets(
                userId,
                range.startTime,
                includeRealtimeCurrentHour ? currentHour - HOUR_MILLIS : range.endTime
        ));
        if (includeRealtimeCurrentHour) {
            flowList.addAll(buildRealtimeCurrentHourForwardBuckets(userId, currentHour, forwards));
        }
        return flowList;
    }

    private FlowStatsSeriesSupport.SeriesBuildResult buildTrendSeries(List<StatisticsFlow> flowList,
                                                                      NormalizedRange range,
                                                                      GranularityType granularityType) {
        FlowStatsSeriesSupport.SeriesBuildResult hourlySeries = FlowStatsSeriesSupport.buildHistoricalSeries(
                range.startTime,
                range.endTime,
                flowList,
                hourTime -> formatBucketTime(hourTime, GranularityType.HOUR)
        );
        if (granularityType == GranularityType.HOUR) {
            return hourlySeries;
        }
        return FlowStatsSeriesSupport.aggregateSeriesByDay(
                hourlySeries.getSeries(),
                bucketTime -> formatBucketTime(bucketTime, GranularityType.DAY)
        );
    }

    private List<UserPackageFlowStatsDto.ForwardFlowStatsDto> buildHourDetailRows(Long userId,
                                                                                  long hourTime,
                                                                                  List<UserPackageDto.UserForwardDetailDto> forwards,
                                                                                  long currentHour) {
        if (hourTime == currentHour) {
            return aggregateForwardStats(buildRealtimeCurrentHourForwardBuckets(userId, currentHour, forwards), forwards);
        }
        QueryWrapper<ForwardStatisticsFlow> queryWrapper = new QueryWrapper<ForwardStatisticsFlow>()
                .eq("hour_time", hourTime);
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        return aggregateForwardStats(forwardStatisticsFlowService.list(queryWrapper), forwards);
    }

    private List<StatisticsFlow> loadHistoricalUserBuckets(Long userId, long startTime, long endTime) {
        if (endTime < startTime) {
            return new ArrayList<>();
        }
        QueryWrapper<StatisticsFlow> queryWrapper = new QueryWrapper<StatisticsFlow>()
                .between("hour_time", startTime, endTime)
                .orderByAsc("hour_time");
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        return statisticsFlowService.list(queryWrapper);
    }

    private List<ForwardStatisticsFlow> loadHistoricalForwardBuckets(Long userId, long startTime, long endTime) {
        if (endTime < startTime) {
            return new ArrayList<>();
        }
        QueryWrapper<ForwardStatisticsFlow> queryWrapper = new QueryWrapper<ForwardStatisticsFlow>()
                .between("hour_time", startTime, endTime);
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        return forwardStatisticsFlowService.list(queryWrapper);
    }

    private List<StatisticsFlow> buildRealtimeCurrentHourUserBuckets(Long userId, long hourTime) {
        List<User> users = loadRealtimeUsers(userId);
        Map<Long, StatisticsFlow> latestBuckets = loadLatestUserBucketsBefore(hourTime, users);

        List<StatisticsFlow> realtimeBuckets = new ArrayList<>();
        for (User currentUser : users) {
            StatisticsFlow latestBucket = latestBuckets.get(currentUser.getId());
            long totalInFlow = defaultLong(currentUser.getInFlow());
            long totalOutFlow = defaultLong(currentUser.getOutFlow());
            long realtimeInFlow = calculateIncrement(totalInFlow, latestBucket == null ? null : latestBucket.getTotalInFlow());
            long realtimeOutFlow = calculateIncrement(totalOutFlow, latestBucket == null ? null : latestBucket.getTotalOutFlow());

            StatisticsFlow bucket = new StatisticsFlow();
            bucket.setUserId(currentUser.getId());
            bucket.setHourTime(hourTime);
            bucket.setTime(formatBucketTime(hourTime, GranularityType.HOUR));
            bucket.setInFlow(realtimeInFlow);
            bucket.setOutFlow(realtimeOutFlow);
            bucket.setFlow(realtimeInFlow + realtimeOutFlow);
            bucket.setTotalInFlow(totalInFlow);
            bucket.setTotalOutFlow(totalOutFlow);
            bucket.setTotalFlow(totalInFlow + totalOutFlow);
            bucket.setCreatedTime(hourTime);
            realtimeBuckets.add(bucket);
        }
        return realtimeBuckets;
    }

    private List<User> loadRealtimeUsers(Long userId) {
        if (userId != null) {
            User currentUser = this.getById(userId);
            if (currentUser == null) {
                return new ArrayList<>();
            }
            return List.of(currentUser);
        }
        return this.list(new QueryWrapper<User>().gt("id", 0));
    }

    private Map<Long, StatisticsFlow> loadLatestUserBucketsBefore(long hourTime, List<User> users) {
        List<Long> userIds = users.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<StatisticsFlow> historicalBuckets = statisticsFlowService.list(
                new QueryWrapper<StatisticsFlow>()
                        .lt("hour_time", hourTime)
                        .in("user_id", userIds)
                        .orderByDesc("hour_time")
        );
        Map<Long, StatisticsFlow> latestBuckets = new LinkedHashMap<>();
        for (StatisticsFlow bucket : historicalBuckets) {
            if (bucket.getUserId() != null && !latestBuckets.containsKey(bucket.getUserId())) {
                latestBuckets.put(bucket.getUserId(), bucket);
            }
        }
        return latestBuckets;
    }

    private List<ForwardStatisticsFlow> buildRealtimeCurrentHourForwardBuckets(Long userId,
                                                                               long hourTime,
                                                                               List<UserPackageDto.UserForwardDetailDto> forwards) {
        if (forwards.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> forwardIds = forwards.stream()
                .map(UserPackageDto.UserForwardDetailDto::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Forward> currentForwardMap = loadCurrentForwardMap(forwardIds);
        Map<Long, ForwardStatisticsFlow> latestBuckets = loadLatestForwardBucketsBefore(hourTime, forwardIds);

        List<ForwardStatisticsFlow> realtimeBuckets = new ArrayList<>();
        for (UserPackageDto.UserForwardDetailDto forwardDetail : forwards) {
            Long forwardId = forwardDetail.getId();
            if (forwardId == null) {
                continue;
            }
            Forward currentForward = currentForwardMap.get(forwardId);
            ForwardStatisticsFlow latestBucket = latestBuckets.get(forwardId);

            long currentInFlow = currentForward == null ? 0L : defaultLong(currentForward.getInFlow());
            long currentOutFlow = currentForward == null ? 0L : defaultLong(currentForward.getOutFlow());
            long realtimeInFlow = calculateIncrement(currentInFlow, latestBucket == null ? null : latestBucket.getTotalInFlow());
            long realtimeOutFlow = calculateIncrement(currentOutFlow, latestBucket == null ? null : latestBucket.getTotalOutFlow());

            ForwardStatisticsFlow bucket = new ForwardStatisticsFlow();
            bucket.setUserId(currentForward != null && currentForward.getUserId() != null
                    ? currentForward.getUserId().longValue()
                    : (userId == null ? null : userId));
            bucket.setForwardId(forwardId);
            bucket.setForwardName(forwardDetail.getName());
            bucket.setTunnelId(forwardDetail.getTunnelId() == null ? null : forwardDetail.getTunnelId().longValue());
            bucket.setTunnelName(forwardDetail.getTunnelName());
            bucket.setHourTime(hourTime);
            bucket.setTime(formatHour(hourTime, RANGE_HOUR_FORMATTER));
            bucket.setInFlow(realtimeInFlow);
            bucket.setOutFlow(realtimeOutFlow);
            bucket.setFlow(realtimeInFlow + realtimeOutFlow);
            bucket.setTotalInFlow(currentInFlow);
            bucket.setTotalOutFlow(currentOutFlow);
            bucket.setTotalFlow(currentInFlow + currentOutFlow);
            bucket.setCreatedTime(hourTime);
            realtimeBuckets.add(bucket);
        }
        return realtimeBuckets;
    }

    private Map<Long, Forward> loadCurrentForwardMap(List<Long> forwardIds) {
        if (forwardIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<Forward> currentForwards = forwardService.list(new QueryWrapper<Forward>().in("id", forwardIds));
        Map<Long, Forward> forwardMap = new LinkedHashMap<>();
        for (Forward currentForward : currentForwards) {
            if (currentForward.getId() != null) {
                forwardMap.put(currentForward.getId(), currentForward);
            }
        }
        return forwardMap;
    }

    private Map<Long, ForwardStatisticsFlow> loadLatestForwardBucketsBefore(long hourTime, List<Long> forwardIds) {
        if (forwardIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<ForwardStatisticsFlow> historicalBuckets = forwardStatisticsFlowService.list(
                new QueryWrapper<ForwardStatisticsFlow>()
                        .lt("hour_time", hourTime)
                        .in("forward_id", forwardIds)
                        .orderByDesc("hour_time")
        );
        Map<Long, ForwardStatisticsFlow> latestBuckets = new LinkedHashMap<>();
        for (ForwardStatisticsFlow bucket : historicalBuckets) {
            if (bucket.getForwardId() != null && !latestBuckets.containsKey(bucket.getForwardId())) {
                latestBuckets.put(bucket.getForwardId(), bucket);
            }
        }
        return latestBuckets;
    }

    private List<UserPackageFlowStatsDto.ForwardFlowStatsDto> aggregateForwardStats(List<ForwardStatisticsFlow> flowList,
                                                                                    List<UserPackageDto.UserForwardDetailDto> forwards) {
        return aggregateForwardStats(flowList, forwards, MetricType.FLOW);
    }

    private List<UserPackageFlowStatsDto.ForwardFlowStatsDto> aggregateForwardStats(List<ForwardStatisticsFlow> flowList,
                                                                                    List<UserPackageDto.UserForwardDetailDto> forwards,
                                                                                    MetricType metricType) {
        Map<Long, UserPackageDto.UserForwardDetailDto> forwardMap = new LinkedHashMap<>();
        for (UserPackageDto.UserForwardDetailDto forward : forwards) {
            forwardMap.put(forward.getId(), forward);
        }

        Map<Long, UserPackageFlowStatsDto.ForwardFlowStatsDto> aggregated = new LinkedHashMap<>();
        for (ForwardStatisticsFlow statisticsFlow : flowList) {
            if (statisticsFlow.getForwardId() == null) {
                continue;
            }
            UserPackageDto.UserForwardDetailDto forward = forwardMap.get(statisticsFlow.getForwardId());
            if (forward == null) {
                continue;
            }
            UserPackageFlowStatsDto.ForwardFlowStatsDto dto = aggregated.computeIfAbsent(statisticsFlow.getForwardId(), key -> createForwardStatsRow(forward));
            dto.setInFlow(dto.getInFlow() + defaultLong(statisticsFlow.getInFlow()));
            dto.setOutFlow(dto.getOutFlow() + defaultLong(statisticsFlow.getOutFlow()));
            dto.setFlow(dto.getFlow() + defaultLong(statisticsFlow.getFlow()));
        }

        return aggregated.values().stream()
                .sorted((left, right) -> compareMetric(metricType, left.getInFlow(), left.getOutFlow(), left.getFlow(), right.getInFlow(), right.getOutFlow(), right.getFlow(), left.getId(), right.getId()))
                .toList();
    }

    private UserPackageFlowStatsDto.ForwardFlowStatsDto createForwardStatsRow(UserPackageDto.UserForwardDetailDto forward) {
        UserPackageFlowStatsDto.ForwardFlowStatsDto item = new UserPackageFlowStatsDto.ForwardFlowStatsDto();
        item.setId(forward.getId());
        item.setName(forward.getName());
        item.setUserName(forward.getUserName());
        item.setTunnelId(forward.getTunnelId());
        item.setTunnelName(forward.getTunnelName());
        item.setInAddress(forward.getInIp());
        item.setRemoteAddr(forward.getRemoteAddr());
        item.setInFlow(0L);
        item.setOutFlow(0L);
        item.setFlow(0L);
        return item;
    }

    private UserPackageFlowStatsDto.SummaryDto buildSummaryFromSeries(List<UserPackageFlowStatsDto.SeriesPointDto> series) {
        return FlowStatsSeriesSupport.buildSummary(series);
    }

    private UserPackageFlowStatsDto.SummaryDto buildSummaryFromRows(List<UserPackageFlowStatsDto.ForwardFlowStatsDto> rows) {
        UserPackageFlowStatsDto.SummaryDto summary = new UserPackageFlowStatsDto.SummaryDto();
        long totalInFlow = rows.stream().mapToLong(item -> defaultLong(item.getInFlow())).sum();
        long totalOutFlow = rows.stream().mapToLong(item -> defaultLong(item.getOutFlow())).sum();
        summary.setTotalInFlow(totalInFlow);
        summary.setTotalOutFlow(totalOutFlow);
        summary.setTotalFlow(totalInFlow + totalOutFlow);
        return summary;
    }

    private UserPackageFlowStatsDto.FiltersDto buildFilters(NormalizedRange range,
                                                            GranularityType granularityType,
                                                            MetricType metricType) {
        UserPackageFlowStatsDto.FiltersDto filters = new UserPackageFlowStatsDto.FiltersDto();
        filters.setStartTime(range.startTime);
        filters.setEndTime(range.endTime);
        filters.setGranularity(granularityType.value);
        filters.setMetric(metricType.value);
        return filters;
    }

    private UserPackageFlowStatsDto.OverviewCardsDto buildOverviewCards(boolean adminScope,
                                                                       User user,
                                                                       List<UserPackageDto.UserForwardDetailDto> forwards,
                                                                       UserPackageFlowStatsDto.SummaryDto summary,
                                                                       PeakPoint peakPoint) {
        UserPackageFlowStatsDto.OverviewCardsDto overviewCards = new UserPackageFlowStatsDto.OverviewCardsDto();
        overviewCards.setTotalInFlow(summary.getTotalInFlow());
        overviewCards.setTotalOutFlow(summary.getTotalOutFlow());
        overviewCards.setTotalFlow(summary.getTotalFlow());
        overviewCards.setPeakBucketTime(peakPoint == null ? null : peakPoint.bucketTime());
        overviewCards.setPeakBucketValue(peakPoint == null ? 0L : peakPoint.value());

        if (adminScope) {
            overviewCards.setUserCount((long) this.count(new QueryWrapper<User>().gt("id", 0)));
            overviewCards.setTunnelCount((long) tunnelService.count(new QueryWrapper<Tunnel>().gt("id", 0)));
            overviewCards.setForwardCount((long) forwardService.count(new QueryWrapper<Forward>().gt("id", 0)));
            return overviewCards;
        }

        overviewCards.setFlowLimit(user.getFlow());
        overviewCards.setUsedFlow(summary.getTotalFlow());
        overviewCards.setForwardLimit(user.getNum());
        overviewCards.setUsedForwardCount(forwards.size());
        return overviewCards;
    }

    private UserPackageFlowStatsDto.RankingsDto buildRankings(boolean adminScope,
                                                              List<StatisticsFlow> userBuckets,
                                                              List<ForwardStatisticsFlow> forwardBuckets,
                                                              List<UserPackageFlowStatsDto.ForwardFlowStatsDto> forwardRanking,
                                                              List<UserPackageDto.UserForwardDetailDto> forwards,
                                                              MetricType metricType) {
        UserPackageFlowStatsDto.RankingsDto rankings = new UserPackageFlowStatsDto.RankingsDto();
        rankings.setRightTitle(adminScope ? "转发流量榜" : "我的转发流量榜");
        rankings.setRight(buildForwardRankingItems(forwardRanking, metricType));

        if (adminScope) {
            rankings.setLeftTitle("用户流量排行榜");
            rankings.setLeft(buildUserRankingItems(userBuckets, metricType));
            return rankings;
        }

        rankings.setLeftTitle("隧道流量榜");
        rankings.setLeft(buildTunnelRankingItems(forwardBuckets, metricType));
        return rankings;
    }

    private List<UserPackageFlowStatsDto.RankingItemDto> buildUserRankingItems(List<StatisticsFlow> userBuckets,
                                                                               MetricType metricType) {
        Map<Long, String> userNames = this.list(new QueryWrapper<User>().gt("id", 0)).stream()
                .filter(item -> item.getId() != null)
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item.getUser()), LinkedHashMap::putAll);

        Map<Long, UserPackageFlowStatsDto.RankingItemDto> rankingMap = new LinkedHashMap<>();
        for (StatisticsFlow bucket : userBuckets) {
            if (bucket.getUserId() == null) {
                continue;
            }
            UserPackageFlowStatsDto.RankingItemDto item = rankingMap.computeIfAbsent(bucket.getUserId(), userId -> {
                UserPackageFlowStatsDto.RankingItemDto rankingItem = new UserPackageFlowStatsDto.RankingItemDto();
                rankingItem.setId(userId);
                rankingItem.setName(userNames.getOrDefault(userId, "用户#" + userId));
                rankingItem.setInFlow(0L);
                rankingItem.setOutFlow(0L);
                rankingItem.setFlow(0L);
                return rankingItem;
            });
            item.setInFlow(item.getInFlow() + defaultLong(bucket.getInFlow()));
            item.setOutFlow(item.getOutFlow() + defaultLong(bucket.getOutFlow()));
            item.setFlow(item.getFlow() + defaultLong(bucket.getFlow()));
        }

        return rankingMap.values().stream()
                .filter(item -> metricType.valueOf(item.getInFlow(), item.getOutFlow(), item.getFlow()) > 0)
                .sorted((left, right) -> compareMetric(metricType, left.getInFlow(), left.getOutFlow(), left.getFlow(), right.getInFlow(), right.getOutFlow(), right.getFlow(), left.getId(), right.getId()))
                .toList();
    }

    private List<UserPackageFlowStatsDto.RankingItemDto> buildTunnelRankingItems(List<ForwardStatisticsFlow> forwardBuckets,
                                                                                 MetricType metricType) {
        Map<Long, UserPackageFlowStatsDto.RankingItemDto> rankingMap = new LinkedHashMap<>();
        for (ForwardStatisticsFlow bucket : forwardBuckets) {
            if (bucket.getTunnelId() == null) {
                continue;
            }
            UserPackageFlowStatsDto.RankingItemDto item = rankingMap.computeIfAbsent(bucket.getTunnelId(), tunnelId -> {
                UserPackageFlowStatsDto.RankingItemDto rankingItem = new UserPackageFlowStatsDto.RankingItemDto();
                rankingItem.setId(tunnelId);
                rankingItem.setName(bucket.getTunnelName() == null ? "未命名隧道" : bucket.getTunnelName());
                rankingItem.setInFlow(0L);
                rankingItem.setOutFlow(0L);
                rankingItem.setFlow(0L);
                return rankingItem;
            });
            item.setInFlow(item.getInFlow() + defaultLong(bucket.getInFlow()));
            item.setOutFlow(item.getOutFlow() + defaultLong(bucket.getOutFlow()));
            item.setFlow(item.getFlow() + defaultLong(bucket.getFlow()));
        }

        return rankingMap.values().stream()
                .filter(item -> metricType.valueOf(item.getInFlow(), item.getOutFlow(), item.getFlow()) > 0)
                .sorted((left, right) -> compareMetric(metricType, left.getInFlow(), left.getOutFlow(), left.getFlow(), right.getInFlow(), right.getOutFlow(), right.getFlow(), left.getId(), right.getId()))
                .toList();
    }

    private List<UserPackageFlowStatsDto.RankingItemDto> buildForwardRankingItems(List<UserPackageFlowStatsDto.ForwardFlowStatsDto> forwardRanking,
                                                                                  MetricType metricType) {
        return forwardRanking.stream()
                .filter(item -> metricType.valueOf(item.getInFlow(), item.getOutFlow(), item.getFlow()) > 0)
                .map(item -> {
                    UserPackageFlowStatsDto.RankingItemDto rankingItem = new UserPackageFlowStatsDto.RankingItemDto();
                    rankingItem.setId(item.getId());
                    rankingItem.setName(item.getName());
                    rankingItem.setSecondaryName(StringUtils.isNotBlank(item.getUserName()) ? item.getUserName() : item.getTunnelName());
                    rankingItem.setInFlow(item.getInFlow());
                    rankingItem.setOutFlow(item.getOutFlow());
                    rankingItem.setFlow(item.getFlow());
                    return rankingItem;
                })
                .toList();
    }

    private List<UserPackageFlowStatsDto.TopRuleSeriesDto> buildTopRuleSeries(List<ForwardStatisticsFlow> forwardBuckets,
                                                                              List<UserPackageFlowStatsDto.ForwardFlowStatsDto> forwardRanking,
                                                                              GranularityType granularityType,
                                                                              List<UserPackageFlowStatsDto.SeriesPointDto> trendSeries) {
        Map<Long, List<ForwardStatisticsFlow>> bucketMap = new LinkedHashMap<>();
        for (ForwardStatisticsFlow bucket : forwardBuckets) {
            if (bucket.getForwardId() == null) {
                continue;
            }
            bucketMap.computeIfAbsent(bucket.getForwardId(), ignored -> new ArrayList<>()).add(bucket);
        }

        List<UserPackageFlowStatsDto.TopRuleSeriesDto> result = new ArrayList<>();
        for (UserPackageFlowStatsDto.ForwardFlowStatsDto forward : forwardRanking.stream().limit(TOP_RULE_LIMIT).toList()) {
            UserPackageFlowStatsDto.TopRuleSeriesDto seriesDto = new UserPackageFlowStatsDto.TopRuleSeriesDto();
            seriesDto.setId(forward.getId());
            seriesDto.setName(forward.getName());
            seriesDto.setUserName(forward.getUserName());
            seriesDto.setTotalInFlow(forward.getInFlow());
            seriesDto.setTotalOutFlow(forward.getOutFlow());
            seriesDto.setTotalFlow(forward.getFlow());

            Map<Long, UserPackageFlowStatsDto.SeriesPointDto> pointMap = aggregateForwardSeriesByGranularity(
                    bucketMap.getOrDefault(forward.getId(), new ArrayList<>()),
                    granularityType
            );
            for (UserPackageFlowStatsDto.SeriesPointDto timelinePoint : trendSeries) {
                long bucketTime = resolveSeriesTime(timelinePoint);
                if (Boolean.FALSE.equals(timelinePoint.getSampled())) {
                    seriesDto.getSeries().add(createGapSeriesPoint(bucketTime, timelinePoint.getTime()));
                    continue;
                }

                UserPackageFlowStatsDto.SeriesPointDto point = pointMap.get(bucketTime);
                if (point == null) {
                    point = FlowStatsSeriesSupport.createSampledPoint(bucketTime, timelinePoint.getTime());
                } else {
                    point.setTime(timelinePoint.getTime());
                }
                seriesDto.getSeries().add(point);
            }
            result.add(seriesDto);
        }
        return result;
    }

    private Map<Long, UserPackageFlowStatsDto.SeriesPointDto> aggregateForwardSeriesByGranularity(List<ForwardStatisticsFlow> forwardBuckets,
                                                                                                  GranularityType granularityType) {
        Map<Long, UserPackageFlowStatsDto.SeriesPointDto> pointMap = new LinkedHashMap<>();
        for (ForwardStatisticsFlow bucket : forwardBuckets) {
            if (bucket.getHourTime() == null) {
                continue;
            }
            long bucketTime = granularityType == GranularityType.DAY ? truncateToDay(bucket.getHourTime()) : bucket.getHourTime();
            UserPackageFlowStatsDto.SeriesPointDto point = pointMap.computeIfAbsent(
                    bucketTime,
                    ignored -> FlowStatsSeriesSupport.createSampledPoint(bucketTime, formatBucketTime(bucketTime, granularityType))
            );
            point.setInFlow(point.getInFlow() + defaultLong(bucket.getInFlow()));
            point.setOutFlow(point.getOutFlow() + defaultLong(bucket.getOutFlow()));
            point.setFlow(point.getFlow() + defaultLong(bucket.getFlow()));
        }
        return pointMap;
    }

    private UserPackageFlowStatsDto.SeriesPointDto createGapSeriesPoint(long bucketTime, String time) {
        UserPackageFlowStatsDto.SeriesPointDto point = new UserPackageFlowStatsDto.SeriesPointDto();
        point.setBucketTime(bucketTime);
        point.setHourTime(bucketTime);
        point.setTime(time);
        point.setSampled(false);
        point.setInFlow(null);
        point.setOutFlow(null);
        point.setFlow(null);
        return point;
    }

    private UserPackageFlowStatsDto.MetaDto buildMeta(String scope,
                                                      String rankingMode,
                                                      int totalRuleCount,
                                                      int returnedRuleCount,
                                                      boolean hasSamplingGap,
                                                      GranularityType granularityType,
                                                      MetricType metricType,
                                                      int topRuleCount,
                                                      Long peakBucketTime) {
        UserPackageFlowStatsDto.MetaDto meta = new UserPackageFlowStatsDto.MetaDto();
        meta.setScope(scope);
        meta.setRankingMode(rankingMode);
        meta.setTotalRuleCount(totalRuleCount);
        meta.setReturnedRuleCount(returnedRuleCount);
        meta.setHasSamplingGap(hasSamplingGap);
        meta.setGranularity(granularityType.value);
        meta.setMetric(metricType.value);
        meta.setTopRuleCount(topRuleCount);
        meta.setPeakBucketTime(peakBucketTime);
        return meta;
    }

    private long selectDefaultHourTime(List<UserPackageFlowStatsDto.SeriesPointDto> series, long fallbackHourTime) {
        for (int index = series.size() - 1; index >= 0; index--) {
            UserPackageFlowStatsDto.SeriesPointDto point = series.get(index);
            if (defaultLong(point.getFlow()) > 0 && resolveSeriesTime(point) > 0) {
                return resolveSeriesTime(point);
            }
        }
        return fallbackHourTime;
    }

    private List<UserPackageDto.UserForwardDetailDto> loadForwardDetails(boolean adminScope, Long userId) {
        return adminScope
                ? userMapper.getAllForwardDetailsForAdmin()
                : userMapper.getUserForwardDetails(userId.intValue());
    }

    private boolean isAdminScope() {
        ActorContext actorContext = ActorContextHolder.get();
        if (actorContext != null) {
            return actorContext.getActorType() == ActorType.USER && Objects.equals(actorContext.getRoleId(), 0);
        }
        return Objects.equals(JwtUtil.getRoleIdFromToken(), 0);
    }

    private Integer getCurrentUserId() {
        ActorContext actorContext = ActorContextHolder.get();
        if (actorContext != null && actorContext.getActorType() == ActorType.USER) {
            return actorContext.getUserId();
        }
        return JwtUtil.getUserIdFromToken();
    }

    private boolean rangeContainsHour(NormalizedRange range, long hourTime) {
        return range.startTime <= hourTime && range.endTime >= hourTime;
    }

    private String validateRange(Long startTime, Long endTime) {
        if (endTime <= startTime) {
            return "结束时间必须大于开始时间";
        }
        if (endTime - startTime > MAX_FLOW_STATS_RANGE_MILLIS) {
            return "统计时间范围不能超过30天";
        }
        return null;
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

    private long calculateIncrement(long currentValue, Long previousTotal) {
        if (previousTotal == null) {
            return currentValue;
        }
        long increment = currentValue - previousTotal;
        return increment < 0 ? currentValue : increment;
    }

    private PeakPoint findPeakPoint(List<UserPackageFlowStatsDto.SeriesPointDto> series, MetricType metricType) {
        PeakPoint peakPoint = null;
        for (UserPackageFlowStatsDto.SeriesPointDto point : series) {
            if (Boolean.FALSE.equals(point.getSampled())) {
                continue;
            }
            long value = metricType.valueOf(point.getInFlow(), point.getOutFlow(), point.getFlow());
            if (value <= 0) {
                continue;
            }
            if (peakPoint == null || value > peakPoint.value()) {
                peakPoint = new PeakPoint(resolveSeriesTime(point), value);
            }
        }
        return peakPoint;
    }

    private int compareMetric(MetricType metricType,
                              Long leftInFlow,
                              Long leftOutFlow,
                              Long leftFlow,
                              Long rightInFlow,
                              Long rightOutFlow,
                              Long rightFlow,
                              Long leftId,
                              Long rightId) {
        int metricCompare = Long.compare(
                metricType.valueOf(rightInFlow, rightOutFlow, rightFlow),
                metricType.valueOf(leftInFlow, leftOutFlow, leftFlow)
        );
        if (metricCompare != 0) {
            return metricCompare;
        }
        return Long.compare(defaultLong(leftId), defaultLong(rightId));
    }

    private long resolveSeriesTime(UserPackageFlowStatsDto.SeriesPointDto point) {
        if (point.getBucketTime() != null) {
            return point.getBucketTime();
        }
        return defaultLong(point.getHourTime());
    }

    private String formatBucketTime(long timestamp, GranularityType granularityType) {
        return formatHour(timestamp, granularityType == GranularityType.DAY ? RANGE_DAY_FORMATTER : RANGE_HOUR_FORMATTER);
    }

    private GranularityType parseGranularityType(String granularity) {
        for (GranularityType value : GranularityType.values()) {
            if (value.value.equals(granularity)) {
                return value;
            }
        }
        return null;
    }

    private MetricType parseMetricType(String metric) {
        for (MetricType value : MetricType.values()) {
            if (value.value.equals(metric)) {
                return value;
            }
        }
        return null;
    }

    private long truncateToDay(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
                .toEpochMilli();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private record PeakPoint(long bucketTime, long value) {
    }

    private enum GranularityType {
        HOUR("hour"),
        DAY("day");

        private final String value;

        GranularityType(String value) {
            this.value = value;
        }
    }

    private enum MetricType {
        FLOW("flow"),
        IN_FLOW("inFlow"),
        OUT_FLOW("outFlow");

        private final String value;

        MetricType(String value) {
            this.value = value;
        }

        private long valueOf(Long inFlow, Long outFlow, Long flow) {
            if (this == IN_FLOW) {
                return inFlow == null ? 0L : inFlow;
            }
            if (this == OUT_FLOW) {
                return outFlow == null ? 0L : outFlow;
            }
            return flow == null ? 0L : flow;
        }
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
