<#
.SYNOPSIS
    Excludes Kernel's build directories and the Gradle home from Microsoft Defender
    real-time scanning.

.DESCRIPTION
    A Gradle build writes and re-reads a very large number of small files: compiled
    classes, remapped Minecraft jars, and the extracted dependency cache. Defender's
    real-time protection inspects each of those operations. On a mechanical disk the
    scan competes with the build for the same disk head, so the cost is considerably
    higher than it would be on an SSD.

    This script excludes only build outputs and caches, which are regenerated from
    source and from public Maven repositories. It does not exclude the source tree,
    the user's profile, or any other location.

    Review the paths below before running. Excluding a directory from antivirus
    scanning is a real reduction in coverage: anything written there, including a
    dependency pulled from a remote repository, will no longer be inspected. Decide
    whether that tradeoff is acceptable for this machine.

.NOTES
    Requires an elevated PowerShell session. Run Show-KernelExclusions or pass
    -Remove to undo.

.EXAMPLE
    # From an elevated PowerShell prompt, in the repository root:
    powershell -ExecutionPolicy Bypass -File .\scripts\exclude-build-paths-from-defender.ps1

.EXAMPLE
    # Undo
    powershell -ExecutionPolicy Bypass -File .\scripts\exclude-build-paths-from-defender.ps1 -Remove
#>

[CmdletBinding()]
param(
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error 'This script changes Defender settings and must run from an elevated PowerShell session.'
    return
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }

# Build outputs and caches only. The source tree is deliberately left scanned.
$paths = @(
    (Join-Path $repositoryRoot 'build')
    (Join-Path $repositoryRoot '.gradle')
    (Join-Path $repositoryRoot 'mod\versions')
    (Join-Path $repositoryRoot 'knot-client\build')
    (Join-Path $repositoryRoot 'knot-client\bin')
    (Join-Path $repositoryRoot 'run')
    $gradleUserHome
) | Select-Object -Unique

# Long-lived build processes, so their file access is not re-inspected per operation.
$processes = @('java.exe', 'javaw.exe')

foreach ($path in $paths) {
    if ($Remove) {
        Remove-MpPreference -ExclusionPath $path
        Write-Host "removed exclusion  $path"
    }
    else {
        Add-MpPreference -ExclusionPath $path
        Write-Host "excluded          $path"
    }
}

foreach ($process in $processes) {
    if ($Remove) {
        Remove-MpPreference -ExclusionProcess $process
        Write-Host "removed exclusion  $process"
    }
    else {
        Add-MpPreference -ExclusionProcess $process
        Write-Host "excluded          $process"
    }
}

Write-Host ''
Write-Host 'Current Defender path exclusions:'
(Get-MpPreference).ExclusionPath | ForEach-Object { Write-Host "  $_" }
