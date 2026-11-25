#!/bin/sh
#     Arbor: Procedural Voxel Tree Generation.
#     Copyright (C) 2026  Jeff Chen
#
#     This program is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This program is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.

# Runs Arbor on Linux and macOS, like arbor.cmd on Windows. Build it first with: sh mvnw package
JAVA_CMD=java
if [ -n "$JAVA_HOME" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
fi
# Newer Java versions warn about the graphics library unless these are allowed; older ones lack the first flag.
UNSAFE_FLAG=
if "$JAVA_CMD" --sun-misc-unsafe-memory-access=allow -version >/dev/null 2>&1; then
  UNSAFE_FLAG=--sun-misc-unsafe-memory-access=allow
fi
DIR=$(cd "$(dirname "$0")" && pwd)
# The jar is named after the version. A release keeps it beside this file; in the project, the newest one built is used.
JAR=$(ls -t "$DIR"/Arbor-*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR" ]; then
  JAR=$(ls -t "$DIR"/ArborGUI/target/Arbor-*.jar 2>/dev/null | head -n 1)
fi
if [ -z "$JAR" ]; then
  echo "Arbor is not built yet. Build it first with: sh mvnw package" >&2
  exit 1
fi
exec "$JAVA_CMD" $UNSAFE_FLAG --enable-native-access=ALL-UNNAMED -jar "$JAR" "$@"
