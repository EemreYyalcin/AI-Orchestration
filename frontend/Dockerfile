# Derleme aşaması: kilit dosyasından bağımlılık kurar, TypeScript kontrolü ve Vite üretim paketi oluşturur.
FROM node:24-alpine AS build
WORKDIR /build
RUN npm install --global npm@12.2.0
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Çalışma aşaması: Node yerine yetkisiz nginx statik dosyaları ve aynı-origin proxy yollarını sunar.
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
