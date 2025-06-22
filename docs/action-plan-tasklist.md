# 📋 Plan de Acción: Lista de Tareas Hodei Pipelines

## **Objetivo Final**
Implementar un sistema completo de CI/CD con workers efímeros tipo Jenkins, API REST y UI Kotlin Compose, basado en la documentación creada.

---

## **🔥 TAREAS CRÍTICAS (ALTA PRIORIDAD)**

### **BACKEND CORE**

#### **[T01] Implementar Real Kubernetes Integration**
- **File**: `backend/infrastructure/src/main/kotlin/.../orchestration/RealKubernetesOrchestrator.kt`
- **Dependencies**: Kubernetes Java Client 18.0.0
- **Descripción**: Reemplazar `SimpleKubernetesOrchestrator` con implementación real
- **Deliverables**:
  - Creación real de Pods en Kubernetes
  - Destrucción automática de Pods
  - Gestión de recursos y quotas
  - Error handling y logs
- **Referencia**: `ephemeral-workers-roadmap.md` - FASE 1
- **Estimación**: 5-7 días

#### **[T02] Crear Sistema de Worker Templates**
- **Files**: 
  - `core/domain/src/main/kotlin/.../orchestration/WorkerTemplate.kt`
  - `core/domain/src/main/kotlin/.../orchestration/BuiltInWorkerTemplates.kt`
  - `core/infrastructure/src/main/kotlin/.../repository/InMemoryWorkerTemplateRepository.kt`
- **Descripción**: Sistema completo de templates especializados
- **Deliverables**:
  - Templates para Java, Node, Docker, Python, Go
  - Repository para gestión de templates
  - Template selection logic
  - API DTOs para templates
- **Referencia**: `ephemeral-workers-roadmap.md` - FASE 2.2
- **Estimación**: 3-4 días

#### **[T03] Implementar API REST Base**
- **Files**:
  - `backend/rest-api/src/main/kotlin/HodeiRestServer.kt`
  - `backend/rest-api/src/main/kotlin/routes/JobRoutes.kt`
  - `backend/rest-api/src/main/kotlin/routes/WorkerRoutes.kt`
  - `backend/rest-api/src/main/kotlin/dto/ApiDtos.kt`
- **Dependencies**: Ktor Server, Kotlinx Serialization
- **Descripción**: API REST completa para jobs, workers y templates
- **Deliverables**:
  - CRUD endpoints para jobs
  - Worker management endpoints
  - Worker templates endpoints
  - API DTOs y mappers
  - Basic error handling
- **Referencia**: `api-rest-and-ui-plan.md` - Section 1.2
- **Estimación**: 4-5 días

#### **[T04] Desarrollar EphemeralWorkerManager**
- **File**: `core/application/src/main/kotlin/.../EphemeralWorkerManager.kt`
- **Descripción**: Manager completo para workers efímeros
- **Deliverables**:
  - Dynamic worker provisioning
  - Template selection logic
  - Auto-destroy scheduling
  - Integration con CreateAndExecuteJobUseCase
- **Referencia**: `ephemeral-workers-roadmap.md` - FASE 3.1
- **Estimación**: 5-6 días

#### **[T05] Setup Kotlin Compose Multiplatform**
- **Files**:
  - `hodei-ui/build.gradle.kts`
  - `hodei-ui/common/build.gradle.kts`
  - `hodei-ui/desktop/build.gradle.kts`
  - `hodei-ui/web/build.gradle.kts`
- **Dependencies**: Compose Multiplatform, Ktor Client
- **Descripción**: Estructura completa del proyecto UI
- **Deliverables**:
  - Project structure setup
  - Build configuration
  - Platform-specific modules
  - Basic navigation setup
- **Referencia**: `integrated-implementation-roadmap.md` - FASE 2
- **Estimación**: 2-3 días

#### **[T06] Implementar Design System HodeiTheme**
- **Files**:
  - `hodei-ui/common/src/commonMain/kotlin/ui/theme/HodeiTheme.kt`
  - `hodei-ui/common/src/commonMain/kotlin/ui/theme/HodeiColors.kt`
  - `hodei-ui/common/src/commonMain/kotlin/ui/theme/HodeiTypography.kt`
- **Descripción**: Design system completo en Compose
- **Deliverables**:
  - Color palette implementation
  - Typography system
  - Spacing tokens
  - Theme configuration
