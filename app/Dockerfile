# ============================================================
#  Laulain Luxe Rentals — Dockerfile
#  Multi-stage build: Build → Runtime
#
#  Stage 1: Uses Maven + Corretto 17 to compile and package the JAR
#  Stage 2: Lean Corretto 17 runtime image — no build tools
# ============================================================

# ---- Stage 1: Build ----
# Use the official Maven image with Corretto 17 — no mvnw needed
FROM maven:3.9.6-amazoncorretto-17 AS builder

WORKDIR /build

# Copy pom.xml first — Docker caches this layer separately.
# Dependencies are only re-downloaded when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source code and build the JAR
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# Extract Spring Boot layers for optimised runtime image layering
RUN java -Djarmode=layertools \
    -jar target/*.jar extract

# ---- Stage 2: Runtime ----
FROM amazoncorretto:17-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S laulain && adduser -S laulain -G laulain

WORKDIR /app

# Copy Spring Boot layers in order of change frequency
# (least-changing first = better Docker layer cache hits)
COPY --from=builder /build/dependencies/          ./
COPY --from=builder /build/spring-boot-loader/    ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/           ./

# Mount point for DocuSign private key (provided via K8s Secret or -v flag)
RUN mkdir -p /app/secrets && chown laulain:laulain /app/secrets

USER laulain

# Health check — used by K8s liveness/readiness probes and Docker healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

EXPOSE 8080

# JVM flags tuned for containers
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
