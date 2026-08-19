# Requirements Document

## Introduction

The Java Escrow System is a Spring Boot application that facilitates secure financial transactions between parties by holding funds in escrow until agreed conditions are met. The system supports two-party transactions (buyer and seller) and three-party transactions (buyer, seller, and arbitrator). Funds are held in virtual/simulated balances stored in an H2 in-memory database. Funds are released to the seller upon buyer confirmation, automatically released after a deadline if no dispute is raised, or resolved by an arbitrator when a dispute is filed. No authentication is required in this version; all API endpoints are open.

## Glossary

- **System**: The Java Escrow System application as a whole
- **User**: A registered participant in the system with a role of BUYER, SELLER, or ARBITRATOR and a virtual balance
- **Buyer**: A User with role BUYER who initiates a transaction and deposits funds into escrow
- **Seller**: A User with role SELLER who receives funds upon successful transaction completion
- **Arbitrator**: A User with role ARBITRATOR who resolves disputes between Buyer and Seller
- **Transaction**: A record representing an escrow agreement between a Buyer and Seller, optionally involving an Arbitrator
- **EscrowAccount**: A record that holds the locked funds for a given Transaction
- **Dispute**: A record representing a contested Transaction raised by either the Buyer or the Seller before the auto-release deadline
- **Auto-release Deadline**: The scheduled point in time after which funds are automatically released to the Seller if no Dispute has been raised
- **TransactionStatus**: An enumeration of valid Transaction states: PENDING, FUNDED, COMPLETED, REFUNDED, DISPUTED
- **DisputeResolution**: An enumeration of arbitrator decisions: RELEASE (funds go to Seller) or REFUND (funds go to Buyer)
- **SchedulerJob**: The Spring @Scheduled background job responsible for auto-releasing funds past their deadline

---

## Requirements

### Requirement 1: User Management

**User Story:** As a participant, I want to register as a User with a name, role, and starting balance, so that I can take part in escrow transactions.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/users` with a valid name, role, and balance, THE System SHALL create a new User record and return the created User with HTTP 201.
2. IF a POST request to `/api/users` is made with a missing or blank name, THEN THE System SHALL return HTTP 400 with an error message indicating the name field is required.
3. IF a POST request to `/api/users` is made with a name exceeding 100 characters, THEN THE System SHALL return HTTP 400 with an error message indicating the name field maximum length.
4. IF a POST request to `/api/users` is made with a role value outside the set {BUYER, SELLER, ARBITRATOR}, THEN THE System SHALL return HTTP 400 with an error message indicating the valid role values.
5. IF a POST request to `/api/users` is made with a balance value outside the range 0.00–999,999,999.99, THEN THE System SHALL return HTTP 400 with an error message indicating the valid balance range.
6. WHEN a GET request is made to `/api/users`, THE System SHALL return a list of all registered Users with HTTP 200; IF no Users exist, THE System SHALL return an empty list with HTTP 200.
7. WHEN a GET request is made to `/api/users/{id}` with a valid User ID, THE System SHALL return the corresponding User record with HTTP 200.
8. IF a GET request is made to `/api/users/{id}` with a User ID that does not exist in the system, THEN THE System SHALL return HTTP 404 with an error message indicating the specified User ID was not found.

---

### Requirement 2: Transaction Creation

**User Story:** As a Buyer, I want to create a Transaction with a Seller (and optionally an Arbitrator), so that I can initiate an escrow agreement.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/transactions` with all required fields (buyerId, sellerId, amount, deadline) passing individual validation rules, THE System SHALL create a new Transaction with status PENDING and return the created Transaction with HTTP 201.
2. WHEN an arbitratorId is provided in the transaction creation request, THE System SHALL associate the Arbitrator with the Transaction.
3. IF a POST request to `/api/transactions` is made with any required field (buyerId, sellerId, amount, or deadline) absent or null, THEN THE System SHALL return HTTP 400 with field-level detail indicating which fields are missing.
4. IF a POST request to `/api/transactions` is made with a buyerId that does not correspond to a User with role BUYER, THEN THE System SHALL return HTTP 400 with a descriptive error message.
5. IF a POST request to `/api/transactions` is made with a sellerId that does not correspond to a User with role SELLER, THEN THE System SHALL return HTTP 400 with a descriptive error message.
6. IF a POST request to `/api/transactions` is made with buyerId equal to sellerId, THEN THE System SHALL return HTTP 400 with an error message indicating the buyer and seller must be different users.
7. IF a POST request to `/api/transactions` is made with an arbitratorId that does not correspond to a User with role ARBITRATOR, THEN THE System SHALL return HTTP 400 with a descriptive error message.
8. IF a POST request to `/api/transactions` is made with an amount less than or equal to zero or greater than 999,999,999.99, THEN THE System SHALL return HTTP 400 with a descriptive error message.
9. IF a POST request to `/api/transactions` is made with a deadline that is not in ISO 8601 format or is not at least 1 minute in the future, THEN THE System SHALL return HTTP 400 with a descriptive error message.
10. WHEN a GET request is made to `/api/transactions/{id}` with a Transaction ID that exists in the system, THE System SHALL return the corresponding Transaction record with HTTP 200.
11. IF a GET request is made to `/api/transactions/{id}` with a Transaction ID that does not exist in the system, THEN THE System SHALL return HTTP 404 with a descriptive error message.
12. WHEN a GET request is made to `/api/transactions?userId={id}`, THE System SHALL return all Transactions in which the specified User is the Buyer, Seller, or Arbitrator with HTTP 200; IF no matching Transactions exist, THE System SHALL return an empty list with HTTP 200.

