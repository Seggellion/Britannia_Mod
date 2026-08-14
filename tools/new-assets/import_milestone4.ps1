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
    [IO.File]::WriteAllText($path, ($value | ConvertTo-Json -Depth 100) + "`n", $utf8)
}

function Set-Texture($model, [string]$key, [string]$value) {
    if ($model.textures.PSObject.Properties.Name -contains $key) {
        $model.textures.$key = $value
    } else {
        $model.textures | Add-Member -NotePropertyName $key -NotePropertyValue $value
    }
}

function Translate-Model($model, [double]$xOffset, [double]$yOffset, [double]$zOffset) {
    foreach ($element in @($model.elements)) {
        $element.from = @(
            ([double]$element.from[0] + $xOffset)
            ([double]$element.from[1] + $yOffset)
            ([double]$element.from[2] + $zOffset))
        $element.to = @(
            ([double]$element.to[0] + $xOffset)
            ([double]$element.to[1] + $yOffset)
            ([double]$element.to[2] + $zOffset))
        if ($null -ne $element.rotation -and $null -ne $element.rotation.origin) {
            $element.rotation.origin = @(
                ([double]$element.rotation.origin[0] + $xOffset)
                ([double]$element.rotation.origin[1] + $yOffset)
                ([double]$element.rotation.origin[2] + $zOffset))
        }
    }
}

function Faces([string]$texture) {
    return [ordered]@{
        north = [ordered]@{ texture = $texture }
        east = [ordered]@{ texture = $texture }
        south = [ordered]@{ texture = $texture }
        west = [ordered]@{ texture = $texture }
        up = [ordered]@{ texture = $texture }
        down = [ordered]@{ texture = $texture }
    }
}

function Write-BlockResources([string]$id) {
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
            when = [ordered]@{ facing = $direction.facing; part = '0' }
            apply = $apply
        }
    }
    Write-Json (Join-Path $stateRoot "$id.json") ([ordered]@{ multipart = @($multipart) })
    Write-Json (Join-Path $itemRoot "$id.json") ([ordered]@{ parent = "britannia_mod:block/new_assets/$id" })
    Write-Json (Join-Path $dataRoot "$id.json") ([ordered]@{ type = 'minecraft:block'; pools = @() })
}

$crateZip = Join-Path $RawRoot 'Nexo Assets - Crates & Barrels.zip'
$small = (Read-ZipText $crateZip 'Raw Files/models/crate.json') | ConvertFrom-Json
Set-Texture $small '1' 'britannia_mod:block/new_assets/small_crate'
Set-Texture $small 'particle' 'britannia_mod:block/new_assets/small_crate'
$small.credit = 'Temporary purchased small-crate art'
Write-Json (Join-Path $modelRoot 'small_crate.json') $small
Copy-ZipEntry $crateZip 'Raw Files/textures/crate.png' (Join-Path $textureRoot 'small_crate.png')

$large = (Read-ZipText $crateZip 'Raw Files/models/large_crate.json') | ConvertFrom-Json
Translate-Model $large 6.0 1.0 3.0
Set-Texture $large '0' 'britannia_mod:block/new_assets/large_crate'
Set-Texture $large 'particle' 'britannia_mod:block/new_assets/large_crate'
$large.credit = 'Temporary purchased large-crate art normalized to a 2x2x2 structure'
Write-Json (Join-Path $modelRoot 'large_crate.json') $large
Copy-ZipEntry $crateZip 'Raw Files/textures/large_crate.png' (Join-Path $textureRoot 'large_crate.png')

$medium = [ordered]@{
    credit = 'Code-authored unmistakable medium-crate placeholder'
    parent = 'minecraft:block/block'
    textures = [ordered]@{
        wood = 'minecraft:block/magenta_concrete'
        brace = 'minecraft:block/black_concrete'
        particle = 'minecraft:block/magenta_concrete'
    }
    elements = @(
        [ordered]@{ from = @(1, 0, 1); to = @(15, 13, 15); faces = Faces '#wood' },
        [ordered]@{ from = @(0.5, 0, 0.5); to = @(15.5, 2, 15.5); faces = Faces '#brace' },
        [ordered]@{ from = @(0.5, 11, 0.5); to = @(15.5, 14, 15.5); faces = Faces '#brace' },
        [ordered]@{ from = @(2, 2, 0); to = @(4, 11, 1); faces = Faces '#brace' },
        [ordered]@{ from = @(12, 2, 0); to = @(14, 11, 1); faces = Faces '#brace' }
    )
    display = [ordered]@{
        gui = [ordered]@{ rotation = @(25, 225, 0); translation = @(0, 0, 0); scale = @(0.75, 0.75, 0.75) }
        ground = [ordered]@{ translation = @(0, 3, 0); scale = @(0.4, 0.4, 0.4) }
        fixed = [ordered]@{ scale = @(0.65, 0.65, 0.65) }
    }
}
Write-Json (Join-Path $modelRoot 'medium_crate.json') $medium

foreach ($id in @('small_crate', 'medium_crate', 'large_crate')) {
    Write-BlockResources $id
}

Write-Output 'Milestone 4 crate assets normalized successfully.'
