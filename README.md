# Order Service

Microservicio encargado de la gestión del ciclo de vida de las órdenes de compra. Actúa como el orquestador principal del flujo de entrada y coordina la validación de inventario síncrona (REST) y la reconciliación asíncrona (Saga vía Kafka).

## 🚀 Tecnologías
* **Runtime:** Java 17 / Spring Boot 3.x
* **Base de Datos:** H2
* **Mensajería:** Apache Kafka
* **Resiliencia:** Resilience4j (Circuit Breaker & Fallback)
* **Observabilidad:** Micrometer Tracing + Prometheus/CloudWatch

## ⚙️ Puerto y Endpoints
* **Puerto Local:** `8080`
* **Consola H2 (Dev):** `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:order_db`)
* **Endpoints HTTP:**
  * `POST /api/v1/orders` - Creación de pedido.
  * `GET /api/v1/orders/{id}` - Consulta de estado.
  * `GET /actuator/health/readiness` - Health Check.

## 🛠️ Variables de Entorno Clave (Perfil `local`)
```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_URL=jdbc:h2:mem:order_db
SPRING_H2_CONSOLE_ENABLED=true
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
INVENTORY_SERVICE_URL=http://localhost:8081
