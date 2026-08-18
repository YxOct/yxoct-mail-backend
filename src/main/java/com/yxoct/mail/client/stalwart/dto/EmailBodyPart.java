package com.yxoct.mail.client.stalwart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailBodyPart(
    String partId,
    String blobId,
    Long size,
    String name,
    String type,
    String disposition,
    String cid) {

  public EmailBodyPart(String partId) {
    this(partId, null, null, null, null, null, null);
  }
}
