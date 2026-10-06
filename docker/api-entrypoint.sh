#!/bin/sh
set -eu
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy socks_proxy SOCKS_PROXY || true
export no_proxy='*'
export NO_PROXY='*'
exec java \
  -Djava.net.useSystemProxies=false \
  -DsocksProxyHost= \
  -DsocksProxyPort=0 \
  -Dhttp.proxyHost= \
  -Dhttps.proxyHost= \
  -Dhttp.nonProxyHosts='*' \
  -jar /app/app.jar
