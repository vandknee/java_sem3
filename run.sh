#!/bin/sh
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/util/*.java src/Main.java || exit 1
java -cp out Main "$@"
