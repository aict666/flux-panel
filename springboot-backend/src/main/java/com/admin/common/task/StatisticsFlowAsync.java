package com.admin.common.task;

import com.admin.entity.Forward;
import com.admin.entity.ForwardStatisticsFlow;
import com.admin.entity.StatisticsFlow;
import com.admin.entity.User;
import com.admin.service.ForwardService;
import com.admin.service.ForwardStatisticsFlowService;
import com.admin.service.StatisticsFlowService;
import com.admin.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Configuration
@EnableScheduling
public class StatisticsFlowAsync {

    private static final long THIRTY_DAYS_MILLIS = ChronoUnit.DAYS.getDuration().toMillis() * 30;

    @Resource
    UserService userService;

    @Resource
    StatisticsFlowService statisticsFlowService;

    @Resource
    ForwardService forwardService;

    @Resource
    ForwardStatisticsFlowService forwardStatisticsFlowService;

    @Scheduled(cron = "0 0 * * * ?")
    public void statistics_flow() {
        LocalDateTime currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        String hourString = currentHour.format(DateTimeFormatter.ofPattern("HH:mm"));
        long hourTime = currentHour.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long cutoffMs = hourTime - THIRTY_DAYS_MILLIS;

        statisticsFlowService.remove(
                new LambdaQueryWrapper<StatisticsFlow>()
                        .lt(StatisticsFlow::getHourTime, cutoffMs)
        );
        forwardStatisticsFlowService.remove(
                new LambdaQueryWrapper<ForwardStatisticsFlow>()
                        .lt(ForwardStatisticsFlow::getHourTime, cutoffMs)
        );

        List<User> list = userService.list();
        List<StatisticsFlow> statisticsFlowList = new ArrayList<>();

        for (User user : list) {
            StatisticsFlow statisticsFlow = buildUserBucket(user, hourTime, hourString);
            if (statisticsFlow != null) {
                statisticsFlowList.add(statisticsFlow);
            }
        }
        saveOrUpdateUserBuckets(hourTime, statisticsFlowList);

        List<Forward> forwards = forwardService.list();
        List<ForwardStatisticsFlow> forwardStatistics = new ArrayList<>();
        for (Forward forward : forwards) {
            ForwardStatisticsFlow statisticsFlow = buildForwardBucket(forward, hourTime, hourString);
            if (statisticsFlow != null) {
                forwardStatistics.add(statisticsFlow);
            }
        }
        saveOrUpdateForwardBuckets(hourTime, forwardStatistics);
    }

    private StatisticsFlow buildUserBucket(User user, long hourTime, String hourString) {
        long currentInFlow = safeLong(user.getInFlow());
        long currentOutFlow = safeLong(user.getOutFlow());
        StatisticsFlow lastFlowRecord = statisticsFlowService.getOne(
                new LambdaQueryWrapper<StatisticsFlow>()
                        .eq(StatisticsFlow::getUserId, user.getId())
                        .lt(StatisticsFlow::getHourTime, hourTime)
                        .orderByDesc(StatisticsFlow::getHourTime)
                        .last("LIMIT 1")
        );

        long incrementInFlow = calculateIncrement(currentInFlow, lastFlowRecord == null ? null : lastFlowRecord.getTotalInFlow());
        long incrementOutFlow = calculateIncrement(currentOutFlow, lastFlowRecord == null ? null : lastFlowRecord.getTotalOutFlow());

        StatisticsFlow statisticsFlow = new StatisticsFlow();
        statisticsFlow.setUserId(user.getId());
        statisticsFlow.setInFlow(incrementInFlow);
        statisticsFlow.setOutFlow(incrementOutFlow);
        statisticsFlow.setFlow(incrementInFlow + incrementOutFlow);
        statisticsFlow.setTotalInFlow(currentInFlow);
        statisticsFlow.setTotalOutFlow(currentOutFlow);
        statisticsFlow.setTotalFlow(currentInFlow + currentOutFlow);
        statisticsFlow.setHourTime(hourTime);
        statisticsFlow.setTime(hourString);
        statisticsFlow.setCreatedTime(hourTime);
        return statisticsFlow;
    }

