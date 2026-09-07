@echo off
rem Windows launcher of a knowledge release bundle: lib\bb.exe --jar lib\knowledge.jar
set "DIR=%~dp0.."
"%DIR%\lib\bb.exe" --jar "%DIR%\lib\knowledge.jar" -- %*
