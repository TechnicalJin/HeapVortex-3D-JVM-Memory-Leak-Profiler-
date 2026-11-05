# Creates a self-signed certificate for the monitored JVM and a trust store for HeapVortex.
$root = Split-Path -Parent $PSScriptRoot
$dir = "$root\certs"
$password = "changeit"

New-Item -ItemType Directory -Force -Path $dir | Out-Null
Remove-Item "$dir\*" -Force -ErrorAction SilentlyContinue

keytool -genkeypair -alias heapvortex -keyalg RSA -keysize 2048 -validity 365 `
  -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" `
  -storetype PKCS12 -keystore "$dir\keystore.p12" -storepass $password
keytool -exportcert -alias heapvortex -keystore "$dir\keystore.p12" -storepass $password -file "$dir\server.cer"
keytool -importcert -noprompt -alias heapvortex -file "$dir\server.cer" `
  -keystore "$dir\truststore.p12" -storetype PKCS12 -storepass $password

Write-Host "Created $dir\keystore.p12 (private key, stays on the monitored JVM) and $dir\truststore.p12 (for HeapVortex)"