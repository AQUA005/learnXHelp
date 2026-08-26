package com.ustc.learnx.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * The one place the application sends email.
 *
 * <p>Two things this centralises that were previously repeated at every call
 * site, and wrong in the same way each time.
 *
 * <p><strong>The sender address.</strong> It used to be the SMTP username. That
 * works for a mailbox provider, where the two are the same, but not for a
 * relay: Brevo, SendGrid and the like issue a username such as
 * {@code 8a1b2c001@smtp-brevo.com} and then refuse to send from it, because the
 * sender must be an address you have verified. Configure
 * {@code LEARNX_MAIL_FROM} with that verified address.
 *
 * <p><strong>Failures.</strong> Each call site caught the exception and printed
 * it, so a relay rejecting every message looked exactly like everything working.
 * Failures are now logged at error level and recorded in the audit trail, where
 * an administrator will see them.
 */
@Service
@RequiredArgsConstructor
public class MailService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    /**
     * Absent when no mail server is configured: Spring only creates the sender
     * when spring.mail.host is set, and the application must still start.
     */
    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSender;
    private final AuditService auditService;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${learnx.mail.from:}")
    private String configuredFrom;

    @Value("${learnx.mail.from-name:LearnX}")
    private String fromName;

    /** Whether enough is configured to attempt a send. */
    public boolean isConfigured() {
        return !host.isBlank() && !from().isBlank() && mailSender.getIfAvailable() != null;
    }

    /**
     * The address messages are sent from.
     *
     * <p>Falls back to the SMTP username, which is right for a mailbox provider
     * and wrong for a relay — hence the warning at start-up.
     */
    public String from() {
        return configuredFrom.isBlank() ? username : configuredFrom;
    }

    /** Reports what is configured, so a misconfiguration is visible immediately. */
    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (host.isBlank()) {
            log.warn("""
                    No mail server is configured, so sign-up notices and password \
                    recovery will not be sent. Set SPRING_MAIL_HOST, SPRING_MAIL_PORT, \
                    SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD and LEARNX_MAIL_FROM. \
                    An administrator can still reset a password from Administration > People.""");
            return;
        }
        if (configuredFrom.isBlank()) {
            log.warn("""
                    LEARNX_MAIL_FROM is not set, so mail will be sent from the SMTP \
                    username '{}'. A relay such as Brevo or SendGrid will reject that: \
                    it requires a sender address you have verified with them. Set \
                    LEARNX_MAIL_FROM to that verified address.""", username);
            return;
        }
        log.info("Mail is configured: sending from {} via {}", from(), host);
    }

    /**
     * Sends a message.
     *
     * @return true if the message was handed to the mail server
     */
    public boolean send(String to, String subject, String body) {
        if (!isConfigured()) {
            log.warn("Not sending '{}': no mail server is configured", subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", fromName, from()));
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.getObject().send(message);
            return true;
        } catch (Exception e) {
            // Recorded rather than swallowed: a relay refusing every message used
            // to be indistinguishable from working.
            log.error("Could not send '{}': {}", subject, e.getMessage(), e);
            auditService.record("MAIL", "SEND_FAILED", "system",
                    "Could not send '" + subject + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a message that has already been composed, applying the correct
     * sender address and recording any failure.
     *
     * @return true if the message was handed to the mail server
     */
    public boolean send(SimpleMailMessage message) {
        if (!isConfigured()) {
            log.warn("Not sending '{}': no mail server is configured", message.getSubject());
            return false;
        }
        try {
            message.setFrom(String.format("%s <%s>", fromName, from()));
            mailSender.getObject().send(message);
            return true;
        } catch (Exception e) {
            log.error("Could not send '{}': {}", message.getSubject(), e.getMessage(), e);
            auditService.record("MAIL", "SEND_FAILED", "system",
                    "Could not send '" + message.getSubject() + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a message and reports why it failed.
     *
     * <p>Used by the test-send screen, where the administrator is configuring
     * mail and needs the mail server's own words to fix it.
     */
    public String sendAndDescribeFailure(String to, String subject, String body) {
        if (!isConfigured()) {
            return "No mail server is configured. Set SPRING_MAIL_HOST and LEARNX_MAIL_FROM.";
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", fromName, from()));
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.getObject().send(message);
            return null;
        } catch (Exception e) {
            log.error("Test message to {} failed", to, e);
            return rootCause(e);
        }
    }

    /** The innermost message, which is the one that names the actual problem. */
    private static String rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}
