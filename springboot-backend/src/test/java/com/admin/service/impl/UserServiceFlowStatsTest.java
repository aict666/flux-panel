package com.admin.service.impl;

import com.admin.common.dto.FlowStatsQueryDto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
    void shouldReturnHourlySeriesWithFilledGapsAndAggregatedForwardTotals() {
        long now = System.currentTimeMillis();

        User user = new User();
        user.setUser("query-user");
        user.setPwd("pwd");
        user.setRoleId(1);
        user.setExpTime(now + 86_400_000L);
        user.setFlow(99999L);
        user.setInFlow(500L);
        user.setOutFlow(400L);
        user.setFlowResetTime(0L);
        user.setNum(10);
        user.setStatus(1);
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        userService.save(user);

        Tunnel tunnel = new Tunnel();
        tunnel.setName("query-tunnel");
        tunnel.setTrafficRatio(java.math.BigDecimal.ONE);
        tunnel.setType(2);
        tunnel.setProtocol("tcp");
        tunnel.setFlow(1);
        tunnel.setInIp("1.2.3.4");
        tunnel.setCreatedTime(now);
        tunnel.setUpdatedTime(now);
        tunnel.setStatus(1);
        tunnelService.save(tunnel);

        Forward forward = new Forward();
        forward.setUserId(user.getId().intValue());
        forward.setUserName(user.getUser());
        forward.setName("query-forward");
        forward.setTunnelId(tunnel.getId().intValue());
        forward.setRemoteAddr("8.8.8.8:53");
        forward.setStrategy("fifo");
        forward.setInFlow(300L);
        forward.setOutFlow(250L);
        forward.setCreatedTime(now);
        forward.setUpdatedTime(now);
        forward.setStatus(1);
        forward.setInx(0);
        forwardService.save(forward);

        ForwardPort forwardPort = new ForwardPort();
        forwardPort.setForwardId(forward.getId());
        forwardPort.setNodeId(1L);
        forwardPort.setPort(18080);
        forwardPortService.save(forwardPort);

        long startHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(2)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long middleHour = startHour + ChronoUnit.HOURS.getDuration().toMillis();
        long endHour = middleHour + ChronoUnit.HOURS.getDuration().toMillis();

        statisticsFlowService.save(buildUserBucket(user.getId(), startHour, 100L, 20L, 120L));
        statisticsFlowService.save(buildUserBucket(user.getId(), endHour, 50L, 10L, 60L));

        forwardStatisticsFlowService.save(buildForwardBucket(user.getId(), forward.getId(), forward.getName(), tunnel.getId(), tunnel.getName(), startHour, 20L, 5L, 25L));
        forwardStatisticsFlowService.save(buildForwardBucket(user.getId(), forward.getId(), forward.getName(), tunnel.getId(), tunnel.getName(), endHour, 30L, 15L, 45L));

        setUserRequest(user.getId().intValue(), user.getUser());

        FlowStatsQueryDto queryDto = new FlowStatsQueryDto();
        queryDto.setStartTime(startHour);
        queryDto.setEndTime(endHour);

        R result = userService.getUserPackageFlowStats(queryDto);

        assertEquals(0, result.getCode());
        UserPackageFlowStatsDto data = assertInstanceOf(UserPackageFlowStatsDto.class, result.getData());
        assertEquals(startHour, data.getRange().getStartTime());
        assertEquals(endHour, data.getRange().getEndTime());
        assertEquals(3, data.getSeries().size());
        assertEquals(120L, data.getSeries().get(0).getFlow());
        assertEquals(0L, data.getSeries().get(1).getFlow());
        assertEquals(60L, data.getSeries().get(2).getFlow());
        assertEquals(1, data.getForwardStats().size());
        assertEquals("query-forward", data.getForwardStats().get(0).getName());
        assertEquals("1.2.3.4:18080", data.getForwardStats().get(0).getInAddress());
        assertEquals(50L, data.getForwardStats().get(0).getInFlow());
        assertEquals(20L, data.getForwardStats().get(0).getOutFlow());
        assertEquals(70L, data.getForwardStats().get(0).getFlow());
    }

    @Test
    void shouldRejectRangesLongerThanThirtyDays() {
        User user = new User();
        user.setUser("range-user");
        user.setPwd("pwd");
        user.setRoleId(1);
        user.setExpTime(System.currentTimeMillis() + 86_400_000L);
        user.setFlow(99999L);
        user.setInFlow(0L);
        user.setOutFlow(0L);
        user.setFlowResetTime(0L);
        user.setNum(1);
        user.setStatus(1);
        user.setCreatedTime(System.currentTimeMillis());
        user.setUpdatedTime(System.currentTimeMillis());
        userService.save(user);
        setUserRequest(user.getId().intValue(), user.getUser());

        FlowStatsQueryDto queryDto = new FlowStatsQueryDto();
        queryDto.setStartTime(System.currentTimeMillis() - ChronoUnit.DAYS.getDuration().toMillis() * 31);
        queryDto.setEndTime(System.currentTimeMillis());

        R result = userService.getUserPackageFlowStats(queryDto);

        assertEquals(-1, result.getCode());
        assertEquals("统计时间范围不能超过30天", result.getMsg());
    }

    private StatisticsFlow buildUserBucket(Long userId, long hourTime, long inFlow, long outFlow, long flow) {
        StatisticsFlow bucket = new StatisticsFlow();
        bucket.setUserId(userId);
        bucket.setHourTime(hourTime);
        bucket.setTime("bucket");
        bucket.setInFlow(inFlow);
        bucket.setOutFlow(outFlow);
        bucket.setFlow(flow);
        bucket.setTotalInFlow(inFlow);
        bucket.setTotalOutFlow(outFlow);
        bucket.setTotalFlow(flow);
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
        bucket.setTotalInFlow(inFlow);
        bucket.setTotalOutFlow(outFlow);
        bucket.setTotalFlow(flow);
        bucket.setCreatedTime(hourTime);
        return bucket;
    }

    private void setUserRequest(int userId, String name) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", buildToken(userId, 1, name));
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
