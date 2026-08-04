# Windows alternative to release.sh — bumps the version, tags, and pushes so
# GitHub Actions builds and publishes the release.
#
# Usage (from the project root):
#   .\release.ps1 2.0.2

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$VersionName
)

$ErrorActionPreference = 'Stop'

$Tag = "v$VersionName"
$GradleFile = "app\build.gradle.kts"

if (-not (Test-Path $GradleFile)) {
    Write-Error "ERROR: $GradleFile not found. Run this from the project root."
    exit 1
}

$worktreeStatus = git status --porcelain
if ($LASTEXITCODE -ne 0) { exit 1 }
if ($worktreeStatus) {
    Write-Error "ERROR: commit or stash all changes before creating a release."
    exit 1
}

$existingTag = git tag --list $Tag
if ($existingTag) {
    Write-Error "ERROR: tag $Tag already exists."
    exit 1
}

$content = [System.IO.File]::ReadAllText((Resolve-Path $GradleFile))
$originalContent = $content

$codeMatch = [regex]::Match($content, 'versionCode = (\d+)')
if (-not $codeMatch.Success) {
    Write-Error "ERROR: versionCode not found in $GradleFile."
    exit 1
}
$currentVersionCode = [int]$codeMatch.Groups[1].Value
$newVersionCode = $currentVersionCode + 1

$nameMatch = [regex]::Match($content, 'versionName = "([^"]*)"')
if (-not $nameMatch.Success) {
    Write-Error "ERROR: versionName not found in $GradleFile."
    exit 1
}
if ($nameMatch.Groups[1].Value -eq $VersionName) {
    Write-Error "ERROR: versionName is already $VersionName."
    exit 1
}

Write-Host "versionCode: $currentVersionCode -> $newVersionCode"
Write-Host "versionName: -> $VersionName"

$content = $content -replace 'versionCode = \d+', "versionCode = $newVersionCode"
$content = $content -replace 'versionName = "[^"]*"', "versionName = `"$VersionName`""

$committed = $false
try {
    # WriteAllText keeps UTF-8 without BOM, matching what Gradle expects.
    [System.IO.File]::WriteAllText((Resolve-Path $GradleFile), $content)

    & .\gradlew.bat test lint assembleRelease --no-parallel
    if ($LASTEXITCODE -ne 0) { throw "Release quality gate failed." }

    git add $GradleFile
    if ($LASTEXITCODE -ne 0) { throw "Could not stage the version change." }
    git commit -m "chore: release $Tag"
    if ($LASTEXITCODE -ne 0) { throw "Could not commit the release version." }
    $committed = $true

    git tag -a $Tag -m "Release $Tag"
    if ($LASTEXITCODE -ne 0) { throw "Could not create $Tag." }
    git push --atomic origin HEAD $Tag
    if ($LASTEXITCODE -ne 0) {
        throw "Atomic push failed. The release commit and tag remain local."
    }
} catch {
    if (-not $committed) {
        [System.IO.File]::WriteAllText((Resolve-Path $GradleFile), $originalContent)
        git restore --staged -- $GradleFile 2>$null
    }
    throw
}

Write-Host ""
Write-Host "Pushed $Tag. GitHub Actions will now build, sign, and publish the release."
