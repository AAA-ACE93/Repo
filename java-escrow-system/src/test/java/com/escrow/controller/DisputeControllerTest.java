package com.escrow.controller;

import com.escrow.exception.DisputeNotFoundException;
import com.escrow.exception.GlobalExceptionHandler;
import com.escrow.model.*;
import com.escrow.service.DisputeService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DisputeController.class)
@Import(GlobalExceptionHandler.class)
class DisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DisputeService disputeService;

    private Dispute sampleDispute(DisputeStatus status) {
        User buyer = new User("Alice", UserRole.BUYER, BigDecimal.ZERO);
        buyer.setId(1L);
        User seller = new User("Bob", UserRole.SELLER, BigDecimal.ZERO);
        seller.setId(2L);
        Transaction tx = new Transaction();
        tx.setId(10L);
        tx.setBuyer(buyer);
        tx.setSeller(seller);
        tx.setAmount(new BigDecimal("200.00"));
        tx.setStatus(TransactionStatus.DISPUTED);
        tx.setDeadline(Instant.now().plusSeconds(3600));

        Dispute d = new Dispute();
        d.setId(1L);
        d.setTransaction(tx);
        d.setRaisedBy(buyer);
        d.setReason("Test");
        d.setStatus(status);
        return d;
    }

    @Test
    void getDisputeById_returnsFullRecord() throws Exception {
        Dispute d = sampleDispute(DisputeStatus.RESOLVED);
        d.setResolution(DisputeResolution.RELEASE);
        d.setResolvedAt(Instant.now());
        when(disputeService.getDisputeById(1L)).thenReturn(d);

        mockMvc.perform(get("/api/disputes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void getDisputeById_notFound_returns404() throws Exception {
        when(disputeService.getDisputeById(99L)).thenThrow(new DisputeNotFoundException(99L));
        mockMvc.perform(get("/api/disputes/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveDispute_withRelease_returns200() throws Exception {
        Dispute resolved = sampleDispute(DisputeStatus.RESOLVED);
        resolved.setResolution(DisputeResolution.RELEASE);
        resolved.setResolvedAt(Instant.now());
        when(disputeService.resolveDispute(anyLong(), any())).thenReturn(resolved);

        Map<String, Object> body = Map.of("resolution", "RELEASE");
        mockMvc.perform(post("/api/disputes/1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void resolveDispute_withInvalidResolution_returns400() throws Exception {
        String json = "{\"resolution\":\"INVALID\"}";
        mockMvc.perform(post("/api/disputes/1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }


}
