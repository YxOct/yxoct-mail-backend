package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record EmailQueryResult(
    String accountId, String queryState, int position, Integer total, List<String> ids) {}
