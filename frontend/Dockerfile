FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# TESTING ONLY: true adds the one-click page for the demo accounts (/demo-login). Leave it unset for anything but the local stack.
ARG VITE_DEMO_LOGIN=false
ENV VITE_DEMO_LOGIN=$VITE_DEMO_LOGIN
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
