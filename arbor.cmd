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
"%JAVA_CMD%" %UNSAFE_FLAG% --enable-native-access=ALL-UNNAMED -jar "%~dp0ArborGUI\target\arbor.jar" %*
exit /b %ERRORLEVEL%
