# Omnia

[![Build Status](https://github.com/derandomize/omnia/actions/workflows/gradle-tests.yml/badge.svg)](https://github.com/derandomize/omnia/actions/workflows/gradle-tests.yml)

*A Transparent Scaling Layer for OpenSearch*

Omnia addresses a critical challenge in OpenSearch: the performance degradation and operational complexity that arise when managing tens or hundreds of thousands of indices (soft limit is 1000 and hard limit is 10000), . This is a common scenario in multi-tenant architectures where each tenant is provisioned with a dedicated index.

This project introduces a collection of tools that decouples your application's logical indices from the physical indices in OpenSearch. It achieves this by grouping multiple logical indices into larger physical indices called **"communes"**. The result is a system that can gracefully scale to a massive number of indices while preserving your existing application code.

## Features
- Plugs directly into your existing OpenSearch Java Client code with a one-line change.
- A background `Migrator` service automatically monitors commune size and splits them when they grow too large, ensuring cluster health and performance.
- Can work with thousand of indexes with minimal overhead on latency

## Benchmark

Comparing Standard client and omnia shows following results:

| Client Type | Index Count | Score (ms/op) |
|:------------|------------:|--------------:|
| **STANDARD**| 10          |         0.585 |
|             | 400         |         0.601 |
|             | 800         |          DEAD |
|             | 2,000       |          DEAD |
|             | 5,000       |          DEAD |
|             | 10,000      |          DEAD |
|             | 50,000      |          DEAD |
|             | 100,000     |          DEAD |
| **OMNIA**   | 10          |         0.882 |
|             | 400         |         0.916 |
|             | 800         |         0.860 |
|             | 2,000       |         0.940 |
|             | 5,000       |         0.923 |
|             | 10,000      |         0.865 |
|             | 50,000      |         0.946 |
|             | 100,000     |         0.938 |
---

## Table of Contents

- [Core Concepts](#core-concepts)
- [Project Architecture](#project-architecture)
- [Getting Started](#getting-started)
  - [Client Integration](#client-integration)
  - [Migrator Setup](#migrator-setup)
- [Configuration](#configuration)
- [Building the Project](#building-the-project)

---

## Core Concepts

- **Logical Index:** The index name your application uses (e.g., `tenant-123-logs`). This is the only index your code needs to be aware of.
- **Commune:** A physical index within OpenSearch (e.g., `commune_abc123`). It contains documents from many different logical indices.
- **OmniaTransport:** A custom wrapper for the OpenSearch transport layer. It intercepts every request, rewrites it to target the correct commune, and injects the necessary filters automatically.
- **Migrator Daemon:** A standalone background service that performs automated housekeeping. It finds communes that exceed a configured document threshold and rebalances them by migrating some of its logical indices to a new commune.

## Project Architecture

The project is composed of:

- **SDK (`sdk`):** Provides the core logic for resolving a logical index name to its corresponding commune. It uses a PostgreSQL backend to persistently store this mapping. Can be easily expanded to use any other Database, Cache etc.

- **Transport (`transport`):** The engine of transparent integration. This module contains `OmniaTransport`, which wraps the standard OpenSearch client transport. It uses the SDK to rewrite requests in-flight before they are sent to the cluster.

- **Migrator (`migrator`):** A standalone Java application that connects to both OpenSearch and PostgreSQL with daemon scripts. It prevents any single commune from growing too large.

- **Common (`common`):** A shared library for YAML configuration parsing and data structures used across the project.

- **Benchmark (`benchmark`):** A JMH-based benchmark suite to validate the performance and scaling claims of the system.

## Getting Started

### Client Integration

Integrating Omnia into your application is designed to be minimally invasive. You simply initialize your OpenSearch client with the custom `OmniaTransport`.

**Standard OpenSearch Client Setup:**
```java
RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();
OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
OpenSearchClient client = new OpenSearchClient(transport);
```

**Omnia-powered OpenSearch Client Setup:**
```java
// 1. Load your application configuration from a YAML file
AppConfig config = YamlParser.parseYaml(new FileInputStream("config.yml"));

// 2. Initialize the Omnia SDK (backed by PostgreSQL)
OmniaSDK sdk = new OmniaSDKPostgreSQL(config);

// 3. Initialize the standard REST transport
RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();
OpenSearchTransport baseTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());

// 4. Wrap the base transport with OmniaTransport
OpenSearchTransport omniaTransport = new OmniaTransport(baseTransport, sdk);

// 5. Create the client. All requests are now transparently handled.
OpenSearchClient client = new OpenSearchClient(omniaTransport);

// --> Your existing application code works without changes <--
client.search(s -> s.index("your-logical-index-name").query(...));
```

### Migrator Setup

The Migrator is a crucial backend component that runs as a long-lived process to manage communes. The easiest way to run it is with the provided Docker environment.

1.  **Configure:** Edit `docker/config.yml` with your OpenSearch and PostgreSQL connection details.
2.  **Run:** From the project root, build and run the service using Docker Compose.
    ```bash
    docker-compose up --build -d
    ```
3.  **Manage:** You can check the status or manage the daemon using the entrypoint script.
    ```bash
    # Check the migrator daemon's status
    docker-compose exec migrator /app/entrypoint.sh daemon status

    # Stop the daemon
    docker-compose exec migrator /app/entrypoint.sh daemon stop
    ```

The migrator also provides a CLI for manual operations. Run `docker-compose exec migrator /app/entrypoint.sh --help` for more information.


## Configuration

Omnia is configured via a single `config.yml` file.

```yaml
# OpenSearch cluster connection details (required)
open_search:
  host: opensearch
  port: 9200
  username: admin
  password: admin

# Database for storing index-to-commune mappings (required)
database:
  type: postgresql
  params:
    host: postgres
    port: 5432
    username: postgres
    password: postgres
    database_name: omnia

# General Omnia configuration (optional)
# Defaults will be used if this section is omitted.
config:
  # The field name used to store the logical index name within a commune.
  # This must be consistent across your application's document mappings.
  # Default: "real_index"
  feature_name: "real_index" 
  
  # The maximum number of documents a commune can hold before the
  # migrator service will trigger a split.
  # Default: 10000
  threshold: 10000 
  
  # Name of the table in the database that stores the index-to-commune mapping.
  # Default: "omnia_config"
  config_table_name: "omnia_config"
```

## Building the Project

The project is built using Gradle.

```bash
# Build all modules and run tests
./gradlew build

# Build the migrator jar without running tests (useful for Docker builds)
./gradlew :migrator:build -x test
```
