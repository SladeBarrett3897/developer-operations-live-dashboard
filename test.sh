#!/bin/sh
set -eu
rm -rf target/test-classes
mkdir -p target/test-classes
javac -d target/test-classes $(find src/main/java src/test/java -name '*.java' -print)
java -cp target/test-classes devdashboard.DeveloperSignalServiceTest
