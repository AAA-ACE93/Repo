package com.escrow.property;

import com.escrow.dto.CreateUserRequest;
import com.escrow.model.User;
import com.escrow.model.UserRole;
import com.escrow.repository.UserRepository;
import com.escrow.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Valid User Creation Round-Trip — Validates: Requirements 1.1, 1.7
 * Property 2: Blank Name Rejection — Validates: Requirements 1.2
 */
@SpringBootTest
@ActiveProfiles("test")
class UserPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private static final UserRole[] ROLES = UserRole.values();
    private static final Random RNG = new Random(42L);

    /**
     * Property 1: Valid User Creation Round-Trip
     * Validates: Requirements 1.1, 1.7
     *
     * For any valid (name, role, balance) triple, creating a User and then retrieving
     * it by ID shall return a User with the same name, role, and balance.
     */
    @Test
    @Transactional
    void validUserCreationRoundTrip() {
        for (int i = 0; i < 100; i++) {
            String name = randomName(1, 100);
            UserRole role = ROLES[RNG.nextInt(ROLES.length)];
            BigDecimal balance = randomBalance();

            CreateUserRequest req = new CreateUserRequest(name, role, balance);
            User created = userService.createUser(req);
            User fetched = userService.getUserById(created.getId());

            assertThat(fetched.getName()).as("name round-trip").isEqualTo(name);
            assertThat(fetched.getRole()).as("role round-trip").isEqualTo(role);
            assertThat(fetched.getBalance()).as("balance round-trip").isEqualByComparingTo(balance);
        }
    }

    /**
     * Property 2: Blank Name Rejection
     * Validates: Requirements 1.2
     *
     * For any whitespace-only name, validation must fail.
     */
    @Test
    void blankNameRejection() {
        String[] blanks = {"", " ", "   ", "\t", "\n", "\r\n", "  \t  "};
        jakarta.validation.Validator validator = jakarta.validation.Validation
                .buildDefaultValidatorFactory().getValidator();

        for (String blank : blanks) {
            CreateUserRequest req = new CreateUserRequest(blank, UserRole.BUYER, new BigDecimal("100.00"));
            var violations = validator.validate(req);
            boolean hasNameViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"));
            assertThat(hasNameViolation)
                    .as("Blank name '%s' should fail @NotBlank validation", blank)
                    .isTrue();
        }
    }

    // ---- generators ----

    private String randomName(int minLen, int maxLen) {
        int len = minLen + RNG.nextInt(maxLen - minLen + 1);
        StringBuilder sb = new StringBuilder();
        // First char must not be whitespace
        sb.append((char) ('a' + RNG.nextInt(26)));
        for (int i = 1; i < len; i++) {
            int c = RNG.nextInt(36);
            sb.append(c < 26 ? (char) ('a' + c) : (char) ('0' + c - 26));
        }
        return sb.toString();
    }

    private BigDecimal randomBalance() {
        double raw = RNG.nextDouble() * 999999.99;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
