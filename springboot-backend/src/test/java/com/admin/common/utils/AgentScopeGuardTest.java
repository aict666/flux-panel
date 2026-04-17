package com.admin.common.utils;

import com.admin.common.context.ActorContext;
import com.admin.common.context.ActorContextHolder;
import com.admin.common.context.ActorType;
import com.admin.common.lang.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentScopeGuardTest {

    @AfterEach
    void tearDown() {
        ActorContextHolder.clear();
    }

    @Test
    void requireScopeShouldAllowUserActors() {
        ActorContextHolder.set(new ActorContext(ActorType.USER, 1, "admin", 0, null, null, Set.of()));

        R result = AgentScopeGuard.requireScope("forwards:write");

        assertNull(result);
    }

    @Test
    void requireScopeShouldBlockAgentWithoutScope() {
        ActorContextHolder.set(new ActorContext(ActorType.AGENT, null, null, null, 9L, "claw", Set.of("stats:read")));

        R result = AgentScopeGuard.requireScope("forwards:write");

        assertEquals(403, result.getCode());
        assertEquals("缺少所需权限: forwards:write", result.getMsg());
    }

    @Test
    void requireAnyScopeShouldPassWhenAgentHasOneRequiredScope() {
        ActorContextHolder.set(new ActorContext(ActorType.AGENT, null, null, null, 9L, "claw", Set.of("forwards:control")));

        R result = AgentScopeGuard.requireAnyScope("forwards:write", "forwards:control");

        assertNull(result);
    }
}
