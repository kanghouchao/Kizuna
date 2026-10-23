ARG NODE_IMAGE=dhi.io/node:24-alpine3.23-dev
FROM ${NODE_IMAGE} AS lint
WORKDIR /e2e
COPY package.json ./
RUN --mount=type=cache,id=e2e-npm-cache,target=/root/.npm,sharing=locked \
    npm install --legacy-peer-deps --no-audit --no-fund
COPY . .
RUN npm run typecheck