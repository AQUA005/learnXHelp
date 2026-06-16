package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.env.Environment;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mail")
@AllArgsConstructor
public class EmailController {

    private final JavaMailSender mailSender;
    private final Environment env;
    private final UserRepository userRepository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMailRequest {
        private String to;
        private String subject;
        private String body;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMail(@RequestBody SendMailRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        if (request.getTo() == null || request.getTo().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient email ('to') is required"));
        }
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject is required"));
        }
        if (request.getBody() == null || request.getBody().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message body is required"));
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String fromEmail = env.getProperty("spring.mail.username");
            if (fromEmail != null && !fromEmail.isEmpty()) {
                message.setFrom(fromEmail);
            }
            message.setTo(request.getTo());
            message.setSubject(request.getSubject());
            message.setText(request.getBody() + "\n\n---\nSent by: " + principal.getName() + " via LearnX");
            
            mailSender.send(message);
            return ResponseEntity.ok(Map.of("message", "Email sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        List<Map<String, String>> users = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && !u.getEmail().trim().isEmpty())
                .map(u -> Map.of(
                        "fullName", u.getFullName() != null ? u.getFullName() : "", 
                        "email", u.getEmail(), 
                        "role", u.getRole() != null ? u.getRole().name() : "USER"
                ))
                .toList();
        return ResponseEntity.ok(users);
    }
}
