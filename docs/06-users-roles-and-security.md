# 06 — Users, roles, and security

## 1. Stage status

The user, authentication, and authorization foundation of Industrial Maintenance Tracker has been implemented and verified.

At this stage, the application can:

- store users in PostgreSQL;
- create the first administrator from environment variables;
- authenticate users by email and password;
- prevent inactive users from signing in;
- log authenticated users out;
- distinguish `ADMIN`, `TECHNICIAN`, and `REQUESTER` roles;
- restrict the user-management section to administrators;
- list, create, edit, activate, and deactivate users;
- prevent duplicate email addresses without regard to letter case;
- prevent deactivation of the last active administrator;
- prevent removal of the `ADMIN` role from the last active administrator;
- display validation, authentication, and access-denied messages;
- verify the implemented behavior with automated and manual tests.

Equipment and repair-request functionality has not yet been implemented.

## 2. Implemented domain model

The user module is located in:

```text
src/main/java/com/vladyslav/industrialmaintenancetracker/user/
```

The central domain types are:

```text
Role
User
UserRepository
UserService
UserController
InitialAdminBootstrap
```

The `Role` enum contains:

```text
ADMIN
TECHNICIAN
REQUESTER
```

The `User` entity contains:

| Field | Purpose |
|---|---|
| `id` | Internal database identifier |
| `fullName` | User-visible full name |
| `email` | Login and unique business identifier |
| `passwordHash` | Encoded password; the plain password is never stored |
| `role` | One application role |
| `active` | Whether the account is allowed to sign in |
| `createdAt` | Creation timestamp |
| `updatedAt` | Last-update timestamp |

New users are active by default.

Domain state is changed through explicit methods:

```text
updateProfile(...)
activate()
deactivate()
```

The application does not expose physical user deletion.

## 3. Database migration

The user table is created by:

```text
src/main/resources/db/migration/V2__create_users_table.sql
```

The migration creates:

- the `users` table;
- an identity primary key;
- required columns for profile, security, role, status, and timestamps;
- checks for trimmed full names;
- checks for normalized email addresses;
- a role constraint for the three allowed values;
- a case-insensitive unique index on email.

Important database guarantees include:

```text
email = LOWER(BTRIM(email))
```

and:

```text
UNIQUE (LOWER(email))
```

Therefore, addresses such as `User@example.com` and `user@example.com` cannot be stored as different accounts.

Flyway remains the only mechanism that modifies the database schema. Hibernate continues to use:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

## 4. Repository layer

`UserRepository` extends `JpaRepository<User, Long>` and provides:

```java
Optional<User> findByEmailIgnoreCase(String email);

boolean existsByEmailIgnoreCase(String email);

long countByRoleAndActiveTrue(Role role);
```

These operations support:

- authentication by email;
- duplicate-email protection;
- initial administrator creation;
- protection of the last active administrator.

Repository behavior is tested against a real PostgreSQL 18 container through Testcontainers.

## 5. Form models and validation

The web layer does not bind HTML forms directly to the `User` entity.

It uses dedicated form classes:

```text
UserCreateForm
UserEditForm
```

Creation validation requires:

- full name: 2–100 characters and not blank;
- email: valid format, not blank, maximum 254 characters;
- password: not blank and at least 8 characters;
- role: required.

Editing validation requires:

- full name: 2–100 characters and not blank;
- email: valid format, not blank, maximum 254 characters;
- role: required.

Passwords are intentionally absent from `UserEditForm` so profile editing cannot accidentally overwrite a password.

The list page receives `UserListItem` view objects instead of persistence entities.

## 6. User service behavior

`UserService` is responsible for business rules and transaction boundaries.

### Creating a user

Before saving, the service:

1. trims the full name;
2. trims the email;
3. converts the email to lowercase with `Locale.ROOT`;
4. checks case-insensitive uniqueness;
5. encodes the password;
6. creates and saves the active user.

### Listing users

Users are loaded in ascending order by `fullName` and converted to `UserListItem` objects.

### Editing a user

The service:

1. loads the requested user or throws `UserNotFoundException`;
2. normalizes the submitted full name and email;
3. checks email uniqueness only when the email has changed;
4. prevents removal of the role from the last active administrator;
5. updates the managed entity inside a transaction.

