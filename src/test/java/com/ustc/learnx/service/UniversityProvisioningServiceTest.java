package com.ustc.learnx.service;

import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.SystemMetadataRepository;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bringing a university onto the platform.
 *
 * <p>Three things this pins down, each of which was a real gap: the university
 * and its administrator are created together or not at all; a new tenant starts
 * with reference data rather than empty dropdowns; and a tenant with no
 * departments cannot be published, since its signup form would have nothing to
 * offer.
 *
 * <p>Its own database, because it inserts universities and the rest of the suite
 * anchors fixtures to "the first university".
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:learnx-provisioning;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class UniversityProvisioningServiceTest {

    @Autowired private UniversityProvisioningService provisioning;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SystemMetadataRepository systemMetadataRepository;

    private static UniversityProvisioningService.NewUniversity request(String name, String domain, String email) {
        return new UniversityProvisioningService.NewUniversity(
                name, domain, "A description", "contact@" + domain,
                "First Administrator", email, "password1");
    }

    @Test
    void createsTheUniversityItsAdministratorAndItsReferenceData() {
        University created = provisioning.create(
                request("Chittagong Institute", "cit.test", "head@cit.test"), "master");

        assertThat(created.getSlug()).isEqualTo("chittagong-institute");
        assertThat(created.isPublished()).isFalse();

        User admin = userRepository.findByEmail("head@cit.test").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(admin.isApproved()).isTrue();
        assertThat(admin.getUniversity().getId()).isEqualTo(created.getId());
        // Derived from the address, as at signup.
        assertThat(admin.getUsername()).isEqualTo("head");

        // Empty dropdowns are how one class group per spelling of "CSE" happens.
        assertThat(systemMetadataRepository.findByUniversity(created))
                .extracting(SystemMetadata::getType)
                .contains("SEMESTER", "SECTION", "DESIGNATION");
    }

    /** Two universities can easily be given similar names. */
    @Test
    void aSlugCollisionIsResolvedRatherThanFailing() {
        provisioning.create(request("Delta College", "delta-one.test", "a@delta-one.test"), "master");
        University second = provisioning.create(
                request("Delta  College!", "delta-two.test", "b@delta-two.test"), "master");

        assertThat(second.getSlug()).isEqualTo("delta-college-2");
    }

    @Test
    void aWeakAdministratorPasswordIsRefused() {
        var weak = new UniversityProvisioningService.NewUniversity(
                "Weak College", "weak.test", null, null, "Admin", "admin@weak.test", "short");

        assertThatThrownBy(() -> provisioning.create(weak, "master"))
                .isInstanceOf(ValidationException.class);

        assertThat(universityRepository.findByDomain("weak.test")).isEmpty();
    }

    /** Email is the credential, so a duplicate is a sign-in collision. */
    @Test
    void aDuplicateAdministratorAddressIsRefused() {
        provisioning.create(request("First College", "first.test", "shared@example.test"), "master");

        assertThatThrownBy(() -> provisioning.create(
                request("Second College", "second.test", "shared@example.test"), "master"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void publishingIsRefusedUntilThereAreDepartments() {
        University created = provisioning.create(
                request("Blank College", "blank.test", "admin@blank.test"), "master");

        assertThatThrownBy(() -> provisioning.setPublished(created, true, "master"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("department");

        systemMetadataRepository.save(SystemMetadata.builder()
                .type("DEPARTMENT").value("CSE").university(created).build());

        assertThat(provisioning.setPublished(created, true, "master").isPublished()).isTrue();
    }

    /** Taking a university back off the homepage carries no such condition. */
    @Test
    void unpublishingIsAlwaysAllowed() {
        University created = provisioning.create(
                request("Retiring College", "retiring.test", "admin@retiring.test"), "master");
        assertThat(provisioning.setPublished(created, false, "master").isPublished()).isFalse();
    }
}
