# 04 — Техническая архитектура и план реализации

## Статус документа

Этап 04 завершён.

На этом этапе определены:

- окончательный стек проекта;
- общий архитектурный стиль;
- ответственность слоёв;
- структура пакетов;
- роль Spring Security;
- взаимодействие Spring Boot, Thymeleaf, PostgreSQL и Flyway;
- порядок реализации функций;
- техническая дорожная карта от пустого проекта до готового MVP;
- стратегия тестирования;
- сознательные ограничения MVP;
- критерии готовности технического каркаса, ядра и полного MVP.

Код приложения, Java-классы, SQL-миграции и Spring Boot-проект на этом этапе ещё не создавались.

---

# 1. Общая архитектура приложения

Industrial Maintenance Tracker реализуется как **модульный монолит с классической слоистой архитектурой**.

Это означает:

- одно Spring Boot-приложение;
- один Maven-проект;
- один GitHub-репозиторий;
- одна PostgreSQL-база;
- один процесс запуска и развёртывания;
- логическое разделение приложения на функциональные модули;
- отсутствие сетевого взаимодействия между внутренними модулями.

Основная схема:

```text
Пользователь в браузере
        ↓
Spring Security
        ↓
Spring MVC Controller
        ↓
Service
        ↓
Repository
        ↓
Hibernate / JDBC
        ↓
PostgreSQL
```

Формирование HTML-ответа:

```text
Controller
        ↓
Model
        ↓
Thymeleaf
        ↓
Bootstrap
        ↓
готовая HTML-страница
        ↓
браузер
```

При запуске приложения:

```text
Docker Compose
        ↓
PostgreSQL

Spring Boot
        ↓
подключение к PostgreSQL
        ↓
Flyway применяет миграции
        ↓
Hibernate проверяет соответствие Entity схеме
        ↓
Spring Security настраивает доступ
        ↓
встроенный Tomcat принимает HTTP-запросы
```

## Почему выбран модульный монолит

Для MVP и первого полноценного портфолио-проекта это наиболее разумный вариант.

Он позволяет:

- сохранить понятную структуру;
- использовать одну транзакционную систему;
- проще тестировать бизнес-правила;
- быстрее реализовать полный пользовательский сценарий;
- не тратить время на сетевое взаимодействие, распределённые транзакции и сложное развёртывание.

Микросервисы здесь не дают реальной пользы и только увеличивают объём работы.

---

# 2. Окончательный стек проекта

## Backend

- Java 21;
- Spring Boot 3.5.x;
- Spring MVC;
- Spring Security;
- Spring Data JPA;
- Hibernate;
- Jakarta Bean Validation.

## Frontend

- Thymeleaf;
- Bootstrap 5;
- HTML;
- CSS.

## Database

- PostgreSQL 18.x;
- Flyway.

## Build и инструменты

- Maven;
- Maven Wrapper;
- IntelliJ IDEA;
- Git;
- GitHub.

## Тестирование

- JUnit 5;
- Mockito;
- AssertJ;
- Spring Boot Test;
- MockMvc;
- Spring Security Test;
- Testcontainers;
- PostgreSQL в интеграционных тестах.

## Инфраструктура

- Docker Compose;
- GitHub Actions после появления устойчивого набора тестов.

## Что не входит в обязательный стек MVP

- React, Angular, Vue;
- отдельный Node.js frontend;
- WebFlux;
- REST API;
- Swagger / OpenAPI;
- JWT;
- OAuth 2.0;
- Keycloak;
- Kafka;
- RabbitMQ;
- Redis;
- Elasticsearch;
- Kubernetes;
- Spring Cloud;
- GraphQL;
- CQRS;
- Event Sourcing;
- Lombok;
- MapStruct.

---

# 3. Роль основных технологий

## Spring Boot

Spring Boot:

- запускает приложение;
- создаёт и связывает компоненты;
- поднимает встроенный Tomcat;
- настраивает Spring MVC;
- подключает Spring Security;
- подключает Thymeleaf;
- подключается к PostgreSQL;
- запускает Flyway;
- создаёт Repository и другие Spring-компоненты.

## Spring MVC

Spring MVC:

- принимает HTTP-запросы;
- связывает параметры и поля формы с Java-объектами;
- вызывает Controller;
- передаёт данные в Model;
- выбирает Thymeleaf-шаблон;
- выполняет redirect после успешных POST-запросов.

## Thymeleaf

Thymeleaf:

- получает данные из Model;
- обрабатывает HTML-шаблон на сервере;
- формирует готовую HTML-страницу;
- отображает формы, таблицы, ошибки и сообщения;
- показывает или скрывает элементы интерфейса в зависимости от роли.

Thymeleaf не является уровнем безопасности. Скрытая кнопка не заменяет проверку в Spring Security и Service.

## Bootstrap

Bootstrap оформляет таблицы, формы, навигацию, сообщения и адаптивный интерфейс. Он не участвует в бизнес-логике.

