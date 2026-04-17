package com.admin.common.utils;

import com.admin.common.context.ActorContext;
import com.admin.common.context.ActorContextHolder;
import com.admin.common.lang.R;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public final class AgentScopeGuard {

    public static final String DENIED_STATUS_ATTR = "agent_scope_denied_status";
    public static final String DENIED_MESSAGE_ATTR = "agent_scope_denied_message";

    private AgentScopeGuard() {
    }

    public static R requireScope(String scope) {
        ActorContext actorContext = ActorContextHolder.get();
        if (actorContext == null) {
            return R.err(401, "未认证的调用者");
        }
        if (actorContext.isUser()) {
            return null;
        }
        if (actorContext.hasScope(scope)) {
            return null;
        }
        markDenied("缺少所需权限: " + scope);
        return R.err(403, "缺少所需权限: " + scope);
    }

    public static R requireAnyScope(String... scopes) {
        ActorContext actorContext = ActorContextHolder.get();
        if (actorContext == null) {
            return R.err(401, "未认证的调用者");
        }
        if (actorContext.isUser()) {
            return null;
        }
        for (String scope : scopes) {
            if (actorContext.hasScope(scope)) {
                return null;
            }
        }
        markDenied("缺少所需权限");
        return R.err(403, "缺少所需权限");
    }

    private static void markDenied(String message) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        request.setAttribute(DENIED_STATUS_ATTR, 403);
        request.setAttribute(DENIED_MESSAGE_ATTR, message);
    }
}
