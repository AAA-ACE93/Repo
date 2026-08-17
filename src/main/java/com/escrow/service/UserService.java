package com.escrow.service;

import com.escrow.dto.*;

public interface UserService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void resetPassword(PasswordResetRequest request);
    UserResponse getCurrentUser(String email);
}
