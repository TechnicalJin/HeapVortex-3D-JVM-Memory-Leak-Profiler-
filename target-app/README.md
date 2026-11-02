# LeakyApp

A tiny Java program with a deliberate memory leak. HeapVortex attaches to it for testing.

## Run

    ./run.sh            (Linux, macOS)
    run.bat             (Windows)

Optional arguments: size of each leaked entry in KB, delay between entries in ms.

    ./run.sh 100 20     (leaks faster)

It prints its PID on start. It stops with an OutOfMemoryError after a few minutes. That is expected.

## Check that it leaks

    jps -l                      (LeakyApp must be listed)
    jstat -gcutil <PID> 2000    (the O column, old generation, keeps growing)