## Spring Data JPA и Hibernate

Цепочка доступа к данным:

```text
Spring Data JPA
        ↓
Hibernate
        ↓
PostgreSQL JDBC Driver
        ↓
PostgreSQL
```

Spring Data JPA предоставляет интерфейсы Repository.

Hibernate:

- преобразует Entity в SQL;
- выполняет SELECT, INSERT, UPDATE и DELETE;
- управляет Persistence Context;
- отслеживает изменения Entity внутри транзакции;
- проверяет соответствие Entity структуре базы.

## PostgreSQL

PostgreSQL хранит пользователей, оборудование, ремонтные заявки, историю обслуживания, связи, ограничения, роли, статусы, приоритеты и временные отметки.

## Flyway

Flyway является единственным инструментом управления схемой базы.

Он выполняет:

- CREATE TABLE;
- ALTER TABLE;
- CREATE INDEX;
- ADD CONSTRAINT;
- последовательное применение SQL-миграций;
- фиксацию истории миграций и контрольных сумм.

Целевая настройка Hibernate:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Не используются:

```text
spring.jpa.hibernate.ddl-auto=update
spring.jpa.hibernate.ddl-auto=create
spring.jpa.hibernate.ddl-auto=create-drop
```

Разделение ответственности:

```text
Flyway:
создаёт и изменяет схему базы

Hibernate:
проверяет схему и работает с данными
```

---

# 4. Структура пакетов

Базовый пакет:

```text
com.vladyslav.industrialmaintenancetracker
```

Основная структура:

```text
com.vladyslav.industrialmaintenancetracker
│
├── IndustrialMaintenanceTrackerApplication
│
├── user
│   ├── User
│   ├── UserRole
│   ├── UserRepository
│   ├── UserService
│   ├── UserController
│   └── dto
│
├── equipment
│   ├── Equipment
│   ├── EquipmentStatus
│   ├── EquipmentRepository
│   ├── EquipmentService
│   ├── EquipmentController
│   └── dto
│
├── repairrequest
│   ├── RepairRequest
│   ├── RepairRequestStatus
│   ├── RepairRequestPriority
│   ├── RepairRequestRepository
│   ├── RepairRequestService
│   ├── RepairRequestController
│   └── dto
│
├── maintenance
│   ├── MaintenanceRecord
│   ├── MaintenanceRecordRepository
│   ├── MaintenanceRecordService
│   ├── MaintenanceRecordController
│   └── dto
│
├── security
│   ├── SecurityConfig
│   ├── CustomUserDetails
│   └── CustomUserDetailsService
│
├── config
├── exception
└── common
```

Используется структура по функциям, а не один общий пакет для всех Controller или Service.

Дополнительная вложенность вроде `equipment/controller` и `equipment/service` появится только при реальном разрастании модуля.

`common` используется осторожно и не должен превращаться в папку для всего непонятного.


---

# 5. Ответственность слоёв

## 5.1 Controller

Controller отвечает за:

- URL;
- HTTP-методы GET и POST;
- получение параметров;
- получение данных формы;
- Bean Validation;
- BindingResult;
- вызов Service;
- передачу данных в Model;
- выбор Thymeleaf-шаблона;
- redirect;
- flash-сообщения;
- возврат формы при ошибках.

Controller не должен:

- обращаться к Repository напрямую;
- содержать бизнес-правила;
- самостоятельно менять статус;
- координировать несколько Repository;
- создавать MaintenanceRecord;
- управлять транзакциями.

Разрешённая цепочка:

```text
Controller
        ↓
Service
        ↓
Repository
```

## 5.2 Service

Service является главным слоем бизнес-логики.

Он отвечает за:

- пользовательские сценарии;
- бизнес-правила;
- права на конкретную сущность;
- переходы статусов;
- создание и изменение Entity;
- координацию Repository;
- транзакции;
- ручное преобразование Form DTO;
- понятные исключения.

Например, закрытие заявки должно:

1. найти заявку;
2. проверить существование;
3. определить текущего пользователя;
4. проверить роль и отношение к заявке;
5. проверить статус;
6. проверить результат ремонта;
7. установить финальный статус и время;
8. создать MaintenanceRecord;
9. сохранить всё в одной транзакции.

Service не должен работать с Model, Thymeleaf, redirect или HTML.

## 5.3 Repository

Repository отвечает за:

- поиск;
- сохранение;
- проверку существования;
- фильтрацию;
- сортировку;
- пагинацию;
- специальные запросы.

Repository не проверяет роли, переходы статусов и бизнес-разрешения.

## 5.4 Domain

Domain содержит:

- User;
- Equipment;
- RepairRequest;
- MaintenanceRecord;
- роли;
- статусы;
- приоритеты;
- связи и внутреннее состояние.

Entity может иметь методы вроде:

```text
assignTechnician(...)
markStarted(...)
markCompleted(...)
markClosed(...)
```

