param(
    [string]$RunDirectory = 'tmp/alligator-0.1.8d/world-focused',
    [string[]]$GradleTasks = @('runGameTestServer'),
    [string]$Namespaces = 'britannia_mod,britannia_alligator,britannia_alligator_idle,britannia_alligator_performance'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$run = [IO.Path]::GetFullPath((Join-Path $root $RunDirectory))
$allowed = [IO.Path]::GetFullPath((Join-Path $root 'tmp/alligator-0.1.8d')) + [IO.Path]::DirectorySeparatorChar
if (!$run.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Disposable run directory must be inside tmp/alligator-0.1.8d.'
}
if (Test-Path (Join-Path $run 'config/britannia_mod-server.properties')) {
    throw 'Refusing a run directory containing server credentials.'
}
if (Test-Path (Join-Path $root 'build/test-run/config/britannia_mod-server.properties')) {
    throw 'Refusing a unit-test directory containing server credentials.'
}
$saved = @{}
try {
    foreach ($entry in Get-ChildItem Env: | Where-Object Name -match '^(ULTIMACRAFT_|ROWAN_LIVE_|BRITANNIA_LIVE_)') {
        $saved[$entry.Name] = $entry.Value
        Remove-Item -LiteralPath ('Env:' + $entry.Name)
    }
    Set-Location $root
    & ./gradlew.bat @GradleTasks "-PgameTestRunDirectory=$run" "-PalligatorClientRunDirectory=$run" "-PgameTestNamespaces=$Namespaces" --no-configuration-cache --console=plain
    $code = $LASTEXITCODE
} finally {
    foreach ($name in $saved.Keys) { Set-Item -LiteralPath ('Env:' + $name) -Value $saved[$name] }
}
exit $code
