package com.escrow.controller;

import com.escrow.exception.GlobalExceptionHandler;
import com.escrow.exception.TransactionNotFoundException;
import com.escrow.model.*;
import com.escrow.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    private Transaction sampleFundedTx() {
        User buyer = new User("Alice", UserRole.BUYER, new BigDecimal("800.00"));
        buyer.setId(1L);
        User seller = new User("Bob", UserRole.SELLER, new BigDecimal("0.00"));
        seller.setId(2L);
        Transaction tx = new Transaction();
        tx.setId(10L);
        tx.setBuyer(buyer);
        tx.setSeller(seller);
        tx.setAmount(new BigDecimal("200.00"));
        tx.setStatus(TransactionStatus.FUNDED);
        tx.setDeadline(Instant.now().plus(2, ChronoUnit.HOURS));
        return tx;
    }

    private Transaction samplePendingTx() {
        Transaction tx = sampleFundedTx();
        tx.setStatus(TransactionStatus.PENDING);
        return tx;
    }

    @Test
    void createTransaction_happyPath_returns201() throws Exception {
        when(transactionService.createTransaction(any())).thenReturn(samplePendingTx());

        Map<String, Object> body = Map.of(
                "buyerId", 1,
                "sellerId", 2,
                "amount", "200.00",
                "deadline", Instant.now().plus(10, ChronoUnit.MINUTES).toString());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void createTransaction_missingRequiredFields_returns400() throws Exception {
        Map<String, Object> body = Map.of("amount", "200.00");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTransactionById_notFound_returns404() throws Exception {
        when(transactionService.getTransactionById(99L)).thenThrow(new TransactionNotFoundException(99L));
        mockMvc.perform(get("/api/transactions/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fundTransaction_happyPath_returns200() throws Exception {
        when(transactionService.fundTransaction(anyLong(), anyLong())).thenReturn(sampleFundedTx());
        Map<String, Object> body = Map.of("requestingUserId", 1);
        mockMvc.perform(post("/api/transactions/10/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void confirmTransaction_happyPath_returns200() throws Exception {
        Transaction completed = sampleFundedTx();
        completed.setStatus(TransactionStatus.COMPLETED);
        when(transactionService.confirmTransaction(anyLong(), anyLong())).thenReturn(completed);
        Map<String, Object> body = Map.of("requestingUserId", 1);
        mockMvc.perform(post("/api/transactions/10/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
