# syntax=docker/dockerfile:1

FROM node:22-alpine AS client-builder

WORKDIR /workspace/client/web
COPY client/web/package*.json ./
RUN npm ci
COPY client/web ./
ENV VITE_API_BASE_URL=
RUN npm run build

FROM node:22-alpine AS admin-builder

WORKDIR /workspace/admin
COPY admin/package*.json ./
RUN npm ci
COPY admin ./
ENV VITE_API_BASE_URL=
RUN npm run build -- --base=/admin/

FROM nginx:1.27-alpine

COPY infra/docker/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=client-builder /workspace/client/web/dist /usr/share/nginx/html/client
COPY --from=admin-builder /workspace/admin/dist /usr/share/nginx/html/admin

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/healthz || exit 1
