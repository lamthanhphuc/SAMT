#!/bin/sh
set -e

AI_BASE_URL="${AI_BASE_URL:-http://ollama:11434}"
AI_MODEL="${AI_MODEL:-phi3}"
OLLAMA_TAGS_URL="${AI_BASE_URL%/}/api/tags"
OLLAMA_GENERATE_URL="${AI_BASE_URL%/}/api/generate"

until curl -sS --fail --connect-timeout 3 --max-time 5 "$OLLAMA_TAGS_URL" > /dev/null; do
  echo "Waiting for Ollama..."
  sleep 2
done

# Best-effort warmup (never block service startup)
curl -sS --fail --connect-timeout 3 --max-time 15 "$OLLAMA_GENERATE_URL" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"$AI_MODEL\",\"prompt\":\"warmup\",\"stream\":false}" > /dev/null || true

exec java -jar /app/app.jar
