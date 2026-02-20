![License](https://img.shields.io/badge/License-AGPL%20v3.0-blue.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen.svg)
![Python](https://img.shields.io/badge/Python-3.12-blue.svg)
![Next.js](https://img.shields.io/badge/Next.js-16-black.svg)

# AL9oo

## Introduction

**AL9oo** is a service that provides lap time references for the mobile racing game [Racing Master](https://www.racingmaster.game/).

- Lap time references sourced from YouTube videos
- Available via **Web** (Frontend + Backend) or **Discord Bot**
- Both clients share the same data through a unified REST API

---

## Development Period

- **Started**: January 9, 2026
- **Status**: In Development

---

## Contributors

| Name | GitHub |
|------|--------|
| Gooraeng | [Gooraeng](https://github.com/Gooraeng) |

---

## Monorepo Structure

```
AL9oo/
├── backend/    # Spring Boot REST API
├── discord/    # Discord Bot (Python)
├── frontend/   # Next.js Web UI
└── infra/      # Terraform Based Infrastructure Management
```

---

## Tech Stack

| Component | Stack |
|-----------|-------|
| Backend | Java 25, Spring Boot 4.0.1, JPA, Redis, JWT, OAuth2 |
| Discord Bot | Python 3.12, discord.py 2.6 |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS v4 |
| Infra | Terraform 1.14 | 

> **Discord Bot**: Service was terminated on Apr 16, 2025 — Resuming soon.

---

## License

This project is licensed under the [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.html).