An explicit repository `save` call is not needed for edits or status changes because Hibernate dirty checking persists changes when the transaction completes.

### Activating and deactivating

- activation changes `active` to `true`;
- repeated deactivation of an already inactive user does nothing;
- non-administrator users can be deactivated;
- an administrator can be deactivated only while another active administrator exists;
- the last active administrator always remains active and keeps the `ADMIN` role.

The service uses these domain-specific exceptions:

```text
DuplicateEmailException
LastActiveAdminException
UserNotFoundException
```

## 7. Password encoding

`SecurityConfig` exposes a `PasswordEncoder` created through:

```java
PasswordEncoderFactories.createDelegatingPasswordEncoder()
```

The current default encoding algorithm is BCrypt, and stored hashes include an algorithm identifier such as:

```text
{bcrypt}...
```

Plain passwords are used only as short-lived form input and are never written to the database or logs by application code.

## 8. Initial administrator bootstrap

`InitialAdminBootstrap` runs during application startup.

Its behavior is:

1. count active administrators;
2. stop immediately if at least one active administrator exists;
3. read the initial credentials from environment variables;
4. construct and validate `UserCreateForm`;
5. create the administrator through `UserService`.

Required environment variables for an empty database are:

```text
APP_ADMIN_USERNAME
APP_ADMIN_PASSWORD
```

The initial full name is:

```text
System Administrator
```

Invalid or missing initial credentials cause startup to fail clearly instead of creating an unusable or insecure administrator.

The bootstrap does not create a duplicate administrator on subsequent starts.

## 9. Database-backed authentication

Authentication follows this path:

```text
Login form
  -> Spring Security authentication filter
  -> CustomUserDetailsService
  -> UserRepository
  -> User
  -> CustomUserDetails
  -> authenticated session
```

`CustomUserDetailsService` normalizes the entered email before loading the user.

`CustomUserDetails` exposes:

- database user ID;
- full name;
- normalized email as the username;
- encoded password;
- role as `ROLE_<ROLE>`;
- active state through `isEnabled()`.

Because `isEnabled()` returns the stored `active` value, an inactive account is rejected by Spring Security.

## 10. Web security configuration

The application uses session-based authentication and a `SecurityFilterChain`.

Public endpoints:

```text
/login
/access-denied
```

Administrator-only endpoints:

```text
/users
/users/**
```

Every other endpoint currently requires authentication.

Security behavior:

- an anonymous user requesting a protected page is redirected to `/login`;
- invalid credentials redirect to `/login?error`;
- successful logout redirects to `/login?logout`;
- an authenticated user without the required role receives HTTP 403;
- denied access is forwarded to `/access-denied`;
- CSRF protection remains enabled;
- state-changing user operations use POST requests.

## 11. Login, logout, and error pages

Implemented Thymeleaf templates:

```text
templates/login.html
templates/access-denied.html
```

The login page contains:

- an email/username field;
- a password field;
- a CSRF token;
- an invalid-credentials message;
- a successful-logout message.

Logout is performed through a POST form so it remains protected by CSRF.

The access-denied page explains that the authenticated user does not have permission and provides navigation back to the home page.

## 12. Administrator user management

The user-management section is available only to `ADMIN`.

Implemented routes:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/users` | List users |
| `GET` | `/users/new` | Display the creation form |
| `POST` | `/users` | Create a user |
| `GET` | `/users/{userId}/edit` | Display the edit form |
| `POST` | `/users/{userId}/edit` | Update name, email, and role |
| `POST` | `/users/{userId}/activate` | Activate an account |
| `POST` | `/users/{userId}/deactivate` | Deactivate an account |

Implemented templates:

```text
templates/users/list.html
templates/users/create.html
templates/users/edit.html
```

The list displays:

- full name;
- email;
- role;
- active/inactive status;
- creation timestamp;
- edit action;
- context-sensitive activate/deactivate action.

Successful and failed operations use redirect flash messages where appropriate.

## 13. Automated verification

The complete test suite contains 60 tests:

| Test class | Tests |
|---|---:|
| `IndustrialMaintenanceTrackerApplicationTests` | 1 |
| `CustomUserDetailsServiceTest` | 2 |
| `CustomUserDetailsTest` | 2 |
| `SecurityAccessTest` | 15 |
| `SecurityConfigTest` | 1 |
| `UserCreateFormValidationTest` | 2 |
| `InitialAdminBootstrapTest` | 3 |
| `UserControllerTest` | 3 |
| `UserEditControllerTest` | 8 |
| `UserEditServiceTest` | 7 |
| `UserRepositoryTest` | 2 |
| `UserServiceTest` | 3 |
| `UserStatusControllerTest` | 5 |
| `UserStatusServiceTest` | 6 |
| **Total** | **60** |

Final result:

```text
Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The suite verifies:

