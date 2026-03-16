param(
  [string]$WorkspaceRoot = "e:\Bin\project"
)

$ErrorActionPreference = 'Stop'
$samtRoot = Join-Path $WorkspaceRoot 'SAMT'
if (-not (Test-Path -LiteralPath $samtRoot)) {
  throw "SAMT path not found: $samtRoot"
}

$metricsPath = Join-Path $WorkspaceRoot '.backend-cleanup-metrics.json'
$removedPath = Join-Path $WorkspaceRoot '.backend-cleanup-removed.txt'
$errorsPath = Join-Path $WorkspaceRoot '.backend-cleanup-errors.txt'

if (Test-Path -LiteralPath $removedPath) { Remove-Item -LiteralPath $removedPath -Force }
if (Test-Path -LiteralPath $errorsPath) { Remove-Item -LiteralPath $errorsPath -Force }

function Get-RepoSizeBytes([string]$path) {
  $sum = (Get-ChildItem -LiteralPath $path -Recurse -File -Force | Measure-Object -Property Length -Sum).Sum
  if ($null -eq $sum) { return 0 }
  return [int64]$sum
}

$beforeBytes = Get-RepoSizeBytes -path $samtRoot

$dirNames = @('target', 'build', 'coverage', 'dist')
$toDelete = New-Object System.Collections.Generic.List[string]

function Is-IgnoredPath([string]$fullPath) {
  return $fullPath -match '[\\/]node_modules[\\/]'
}

Get-ChildItem -LiteralPath $samtRoot -Recurse -Directory -Force |
  Where-Object { ($dirNames -contains $_.Name) -and -not (Is-IgnoredPath $_.FullName) } |
  ForEach-Object { $toDelete.Add($_.FullName) }

Get-ChildItem -LiteralPath $samtRoot -Recurse -Directory -Force |
  Where-Object { ($_.FullName -match '[\\/]target[\\/](generated-sources|generated-test-sources)$') -and -not (Is-IgnoredPath $_.FullName) } |
  ForEach-Object { $toDelete.Add($_.FullName) }

Get-ChildItem -LiteralPath $samtRoot -Recurse -File -Force |
  Where-Object {
    -not (Is-IgnoredPath $_.FullName) -and (
    $_.Name -like '*.log' -or
    $_.Name -like '*.tmp' -or
    $_.Name -like '*-report.json' -or
    $_.Name -like '*-report.md'
    )
  } |
  ForEach-Object { $toDelete.Add($_.FullName) }

$uniqueDelete = $toDelete | Sort-Object -Unique
$removedCount = 0

foreach ($path in $uniqueDelete) {
  if (-not (Test-Path -LiteralPath $path)) { continue }
  try {
    Remove-Item -LiteralPath $path -Recurse -Force
    Add-Content -LiteralPath $removedPath -Value $path
    $removedCount++
  } catch {
    Add-Content -LiteralPath $errorsPath -Value ("$path :: " + $_.Exception.Message)
  }
}

$afterBytes = Get-RepoSizeBytes -path $samtRoot

$metrics = [pscustomobject]@{
  beforeBytes = $beforeBytes
  afterBytes = $afterBytes
  removedCount = $removedCount
  removedLogPath = $removedPath
  errorLogPath = $errorsPath
  timestamp = (Get-Date).ToString('s')
}
$metrics | ConvertTo-Json | Set-Content -LiteralPath $metricsPath -Encoding UTF8

Write-Output "Cleanup complete. Removed=$removedCount Before=$beforeBytes After=$afterBytes"
