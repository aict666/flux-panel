package com.admin;

import com.admin.common.dto.GostDto;
import com.admin.common.utils.GostUtil;
import com.admin.entity.Forward;
import com.admin.entity.Tunnel;
import com.admin.entity.User;
import com.admin.entity.UserTunnel;
import com.admin.service.ForwardService;
import com.admin.service.TunnelService;
import com.admin.service.UserService;
import com.admin.service.UserTunnelService;
import com.admin.support.PostgresIntegrationTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;


@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class AdminApplicationTests extends PostgresIntegrationTestSupport {



    @Test
    public void test(){

    }




}