- **Referencia**: Design system JSON + `api-rest-and-ui-plan.md` - Section 2.3
- **Estimación**: 3-4 días

---

## **⚡ TAREAS IMPORTANTES (MEDIA PRIORIDAD)**

### **UI COMPONENTS & SCREENS**

#### **[T07] Crear Componentes UI Core**
- **Files**:
  - `hodei-ui/common/src/commonMain/kotlin/ui/components/HodeiButton.kt`
  - `hodei-ui/common/src/commonMain/kotlin/ui/components/HodeiDataTable.kt`
  - `hodei-ui/common/src/commonMain/kotlin/ui/components/StatusBadge.kt`
  - `hodei-ui/common/src/commonMain/kotlin/ui/components/WorkerTemplateCard.kt`
- **Descripción**: Componentes reutilizables según design system
- **Deliverables**:
  - HodeiButton (Primary, Secondary, Icon)
  - Data table con sorting y pagination
  - Status badges para jobs y workers
  - Worker template selection cards
- **Referencia**: `api-rest-and-ui-plan.md` - Section 2.4
- **Estimación**: 4-5 días

#### **[T08] Desarrollar Pipeline Editor**
- **File**: `hodei-ui/common/src/commonMain/kotlin/ui/screens/PipelineEditorScreen.kt`
- **Descripción**: Editor visual + script con worker template selection
- **Deliverables**:
  - Visual pipeline flow editor
  - Worker template selector
  - Agent labels input
  - Script editor with validation
  - Real-time script generation
- **Referencia**: `integrated-implementation-roadmap.md` - FASE 3.1
- **Estimación**: 6-7 días

#### **[T09] Implementar Dashboard Screen**
- **File**: `hodei-ui/common/src/commonMain/kotlin/ui/screens/DashboardScreen.kt`
- **Descripción**: Dashboard principal con favorites y recent jobs
- **Deliverables**:
  - Favorites cards section
  - Recent jobs table
  - Quick actions
  - Real-time updates
- **Referencia**: `api-rest-and-ui-plan.md` - Section 2.5
- **Estimación**: 4-5 días

#### **[T10] Crear Job Detail Screen**
- **File**: `hodei-ui/common/src/commonMain/kotlin/ui/screens/JobDetailScreen.kt`
- **Descripción**: Vista detallada con real-time monitoring
- **Deliverables**:
  - Job header con status
  - Pipeline flow visualization
  - Real-time logs viewer
  - Worker provisioning status
  - Cancel/rerun actions
- **Referencia**: `integrated-implementation-roadmap.md` - FASE 4.1
- **Estimación**: 5-6 días

### **INTEGRATION & COMMUNICATION**

#### **[T11] Implementar API Client**
- **File**: `hodei-ui/common/src/commonMain/kotlin/data/HodeiApiClient.kt`
- **Dependencies**: Ktor Client
- **Descripción**: Cliente HTTP para comunicación con backend
- **Deliverables**:
  - HTTP client setup
  - Jobs API calls
  - Workers API calls
  - Templates API calls
  - Error handling
- **Referencia**: `integrated-implementation-roadmap.md` - FASE 2.1
- **Estimación**: 3-4 días

#### **[T12] Desarrollar Real-time Job Streaming**
- **Files**:
  - `backend/rest-api/src/main/kotlin/routes/EventRoutes.kt`
  - `hodei-ui/common/src/commonMain/kotlin/data/EventStreamClient.kt`
- **Dependencies**: Ktor SSE
- **Descripción**: Streaming en tiempo real de job execution
- **Deliverables**:
  - Server-Sent Events backend
  - SSE client en UI
  - Real-time job status updates
  - Real-time logs streaming
- **Referencia**: `api-rest-and-ui-plan.md` - Section 1.4
- **Estimación**: 4-5 días

### **INFRASTRUCTURE & DEPLOYMENT**

#### **[T13] Crear Container Images Especializadas**
- **Files**:
  - `docker/base/Dockerfile`
  - `docker/java-maven/Dockerfile`
  - `docker/node-npm/Dockerfile`
  - `docker/docker-dind/Dockerfile`
  - `docker/python-pip/Dockerfile`
  - `scripts/build-images.sh`