Но Service обязан предварительно проверить роль, принадлежность, статус и обязательные данные.

Entity не обращается к Repository, Service, Controller, HTTP-сессии или SecurityContext.

---

# 6. Разрешённые зависимости

Основное направление:

```text
Controller
        ↓
Service
        ↓
Repository
        ↓
Domain
```

Допустимо:

```text
Controller → Service
Controller → DTO
Controller → Security principal

Service → Repository
Service → Domain
Service → DTO
Service → другой Service при обоснованной необходимости
Service → CurrentUserService

Repository → Domain

Security → UserRepository
Security → User
```

Запрещено:

```text
Controller → Repository

Repository → Service
Repository → Controller

Domain → Repository
Domain → Service
Domain → Controller
Domain → Spring MVC

Service → Controller
Service → Thymeleaf
Service → Model
```

Циклические зависимости между Service запрещены.

---

# 7. DTO и валидация

JPA Entity не используется как универсальный объект HTML-формы.

Примерные Form DTO:

```text
UserCreateForm
UserEditForm
EquipmentCreateForm
EquipmentEditForm
RepairRequestCreateForm
RepairRequestAssignForm
RepairRequestCompleteForm
RepairRequestFilter
```

Схема:

```text
HTML-форма
        ↓
Form DTO
        ↓
Controller
        ↓
Service
        ↓
Entity
        ↓
Repository
```

Пользователь не передаёт служебные поля:

- id;
- createdAt;
- createdBy;
- status;
- assignedTechnician;
- closedAt;
- passwordHash.

View DTO создаются только там, где они действительно полезны: для защиты данных, сложных страниц, предотвращения lazy loading и объединения данных.

MapStruct не используется. Маппинг выполняется вручную.

## Bean Validation

Проверяет форму:

- обязательность;
- длину;
- формат;
- null;
- диапазон.

## Бизнес-валидация

Выполняется в Service:

- оборудование активно;
- пользователь существует;
- техник активен;
- роль корректна;
- статус допускает действие;
- пользователь имеет право;
- инвентарный номер уникален;
- заявку можно закрыть;
- MaintenanceRecord ещё не существует.

---

# 8. Spring Security

Spring Security отвечает за:

```text
Authentication:
кто пользователь

Authorization:
что ему разрешено
```

Используются:

- вход по username и password;
- собственная страница login;
- серверная HTTP-сессия;
- session cookie;
- logout;
- PasswordEncoder;
- роли ADMIN, TECHNICIAN и REQUESTER;
- ограничение URL;
- CSRF;
- запрет входа деактивированного пользователя.

Не используются JWT, OAuth, OpenID Connect, Keycloak, внешние провайдеры, публичная регистрация, email reset и 2FA.

## 8.1 Схема входа

```text
POST /login
        ↓
Spring Security
        ↓
CustomUserDetailsService
        ↓
UserRepository
        ↓
PostgreSQL
        ↓
CustomUserDetails
        ↓
проверка password hash и active
        ↓
создание HTTP-сессии
```

Основной идентификатор входа — `username`. Email остаётся контактным полем.

## 8.2 Роли

В доменной модели:

```text
ADMIN
TECHNICIAN
REQUESTER
```

В Spring Security:

```text
ROLE_ADMIN
ROLE_TECHNICIAN
ROLE_REQUESTER
```

### ADMIN

Может управлять пользователями и оборудованием, видеть все заявки, назначать техников, контролировать жизненный цикл и просматривать историю.

### TECHNICIAN

Может видеть назначенные заявки, начинать работу, фиксировать результат, завершать разрешённые этапы и просматривать историю.

### REQUESTER

Может создавать заявки, видеть собственные заявки, отслеживать статус и отменять свою заявку, если это разрешено.

Точные действия и переходы должны полностью соответствовать документу `03-data-model-and-business-rules.md`.

## 8.3 Два уровня доступа

Spring Security проверяет:

- вошёл ли пользователь;
- какая у него роль;
- доступен ли раздел или endpoint.

Service проверяет:

- принадлежит ли заявка пользователю;
- назначена ли она технику;
- допустим ли статус;
- разрешено ли конкретное действие.

Скрытая кнопка не является защитой.

## 8.4 Текущий пользователь

Используется отдельный компонент:

```text
CurrentUserService
```

Схема:

```text
Service
        ↓
CurrentUserService
        ↓
SecurityContext
        ↓
username
        ↓
UserRepository
        ↓
User
```

## 8.5 Пароли и начальный ADMIN

Пароль хранится только как хеш.

Начальный администратор создаётся через контролируемый bootstrap-механизм с переменными:

```text
APP_ADMIN_USERNAME
APP_ADMIN_PASSWORD
```

Открытый пароль не хранится в базе, Entity, миграциях, GitHub или `application.properties`.

## 8.6 CSRF и logout

CSRF не отключается.

Изменяющие операции выполняются через POST.

Logout выполняется через POST и завершает сессию.

