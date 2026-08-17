package com.yxoct.mail.client.stalwart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JmapSession(
    Map<String, Object> accounts,
    Map<String, String> primaryAccounts,
    String username,
    URI apiUrl,
    String downloadUrl,
    String uploadUrl,
    String eventSourceUrl,
    String state) {}
