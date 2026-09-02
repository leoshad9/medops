# MedOps deploy to EC2 (behind ALB)

ALB: `http://medops-alb-1371426590.ap-southeast-2.elb.amazonaws.com`

## 1. GitHub Actions secrets

Repo → **Settings** → **Secrets and variables** → **Actions**. Do **not** commit `medops.pem`.

| Secret | Value |
| --- | --- |
| `EC2_HOST` | `medops.duckdns.org` (or the instance public IP) |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_PRIVATE_KEY` | Full `medops.pem` (`BEGIN`/`END` lines included) |
| `EC2_SSH_KEY` | Same as above (legacy name; either secret works) |
| `EC2_PORT` | `22` (optional) |

The workflow SSHs as `ec2-user` into **`/home/ec2-user/medops`**, then `git fetch`/`reset` (EC2 GitHub deploy key) and `sudo docker compose … up -d --build`.

GitHub-hosted runners are **not** your laptop IP. Keep the security group as-is until secrets are set; then allow port 22 from Actions (or a bastion), not permanently `0.0.0.0/0` if you can avoid it.

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
# Compose v5+ needs Buildx >= 0.17 (AL2023 ships 0.12.x).
BUILDX_VER="$(curl -fsSL https://api.github.com/repos/docker/buildx/releases/latest | grep -oP '"tag_name":\s*"\K[^"]+')"
sudo curl -SL "https://github.com/docker/buildx/releases/download/${BUILDX_VER}/buildx-${BUILDX_VER}.linux-$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')" \
  -o /usr/libexec/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/libexec/docker/cli-plugins/docker-buildx

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

Optional first manual start (as **ec2-user**, never `sudo su`):

```bash
bash deploy/ec2-deploy.sh
```

If `git fetch` fails (no GitHub deploy key), deploy the tree already on disk:

```bash
SKIP_GIT_SYNC=true bash deploy/ec2-deploy.sh
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

## 6. pgAdmin (SSH tunnel)

Postgres is bound to **127.0.0.1:5432 on the instance only**. Do not open 5432 in the security group.

On your Windows machine (leave this window open):

```powershell
ssh -i medops.pem -N -L 5433:127.0.0.1:5432 ec2-user@3.26.240.12
```

In pgAdmin → Register → Server:

| Field | Value |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `5433` |
| Database | `POSTGRES_DB` from `/opt/medops/.env` |
| Username | `POSTGRES_USER` from `.env` |
| Password | `POSTGRES_PASSWORD` from `.env` |
| SSL | Disable (the SSH tunnel is already encrypted) |

