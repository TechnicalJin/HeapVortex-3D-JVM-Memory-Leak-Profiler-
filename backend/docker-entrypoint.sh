#!/bin/sh
# Demo mode: start the leaking app next to the backend so the Attach API can see it.
if [ "$START_LEAKY_APP" = "true" ]; then
  java -Xmx256m -cp /leaky com.heapvortex.mock.LeakyApp &
fi
exec java -jar /app/app.jar