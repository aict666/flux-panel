package com.admin.config;

import com.admin.common.interceptor.AgentApiInterceptor;
import com.admin.service.AgentApiAuditLogService;
import com.admin.service.AgentApiKeyService;
import com.admin.service.AgentClientService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WebMvcConfigContextTest {

    @Test
    void contextShouldRefreshWithWebMvcConfigAndAgentInterceptor() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfig.class, AgentApiInterceptor.class, MockServicesConfig.class);

        try {
            assertDoesNotThrow(context::refresh);
        } finally {
            context.close();
        }
    }

    @Configuration
    static class MockServicesConfig {
        @Bean
        AgentApiKeyService agentApiKeyService() {
            return Mockito.mock(AgentApiKeyService.class);
        }

        @Bean
        AgentClientService agentClientService() {
            return Mockito.mock(AgentClientService.class);
        }

        @Bean
        AgentApiAuditLogService agentApiAuditLogService() {
            return Mockito.mock(AgentApiAuditLogService.class);
        }
    }
}
