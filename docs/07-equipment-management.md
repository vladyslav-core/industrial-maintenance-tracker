# 07 — Equipment management

## 1. Stage status

The equipment-management foundation of Industrial Maintenance Tracker has been implemented and verified.

At this stage, the application can:

- store physical equipment units in PostgreSQL;
- distinguish four equipment statuses;
- assign `OPERATIONAL` automatically to new equipment;
- list equipment in a stable order;
- search equipment by name or inventory number without regard to letter case;
- filter equipment by status;
- display complete equipment details;
- create and edit equipment through validated forms;
- prevent duplicate inventory numbers without regard to letter case;
- change equipment status through an explicit business operation;
- prevent reactivation of decommissioned equipment;
- allow every authenticated user to view equipment;
- restrict creation, editing, and status changes to administrators;
- handle missing equipment and rejected operations without exposing application errors;
- verify the implemented behavior with automated and manual tests.

Repair-request functionality has not yet been implemented.

## 2. Requirements alignment

Before implementation, the existing planning documents were aligned with the final equipment scope.

For the MVP, one `Equipment` record represents one physical unit, not an equipment category or model.

The implemented equipment data is intentionally limited to:

```text
name
inventoryNumber
description
location
status
createdAt
updatedAt
```

Manufacturer, model, serial number, attachments, maintenance history, and repair-request relationships remain outside this stage.

The requirements, user flows, data model, and implementation plan were updated before the code was added. This alignment is recorded in commit:

```text
38d05d5 docs: align equipment requirements and implementation plan
```

## 3. Implemented domain model

The equipment module is located in:

```text
src/main/java/com/vladyslav/industrialmaintenancetracker/equipment/
```

The central types are:

```text
EquipmentStatus
Equipment
EquipmentRepository
EquipmentService
EquipmentController
```

The `Equipment` entity contains:

| Field | Purpose |
|---|---|
| `id` | Internal database identifier |
| `name` | Human-readable equipment name |
| `inventoryNumber` | Unique business identifier |
| `description` | Optional equipment description |
| `location` | Current physical location |
| `status` | Current lifecycle state |
| `createdAt` | Creation timestamp |
| `updatedAt` | Last-update timestamp |

New equipment starts with:

```text
OPERATIONAL
```

Domain state is changed through explicit methods:

```text
updateDetails(...)
changeStatus(...)
```

The application does not expose physical equipment deletion.

## 4. Equipment statuses and transition rule

`EquipmentStatus` contains:

```text
OPERATIONAL
UNDER_REPAIR
OUT_OF_SERVICE
DECOMMISSIONED
```

Their intended meanings are:

| Status | Meaning |
|---|---|
| `OPERATIONAL` | Available for normal operation |
| `UNDER_REPAIR` | Currently being repaired |
| `OUT_OF_SERVICE` | Not available for operation |
| `DECOMMISSIONED` | Permanently removed from service |

The implemented transition rule is deliberately small:

- equipment in any non-decommissioned state may move to any defined status;
- `DECOMMISSIONED` is terminal;
- equipment cannot move from `DECOMMISSIONED` back to another status;
- a null target status is rejected;
- submitting the current status again is accepted and leaves the effective state unchanged.

An attempt to reactivate decommissioned equipment throws:

```text
InvalidEquipmentStatusTransitionException
```

This rule belongs to the `Equipment` entity, so it applies independently of the web interface.

## 5. Database migration

The equipment table is created by:

```text
src/main/resources/db/migration/V3__create_equipment_table.sql
```

The migration creates:

- the `equipment` table;
- an identity primary key;
- required name, inventory number, location, status, and timestamp columns;
- an optional description column;
- length and trimming checks;
- a constraint for the four allowed status values;
- a case-insensitive unique index on inventory number;
- an index on status.

Important database guarantees include:

```text
UNIQUE (LOWER(inventory_number))
```

and:

```text
status IN (
    'OPERATIONAL',
    'UNDER_REPAIR',
    'OUT_OF_SERVICE',
    'DECOMMISSIONED'
)
```

The database also rejects leading or trailing whitespace in stored names, inventory numbers, descriptions, and locations.

The description is either `NULL` or a trimmed value between 1 and 1000 characters. The service converts an empty or whitespace-only description to `NULL`.

Flyway remains the only mechanism that modifies the database schema. Hibernate continues to use:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

## 6. Repository layer and search

`EquipmentRepository` extends `JpaRepository<Equipment, Long>` and provides:

```java
Optional<Equipment> findByInventoryNumberIgnoreCase(
        String inventoryNumber
);

boolean existsByInventoryNumberIgnoreCase(
        String inventoryNumber
);

List<Equipment> search(
        String searchTerm,
        EquipmentStatus status
);
```

The custom JPQL search:

- matches partial equipment names;
- matches partial inventory numbers;
- ignores letter case;
- applies the status filter only when a status is supplied;
- treats an empty search term as no text restriction;
- sorts by name and then by inventory number in ascending order.

This allows the same repository operation to support:

