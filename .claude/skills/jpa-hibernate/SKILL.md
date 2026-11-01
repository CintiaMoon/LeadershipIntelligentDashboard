# JPA and Hibernate Persistence

## When to use
Use when designing or changing JPA entities, repositories, mappings, fetch behavior, optimistic locking, or transaction-sensitive persistence logic.

## Rules
1. Read the approved data model and Flyway migration before changing entity mappings.
2. Keep entity relationships intentional; avoid broad eager loading and accidental N+1 queries.
3. Never serialize JPA entities directly as API responses; map to DTOs inside a deliberate transaction boundary.
4. Use `@Version` or a documented locking/conditional-update strategy when concurrent updates can violate invariants.
5. Use database constraints for uniqueness and referential integrity wherever feasible.
6. Keep production Hibernate schema management non-mutating and use Flyway for DDL.
7. Test persistence behavior against PostgreSQL with Testcontainers, not only an in-memory database.
