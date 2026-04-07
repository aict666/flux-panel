package com.admin.service.impl;

import com.admin.common.dto.LoginDto;
import com.admin.common.lang.R;
import com.admin.common.utils.Md5Util;
import com.admin.controller.OpenApiController;
import com.admin.entity.User;
import com.admin.service.UserService;
import com.admin.support.PostgresIntegrationTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "LOG_DIR=target/test-logs",
        "JWT_SECRET=test-secret"
})
class UserAuthenticationLookupTest extends PostgresIntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private OpenApiController openApiController;

    @BeforeEach
    void setUp() {
        userService.remove(new QueryWrapper<User>().gt("id", 1));
    }

    @Test
    void loginShouldFindUserByLegacyUserFieldAndReturnToken() {
        User user = createUser("login-user", "plain-password");

        LoginDto loginDto = new LoginDto();
        loginDto.setUsername(user.getUser());
        loginDto.setPassword("plain-password");

        R result = userService.login(loginDto);

        assertEquals(0, result.getCode());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertNotNull(data.get("token"));
        assertEquals("login-user", data.get("name"));
    }

    @Test
    void openApiShouldAuthenticateWithLegacyUserParameterAgainstPostgresUsersTable() {
        User user = createUser("open-user", "plain-password");
        user.setInFlow(128L);
        user.setOutFlow(64L);
        user.setFlow(1024L);
        userService.updateById(user);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = openApiController.create("open-user", "plain-password", "-1", response);

        assertInstanceOf(String.class, result);
        String header = (String) result;
        assertEquals(header, response.getHeader("subscription-userinfo"));
        assertTrue(header.contains("upload=64"));
        assertTrue(header.contains("download=128"));
    }

    private User createUser(String username, String password) {
        long now = System.currentTimeMillis();
        User user = new User();
        user.setUser(username);
        user.setPwd(Md5Util.md5(password));
        user.setRoleId(1);
        user.setExpTime(now + 86_400_000L);
        user.setFlow(99999L);
        user.setInFlow(0L);
        user.setOutFlow(0L);
        user.setFlowResetTime(0L);
        user.setNum(1);
        user.setStatus(1);
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        userService.save(user);
        return user;
    }
}
