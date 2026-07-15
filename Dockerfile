# ── Stage 1: Build React web admin ───────────────────────────────────────────
FROM node:20-alpine AS web-builder
WORKDIR /web
COPY web/admin/package*.json ./
RUN npm ci --silent
COPY web/admin/ .
# Output goes directly to server/src/main/resources/static via vite config,
# but in Docker we build to a local dist/ and copy in the next stage.
RUN sed -i "s|outDir: '../../server/src/main/resources/static'|outDir: 'dist'|" vite.config.ts && \
    npm run build

# ── Stage 2: Build Ktor server ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS server-builder
WORKDIR /build

# Copy Gradle wrapper & version catalog first for layer caching
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# Copy only the server module (pure JVM, no Android dependencies)
COPY server/ server/

# Copy web build output into server resources so Ktor can serve the SPA
COPY --from=web-builder /web/dist/ server/src/main/resources/static/

RUN chmod +x gradlew && \
    ./gradlew :server:installDist --no-daemon -q

# ── Stage 3: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S pos && adduser -S pos -G pos
USER pos

COPY --from=server-builder --chown=pos:pos /build/server/build/install/server/ .

EXPOSE 8080

ENTRYPOINT ["bin/server"]
