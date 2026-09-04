# MedOps

A unified hospital operations platform that streamlines patient management, appointments, clinical workflows, and day-to-day hospital operations.

Live at: **https://medops.duckdns.org**

## Services

| Service | Stack | Description |
|---|---|---|
| `medops-api` | Spring Boot 3, Java 21 | REST API — auth, patients, doctors, appointments, prescriptions, reports |
| `medops-ui` | React, TypeScript, Vite | Frontend served via Nginx |
| `medops-ai` | Python, FastAPI | LLM-powered clinical report summarisation |
| `postgres` | PostgreSQL 16 | Primary database |
| `redis` | Redis 7 | Idempotency keys, caching |
| `kafka` | Apache Kafka 3.8 | Async messaging (appointments, reports) |

## Local Development

**Prerequisites:** Docker, Docker Compose

```bash
git clone https://github.com/leoshad9/medops
cd medops
cp .env.example .env        # fill in secrets
docker compose up -d        # starts all services
```

API: http://localhost:8080  
UI: http://localhost:5173  
Swagger: http://localhost:8080/swagger-ui.html

## Environment Variables

Copy `.env.example` to `.env` and set:

| Variable | Description |
|---|---|
| `POSTGRES_PASSWORD` | Database password |
| `JWT_SECRET` | JWT signing secret — generate with `openssl rand -hex 32` |
| `LLM_PROVIDER` | LLM API dialect (`generate_content` or `chat_completions`) |
| `LLM_API_KEY` | API key for the LLM provider |
| `LLM_MODEL` | Model name |
| `MEDOPS_AI_ENABLED` | Enable AI report summarisation (`true`/`false`) |
| `MEDOPS_SECURITY_OPEN_REGISTRATION` | Allow public patient/doctor self-registration (`true`/`false`) |

## Production Deploy

Deploys automatically to AWS EC2 via GitHub Actions on push to `dev`.

Pipeline: **test** (mvn verify) → **deploy** (SSM send-command → ec2-deploy.sh)

Manual deploy:
```bash
cd /home/ssm-user/medops
bash deploy/ec2-deploy.sh
```

### GitHub Actions Secrets & Variables

| Name | Type | Description |
|---|---|---|
| `AWS_ACCESS_KEY_ID` | Secret | AWS credentials |
| `AWS_SECRET_ACCESS_KEY` | Secret | AWS credentials |
| `EC2_INSTANCE_ID` | Secret | Target EC2 instance |
| `AWS_REGION` | Variable | e.g. `ap-south-1` |

## Project Structure

```
medops/
├── medops-api/       # Spring Boot API
├── medops-ui/        # React frontend
├── medops-ai/        # FastAPI AI service
├── deploy/           # EC2 deploy script
├── docker-compose.yml
├── docker-compose.prod.yml
└── .env.example
```

## License

[MIT](LICENSE)
