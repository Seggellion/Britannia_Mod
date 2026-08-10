param(
    [Parameter(Mandatory = $true)][string]$RawRoot,
    [Parameter(Mandatory = $true)][string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$archive = Join-Path $RawRoot 'models to import/training_dummy.zip'
$itemSource = Join-Path $RawRoot 'models to import/training_dummy/resourcepack/assets/fischvogel/models/training_dummy/training_dummy_gray.json'
$assetRoot = Join-Path $ProjectRoot 'src/main/resources/assets/britannia_mod'
$dataRoot = Join-Path $ProjectRoot 'src/main/resources/data/britannia_mod'
$geoTarget = Join-Path $assetRoot 'geo/training_dummy.geo.json'
$animationTarget = Join-Path $assetRoot 'animations/training_dummy.animation.json'
$textureTarget = Join-Path $assetRoot 'textures/block/new_assets/training_dummy.png'
$itemTarget = Join-Path $assetRoot 'models/item/training_dummy.json'
$emptyBlockModelTarget = Join-Path $assetRoot 'models/block/new_assets/training_dummy_empty.json'
$blockstateTarget = Join-Path $assetRoot 'blockstates/training_dummy.json'
$lootTarget = Join-Path $dataRoot 'loot_table/blocks/training_dummy.json'

foreach ($required in @($archive, $itemSource)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing Milestone 7 source: $required" }
}
foreach ($directory in @($geoTarget, $animationTarget, $textureTarget, $itemTarget, $emptyBlockModelTarget, $blockstateTarget, $lootTarget)) {
    [IO.Directory]::CreateDirectory((Split-Path -Parent $directory)) | Out-Null
}

function Read-ZipText([IO.Compression.ZipArchive]$zip, [string]$path) {
    $entry = $zip.Entries | Where-Object FullName -eq $path
    if ($null -eq $entry) { throw "Archive entry not found: $path" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Write-Json([object]$value, [string]$path, [int]$depth = 100) {
    $json = $value | ConvertTo-Json -Depth $depth
    [IO.File]::WriteAllText($path, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

function Tx([double]$x) { return ($x * 1.6D) + 8.0D }
function Ty([double]$y) { return ($y - 4.0D) * (48.0D / 44.0D) }
function Has-NonZero([object[]]$values) {
    foreach ($value in $values) { if ([math]::Abs([double]$value) -gt 0.000001D) { return $true } }
    return $false
}
function Pivot([object[]]$values) { return @(-(Tx ([double]$values[0])), (Ty ([double]$values[1])), [double]$values[2]) }
function Rotation([object[]]$values) { return @([double]$values[0], -[double]$values[1], -[double]$values[2]) }

$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    $source = (Read-ZipText $zip 'fv_punching_bags/plugins/ModelEngine/blueprints/fv_punching_bag_gray.bbmodel') | ConvertFrom-Json
    $textureEntry = $zip.Entries | Where-Object FullName -eq 'fv_punching_bags/plugins/ItemsAdder/contents/fv_punching_bag/resourcepack/assets/fischvogel/textures/fv_punching_bag/gray.png'
    if ($null -eq $textureEntry) { throw 'Gray training-dummy texture entry not found.' }
    $sourceStream = $textureEntry.Open()
    $targetStream = [IO.File]::Create($textureTarget)
    try { $sourceStream.CopyTo($targetStream) } finally { $targetStream.Dispose(); $sourceStream.Dispose() }
} finally { $zip.Dispose() }

$groups = @{}
foreach ($group in $source.groups) { $groups[[string]$group.uuid] = $group }
$elements = @{}
foreach ($element in $source.elements) { $elements[[string]$element.uuid] = $element }

function Cube([object]$element) {
    $from = @($element.from); $to = @($element.to)
    $uv = if ($null -eq $element.uv_offset) { @(0, 0) } else { @($element.uv_offset) }
    $cube = [ordered]@{
        origin = @(-(Tx ([double]$to[0])), (Ty ([double]$from[1])), [double]$from[2])
        size = @((([double]$to[0] - [double]$from[0]) * 1.6D), (([double]$to[1] - [double]$from[1]) * (48.0D / 44.0D)), ([double]$to[2] - [double]$from[2]))
        uv = @([double]$uv[0], [double]$uv[1])
    }
    if (Has-NonZero @($element.rotation)) { $cube.pivot = Pivot @($element.origin); $cube.rotation = Rotation @($element.rotation) }
    if ([math]::Abs([double]$element.inflate) -gt 0.000001D) { $cube.inflate = [double]$element.inflate }
    if ($element.mirror_uv -eq $true) { $cube.mirror = $true }
    return $cube
}

$bones = [Collections.Generic.List[object]]::new()
function Add-Bone([object]$node, [string]$parentName) {
    $group = $groups[[string]$node.uuid]
    if ($null -eq $group) { throw "Unknown training-dummy group $($node.uuid)" }
    $bone = [ordered]@{ name = [string]$group.name; pivot = Pivot @($group.origin) }
    if ($parentName) { $bone.parent = $parentName }
    if (Has-NonZero @($group.rotation)) { $bone.rotation = Rotation @($group.rotation) }
    $cubes = [Collections.Generic.List[object]]::new()
    foreach ($child in @($node.children)) {
        if ($child -is [string] -and $elements.ContainsKey($child) -and $elements[$child].export -ne $false) { $cubes.Add((Cube $elements[$child])) }
    }
    if ($cubes.Count) { $bone.cubes = $cubes }
    $bones.Add($bone)
    foreach ($child in @($node.children)) { if ($child -isnot [string]) { Add-Bone $child ([string]$group.name) } }
}

$rootCubes = [Collections.Generic.List[object]]::new()
foreach ($root in @($source.outliner)) {
    if ($root -is [string]) { $rootCubes.Add((Cube $elements[$root])) } else { Add-Bone $root '' }
}
if ($rootCubes.Count) { $bones.Insert(0, [ordered]@{ name = 'training_dummy_base'; pivot = @(0, 0, 0); cubes = $rootCubes }) }

$geometry = [ordered]@{
    format_version = '1.12.0'
    'minecraft:geometry' = @([ordered]@{
        description = [ordered]@{
            identifier = 'geometry.training_dummy'; texture_width = [int]$source.resolution.width; texture_height = [int]$source.resolution.height
            visible_bounds_width = 2.5; visible_bounds_height = 3.25; visible_bounds_offset = @(0.5, 1.5, 0)
        }
        bones = $bones
    })
}

function AnimValue([object]$value) {
    $number = 0.0D
    if ([double]::TryParse([string]$value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$number)) { return $number }
    return [string]$value
}
function AnimVector([object]$point, [string]$channel) {
    $v = @((AnimValue $point.x), (AnimValue $point.y), (AnimValue $point.z))
    if ($v | Where-Object { $_ -is [string] }) { return $v }
    switch ($channel) {
        'rotation' { return @($v[0], -[double]$v[1], -[double]$v[2]) }
        'position' { return @((-1.6D * [double]$v[0]), ((48.0D / 44.0D) * [double]$v[1]), [double]$v[2]) }
        default { return $v }
    }
}

$sourceAnimation = @($source.animations)[0]
$boneAnimations = [ordered]@{}
foreach ($property in $sourceAnimation.animators.PSObject.Properties) {
    $animator = $property.Value
    if ([string]$animator.type -ne 'bone') { continue }
    $group = $groups[[string]$property.Name]
    $name = if ($null -ne $group) { [string]$group.name } else { [string]$animator.name }
    $channels = [ordered]@{}
    foreach ($keyframe in @($animator.keyframes)) {
        $channel = [string]$keyframe.channel
        if ($channel -notin @('rotation', 'position', 'scale')) { continue }
        if (-not $channels.Contains($channel)) { $channels[$channel] = [ordered]@{} }
        $time = (([double]$keyframe.time) * 0.2D).ToString('0.#####', [Globalization.CultureInfo]::InvariantCulture)
        $channels[$channel][$time] = [ordered]@{ vector = AnimVector (@($keyframe.data_points)[0]) $channel }
    }
    if ($channels.Count) { $boneAnimations[$name] = $channels }
}
$animations = [ordered]@{
    format_version = '1.8.0'
    animations = [ordered]@{ 'animation.training_dummy.hit' = [ordered]@{ loop = $false; animation_length = 1.0D; bones = $boneAnimations } }
}

$item = Get-Content -Raw -Encoding UTF8 -LiteralPath $itemSource | ConvertFrom-Json
$item.textures.'0' = 'britannia_mod:block/new_assets/training_dummy'
$item.textures | Add-Member -NotePropertyName particle -NotePropertyValue 'britannia_mod:block/new_assets/training_dummy' -Force
Write-Json $geometry $geoTarget
Write-Json $animations $animationTarget
Write-Json $item $itemTarget
Write-Json ([ordered]@{}) $emptyBlockModelTarget
Write-Json ([ordered]@{ multipart = @([ordered]@{ apply = [ordered]@{ model = 'britannia_mod:block/new_assets/training_dummy_empty' } }) }) $blockstateTarget
Write-Json ([ordered]@{ type = 'minecraft:block'; pools = @() }) $lootTarget

Write-Output 'Milestone 7 training-dummy geometry, one-shot animation, item model, and texture imported successfully.'