```text
all equipment
text search only
status filter only
text search and status filter together
```

Repository behavior is tested against a real PostgreSQL 18 container through Testcontainers.

## 7. Form models and validation

The web layer does not bind HTML forms directly to the `Equipment` entity.

It uses dedicated form classes:

```text
EquipmentCreateForm
EquipmentEditForm
```

Both forms apply the same field validation:

- name: 2–120 characters and not blank;
- inventory number: 2–50 characters and not blank;
- description: optional, maximum 1000 characters;
- location: 2–120 characters and not blank.

Status is intentionally absent from both forms.

Creation always begins with `OPERATIONAL`, while later status changes use a separate explicit operation. This prevents ordinary profile editing from silently changing lifecycle state.

The controller returns the form with field errors when validation fails.

## 8. View models

The read side uses dedicated immutable view objects:

```text
EquipmentListItem
EquipmentDetails
```

`EquipmentListItem` contains only the information required by the table:

```text
id
name
inventoryNumber
location
status
```

`EquipmentDetails` contains:

```text
id
name
inventoryNumber
description
location
status
createdAt
updatedAt
```

This keeps persistence entities out of the Thymeleaf model and makes the data expected by each page explicit.

## 9. Equipment service behavior

`EquipmentService` owns the equipment use cases, business checks, normalization, DTO mapping, and transaction boundaries.

### Creating equipment

Before saving, the service:

1. trims the name;
2. trims the inventory number;
3. converts a blank optional description to `null`;
4. trims a non-blank description;
5. trims the location;
6. checks inventory-number uniqueness without regard to case;
7. constructs equipment with `OPERATIONAL` status;
8. saves it through the repository.

### Listing and searching

The service converts a null search term to an empty string and trims a supplied search term.

It delegates text matching, optional status filtering, and ordering to the repository, then maps results to `EquipmentListItem` objects.

### Loading details

The service loads equipment by ID and maps it to `EquipmentDetails`.

An unknown identifier throws:

```text
EquipmentNotFoundException
```

### Editing equipment

The service:

1. loads the requested equipment;
2. normalizes all submitted values;
3. checks uniqueness only when the inventory number changed ignoring case;
4. updates the managed entity through `updateDetails(...)`.

An explicit repository `save` call is not needed because Hibernate dirty checking persists the update when the transaction completes.

### Changing status

The service loads the equipment and delegates the transition to:

```java
equipment.changeStatus(targetStatus);
```

This preserves the terminal-state rule inside the domain model.

The service uses these domain-specific exceptions:

```text
DuplicateInventoryNumberException
EquipmentNotFoundException
InvalidEquipmentStatusTransitionException
```

## 10. Equipment web interface

Implemented routes:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/equipment` | List, search, and filter equipment |
| `GET` | `/equipment/{equipmentId}` | Display equipment details |
| `GET` | `/equipment/new` | Display the creation form |
| `POST` | `/equipment` | Create equipment |
| `GET` | `/equipment/{equipmentId}/edit` | Display the edit form |
| `POST` | `/equipment/{equipmentId}/edit` | Update equipment details |
| `POST` | `/equipment/{equipmentId}/status` | Change equipment status |

Implemented Thymeleaf templates:

```text
templates/equipment/list.html
templates/equipment/details.html
templates/equipment/create.html
templates/equipment/edit.html
```

The list page displays:

- name;
- inventory number;
- location;
- status;
- a details link;
- an edit link for administrators.

It also provides:

- case-insensitive text search by name or inventory number;
- status filtering;
- combined search and filtering;
- a clear-filter link;
- an empty-result message.

The details page displays the complete equipment record, timestamps, flash messages, and administrator-only management controls.

The home page now contains navigation to the equipment section.

## 11. Authorization and CSRF protection

Equipment permissions are enforced by `SecurityConfig`.

Every authenticated role may use:

```text
GET /equipment
GET /equipment/{equipmentId}
```

Only `ADMIN` may use:

```text
GET  /equipment/new
POST /equipment
GET  /equipment/{equipmentId}/edit
POST /equipment/{equipmentId}/edit
POST /equipment/{equipmentId}/status
```

Therefore:

- `ADMIN` can view and manage equipment;
- `TECHNICIAN` can view equipment but cannot modify it;
- `REQUESTER` can view equipment but cannot modify it;
- anonymous users are redirected to the login page;
- authenticated non-administrators receive HTTP 403 for modifying endpoints.

The controller exposes a `canManageEquipment` model attribute based on the presence of `ROLE_ADMIN`.

Thymeleaf uses this value to hide creation, editing, and status controls from users who cannot execute those operations. Hiding controls improves the interface, while Spring Security remains the actual authorization boundary.

CSRF protection remains enabled, and all state-changing equipment operations use POST requests.

## 12. Error handling and user feedback

The web layer handles expected failures explicitly:

- invalid form input returns the same form with field errors;
- a duplicate inventory number produces an error on the `inventoryNumber` field;
- an unknown equipment details request redirects to the equipment list with an error flash message;
- an unknown edit request redirects to the equipment list;
- an unknown status-change request redirects to the equipment list;
- an invalid status transition redirects back to the equipment details page with an error flash message;
- successful creation, editing, and status changes produce success flash messages.

Creation redirects to the new equipment details page. Editing and status changes redirect back to the affected equipment details page.

## 13. Automated verification

Stage 07 adds 38 equipment tests:

| Test class | Tests |
|---|---:|
| `EquipmentFormValidationTest` | 4 |
| `EquipmentReadControllerTest` | 6 |
| `EquipmentRepositoryTest` | 6 |
| `EquipmentServiceTest` | 12 |
| `EquipmentWriteControllerTest` | 10 |
| **Equipment tests** | **38** |

The complete project test suite now contains 98 tests:

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
| `EquipmentFormValidationTest` | 4 |
| `EquipmentReadControllerTest` | 6 |
| `EquipmentRepositoryTest` | 6 |
| `EquipmentServiceTest` | 12 |
| `EquipmentWriteControllerTest` | 10 |
| **Total** | **98** |

Final result:

```text
Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The equipment tests verify:

