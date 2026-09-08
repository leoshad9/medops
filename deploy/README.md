# MedOps - EC2 deployment guide

Public URL: **https://medops.duckdns.org**. Traffic reaches the EC2 instance directly; nginx inside the `medops-ui` container terminates TLS.

## How it works

```
push to dev --> GitHub Actions
                 |-- job test:   mvn -B verify (medops-api, Java 21)
                 `-- job deploy: aws ssm send-command
                       `-> on EC2, as ssm-user: bash deploy/ec2-deploy.sh
                             |-- git fetch + hard reset to origin/<branch>
                             |-- docker prune (containers/images/build cache, volumes KEPT)
                             `-- sudo docker compose up -d --build --remove-orphans
```

Traffic path (nothing else is exposed):

```
Internet
  |
  v
EC2 security group (ports 80 + 443)
  |
  v
nginx (medops-ui container)
  |-- :80   /.well-known/acme-challenge/ -> /opt/medops-acme  (certbot webroot)
  |-- :80   everything else              -> 301 https://medops.duckdns.org
  `-- :443  TLS (cert for medops.duckdns.org from /opt/medops-certs)
        |-- /           -> React UI (static files)
        |-- /api/       -> medops-api:8080
        `-- /actuator/  -> medops-api:8080 (health endpoints only)
```

Postgres binds to 127.0.0.1:5432 on the host only. Redis, Kafka and medops-ai are internal to the compose network.

| File | Role |
| --- | --- |
| `.github/workflows/deploy.yml` | Pipeline: test -> SSM deploy |
| `deploy/ec2-deploy.sh` | Runs on the instance: git sync -> prune -> compose up --build |
| `docker-compose.yml` | Base: redis, kafka, medops-ai |
| `docker-compose.prod.yml` | Prod overlay: postgres, medops-api, medops-ui (nginx TLS) |

## 1. GitHub Actions (access keys + SSM)

The runner uses IAM access keys to send an SSM command that runs `deploy/ec2-deploy.sh` on the instance as `ssm-user`.

**Settings -> Secrets and variables -> Actions:**

| Type | Name | Value |
| --- | --- | --- |
| Secret | `AWS_ACCESS_KEY_ID` | IAM access key ID |
| Secret | `AWS_SECRET_ACCESS_KEY` | IAM secret access key |
| Secret | `EC2_INSTANCE_ID` | `i-076902fa975b6d261` |
| Variable | `AWS_REGION` | `ap-southeast-2` |

Instance prerequisites: SSM agent running and an instance profile with `AmazonSSMManagedInstanceCore` attached.

The repo checkout lives at `/home/ssm-user/medops`. The SSM command `cd`s there, exports `DEPLOY_BRANCH=<branch>` and runs the script. `git fetch` uses the SSH deploy key if configured, with an HTTPS fallback for public repos. Shell access on the instance is via SSM Session Manager.

## 2. EC2 one-time setup

```bash
sudo su
# Docker + compose plugin (Amazon Linux 2023)
dnf install -y docker git
systemctl enable --now docker
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# ssm-user needs sudo for docker (workflow runs the script as ssm-user)
echo 'ssm-user ALL=(ALL) NOPASSWD: /usr/bin/docker' > /etc/sudoers.d/ssm-user-docker

# TLS dirs nginx mounts from docker-compose.prod.yml
mkdir -p /opt/medops-acme /opt/medops-certs
```

### Issue the certificate (webroot mode, medops.duckdns.org must point at this instance)

```bash
dnf install -y certbot
certbot certonly --webroot -w /opt/medops-acme \
  -d medops.duckdns.org -d www.medops.duckdns.org \
  --email you@example.com --agree-tos --no-eff-email
