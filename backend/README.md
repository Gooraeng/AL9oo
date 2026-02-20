![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.1-brightgreen?logo=springboot)
![Gradle](https://img.shields.io/badge/Build-Gradle_Kotlin_DSL-blue?logo=gradle)
![License](https://img.shields.io/badge/License-AGPL_v3.0-blue.svg)

# AL9oo Racing Reference — Backend

Spring Boot REST API for the Racing Master lap time reference service

---

## Overview

Backend API server for managing lap time references for the Racing Master game, based on YouTube videos.

**Architecture Flow**:
- `Web Browser (FE) → BE → DB`
- `Discord Bot → BE → DB`

Both clients share the same REST API.

---

## Architecture

```
┌──────────────────────────────────────────────────┐
│                    Clients                       │
│   Web Browser (FE)          Discord Bot          │
└────────────┬───────────────────────┬─────────────┘
             │ HTTP/REST             │ HTTP/REST
             ▼                       ▼
┌──────────────────────────────────────────────────┐
│         Spring Boot Backend (:8080)              │
│  ┌──────────────┐  ┌──────────┐  ┌──────────┐   │
│  │  Auth Layer  │  │  Domain  │  │  Global  │   │
│  │  JWT+OAuth2  │  │  Layer   │  │  Layer   │   │
│  └──────┬───────┘  └────┬─────┘  └────┬─────┘   │
└─────────┼───────────────┼─────────────┼──────────┘
          ▼               ▼             ▼
       Redis           JPA/DB      YouTube API
     (Sessions)   (H2/MySQL/Oracle)   (v3)
```

### Package Structure

```
com.back
├── domain/
│   ├── car/               # Car management (CRUD + soft delete)
│   ├── manufacturer/      # Manufacturer management
│   ├── reference/         # Lap time references + YouTube video linking
│   │   ├── scheduler/     # ReferenceStatusRenewScheduler
│   │   └── type/          # ReferenceStatus, ReferenceType enums
│   ├── home/              # Home controller
│   ├── admin/             # (In development)
│   ├── track/             # (In development)
│   └── user/
│       ├── auth/          # AuthAccount, AccountLink, Session management
│       └── member/        # Member profile + use cases
├── global/
│   ├── entity/            # BaseEntity → BaseTime → Editable → SoftDeletable
│   ├── exception/         # RFC 7807 (ApiException, ErrorCode)
│   ├── external/video/    # VideoClient interface + YouTubeClient
│   ├── property/          # @ConfigurationProperties
│   ├── security/
│   │   ├── jwt/           # JwtTokenService, JwtAuthenticationFilter, Redis RefreshToken
│   │   ├── oauth2/        # Strategy pattern (Login/AccountLink), handlers
│   │   ├── local/         # LocalAuthService
│   │   └── ratelimit/     # AccountLinkRateLimiter
│   └── temp/
│       ├── email/         # Hexagonal: SendEmailPort / VerifyEmailPort
│       └── nonce/         # Nonce management
└── utils/                 # BatchHelper, YouTubeVideoIdParser, url/
```

---

## Tech Stack

| Category | Technology | Version |
|---|---|---|
| Framework | Spring Boot | 4.0.1 |
| Language | Java (Adoptium) | 25 |
| Build | Gradle (Kotlin DSL) | - |
| ORM | Spring Data JPA | - |
| DB (dev) | H2 (MySQL mode) | - |
| DB (prod) | MySQL / Oracle | - |
| Cache/Session | Redis (Embedded dev / Standalone prod) | - |
| Security | Spring Security + OAuth2 Client | - |
| JWT | JJWT | 0.13.0 |
| External API | Google YouTube Data API v3 | v3-rev20251217 |
| API Docs | SpringDoc OpenAPI (Swagger UI) | 2.8.9 |
| Monitoring | Actuator + Micrometer Prometheus | - |
| Boilerplate | Lombok | - |

---

## Getting Started

### Prerequisites

- JDK 25 (Adoptium)
- Gradle Wrapper included (use `./gradlew`)

### Create .env File

Create a `.env` file in the project root (`backend/`).

```properties
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
YOUTUBE_API_KEY=...
DISCORD_CLIENT_ID=...
DISCORD_CLIENT_SECRET=...
REFRESH_TOKEN_KEY_PREFIX=al9oo:rt:
```

### Run

```bash
# Run with dev profile (H2 + Embedded Redis)
./gradlew bootRun
```

### Access

| URL | Description |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI (dev only) |
| `http://localhost:8080/h2-console` | H2 Console (dev only) |
| `http://localhost:8080/actuator/prometheus` | Prometheus metrics |

---

## Configuration

### Profile Differences

| Property | dev | test | prod |
|---|---|---|---|
| Database | H2 (file) | H2 (in-memory) | MySQL / Oracle |
| Redis | Embedded | Embedded | Standalone |
| Swagger | Disabled* | Disabled | Disabled |
| DDL Auto | update | create-drop | validate |
| Virtual Threads | Enabled | - | - |
| AT Cookie | `dev-pat` | - | `__Host-pat` |
| RT Cookie | `dev-prt` | - | `__Host-prt` |

> *Swagger is currently disabled even in dev per `application.yml`. Enable with `springdoc.swagger-ui.enabled: true` if needed.

### Token Settings

| Token | Duration | SameSite | Path |
|---|---|---|---|
| Access Token | 10 min | Strict | `/` |
| Refresh Token | 14 days | Strict | `/api/*/auth/session` |

- **RTR (Refresh Token Rotation)**: Issues a new RT on every refresh + stores in Redis
- If reuse is detected in Redis, all sessions for that Member are invalidated

---

## API Reference

Full API docs: `http://localhost:8080/swagger-ui.html` (dev only)

### Home

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/home` | Public | Home data |

### Car

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/car` | Public | List all cars |
| GET | `/api/v1/car/{id}` | Public | Get car detail |
| POST | `/api/v1/car` | ADMIN | Add a car |
| PATCH | `/api/v1/car/{id}` | ADMIN | Update car |
| DELETE | `/api/v1/car/{id}` | ADMIN | Soft-delete car |

### Manufacturer

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/manufacturer` | ADMIN | Add manufacturer |
| PATCH | `/api/v1/manufacturer/{id}` | ADMIN | Update manufacturer |
| DELETE | `/api/v1/manufacturer/{id}` | ADMIN | Delete manufacturer |

### Auth

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/auth/check-display-name/{name}` | Public | Check display name availability |
| POST | `/api/v1/auth/oauth2/signup` | GUEST | Complete OAuth2 signup |

### Session

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/session/logout` | GUEST | Logout current device |
| POST | `/api/v1/auth/session/refresh-token` | Public (RT cookie) | Rotate tokens (RTR) |
| GET | `/api/v1/auth/session` | GUEST | List active sessions |
| DELETE | `/api/v1/auth/session/invalidate?deviceId=` | GUEST | Remove specific session |
| DELETE | `/api/v1/auth/session/invalidate/all` | GUEST | Remove all sessions |

### Account Linking

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/auth/account-link` | GENERAL | Get linked providers |
| POST | `/api/v1/auth/account-link/start?provider=` | GENERAL | Start OAuth linking |
| DELETE | `/api/v1/auth/account-link/unlink?provider=` | GENERAL | Unlink provider |

### Member

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/member/{memberId}` | Public | Public profile |
| GET | `/api/v1/member/me` | Authenticated | Own private profile |
| PATCH | `/api/v1/member/me` | Authenticated | Update profile |
| DELETE | `/api/v1/member/me` | Authenticated | Withdraw |

### Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/reference` | - | Planned (not yet implemented) |

### OAuth2 Endpoints (Managed by Spring Security)

- `GET /oauth2/authorization/google`
- `GET /oauth2/authorization/discord`

---

## Authentication Flow

### Role Hierarchy

```
GUEST (OAuth login, display name not set)
  └── GENERAL (Signup complete)
        └── ADMIN (Administrator)
```

### OAuth2 New User Signup Flow

```
Client → /oauth2/authorization/google → Google consent → Callback
→ [New user] Issue GUEST JWT → FE signup page
→ POST /api/v1/auth/oauth2/signup {displayName, termsAccepted}
→ Role upgrade: GUEST → GENERAL
→ Issue new AT + RT
```

### RTR Token Refresh

```
POST /auth/session/refresh-token (RT cookie)
→ Look up / validate / delete RT from Redis
→ Issue new AT + RT + store in Redis

[If reuse detected]
→ Delete all sessions for that Member → 401 SESSION_EXPIRED
```

### Account Linking Flow

```
POST /auth/account-link/start?provider=google
→ Generate linkCode (stored in Redis)
→ Return oauthUrl (linkCode encoded in state)
→ OAuth auth complete → AccountLinkStrategy executed
→ Add new AuthAccount to existing Member
```

---

## Data Model

### Entity Hierarchy

```
BaseEntity (id)
  └── BaseTime (+ createdDate)
        └── Editable (+ modifiedDate)
              └── SoftDeletable (+ deleted, markAsDeleted())
```

### Key Relationships

- `Manufacturer` 1:N `Car`
- `Member` 1:N `AuthAccount` (LOCAL / GOOGLE / DISCORD)
- `Member` 1:N `Reference`
- `Reference` N:1 `ReferenceVideo`
- `Reference` N:1 `Car`

---

## Design Patterns

| Pattern | Location | Description |
|---|---|---|
| Use Case Pattern | `auth/useCase/`, `member/useCase/` | `AccountLinkCase`, `Oauth2SignUpOrLoginCase`, `MemberInfoUseCase` |
| Strategy Pattern | `security/oauth2/` | `LoginStrategy` / `AccountLinkStrategy` (OAuth2 processing) |
| Factory Pattern | `global/external/video/` | `VideoClientFactory` (VideoProvider enum-based) |
| Hexagonal Architecture | `global/temp/email/` | `SendEmailPort` / `VerifyEmailPort` (Port/Adapter separation) |
| Soft Delete | `global/entity/` | `SoftDeletable.markAsDeleted()` |
| BaseMapper | `global/mapper/` | `BaseMapper<D,E>` generic interface |

---

## Testing

```bash
# Run all tests (test profile: H2 in-memory)
./gradlew test

# Run a specific test class
./gradlew test --tests "com.back.domain.car.service.CarServiceTest"

# Run a specific test method
./gradlew test --tests "com.back.domain.car.service.CarServiceTest.testAddCar"
```

### Test Types

| Type | Examples |
|---|---|
| Unit (Service) | `CarServiceTest`, `SessionManagementServiceTest`, `MemberInfoUseCaseTest` |
| Controller | `CarControllerV1Test`, `AuthControllerV1Test`, `SessionControllerV1Test` |
| Integration | `AccountLinkIntegrationTest`, `Oauth2SignUpOrLoginIntegrationTest` |

- `VideoClient` and `SendEmailPort` are always mocked to prevent external API calls
- `TestSecurityConfig` overrides Security context in Controller tests

---

## Scheduled Tasks

| Scheduler | Cron | Description |
|---|---|---|
| `ReferenceStatusRenewScheduler` | `* * 4 * * *` (around 4 AM daily) | Fetches all `ReferenceVideo` records in batches of 50 via YouTube API → syncs status. Missing videos are marked `UNAVAILABLE`. |

---

## Error Handling

Follows RFC 7807 Problem Details format.

```json
{
  "type": "...",
  "title": "...",
  "status": 401,
  "detail": "...",
  "errorCode": "AUTH001"
}
```

| Prefix | Category |
|---|---|
| `AUTH` | Authentication/Authorization |
| `COM` | Common |
| `CAR` | Car |
| `MAN` | Manufacturer |
| `MEM` | Member |
| `EMA` | Email |

---

## Monitoring

| Endpoint | Description |
|---|---|
| `/actuator/prometheus` | Expose Prometheus metrics |

> Redis health check is disabled in dev (Embedded Redis `INFO` command parsing issue).

---

## Project Structure

```
AL9oo-Refactor/
├── backend/      ← This application (Spring Boot REST API)
├── discord/      # Discord Bot (Python)
├── frontend/     # Next.js Web UI (planned)
└── infra/        # Infrastructure (Terraform)
```
