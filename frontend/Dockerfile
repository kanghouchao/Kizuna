## frontend/Dockerfile

FROM node:24.7.0-alpine3.22 AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,id=npm-cache,target=/root/.npm,uid=1000,gid=1000,sharing=locked \
    npm ci --no-audit --no-fund

FROM deps AS lint
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY . .
RUN --mount=type=cache,id=npm-cache,target=/root/.npm,uid=1000,gid=1000,sharing=locked \
    npm run format:check && \
    npm run lint && \
    npm run lint:fsd

FROM deps AS test
ENV NEXT_TELEMETRY_DISABLED=1
ENV JEST_JUNIT_OUTPUT=/app/junit.xml
WORKDIR /app
COPY . .
RUN --mount=type=cache,id=npm-cache,target=/root/.npm,uid=1000,gid=1000,sharing=locked \
    npm run test -- --ci --reporters=default --reporters=jest-junit

FROM deps AS builder
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY . .
RUN --mount=type=cache,id=npm-cache,target=/root/.npm,uid=1000,gid=1000,sharing=locked \
    npm run build

FROM node:24.7.0-alpine3.22 AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
# Ensure Next server binds correctly in containers
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

# Alpine: add glibc compatibility for Next/SWC native binaries and tools for healthcheck
RUN apk add --no-cache libc6-compat wget curl

# Copy standalone server output to root (as Next docs recommend)
COPY --from=builder /app/.next/standalone ./
# Static assets
COPY --from=builder /app/.next/static ./.next/static
# Public assets (e.g., /health)
COPY --from=builder /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]