- PostgreSQL migration and JPA persistence;
- default `OPERATIONAL` status;
- case-insensitive inventory-number uniqueness;
- case-insensitive partial search by name and inventory number;
- optional status filtering;
- deterministic result ordering;
- creation and edit validation;
- value normalization;
- blank-description conversion to `null`;
- creation, listing, details, and editing service behavior;
- unknown-equipment handling;
- valid and invalid status changes;
- list and details controller rendering;
- anonymous authentication requirements;
- administrator-only modifying endpoints;
- duplicate-inventory-number form errors;
- redirects and flash messages;
- CSRF-protected write operations.

## 14. Manual verification

The following browser scenarios were verified against the development PostgreSQL database:

- Docker PostgreSQL started successfully;
- the application connected to PostgreSQL and started on port 8080;
- Flyway validated the existing migrations and applied migration `V3`;
- the empty equipment list rendered correctly;
- the administrator could open the equipment creation form;
- equipment named `Hydraulic press` with inventory number `PRESS-001` was created;
- newly created equipment received `OPERATIONAL` automatically;
- the success message and details page rendered correctly;
- status was changed from `OPERATIONAL` to `UNDER_REPAIR`;
- the status-change success message appeared;
- `updatedAt` changed while `createdAt` remained unchanged;
- location was edited from `Workshop A` to `Workshop B`;
- the edit success message appeared and the existing status was preserved;
- combined text search for `press` and status filter `UNDER_REPAIR` returned the expected equipment;
- clearing filters restored the unfiltered equipment list.

Role restrictions and rejected write operations are covered by automated MVC security tests. The manual browser session used the administrator account.

## 15. Relevant commits

The stage was implemented incrementally:

```text
38d05d5 docs: align equipment requirements and implementation plan
111e28c feat: add equipment domain and persistence
63dfa89 feat: add equipment service and search
f24b4fa feat: add equipment web interface
```

Each functional block was compiled or tested before being committed and pushed to `main`.

## 16. Deliberately deferred work

The following work is not part of the completed stage:

- repair requests linked to equipment;
- assignment of technicians to repair work;
- automatic equipment-status changes based on repair-request state;
- equipment status history and audit trail;
- recording which user changed equipment data or status;
- physical equipment deletion;
- optimistic locking for concurrent equipment edits;
- pagination and user-selectable sorting;
- advanced or full-text search;
- equipment photographs, manuals, and attachments;
- manufacturer, model, serial number, and category fields;
- localized status labels and formatted local timestamps;
- final shared page layout and visual styling.

The current status model is intentionally independent of repair requests. Their relationship will be designed when the repair-request module exists.

## 17. Stage completion criteria

Stage 07 is complete because:

- the equipment requirements were aligned before implementation;
- the `Equipment` domain model is persisted through Flyway and JPA;
- the four final equipment statuses are implemented;
- new equipment starts as `OPERATIONAL`;
- decommissioning is irreversible;
- inventory-number normalization and case-insensitive uniqueness are enforced;
- the service layer owns transactions, mapping, and business checks;
- authenticated users can list, search, filter, and view equipment;
- administrators can create, edit, and change equipment status;
- modifying endpoints are protected by Spring Security and CSRF;
- validation and expected failures produce useful page-level feedback;
- manual browser scenarios pass;
- all 98 automated tests pass;
- every implemented functional block has been committed and pushed.

## 18. Next stage

The next stage is implementation of repair requests.

Recommended order:

1. align the repair-request requirements, roles, fields, and status names;
2. define the repair-request domain model and transition rules;
3. create the next Flyway migration with references to equipment and users;
4. add repository integration tests;
5. implement creation, listing, filtering, and details use cases;
6. implement assignment and repair-request status management;
7. enforce administrator, technician, and requester permissions;
8. add Thymeleaf pages and navigation;
9. verify the module with automated and manual tests;
10. document the completed stage.
