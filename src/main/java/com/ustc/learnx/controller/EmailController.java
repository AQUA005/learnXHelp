package com.ustc.learnx.controller;

import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UserRepository;
import com.ustc.learnx.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sends mail from the institution's address to a member of the university.
 *
 * <p>Recipients are limited to registered accounts within the sender's own
 * university. Any address used to be accepted, which made this a way to send
 * mail from the institution's identity to anyone at all.
 */
@RestController
@RequestMapping("/api/mail")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmailController {

    private final com.ustc.learnx.service.MailService mailService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}")
    private String host;

    public record SendMailRequest(
            @NotBlank(message = "Recipient is required")
            @Email(message = "Recipient must be a valid email address")
            String to,

            @NotBlank(message = "Subject is required")
            @Size(max = 255, message = "Subject is too long")
            String subject,

            @NotBlank(message = "Message body is required")
            @Size(max = 20000, message = "Message body is too long")
            String body) {
    }

    public record RecipientResponse(String fullName, String email, String role) {
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMail(@Valid @RequestBody SendMailRequest request) {
        User sender = currentUserService.requireCurrentUser();

        User recipient = userRepository.findByEmail(request.to().trim())
                .orElseThrow(() -> new ValidationException(
                        "That address does not belong to a registered member of this university"));
        currentUserService.assertSameUniversity(recipient.getUniversity());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient.getEmail());
        message.setSubject(request.subject());
        message.setText(request.body()
                + "\n\n---\nSent by: " + sender.getFullName() + " via LearnX");

        // MailService logs and audits the reason. It is not returned here,
        // because it can name the mail host and the credentials.
        if (!mailService.send(message)) {
            return ResponseEntity.status(502).body(Map.of(
                    "message", "The message could not be delivered. Check the mail settings."));
        }
        return ResponseEntity.ok(Map.of("message", "Email sent successfully!"));
    }

    public record MailStatusResponse(boolean configured, String from, String host) {
    }

    /** Whether mail is set up, so the administrator can see it before relying on it. */
    @GetMapping("/status")
    public ResponseEntity<MailStatusResponse> status() {
        return ResponseEntity.ok(new MailStatusResponse(
                mailService.isConfigured(), mailService.from(), host));
    }

    /**
     * Sends a message to the administrator asking for it.
     *
     * <p>Mail configuration goes wrong quietly: a relay that will not send from
     * the configured address, or a domain missing its SPF and DKIM records,
     * both look like everything working until somebody needs a recovery code.
     * This proves it end to end, and returns the mail server's own words when it
     * does not.
     */
    @PostMapping("/test")
    public ResponseEntity<?> sendTest() {
        User admin = currentUserService.requireCurrentUser();
        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            throw new ValidationException("Your account has no email address to send to");
        }

        String failure = mailService.sendAndDescribeFailure(
                admin.getEmail(),
                "LearnX test message",
                "This is a test from LearnX.\n\n"
                        + "If you are reading it, sign-up notices and password recovery will "
                        + "reach your students.\n\n"
                        + "Check that it did not land in spam. If it did, add the SPF and DKIM "
                        + "records your mail provider gives you to the domain this was sent "
                        + "from.\n");

        if (failure != null) {
            return ResponseEntity.status(502).body(Map.of(
                    "message", "The message could not be sent.",
                    "reason", failure));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Sent to " + admin.getEmail()
                        + ". If it does not arrive, check the spam folder."));
    }

    /** Addresses this administrator may write to. */
    @GetMapping("/users")
    public ResponseEntity<List<RecipientResponse>> listUsers() {
        Long universityId = currentUserService.requireUniversityId();

        List<RecipientResponse> recipients = userRepository.findByUniversity_Id(universityId).stream()
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .map(u -> new RecipientResponse(
                        u.getFullName() == null ? "" : u.getFullName(),
                        u.getEmail(),
                        u.getRole() == null ? "USER" : u.getRole().name()))
                .toList();

        return ResponseEntity.ok(recipients);
    }
}
