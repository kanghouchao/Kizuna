ARG NODE_IMAGE=node:24.7.0-alpine3.22
FROM ${NODE_IMAGE} AS lint
WORKDIR /e2e
COPY package.json package-lock.json ./
RUN --mount=type=cache,id=e2e-npm-cache,target=/root/.npm,sharing=locked \
    npm ci --no-audit --no-fund
COPY . .
RUN npm run typecheck
