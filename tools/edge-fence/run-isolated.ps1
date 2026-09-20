param(
    [string]$RunDirectory = 'tmp/edge-fence/world-focused',
    [string[]]$GradleTasks = @('runGameTestServer'),
    [string]$Namespaces = 'britannia_edge_fence',
    [string]$GradleExecutable = 'gradlew.bat',
    [string]$LifecyclePhase = ''
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$run = [IO.Path]::GetFullPath((Join-Path $root $RunDirectory))
$allowed = [IO.Path]::GetFullPath((Join-Path $root 'tmp/edge-fence')) + [IO.Path]::DirectorySeparatorChar
if (!$run.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Run must be inside tmp/edge-fence.' }
foreach ($folder in @($run, (Join-Path $root 'build/test-run'))) {
    if (Test-Path (Join-Path $folder 'config/britannia_mod-server.properties')) { throw 'Refusing a directory with server credentials.' }
}
$saved = @{}
try {
    foreach ($entry in Get-ChildItem Env: | Where-Object Name -match '^(ULTIMACRAFT_|ROWAN_LIVE_|BRITANNIA_LIVE_)') {
        $saved[$entry.Name] = $entry.Value
        Remove-Item -LiteralPath ('Env:' + $entry.Name)
    }
    Set-Location $root
    if ($GradleExecutable -eq 'gradlew.bat') { $GradleExecutable = Join-Path $root 'gradlew.bat' }
    & $GradleExecutable @GradleTasks "-PgameTestRunDirectory=$run" "-PalligatorClientRunDirectory=$run" "-PedgeFenceServerRunDirectory=$run" "-PgameTestNamespaces=$Namespaces" "-PedgeFenceLifecyclePhase=$LifecyclePhase" --no-configuration-cache --console=plain
    $code = $LASTEXITCODE
} finally {
    foreach ($name in $saved.Keys) { Set-Item -LiteralPath ('Env:' + $name) -Value $saved[$name] }
}
exit $code
