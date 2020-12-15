package io.harness.notification.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.notification.constant.NotificationClientConstants.HARNESS_NAME;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.exception.ExceptionUtils;
import io.harness.notification.SmtpConfig;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

@Slf4j
public class MailSenderImpl {
  public boolean send(
      List<String> emailIds, String subject, String body, String notificationId, SmtpConfig smtpConfig) {
    try {
      if (Objects.isNull(stripToNull(body))) {
        log.error("No email body available. Aborting notification request {}", notificationId);
        return false;
      }

      Email email = new HtmlEmail();
      email.setHostName(smtpConfig.getHost());
      email.setSmtpPort(smtpConfig.getPort());

      if (!isEmpty(smtpConfig.getPassword())) {
        email.setAuthenticator(
            new DefaultAuthenticator(smtpConfig.getUsername(), new String(smtpConfig.getPassword())));
      }
      email.setSSLOnConnect(smtpConfig.isUseSSL());
      if (smtpConfig.isUseSSL()) {
        email.setSslSmtpPort(Integer.toString(smtpConfig.getPort()));
      }

      try {
        email.setReplyTo(ImmutableList.of(new InternetAddress(smtpConfig.getFromAddress())));
      } catch (AddressException | EmailException e) {
        log.error(ExceptionUtils.getMessage(e), e);
      }
      email.setFrom(smtpConfig.getFromAddress(), HARNESS_NAME);

      for (String emailId : emailIds) {
        email.addTo(emailId);
      }

      email.setSubject(subject);
      ((HtmlEmail) email).setHtmlMsg(body);
      email.send();
    } catch (EmailException e) {
      log.error("Failed to send email. Check SMTP configuration. notificationId: {}\n{}", notificationId,
          ExceptionUtils.getMessage(e));
      return false;
    }
    return true;
  }
}
