# MedOps deploy to EC2 (behind ALB)

ALB: `http://medops-alb-1371426590.ap-southeast-2.elb.amazonaws.com`

## 1. GitHub Actions secrets

Repo → **Settings** → **Secrets and variables** → **Actions**:

| Secret | Value |
| --- | --- |
| `EC2_HOST` | EC2 public IP (e.g. `3.26.240.12`) — **not** the ALB DNS |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | Full `medops.pem` contents (`BEGIN`/`END` lines included) |
| `EC2_PORT` | `22` (optional) |

## 2. One-time EC2 prep

SSH in, then:

```bash
sudo dnf update -y
sudo dnf install -y docker git curl
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

sudo mkdir -p /usr/libexec/docker/cli-plugins
sudo curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/libexec/docker/cli-plugins/docker-compose
sudo chmod +x /usr/libexec/docker/cli-plugins/docker-compose

sudo mkdir -p /opt/medops
sudo chown -R ec2-user:ec2-user /opt/medops
```

Log out and back in (docker group).

**Clone first**, then create `.env` (do not create `.env` before clone — non-empty `/opt/medops` breaks `git clone`):

```bash
cd /opt/medops
git clone https://github.com/leoshad9/medops.git .
git checkout dev

JWT_SECRET_VALUE="$(openssl rand -hex 32)"
cat > .env <<EOF
HOST_HTTP_PORT=80
POSTGRES_DB=medops
POSTGRES_USER=medops
POSTGRES_PASSWORD=change-me-strong-password
JWT_SECRET=${JWT_SECRET_VALUE}
LLM_PROVIDER=generate_content
LLM_API_KEY=your_llm_api_key
LLM_MODEL=your-model-id
LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta
MEDOPS_AI_ENABLED=true
EOF
chmod 600 .env
nano .env   # set real passwords / LLM values
```

Optional first manual start:

```bash
chmod +x deploy/ec2-deploy.sh
./deploy/ec2-deploy.sh
```

## 3. AWS / ALB

| Item | Setting |
| --- | --- |
| Target group port | **80** |
| Health check | `/actuator/health/liveness` (or `/`) |
| EC2 SG :80 | only from **`medops-alb-sg`** |
| EC2 SG :22 | your IP |

## 4. Trigger deploy

Push to `dev` / `main` / `master`, or **Actions** → **Deploy MedOps to AWS EC2** → **Run workflow**.

Workflow file: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml).

Uses:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env up -d --build
```

(not plain `docker compose up` — that would load the local override and skip Postgres/API/UI).

## 5. Verify

```bash
curl -sS http://127.0.0.1/actuator/health/liveness
```

Then open the ALB URL; target group should become **Healthy**.