## 8.7 Ошибки доступа

- неавторизованный пользователь перенаправляется на `/login`;
- авторизованный пользователь без прав получает 403;
- чужие данные защищаются дополнительной проверкой в Service.


---

# 9. Запуск приложения и обработка запросов

## 9.1 Запуск

При запуске:

1. Spring Boot читает конфигурацию;
2. сканирует пакеты;
3. создаёт компоненты;
4. подключается к PostgreSQL;
5. Flyway применяет миграции;
6. Hibernate выполняет `validate`;
7. настраивается Spring Security;
8. запускается Tomcat;
9. приложение принимает HTTP-запросы.

Во время разработки:

```text
IntelliJ
        ↓
Spring Boot application

Docker Compose
        ↓
PostgreSQL
```

Приложение запускается из IntelliJ, база — в контейнере.

## 9.2 Конфигурация

Основной формат:

```text
application.properties
```

Секреты передаются через переменные окружения:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

Рекомендуемые профили:

```text
application.properties
application-local.properties
application-test.properties
```

Целевая настройка:

```properties
spring.jpa.open-in-view=false
```

Необходимые связанные данные загружаются внутри Service.

## 9.3 GET-запрос

```text
GET /equipment
        ↓
Spring Security
        ↓
EquipmentController
        ↓
EquipmentService
        ↓
EquipmentRepository
        ↓
PostgreSQL
        ↓
данные
        ↓
Model
        ↓
equipment/list.html
        ↓
готовый HTML
```

## 9.4 POST-запрос

```text
POST /repair-requests
        ↓
Spring Security
        ↓
CSRF
        ↓
RepairRequestCreateForm
        ↓
Bean Validation
        ↓
Controller
        ↓
RepairRequestService
        ↓
business rules
        ↓
Repository
        ↓
PostgreSQL
        ↓
redirect
        ↓
GET страницы заявки
```

После успешного POST используется Post / Redirect / Get, чтобы обновление страницы не повторяло операцию.

Ошибки формы возвращают пользователя на ту же форму. Бизнес-ошибки обнаруживаются в Service и не позволяют сохранить запрещённое состояние.

---

# 10. Транзакции, lazy loading и N+1

Операции, меняющие несколько сущностей, выполняются атомарно.

Главный пример:

```text
закрытие RepairRequest
        +
создание MaintenanceRecord
```

Результат:

```text
либо сохраняются оба изменения
либо не сохраняется ничего
```

Недопустимо:

```text
RepairRequest = CLOSED
MaintenanceRecord отсутствует
```

Hibernate dirty checking используется осознанно внутри транзакции.

Для загрузки данных страниц применяются:

- `JOIN FETCH`;
- `@EntityGraph`;
- DTO-проекции;
- специальные Repository-запросы;
- подготовка View DTO в Service.

Особенно проверяются списки заявок и оборудования, чтобы не допустить N+1.

---

# 11. Принцип реализации

Функции реализуются вертикально.

Не используется подход:

```text
сначала все Entity
потом все Repository
потом все Service
потом все Controller
```

Используется:

```text
один пользовательский сценарий
        ↓
Entity и миграция
        ↓
Repository
        ↓
Service
        ↓
Controller
        ↓
Thymeleaf
        ↓
тесты
        ↓
коммит
```

После каждого этапа проект должен компилироваться, запускаться и проходить существующие тесты.

---

# 12. Общая последовательность реализации

1. технический каркас;
2. User и вход;
3. Equipment;
4. создание и просмотр RepairRequest;
5. назначение TECHNICIAN;
6. полный жизненный цикл RepairRequest;
7. автоматическое создание MaintenanceRecord;
8. история обслуживания;
9. управление пользователями;
10. dashboard;
11. фильтрация и пагинация;
12. обработка ошибок;
13. финальная проверка бизнес-правил и безопасности;
14. CI, контейнеризация и README.

---

# 13. Подробная дорожная карта

## 13.1 Инициализация проекта

Параметры:

```text
Project: Maven
Language: Java
Spring Boot: 3.5.x
Java: 21
Packaging: Jar
Group: com.vladyslav
Artifact: industrial-maintenance-tracker
Package: com.vladyslav.industrialmaintenancetracker
```

Начальные зависимости:

- Spring Web;
- Thymeleaf;
- Spring Security;
- Spring Data JPA;
- Validation;
- PostgreSQL Driver;
- Flyway;
- DevTools;
- Spring Boot Starter Test;
- Testcontainers PostgreSQL;
- Spring Security Test.

Коммит:

```text
chore: initialize Spring Boot project
```

## 13.2 PostgreSQL и Docker Compose

Создать:

```text
compose.yaml
.env.example
```

`.env` добавить в `.gitignore`.

Проверить:

```text
docker compose up -d
docker compose ps
```

Коммит:

```text
chore: add PostgreSQL Docker Compose setup
```

## 13.3 Конфигурация и Flyway

