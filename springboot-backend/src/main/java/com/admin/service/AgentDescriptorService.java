package com.admin.service;

import java.util.Map;
import java.util.Set;

public interface AgentDescriptorService {

    Map<String, Object> buildDescriptor(String format, Set<String> scopes);
}
