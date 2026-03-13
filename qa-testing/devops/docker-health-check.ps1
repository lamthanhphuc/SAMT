param(
  [string]$ComposeFile = "../docker-compose.yml",
  [switch]$Build
)

$ErrorActionPreference = "Stop"

Write-Host "[qa-devops] Using compose file: $ComposeFile"

if ($Build) {
  Write-Host "[qa-devops] Building images..."
  docker compose -f $ComposeFile build
}

Write-Host "[qa-devops] Starting stack..."
docker compose -f $ComposeFile up -d

Write-Host "[qa-devops] Checking container health..."
$containers = docker compose -f $ComposeFile ps -q
if (-not $containers) {
  throw "No containers found. Compose stack may have failed to start."
}

$failed = @()
foreach ($id in $containers) {
  $name = docker inspect --format "{{.Name}}" $id
  $status = docker inspect --format "{{.State.Status}}" $id
  $health = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}" $id

  Write-Host " - $name => status=$status health=$health"

  if ($status -ne "running") {
    $failed += "$name status=$status"
  }

  if ($health -ne "n/a" -and $health -ne "healthy") {
    $failed += "$name health=$health"
  }
}

if ($failed.Count -gt 0) {
  Write-Error "[qa-devops] Health check failed:`n$($failed -join "`n")"
  exit 1
}

Write-Host "[qa-devops] All containers are running and healthy."
