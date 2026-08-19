package com.escrow.dto;

import com.escrow.model.UserRole;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateUserRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Role is required")
    private UserRole role;

    @NotNull(message = "Balance is required")
    @DecimalMin(value = "0.00", message = "Balance must be at least 0.00")
    @DecimalMax(value = "999999999.99", message = "Balance must not exceed 999999999.99")
    private BigDecimal balance;

    public CreateUserRequest() {}

    public CreateUserRequest(String name, UserRole role, BigDecimal balance) {
        this.name = name;
        this.role = role;
        this.balance = balance;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
