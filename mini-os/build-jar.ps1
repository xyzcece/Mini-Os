$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcDir = Join-Path $root "src"
$binDir = Join-Path $root "bin"
$jarPath = Join-Path $root "Hyperpuff.jar"

$javac = Get-Command javac -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
$jar = Get-Command jar -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source

if (-not $javac -or -not $jar) {
    $jdkBin = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName "bin" } |
        Where-Object { Test-Path (Join-Path $_ "javac.exe") } |
        Select-Object -First 1
    if ($jdkBin) {
        $javac = Join-Path $jdkBin "javac.exe"
        $jar = Join-Path $jdkBin "jar.exe"
    }
}

if (-not $javac -or -not $jar) {
    throw "JDK tools (javac / jar) not found. Please install JDK 21+ and add it to PATH."
}

if (Test-Path $binDir) {
    Remove-Item -Recurse -Force (Join-Path $binDir "*") -ErrorAction SilentlyContinue
} else {
    New-Item -ItemType Directory -Path $binDir | Out-Null
}

$files = Get-ChildItem -Recurse -Filter *.java $srcDir | Select-Object -ExpandProperty FullName
Write-Host "Compiling $($files.Count) Java source files (targeting Java 21)..."
& $javac --release 21 -d $binDir $files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (Test-Path $jarPath) { Remove-Item -Force $jarPath }
Write-Host "Packaging Hyperpuff.jar..."
& $jar --create --file $jarPath --main-class app.Main -C $binDir .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$sizeKb = [Math]::Round((Get-Item $jarPath).Length / 1KB, 1)
Write-Host "Done! Built $jarPath ($sizeKb KB)"
Write-Host "Run with: java -jar Hyperpuff.jar"
