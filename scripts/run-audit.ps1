# Runs the extraction audit and saves the evidence lines to docs\audit-evidence.txt
$root = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force -Path "$root\docs" | Out-Null
$out = "$root\docs\audit-evidence.txt"

Push-Location "$root\backend"
$log = .\mvnw test "-Dtest=ExtractionAuditTest" 2>&1 | Tee-Object -Variable full
Pop-Location

$lines = $full | Select-String -Pattern "AUDIT|Tests run:|BUILD"
"HeapVortex extraction audit - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Out-File $out
$lines | ForEach-Object { $_.Line } | Out-File $out -Append
Write-Host "Saved to $out"