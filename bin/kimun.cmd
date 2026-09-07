@echo off
rem Windows launcher of a kimun release bundle: lib\bb.exe --jar lib\kimun.jar
set "DIR=%~dp0.."
"%DIR%\lib\bb.exe" --jar "%DIR%\lib\kimun.jar" -- %*
