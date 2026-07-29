# ==============================================================================
# Etapa 1: Build / Compilación
# ==============================================================================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# 1. Copiar la configuración del build primero para aprovechar el caché de Docker
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# 2. Descargar dependencias offline (si pom.xml no cambia, esta capa se reutiliza)
RUN ./mvnw dependency:go-offline

# 3. Copiar código fuente y empaquetar la aplicación omitiendo los tests (los ejecutamos en el CI)
COPY src ./src
RUN ./mvnw clean package -DskipTests

# ==============================================================================
# Etapa 2: Runtime seguro y ligero para Producción
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# 4. Seguridad (DevSecOps): Crear un usuario sin privilegios para no correr como root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 5. Copiar únicamente el JAR generado desde la etapa de compilación
COPY --from=builder /app/target/orderinvent-order-service-1.0.0.jar app.jar

# 6. Permisos sobre el directorio de trabajo
RUN chown -R appuser:appgroup /app

# 7. Cambiar al usuario no administrativo
USER appuser

EXPOSE 8080

# 8. Parámetros de la JVM optimizados para contenedores (cgroups v2 en AWS/K8s)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]