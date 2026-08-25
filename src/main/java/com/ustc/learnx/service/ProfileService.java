package com.ustc.learnx.service;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.ProfileChangeRequest;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.ProfileChangeRequestRepository;
import com.ustc.learnx.repository.UserRepository;
import com.ustc.learnx.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Map;

/**
 * Profile changes.
 *
 * <p>A member may change their own display name and avatar directly. Anything
 * that identifies them academically — email, ID number, department, batch,
 * semester, section, designation — is submitted for administrator approval
 * instead of being applied.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    /** Image types accepted for an avatar, mapped to the extension to store. */
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp");

    /** Avatars are small; anything larger is a mistake or an attempt to fill the disk. */
    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private final UserRepository userRepository;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    /** Whether the submitted changes needed approval. */
    public record UpdateOutcome(boolean approvalRequired, String profilePicUrl) {
    }

    @Transactional
    public UpdateOutcome update(String fullName, String profilePicture,
                               String email, String idNo, String department,
                               String batch, String semester, String section,
                               String designation) {
        User user = currentUserService.requireCurrentUser();

        if (fullName != null && !fullName.isBlank() && !fullName.equals(user.getFullName())) {
            if (fullName.length() > 255) {
                throw new ValidationException("Name is too long");
            }
            user.setFullName(fullName.trim());
        }

        if (profilePicture != null && !profilePicture.isBlank()) {
            storeAvatar(user, profilePicture);
        }

        userRepository.save(user);

        boolean approvalRequired = differs(email, user.getEmail())
                || differs(idNo, user.getIdNo())
                || differs(department, user.getDepartment())
                || differs(batch, user.getBatch())
                || differs(semester, user.getSemester())
                || differs(section, user.getSection())
                || differs(designation, user.getDesignation());

        if (approvalRequired) {
            profileChangeRequestRepository.save(ProfileChangeRequest.builder()
                    .user(user)
                    .newFullName(user.getFullName())
                    .newEmail(email)
                    .newIdNo(idNo)
                    .newDepartment(department)
                    .newBatch(batch)
                    .newSemester(semester)
                    .newSection(section)
                    .newDesignation(designation)
                    .build());
        }

        return new UpdateOutcome(approvalRequired, user.getProfilePicUrl());
    }

    /** The stored avatar of a member of the caller's university. */
    @Transactional(readOnly = true)
    public Avatar loadAvatar(Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        currentUserService.assertSameUniversity(owner.getUniversity());

        if (owner.getProfilePicKey() == null) {
            throw new NotFoundException("That user has no profile picture");
        }
        return new Avatar(fileStorageService.load(owner.getProfilePicKey()),
                contentTypeFor(owner.getProfilePicKey()));
    }

    public record Avatar(Resource content, String contentType) {
    }

    /**
     * Decodes a data URL and writes the image to storage.
     *
     * <p>Only a recognised image type is accepted, and only up to a fixed size.
     * The column previously took whatever string was sent, which both allowed
     * unbounded growth and put attacker-controlled text into an {@code img}
     * tag's src attribute.
     */
    private void storeAvatar(User user, String dataUrl) {
        if (!dataUrl.startsWith("data:")) {
            throw new ValidationException("Profile picture must be an uploaded image");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new ValidationException("Profile picture is not a valid image");
        }

        String header = dataUrl.substring(5, comma).toLowerCase();
        if (!header.contains(";base64")) {
            throw new ValidationException("Profile picture must be a base64 encoded image");
        }
        String mimeType = header.substring(0, header.indexOf(';'));
        String extension = ALLOWED_IMAGE_TYPES.get(mimeType);
        if (extension == null) {
            throw new ValidationException("Profile picture must be a PNG, JPEG, GIF or WebP image");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Profile picture is not valid base64 data");
        }
        if (decoded.length > MAX_AVATAR_BYTES) {
            throw new ValidationException("Profile picture must be smaller than 2 MB");
        }

        String previousKey = user.getProfilePicKey();
        FileStorageService.StoredFile stored = fileStorageService.store(decoded, extension);
        user.setProfilePicKey(stored.storageKey());
        user.setProfilePicUrl("/api/profile/avatar/" + user.getId());

        // Only once the new image is safely written.
        if (previousKey != null) {
            fileStorageService.delete(previousKey);
        }
    }

    private static String contentTypeFor(String storageKey) {
        String lower = storageKey.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static boolean differs(String submitted, String current) {
        return submitted != null && !submitted.isBlank() && !submitted.equals(current);
    }
}
