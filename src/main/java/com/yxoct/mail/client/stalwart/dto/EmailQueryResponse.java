package com.yxoct.mail.client.stalwart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailQueryResponse(List<String> ids, String accountId, String queryState) {}
