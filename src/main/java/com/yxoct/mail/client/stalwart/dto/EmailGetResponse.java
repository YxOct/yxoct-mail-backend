package com.yxoct.mail.client.stalwart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailGetResponse(String accountId, List<Map<String, Object>> list) {}