Создать:

```text
src/main/resources/application.properties
src/main/resources/db/migration/
```

Настроить:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
```

Коммит:

```text
chore: configure Flyway database migrations
```

## 13.4 Проверка Spring MVC и Thymeleaf

Создать минимальный Controller и HTML-страницу.

Контрольная точка:

- Spring Boot запускается;
- PostgreSQL доступен;
- Flyway работает;
- Thymeleaf формирует HTML.

Коммит:

```text
feat: add basic Thymeleaf home page
```

## 13.5 User и начальный ADMIN

Порядок:

1. `UserRole`;
2. `User`;
3. миграция таблицы users;
4. `UserRepository`;
5. Repository-тесты;
6. bootstrap первого ADMIN.

Коммиты:

```text
feat: add user domain and persistence
feat: add initial admin bootstrap
```

## 13.6 Spring Security

Порядок:

1. PasswordEncoder;
2. CustomUserDetails;
3. CustomUserDetailsService;
4. SecurityConfig;
5. login.html;
6. dashboard;
7. logout;
8. 403;
9. security-тесты.

Коммит:

```text
feat: implement session-based authentication
```

## 13.7 Общий HTML-каркас

Создать:

```text
templates/
├── login.html
├── dashboard.html
└── fragments/
    ├── head.html
    ├── navigation.html
    └── messages.html
