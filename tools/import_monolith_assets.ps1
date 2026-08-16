param(
    [string]$SourceDirectory = 'C:\projects\britannia\raw fiels\monolith'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $projectRoot 'src\main\resources\assets\britannia_mod'
$textureDirectory = Join-Path $assetRoot 'textures\block\monolith'
$geometryDirectory = Join-Path $assetRoot 'geo'

$imports = @(
    [pscustomobject]@{
        Number = 1
        SourceModelHash = '5C052C21522C64BE9C5CCCA2E5D3843346769D36A37A90D7AEC479F1FBF58A4F'
        SourceGeometryHash = '73EAF375D88A590F37D8F8759C15763189CC5848443C9C461E0443F68D13ED62'
        ExpectedTextures = @('sarsen_stone_1.png', 'sarsen_stone_5.png')
        RuntimeGeometry = 'monolith_diagnostic.geo.json'
        RuntimeTexture = 'diagnostic_stone.png'
        Identifier = 'geometry.britannia_mod.monolith_diagnostic'
    },
    [pscustomobject]@{
        Number = 2
        SourceModelHash = '2F7B1ED85AEB27DA1F47D4BD693AC4C04E1C7C96AE975A83347AB1AB435CC392'
        SourceGeometryHash = 'A56B9EADB664E59B6F3257D9F16BAFCF519DDEE5A078570687921BBCE2F0072C'
        ExpectedTextures = @('sarsen_stone_1.png', 'sarsen_stone_6.png')
        RuntimeGeometry = 'monolith_diagnostic_alternate.geo.json'
        RuntimeTexture = 'diagnostic_alternate_stone.png'
        Identifier = 'geometry.britannia_mod.monolith_diagnostic_alternate'
    },
    [pscustomobject]@{
        Number = 3
        SourceModelHash = '3EA5D3D6B07A4EE93EBAD5A9C313C8043F556F331104E9B7BEE74766BF5915A1'
        SourceGeometryHash = 'F0CEBC3A9D7A7BBD15CD07A1738BB5A3D89614D10A13C47A8CC4CB8CF0403617'
        ExpectedTextures = @('sarsen_stone_4.png', 'sarsen_stone_7.png')
        RuntimeGeometry = 'monolith_diagnostic_crystalline.geo.json'
        RuntimeTexture = 'diagnostic_crystalline_stone.png'
        Identifier = 'geometry.britannia_mod.monolith_diagnostic_crystalline'
    }
)

function Assert-Hash([string]$Path, [string]$Expected) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required source file is missing: $Path"
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual -ne $Expected) {
        throw "Unexpected SHA-256 for $Path. Expected $Expected but found $actual"
    }
}

function Test-NearlyEqual([double]$Left, [double]$Right) {
    return [Math]::Abs($Left - $Right) -lt 0.0001
}

function Test-Triplet([object[]]$Left, [object[]]$Right) {
    for ($index = 0; $index -lt 3; $index++) {
        if (-not (Test-NearlyEqual ([double]$Left[$index]) ([double]$Right[$index]))) {
            return $false
        }
    }
    return $true
}

