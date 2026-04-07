package com.admin.service.impl;

import com.admin.common.dto.UserTunnelUpdateDto;
import com.admin.common.lang.R;
import com.admin.entity.Forward;
import com.admin.entity.UserTunnel;
import com.admin.service.ForwardService;
import com.admin.service.UserTunnelService;
import com.admin.support.PostgresIntegrationTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class UserTunnelServiceImplTest extends PostgresIntegrationTestSupport {

    @Autowired
    private UserTunnelService userTunnelService;

    @MockBean
    private ForwardService forwardService;

    @BeforeEach
    void setUp() {
        userTunnelService.remove(new QueryWrapper<UserTunnel>().gt("id", 0));
        Mockito.reset(forwardService);
    }

    @Test
    void shouldReturnSuccessWhenUserTunnelUpdateSucceeds() {
        UserTunnel userTunnel = new UserTunnel();
        userTunnel.setUserId(1);
        userTunnel.setTunnelId(2);
        userTunnel.setFlow(100L);
        userTunnel.setNum(3);
        userTunnel.setFlowResetTime(0L);
        userTunnel.setExpTime(System.currentTimeMillis() + 86_400_000L);
        userTunnel.setStatus(1);
        userTunnelService.save(userTunnel);

        UserTunnelUpdateDto updateDto = new UserTunnelUpdateDto();
        updateDto.setId(userTunnel.getId());
        updateDto.setFlow(200L);
        updateDto.setNum(5);
        updateDto.setFlowResetTime(15L);
        updateDto.setExpTime(System.currentTimeMillis() + 172_800_000L);
        updateDto.setStatus(1);
        updateDto.setSpeedId(null);

        R result = userTunnelService.updateUserTunnel(updateDto);

        assertEquals(0, result.getCode());
    }

    @Test
    void shouldRefreshExistingForwardsWhenSpeedLimitChanges() {
        UserTunnel userTunnel = new UserTunnel();
        userTunnel.setUserId(8);
        userTunnel.setTunnelId(9);
        userTunnel.setFlow(100L);
        userTunnel.setNum(3);
        userTunnel.setFlowResetTime(0L);
        userTunnel.setExpTime(System.currentTimeMillis() + 86_400_000L);
        userTunnel.setStatus(1);
        userTunnel.setSpeedId(1);
        userTunnelService.save(userTunnel);

        Forward forward = new Forward();
        forward.setId(12L);
        forward.setUserId(userTunnel.getUserId());
        forward.setTunnelId(userTunnel.getTunnelId());
        forward.setName("forward-a");
        forward.setRemoteAddr("127.0.0.1:8080");
        forward.setStrategy("fifo");

        Mockito.when(forwardService.list(Mockito.<QueryWrapper<Forward>>any())).thenReturn(List.of(forward));
        Mockito.when(forwardService.updateForward(Mockito.any())).thenReturn(R.ok());

        UserTunnelUpdateDto updateDto = new UserTunnelUpdateDto();
        updateDto.setId(userTunnel.getId());
        updateDto.setFlow(300L);
        updateDto.setNum(7);
        updateDto.setFlowResetTime(7L);
        updateDto.setExpTime(System.currentTimeMillis() + 172_800_000L);
        updateDto.setStatus(0);
        updateDto.setSpeedId(2);

        R result = userTunnelService.updateUserTunnel(updateDto);

        assertEquals(0, result.getCode());
        Mockito.verify(forwardService).updateForward(Mockito.argThat(dto ->
                dto.getId().equals(forward.getId())
                        && dto.getUserId().equals(forward.getUserId())
                        && dto.getName().equals(forward.getName())
                        && dto.getRemoteAddr().equals(forward.getRemoteAddr())
                        && dto.getStrategy().equals(forward.getStrategy())
        ));

        UserTunnel updated = userTunnelService.getById(userTunnel.getId());
        assertNotNull(updated);
        assertEquals(300L, updated.getFlow());
        assertEquals(7, updated.getNum());
        assertEquals(7L, updated.getFlowResetTime());
        assertEquals(0, updated.getStatus());
        assertEquals(2, updated.getSpeedId());
    }

    @Test
    void shouldReturnErrorWhenUserTunnelDoesNotExist() {
        UserTunnelUpdateDto updateDto = new UserTunnelUpdateDto();
        updateDto.setId(999999);
        updateDto.setFlow(100L);
        updateDto.setNum(3);
        updateDto.setFlowResetTime(1L);
        updateDto.setExpTime(System.currentTimeMillis() + 86_400_000L);
        updateDto.setStatus(1);

        R result = userTunnelService.updateUserTunnel(updateDto);

        assertEquals(-1, result.getCode());
        assertEquals("隧道不存在", result.getMsg());
    }
}
