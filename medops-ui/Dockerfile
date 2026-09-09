# Build Vite SPA, serve with nginx (proxies /api to medops-api).
FROM node:26-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
RUN rm /docker-entrypoint.d/10-listen-on-ipv6-by-default.sh
EXPOSE 8080