    private ForwardStatisticsFlow buildForwardBucket(Forward forward, long hourTime, String hourString) {
        long currentInFlow = safeLong(forward.getInFlow());
        long currentOutFlow = safeLong(forward.getOutFlow());
        ForwardStatisticsFlow lastFlowRecord = forwardStatisticsFlowService.getOne(
                new LambdaQueryWrapper<ForwardStatisticsFlow>()
                        .eq(ForwardStatisticsFlow::getForwardId, forward.getId())
                        .lt(ForwardStatisticsFlow::getHourTime, hourTime)
                        .orderByDesc(ForwardStatisticsFlow::getHourTime)
                        .last("LIMIT 1")
        );

        long incrementInFlow = calculateIncrement(currentInFlow, lastFlowRecord == null ? null : lastFlowRecord.getTotalInFlow());
        long incrementOutFlow = calculateIncrement(currentOutFlow, lastFlowRecord == null ? null : lastFlowRecord.getTotalOutFlow());

        ForwardStatisticsFlow statisticsFlow = new ForwardStatisticsFlow();
        statisticsFlow.setUserId(forward.getUserId() == null ? null : forward.getUserId().longValue());
        statisticsFlow.setForwardId(forward.getId());
        statisticsFlow.setForwardName(forward.getName());
        statisticsFlow.setTunnelId(forward.getTunnelId() == null ? null : forward.getTunnelId().longValue());
        statisticsFlow.setInFlow(incrementInFlow);
        statisticsFlow.setOutFlow(incrementOutFlow);
        statisticsFlow.setFlow(incrementInFlow + incrementOutFlow);
        statisticsFlow.setTotalInFlow(currentInFlow);
        statisticsFlow.setTotalOutFlow(currentOutFlow);
        statisticsFlow.setTotalFlow(currentInFlow + currentOutFlow);
        statisticsFlow.setHourTime(hourTime);
        statisticsFlow.setTime(hourString);
        statisticsFlow.setCreatedTime(hourTime);
        return statisticsFlow;
    }

    private void saveOrUpdateUserBuckets(long hourTime, List<StatisticsFlow> buckets) {
        for (StatisticsFlow bucket : buckets) {
            StatisticsFlow currentHourRecord = statisticsFlowService.getOne(
                    new LambdaQueryWrapper<StatisticsFlow>()
                            .eq(StatisticsFlow::getUserId, bucket.getUserId())
                            .eq(StatisticsFlow::getHourTime, hourTime)
                            .last("LIMIT 1")
            );
            if (currentHourRecord == null) {
                statisticsFlowService.save(bucket);
                continue;
            }
            currentHourRecord.setInFlow(bucket.getInFlow());
            currentHourRecord.setOutFlow(bucket.getOutFlow());
            currentHourRecord.setFlow(bucket.getFlow());
            currentHourRecord.setTotalInFlow(bucket.getTotalInFlow());
            currentHourRecord.setTotalOutFlow(bucket.getTotalOutFlow());
            currentHourRecord.setTotalFlow(bucket.getTotalFlow());
            currentHourRecord.setTime(bucket.getTime());
            currentHourRecord.setCreatedTime(bucket.getCreatedTime());
            statisticsFlowService.updateById(currentHourRecord);
        }
    }

    private void saveOrUpdateForwardBuckets(long hourTime, List<ForwardStatisticsFlow> buckets) {
        for (ForwardStatisticsFlow bucket : buckets) {
            ForwardStatisticsFlow currentHourRecord = forwardStatisticsFlowService.getOne(
                    new LambdaQueryWrapper<ForwardStatisticsFlow>()
                            .eq(ForwardStatisticsFlow::getForwardId, bucket.getForwardId())
                            .eq(ForwardStatisticsFlow::getHourTime, hourTime)
                            .last("LIMIT 1")
            );
            if (currentHourRecord == null) {
                forwardStatisticsFlowService.save(bucket);
                continue;
            }
            currentHourRecord.setUserId(bucket.getUserId());
            currentHourRecord.setForwardName(bucket.getForwardName());
            currentHourRecord.setTunnelId(bucket.getTunnelId());
            currentHourRecord.setTunnelName(bucket.getTunnelName());
            currentHourRecord.setInFlow(bucket.getInFlow());
            currentHourRecord.setOutFlow(bucket.getOutFlow());
            currentHourRecord.setFlow(bucket.getFlow());
            currentHourRecord.setTotalInFlow(bucket.getTotalInFlow());
            currentHourRecord.setTotalOutFlow(bucket.getTotalOutFlow());
            currentHourRecord.setTotalFlow(bucket.getTotalFlow());
            currentHourRecord.setTime(bucket.getTime());
            currentHourRecord.setCreatedTime(bucket.getCreatedTime());
            forwardStatisticsFlowService.updateById(currentHourRecord);
        }
    }

    private long calculateIncrement(long currentValue, Long previousTotal) {
        if (previousTotal == null) {
            return currentValue;
        }
        long increment = currentValue - previousTotal;
        return increment < 0 ? currentValue : increment;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
