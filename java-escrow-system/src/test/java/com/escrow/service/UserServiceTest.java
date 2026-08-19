package com.escrow.service;

import com.escrow.dto.CreateUserRequest;
import com.escrow.exception.UserNotFoundException;
import com.escrow.model.User;
import com.escrow.model.UserRole;
import com.escrow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("Alice", UserRole.BUYER, new BigDecimal("500.00"));
        sampleUser.setId(1L);
    }

    @Test
    void createUser_persistsAndReturnsUser() {
        CreateUserRequest req = new CreateUserRequest("Alice", UserRole.BUYER, new BigDecimal("500.00"));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        User result = userService.createUser(req);

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getRole()).isEqualTo(UserRole.BUYER);
        assertThat(result.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void getUserById_throwsUserNotFoundException_whenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getUserById_returnsUser_whenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        User result = userService.getUserById(1L);
        assertThat(result.getId()).isEqualTo(1L);
    }
}
