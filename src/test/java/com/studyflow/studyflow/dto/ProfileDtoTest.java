package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProfileDto}.
 *
 * Verifies the profile DTO foundation contract:
 *  - safe {@code from(User)} mapping of exactly name + email,
 *  - null handling,
 *  - name editing validation (required, sensible max length),
 *  - the structural guarantee that password / role / userId are NEVER part
 *    of the editable profile DTO.
 */
class ProfileDtoTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private User user() {
        return User.builder()
                .id(7L)
                .name("Iffat")
                .email("iffat@example.com")
                .password("encoded-hash")
                .role(Role.USER)
                .build();
    }

    @Test
    void componentNames_areOnlyNameAndEmail() {
        String[] names = Arrays.stream(ProfileDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
        assertThat(names).containsExactlyInAnyOrder("name", "email");
    }

    @Test
    void from_mapsExactlyNameAndEmail() {
        ProfileDto dto = ProfileDto.from(user());
        assertThat(dto.name()).isEqualTo("Iffat");
        assertThat(dto.email()).isEqualTo("iffat@example.com");
    }

    @Test
    void from_nullUser_mapsToNull() {
        assertThat(ProfileDto.from(null)).isNull();
    }

    @Test
    void validName_noViolations() {
        ProfileDto dto = new ProfileDto("Iffat", "iffat@example.com");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void blankName_isRejected() {
        ProfileDto dto = new ProfileDto("   ", "iffat@example.com");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void overlongName_isRejected() {
        ProfileDto dto = new ProfileDto("n".repeat(101), "iffat@example.com");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void email_carriesNoUpdateValidation_soUnvalidatedEmailPasses() {
        // Email is read-only (login identifier); it must not block a profile
        // update. A blank email value flows through validation untouched.
        ProfileDto dto = new ProfileDto("Iffat", "");
        assertThat(validator.validate(dto)).isEmpty();
    }
}
