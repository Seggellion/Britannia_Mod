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
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($targetPath)) | Out-Null
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

function Set-Texture($model, [string]$key, [string]$value) {
    if ($model.textures.PSObject.Properties.Name -contains $key) {
        $model.textures.$key = $value
    } else {
        $model.textures | Add-Member -NotePropertyName $key -NotePropertyValue $value
    }
}

function Transform-Model($model, [scriptblock]$transform) {
    foreach ($element in @($model.elements)) {
        $element.from = @($transform.Invoke(
            [double]$element.from[0], [double]$element.from[1], [double]$element.from[2]))
        $element.to = @($transform.Invoke(
            [double]$element.to[0], [double]$element.to[1], [double]$element.to[2]))
        if ($null -ne $element.rotation -and $null -ne $element.rotation.origin) {
            $element.rotation.origin = @($transform.Invoke(
                [double]$element.rotation.origin[0],
                [double]$element.rotation.origin[1],
                [double]$element.rotation.origin[2]))
        }
    }
}

function Write-BlockResources([string]$id, [int]$rootPart) {
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
            when = [ordered]@{ facing = $direction.facing; part = "$rootPart" }
            apply = $apply
        }
    }
    Write-Json (Join-Path $stateRoot "$id.json") ([ordered]@{ multipart = @($multipart) })
    Write-Json (Join-Path $itemRoot "$id.json") ([ordered]@{ parent = "britannia_mod:block/new_assets/$id" })
    Write-Json (Join-Path $dataRoot "$id.json") ([ordered]@{ type = 'minecraft:block'; pools = @() })
}

$marketZip = Join-Path $RawRoot 'Medieval Market Furniture Set.zip'
$marketBase = 'Medieval Market Furniture Set/Medieval Market Furniture Set/Raw Files'
$redModel = (Read-ZipText $marketZip "$marketBase/models/medieval_market_wagon_red.json") | ConvertFrom-Json
$purpleModel = (Read-ZipText $marketZip "$marketBase/models/medieval_market_wagon_purple.json") | ConvertFrom-Json

# The client applies the owner-requested 1.2 visual scale after baking. Keeping the purchased
# coordinates here preserves the vanilla JSON parser's -16..32 element boundary.
$redModel.credit = 'Temporary purchased red cart art; client-rendered at 1.2 scale'
$purpleModel.credit = 'Temporary purchased purple cart art; client-rendered at 1.2 scale'

Set-Texture $redModel '1' 'britannia_mod:block/new_assets/merchant_cart_red'
Set-Texture $redModel 'particle' 'britannia_mod:block/new_assets/merchant_cart_red'
Set-Texture $purpleModel '1' 'britannia_mod:block/new_assets/merchant_cart_purple'
Set-Texture $purpleModel 'particle' 'britannia_mod:block/new_assets/merchant_cart_purple'
Write-Json (Join-Path $modelRoot 'merchant_cart_red.json') $redModel
Write-Json (Join-Path $modelRoot 'merchant_cart_purple.json') $purpleModel

Copy-ZipEntry $marketZip "$marketBase/textures/medieval_market_wagon_red.png" (Join-Path $textureRoot 'merchant_cart_red.png')
Copy-ZipEntry $marketZip "$marketBase/textures/medieval_market_wagon_purple.png" (Join-Path $textureRoot 'merchant_cart_purple.png')

foreach ($color in @('blue', 'green', 'yellow', 'white')) {
    $model = (($redModel | ConvertTo-Json -Depth 100) | ConvertFrom-Json)
    Set-Texture $model '1' "minecraft:block/${color}_wool"
    Set-Texture $model 'particle' "minecraft:block/${color}_wool"
    $model.credit = "Temporary $color placeholder using purchased cart geometry"
    Write-Json (Join-Path $modelRoot "merchant_cart_$color.json") $model
}

$scarecrow = (Read-ZipText $marketZip "$marketBase/models/medieval_market_scarecrow.json") | ConvertFrom-Json
Transform-Model $scarecrow { param($x, $y, $z) @(($x + 4.5); ($y + 0.21682); $z) }
# The source contains four negative-size "inverted" cubes exactly coincident with ordinary
# arm/leg cubes. Minecraft bakes both surfaces, causing dark z-fighting artifacts in-world.
$scarecrow.elements = @($scarecrow.elements | Where-Object { $_.name -notlike '* inverted' })
if ($scarecrow.groups -and $scarecrow.groups.Count -gt 0) {
    $scarecrow.groups[0].children = @(0..($scarecrow.elements.Count - 1))
}
# The source also mixes ordinary shaded cubes with shade-disabled mirrored limbs. Vanilla's
# directional lighting makes the head, hat, body, and one side nearly black in-world.
$scarecrow | Add-Member -NotePropertyName ambientocclusion -NotePropertyValue $false -Force
foreach ($element in $scarecrow.elements) {
    $element | Add-Member -NotePropertyName shade -NotePropertyValue $false -Force
}
Set-Texture $scarecrow '0' 'britannia_mod:block/new_assets/scarecrow'
Set-Texture $scarecrow 'particle' 'britannia_mod:block/new_assets/scarecrow'
Write-Json (Join-Path $modelRoot 'scarecrow.json') $scarecrow
Copy-ZipEntry $marketZip "$marketBase/textures/medieval_market_scarecrow.png" (Join-Path $textureRoot 'scarecrow.png')

