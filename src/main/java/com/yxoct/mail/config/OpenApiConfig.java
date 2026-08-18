package com.yxoct.mail.config;

import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.common.response.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  @Bean
  OpenAPI mailOpenApi() {
    return new OpenAPI()
        .components(errorComponents())
        .info(
            new Info()
                .title("YxOct Mail API")
                .version("v1")
                .description(
                    "REST API for user access and receiving and managing email through Stalwart JMAP."));
  }

  private Components errorComponents() {
    Components components = new Components();
    ModelConverters.getInstance().read(ApiErrorResponse.class).forEach(components::addSchemas);
    return components
        .addResponses("BadRequest", errorResponse("Invalid request", ErrorCode.BAD_REQUEST))
        .addResponses(
            "InternalError", errorResponse("Unexpected server error", ErrorCode.INTERNAL_ERROR))
        .addResponses("EmailNotFound", errorResponse("Email not found", ErrorCode.EMAIL_NOT_FOUND))
        .addResponses(
            "InvalidInvitationOrRequest",
            errorResponse(
                "Invalid registration request",
                ErrorCode.BAD_REQUEST,
                ErrorCode.INVITATION_INVALID))
        .addResponses(
            "RegistrationConflict",
            errorResponse(
                "Invitation or email address conflict",
                ErrorCode.INVITATION_ALREADY_USED,
                ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE))
        .addResponses(
            "InvitationGone",
            errorResponse(
                "Invitation expired or was revoked",
                ErrorCode.INVITATION_EXPIRED,
                ErrorCode.INVITATION_REVOKED))
        .addResponses(
            "MailboxNotFound", errorResponse("Mailbox not found", ErrorCode.MAILBOX_NOT_FOUND))
        .addResponses(
            "EmailOrAttachmentNotFound",
            errorResponse(
                "Email or attachment not found",
                ErrorCode.EMAIL_NOT_FOUND,
                ErrorCode.ATTACHMENT_NOT_FOUND))
        .addResponses(
            "MailServiceUnavailable",
            errorResponse(
                "Stalwart is unavailable or authentication failed",
                ErrorCode.MAIL_SERVICE_UNAVAILABLE,
                ErrorCode.MAIL_SERVICE_AUTHENTICATION_FAILED))
        .addResponses(
            "MailServiceTimeout",
            errorResponse("Stalwart request timed out", ErrorCode.MAIL_SERVICE_TIMEOUT));
  }

  private ApiResponse errorResponse(String description, ErrorCode... errorCodes) {
    MediaType mediaType =
        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"));
    for (ErrorCode errorCode : errorCodes) {
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("code", errorCode.getCode());
      value.put("message", errorCode.getMessage());
      value.put("data", null);
      mediaType.addExamples(
          "code-" + errorCode.getCode(), new Example().summary(errorCode.name()).value(value));
    }
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType));
  }
}
