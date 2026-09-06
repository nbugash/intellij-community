:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# Builds the thin client installers for the open remote development fork.
# Works on Linux, Windows and macOS.
# Arguments are passed as JVM options and read by org.jetbrains.intellij.build.BuildOptions

set -eu
root="$(cd "$(dirname "$0")" && pwd)"

exec "$root/build/run_build_target.sh" "$root" @community//build/thin-client:i_build_target "$@"

:CMDSCRIPT

"%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe" ^
  -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass ^
  -File "%~dp0build\run_build_target.ps1" ^
  "%~dp0" ^
  "@community//build/thin-client:i_build_target" ^
  %*
