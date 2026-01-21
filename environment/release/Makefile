PROJECT_NAME := kizuna
service ?=

up:
	@docker compose -p $(PROJECT_NAME) up -d --timestamps --wait

down:
	@docker compose -p $(PROJECT_NAME) down

ps:
	@docker compose -p $(PROJECT_NAME) ps

logs:
ifndef service
	@docker compose -p $(PROJECT_NAME) logs -f --tail=100
else
	echo "📜 $(service)サービスのログを表示中...";
	@docker compose -p $(PROJECT_NAME) logs -f --tail=100 $(service)
endif

exec:
ifndef service
	@echo "❗ serviceを指定してください。例: make exec service=backend"
else
	@docker compose -p $(PROJECT_NAME) exec $(service) sh
endif

restart:
ifndef service
	@echo "🔄 すべてのサービスを再起動中..."
	@docker compose -p $(PROJECT_NAME) restart
else
	@echo "🔄 $(service)サービスを再起動中..."
	@docker compose -p $(PROJECT_NAME) restart $(service)
endif

.PHONY: up down ps logs exec restart