param(
    [Parameter(Mandatory = $true)]
    [string]$RawRoot,

    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$utf8 = [Text.UTF8Encoding]::new($false)
$assets = Join-Path $ProjectRoot 'src/main/resources/assets/britannia_mod'
$models = Join-Path $assets 'models/block'
$newModels = Join-Path $models 'new_assets'
$textures = Join-Path $assets 'textures/block/new_assets'
$states = Join-Path $assets 'blockstates'
$items = Join-Path $assets 'models/item'
$loot = Join-Path $ProjectRoot 'src/main/resources/data/britannia_mod/loot_table/blocks'

function Write-Json([string]$path, $value) {
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
    [IO.File]::WriteAllText($path, (($value | ConvertTo-Json -Depth 100) + "`n"), $utf8)
}

function Read-ZipJson($zip, [string]$entryPath) {
    $entry = $zip.GetEntry($entryPath)
    if ($null -eq $entry) { throw "Missing archive entry: $entryPath" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
}

function Copy-ZipEntry($zip, [string]$entryPath, [string]$targetPath) {
    $entry = $zip.GetEntry($entryPath)
    if ($null -eq $entry) { throw "Missing archive entry: $entryPath" }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($targetPath)) | Out-Null
    $source = $entry.Open()
    $target = [IO.File]::Create($targetPath)
    try { $source.CopyTo($target) } finally { $target.Dispose(); $source.Dispose() }
}

function Shift-Y($model, [double]$offset) {
    foreach ($element in @($model.elements)) {
        $element.from[1] = [double]$element.from[1] + $offset
        $element.to[1] = [double]$element.to[1] + $offset
        if ($null -ne $element.rotation -and $null -ne $element.rotation.origin) {
            $element.rotation.origin[1] = [double]$element.rotation.origin[1] + $offset
        }
    }
}

# Split the existing city-gate model into a static floor and a camera-facing vertical model.
$fullGate = Get-Content -Raw -LiteralPath (Join-Path $models 'moongate_block.json') | ConvertFrom-Json
$baseGate = $fullGate | ConvertTo-Json -Depth 100 | ConvertFrom-Json
$baseGate.elements = @($fullGate.elements | Select-Object -Last 2)
$billboardGate = $fullGate | ConvertTo-Json -Depth 100 | ConvertFrom-Json
$billboardGate.elements = @($fullGate.elements | Select-Object -First 5)
Write-Json (Join-Path $models 'moongate_base.json') $baseGate
Write-Json (Join-Path $models 'moongate_billboard.json') $billboardGate

# Derive bottom/middle/top hedge slices from the already imported source-atlas model.
$hedgeSource = Get-Content -Raw -LiteralPath (Join-Path $newModels 'hedge_bush.json') | ConvertFrom-Json
$hedgeTexture = 'britannia_mod:block/new_assets/hedge_bush'
foreach ($textureProperty in @($hedgeSource.textures.PSObject.Properties)) {
    $textureProperty.Value = $hedgeTexture
}
$hedgeSource.credit = 'Temporary purchased hedge art; breaking rename to hedge_bush'
Write-Json (Join-Path $newModels 'hedge_bush.json') $hedgeSource
$hedgeSegments = @(
    @{ name = 'bottom'; offset = 0.6875 },
    @{ name = 'middle'; offset = 0.34375 },
    @{ name = 'top'; offset = 0.0 }
)
foreach ($segment in $hedgeSegments) {
    $model = $hedgeSource | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $model.credit = "Temporary purchased hedge art: $($segment.name) stack segment"
    foreach ($element in @($model.elements)) {
        $element.from[1] = 0
        $element.to[1] = 16
        if ($null -ne $element.rotation -and $null -ne $element.rotation.origin) {
            $element.rotation.origin[1] = 8
        }
        foreach ($face in @($element.faces.PSObject.Properties.Value)) {
            if ($null -ne $face.uv) {
                $face.uv[1] = $segment.offset
                $face.uv[3] = $segment.offset + 1.0
            }
        }
    }
    Write-Json (Join-Path $newModels "hedge_bush_$($segment.name).json") $model
}

$hedgeVariants = [ordered]@{}
$facings = @(
    @{ name = 'north'; y = 0 }, @{ name = 'east'; y = 90 },
    @{ name = 'south'; y = 180 }, @{ name = 'west'; y = 270 }
)
for ($segment = 0; $segment -lt 3; $segment++) {
    $segmentName = $hedgeSegments[$segment].name
    foreach ($facing in $facings) {
        $apply = [ordered]@{ model = "britannia_mod:block/new_assets/hedge_bush_$segmentName" }
        if ($facing.y -ne 0) { $apply.y = $facing.y }
        $hedgeVariants["facing=$($facing.name),segment=$segment"] = $apply
    }
}
Write-Json (Join-Path $states 'hedge_bush.json') ([ordered]@{ variants = $hedgeVariants })
Write-Json (Join-Path $items 'hedge_bush.json') ([ordered]@{ parent = 'britannia_mod:block/new_assets/hedge_bush_bottom' })
Write-Json (Join-Path $loot 'hedge_bush.json') ([ordered]@{
    type = 'minecraft:block'
    pools = @([ordered]@{
        rolls = 1
        entries = @([ordered]@{ type = 'minecraft:item'; name = 'britannia_mod:hedge_bush' })
        conditions = @([ordered]@{ condition = 'minecraft:survives_explosion' })
    })
    random_sequence = 'britannia_mod:blocks/hedge_bush'
})

# Import all eight placeable visuals from the previously unassigned blood pack.
$bloodZipPath = Join-Path $RawRoot 'blood.zip'
$expectedHash = '163669328cb2eb8f90610012f19372e119e26c603897351121e10cd1e839da60'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $bloodZipPath).Hash.ToLowerInvariant() -ne $expectedHash) {
    throw 'blood.zip checksum does not match the inventoried source pack'
}
$bloodZip = [IO.Compression.ZipFile]::OpenRead($bloodZipPath)
try {
    $modelBase = 'blood/plugins/Nexo/pack/assets/fischvogel/models/fv_blood'
    $textureBase = 'blood/plugins/Nexo/pack/assets/fischvogel/textures/fv_blood'
    $sourceNames = @(
        'big-blood-1', 'big-blood-2', 'small-blood-1', 'small-blood-2',
        'roof-small-blood-1', 'roof-small-blood-2', 'roof-big-blood-1', 'blood-footstep'
    )
    for ($index = 0; $index -lt $sourceNames.Count; $index++) {
        $sourceName = $sourceNames[$index]
        $model = Read-ZipJson $bloodZip "$modelBase/$sourceName.json"
        if ($sourceName.StartsWith('roof-')) { Shift-Y $model 8.05 }
        $textureId = "britannia_mod:block/new_assets/pool_of_blood/variant_$index"
        foreach ($textureProperty in @($model.textures.PSObject.Properties)) {
            if ($textureProperty.Name -ne 'particle') {
                $textureProperty.Value = $textureId
            }
        }
        $model.textures | Add-Member -NotePropertyName '0' -NotePropertyValue $textureId -Force
        if ($model.textures.PSObject.Properties.Name -contains 'particle') {
            $model.textures.particle = $textureId
        } else {
            $model.textures | Add-Member -NotePropertyName particle -NotePropertyValue $textureId
        }
        $model.credit = "Temporary purchased pool-of-blood visual $index from $sourceName"
        Write-Json (Join-Path $newModels "pool_of_blood_$index.json") $model
        Copy-ZipEntry $bloodZip "$textureBase/$sourceName.png" (Join-Path $textures "pool_of_blood/variant_$index.png")
    }
} finally {
    $bloodZip.Dispose()
}

$bloodVariants = [ordered]@{}
for ($variant = 0; $variant -lt 8; $variant++) {
    foreach ($facing in $facings) {
        $apply = [ordered]@{ model = "britannia_mod:block/new_assets/pool_of_blood_$variant" }
        if ($facing.y -ne 0) { $apply.y = $facing.y }
        $bloodVariants["facing=$($facing.name),variant=$variant"] = $apply
    }
}
Write-Json (Join-Path $states 'pool_of_blood.json') ([ordered]@{ variants = $bloodVariants })
Write-Json (Join-Path $items 'pool_of_blood.json') ([ordered]@{ parent = 'britannia_mod:block/new_assets/pool_of_blood_0' })
Write-Json (Join-Path $loot 'pool_of_blood.json') ([ordered]@{
    type = 'minecraft:block'
    pools = @([ordered]@{
        rolls = 1
        entries = @([ordered]@{ type = 'minecraft:item'; name = 'britannia_mod:pool_of_blood' })
        conditions = @([ordered]@{ condition = 'minecraft:survives_explosion' })
    })
    random_sequence = 'britannia_mod:blocks/pool_of_blood'
})

Write-Output 'Post-closure defect assets imported and derived successfully.'
