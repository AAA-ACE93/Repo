package com.escrow.mapper;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.dto.EscrowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscrowMapper {

    private final UserMapper userMapper;

    public EscrowResponse toResponse(EscrowTransaction escrow) {
        if (escrow == null) {
            return null;
        }
        return EscrowResponse.builder()
                .id(escrow.getId())
                .referenceNumber(escrow.getReferenceNumber())
                .buyer(userMapper.toResponse(escrow.getBuyer()))
                .seller(userMapper.toResponse(escrow.getSeller()))
                .amount(escrow.getAmount())
                .currency(escrow.getCurrency())
                .description(escrow.getDescription())
                .terms(escrow.getTerms())
                .status(escrow.getStatus())
                .paymentStatus(escrow.getPaymentStatus())
                .releaseConditions(escrow.getReleaseConditions())
                .expectedCompletionDate(escrow.getExpectedCompletionDate())
                .createdAt(escrow.getCreatedAt())
                .fundedAt(escrow.getFundedAt())
                .releasedAt(escrow.getReleasedAt())
                .completedAt(escrow.getCompletedAt())
                .updatedAt(escrow.getUpdatedAt())
                .build();
    }
}