---

### Requirement 3: Fund Deposit into Escrow

**User Story:** As a Buyer, I want to deposit funds into the EscrowAccount for a Transaction, so that the Seller has assurance that payment is secured.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/transactions/{id}/fund` for a Transaction with status PENDING and the requesting user is the Buyer of that Transaction, THE System SHALL deduct the Transaction amount from the Buyer's balance, create an EscrowAccount holding the locked amount equal to the Transaction amount, update the Transaction status to FUNDED, and return the updated Transaction with HTTP 200.
2. IF a POST request is made to `/api/transactions/{id}/fund` and the Buyer's balance is less than the Transaction amount, THEN THE System SHALL return HTTP 400 with an error message indicating insufficient funds, and leave the Buyer's balance, the Transaction status, and any EscrowAccount state unchanged.
3. IF a POST request is made to `/api/transactions/{id}/fund` for a Transaction whose status is not PENDING, THEN THE System SHALL return HTTP 400 with an error message indicating the Transaction cannot be funded in its current status.
4. IF a POST request is made to `/api/transactions/{id}/fund` and no Transaction exists with the given id, THEN THE System SHALL return HTTP 404 with an error message indicating the Transaction was not found.
5. IF a POST request is made to `/api/transactions/{id}/fund` and the requesting user is not the Buyer of the Transaction, THEN THE System SHALL return HTTP 403 with an error message indicating the operation is not permitted.
6. THE System SHALL ensure that the balance deduction and EscrowAccount creation during funding occur atomically, so that if any step fails, all changes are rolled back and no partial state is persisted.

---

### Requirement 4: Buyer Confirmation and Fund Release

**User Story:** As a Buyer, I want to confirm delivery of goods or services, so that the escrowed funds are released to the Seller.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/transactions/{id}/confirm` by the Buyer of a Transaction with status FUNDED, THE System SHALL transfer the locked amount from the EscrowAccount to the Seller's balance, update the EscrowAccount status to RELEASED, update the Transaction status to COMPLETED, and return the updated Transaction with HTTP 200.
2. IF a POST request is made to `/api/transactions/{id}/confirm` by a user who is not the Buyer of the identified Transaction, THEN THE System SHALL return HTTP 403 with an error message indicating the requestor is not authorized to confirm this transaction.
3. IF a POST request is made to `/api/transactions/{id}/confirm` for a Transaction ID that does not exist, THEN THE System SHALL return HTTP 404 with an error message indicating the transaction was not found.
4. IF a POST request is made to `/api/transactions/{id}/confirm` for a Transaction whose status is COMPLETED or REFUNDED, THEN THE System SHALL return HTTP 409 with an error message indicating the transaction is not in a confirmable state.
5. IF a POST request is made to `/api/transactions/{id}/confirm` for a Transaction with status DISPUTED, THEN THE System SHALL return HTTP 409 with an error message indicating the transaction is under dispute and cannot be confirmed.
6. THE System SHALL ensure that the Seller balance credit, EscrowAccount status update, and Transaction status update during confirmation occur within a single atomic operation, so that if any step fails, all changes are rolled back and the Transaction remains in its previous state.

---

