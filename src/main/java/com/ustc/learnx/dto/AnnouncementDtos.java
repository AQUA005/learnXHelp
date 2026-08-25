package com.ustc.learnx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AnnouncementDtos {

    private AnnouncementDtos() {
    }

    public record CreateAnnouncementRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title is too long")
            String title,

            @NotBlank(message = "Content is required")
            @Size(max = 10000, message = "Content is too long")
            String content,

            /**
             * Whether the announcement reaches the whole university. Ignored for
             * a class representative, who may only address their own class.
             */
            boolean global) {
    }

    public record AnnouncementResponse(
            Long id,
            String title,
            String content,
            LocalDateTime createdAt,
            String createdBy,
            String createdByRole,
            Long studentClassId,
            String className) {
    }
}
