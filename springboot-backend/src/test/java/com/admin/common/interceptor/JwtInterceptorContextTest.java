package com.admin.common.interceptor;

import com.admin.common.context.ActorContext;
import com.admin.common.context.ActorContextHolder;
import com.admin.common.context.ActorType;
import com.admin.common.utils.JwtUtil;
import com.admin.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtInterceptorContextTest {

    @AfterEach
    void tearDown() {
        ActorContextHolder.clear();
    }

    @Test
    void preHandleShouldPopulateActorContextAndAfterCompletionShouldClear() throws Exception {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "test-secret");
        jwtUtil.init();

        User user = new User();
        user.setId(7L);
        user.setUser("admin_user");
        user.setRoleId(0);
        String token = JwtUtil.generateToken(user);

        JwtInterceptor interceptor = new JwtInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", token);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);

        ActorContext context = ActorContextHolder.get();
        assertEquals(ActorType.USER, context.getActorType());
        assertEquals(7, context.getUserId());
        assertEquals("admin_user", context.getUserName());
        assertEquals(0, context.getRoleId());

        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertNull(ActorContextHolder.get());
    }
}