- **Descripción**: Imágenes Docker para diferentes worker types
- **Deliverables**:
  - Base worker image
  - Java/Maven specialized image
  - Node.js/NPM specialized image
  - Docker-in-Docker image
  - Python/Pip image
  - Build scripts
- **Referencia**: `ephemeral-workers-roadmap.md` - FASE 5
- **Estimación**: 5-6 días

#### **[T14] Setup Kubernetes RBAC**
- **Files**:
  - `k8s/rbac.yaml`
  - `k8s/namespace.yaml`
  - `k8s/server-deployment.yaml`
- **Descripción**: Configuración RBAC para gestión de pods
- **Deliverables**:
  - ServiceAccount para hodei-server
  - ClusterRole para pod management
  - ClusterRoleBinding
  - Namespace configuration
- **Referencia**: `ephemeral-workers-roadmap.md` - FASE 1.3
- **Estimación**: 2-3 días

---

## **🔧 TAREAS DE MEJORA (BAJA PRIORIDAD)**

#### **[T15] Implementar Enhanced Pipeline DSL**
- **Files**:
  - `worker/domain/src/main/kotlin/.../dsl/AgentSpec.kt`
  - `worker/infrastructure/src/main/kotlin/.../dsl/LabelExpressionParser.kt`
- **Descripción**: DSL avanzado con agent selection tipo Jenkins
- **Deliverables**:
  - Agent specification DSL
  - Label expression parser
  - Template-based agent selection
  - Stage-specific agents
- **Referencia**: `ephemeral-workers-roadmap.md` - FASE 4
- **Estimación**: 4-5 días

#### **[T16] Crear Tests de Integración End-to-End**
- **Files**:
  - `integration-tests/src/test/kotlin/EndToEndTests.kt`
  - `integration-tests/src/test/kotlin/EphemeralWorkerTests.kt`
- **Descripción**: Tests completos del flujo end-to-end
- **Deliverables**:
  - Test job creation → worker provisioning → execution
  - Test UI → API → Backend integration
  - Test ephemeral worker lifecycle
  - Performance tests
- **Estimación**: 5-6 días

#### **[T17] Setup Deployment Completo**
- **Files**:
  - `docker-compose.yml`
  - `k8s/complete-deployment/`
  - `helm/hodei-pipelines/`
- **Descripción**: Deployment production-ready
- **Deliverables**:
  - Docker Compose para desarrollo
  - Kubernetes manifests completos
  - Helm chart
  - Environment configuration
- **Estimación**: 3-4 días

---

## **📅 CRONOGRAMA SUGERIDO**

### **Sprint 1 (Semana 1-2)**: Foundation Backend
- **T01**: Real Kubernetes Integration
- **T02**: Worker Templates System  
- **T03**: API REST Base

### **Sprint 2 (Semana 3-4)**: Core Logic + UI Setup
- **T04**: EphemeralWorkerManager
- **T05**: Compose Multiplatform Setup
- **T06**: Design System Implementation

### **Sprint 3 (Semana 5-6)**: UI Components
- **T07**: Core UI Components
- **T11**: API Client
- **T14**: Kubernetes RBAC

### **Sprint 4 (Semana 7-8)**: Screens & Integration
- **T08**: Pipeline Editor
- **T09**: Dashboard Screen
- **T12**: Real-time Streaming

### **Sprint 5 (Semana 9-10)**: Polish & Deploy
- **T10**: Job Detail Screen
- **T13**: Container Images
- **T16**: Integration Tests

---

## **🎯 CRITERIOS DE ÉXITO**

### **MVP Completo**:
- [ ] Jobs se crean desde UI y ejecutan en workers efímeros
- [ ] Workers se provisionan automáticamente en Kubernetes
- [ ] UI muestra real-time job execution progress
- [ ] Workers se destruyen automáticamente post-ejecución
- [ ] Pipeline editor permite selección de worker templates

### **Production Ready**:
- [ ] Sistema soporta múltiples jobs concurrentes
- [ ] Workers especializados por tecnología (Java, Node, Docker)
- [ ] Monitoring y logging completo
- [ ] Tests de integración pasando
- [ ] Deployment automatizado

**Tiempo Total Estimado**: **8-10 semanas** para sistema completo production-ready

**Próximo Paso Inmediato**: Comenzar con **T01 - Real Kubernetes Integration** como base para todo el sistema.