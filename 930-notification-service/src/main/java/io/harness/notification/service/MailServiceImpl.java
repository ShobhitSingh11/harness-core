package io.harness.notification.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.exception.WingsException.USER;

import static freemarker.template.Configuration.VERSION_2_3_23;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.NotificationRequest;
import io.harness.Team;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.MailTaskParams;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.SmtpConfig;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.EmailSettingDTO;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.serializer.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import freemarker.core.InvalidReferenceException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class MailServiceImpl implements ChannelService {
  private final Configuration cfg = new Configuration(VERSION_2_3_23);
  private final SmtpConfig smtpConfigDefault;
  private final NotificationSettingsService notificationSettingsService;
  private final NotificationTemplateService notificationTemplateService;
  private final YamlUtils yamlUtils;
  private final MailSenderImpl mailSender;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Override
  public boolean send(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasEmail()) {
      return false;
    }

    String notificationId = notificationRequest.getId();
    NotificationRequest.Email mailDetails = notificationRequest.getEmail();
    Map<String, String> templateData = mailDetails.getTemplateDataMap();
    String templateId = mailDetails.getTemplateId();

    if (Objects.isNull(stripToNull(templateId))) {
      log.info("template Id is null for notification request {}", notificationId);
      return false;
    }

    List<String> emailIds = resolveRecipients(notificationRequest);
    if (isEmpty(emailIds)) {
      log.info("No recipients found in notification request {}", notificationId);
      return false;
    }

    try {
      String subject = null;
      String body = null;
      Optional<EmailTemplate> emailTemplateOpt = getTemplate(templateId, notificationRequest.getTeam());
      if (!emailTemplateOpt.isPresent()) {
        log.error(
            "Failed to send email for notification request {} possibly due to no valid template with name {} found",
            notificationId, templateId);
        return false;
      }
      EmailTemplate emailTemplate = emailTemplateOpt.get();

      subject = processTemplate(templateId + "-subject", emailTemplate.getSubject(), templateData);
      body = processTemplate(templateId + "-body", emailTemplate.getBody(), templateData);
      return send(emailIds, subject, body, notificationId, notificationRequest.getAccountId());
    } catch (Exception e) {
      log.error("Failed to send email. Check template details for notificationId: {}\n{}", notificationId,
          ExceptionUtils.getMessage(e));
      return false;
    }
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    EmailSettingDTO emailSettingDTO = (EmailSettingDTO) notificationSettingDTO;
    String email = emailSettingDTO.getRecipient();
    if (Objects.isNull(stripToNull(email)) || !EmailValidator.getInstance().isValid(email)) {
      throw new NotificationException("Malformed webhook Url " + email, DEFAULT_ERROR_CODE, USER);
    }
    boolean sent = send(Collections.singletonList(email), emailSettingDTO.getSubject(), emailSettingDTO.getBody(),
        email, notificationSettingDTO.getAccountId());
    if (!sent) {
      throw new NotificationException("Failed to send email. Check SMTP configuration", DEFAULT_ERROR_CODE, USER);
    }
    return true;
  }

  private boolean send(List<String> emailIds, String subject, String body, String notificationId, String accountId) {
    boolean sent = false;
    if (notificationSettingsService.getSendNotificationViaDelegate(accountId)) {
      DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(accountId)
              .taskType("NOTIFY_MAIL")
              .taskParameters(
                  MailTaskParams.builder()
                      .notificationId(notificationId)
                      .subject(subject)
                      .body(body)
                      .emailIds(emailIds)
                      .smtpConfig(notificationSettingsService.getSmtpConfig(accountId).orElse(smtpConfigDefault))
                      .build())
              .executionTimeout(Duration.ofMinutes(1L))
              .build();
      NotificationTaskResponse notificationTaskResponse =
          (NotificationTaskResponse) delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
      sent = notificationTaskResponse.isSent();
    } else {
      SmtpConfig smtpConfig = notificationSettingsService.getSmtpConfig(accountId).orElse(smtpConfigDefault);
      sent = mailSender.send(emailIds, subject, body, notificationId, smtpConfig);
    }
    log.info(sent ? "Notificaition request {} sent" : "Failed to send notification for request {}", notificationId);
    return sent;
  }

  private String processTemplate(String templateName, String templateStr, Map<String, String> templateData) {
    if (Objects.isNull(stripToNull(templateStr))) {
      return null;
    }
    Template template = null;
    try {
      template = new Template(templateName, templateStr, cfg);
    } catch (IOException e) {
      log.error("Can't parse the stored file as .ftl template file");
    }

    if (Objects.isNull(template)) {
      return null;
    }

    Map<String, String> dataMap = new HashMap<>(templateData);
    boolean exceptionCaught;
    StringWriter strWriter;
    do {
      strWriter = new StringWriter();
      exceptionCaught = false;
      try {
        template.process(dataMap, strWriter);
      } catch (InvalidReferenceException e) {
        exceptionCaught = true;
        dataMap.put(e.getBlamedExpressionString(), String.format("${%s}", e.getBlamedExpressionString()));
      } catch (IOException | TemplateException e) {
        log.error("Failed to process template. Check template {}", templateName);
      }
    } while (exceptionCaught);

    return strWriter.toString();
  }

  private Optional<EmailTemplate> getTemplate(String templateId, Team team) {
    Optional<String> templateStrOptional = notificationTemplateService.getTemplateAsString(templateId, team);
    Optional<EmailTemplate> emailTemplateOpt = Optional.empty();
    if (templateStrOptional.isPresent()) {
      try {
        emailTemplateOpt =
            Optional.of(yamlUtils.read(templateStrOptional.get(), new TypeReference<EmailTemplate>() {}));
      } catch (IOException e) {
        log.error("failed to parse template {} into yaml. Please check the format", templateId);
      }
    }
    return emailTemplateOpt;
  }

  private List<String> resolveRecipients(NotificationRequest notificationRequest) {
    NotificationRequest.Email emailDetails = notificationRequest.getEmail();
    List<String> recipients = new ArrayList<>(emailDetails.getEmailIdsList());
    List<String> resolvedRecipients = notificationSettingsService.getNotificationSettingsForGroups(
        emailDetails.getUserGroupIdsList(), NotificationChannelType.EMAIL, notificationRequest.getAccountId());
    recipients.addAll(resolvedRecipients);
    return recipients;
  }

  @Getter
  @Setter
  public static class EmailTemplate {
    private String subject;
    private String body;
  }
}
