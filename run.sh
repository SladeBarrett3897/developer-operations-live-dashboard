#!/bin/sh
set -eu
rm -rf target/classes
mkdir -p target/classes
javac -d target/classes $(find src/main/java -name '*.java' -print)
exec java -cp target/classes devdashboard.DeveloperOperationsServer "$@"