- complete Spring context startup;
- Flyway migration and Hibernate validation;
- repository behavior on PostgreSQL;
- password encoding;
- initial administrator bootstrap;
- user creation and validation;
- database-backed authentication;
- active/inactive account behavior;
- login and logout pages;
- anonymous, administrator, technician, and requester access;
- user listing and creation;
- activation and deactivation;
- last-active-administrator protection;
- user editing and normalization;
- duplicate-email rejection;
- unknown-user handling;
- CSRF-protected controller operations.

## 14. Manual verification

The following browser scenarios were verified against the development PostgreSQL database:

- anonymous access redirects to the custom login page;
- the initial administrator can sign in;
- the administrator can open `/users`;
- a non-administrator receives the access-denied page for `/users`;
- a new technician account can be created;
- a deactivated user cannot sign in;
- a reactivated user can sign in again;
- the last active administrator cannot be deactivated;
- a user's name and role can be edited;
- a duplicate email is rejected on the edit form;
- the last active administrator cannot be moved to another role;
- rejected edits leave the stored user data unchanged.

## 15. Relevant commits

The stage was implemented incrementally:

```text
165792e feat: add user domain and persistence
95ffd07 feat: configure password encoding
7fe393f feat: add user creation form validation
98035cb feat: add user creation service
dd5527c feat: add initial admin bootstrap
be3796b feat: add custom security user details
aa41c12 feat: load security users from database
34eace1 feat: configure web authentication
6bdb090 feat: add custom login and logout
2533ccf feat: restrict user management to admins
08cf935 feat: add admin user list
d64e80f feat: add admin user creation
06bff5e feat: add user activation controls
0941f73 feat: add admin user editing
```

Each functional block was compiled or tested before being committed and pushed to `main`.

## 16. Deliberately deferred work

The following work is not part of the completed stage:

- self-service password change;
- password reset by an administrator;
- restricting technician deactivation or role changes based on assigned repair requests;
- immediate refresh of an already authenticated session after changing that same user's email or role;
- multiple roles per user;
- advanced permission management;
- pagination and search in the user list;
- final shared page layout and visual styling.

The technician-assignment restrictions cannot be completed correctly until `RepairRequest` exists.

The current last-administrator check is appropriate for the MVP's normal workflow. Special serialization for simultaneous competing administrator updates has not been introduced.

## 17. Stage completion criteria

Stage 06 is complete because:

- the `User` domain model is persisted through Flyway and JPA;
- email normalization and uniqueness are enforced;
- passwords are encoded and never stored in plain text;
- the first administrator is bootstrapped safely;
- authentication uses users stored in PostgreSQL;
- inactive users cannot sign in;
- login and logout work;
- roles are converted to Spring Security authorities;
- anonymous and forbidden access are handled correctly;
- `/users/**` is protected for administrators;
- administrators can list, create, edit, activate, and deactivate users;
- the last active administrator is protected;
- manual browser scenarios pass;
- all 60 automated tests pass;
- every implemented functional block has been committed and pushed.

## 18. Next stage

The next stage is implementation of equipment.

Recommended order:

1. resolve and document the final equipment-status names;
2. define `EquipmentStatus`;
3. implement the `Equipment` entity;
4. create Flyway migration `V3`;
5. add `EquipmentRepository` and PostgreSQL integration tests;
6. add equipment list and detail pages;
7. add creation and validation;
8. add editing and status management;
9. protect modifying operations for `ADMIN`.

Repair requests will be implemented only after the equipment foundation is stable.
