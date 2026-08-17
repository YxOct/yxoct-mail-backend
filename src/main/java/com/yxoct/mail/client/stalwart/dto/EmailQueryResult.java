package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record EmailQueryResult(int position, Integer total, List<String> ids) {}
