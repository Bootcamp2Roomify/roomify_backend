#!/bin/sh
# Starts the private CV service, waits for it, then runs the API in the foreground on $PORT.
cd /app/cv
uvicorn app.main:app --host 127.0.0.1 --port 8000 &
for i in $(seq 1 60); do
  curl -sf http://127.0.0.1:8000/health >/dev/null && break
  sleep 1
done
exec java -jar /app/app.jar
