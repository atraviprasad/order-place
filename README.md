# order-place
## Overview

Order Place is a B2B enterprise application that lets corporate and institutional customers (offices, universities, etc.) design their workspace layouts and place bulk furniture orders. Orders are routed to partner dealers, who fulfil them through a structured order-management workflow.

**Project Structure**

order-place/
├── pom.xml                      # parent POM (versions, plugins, module list)
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml           # infrastructure (from the earlier skeleton)
├── docker-compose.apps.yml      # application services (added as you build)
├── infra/
│   ├── postgres/init/           # database creation SQL
│   └── keycloak/realm/          # realm export JSON
├── libs/                        # small shared libraries (see section 5)
│   ├── common-events/           # event envelope and event records
│   ├── common-web/              # error handling, correlation ID, pagination
│   └── common-security/         # JWT claims helpers, tenant context
├── services/
│   ├── api-gateway/             # Spring Cloud Gateway
│   ├── user-service/
│   ├── catalog-service/
│   ├── dealer-service/
│   ├── order-service/
│   ├── payment-service/
│   └── notification-service/
├── frontend/                    # React app (later)
├── docs/                        # requirements, architecture, API, diagrams
└── .github/workflows/           # CI pipelines
