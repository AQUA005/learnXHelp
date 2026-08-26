package com.ustc.learnx.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A rejected sign-up or sign-in must say why.
 *
 * <p>These endpoints used to answer {@code {"error": "…"}}. The frontend reads
 * {@code message}, then {@code detail}, then {@code title}; an {@code error} key
 * matches none of them, so every validation failure reached the user as the
 * unhelpful "Request failed (400)" and the real reason was never shown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthErrorBodyTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void aRejectedPasswordSaysWhatIsWrongWithIt() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"universitySlug":"ustc-ac-bd","password":"short","fullName":"Weak Ling",
                                 "email":"weakling@learnx.test","role":"STUDENT","idNo":"1","department":"CSE",
                                 "batch":"Batch 21","semester":"1st Year 1st Semester","section":"Section A"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("8")));
    }

    @Test
    void aMissingFieldIsReportedRatherThanSwallowed() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"universitySlug":"ustc-ac-bd","password":"password1","fullName":"No Fields",
                                 "email":"nofields@learnx.test","role":"STUDENT"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("necessary registration fields")));
    }

    @Test
    void administratorRegistrationIsRefusedWithAReason() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"universitySlug":"ustc-ac-bd","password":"password1","fullName":"Sneaky",
                                 "email":"sneaky@learnx.test","role":"ADMIN","idNo":"1","department":"CSE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("administrator registration is prohibited")));
    }

    /**
     * An unknown account and a wrong password must answer identically, or the
     * response tells an attacker which usernames exist.
     */
    @Test
    void aFailedSignInIs401WithAGenericReason() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-at-all@learnx.test\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
