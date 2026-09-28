FROM node:24-alpine AS build
WORKDIR /workspace
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_DEMO_MODE=false
ENV VITE_DEMO_MODE=$VITE_DEMO_MODE
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
