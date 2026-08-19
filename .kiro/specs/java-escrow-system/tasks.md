# Implementation Plan: Java Escrow System

## Overview

Implement a Spring Boot REST application that brokers secure financial transactions between Buyer and Seller parties using an H2 in-memory database. The implementation follows the layered architecture defined in the design: Controllers → Services → Repositories → H2. Tasks are ordered so each step builds on the previous, with no orphaned code.

## Tasks

- [ ] 1. Set up Maven project structure and core dependencies
  - Generate a Spring Boot 3.x Maven project with `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `com.h2database:h2`, and `spring-boot-starter-test`
  - Add `net.jqwik:jqwik:1.8.4` (test scope) to `pom.xml`
  - Configure `src/main/resources/application.properties`: datasource url `jdbc:h2:mem:escrowdb`, `ddl-auto=create-drop`, enable H2 console at `/h2-console`
  - Create `src/test/resources/application-test.yml` with `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`, `ddl-auto=create-drop`, `show-sql=false`
  - Create the top-level package `com.escrow` with sub-packages: `controller`, `service`, `repository`, `model`, `dto`, `exception`, `scheduler`
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [ ] 2. Define enumerations and domain entities
  - [ ] 2.1 Create enumerations
    - Create `UserRole { BUYER, SELLER, ARBITRATOR }` in `model`
    - Create `TransactionStatus { PENDING, FUNDED, COMPLETED, REFUNDED, DISPUTED }` in `model`
    - Create `EscrowStatus { LOCKED, RELEASED }` in `model`
    - Create `DisputeStatus { OPEN, IN_PROGRESS, RESOLVED }` in `model`
    - Create `DisputeResolution { RELEASE, REFUND }` in `model`
    - _Requirements: 9.1_

  - [ ] 2.2 Create `User` JPA entity
    - Annotate with `@Entity @Table(name = "users")`
    - Fields: `id` (`@Id @GeneratedValue IDENTITY`), `name` (`@Column(nullable=false, length=100)`), `role` (`@Enumerated(STRING) @Column(nullable=false)`), `balance` (`@Column(nullable=false, precision=15, scale=2)`)
    - Generate getters/setters and a no-arg constructor
    - _Requirements: 1.1_

  - [ ] 2.3 Create `Transaction` JPA entity
    - Annotate with `@Entity @Table(name = "transactions")`
    - Fields: `id`, `buyer` (`@ManyToOne(optional=false)`), `seller` (`@ManyToOne(optional=false)`), `arbitrator` (`@ManyToOne` nullable), `amount`, `status` (`@Enumerated(STRING)`), `deadline` (`Instant`), `createdAt` (`@Column(updatable=false)`)
    - Set `createdAt` via `@PrePersist`
    - _Requirements: 2.1, 9.5_

  - [ ] 2.4 Create `EscrowAccount` JPA entity
    - Annotate with `@Entity @Table(name = "escrow_accounts")`
    - Fields: `id`, `transaction` (`@OneToOne(optional=false)`), `lockedAmount`, `status` (`EscrowStatus`)
    - _Requirements: 3.1_

  - [ ] 2.5 Create `Dispute` JPA entity
    - Annotate with `@Entity @Table(name = "disputes")`
    - Fields: `id`, `transaction` (`@OneToOne(optional=false)`), `raisedBy` (`@ManyToOne(optional=false)`), `reason` (`@Column(length=1000)`), `status` (`DisputeStatus`), `resolution` (`DisputeResolution`, nullable), `filedAt` (`@Column(updatable=false)`), `resolvedAt` (nullable)
    - Set `filedAt` via `@PrePersist`
    - _Requirements: 6.1_

- [ ] 3. Create Spring Data JPA repositories
  - [ ] 3.1 Create `UserRepository extends JpaRepository<User, Long>`
    - _Requirements: 1.1, 1.6, 1.7_

  - [ ] 3.2 Create `TransactionRepository extends JpaRepository<Transaction, Long>`
    - Add `List<Transaction> findByBuyerIdOrSellerIdOrArbitratorId(Long b, Long s, Long a)`
    - Add `List<Transaction> findByStatusAndDeadlineBefore(TransactionStatus status, Instant deadline)`
    - _Requirements: 2.10, 2.12, 5.1_

  - [ ] 3.3 Create `EscrowAccountRepository extends JpaRepository<EscrowAccount, Long>`
    - Add `Optional<EscrowAccount> findByTransactionId(Long txId)`
    - _Requirements: 3.1_

  - [ ] 3.4 Create `DisputeRepository extends JpaRepository<Dispute, Long>`
    - Add `Optional<Dispute> findByTransactionId(Long txId)`
    - Add `boolean existsByTransactionIdAndStatusIn(Long txId, List<DisputeStatus> statuses)`
    - _Requirements: 6.1, 6.7_

  - [ ]* 3.5 Write repository integration tests (`@DataJpaTest`)
    - Test `TransactionRepository.findByBuyerIdOrSellerIdOrArbitratorId` returns correct subset
    - Test `TransactionRepository.findByStatusAndDeadlineBefore` returns only FUNDED + overdue rows
    - Test `DisputeRepository.existsByTransactionIdAndStatusIn` returns true/false correctly
    - Test `EscrowAccountRepository.findByTransactionId` returns correct escrow
    - _Requirements: 2.12, 5.1, 6.7_

- [ ] 4. Define request/response DTOs with Bean Validation
  - [ ] 4.1 Create request DTOs
    - `CreateUserRequest`: `name` (`@NotBlank @Size(max=100)`), `role` (`@NotNull UserRole`), `balance` (`@NotNull @DecimalMin("0.00") @DecimalMax("999999999.99")`)
    - `CreateTransactionRequest`: `buyerId` (`@NotNull`), `sellerId` (`@NotNull`), `arbitratorId` (optional), `amount` (`@NotNull @DecimalMin("0.01") @DecimalMax("999999999.99")`), `deadline` (`@NotNull`, custom `@FutureByOneMinute` validator)
    - `FundTransactionRequest`: `requestingUserId` (`@NotNull`)
    - `ConfirmTransactionRequest`: `requestingUserId` (`@NotNull`)
    - `FileDisputeRequest`: `raisedByUserId` (`@NotNull`), `reason` (`@NotBlank @Size(max=1000)`)
    - `ResolveDisputeRequest`: `resolution` (`@NotNull DisputeResolution`)
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 2.3, 2.8, 2.9, 6.5, 7.4_

  - [ ] 4.2 Create custom deadline validator `@FutureByOneMinute` and its `ConstraintValidator` implementation
    - Validates that `Instant` value is strictly more than 1 minute from `Instant.now()`
    - _Requirements: 2.9_

- [ ] 5. Implement the exception hierarchy and global exception handler
  - [ ] 5.1 Create exception classes
    - Base: `EscrowApplicationException extends RuntimeException`
    - `NotFoundException extends EscrowApplicationException`
    - `UserNotFoundException`, `TransactionNotFoundException`, `DisputeNotFoundException` (all extend `NotFoundException`)
    - `BusinessRuleException extends EscrowApplicationException`
    - `InsufficientFundsException`, `InvalidTransactionStatusException`, `DisputeWindowClosedException`, `InvalidRoleException` (all extend `BusinessRuleException`)
    - `AccessDeniedException extends EscrowApplicationException`
    - `UnauthorizedOperationException extends AccessDeniedException`
    - `ConflictException extends EscrowApplicationException`
    - `DisputeAlreadyExistsException`, `DisputeAlreadyResolvedException` (extend `ConflictException`)
    - `UnprocessableEntityException extends EscrowApplicationException`
    - `TransactionNotResolvableException extends UnprocessableEntityException`
    - _Requirements: 3.2, 3.5, 4.2, 4.3, 4.4, 4.5, 6.2, 6.3, 6.4, 6.6, 7.3, 7.5, 7.6_

  - [ ] 5.2 Create `GlobalExceptionHandler` (`@RestControllerAdvice`)
    - Map each exception type to its HTTP status per the design's exception-to-HTTP table
    - Return `ErrorResponse` record with `timestamp`, `status`, `error`, `message`, `path` fields
    - For `MethodArgumentNotValidException` return additional `fieldErrors` map
    - Handle `HttpMessageNotReadableException` → 400
    - Catch-all `Exception` → 500
    - _Requirements: 1.2, 1.3, 2.3, 3.2_

- [ ] 6. Implement `UserService` and `UserController`
  - [ ] 6.1 Implement `UserService`
    - `createUser(CreateUserRequest)` — save and return new User
    - `getAllUsers()` — return all from repository
    - `getUserById(Long id)` — return User or throw `UserNotFoundException`
    - _Requirements: 1.1, 1.6, 1.7, 1.8_

  - [ ] 6.2 Implement `UserController` (`@RestController @RequestMapping("/api/users")`)
    - `POST /api/users` → `@Valid` body, call `UserService.createUser`, return 201
    - `GET /api/users` → return 200 list
    - `GET /api/users/{id}` → return 200 user
    - _Requirements: 1.1, 1.6, 1.7_

  - [ ]* 6.3 Write `UserService` unit tests (JUnit 5 + Mockito)
    - Test `createUser` persists and returns correct User
    - Test `getUserById` throws `UserNotFoundException` for missing id
    - _Requirements: 1.1, 1.8_

  - [ ]* 6.4 Write `UserController` tests (`@WebMvcTest`)
    - Test `POST /api/users` happy path returns 201
    - Test `POST /api/users` with blank name returns 400 with field error
    - Test `POST /api/users` with name > 100 chars returns 400
    - Test `POST /api/users` with invalid role returns 400
    - Test `POST /api/users` with balance out of range returns 400
    - Test `GET /api/users/{id}` not found returns 404
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.8_

  - [ ]* 6.5 Write property test `UserPropertyTest` (jqwik)
    - **Property 1: Valid User Creation Round-Trip** — arbitrary valid (name, role, balance) → create → GET by id → same values returned
    - **Validates: Requirements 1.1, 1.7**
    - **Property 2: Blank Name Rejection** — arbitrary whitespace-only name → POST → HTTP 400, no user created
    - **Validates: Requirements 1.2**

- [ ] 7. Implement `EscrowService`
  - [ ] 7.1 Implement `EscrowService`
    - `createEscrow(Transaction tx, BigDecimal amount)` — persist LOCKED `EscrowAccount`; return it
    - `releaseEscrow(EscrowAccount escrow, User recipient)` — add `lockedAmount` to recipient balance; set escrow status RELEASED; save both
    - `refundEscrow(EscrowAccount escrow, User buyer)` — add `lockedAmount` to buyer balance; set escrow status RELEASED; save both
    - All three methods must run within the caller's `@Transactional` boundary
    - _Requirements: 3.1, 4.1, 7.1, 7.2, 9.3, 9.6_

- [ ] 8. Implement `TransactionService` and `TransactionController`
  - [ ] 8.1 Implement `createTransaction` in `TransactionService`
    - Validate buyer role == BUYER (throw `InvalidRoleException`), seller role == SELLER, arbitrator role == ARBITRATOR when present
    - Validate buyer != seller (throw `BusinessRuleException`)
    - Persist and return Transaction with status PENDING
    - _Requirements: 2.1, 2.2, 2.4, 2.5, 2.6, 2.7, 9.5_

  - [ ] 8.2 Implement `getTransactionById` and `getTransactionsByUserId` in `TransactionService`
    - `getTransactionById` throws `TransactionNotFoundException` for missing id
    - `getTransactionsByUserId` delegates to repository's three-way OR query
    - _Requirements: 2.10, 2.11, 2.12_

  - [ ] 8.3 Implement `fundTransaction` in `TransactionService` (`@Transactional`)
    - Guard: status must be PENDING → else `InvalidTransactionStatusException`
    - Guard: requestingUserId must be the Buyer → else `UnauthorizedOperationException`
    - Guard: buyer.balance >= amount → else `InsufficientFundsException`
    - Deduct amount from buyer balance; call `EscrowService.createEscrow`; set status FUNDED
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 3.6, 9.1_

  - [ ] 8.4 Implement `confirmTransaction` in `TransactionService` (`@Transactional`)
    - Guard: requestingUserId must be the Buyer → else `UnauthorizedOperationException`
    - Guard: status must be FUNDED → if COMPLETED or REFUNDED throw `ConflictException`; if DISPUTED throw `ConflictException`; else `InvalidTransactionStatusException`
    - Call `EscrowService.releaseEscrow` to Seller; set status COMPLETED
    - _Requirements: 4.1, 4.2, 4.4, 4.5, 4.6, 9.1, 9.6_

  - [ ] 8.5 Implement `TransactionController` (`@RestController @RequestMapping("/api/transactions")`)
    - `POST /api/transactions` → create, return 201
    - `GET /api/transactions/{id}` → return 200
    - `GET /api/transactions?userId={id}` → return 200 list
    - `POST /api/transactions/{id}/fund` → `@Valid` body, return 200
    - `POST /api/transactions/{id}/confirm` → `@Valid` body, return 200
    - `POST /api/transactions/{id}/dispute` → `@Valid` body, return 201 (delegated to dispute flow — wired in task 9)
    - _Requirements: 2.1, 2.10, 2.12, 3.1, 4.1_

  - [ ]* 8.6 Write `TransactionService` unit tests (JUnit 5 + Mockito)
    - Test each forbidden state machine transition is rejected (one test per disallowed transition)
    - Test insufficient funds leaves buyer balance, transaction status, and escrow state unchanged
    - Test non-buyer attempting to fund is rejected with 403
    - Test non-buyer attempting to confirm is rejected with 403
    - Test terminal state (COMPLETED/REFUNDED) operations are rejected
    - _Requirements: 3.2, 3.3, 3.5, 4.2, 4.4, 4.5, 9.1, 9.2, 9.4_

  - [ ]* 8.7 Write `TransactionController` tests (`@WebMvcTest`)
    - Test `POST /api/transactions` happy path returns 201
    - Test `POST /api/transactions` missing required fields returns 400 with field-level detail
    - Test `POST /api/transactions` invalid deadline returns 400
    - Test `GET /api/transactions/{id}` not found returns 404
    - Test `POST /api/transactions/{id}/fund` happy path returns 200
    - Test `POST /api/transactions/{id}/confirm` happy path returns 200
    - _Requirements: 2.3, 2.9, 2.11_

  - [ ]* 8.8 Write property tests for transactions (`TransactionPropertyTest`, jqwik)
    - **Property 3: New Transactions Always Start as PENDING** — arbitrary valid inputs → created transaction has status PENDING and correct amount
    - **Validates: Requirements 2.1, 9.5**
    - **Property 4: Transaction Lookup Round-Trip** — arbitrary created transaction → GET by id → same buyerId, sellerId, amount, deadline, status
    - **Validates: Requirements 2.10**
    - **Property 5: User Transaction Filter Completeness** — arbitrary user + set of transactions → GET by userId returns exactly those where user is buyer/seller/arbitrator
    - **Validates: Requirements 2.12**

  - [ ]* 8.9 Write property tests for funding (`FundingPropertyTest`, jqwik)
    - **Property 6: Funding Produces Correct State Changes** — arbitrary PENDING tx where buyer.balance >= amount → after fund: buyer balance decreases by amount, escrow LOCKED with correct amount, status FUNDED
    - **Validates: Requirements 3.1**
    - **Property 7: Insufficient Funds Leaves All State Unchanged** — arbitrary tx where buyer.balance < amount → fund rejected, buyer balance/tx status/escrow state unchanged
    - **Validates: Requirements 3.2**

  - [ ]* 8.10 Write property test for confirmation (`ConfirmationPropertyTest`, jqwik)
    - **Property 8: Buyer Confirmation Transfers Escrow to Seller** — arbitrary FUNDED tx with LOCKED escrow amount A → after confirm: seller balance increases by A, escrow RELEASED, status COMPLETED
    - **Validates: Requirements 4.1, 9.6**

- [ ] 9. Checkpoint — Ensure all tests pass up to this point
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Implement `DisputeService` and `DisputeController`
  - [ ] 10.1 Implement `fileDispute` in `DisputeService` (called from `TransactionService`)
    - Wire `fileDispute(Long txId, Long raisedByUserId, String reason)` in `TransactionService` (`@Transactional`)
    - Guard: status must be FUNDED → else `InvalidTransactionStatusException`
    - Guard: `Instant.now() < tx.deadline` → else `DisputeWindowClosedException`
    - Guard: raisedByUserId must be Buyer or Seller → else `UnauthorizedOperationException`
    - Guard: no existing DISPUTED status or active dispute → else `DisputeAlreadyExistsException`
    - Call `DisputeService.createDispute`; set transaction status DISPUTED
    - Wire `POST /api/transactions/{id}/dispute` in `TransactionController` to this flow, returning 201 with created Dispute
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 9.1_

  - [ ] 10.2 Implement `resolveDispute` in `DisputeService` (`@Transactional`)
    - `resolveDispute(Long disputeId, DisputeResolution resolution)`
    - Guard: dispute.status != RESOLVED → else `DisputeAlreadyResolvedException`
    - Guard: transaction.status == DISPUTED → else `TransactionNotResolvableException`
    - For RELEASE: call `EscrowService.releaseEscrow` to Seller; set transaction COMPLETED
    - For REFUND: call `EscrowService.refundEscrow` to Buyer; set transaction REFUNDED
    - Set dispute status RESOLVED, record resolution and `resolvedAt`
    - _Requirements: 7.1, 7.2, 7.3, 7.6, 7.7_

  - [ ] 10.3 Implement `DisputeController` (`@RestController @RequestMapping("/api/disputes")`)
    - `GET /api/disputes` → return 200 list
    - `GET /api/disputes/{id}` → return 200 dispute (include id, transactionId, status, reason, filedAt, resolvedAt)
    - `POST /api/disputes/{id}/resolve` → `@Valid` body, call `DisputeService.resolveDispute`, return 200
    - _Requirements: 8.1, 8.2, 8.3, 7.1, 7.2_

  - [ ]* 10.4 Write `DisputeService` unit tests (JUnit 5 + Mockito)
    - Test dispute filed after deadline is rejected (400)
    - Test dispute filed on non-FUNDED transaction is rejected (400)
    - Test dispute filed by non-buyer/non-seller is rejected (403)
    - Test duplicate dispute is rejected (409)
    - Test RELEASE resolution credits seller, sets statuses
    - Test REFUND resolution credits buyer, sets statuses
    - Test resolving already-resolved dispute returns 409
    - Test resolving dispute on non-DISPUTED transaction returns 422
    - _Requirements: 6.2, 6.3, 6.4, 6.6, 7.1, 7.2, 7.3, 7.6_

  - [ ]* 10.5 Write `DisputeController` tests (`@WebMvcTest`)
    - Test `GET /api/disputes/{id}` returns full dispute record including resolvedAt when resolved
    - Test `GET /api/disputes/{id}` not found returns 404
    - Test `POST /api/disputes/{id}/resolve` with RELEASE returns 200
    - Test `POST /api/disputes/{id}/resolve` with invalid resolution returns 400
    - _Requirements: 7.4, 8.2, 8.3_

  - [ ]* 10.6 Write property tests for dispute filing (`DisputePropertyTest`, jqwik)
    - **Property 11: Dispute Filing Produces Correct Initial State** — arbitrary FUNDED tx before deadline, raiser is buyer or seller → dispute created with status OPEN, transaction status DISPUTED
    - **Validates: Requirements 6.1**

  - [ ]* 10.7 Write property tests for dispute resolution (`DisputeResolutionPropertyTest`, jqwik)
    - **Property 12: RELEASE Resolution Credits Seller** — arbitrary DISPUTED tx with LOCKED escrow amount A → resolve RELEASE → seller balance += A, escrow RELEASED, tx COMPLETED, dispute RESOLVED with RELEASE
    - **Validates: Requirements 7.1**
    - **Property 13: REFUND Resolution Credits Buyer** — arbitrary DISPUTED tx with LOCKED escrow amount A → resolve REFUND → buyer balance += A, escrow RELEASED, tx REFUNDED, dispute RESOLVED with REFUND
    - **Validates: Requirements 7.2**

- [ ] 11. Implement `SchedulerService`
  - [ ] 11.1 Implement `SchedulerService` with `@Scheduled(fixedDelay = 60_000)`
    - Query `TransactionRepository.findByStatusAndDeadlineBefore(FUNDED, Instant.now())`
    - For each candidate: check `DisputeRepository.existsByTransactionIdAndStatusIn(id, [OPEN, IN_PROGRESS])`
    - If no active dispute: call `TransactionService.autoRelease(tx)` within `@Transactional`
    - If active dispute: skip (log DEBUG)
    - Wrap each transaction's processing in try-catch; log ERROR with transaction id on failure; continue
    - Enable scheduling with `@EnableScheduling` on the main application class
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 6.7_

  - [ ] 11.2 Implement `autoRelease(Transaction tx)` in `TransactionService` (`@Transactional`)
    - Guard: status must still be FUNDED (idempotency guard) → log and return if already COMPLETED
    - Retrieve EscrowAccount by transaction id
    - Call `EscrowService.releaseEscrow` to Seller; set transaction status COMPLETED
    - _Requirements: 5.2, 5.4, 5.6_

  - [ ] 11.3 Extract `isEligible(Transaction tx, boolean hasActiveDispute)` as a package-visible static method on `SchedulerService`
    - Returns `true` iff `tx.status == FUNDED && tx.deadline.isBefore(Instant.now()) && !hasActiveDispute`
    - This pure method is the seam for property-based testing without triggering the scheduler timer
    - _Requirements: 5.1, 6.7_

  - [ ]* 11.4 Write `SchedulerService` unit tests (JUnit 5 + Mockito)
    - Test disputed transaction is not auto-released
    - Test FUNDED transaction past deadline with no dispute IS auto-released
    - Test FUNDED transaction before deadline is NOT released
    - Test non-FUNDED transaction is not processed by scheduler
    - Test error in one transaction does not prevent processing of remaining transactions
    - _Requirements: 5.1, 5.5, 5.6, 6.7_

  - [ ]* 11.5 Write property tests for scheduler (`SchedulerPropertyTest`, jqwik)
    - **Property 9: Scheduler Eligibility Correctness** — arbitrary collection of transactions with mixed statuses/deadlines/disputes → filtered set via `isEligible` equals manually computed expected set
    - **Validates: Requirements 5.1, 6.7**
    - **Property 10: Scheduler Auto-Release Idempotence** — arbitrary eligible transaction, call `autoRelease` twice → second call is a no-op; no balance or escrow state changes after first call
    - **Validates: Requirements 5.6**

- [ ] 12. Implement state machine and balance conservation property tests
  - [ ] 12.1 Write `StateMachinePropertyTest` (jqwik)
    - **Property 14: Valid State Machine Transitions Only** — arbitrary transaction status + arbitrary disallowed operation → operation rejected with HTTP 4xx, transaction status unchanged
    - Enumerate all disallowed (status, operation) pairs from the state machine table
    - **Validates: Requirements 9.1, 9.2**

  - [ ] 12.2 Write `TerminalStatePropertyTest` (jqwik)
    - **Property 16: Terminal State Immutability** — arbitrary COMPLETED or REFUNDED transaction + arbitrary mutation (fund, confirm, dispute, resolve) → operation rejected with HTTP 409, all state unchanged
    - **Validates: Requirements 9.4**

  - [ ] 12.3 Write `BalanceConservationPropertyTest` (jqwik, `@Property(tries = 500)`)
    - **Property 15: Balance Conservation Invariant** — generate arbitrary set of Users with random balances, compute `initialTotal = sum(balances)`; generate valid sequence of operations (create tx, fund, confirm/dispute, resolve); after each operation assert `sum(currentBalances) + sum(lockedEscrowAmounts) == initialTotal`
    - Use `BigDecimal` arithmetic with `HALF_UP` rounding throughout
    - **Validates: Requirements 9.3**

- [ ] 13. Write full end-to-end integration tests (`@SpringBootTest`)
  - [ ]* 13.1 Write integration test: create users → create transaction → fund → confirm (happy path)
    - Assert buyer balance deducted, seller balance credited, escrow RELEASED, transaction COMPLETED
    - _Requirements: 3.1, 4.1, 9.3_

  - [ ]* 13.2 Write integration test: create users → create transaction → fund → dispute → resolve RELEASE
    - Assert seller receives funds, escrow RELEASED, transaction COMPLETED, dispute RESOLVED
    - _Requirements: 6.1, 7.1_

  - [ ]* 13.3 Write integration test: create users → create transaction → fund → dispute → resolve REFUND
    - Assert buyer receives refund, escrow RELEASED, transaction REFUNDED, dispute RESOLVED
    - _Requirements: 6.1, 7.2_

  - [ ]* 13.4 Write integration test: scheduler auto-release flow
    - Create users and transaction with past deadline, fund it, trigger `SchedulerService` method directly, assert transaction COMPLETED and seller credited
    - _Requirements: 5.1, 5.2_

- [ ] 14. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints at tasks 9 and 14 provide incremental validation gates
- Property tests use jqwik 1.8.4; each property is annotated with its design property number and the requirements clause it validates
- Unit tests use JUnit 5 + Mockito; controller tests use `@WebMvcTest`; repository tests use `@DataJpaTest`
- All multi-step mutations must be within `@Transactional` boundaries — never mix partial saves across transaction boundaries
- `autoRelease` includes an idempotency guard so a race between scheduler and buyer confirmation is safe
- `SchedulerService.isEligible` is a pure static method extracted specifically to enable property-based testing without a running scheduler

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4", "2.5"] },
    { "id": 3, "tasks": ["3.1", "3.2", "3.3", "3.4"] },
    { "id": 4, "tasks": ["4.1", "4.2", "5.1", "3.5"] },
    { "id": 5, "tasks": ["5.2"] },
    { "id": 6, "tasks": ["6.1", "7.1"] },
    { "id": 7, "tasks": ["6.2", "8.1", "8.2"] },
    { "id": 8, "tasks": ["6.3", "6.4", "6.5", "8.3"] },
    { "id": 9, "tasks": ["8.4", "8.8", "8.9"] },
    { "id": 10, "tasks": ["8.5", "8.6", "8.7", "8.10"] },
    { "id": 11, "tasks": ["10.1"] },
    { "id": 12, "tasks": ["10.2", "10.3", "10.6"] },
    { "id": 13, "tasks": ["10.4", "10.5", "10.7", "11.3"] },
    { "id": 14, "tasks": ["11.1", "11.2"] },
    { "id": 15, "tasks": ["11.4", "11.5"] },
    { "id": 16, "tasks": ["12.1", "12.2", "12.3"] },
    { "id": 17, "tasks": ["13.1", "13.2", "13.3", "13.4"] }
  ]
}
```
