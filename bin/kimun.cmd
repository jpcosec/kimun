@echo off
rem Development launcher (Windows), the counterpart of bin\kimun: runs the kimun CLI from this
rem checkout with Babashka, in the caller's working directory. NOT TESTED: no Windows bundle is
rem published and CI does not run on Windows (README, Install). The release bundle has its own
rem bin\kimun.cmd, written by scripts/release.clj.
set "REPO=%~dp0.."
bb --config "%REPO%\bb.edn" -m kimun.cli.main -- %*
