@echo off
for %%A in ("%~dp0.") do set "FolderName=%%~nA"
title %FolderName%

call gradlew clean

call gradlew build

call gradlew runclient

pause