```

Certbot writes to `/etc/letsencrypt/live/medops.duckdns.org/`. Copy the files nginx expects into the mounted dir and add a renewal hook:

```bash
cp /etc/letsencrypt/live/medops.duckdns.org/fullchain.pem /opt/medops-certs/
cp /etc/letsencrypt/live/medops.duckdns.org/privkey.pem  /opt/medops-certs/
cat > /etc/letsencrypt/renewal-hooks/deploy/medops-certs.sh <<'EOF'
#!/bin/bash
cp -f /etc/letsencrypt/live/medops.duckdns.org/fullchain.pem /opt/medops-certs/
cp -f /etc/letsencrypt/live/medops.duckdns.org/privkey.pem  /opt/medops-certs/
cd /home/ssm-user/medops && sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T medops-ui nginx -s reload
EOF
chmod +x /etc/letsencrypt/renewal-hooks/deploy/medops-certs.sh
```

Renewal runs automatically via the certbot systemd timer. For local dev there is no real cert — self-signed `fullchain.pem`/`privkey.pem` stand in for `/opt/medops-certs` (see section 5).

## 3. Server config (.env)

```bash
cd /home/ssm-user/medops
nano .env   # nano: dnf install -y nano; or use vi
chmod 600 .env
```

```ini
# required
POSTGRES_PASSWORD=<strong password>
JWT_SECRET=<random 32+ chars>

# database / api
POSTGRES_USER=medops
POSTGRES_DB=medops

# medops-ai (optional - enables LLM features)
LLM_PROVIDER=generate_content
LLM_API_KEY=<key>
LLM_MODEL=<model>
```

`POSTGRES_PASSWORD` and `JWT_SECRET` are enforced: compose fails without them.

Security group: inbound 80 + 443 from 0.0.0.0/0. Nothing else (Postgres binds to 127.0.0.1 only; shell access is SSM).

## 4. Deploy and verify

```bash
# on GitHub: push to dev, or Actions -> deploy.yml -> Run workflow.
# watch: Actions tab -> deploy job

# then verify on the instance (SSM session):
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
curl -s http://localhost:8080/actuator/health        # {"status":"UP"...}
curl -sIk https://medops.duckdns.org | head -1       # HTTP/2 200
curl -sI  http://medops.duckdns.org | head -1        # 301 -> https
```

Manual redeploy without GitHub Actions:

```bash
cd /home/ssm-user/medops
DEPLOY_BRANCH=dev bash deploy/ec2-deploy.sh
```

### Rollback

```bash
cd /home/ssm-user/medops
git checkout <last-good-tag-or-sha>
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## 5. Local dev with the same prod files

Self-signed cert with CN=medops.duckdns.org, copied into the Docker Desktop VM so the unmodified prod overlay resolves `/opt/medops-certs`:

```powershell
openssl req -x509 -nodes -days 365 -newkey rsa:2048 `
  -keyout certs/privkey.pem -out certs/fullchain.pem -subj "/CN=medops.duckdns.org"
mkdir opt/medops-acme, opt/medops-certs
Copy-Item certs\*.pem opt\medops-certs\
```

Then run the helper scripts (they stop, prune, rebuild and start everything):

```powershell
.\deploy.ps1            # or: .\deploy.ps1 help
```

```bash
./deploy.sh             # or: ./deploy.sh help
```

UI: http://localhost and https://localhost (self-signed warning is expected) - AI: http://localhost:8000/health - API: http://localhost:8080/actuator/health.

## 6. Troubleshooting

| Symptom | Fix |
| --- | --- |
| deploy job fails, `Success` never reported | Instance offline/SSM agent down: check instance profile + `amazon-ssm-agent` status; `aws ssm describe-command-commands --command-id <id>` for output |
| `Set POSTGRES_PASSWORD in .env` on compose up | Create `.env` at `/home/ssm-user/medops` (section 3), then rerun |
| nginx keeps restarting, `no such file: /etc/nginx/certs/fullchain.pem` | Certs missing in `/opt/medops-certs` on the host (local dev: copy step skipped or VM reset) |
| 502 from UI | medops-api still booting or crashed: `sudo docker logs medops-medops-api-1` |
| certbot fails, connection refused | Port 80 closed in the security group, or DNS not pointing at this instance |
| Cert expiry | `certbot renew --dry-run`; confirm the renewal hook copied new files into `/opt/medops-certs` |
| Stale containers from old names | `sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --remove-orphans` |
