$root = Split-Path -Parent $PSScriptRoot
$trust = "$root\certs\truststore.p12" -replace '\\', '/'

Push-Location "$root\backend"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--heapvortex.jmx.ssl.trust-store=$trust --heapvortex.jmx.remote-targets=localhost:9010"
Pop-Location