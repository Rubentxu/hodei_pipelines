# Hodei Pipelines OpenAPI Specification

This directory contains the modular OpenAPI 3.0.3 specification for the Hodei Pipelines REST API.

## Quick Start

The main entry point is `openapi.yaml` which references all other components.

### View the API Documentation

```bash
# Using Docker and Swagger UI
docker run -p 8080:8080 -e SWAGGER_JSON=/api/openapi.yaml \
  -v $(pwd):/api swaggerapi/swagger-ui

# Open http://localhost:8080
```

### Validate the Specification

```bash
# Install swagger-cli
npm install -g @apidevtools/swagger-cli

# Validate
swagger-cli validate openapi.yaml
```

## Structure

- `openapi.yaml` - Main specification file with references
- `paths/` - API endpoints organized by resource:
  - `admin.yaml` - Health checks and metrics endpoints
  - `templates.yaml` - Template management endpoints
  - `jobs.yaml` - Job lifecycle endpoints  
  - `executions.yaml` - Execution monitoring endpoints
  - `pools.yaml` - Resource pool management endpoints
  - `workers.yaml` - Worker management endpoints (pending)
  - `auth.yaml` - Authentication endpoints (pending)
- `schemas/` - Data type definitions organized by domain
- `parameters/` - Reusable parameters by resource type
- `responses/` - Common HTTP response definitions

## Development

When adding new endpoints:

1. Add path definitions to the appropriate file in `paths/`
2. Add schemas to the corresponding file in `schemas/`
3. Add parameters to `parameters/` if reusable
4. Reference from the main `openapi.yaml` file
5. Run validation to ensure correctness

## API Coverage Status

### OpenAPI Specification
- ✅ Templates API (100%) - All CRUD + validation + publishing
- ✅ Jobs API (100%) - Ad-hoc + template-based + lifecycle
- ✅ Executions API (100%) - Monitoring + logs + events + streaming
- ✅ Pools API (100%) - Management + quotas + workers + metrics
- ✅ Workers API (100%) - Full worker lifecycle + maintenance + metrics
- ✅ Admin API (100%) - Health checks + metrics + configuration
- ✅ Auth API (100%) - Authentication + authorization + session management

### Implementation Status
- ✅ Templates Controller - All basic endpoints implemented
- ✅ Jobs Controller - Core job management implemented
- ✅ Executions Controller - Basic execution monitoring implemented
- ✅ Pools Controller - Resource pool management implemented  
- ✅ Workers Controller - Worker CRUD operations implemented
- ✅ Admin Controller - Basic admin operations implemented
- ✅ Auth Controller - Basic authentication implemented

### Missing Implementations
- Template validation endpoint (`/templates/validate`)
- Template publishing endpoint (`/templates/{id}/publish`) 
- Job creation from template (`/jobs/from-template`)
- Job retry functionality (`/jobs/{id}/retry`)
- Execution streaming endpoints (logs/events)
- Pool management operations (drain, resume, maintenance)
- Pool quotas management
- Authentication endpoints in OpenAPI

## Generating Code

This specification can be used to generate:

- Server stubs (Kotlin/Ktor)
- Client SDKs (multiple languages)
- API documentation
- Postman collections

Example using OpenAPI Generator:

```bash
# Generate Kotlin server stubs
openapi-generator generate -i openapi.yaml \
  -g kotlin-server -o generated/server

# Generate TypeScript client
openapi-generator generate -i openapi.yaml \
  -g typescript-fetch -o generated/client
```