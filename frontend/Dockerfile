FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_OIDC_AUTHORITY
ARG VITE_OIDC_CLIENT_ID=lis-web
ARG VITE_DEV_PERSONAS=false
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID VITE_DEV_PERSONAS=$VITE_DEV_PERSONAS
RUN npm run build

FROM nginx:1.27-alpine
# the official image renders templates with the environment at start; IDP_ORIGIN is the identity provider origin
ENV IDP_ORIGIN=
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
