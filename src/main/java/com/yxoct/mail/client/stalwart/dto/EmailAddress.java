package com.yxoct.mail.client.stalwart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailAddress(String name, String email) {}
