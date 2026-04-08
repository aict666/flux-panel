package com.admin.service.impl;

import com.admin.common.dto.FlowStatsHourDetailQueryDto;
import com.admin.common.dto.FlowStatsQueryDto;
import com.admin.common.dto.UserPackageDto;
import com.admin.common.dto.UserPackageFlowHourDetailDto;
import com.admin.common.dto.UserPackageFlowStatsDto;
import com.admin.common.lang.R;
import com.admin.entity.Forward;
import com.admin.entity.ForwardPort;
import com.admin.entity.ForwardStatisticsFlow;
import com.admin.entity.StatisticsFlow;
import com.admin.entity.Tunnel;
import com.admin.entity.User;
import com.admin.service.ForwardPortService;
import com.admin.service.ForwardService;
import com.admin.service.ForwardStatisticsFlowService;
import com.admin.service.StatisticsFlowService;
import com.admin.service.TunnelService;
import com.admin.service.UserService;
import com.admin.support.PostgresIntegrationTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class UserServiceFlowStatsTest extends PostgresIntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private TunnelService tunnelService;

    @Autowired
    private ForwardService forwardService;

    @Autowired
    private ForwardPortService forwardPortService;

    @Autowired
    private StatisticsFlowService statisticsFlowService;

    @Autowired
    private ForwardStatisticsFlowService forwardStatisticsFlowService;

    @BeforeEach
    void setUp() {
        forwardStatisticsFlowService.remove(new QueryWrapper<ForwardStatisticsFlow>().gt("id", 0));
        statisticsFlowService.remove(new QueryWrapper<StatisticsFlow>().gt("id", 0));
        forwardPortService.remove(new QueryWrapper<ForwardPort>().gt("id", 0));
        forwardService.remove(new QueryWrapper<Forward>().gt("id", 0));
        tunnelService.remove(new QueryWrapper<Tunnel>().gt("id", 0));
        userService.remove(new QueryWrapper<User>().gt("id", 1));
    }

    @Test
    void shouldReturnUserFlowStatsSummaryDefaultHourAndTopTenRules() {
        long now = System.currentTimeMillis();
        User user = saveUser("query-user", 1, now, 670L, 142L);
        Tunnel tunnel = saveTunnel("query-tunnel", now, "1.2.3.4");

        long startHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(2)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long middleHour = startHour + ChronoUnit.HOURS.getDuration().toMillis();
        long endHour = middleHour + ChronoUnit.HOURS.getDuration().toMillis();

        statisticsFlowService.save(buildUserBucket(user.getId(), startHour, 660L, 132L, 792L));
        statisticsFlowService.save(buildUserBucket(user.getId(), endHour, 10L, 0L, 10L));

        for (int index = 1; index <= 11; index++) {
            Forward forward = saveForward(user, tunnel, now + index, "query-forward-" + index, "8.8.8." + index + ":53", 18_000 + index);
            long startIn = index * 10L;
            long startOut = index * 2L;
            forwardStatisticsFlowService.save(buildForwardBucket(
                    user.getId(),
                    forward.getId(),
                    forward.getName(),
                    tunnel.getId(),
                    tunnel.getName(),
                    startHour,
                    startIn,
                    startOut,
                    startIn + startOut
            ));
            if (index == 11) {
                forwardStatisticsFlowService.save(buildForwardBucket(
                        user.getId(),
                        forward.getId(),
                        forward.getName(),
                        tunnel.getId(),
                        tunnel.getName(),
                        endHour,
                        10L,
                        0L,
                        10L
                ));
            }
        }

        setUserRequest(user.getId().intValue(), 1, user.getUser());

        FlowStatsQueryDto queryDto = new FlowStatsQueryDto();
        queryDto.setStartTime(startHour);
        queryDto.setEndTime(endHour);

        R result = userService.getUserPackageFlowStats(queryDto);

        assertEquals(0, result.getCode());
        UserPackageFlowStatsDto data = assertInstanceOf(UserPackageFlowStatsDto.class, result.getData());
        assertEquals(startHour, data.getRange().getStartTime());
        assertEquals(endHour, data.getRange().getEndTime());
        assertEquals(3, data.getSeries().size());
        assertEquals(792L, data.getSeries().get(0).getFlow());
        assertEquals(0L, data.getSeries().get(1).getFlow());
        assertEquals(10L, data.getSeries().get(2).getFlow());
        assertEquals(670L, data.getSummary().getTotalInFlow());
        assertEquals(132L, data.getSummary().getTotalOutFlow());
        assertEquals(802L, data.getSummary().getTotalFlow());
        assertEquals("self", data.getMeta().getScope());
        assertEquals("top10", data.getMeta().getRankingMode());
        assertEquals(11, data.getMeta().getTotalRuleCount());
        assertEquals(10, data.getMeta().getReturnedRuleCount());
        assertEquals(endHour, data.getDefaultHourTime());
        assertEquals(10, data.getForwardStats().size());
        assertEquals("query-forward-11", data.getForwardStats().get(0).getName());
        assertEquals("1.2.3.4:18011", data.getForwardStats().get(0).getInAddress());
        assertEquals(24L, data.getForwardStats().get(9).getFlow());
    }

    @Test
    void shouldReturnHourDetailForSelectedHourWithoutTruncation() {
        long now = System.currentTimeMillis();
        User user = saveUser("hourly-user", 1, now, 0L, 0L);
        Tunnel tunnel = saveTunnel("hourly-tunnel", now, "2.2.2.2");

        long selectedHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        long totalIn = 0L;
        long totalOut = 0L;
        for (int index = 1; index <= 12; index++) {
            Forward forward = saveForward(user, tunnel, now + index, "hourly-forward-" + index, "9.9.9." + index + ":80", 28_000 + index);
            long inFlow = index * 3L;
            long outFlow = index;
            totalIn += inFlow;
            totalOut += outFlow;
            forwardStatisticsFlowService.save(buildForwardBucket(
                    user.getId(),
                    forward.getId(),
                    forward.getName(),
                    tunnel.getId(),
                    tunnel.getName(),
                    selectedHour,
                    inFlow,
                    outFlow,
                    inFlow + outFlow
            ));
        }

        setUserRequest(user.getId().intValue(), 1, user.getUser());

        FlowStatsHourDetailQueryDto queryDto = new FlowStatsHourDetailQueryDto();
        queryDto.setStartTime(selectedHour - ChronoUnit.HOURS.getDuration().toMillis());
        queryDto.setEndTime(selectedHour + ChronoUnit.HOURS.getDuration().toMillis());
        queryDto.setHourTime(selectedHour);

        R result = userService.getUserPackageFlowHourDetail(queryDto);

        assertEquals(0, result.getCode());
        UserPackageFlowHourDetailDto data = assertInstanceOf(UserPackageFlowHourDetailDto.class, result.getData());
        assertEquals(selectedHour, data.getHour().getHourTime());
        assertEquals(totalIn, data.getSummary().getTotalInFlow());
        assertEquals(totalOut, data.getSummary().getTotalOutFlow());
        assertEquals(totalIn + totalOut, data.getSummary().getTotalFlow());
        assertEquals("self", data.getMeta().getScope());
        assertEquals(12, data.getMeta().getTotalRuleCount());
        assertEquals(12, data.getMeta().getReturnedRuleCount());
        assertEquals(12, data.getRows().size());
        assertEquals("hourly-forward-12", data.getRows().get(0).getName());
        assertEquals("2.2.2.2:28012", data.getRows().get(0).getInAddress());
    }

    @Test
    void shouldIncludeRealtimeCurrentHourInUserFlowStatsAndHourDetail() {
        long now = System.currentTimeMillis();
        User user = saveUser("realtime-user", 1, now, 95L, 55L);
        Tunnel tunnel = saveTunnel("realtime-tunnel", now, "4.4.4.4");

        long currentHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long previousHour = currentHour - ChronoUnit.HOURS.getDuration().toMillis();

        statisticsFlowService.save(buildUserBucket(user.getId(), previousHour, 10L, 5L, 15L, 75L, 35L));

        Forward activeForward = saveForward(user, tunnel, now + 1, "realtime-forward-active", "8.8.4.4:80", 48_001);
        activeForward.setInFlow(90L);
        activeForward.setOutFlow(50L);
        forwardService.updateById(activeForward);
        forwardStatisticsFlowService.save(buildForwardBucket(
                user.getId(),
                activeForward.getId(),
                activeForward.getName(),
                tunnel.getId(),
                tunnel.getName(),
                previousHour,
                6L,
                4L,
                10L,
                70L,
                30L
        ));

        Forward idleForward = saveForward(user, tunnel, now + 2, "realtime-forward-idle", "8.8.4.5:81", 48_002);
        idleForward.setInFlow(5L);
        idleForward.setOutFlow(5L);
        forwardService.updateById(idleForward);
        forwardStatisticsFlowService.save(buildForwardBucket(
                user.getId(),
                idleForward.getId(),
                idleForward.getName(),
                tunnel.getId(),
                tunnel.getName(),
                previousHour,
                2L,
                3L,
                5L,
                5L,
                5L
        ));

        setUserRequest(user.getId().intValue(), 1, user.getUser());

        FlowStatsQueryDto statsQuery = new FlowStatsQueryDto();
        statsQuery.setStartTime(previousHour);
        statsQuery.setEndTime(currentHour);

        R statsResult = userService.getUserPackageFlowStats(statsQuery);

        assertEquals(0, statsResult.getCode());
        UserPackageFlowStatsDto statsDto = assertInstanceOf(UserPackageFlowStatsDto.class, statsResult.getData());
        assertEquals(2, statsDto.getSeries().size());
        assertEquals(15L, statsDto.getSeries().get(0).getFlow());
        assertEquals(20L, statsDto.getSeries().get(1).getInFlow());
        assertEquals(20L, statsDto.getSeries().get(1).getOutFlow());
        assertEquals(40L, statsDto.getSeries().get(1).getFlow());
        assertEquals(30L, statsDto.getSummary().getTotalInFlow());
        assertEquals(25L, statsDto.getSummary().getTotalOutFlow());
        assertEquals(55L, statsDto.getSummary().getTotalFlow());
        assertEquals(currentHour, statsDto.getDefaultHourTime());
        assertEquals(List.of("realtime-forward-active", "realtime-forward-idle"),
                statsDto.getForwardStats().stream().map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getName).toList());
        assertEquals(List.of(50L, 5L),
                statsDto.getForwardStats().stream().map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getFlow).toList());

        FlowStatsHourDetailQueryDto detailQuery = new FlowStatsHourDetailQueryDto();
        detailQuery.setStartTime(previousHour);
        detailQuery.setEndTime(currentHour);
        detailQuery.setHourTime(currentHour);

        R detailResult = userService.getUserPackageFlowHourDetail(detailQuery);

        assertEquals(0, detailResult.getCode());
        UserPackageFlowHourDetailDto detailDto = assertInstanceOf(UserPackageFlowHourDetailDto.class, detailResult.getData());
        assertEquals(currentHour, detailDto.getHour().getHourTime());
        assertEquals(20L, detailDto.getSummary().getTotalInFlow());
        assertEquals(20L, detailDto.getSummary().getTotalOutFlow());
        assertEquals(40L, detailDto.getSummary().getTotalFlow());
        assertEquals(List.of("realtime-forward-active", "realtime-forward-idle"),
                detailDto.getRows().stream().map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getName).toList());
        assertEquals(List.of(40L, 0L),
                detailDto.getRows().stream().map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getFlow).toList());
    }

    @Test
    void shouldTreatRealtimeCountersAsResetWhenCurrentTotalsDropBelowSnapshot() {
        long now = System.currentTimeMillis();
        User user = saveUser("reset-user", 1, now, 7L, 3L);
        Tunnel tunnel = saveTunnel("reset-tunnel", now, "5.5.5.5");

        long currentHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long previousHour = currentHour - ChronoUnit.HOURS.getDuration().toMillis();

        statisticsFlowService.save(buildUserBucket(user.getId(), previousHour, 7L, 8L, 15L, 100L, 90L));

        Forward resetForward = saveForward(user, tunnel, now + 1, "reset-forward", "8.8.5.5:80", 58_001);
        resetForward.setInFlow(7L);
        resetForward.setOutFlow(3L);
        forwardService.updateById(resetForward);
        forwardStatisticsFlowService.save(buildForwardBucket(
                user.getId(),
                resetForward.getId(),
                resetForward.getName(),
                tunnel.getId(),
                tunnel.getName(),
                previousHour,
                5L,
                5L,
                10L,
                20L,
                30L
        ));

        setUserRequest(user.getId().intValue(), 1, user.getUser());

        FlowStatsQueryDto statsQuery = new FlowStatsQueryDto();
        statsQuery.setStartTime(previousHour);
        statsQuery.setEndTime(currentHour);

        R statsResult = userService.getUserPackageFlowStats(statsQuery);

        assertEquals(0, statsResult.getCode());
        UserPackageFlowStatsDto statsDto = assertInstanceOf(UserPackageFlowStatsDto.class, statsResult.getData());
        assertEquals(7L, statsDto.getSeries().get(1).getInFlow());
        assertEquals(3L, statsDto.getSeries().get(1).getOutFlow());
        assertEquals(10L, statsDto.getSeries().get(1).getFlow());
        assertEquals(currentHour, statsDto.getDefaultHourTime());

        FlowStatsHourDetailQueryDto detailQuery = new FlowStatsHourDetailQueryDto();
        detailQuery.setStartTime(previousHour);
        detailQuery.setEndTime(currentHour);
        detailQuery.setHourTime(currentHour);

        R detailResult = userService.getUserPackageFlowHourDetail(detailQuery);

        assertEquals(0, detailResult.getCode());
        UserPackageFlowHourDetailDto detailDto = assertInstanceOf(UserPackageFlowHourDetailDto.class, detailResult.getData());
        assertEquals(List.of(10L),
                detailDto.getRows().stream().map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getFlow).toList());
    }

    @Test
    void shouldReturnAdminGlobalDashboardAndFlowStats() {
        long now = System.currentTimeMillis();
        User admin = saveUser("global-admin", 0, now, 0L, 0L);
        User userOne = saveUser("global-user-1", 1, now, 300L, 100L);
        User userTwo = saveUser("global-user-2", 1, now, 200L, 80L);
        Tunnel tunnel = saveTunnel("global-tunnel", now, "3.3.3.3");

        Forward forwardOne = saveForward(userOne, tunnel, now + 1, "global-forward-a", "7.7.7.7:70", 38_001);
        Forward forwardTwo = saveForward(userTwo, tunnel, now + 2, "global-forward-b", "7.7.7.8:71", 38_002);

        long selectedHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        forwardStatisticsFlowService.save(buildForwardBucket(userOne.getId(), forwardOne.getId(), forwardOne.getName(), tunnel.getId(), tunnel.getName(), selectedHour, 30L, 10L, 40L));
        forwardStatisticsFlowService.save(buildForwardBucket(userTwo.getId(), forwardTwo.getId(), forwardTwo.getName(), tunnel.getId(), tunnel.getName(), selectedHour, 20L, 5L, 25L));

        setUserRequest(admin.getId().intValue(), 0, admin.getUser());

        R packageResult = userService.getUserPackageInfo();

        assertEquals(0, packageResult.getCode());
        UserPackageDto packageDto = assertInstanceOf(UserPackageDto.class, packageResult.getData());
        assertEquals("admin", packageDto.getDashboardMode());
        assertNotNull(packageDto.getAdminOverview());
        assertEquals(userService.count(new QueryWrapper<User>().gt("id", 0)), packageDto.getAdminOverview().getUserCount());
        assertEquals(tunnelService.count(new QueryWrapper<Tunnel>().gt("id", 0)), packageDto.getAdminOverview().getTunnelCount());
        assertEquals(forwardService.count(new QueryWrapper<Forward>().gt("id", 0)), packageDto.getAdminOverview().getForwardCount());
        assertEquals(
                userService.list(new QueryWrapper<User>().gt("id", 0)).stream().mapToLong(item -> (item.getInFlow() == null ? 0L : item.getInFlow()) + (item.getOutFlow() == null ? 0L : item.getOutFlow())).sum(),
                packageDto.getAdminOverview().getTotalFlow()
        );
        assertEquals(2, packageDto.getForwards().size());
        assertEquals(Set.of("global-user-1", "global-user-2"), packageDto.getForwards().stream().map(UserPackageDto.UserForwardDetailDto::getUserName).collect(Collectors.toSet()));

        FlowStatsQueryDto queryDto = new FlowStatsQueryDto();
        queryDto.setStartTime(selectedHour - ChronoUnit.HOURS.getDuration().toMillis());
        queryDto.setEndTime(selectedHour);
        R flowStatsResult = userService.getUserPackageFlowStats(queryDto);

        assertEquals(0, flowStatsResult.getCode());
        UserPackageFlowStatsDto statsDto = assertInstanceOf(UserPackageFlowStatsDto.class, flowStatsResult.getData());
        assertEquals("global", statsDto.getMeta().getScope());
        assertEquals("all", statsDto.getMeta().getRankingMode());
        assertEquals(2, statsDto.getMeta().getTotalRuleCount());
        assertEquals(2, statsDto.getMeta().getReturnedRuleCount());
        assertEquals(55L, statsDto.getSummary().getTotalFlow());
        assertEquals(List.of("global-user-1", "global-user-2"), statsDto.getForwardStats().stream().sorted(Comparator.comparing(UserPackageFlowStatsDto.ForwardFlowStatsDto::getName)).map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getUserName).toList());
    }

    @Test
    void shouldAggregateRealtimeCurrentHourForAdminScope() {
        long now = System.currentTimeMillis();
        User admin = saveUser("realtime-admin", 0, now, 0L, 0L);
        User userOne = saveUser("realtime-admin-user-1", 1, now, 90L, 50L);
        User userTwo = saveUser("realtime-admin-user-2", 1, now, 40L, 25L);
        Tunnel tunnel = saveTunnel("realtime-admin-tunnel", now, "6.6.6.6");

        long currentHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long previousHour = currentHour - ChronoUnit.HOURS.getDuration().toMillis();

        statisticsFlowService.save(buildUserBucket(userOne.getId(), previousHour, 6L, 4L, 10L, 70L, 30L));

        Forward forwardOne = saveForward(userOne, tunnel, now + 1, "realtime-admin-forward-a", "8.8.6.6:80", 68_001);
        forwardOne.setInFlow(90L);
        forwardOne.setOutFlow(50L);
        forwardService.updateById(forwardOne);
        forwardStatisticsFlowService.save(buildForwardBucket(
                userOne.getId(),
                forwardOne.getId(),
                forwardOne.getName(),
                tunnel.getId(),
                tunnel.getName(),
                previousHour,
                6L,
                4L,
                10L,
                70L,
                30L
        ));

        Forward forwardTwo = saveForward(userTwo, tunnel, now + 2, "realtime-admin-forward-b", "8.8.6.7:81", 68_002);
        forwardTwo.setInFlow(40L);
        forwardTwo.setOutFlow(25L);
        forwardService.updateById(forwardTwo);

        setUserRequest(admin.getId().intValue(), 0, admin.getUser());

        FlowStatsQueryDto statsQuery = new FlowStatsQueryDto();
        statsQuery.setStartTime(previousHour);
        statsQuery.setEndTime(currentHour);

        R statsResult = userService.getUserPackageFlowStats(statsQuery);

        assertEquals(0, statsResult.getCode());
        UserPackageFlowStatsDto statsDto = assertInstanceOf(UserPackageFlowStatsDto.class, statsResult.getData());
        assertEquals("global", statsDto.getMeta().getScope());
        assertEquals("all", statsDto.getMeta().getRankingMode());
        assertEquals(2, statsDto.getMeta().getTotalRuleCount());
        assertEquals(2, statsDto.getMeta().getReturnedRuleCount());
        assertEquals(10L, statsDto.getSeries().get(0).getFlow());
        assertEquals(105L, statsDto.getSeries().get(1).getFlow());
        assertEquals(115L, statsDto.getSummary().getTotalFlow());
        assertEquals(currentHour, statsDto.getDefaultHourTime());
        assertEquals(List.of("realtime-admin-user-2", "realtime-admin-user-1"),
                statsDto.getForwardStats().stream().map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getUserName).toList());
        assertEquals(List.of(65L, 40L),
                statsDto.getForwardStats().stream().map(UserPackageFlowStatsDto.ForwardFlowStatsDto::getFlow).toList());
    }

    @Test
    void shouldRejectRangesLongerThanThirtyDays() {
        User user = saveUser("range-user", 1, System.currentTimeMillis(), 0L, 0L);
        setUserRequest(user.getId().intValue(), 1, user.getUser());

        FlowStatsQueryDto queryDto = new FlowStatsQueryDto();
        queryDto.setStartTime(System.currentTimeMillis() - ChronoUnit.DAYS.getDuration().toMillis() * 31);
        queryDto.setEndTime(System.currentTimeMillis());

        R result = userService.getUserPackageFlowStats(queryDto);

        assertEquals(-1, result.getCode());
        assertEquals("统计时间范围不能超过30天", result.getMsg());
    }

    private User saveUser(String username, int roleId, long now, long inFlow, long outFlow) {
        User user = new User();
        user.setUser(username);
        user.setPwd("pwd");
        user.setRoleId(roleId);
        user.setExpTime(now + 86_400_000L);
        user.setFlow(99999L);
        user.setInFlow(inFlow);
        user.setOutFlow(outFlow);
        user.setFlowResetTime(0L);
        user.setNum(100);
        user.setStatus(1);
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        userService.save(user);
        return user;
    }

    private Tunnel saveTunnel(String name, long now, String inIp) {
        Tunnel tunnel = new Tunnel();
        tunnel.setName(name);
        tunnel.setTrafficRatio(java.math.BigDecimal.ONE);
        tunnel.setType(2);
        tunnel.setProtocol("tcp");
        tunnel.setFlow(1);
        tunnel.setInIp(inIp);
        tunnel.setCreatedTime(now);
        tunnel.setUpdatedTime(now);
        tunnel.setStatus(1);
        tunnelService.save(tunnel);
        return tunnel;
    }

    private Forward saveForward(User user, Tunnel tunnel, long now, String name, String remoteAddr, int port) {
        Forward forward = new Forward();
        forward.setUserId(user.getId().intValue());
        forward.setUserName(user.getUser());
        forward.setName(name);
        forward.setTunnelId(tunnel.getId().intValue());
        forward.setRemoteAddr(remoteAddr);
        forward.setStrategy("fifo");
        forward.setInFlow(0L);
        forward.setOutFlow(0L);
        forward.setCreatedTime(now);
        forward.setUpdatedTime(now);
        forward.setStatus(1);
        forward.setInx(0);
        forwardService.save(forward);

        ForwardPort forwardPort = new ForwardPort();
        forwardPort.setForwardId(forward.getId());
        forwardPort.setNodeId(1L);
        forwardPort.setPort(port);
        forwardPortService.save(forwardPort);
        return forward;
    }

    private StatisticsFlow buildUserBucket(Long userId, long hourTime, long inFlow, long outFlow, long flow) {
        return buildUserBucket(userId, hourTime, inFlow, outFlow, flow, inFlow, outFlow);
    }

    private StatisticsFlow buildUserBucket(Long userId,
                                           long hourTime,
                                           long inFlow,
                                           long outFlow,
                                           long flow,
                                           long totalInFlow,
                                           long totalOutFlow) {
        StatisticsFlow bucket = new StatisticsFlow();
        bucket.setUserId(userId);
        bucket.setHourTime(hourTime);
        bucket.setTime("bucket");
        bucket.setInFlow(inFlow);
        bucket.setOutFlow(outFlow);
        bucket.setFlow(flow);
        bucket.setTotalInFlow(totalInFlow);
        bucket.setTotalOutFlow(totalOutFlow);
        bucket.setTotalFlow(totalInFlow + totalOutFlow);
        bucket.setCreatedTime(hourTime);
        return bucket;
    }

    private ForwardStatisticsFlow buildForwardBucket(Long userId,
                                                     Long forwardId,
                                                     String forwardName,
                                                     Long tunnelId,
                                                     String tunnelName,
                                                     long hourTime,
                                                     long inFlow,
                                                     long outFlow,
                                                     long flow) {
        return buildForwardBucket(userId, forwardId, forwardName, tunnelId, tunnelName, hourTime, inFlow, outFlow, flow, inFlow, outFlow);
    }

    private ForwardStatisticsFlow buildForwardBucket(Long userId,
                                                     Long forwardId,
                                                     String forwardName,
                                                     Long tunnelId,
                                                     String tunnelName,
                                                     long hourTime,
                                                     long inFlow,
                                                     long outFlow,
                                                     long flow,
                                                     long totalInFlow,
                                                     long totalOutFlow) {
        ForwardStatisticsFlow bucket = new ForwardStatisticsFlow();
        bucket.setUserId(userId);
        bucket.setForwardId(forwardId);
        bucket.setForwardName(forwardName);
        bucket.setTunnelId(tunnelId);
        bucket.setTunnelName(tunnelName);
        bucket.setHourTime(hourTime);
        bucket.setTime("bucket");
        bucket.setInFlow(inFlow);
        bucket.setOutFlow(outFlow);
        bucket.setFlow(flow);
        bucket.setTotalInFlow(totalInFlow);
        bucket.setTotalOutFlow(totalOutFlow);
        bucket.setTotalFlow(totalInFlow + totalOutFlow);
        bucket.setCreatedTime(hourTime);
        return bucket;
    }

    private void setUserRequest(int userId, int roleId, String name) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", buildToken(userId, roleId, name));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String buildToken(int userId, int roleId, String name) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = String.format("{\"sub\":\"%d\",\"role_id\":%d,\"name\":\"%s\"}", userId, roleId, name);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return header + "." + encodedPayload + ".sig";
    }
}
