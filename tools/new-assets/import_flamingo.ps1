param(
    [Parameter(Mandatory = $true)]
    [string]$RawRoot,

    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

$flamingoRoot = Join-Path $RawRoot 'models to import/flamingo'
if (-not (Test-Path -LiteralPath $flamingoRoot -PathType Container)) {
    $flamingoRoot = Join-Path $RawRoot 'flamingo'
}

$inputs = [ordered]@{
    'flamingo_pink.geo.json' = 'e7f25e8b5e15a73285ff7ad0031d24f57c89d3d9ed8eb7e9d0558cf9723d8e2d'
    'flamingo_rose.geo.json' = '3620bf713f76e3b9813e68552ee549f9c20520c4b9810e879a714752d17de220'
    'flamingo_white.geo.json' = '3620bf713f76e3b9813e68552ee549f9c20520c4b9810e879a714752d17de220'
    'flamingo_pink.animation.json' = 'd207193568576b6edd78351a2beec9d57a0705f57f872b5a0c890f0be3af8bec'
    'flamingo_rose.animation.json' = 'd207193568576b6edd78351a2beec9d57a0705f57f872b5a0c890f0be3af8bec'
    'flamingo_white.animation.json' = 'd207193568576b6edd78351a2beec9d57a0705f57f872b5a0c890f0be3af8bec'
    'nm_flamingo_texture.png' = 'c84131f1b102e300519b7489f1015574aec3bd63bb726d45d93e3c3de1f1cd87'
    'nm_flamingo_rose_texture.png' = '5175b1031937958880dd9de793faab8ccd521902f8e0461c7358dbceef39c6c7'
    'nm_flamingo_white_texture.png' = '1324af1268678ab5edd37c1c1ff6bb474e5e212849eb78b2c0c0c879c7663332'
    'idle.mp3' = '5a6303f397c6353e91fd719ea7ead92f3da1241e5ef476ba68b14eef0d20914d'
}
foreach ($entry in $inputs.GetEnumerator()) {
    $path = Join-Path $flamingoRoot $entry.Key
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($actual -ne $entry.Value) {
        throw "Flamingo source checksum mismatch: $($entry.Key)"
    }
}

$assets = Join-Path $ProjectRoot 'src/main/resources/assets/britannia_mod'
$geoTarget = Join-Path $assets 'geo/flamingo.geo.json'
$animationTarget = Join-Path $assets 'animations/flamingo.animation.json'
$textureDirectory = Join-Path $assets 'textures/entity'
$soundTarget = Join-Path $assets 'sounds/flamingo_idle.ogg'
$itemTarget = Join-Path $assets 'models/item/flamingo_spawn_egg.json'
$lootTarget = Join-Path $ProjectRoot 'src/main/resources/data/britannia_mod/loot_table/entities/flamingo.json'

foreach ($directory in @(
        [IO.Path]::GetDirectoryName($geoTarget),
        [IO.Path]::GetDirectoryName($animationTarget),
        $textureDirectory,
        [IO.Path]::GetDirectoryName($soundTarget),
        [IO.Path]::GetDirectoryName($itemTarget),
        [IO.Path]::GetDirectoryName($lootTarget))) {
    [IO.Directory]::CreateDirectory($directory) | Out-Null
}

# All three supplied rigs and animations are equivalent. Pink is the canonical shared rig.
Copy-Item -LiteralPath (Join-Path $flamingoRoot 'flamingo_pink.geo.json') -Destination $geoTarget -Force
Copy-Item -LiteralPath (Join-Path $flamingoRoot 'flamingo_pink.animation.json') -Destination $animationTarget -Force
Copy-Item -LiteralPath (Join-Path $flamingoRoot 'nm_flamingo_texture.png') -Destination (Join-Path $textureDirectory 'flamingo_pink.png') -Force
Copy-Item -LiteralPath (Join-Path $flamingoRoot 'nm_flamingo_rose_texture.png') -Destination (Join-Path $textureDirectory 'flamingo_rose.png') -Force
Copy-Item -LiteralPath (Join-Path $flamingoRoot 'nm_flamingo_white_texture.png') -Destination (Join-Path $textureDirectory 'flamingo_white.png') -Force

# The supplied feet are zero-height planes with coincident top/bottom faces. Retain the
# intended upper foot art and remove only the hidden bottom faces to prevent z-fighting.
$geo = [IO.File]::ReadAllText($geoTarget)
$footBottomFacePatterns = @(
    ',\r?\n\s*"down": \{"uv": \[31, 6\], "uv_size": \[3, -3\]\}',
    ',\r?\n\s*"down": \{"uv": \[34, 6\], "uv_size": \[-3, -3\]\}'
)
foreach ($pattern in $footBottomFacePatterns) {
    if ([Text.RegularExpressions.Regex]::Matches($geo, $pattern).Count -ne 1) {
        throw "Expected one supplied Flamingo foot bottom face matching: $pattern"
    }
    $geo = [Text.RegularExpressions.Regex]::Replace($geo, $pattern, '')
}
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($geoTarget, $geo, $utf8)

$ffmpeg = Get-Command ffmpeg -ErrorAction Stop
& $ffmpeg.Source -hide_banner -loglevel error -y -i (Join-Path $flamingoRoot 'idle.mp3') -map_metadata -1 -c:a libvorbis -q:a 4 $soundTarget
if ($LASTEXITCODE -ne 0) {
    throw 'ffmpeg failed to convert the supplied Flamingo ambient sound to OGG'
}

[IO.File]::WriteAllText($itemTarget, "{`n  `"parent`": `"minecraft:item/template_spawn_egg`"`n}`n", $utf8)
[IO.File]::WriteAllText($lootTarget, "{`n  `"type`": `"minecraft:entity`",`n  `"pools`": []`n}`n", $utf8)

# Remove the superseded plushie-based renderer assets from the first pass.
$obsolete = @(
    (Join-Path $assets 'models/block/new_assets/flamingo.json'),
    (Join-Path $assets 'textures/block/new_assets/flamingo.png')
)
foreach ($path in $obsolete) {
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        Remove-Item -LiteralPath $path -Force
    }
}

Write-Output 'Animated Pink/Rose/White Flamingo entity assets imported successfully.'
