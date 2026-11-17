# Runs the backend and frontend tests and writes docs\evidence-summary.txt
$root = Split-Path -Parent $PSScriptRoot
$results = "$root\docs\test-results"
New-Item -ItemType Directory -Force -Path $results | Out-Null

Push-Location "$root\backend"
.\mvnw.cmd clean test 2>&1 | Out-File -FilePath "$results\backend-test-output.txt" -Encoding utf8
$backendExit = $LASTEXITCODE
Pop-Location

Push-Location "$root\frontend"
npm test 2>&1 | Out-File -FilePath "$results\frontend-test-output.txt" -Encoding utf8
$frontendExit = $LASTEXITCODE
Pop-Location

$lines = @()
$lines += "HeapVortex evidence summary - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += ""
$lines += "BACKEND (exit code $backendExit, full log: docs/test-results/backend-test-output.txt)"
$lines += Select-String -Path "$results\backend-test-output.txt" `
    -Pattern '(AUDIT |WEEK3 |WEEK4 )|Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+\s*$|BUILD (SUCCESS|FAILURE)' |
    ForEach-Object { $_.Line.Trim() }
$lines += ""
$lines += "FRONTEND (exit code $frontendExit, full log: docs/test-results/frontend-test-output.txt)"
$lines += Select-String -Path "$results\frontend-test-output.txt" -Pattern '^\s*(Test Files|Tests)\s' |
    ForEach-Object { $_.Line.Trim() }

$lines | Set-Content -Path "$root\docs\evidence-summary.txt" -Encoding utf8
Get-Content "$root\docs\evidence-summary.txt"

if ($backendExit -ne 0 -or $frontendExit -ne 0) {
    Write-Host "A test run failed. Do not use this summary as evidence until it is fixed." -ForegroundColor Red
}