function Get-EmbeddedBitmap([object]$Texture) {
    $source = [string]$Texture.source
    if (-not $source.StartsWith('data:image/png;base64,')) {
        throw "Texture $($Texture.name) is not an embedded PNG"
    }
    $bytes = [Convert]::FromBase64String($source.Split(',', 2)[1])
    $stream = [IO.MemoryStream]::new($bytes)
    try {
        $temporary = [Drawing.Bitmap]::new($stream)
        try {
            return [Drawing.Bitmap]::new($temporary)
        }
        finally {
            $temporary.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Assert-PixelEqual([Drawing.Bitmap]$Left, [Drawing.Bitmap]$Right, [string]$Label) {
    if ($Left.Width -ne $Right.Width -or $Left.Height -ne $Right.Height) {
        throw "$Label dimensions differ: $($Left.Width)x$($Left.Height) versus $($Right.Width)x$($Right.Height)"
    }
    for ($y = 0; $y -lt $Left.Height; $y++) {
        for ($x = 0; $x -lt $Left.Width; $x++) {
            if ($Left.GetPixel($x, $y).ToArgb() -ne $Right.GetPixel($x, $y).ToArgb()) {
                throw "$Label pixels differ at ($x,$y)"
            }
        }
    }
}

function Write-TextureAtlas([object]$Blockbench, [string[]]$ExpectedTextures, [string]$OutputPath) {
    if ($Blockbench.textures.Count -ne 2) {
        throw "Expected exactly two Blockbench textures, found $($Blockbench.textures.Count)"
    }

    $sourceBitmaps = @()
    try {
        for ($index = 0; $index -lt 2; $index++) {
            $texture = $Blockbench.textures[$index]
            if ([string]$texture.name -ne $ExpectedTextures[$index]) {
                throw "Texture slot $index is $($texture.name); expected $($ExpectedTextures[$index])"
            }

            $suppliedPath = Join-Path $textureDirectory $ExpectedTextures[$index]
            if (-not (Test-Path -LiteralPath $suppliedPath -PathType Leaf)) {
                throw "Supplied monolith texture is missing: $suppliedPath"
            }
            $embedded = Get-EmbeddedBitmap $texture
            $supplied = [Drawing.Bitmap]::new([string]$suppliedPath)
            try {
                Assert-PixelEqual $embedded $supplied "Embedded and supplied $($texture.name)"
            }
            finally {
                $supplied.Dispose()
            }
            if ($embedded.Width -ne 128 -or $embedded.Height -ne 128) {
                $embedded.Dispose()
                throw "Expected a 128x128 texture for $($texture.name)"
            }
            $sourceBitmaps += $embedded
        }

        $atlas = [Drawing.Bitmap]::new(256, 128, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [Drawing.Graphics]::FromImage($atlas)
            try {
                $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.DrawImageUnscaled($sourceBitmaps[0], 0, 0)
                $graphics.DrawImageUnscaled($sourceBitmaps[1], 128, 0)
            }
            finally {
                $graphics.Dispose()
            }
            $atlas.Save($OutputPath, [Drawing.Imaging.ImageFormat]::Png)
        }
        finally {
            $atlas.Dispose()
        }
    }
    finally {
        foreach ($bitmap in $sourceBitmaps) {
            $bitmap.Dispose()
        }
    }
}

function Get-ElementMaterialMap([object]$Blockbench) {
    $records = @()
    foreach ($element in $Blockbench.elements) {
        $textureSlots = @($element.faces.psobject.Properties.Value |
                ForEach-Object { [int]$_.texture } | Sort-Object -Unique)
        if ($textureSlots.Count -ne 1 -or $textureSlots[0] -notin 0, 1) {
            throw "Element $($element.name) must use one of the two textures on every face"
        }

        # Blockbench's GeckoLib export mirrors X around 8 and translates Z by -8.
        $origin = @(
            (8.0 - [double]$element.to[0])
            ([double]$element.from[1])
            ([double]$element.from[2] - 8.0)
        )
        $size = @(
            ([double]$element.to[0] - [double]$element.from[0])
            ([double]$element.to[1] - [double]$element.from[1])
            ([double]$element.to[2] - [double]$element.from[2])
        )
        $records += [pscustomobject]@{
            Origin = $origin
            Size = $size
            TextureSlot = $textureSlots[0]
            Matched = $false
        }
    }
    return $records
}

function Convert-Coordinate([double]$Value, [double]$SourceMin, [double]$Scale, [double]$TargetMin) {
    $converted = $TargetMin + (($Value - $SourceMin) * $Scale)
    $rounded = [Math]::Round($converted, 6)
    if ([Math]::Abs($rounded - [Math]::Round($rounded)) -lt 0.000001) {
        return [int][Math]::Round($rounded)
    }
    return $rounded
}

function Normalize-GeometryToCollisionEnvelope([object]$Geometry) {
    # The renderer adds (0.5, +1, 0.5), mirrors Bedrock X, and rotates about the
    # anchor. These bounds therefore map exactly to the occupied 3x3x2 cells:
    # NORTH-relative world bounds x=-2..1, y=0..3, z=0..2.
    $targetMin = @(-8.0, -16.0, -8.0)
    $targetMax = @(40.0, 32.0, 24.0)
    $sourceMin = @([double]::PositiveInfinity, [double]::PositiveInfinity, [double]::PositiveInfinity)
    $sourceMax = @([double]::NegativeInfinity, [double]::NegativeInfinity, [double]::NegativeInfinity)

    foreach ($cube in $Geometry.bones[0].cubes) {
        for ($axis = 0; $axis -lt 3; $axis++) {
            $start = [double]$cube.origin[$axis]
            $end = $start + [double]$cube.size[$axis]
            $sourceMin[$axis] = [Math]::Min($sourceMin[$axis], $start)
            $sourceMax[$axis] = [Math]::Max($sourceMax[$axis], $end)
        }
    }

    $scale = @(0.0, 0.0, 0.0)
    for ($axis = 0; $axis -lt 3; $axis++) {
        $sourceLength = $sourceMax[$axis] - $sourceMin[$axis]
        if ($sourceLength -le 0.0) {
            throw "Monolith geometry has an empty axis $axis"
        }
        $scale[$axis] = ($targetMax[$axis] - $targetMin[$axis]) / $sourceLength
    }

    foreach ($cube in $Geometry.bones[0].cubes) {
        for ($axis = 0; $axis -lt 3; $axis++) {
            $cube.origin[$axis] = Convert-Coordinate ([double]$cube.origin[$axis]) `
                    $sourceMin[$axis] $scale[$axis] $targetMin[$axis]
            $cube.size[$axis] = [Math]::Round(([double]$cube.size[$axis]) * $scale[$axis], 6)
            if ($null -ne $cube.PSObject.Properties['pivot']) {
                $cube.pivot[$axis] = Convert-Coordinate ([double]$cube.pivot[$axis]) `
                        $sourceMin[$axis] $scale[$axis] $targetMin[$axis]
            }
        }
    }

    # The animation file is static, so use one canonical root pivot for every variant.
    $Geometry.bones[0].pivot = @(0, -16, 0)
}

function Write-RuntimeGeometry(
        [object]$GeometryDocument,
        [object]$Blockbench,
        [string]$Identifier,
        [string]$OutputPath) {
    if ($GeometryDocument.'minecraft:geometry'.Count -ne 1) {
        throw 'Expected exactly one geometry in the source export'
    }
    $geometry = $GeometryDocument.'minecraft:geometry'[0]
    if ($geometry.bones.Count -ne 1) {
        throw 'Expected exactly one source bone'
    }

    $geometry.description.identifier = $Identifier
    # Each authored 16x16 UV space occupies one half of the 256x128 atlas.
    $geometry.description.texture_width = 32
    $geometry.description.texture_height = 16
    $geometry.description.visible_bounds_width = 4
    $geometry.description.visible_bounds_height = 4
    $geometry.description.visible_bounds_offset = @(1, 0, 0.5)
    $geometry.bones[0].name = 'monolith'

    $materials = @(Get-ElementMaterialMap $Blockbench)
    foreach ($cube in $geometry.bones[0].cubes) {
        $matches = @($materials | Where-Object {
                -not $_.Matched -and
                (Test-Triplet @($_.Origin) @($cube.origin)) -and
                (Test-Triplet @($_.Size) @($cube.size))
            })
        if ($matches.Count -ne 1) {
            throw "Could not uniquely map exported cube origin=$($cube.origin -join ',') size=$($cube.size -join ',')"
        }
        $material = $matches[0]
        $material.Matched = $true
        if ($material.TextureSlot -eq 1) {
            foreach ($face in $cube.uv.psobject.Properties.Value) {
                $face.uv[0] = [double]$face.uv[0] + 16.0
            }
        }
    }
    if (@($materials | Where-Object { -not $_.Matched }).Count -ne 0) {
        throw 'One or more Blockbench elements were not present in the exported geometry'
    }

    Normalize-GeometryToCollisionEnvelope $geometry

    $json = $GeometryDocument | ConvertTo-Json -Depth 50
    [IO.File]::WriteAllText($OutputPath, $json + "`n", [Text.UTF8Encoding]::new($false))
}

foreach ($import in $imports) {
    $sourceModel = Join-Path $SourceDirectory "Monolith_$($import.Number).bbmodel"
    $sourceGeometry = Join-Path $SourceDirectory "Monolith_$($import.Number).geo.json"
    Assert-Hash $sourceModel $import.SourceModelHash
    Assert-Hash $sourceGeometry $import.SourceGeometryHash

    $blockbench = Get-Content -Raw -LiteralPath $sourceModel | ConvertFrom-Json
    $geometry = Get-Content -Raw -LiteralPath $sourceGeometry | ConvertFrom-Json
    $runtimeTexture = Join-Path $textureDirectory $import.RuntimeTexture
    $runtimeGeometry = Join-Path $geometryDirectory $import.RuntimeGeometry

    Write-TextureAtlas $blockbench $import.ExpectedTextures $runtimeTexture
    Write-RuntimeGeometry $geometry $blockbench $import.Identifier $runtimeGeometry

    Write-Output "Imported Monolith_$($import.Number) -> $($import.RuntimeGeometry), $($import.RuntimeTexture)"
}
