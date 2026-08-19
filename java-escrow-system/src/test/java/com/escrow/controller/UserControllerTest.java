package com.escrow.controller;

import com.escrow.exception.GlobalExceptionHandler;
import com.escrow.exception.UserNotFoundException;
import com.escrow.model.User;
import com.escrow.model.UserRole;
import com.escrow.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void createUser_happyPath_returns201() throws Exception {
        User created = new User("Alice", UserRole.BUYER, new BigDecimal("500.00"));
        created.setId(1L);
        when(userService.createUser(any())).thenReturn(created);

        Map<String, Object> body = Map.of("name", "Alice", "role", "BUYER", "balance", "500.00");
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createUser_blankName_returns400WithFieldError() throws Exception {
        Map<String, Object> body = Map.of("name", "   ", "role", "BUYER", "balance", "500.00");
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createUser_nameTooLong_returns400() throws Exception {
        String longName = "A".repeat(101);
        Map<String, Object> body = Map.of("name", longName, "role", "BUYER", "balance", "500.00");
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_invalidRole_returns400() throws Exception {
        String json = "{\"name\":\"Alice\",\"role\":\"INVALID_ROLE\",\"balance\":\"500.00\"}";
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_balanceOutOfRange_returns400() throws Exception {
        Map<String, Object> body = Map.of("name", "Alice", "role", "BUYER", "balance", "-1.00");
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        when(userService.getUserById(99L)).thenThrow(new UserNotFoundException(99L));
        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound());
    }
}