### Requirement 5: Auto-Release of Funds After Deadline

**User Story:** As a Seller, I want escrowed funds to be automatically released to me after the deadline if no dispute has been raised, so that I do not have to wait indefinitely for buyer confirmation.

#### Acceptance Criteria

1. WHEN the SchedulerJob executes, THE System SHALL identify all Transactions with status FUNDED whose deadline timestamp is strictly less than the current execution timestamp and for which no Dispute with status OPEN or IN_PROGRESS exists, as eligible for auto-release.
2. WHEN the SchedulerJob releases funds for an eligible Transaction, THE System SHALL add the locked EscrowAccount amount to the Seller's balance, update the EscrowAccount status to RELEASED, and update the Transaction status to COMPLETED.
3. THE SchedulerJob SHALL execute at a fixed interval of 60 seconds.
4. THE System SHALL ensure that auto-release fund transfers occur atomically per Transaction, so that if any step of the release fails, the Transaction status, EscrowAccount status, and Seller balance are all rolled back to their state prior to that scheduler execution.
5. IF the SchedulerJob encounters an error processing a specific Transaction, THEN THE System SHALL log the error with the Transaction ID and continue processing remaining eligible Transactions.
6. THE System SHALL ensure that each eligible Transaction is processed at most once per scheduler run, so that overlapping SchedulerJob executions do not result in duplicate fund releases for the same Transaction.

---

### Requirement 6: Dispute Filing

**User Story:** As a Buyer or Seller, I want to raise a dispute before the auto-release deadline, so that funds are frozen and an Arbitrator can resolve the disagreement.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/transactions/{id}/dispute` where the raisedBy User ID corresponds to the Buyer or Seller of the Transaction, the Transaction has status FUNDED, and the current timestamp is strictly less than the Transaction's deadline, THE System SHALL atomically create a Dispute record with status OPEN and update the Transaction status to DISPUTED, and return the created Dispute with HTTP 201.
2. IF a POST request is made to `/api/transactions/{id}/dispute` for a Transaction whose deadline timestamp is less than or equal to the current timestamp, THEN THE System SHALL return HTTP 400 with an error message indicating the dispute window has closed.
3. IF a POST request is made to `/api/transactions/{id}/dispute` for a Transaction whose status is not FUNDED, THEN THE System SHALL return HTTP 400 with an error message indicating the Transaction status does not permit filing a dispute.
4. IF a POST request is made to `/api/transactions/{id}/dispute` and the raisedBy User ID does not correspond to the Buyer or Seller of that Transaction, THEN THE System SHALL return HTTP 403 with an error message indicating only the Buyer or Seller may raise a dispute.
5. IF a POST request to `/api/transactions/{id}/dispute` is made with a reason that is blank, contains only whitespace, or exceeds 1000 characters, THEN THE System SHALL return HTTP 400 with an error message indicating the reason field requirements.
6. IF a POST request is made to `/api/transactions/{id}/dispute` for a Transaction that already has status DISPUTED, THEN THE System SHALL return HTTP 409 with an error message indicating a dispute has already been filed for this transaction.
7. WHILE a Transaction has status DISPUTED, THE System SHALL prevent the SchedulerJob from auto-releasing funds for that Transaction.

---

### Requirement 7: Dispute Resolution by Arbitrator

**User Story:** As an Arbitrator, I want to resolve a dispute by either releasing funds to the Seller or refunding them to the Buyer, so that the deadlocked transaction can be concluded.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/disputes/{id}/resolve` with a resolution of RELEASE for a Dispute linked to a Transaction with status DISPUTED, THE System SHALL add the locked EscrowAccount amount to the Seller's balance, update the EscrowAccount status to RELEASED, update the Transaction status to COMPLETED, set the Dispute status to RESOLVED, record the resolution type and resolved timestamp on the Dispute, and return the updated Dispute with HTTP 200.
2. WHEN a POST request is made to `/api/disputes/{id}/resolve` with a resolution of REFUND for a Dispute linked to a Transaction with status DISPUTED, THE System SHALL add the locked EscrowAccount amount to the Buyer's balance, update the EscrowAccount status to RELEASED, update the Transaction status to REFUNDED, set the Dispute status to RESOLVED, record the resolution type and resolved timestamp on the Dispute, and return the updated Dispute with HTTP 200.
3. IF a POST request is made to `/api/disputes/{id}/resolve` for a Dispute whose status is RESOLVED, THEN THE System SHALL return HTTP 409 with an error message indicating the dispute has already been resolved.
4. IF a POST request is made to `/api/disputes/{id}/resolve` with a resolution value outside the set {RELEASE, REFUND}, THEN THE System SHALL return HTTP 400 with an error message indicating the valid resolution values.
5. IF a POST request to `/api/disputes/{id}/resolve` is made for a Dispute ID that does not exist, THEN THE System SHALL return HTTP 404 with an error message indicating the dispute was not found.
6. IF a POST request is made to `/api/disputes/{id}/resolve` for a Dispute that is linked to a Transaction whose status is not DISPUTED, THEN THE System SHALL return HTTP 422 with an error message indicating the transaction is not in a resolvable state.
7. THE System SHALL ensure that balance credits, EscrowAccount status updates, Transaction status updates, and Dispute status updates during dispute resolution occur atomically, so that no partial state is persisted on failure and all affected records are rolled back to their pre-resolution state if any update fails.

