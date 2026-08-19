package com.escrow.service;

import com.escrow.model.EscrowAccount;
import com.escrow.model.EscrowStatus;
import com.escrow.model.Transaction;
import com.escrow.model.User;
import com.escrow.repository.EscrowAccountRepository;
import com.escrow.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * EscrowService runs within the caller's @Transactional boundary.
 * No @Transactional annotation is placed here intentionally.
 */
@Service
public class EscrowService {

    private final EscrowAccountRepository escrowAccountRepository;
    private final UserRepository userRepository;

    public EscrowService(EscrowAccountRepository escrowAccountRepository,
                         UserRepository userRepository) {
        this.escrowAccountRepository = escrowAccountRepository;
        this.userRepository = userRepository;
    }

    public EscrowAccount createEscrow(Transaction tx, BigDecimal amount) {
        EscrowAccount escrow = new EscrowAccount(tx, amount, EscrowStatus.LOCKED);
        return escrowAccountRepository.save(escrow);
    }

    public void releaseEscrow(EscrowAccount escrow, User recipient) {
        recipient.setBalance(recipient.getBalance().add(escrow.getLockedAmount()));
        userRepository.save(recipient);
        escrow.setStatus(EscrowStatus.RELEASED);
        escrowAccountRepository.save(escrow);
    }

    public void refundEscrow(EscrowAccount escrow, User buyer) {
        buyer.setBalance(buyer.getBalance().add(escrow.getLockedAmount()));
        userRepository.save(buyer);
        escrow.setStatus(EscrowStatus.RELEASED);
        escrowAccountRepository.save(escrow);
    }
}
