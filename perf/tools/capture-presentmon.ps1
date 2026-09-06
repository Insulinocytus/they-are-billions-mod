param(
    [string]$ProcessName = "javaw",
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

& $presentMonPath -process_name $ProcessName -output_file $OutputCsv -stop_existing_session -timed $DurationSeconds
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

python (Join-Path $PSScriptRoot "summarize_presentmon.py") $OutputCsv
