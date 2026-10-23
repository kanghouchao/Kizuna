FROM dhi.io/node:24-alpine3.23-dev AS deps
WORKDIR /app
COPY package.json ./
RUN --mount=type=cache,id=npm-cache,target=/root/.npm,uid=1000,gid=1000,sharing=locked \
    npm install --legacy-peer-deps --no-audit --no-fund

FROM deps AS lint
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY . .
RUN --mount=type=cache,id=npm-cache,target=/root/.npm,uid=1000,gid=1000,sharing=locked \
    npm run format:check && \
    npm run lint && \
    npm run lint:fsd && \
    npm run typecheck

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

FROM dhi.io/node:24-alpine3.23 AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

# 実行ユーザーは node（1000）。.next/cache を書けるよう所有者を合わせる
COPY --from=builder --chown=1000:1000 /app/.next/standalone ./
COPY --from=builder --chown=1000:1000 /app/.next/static ./.next/static
COPY --from=builder --chown=1000:1000 /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]
