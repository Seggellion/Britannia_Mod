param(
    [Parameter(Mandatory = $true)]
    [string]$RawRoot,

    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$utf8 = [System.Text.UTF8Encoding]::new($false)
$assetRoot = Join-Path $ProjectRoot 'src/main/resources/assets/britannia_mod'
$dataRoot = Join-Path $ProjectRoot 'src/main/resources/data/britannia_mod/loot_table/blocks'
$modelRoot = Join-Path $assetRoot 'models/block/new_assets'
$itemRoot = Join-Path $assetRoot 'models/item'
$stateRoot = Join-Path $assetRoot 'blockstates'
$textureRoot = Join-Path $assetRoot 'textures/block/new_assets'

@($modelRoot, $itemRoot, $stateRoot, $textureRoot, $dataRoot) | ForEach-Object {
    [IO.Directory]::CreateDirectory($_) | Out-Null
}

function Read-ZipText([string]$zipPath, [string]$entryPath) {
    $zip = [IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $entry = $zip.GetEntry($entryPath)
        if ($null -eq $entry) { throw "Missing archive entry: $entryPath" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $zip.Dispose()
    }
}

function Copy-ZipEntry([string]$zipPath, [string]$entryPath, [string]$targetPath) {
    $zip = [IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $entry = $zip.GetEntry($entryPath)
        if ($null -eq $entry) { throw "Missing archive entry: $entryPath" }
        $source = $entry.Open()
        $target = [IO.File]::Create($targetPath)
        try { $source.CopyTo($target) } finally { $target.Dispose(); $source.Dispose() }
    } finally {
        $zip.Dispose()
    }
}

function Write-Json([string]$path, $value) {
    $json = $value | ConvertTo-Json -Depth 100
    [IO.File]::WriteAllText($path, $json + "`n", $utf8)
}

function Transform-Model($model, [scriptblock]$transform) {
    foreach ($element in @($model.elements)) {
        $from = @($transform.Invoke(
            [double]$element.from[0], [double]$element.from[1], [double]$element.from[2]))
        $to = @($transform.Invoke(
            [double]$element.to[0], [double]$element.to[1], [double]$element.to[2]))
        $element.from = @(
            [Math]::Min($from[0], $to[0]),
            [Math]::Min($from[1], $to[1]),
            [Math]::Min($from[2], $to[2]))
        $element.to = @(
            [Math]::Max($from[0], $to[0]),
            [Math]::Max($from[1], $to[1]),
            [Math]::Max($from[2], $to[2]))
        if ($null -ne $element.rotation -and $null -ne $element.rotation.origin) {
            $element.rotation.origin = @($transform.Invoke(
                [double]$element.rotation.origin[0],
                [double]$element.rotation.origin[1],
                [double]$element.rotation.origin[2]))
        }
    }
}

function Set-Texture($model, [string]$id) {
    $model.textures.'0' = "britannia_mod:block/new_assets/$id"
    $model.textures.particle = "britannia_mod:block/new_assets/$id"
}

function Write-BlockResources([string]$id, [int]$visualPart) {
    $directions = @(
        @{ facing = 'north'; rotation = 0 },
        @{ facing = 'east'; rotation = 90 },
        @{ facing = 'south'; rotation = 180 },
        @{ facing = 'west'; rotation = 270 }
    )
    $multipart = foreach ($direction in $directions) {
        $apply = [ordered]@{ model = "britannia_mod:block/new_assets/$id" }
        if ($direction.rotation -ne 0) { $apply.y = $direction.rotation }
        [ordered]@{
            when = [ordered]@{ facing = $direction.facing; part = "$visualPart" }
            apply = $apply
        }
    }
    Write-Json (Join-Path $stateRoot "$id.json") ([ordered]@{ multipart = @($multipart) })
    Write-Json (Join-Path $itemRoot "$id.json") ([ordered]@{ parent = "britannia_mod:block/new_assets/$id" })
    Write-Json (Join-Path $dataRoot "$id.json") ([ordered]@{ type = 'minecraft:block'; pools = @() })
}

$sourceZip = Join-Path $RawRoot 'shizuart_farmer_props.zip'
$modelBase = 'ItemsAdder/contents/shizuart_furnitures/models/farmer_props'
$textureEntry = 'ItemsAdder/contents/shizuart_furnitures/textures/farmer_props/farmer_props.png'

$well = (Read-ZipText $sourceZip "$modelBase/farmer_well.json") | ConvertFrom-Json
Transform-Model $well {
    param($x, $y, $z)
    @( (($x + 5.0) * 16.0 / 19.0); (($y - 4.0) * 32.0 / 27.5); (($z + 1.5) * 32.0 / 27.0) )
}
Set-Texture $well 'water_well'
$well.credit = 'Temporary purchased well art normalized to 16x32x32; client-rendered at 1.2 scale'
Write-Json (Join-Path $modelRoot 'water_well.json') $well

$ladder = (Read-ZipText $sourceZip "$modelBase/farmer_stepladder.json") | ConvertFrom-Json
Transform-Model $ladder {
    param($x, $y, $z)
    @( (($x + 1.0) * 16.0 / 18.0); ((($y + 4.0) * 48.0 / 35.97734) - 16.0); (($z + 1.27734) * 16.0 / 32.55468) )
}
Set-Texture $ladder 'ladder'
$ladder.credit = 'Temporary purchased stepladder art re-authored to 16x48x16 voxels'
Write-Json (Join-Path $modelRoot 'ladder.json') $ladder

Copy-ZipEntry $sourceZip $textureEntry (Join-Path $textureRoot 'water_well.png')
Copy-ZipEntry $sourceZip $textureEntry (Join-Path $textureRoot 'ladder.png')
Write-BlockResources 'water_well' 0
Write-BlockResources 'ladder' 1

Write-Output 'Milestone 5 temporary well and ladder assets normalized successfully.'
