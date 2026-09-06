param(
    [string]$ProcessName = "java",
    [int]$ProcessId = 0,
    [Parameter(Mandatory = $true)]
    [string]$OutputCsv,
    [int]$DurationSeconds = 600
)

$presentMon = Get-Command PresentMon.exe -ErrorAction SilentlyContinue
if (-not $presentMon) {
    $local = Join-Path $PSScriptRoot "PresentMon.exe"
    if (Test-Path $local) {
        $presentMonPath = $local
    } else {
        Write-Error "PresentMon.exe not found on PATH or in perf/tools. Install PresentMon to capture external frame times."
        exit 1
    }
} else {
    $presentMonPath = $presentMon.Source
}

$outputDir = Split-Path -Parent $OutputCsv
if ($outputDir) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}

$captureArgs = @(
    "-output_file", $OutputCsv,
    "-stop_existing_session",
    "-timed", $DurationSeconds,
    "--terminate_after_timed"
)
if ($ProcessId -gt 0) {
    $captureArgs = @("-process_id", $ProcessId) + $captureArgs
} else {
    $captureArgs = @("-process_name", $ProcessName) + $captureArgs
}

& $presentMonPath @captureArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

python (Join-Path $PSScriptRoot "summarize_presentmon.py") $OutputCsv
