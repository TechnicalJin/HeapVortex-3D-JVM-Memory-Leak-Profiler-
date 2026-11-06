$root = Split-Path -Parent $PSScriptRoot
$certs = "$root\certs"
$classes = "$root\backend\target\test-classes"

java -Xmx256m -Xmn16m `
  "-Dcom.sun.management.jmxremote.port=9010" `
  "-Dcom.sun.management.jmxremote.rmi.port=9010" `
  "-Dcom.sun.management.jmxremote.host=127.0.0.1" `
  "-Dcom.sun.management.jmxremote.authenticate=false" `
  "-Dcom.sun.management.jmxremote.ssl=true" `
  "-Dcom.sun.management.jmxremote.registry.ssl=true" `
  "-Djava.rmi.server.hostname=localhost" `
  "-Djavax.net.ssl.keyStore=$certs\keystore.p12" `
  "-Djavax.net.ssl.keyStorePassword=changeit" `
  -cp $classes com.heapvortex.mock.LeakyApp