$tailoringZip = Join-Path $RawRoot 'Nexo Assets - Tailoring Station.zip'
$tailoringModelBase = 'Nexo/pack/external_packs/WorkshopSix/assets/minecraft/models/workshop_six/tailoring_station'
$tailoringTextureBase = 'Nexo/pack/external_packs/WorkshopSix/assets/minecraft/textures/workshop_six/tailoring_station'

$dressForm = (Read-ZipText $tailoringZip "$tailoringModelBase/mannequin.json") | ConvertFrom-Json
$dressForm | Add-Member -NotePropertyName ambientocclusion -NotePropertyValue $false -Force
$head = @($dressForm.elements) | Where-Object { [double]$_.from[1] -eq 24.0 -and [double]$_.to[1] -eq 32.0 } | Select-Object -First 1
if ($null -eq $head) { throw 'Could not locate dress-form head cube for top-face repair' }
$head | Add-Member -NotePropertyName shade -NotePropertyValue $false -Force
# The original top UV spans a baked dark-to-light gradient. Sample one neutral atlas pixel instead.
$head.faces.up.uv = @(5.0, 5.0, 5.25, 5.25)
Set-Texture $dressForm '0' 'britannia_mod:block/new_assets/dress_form'
Set-Texture $dressForm 'particle' 'britannia_mod:block/new_assets/dress_form'
Write-Json (Join-Path $modelRoot 'dress_form.json') $dressForm
Copy-ZipEntry $tailoringZip "$tailoringTextureBase/mannequin.png" (Join-Path $textureRoot 'dress_form.png')

$loom = (Read-ZipText $tailoringZip "$tailoringModelBase/loom.json") | ConvertFrom-Json
Transform-Model $loom { param($x, $y, $z) @(($x + 16.0); (($y * 1.5) - 16.0); (($z + 4.0) * 16.0 / 36.0)) }
Set-Texture $loom '3' 'britannia_mod:block/new_assets/loom'
Set-Texture $loom 'particle' 'britannia_mod:block/new_assets/loom'
$loom.credit = 'Temporary purchased loom art re-authored to 32x48x16 voxels'
Write-Json (Join-Path $modelRoot 'loom.json') $loom
Copy-ZipEntry $tailoringZip "$tailoringTextureBase/loom.png" (Join-Path $textureRoot 'loom.png')

$fountainSource = Join-Path $RawRoot 'fountain'
$fountain = Get-Content -Raw -LiteralPath (Join-Path $fountainSource 'models/item/tiered_fountain_angel.json') | ConvertFrom-Json
Transform-Model $fountain { param($x, $y, $z) @(($x + 8.0); $y; ($z + 8.0)) }
Set-Texture $fountain '2' 'britannia_mod:block/new_assets/fountain_water'
Set-Texture $fountain '3' 'britannia_mod:block/new_assets/fountain'
Set-Texture $fountain 'particle' 'britannia_mod:block/new_assets/fountain'
Write-Json (Join-Path $modelRoot 'fountain.json') $fountain
Copy-Item -LiteralPath (Join-Path $fountainSource 'textures/tiered_fountain.png') -Destination (Join-Path $textureRoot 'fountain.png') -Force
Copy-Item -LiteralPath (Join-Path $fountainSource 'textures/fountain_water.png') -Destination (Join-Path $textureRoot 'fountain_water.png') -Force
Copy-Item -LiteralPath (Join-Path $fountainSource 'textures/fountain_water.png.mcmeta') -Destination (Join-Path $textureRoot 'fountain_water.png.mcmeta') -Force

foreach ($id in @(
    'merchant_cart_red', 'merchant_cart_purple', 'merchant_cart_blue', 'merchant_cart_black',
    'merchant_cart_green', 'merchant_cart_yellow', 'merchant_cart_white')) {
    Write-BlockResources $id 13
}
Write-BlockResources 'fountain' 13
Write-BlockResources 'scarecrow' 0
Write-BlockResources 'dress_form' 0
Write-BlockResources 'loom' 2

Write-Output 'Milestone 3 temporary assets normalized successfully.'
