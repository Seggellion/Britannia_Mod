param(
    [string[]]$GradleTasks = @('test'),
    [string]$RunDirectory = 'tmp/plaster-corner-0.1.8d/world',
    [string]$GradleExecutable = 'gradlew.bat'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$run = [IO.Path]::GetFullPath((Join-Path $root $RunDirectory))
$allowed = [IO.Path]::GetFullPath((Join-Path $root 'tmp/plaster-corner-0.1.8d')) + [IO.Path]::DirectorySeparatorChar
if (!$run.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Run must be inside tmp/plaster-corner-0.1.8d.' }
foreach ($folder in @($run, (Join-Path $root 'build/test-run'))) {
    if (Test-Path (Join-Path $folder 'config/britannia_mod-server.properties')) { throw 'Refusing a credential-bearing run directory.' }
}
$saved = @{}
try {
    foreach ($entry in Get-ChildItem Env: | Where-Object Name -match '^(ULTIMACRAFT_|ROWAN_LIVE_|BRITANNIA_LIVE_)') {
        $saved[$entry.Name] = $entry.Value
        Remove-Item -LiteralPath ('Env:' + $entry.Name)
    }
    Push-Location $root
    if ($GradleExecutable -eq 'gradlew.bat') { $GradleExecutable = Join-Path $root 'gradlew.bat' }
    & $GradleExecutable @GradleTasks "-PgameTestRunDirectory=$run" "-PalligatorClientRunDirectory=$run" "-PedgeFenceServerRunDirectory=$run" --no-configuration-cache --console=plain
    $code = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) { Set-Item -LiteralPath ('Env:' + $name) -Value $saved[$name] }
}
exit $code
