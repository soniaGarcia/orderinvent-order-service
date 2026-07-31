# Order Service

Microservicio encargado de la gestión del ciclo de vida de las órdenes de compra. Actúa como el orquestador principal del flujo de entrada y coordina la validación de inventario síncrona (REST) y la reconciliación asíncrona (Saga vía Kafka).

## 🚀 Tecnologías
* **Runtime:** Java 21 / Spring Boot 3.x
* **Base de Datos:** PostgreSQL (Aurora Serverless v2)
* **Mensajería:** Apache Kafka
* **Resiliencia:** Resilience4j (Circuit Breaker & Fallback)
* **Observabilidad:** Micrometer Tracing + Prometheus/CloudWatch

## ⚙️ Puerto y Endpoints
* **Puerto Local:** `8080`
* **Endpoints HTTP:**
  * `POST /api/v1/orders` - Creación de pedido (Atómico).
  * `GET /api/v1/orders/{id}` - Consulta de estado del pedido.
  * `GET /actuator/health/readiness` - Health Check ALB/K8s.

## 🔄 Integración de Eventos (Kafka)
* **Productor:** `order-events` (Publica estados: `PENDING`, `CONFIRMED`, `REJECTED`).
* **Consumidor:** `inventory-events` (Recibe confirmaciones o fallos de stock diferidos).

## 🛠️ Variables de Entorno Clave
```env
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/order_db
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
INVENTORY_SERVICE_URL=http://localhost:8081