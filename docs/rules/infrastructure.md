# Infrastructure Conventions

- **Docker**: Use Docker Compose for local development and testing. Ensure that all services are defined in the `docker-compose.yml` file and that they can be started with a single command.
- **Traefik**: Use Traefik 3+ as the reverse proxy and routing solution. Configure Traefik to handle SSL termination and route traffic to the appropriate services based on subdomains or paths.
- **Database**: Use PostgreSQL 18+ for relational data storage and Redis 8+ for caching. Ensure that database migrations are handled using Liquibase and that the database schema is version-controlled.
- **Environment Variables**: Store sensitive information such as API keys, database credentials, and other secrets in environment variables. Use a `.env` file for local development and ensure that it is not committed to version control.
