@echo off
REM     Arbor: Procedural Voxel Tree Generation.
REM     Copyright (C) 2026  Jeff Chen
REM
REM     This program is free software: you can redistribute it and/or modify
REM     it under the terms of the GNU General Public License as published by
REM     the Free Software Foundation, either version 3 of the License, or
REM     (at your option) any later version.
REM
REM     This program is distributed in the hope that it will be useful,
REM     but WITHOUT ANY WARRANTY; without even the implied warranty of
REM     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM     GNU General Public License for more details.
REM
REM     You should have received a copy of the GNU General Public License
REM     along with this program.  If not, see <http://www.gnu.org/licenses/>.
setlocal
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
set "UNSAFE_FLAG="
"%JAVA_CMD%" --sun-misc-unsafe-memory-access=allow -version >nul 2>&1 && set "UNSAFE_FLAG=--sun-misc-unsafe-memory-access=allow"
REM The jar is named after the version. A release keeps it beside this file; in the project, the newest one built is used.
set "JAR="
for /f "delims=" %%F in ('dir /b /o:d "%~dp0Arbor-*.jar" 2^>nul') do set "JAR=%~dp0%%F"
if not defined JAR for /f "delims=" %%F in ('dir /b /o:d "%~dp0ArborGUI\target\Arbor-*.jar" 2^>nul') do set "JAR=%~dp0ArborGUI\target\%%F"
if not defined JAR (
  echo Arbor is not built yet. Build it first with: .\mvnw.cmd package 1>&2
  exit /b 1
)
"%JAVA_CMD%" %UNSAFE_FLAG% --enable-native-access=ALL-UNNAMED -jar "%JAR%" %*
exit /b %ERRORLEVEL%
