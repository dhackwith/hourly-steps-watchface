# Checks the face with Google's tools (clone and build them first, see README):
# the XML against the WFF v4 schema, then the built APK against the memory limits.
#
# Usage: tools\validate.ps1 [path\to\google-watchface-clone]
param([string]$Tools = "$PSScriptRoot\..\..\watchface-tools\watchface")

$ErrorActionPreference = "Stop"
$repo = Resolve-Path "$PSScriptRoot\.."
$validator = "$Tools\third_party\wff\specification\validator\build\libs\wff-validator.jar"
$footprint = "$Tools\play-validations\memory-footprint\build\libs\memory-footprint.jar"
$face = "$repo\watchface\src\main\res\raw\watchface.xml"
$apk = "$repo\watchface\build\outputs\apk\debug\watchface-debug.apk"

foreach ($file in @($validator, $footprint, $apk)) {
    if (-not (Test-Path $file)) {
        Write-Error "Missing $file. See the README for how to build it."
    }
}

java -jar $validator 4 $face
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

java -jar $footprint --watch-face $apk --schema-version 4 `
    --ambient-limit-mb 10 --active-limit-mb 100 `
    --apply-v1-offload-limitations --estimate-optimization
exit $LASTEXITCODE
