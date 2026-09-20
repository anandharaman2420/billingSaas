# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# --- Runtime stage ---
FROM nginx:1.27-alpine
COPY --from=build /build/dist/billing-saas-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