```

Коммит:

```text
feat: add shared Thymeleaf layout and navigation
```

## 13.8 Equipment

### Domain и persistence

- EquipmentStatus;
- Equipment;
- Flyway-миграция;
- EquipmentRepository.

```text
feat: add equipment domain and persistence
```

### Просмотр

- EquipmentService;
- EquipmentController;
- список;
- детали;
- 404;
- права.

```text
feat: add equipment list and details
```

### Создание

- EquipmentCreateForm;
- GET формы;
- POST;
- валидация;
- уникальность.

```text
feat: add equipment creation
```

### Редактирование и статус

- редактирование разрешённых полей;
- активация;
- деактивация;
- ADMIN-only.

```text
feat: add equipment editing and status management
```

## 13.9 RepairRequest: создание и просмотр

### Domain и persistence

- RepairRequestStatus;
- RepairRequestPriority;
- RepairRequest;
- Flyway-миграция;
- RepairRequestRepository.

Связи:

```text
RepairRequest → Equipment
RepairRequest → createdBy User
RepairRequest → assignedTechnician User
```

```text
feat: add repair request domain and persistence
```

### Создание

- RepairRequestCreateForm;
- createRequest;
- GET формы;
- POST;
- начальный статус и служебные поля задаются приложением.

```text
feat: add repair request creation
```

### Список и детали

- список;
- детали;
- видимость по ролям;
- доступ к конкретной заявке.

```text
feat: add repair request list and details
```

## 13.10 Назначение техника

Создать отдельную операцию:

```text
assignTechnician(...)
```

Проверить право ADMIN, статус заявки, существование, активность и роль TECHNICIAN.

```text
feat: add technician assignment
```

## 13.11 Начало работы

Создать:

```text
startWork(...)
POST /repair-requests/{id}/start
```

Проверить, что текущий TECHNICIAN активен и заявка назначена именно ему.

```text
feat: add repair request work start
```

## 13.12 Завершение ремонта

Создать:

- RepairRequestCompleteForm;
- completeRepair;
- форму результата;
- проверки обязательных данных;
- переход, определённый документом 03.

```text
feat: add repair completion workflow
```

## 13.13 Закрытие и MaintenanceRecord

Создать:

- MaintenanceRecord;
- миграцию;
- MaintenanceRecordRepository;
- closeRequest.

Операция в одной транзакции:

1. загружает заявку;
2. проверяет права;
3. проверяет статус;
4. проверяет результат;
5. закрывает заявку;
6. устанавливает время;
7. создаёт MaintenanceRecord;
8. сохраняет всё атомарно.

```text
feat: close repair requests with maintenance record
```

## 13.14 Отмена

Создать:

```text
cancelRequest(...)
```

Не использовать универсальный `changeStatus`.

```text
feat: add repair request cancellation
```

## 13.15 История обслуживания

Создать:

- MaintenanceRecordService;
- MaintenanceRecordController;
- список;
- детали;
- историю Equipment;
- переход к исходной заявке.

Не создавать ручное создание, редактирование и удаление.

```text
feat: add maintenance history views
```


## 13.16 Управление пользователями

ADMIN должен иметь возможность:

- просматривать пользователей;
- создавать пользователя;
- задавать роль;
- изменять разрешённые данные;
- деактивировать;
- задавать новый временный пароль.

Проверить:

- уникальность username;
- хеширование пароля;
- корректность роли;
- защиту последнего активного ADMIN либо явно зафиксировать ограничение.

Примерные коммиты:

```text
feat: add user management views
feat: add user creation and editing
feat: add user activation management
```

## 13.17 Dashboard

Dashboard создаётся после появления реальных данных.

### ADMIN

- новые заявки;
- неназначенные;
- в работе;
- завершённые, ожидающие закрытия.

### TECHNICIAN

- назначенные мне;
- в работе;
- недавно завершённые.

### REQUESTER

- мои открытые;
- последние изменения;
- создание новой заявки.

```text
feat: add role-based dashboard
```

## 13.18 Фильтрация и пагинация

Добавить:

- статус;
- приоритет;
- техника;
- оборудование;
- сортировку;
- пагинацию.

```text
feat: add repair request filtering and pagination
```

## 13.19 Обработка ошибок

Создать:

```text
ResourceNotFoundException
BusinessRuleException
GlobalExceptionHandler
```

Шаблоны:

```text
error/403.html
error/404.html
error/500.html
```

```text
feat: add global error handling
```

## 13.20 Усиление безопасности

Повторно проверить:

- URL restrictions;
- доступ к конкретным сущностям;
- CSRF;
- прямые POST-запросы;
- изменение ID в URL;
- чужие заявки;
- деактивированных пользователей.

```text
test: strengthen authorization coverage
```

## 13.21 GitHub Actions

Минимальный CI:

```text
checkout
setup Java 21
Maven cache
./mvnw verify
```

```text
ci: add Maven test workflow
```

## 13.22 Dockerfile приложения

Добавить после стабилизации приложения.

```text
chore: add application Docker image
```

## 13.23 Демонстрационные данные

Подготовить:

- ADMIN;
- двух TECHNICIAN;
- двух REQUESTER;
- оборудование;
- заявки в разных статусах;
- несколько MaintenanceRecord.

Данные должны включаться через отдельный local/demo-профиль или другой контролируемый механизм.

## 13.24 README

Финальный README содержит:

- описание и цель;
- роли;
- функции;
- стек;
- архитектуру;
- скриншоты;
- инструкцию запуска;
- переменные окружения;
- запуск PostgreSQL;
- запуск тестов;
- ограничения MVP;
- планы развития.

---

# 14. Git и коммиты

Коммиты небольшие и логические.

Хорошие примеры:

```text
chore: initialize Spring Boot project
chore: add PostgreSQL Docker Compose setup
feat: add user domain and persistence
feat: implement session-based authentication
feat: add equipment creation
feat: add repair request creation
feat: add technician assignment
feat: add repair completion workflow
feat: close repair requests with maintenance record
test: cover repair request status transitions
```

Перед коммитом:

```text
git status
git diff
.\mvnw test
```

Коммит делается, когда:

- код компилируется;
- тесты проходят;
- функция закончена на текущем уровне;
- секреты отсутствуют;
- временные файлы отсутствуют.

Для проекта достаточно:

```text
main
feature/...
```

Примеры:

```text
feature/security
feature/equipment-management
feature/repair-request-creation
feature/technician-assignment
```

Сложная Git Flow не нужна.

---

# 15. Стратегия тестирования

Тесты нужны не ради максимального процента покрытия.

Главные цели:

- подтвердить бизнес-правила;
- защитить переходы статусов;
- проверить роли;
- проверить работу с PostgreSQL;
- предотвратить регрессии.

Уровни:

1. unit-тесты Service;
2. Repository-тесты на PostgreSQL;
3. MVC- и Security-тесты;
4. интеграционные тесты ключевых сценариев.

## 15.1 Unit-тесты Service

Инструменты:

```text
JUnit 5
Mockito
AssertJ
```

Spring-контекст не запускается.

Мокируются Repository, CurrentUserService и другие внешние зависимости тестируемого Service.

Проверяются:

- успешный сценарий;
- неподходящая роль;
- чужой пользователь;
- неправильный статус;
- отсутствующая сущность;
- невалидная связь;
- повторное действие;
- пограничные случаи.

Основной объём тестов приходится на RepairRequestService.

## 15.2 Переходы статусов

Для каждого перехода нужны отдельные тесты.

Проверяются:

- разрешённые исходные состояния;
- запрещённые исходные состояния;
- правильные временные отметки;
- обязательные поля;
- права;
- невозможность повторного выполнения.

Точные enum-значения должны совпадать с документом 03.

## 15.3 Закрытие заявки

Обязательно проверить:

- допустимый исходный статус;
- установку финального статуса;
- closedAt;
- создание MaintenanceRecord;
- связь с Equipment;
- связь с RepairRequest;
- сохранение исполнителя;
- перенос результата;
- создание ровно одной записи;
- запрет повторного закрытия.

## 15.4 Транзакционность

Mockito не доказывает rollback.

Нужен интеграционный тест:

1. существует завершённая заявка;
2. начинается закрытие;
3. создание MaintenanceRecord завершается ошибкой;
4. транзакция откатывается;
5. заявка остаётся в исходном состоянии.

## 15.5 Repository-тесты

Инструменты:

```text
@DataJpaTest
Testcontainers
PostgreSQL
Flyway
```

H2 не используется.

Проверяются:

- кастомные запросы;
- поиск по username;
- уникальность username;
- уникальность inventory number;
- заявки пользователя;
- заявки техника;
- фильтры;
- сортировка;
- история оборудования;
- активные техники;
- внешние ключи;
- обязательные поля.

Стандартные методы JpaRepository отдельно не тестируются.

## 15.6 Flyway в тестах

```text
Testcontainers запускает PostgreSQL
        ↓
