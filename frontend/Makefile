
# フロントエンド用 Docker Makefile
# ======================

# 変数定義
DOCKER_IMAGE ?= kizuna-frontend
DOCKER_TAG ?= latest
COVERAGE_DIR ?= ../reports/
CACHE_FROM?=
CACHE_TO?=

# デフォルトターゲット
.DEFAULT_GOAL := help

help: ## ヘルプ情報を表示
	@echo "利用可能なコマンド一覧:"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## フロントエンドDockerイメージをビルド
	@echo "🏗️  フロントエンドDockerイメージをビルド中..."
	@docker buildx build -t $(DOCKER_IMAGE):$(DOCKER_TAG) .

lint: ## ESLint を実行（Docker lint ステージ経由）
	@echo "🧹 Docker の lint ステージで ESLint を実行中..."
	@docker buildx build \
		--target lint \
		--output type=cacheonly \
		.

format: ## ESLint の自動修正をローカルで実行
	@echo "✍️  ESLint の自動修正を実行中..."
	@if [ ! -d node_modules ]; then echo "📦 依存をインストール中..."; npm ci || npm install; fi
	@npm run format
	@npm run lint:fix

test: ## Docker コンテナ内でユニットテストを実行し、artifacts/ にレポートを収集
	@echo "Docker 上でテストを実行しています..."
	@docker buildx build \
		--target test \
		-t $(DOCKER_IMAGE)-test:$(DOCKER_TAG) \
		--load \
		.

test-copy-artifacts: ## テスト成果物を COVERAGE_DIR/frontend にコピー
	@rm -rf $(COVERAGE_DIR)/frontend
	@set -e; \
		cid=$(shell docker create $(DOCKER_IMAGE)-test:$(DOCKER_TAG) true) && \
		docker cp $$cid:/app/coverage $(COVERAGE_DIR)/frontend || true && \
		docker rm -f $$cid >/dev/null 2>&1 || true

clean: ## Dockerイメージを削除
	@echo "🗑️  フロントエンドDockerイメージを削除中..."
	@docker rmi $(DOCKER_IMAGE):$(DOCKER_TAG) 2>/dev/null || echo "イメージが存在しないか、既に削除されています"
	@docker rmi $(DOCKER_IMAGE)-test:$(DOCKER_TAG) 2>/dev/null || echo "テストイメージが存在しないか、既に削除されています"
	@docker rmi $(DOCKER_IMAGE)-lint:$(DOCKER_TAG) 2>/dev/null || echo "Lintイメージが存在しないか、既に削除されています"

.PHONY: help build test clean