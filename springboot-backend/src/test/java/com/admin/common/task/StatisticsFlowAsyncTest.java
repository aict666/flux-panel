package com.admin.common.task;

import com.admin.entity.Forward;
import com.admin.entity.ForwardStatisticsFlow;
import com.admin.entity.StatisticsFlow;
import com.admin.entity.Tunnel;
import com.admin.entity.User;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class StatisticsFlowAsyncTest extends PostgresIntegrationTestSupport {

    @Autowired
    private StatisticsFlowAsync statisticsFlowAsync;

    @Autowired
    private UserService userService;

    @Autowired
    private TunnelService tunnelService;

    @Autowired
    private ForwardService forwardService;

    @Autowired
    private StatisticsFlowService statisticsFlowService;

    @Autowired
    private ForwardStatisticsFlowService forwardStatisticsFlowService;

    @BeforeEach
    void setUp() {
        forwardStatisticsFlowService.remove(new QueryWrapper<ForwardStatisticsFlow>().gt("id", 0));
        statisticsFlowService.remove(new QueryWrapper<StatisticsFlow>().gt("id", 0));
        forwardService.remove(new QueryWrapper<Forward>().gt("id", 0));
        tunnelService.remove(new QueryWrapper<Tunnel>().gt("id", 0));
        userService.remove(new QueryWrapper<User>().gt("id", 1));
    }

    @Test
    void shouldCaptureUserAndForwardHourlyBucketsAndClearExpiredHistory() {
        User user = new User();
        user.setUser("stats-user");
        user.setPwd("pwd");
        user.setRoleId(1);
        user.setExpTime(System.currentTimeMillis() + 86_400_000L);
        user.setFlow(99999L);
        user.setInFlow(300L);
        user.setOutFlow(200L);
        user.setFlowResetTime(0L);
        user.setNum(10);
        user.setStatus(1);
        long now = System.currentTimeMillis();
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        userService.save(user);

        Tunnel tunnel = new Tunnel();
        tunnel.setName("stats-tunnel");
        tunnel.setTrafficRatio(java.math.BigDecimal.ONE);
        tunnel.setType(2);
        tunnel.setProtocol("tcp");
        tunnel.setFlow(1);
        tunnel.setCreatedTime(now);
        tunnel.setUpdatedTime(now);
        tunnel.setStatus(1);
        tunnelService.save(tunnel);

        Forward forward = new Forward();
        forward.setUserId(user.getId().intValue());
        forward.setUserName(user.getUser());
        forward.setName("stats-forward");
        forward.setTunnelId(tunnel.getId().intValue());
        forward.setRemoteAddr("127.0.0.1:8080");
        forward.setStrategy("fifo");
        forward.setInFlow(120L);
        forward.setOutFlow(80L);
        forward.setCreatedTime(now);
        forward.setUpdatedTime(now);
        forward.setStatus(1);
        forward.setInx(0);
        forwardService.save(forward);

        long currentHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long previousHour = currentHour - ChronoUnit.HOURS.getDuration().toMillis();
        long staleHour = currentHour - ChronoUnit.DAYS.getDuration().toMillis() * 31;

        StatisticsFlow previousUserBucket = new StatisticsFlow();
        previousUserBucket.setUserId(user.getId());
        previousUserBucket.setHourTime(previousHour);
        previousUserBucket.setTime("上一小时");
        previousUserBucket.setInFlow(100L);
        previousUserBucket.setOutFlow(50L);
        previousUserBucket.setFlow(150L);
        previousUserBucket.setTotalInFlow(100L);
        previousUserBucket.setTotalOutFlow(50L);
        previousUserBucket.setTotalFlow(150L);
        previousUserBucket.setCreatedTime(previousHour);
        statisticsFlowService.save(previousUserBucket);

        StatisticsFlow staleUserBucket = new StatisticsFlow();
        staleUserBucket.setUserId(user.getId());
        staleUserBucket.setHourTime(staleHour);
        staleUserBucket.setTime("过期");
        staleUserBucket.setInFlow(1L);
        staleUserBucket.setOutFlow(1L);
        staleUserBucket.setFlow(2L);
        staleUserBucket.setTotalInFlow(1L);
        staleUserBucket.setTotalOutFlow(1L);
        staleUserBucket.setTotalFlow(2L);
        staleUserBucket.setCreatedTime(staleHour);
        statisticsFlowService.save(staleUserBucket);

        ForwardStatisticsFlow previousForwardBucket = new ForwardStatisticsFlow();
        previousForwardBucket.setUserId(user.getId());
        previousForwardBucket.setForwardId(forward.getId());
        previousForwardBucket.setForwardName(forward.getName());
        previousForwardBucket.setTunnelId(tunnel.getId());
        previousForwardBucket.setTunnelName(tunnel.getName());
        previousForwardBucket.setHourTime(previousHour);
        previousForwardBucket.setTime("上一小时");
        previousForwardBucket.setInFlow(40L);
        previousForwardBucket.setOutFlow(10L);
        previousForwardBucket.setFlow(50L);
        previousForwardBucket.setTotalInFlow(40L);
        previousForwardBucket.setTotalOutFlow(10L);
        previousForwardBucket.setTotalFlow(50L);
        previousForwardBucket.setCreatedTime(previousHour);
        forwardStatisticsFlowService.save(previousForwardBucket);

        ForwardStatisticsFlow staleForwardBucket = new ForwardStatisticsFlow();
        staleForwardBucket.setUserId(user.getId());
        staleForwardBucket.setForwardId(forward.getId());
        staleForwardBucket.setForwardName(forward.getName());
        staleForwardBucket.setTunnelId(tunnel.getId());
        staleForwardBucket.setTunnelName(tunnel.getName());
        staleForwardBucket.setHourTime(staleHour);
        staleForwardBucket.setTime("过期");
        staleForwardBucket.setInFlow(1L);
        staleForwardBucket.setOutFlow(1L);
        staleForwardBucket.setFlow(2L);
        staleForwardBucket.setTotalInFlow(1L);
        staleForwardBucket.setTotalOutFlow(1L);
        staleForwardBucket.setTotalFlow(2L);
        staleForwardBucket.setCreatedTime(staleHour);
        forwardStatisticsFlowService.save(staleForwardBucket);

        statisticsFlowAsync.statistics_flow();

        StatisticsFlow currentUserBucket = statisticsFlowService.getOne(new QueryWrapper<StatisticsFlow>()
                .eq("user_id", user.getId())
                .eq("hour_time", currentHour));
        assertEquals(200L, currentUserBucket.getInFlow());
        assertEquals(150L, currentUserBucket.getOutFlow());
        assertEquals(350L, currentUserBucket.getFlow());
        assertEquals(300L, currentUserBucket.getTotalInFlow());
        assertEquals(200L, currentUserBucket.getTotalOutFlow());
        assertEquals(500L, currentUserBucket.getTotalFlow());

        ForwardStatisticsFlow currentForwardBucket = forwardStatisticsFlowService.getOne(new QueryWrapper<ForwardStatisticsFlow>()
                .eq("forward_id", forward.getId())
                .eq("hour_time", currentHour));
        assertEquals(80L, currentForwardBucket.getInFlow());
        assertEquals(70L, currentForwardBucket.getOutFlow());
        assertEquals(150L, currentForwardBucket.getFlow());
        assertEquals(120L, currentForwardBucket.getTotalInFlow());
        assertEquals(80L, currentForwardBucket.getTotalOutFlow());
        assertEquals(200L, currentForwardBucket.getTotalFlow());

        StatisticsFlow removedUserBucket = statisticsFlowService.getById(staleUserBucket.getId());
        ForwardStatisticsFlow removedForwardBucket = forwardStatisticsFlowService.getById(staleForwardBucket.getId());
        assertNull(removedUserBucket);
        assertNull(removedForwardBucket);
    }
}