Flyway применяет настоящие миграции
        ↓
Hibernate выполняет validate
        ↓
запускается тест
```

Тестовая схема не создаётся через `create-drop`.

## 15.7 Controller-тесты

Инструменты:

```text
@WebMvcTest
MockMvc
Spring Security Test
```

Service мокируется.

Проверяются:

### GET

- URL;
- статус;
- доступ;
- шаблон;
- Model;
- redirect на login.

### POST

- binding;
- Bean Validation;
- BindingResult;
- вызов Service;
- redirect;
- flash-сообщение;
- CSRF;
- запрет неподходящей роли.

## 15.8 Security-тесты

Проверяются:

- неавторизованный доступ;
- доступ по ролям;
- POST без CSRF;
- ADMIN-only операции;
- чужая заявка;
- изменение ID;
- деактивированный пользователь;
- login;
- logout.

## 15.9 Интеграционные тесты

Инструменты:

```text
@SpringBootTest
MockMvc
Testcontainers PostgreSQL
Flyway
Spring Security
```

Основные сценарии:

1. вход;
2. создание оборудования;
3. создание заявки;
4. назначение и выполнение;
5. закрытие и MaintenanceRecord;
6. запрещённый доступ.

Selenium, Playwright и Cypress для MVP не обязательны.

## 15.10 Время и тестовые данные

Для времени рекомендуется использовать `java.time.Clock`.

Production:

```text
Clock.systemUTC()
```

Test:

```text
fixed Clock
```

Допустимы небольшие test factories:

```text
TestUserFactory
TestEquipmentFactory
TestRepairRequestFactory
```

Они находятся только в `src/test/java`.

## 15.11 Что не тестируется отдельно

Не нужны отдельные тесты на:

- getter и setter;
- простые enum;
- стандартный JpaRepository.save;
- каждый HTML-тег;
- цвета Bootstrap;
- код Spring Framework;
- конфигурацию без собственной логики.

## 15.12 Покрытие

Цель не равна 100%.

Приоритет:

- Service;
- бизнес-переходы;
- права;
- Controller и Security;
- точечные Repository-тесты;
- несколько сильных интеграционных сценариев.


---

# 16. Что сознательно не реализуется в MVP

## Архитектура и frontend

- микросервисы;
- отдельный frontend;
- React;
- Angular;
- Vue;
- WebFlux;
- API Gateway.

## API и авторизация

- публичный REST API;
- Swagger / OpenAPI;
- JWT;
- refresh token;
- OAuth;
- OpenID Connect;
- Keycloak;
- внешние провайдеры входа;
- публичная регистрация;
- подтверждение email;
- восстановление пароля по email;
- 2FA;
- динамические permissions.

## Бизнес-функции

- плановое ТО;
- повторяющиеся задания;
- календарь обслуживания;
- склад запчастей;
- поставщики;
- закупки;
- стоимость ремонта;
- загрузка файлов;
- фотографии;
- PDF-вложения;
- уведомления;
- email;
- SMS;
- push;
- QR-коды;
- штрихкоды;
- мобильное приложение;
- сложная аналитика;
- MTTR;
- MTBF;
- прогнозирование отказов;
- экспорт PDF, Excel и CSV;
- мультиязычность.

## Инфраструктура

- Kafka;
- RabbitMQ;
- Redis;
- Elasticsearch;
- Kubernetes;
- Helm;
- Terraform;
- service mesh;
- сложный CI/CD;
- production high availability;
- репликация PostgreSQL;
- Prometheus;
- Grafana;
- distributed tracing.

## Избыточные абстракции

- интерфейс и ServiceImpl для каждого Service;
- BaseCrudService;
- BaseController;
- GenericRepository;
- универсальный changeStatus;
- CQRS;
- Event Sourcing;
- Saga;
- сложная Hexagonal Architecture;
- сложная Clean Architecture с несколькими Maven-модулями;
- Lombok;
- MapStruct;
- универсальный mapper framework.

## Физическое удаление

Не создаются пользовательские операции физического удаления:

- User;
- Equipment;
- RepairRequest;
- MaintenanceRecord.

Используются деактивация, статус, архивирование и сохранение истории.

---

# 17. Контрольные точки

## A — Инфраструктура

Готово:

- Spring Boot;
- PostgreSQL;
- Docker Compose;
- Flyway;
- Thymeleaf.

Проверка:

- приложение запускается;
- миграции выполняются;
- страница открывается.

## B — Вход

Готово:

- User;
- ADMIN;
- Spring Security;
- login;
- logout;
- роли.

Проверка:

- ADMIN входит;
- неавторизованный доступ запрещён.

## C — Equipment

Готово:

- создание;
- просмотр;
- редактирование;
- деактивация;
- валидация;
- права.

Проверка:

- ADMIN управляет оборудованием.

## D — RepairRequest

Готово:

- создание;
- список;
- детали;
- видимость по ролям.

Проверка:

- REQUESTER создаёт и видит разрешённую заявку.

## E — Ремонтный процесс

Готово:

- назначение;
- начало работы;
- завершение;
- закрытие;
- отмена.

Проверка:

- разрешённые переходы работают;
- запрещённые блокируются.

## F — Maintenance history

Готово:

- MaintenanceRecord;
- автоматическое создание;
- история Equipment.

Проверка:

- закрытая заявка создаёт запись истории.

## G — Готовый MVP

Готово:

- управление пользователями;
- dashboard;
- фильтры;
- ошибки;
- тесты;
- README;
- демонстрационные данные.

Проверка:

- полный сценарий проходит от входа до истории ремонта.

---

# 18. Критерии готовности технического каркаса

Технический каркас готов, когда:

- Spring Boot запускается;
- PostgreSQL работает через Docker Compose;
- приложение подключается к базе;
- Flyway выполняет миграции;
- Hibernate выполняет validate;
- Thymeleaf формирует HTML;
- Spring Security защищает страницы;
- ADMIN входит и выходит;
- тесты запускаются через Maven Wrapper.

---

# 19. Критерии готовности ядра MVP

Ядро готово, когда работает полный путь:

```text
ADMIN создаёт пользователей
        ↓
