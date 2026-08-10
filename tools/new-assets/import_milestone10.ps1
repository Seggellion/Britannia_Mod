param(
    [Parameter(Mandatory = $true)][string]$RawRoot,
    [Parameter(Mandatory = $true)][string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

$sourceRoot = Join-Path $RawRoot 'models to import/moongate'
$assetRoot = Join-Path $ProjectRoot 'src/main/resources/assets/britannia_mod'
$textureRoot = Join-Path $assetRoot 'textures/block/new_assets/moongate'
$modelTarget = Join-Path $assetRoot 'models/block/moongate_block.json'
$itemTarget = Join-Path $assetRoot 'models/item/moongate_block.json'

$expectedHashes = [ordered]@{
    'portal.bbmodel' = 'a22ccc954d3f7b8a36670c017da4f506d0bb18b326700f416b1e8931d0dd48a7'
    'portal_texture.png' = 'ea03c36402126c06cc885aaae1366af5b6cabe576159c6c05eca90bed607b601'
    'portal_texture2.png' = 'f50bc0d9e5aeb2bbbd7334f7bcc3c20518827747769d92801aa820af085686cf'
    'portal_texture3.png' = '312130cf620e058d64fc24e2a95b6384b55fa6a42b7dcfd94761762227154418'
    'portal_texture4.png' = '5a064231e5783493707c1ae7cbb94ec59c1249e7d9cf21b25343f04f3366ad3a'
    'portal_texture5.png' = '88b822f1d36b6109b0de50d587d65fa01a64608aa18e65cc1cc3d9f905f5f9e0'
    'portal_texture6.png' = 'd9590e2b95557aa3007dd5bc4ad45c942f7d2d6628297c063ca0dbf7a2dc5ac5'
}

foreach ($entry in $expectedHashes.GetEnumerator()) {
    $source = Join-Path $sourceRoot $entry.Key
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing Milestone 10 source: $source" }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash.ToLowerInvariant()
    if ($actual -ne $entry.Value) { throw "Unexpected SHA-256 for $($entry.Key): $actual" }
}

$sourceModel = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $sourceRoot 'portal.bbmodel') | ConvertFrom-Json
$fromX = @($sourceModel.elements | ForEach-Object { [double]$_.from[0] })
$fromY = @($sourceModel.elements | ForEach-Object { [double]$_.from[1] })
$fromZ = @($sourceModel.elements | ForEach-Object { [double]$_.from[2] })
$toX = @($sourceModel.elements | ForEach-Object { [double]$_.to[0] })
$toY = @($sourceModel.elements | ForEach-Object { [double]$_.to[1] })
$toZ = @($sourceModel.elements | ForEach-Object { [double]$_.to[2] })
$bounds = @(
    ($fromX | Measure-Object -Minimum).Minimum,
    ($fromY | Measure-Object -Minimum).Minimum,
    ($fromZ | Measure-Object -Minimum).Minimum,
    ($toX | Measure-Object -Maximum).Maximum,
    ($toY | Measure-Object -Maximum).Maximum,
    ($toZ | Measure-Object -Maximum).Maximum
)
if (($bounds -join ',') -ne '-16,-1.5,-16,16,41.5,16') {
    throw "Unexpected source bounds: $($bounds -join ',')"
}

[IO.Directory]::CreateDirectory($textureRoot) | Out-Null
foreach ($index in 1..6) {
    $suffix = if ($index -eq 1) { '' } else { [string]$index }
    $name = "portal_texture$suffix.png"
    Copy-Item -Force -LiteralPath (Join-Path $sourceRoot $name) -Destination (Join-Path $textureRoot $name)
}

function Write-Json([object]$value, [string]$path, [int]$depth = 100) {
    [IO.Directory]::CreateDirectory((Split-Path -Parent $path)) | Out-Null
    $json = $value | ConvertTo-Json -Depth $depth
    [IO.File]::WriteAllText($path, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

$animation = [ordered]@{ animation = [ordered]@{ frametime = 1 } }
foreach ($name in @('portal_texture.png', 'portal_texture3.png', 'portal_texture4.png')) {
    Write-Json $animation (Join-Path $textureRoot "$name.mcmeta")
}

function Face([double[]]$uv, [string]$texture) {
    return [ordered]@{ uv = $uv; texture = $texture; tintindex = 0 }
}
function Portal-Element([double[]]$from, [double[]]$to, [double[]]$uv, [string]$texture) {
    return [ordered]@{
        from = $from; to = $to; shade = $false
        faces = [ordered]@{ north = Face $uv $texture; south = Face @($uv[2], $uv[1], $uv[0], $uv[3]) $texture }
    }
}

# The purchased source spans 32x43x32 voxels. Re-author it into one logical
# 16x32x16 block: x/z are halved, y is translated by +1.5 then scaled by 32/43.
$model = [ordered]@{
    parent = 'minecraft:block/block'
    ambientocclusion = $false
    render_type = 'minecraft:translucent'
    textures = [ordered]@{
        particle = 'britannia_mod:block/new_assets/moongate/portal_texture2'
        portal = 'britannia_mod:block/new_assets/moongate/portal_texture'
        core = 'britannia_mod:block/new_assets/moongate/portal_texture2'
        outline = 'britannia_mod:block/new_assets/moongate/portal_texture3'
        outer = 'britannia_mod:block/new_assets/moongate/portal_texture4'
        floor = 'britannia_mod:block/new_assets/moongate/portal_texture5'
        floor_glow = 'britannia_mod:block/new_assets/moongate/portal_texture6'
    }
    elements = @(
        (Portal-Element @(3.5, 3.34884, 7.5) @(12.5, 28.65116, 8.5) @(0, 0, 2.25, 4.25) '#portal'),
        (Portal-Element @(3.25, 2.97674, 7.99) @(12.75, 29.02326, 8.01) @(0, 0, 16, 16) '#core'),
        ([ordered]@{ from = @(3.5, 3.34884, 6.99); to = @(12.5, 28.65116, 7.01); shade = $false; faces = [ordered]@{ north = Face @(0, 0, 2.25, 4.25) '#outline' } }),
        ([ordered]@{ from = @(3.5, 3.34884, 8.99); to = @(12.5, 28.65116, 9.01); shade = $false; faces = [ordered]@{ south = Face @(2.25, 0, 0, 4.25) '#outline' } }),
        (Portal-Element @(1.25, 0, 7.98) @(14.75, 32, 8.02) @(0, 0, 6.75, 10.75) '#outer'),
        ([ordered]@{ from = @(0, 1.11628, 0); to = @(16, 1.12628, 16); shade = $false; faces = [ordered]@{ up = Face @(16, 16, 0, 0) '#floor' } }),
        ([ordered]@{ from = @(0, 1.48837, 0); to = @(16, 1.49837, 16); shade = $false; faces = [ordered]@{ up = Face @(16, 16, 0, 0) '#floor_glow' } })
    )
}
Write-Json $model $modelTarget

$item = [ordered]@{
    parent = 'britannia_mod:block/moongate_block'
    display = [ordered]@{
        gui = [ordered]@{ rotation = @(30, 225, 0); translation = @(0, -1, 0); scale = @(0.4, 0.4, 0.4) }
        ground = [ordered]@{ translation = @(0, 2, 0); scale = @(0.25, 0.25, 0.25) }
        fixed = [ordered]@{ translation = @(0, -2, 0); scale = @(0.35, 0.35, 0.35) }
        thirdperson_righthand = [ordered]@{ rotation = @(75, 45, 0); translation = @(0, 2.5, 0); scale = @(0.375, 0.375, 0.375) }
        firstperson_righthand = [ordered]@{ rotation = @(0, 45, 0); translation = @(0, 0, 0); scale = @(0.4, 0.4, 0.4) }
    }
}
Write-Json $item $itemTarget

Write-Output 'Milestone 10 moongate model and six temporary source textures imported successfully.'
