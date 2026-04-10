# ============================================================
# Stage 1 – Build
# Uses full JDK to compile and package the application
# ============================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# Copy dependency descriptors first (layer cache optimization)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

RUN chmod +x mvnw

# Download dependencies (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -B && \
    java -Djarmode=layertools -jar target/app.jar extract --destination target/extracted

# ============================================================
# Stage 2 – Runtime
# Minimal JRE image — no build tools, smaller attack surface
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy layered JAR content (Spring Boot layertools for optimal caching)
COPY --from=builder /workspace/target/extracted/dependencies/ ./
COPY --from=builder /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/extracted/application/ ./

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
