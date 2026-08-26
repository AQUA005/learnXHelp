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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);

    private final JavaMailSender mailSender;
    private final Environment env;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

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

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String fromEmail = env.getProperty("learnx.mail.from");
            if (fromEmail != null && !fromEmail.isEmpty()) {
                message.setFrom(fromEmail);
            }
            message.setTo(recipient.getEmail());
            message.setSubject(request.subject());
            message.setText(request.body()
                    + "\n\n---\nSent by: " + sender.getFullName() + " via LearnX");

            mailSender.send(message);
            return ResponseEntity.ok(Map.of("message", "Email sent successfully!"));
        } catch (Exception e) {
            // The underlying failure can name the mail host and credentials.
            log.error("Failed to send mail to user id {}", recipient.getId(), e);
            return ResponseEntity.status(502)
                    .body(Map.of("message", "The message could not be delivered. Please try again later."));
        }
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