ADMIN создаёт оборудование
        ↓
REQUESTER создаёт заявку
        ↓
ADMIN назначает техника
        ↓
TECHNICIAN начинает работу
        ↓
TECHNICIAN фиксирует результат
        ↓
заявка завершается
        ↓
заявка закрывается
        ↓
автоматически создаётся MaintenanceRecord
        ↓
история отображается у Equipment
```

При этом:

- роли соблюдаются;
- запрещённые переходы блокируются;
- чужие данные защищены;
- формы валидируются;
- ошибки понятны;
- транзакции работают;
- ключевые сценарии покрыты тестами.

---

# 20. Критерии готовности полного MVP

Полный MVP готов, если:

1. реализованы сценарии документа 02;
2. соблюдены правила документа 03;
3. работают роли ADMIN, TECHNICIAN и REQUESTER;
4. работает полный жизненный цикл RepairRequest;
5. MaintenanceRecord создаётся автоматически;
6. история Equipment доступна;
7. ADMIN управляет пользователями;
8. работают валидация и обработка ошибок;
9. защищены URL и конкретные данные;
10. Flyway управляет схемой;
11. проект запускается по README;
12. тесты проходят;
13. в репозитории нет секретов;
14. GitHub Actions проверяет сборку;
15. основной сценарий можно продемонстрировать вручную.

---

# 21. Критерии качества портфолио-проекта

Проект должен демонстрировать:

- понятную структуру;
- чёткое разделение ответственности;
- реальные бизнес-правила;
- контролируемые переходы статусов;
- ролевой доступ;
- защиту данных;
- миграции;
- транзакции;
- осмысленные тесты;
- небольшие логичные коммиты;
- понятную документацию;
- воспроизводимый запуск.

Проект не должен притворяться готовой промышленной системой.

В README нужно честно указать:

- это портфолио-MVP;
- какие функции реализованы;
- какие ограничения существуют;
- какие улучшения возможны после MVP.

---

# 22. Итоговое решение

Industrial Maintenance Tracker строится на следующем наборе:

```text
Java 21
        +
Spring Boot
        +
Spring MVC
        +
Spring Security
        +
Spring Data JPA / Hibernate
        +
Thymeleaf / Bootstrap
        +
PostgreSQL
        +
Flyway
        +
JUnit / Mockito / MockMvc / Testcontainers
        +
Docker Compose
        +
GitHub Actions
```

Архитектура:

```text
модульный монолит
+
функциональные пакеты
+
слои Controller, Service, Repository и Domain
```

Главный принцип реализации:

```text
не создавать всю систему сразу

реализовывать один законченный сценарий

проверять его

покрывать тестами

делать логический коммит

только затем переходить дальше
```

---

# 23. Следующий этап

Следующий этап проекта:

```text
05 — Создание Spring Boot-проекта и технического каркаса
```

На этапе 05 начинается практическая разработка:

- генерация Spring Boot-проекта;
- настройка Maven;
- подключение PostgreSQL;
- создание `compose.yaml`;
- настройка переменных окружения;
- подключение Flyway;
- первый запуск приложения;
- проверка Thymeleaf;
- первый технический коммит.
