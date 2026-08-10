#!/bin/bash

set -e

echo "Starting Xvfb..."

Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp &
export DISPLAY=:99

echo "Starting x11vnc..."

x11vnc -display :99 -forever -shared &

echo "Starting noVNC..."
websockify --web /usr/share/novnc 6080 localhost:5900 &

echo "Starting Spring Boot..."

exec java -jar /TiktokSparkFlow/app.jar
