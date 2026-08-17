package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record JmapRequest(List<String> using, List<JmapMethodCall> methodCalls) {}