---

### Requirement 8: Dispute Retrieval

**User Story:** As any participant, I want to retrieve dispute records, so that I can review the details and status of contested transactions.

#### Acceptance Criteria

1. WHEN a GET request is made to `/api/disputes`, THE System SHALL return a list of all Dispute records with HTTP 200; IF no Disputes exist, THE System SHALL return an empty list with HTTP 200.
2. WHEN a GET request is made to `/api/disputes/{id}` with a valid Dispute ID, THE System SHALL return the corresponding Dispute record with HTTP 200, including the Dispute ID, associated Transaction ID, status, reason, filing timestamp, and resolution timestamp if resolved.
3. IF a GET request is made to `/api/disputes/{id}` with a Dispute ID that does not exist, THEN THE System SHALL return HTTP 404 with an error message indicating the specified Dispute ID was not found.

---

### Requirement 9: Transaction Lifecycle Integrity

**User Story:** As a system operator, I want the Transaction status to follow a strict lifecycle, so that invalid state transitions are prevented and funds are never lost or double-released.

#### Acceptance Criteria

1. THE System SHALL only permit the following Transaction status transitions: PENDING → FUNDED, FUNDED → COMPLETED, FUNDED → DISPUTED, DISPUTED → COMPLETED, DISPUTED → REFUNDED.
2. IF any operation attempts a Transaction status transition not listed in the permitted transitions, THEN THE System SHALL reject the operation, leave the Transaction status unchanged, and return an error message indicating the current Transaction status and the attempted transition.
3. WHEN a state-mutating operation on a Transaction completes, THE System SHALL ensure that the sum of all Buyer and Seller balances plus all locked EscrowAccount amounts equals the sum of all initial User balances at system start (balance conservation invariant), such that no operation leaves the system in a state where this invariant is violated upon returning.
4. WHEN a Transaction reaches status COMPLETED or REFUNDED, THE System SHALL prevent any further status changes to that Transaction.
5. THE System SHALL only create Transactions in PENDING status, and SHALL reject any attempt to create a Transaction with an initial status other than PENDING with an error message indicating the required initial status.
6. WHEN a Transaction transitions to COMPLETED, THE System SHALL release the locked EscrowAccount funds to the Seller's balance; WHEN a Transaction transitions to REFUNDED, THE System SHALL release the locked EscrowAccount funds to the Buyer's balance.

---

### Requirement 10: Data Persistence and Technology Constraints

**User Story:** As a developer, I want the system to use Spring Boot with Spring Data JPA and an H2 in-memory database, so that the application can be run without external infrastructure during development.

#### Acceptance Criteria

1. THE System SHALL use Spring Boot as the application framework.
2. THE System SHALL use Spring Data JPA for all database interactions.
3. THE System SHALL use an H2 in-memory database for data storage, such that all data is non-persistent and is lost when the application process terminates.
4. THE System SHALL use Maven as the build tool.
5. WHEN an HTTP request is made to `/h2-console`, THE System SHALL serve the H2 web console to allow developers to inspect the database during development.
6. THE System SHALL use `spring.jpa.hibernate.ddl-auto=create-drop` so that the schema is created on startup and dropped on shutdown.
7. THE System SHALL allow all API endpoint requests without requiring an authentication token, session credential, or authorization header.
8. WHEN the application is started, THE System SHALL complete startup and be ready to serve HTTP requests within 60 seconds.
