param(
    [Parameter(Mandatory = $true)]
    [string]$RawRoot,

    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

$sourceDirectory = Join-Path $RawRoot 'white ibis'
$sourceModel = Join-Path $sourceDirectory 'white ibis.bbmodel'
$sourceTexture = Join-Path $sourceDirectory 'texture.png'
$assetRoot = Join-Path $ProjectRoot 'src/main/resources/assets/britannia_mod'
$geoTarget = Join-Path $assetRoot 'geo/ibis.geo.json'
$animationTarget = Join-Path $assetRoot 'animations/ibis.animation.json'
$whiteTarget = Join-Path $assetRoot 'textures/entity/ibis_white.png'
$scarletTarget = Join-Path $assetRoot 'textures/entity/ibis_scarlet.png'

foreach ($required in @($sourceModel, $sourceTexture)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing Milestone 6 ibis source: $required"
    }
}

foreach ($directory in @(
    (Split-Path -Parent $geoTarget),
    (Split-Path -Parent $animationTarget),
    (Split-Path -Parent $whiteTarget)
)) {
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
}

$source = Get-Content -Raw -Encoding UTF8 -LiteralPath $sourceModel | ConvertFrom-Json
$groups = @{}
foreach ($group in $source.groups) {
    $groups[[string]$group.uuid] = $group
}
$elements = @{}
foreach ($element in $source.elements) {
    $elements[[string]$element.uuid] = $element
}

function Convert-CoordinateVector([object[]]$values, [string]$kind) {
    $x = [double]$values[0]
    $y = [double]$values[1]
    $z = [double]$values[2]
    switch ($kind) {
        'pivot' { return @(-$x, $y, $z) }
        'rotation' { return @($x, -$y, -$z) }
        'position' { return @(-$x, $y, $z) }
        default { return @($x, $y, $z) }
    }
}

function Has-NonZero([object[]]$values) {
    foreach ($value in $values) {
        if ([math]::Abs([double]$value) -gt 0.000001D) {
            return $true
        }
    }
    return $false
}

$bones = [System.Collections.Generic.List[object]]::new()
function Add-Bone([object]$node, [string]$parentName) {
    $group = $groups[[string]$node.uuid]
    if ($null -eq $group) {
        throw "Outliner references unknown ibis group $($node.uuid)"
    }

    $bone = [ordered]@{
        name = [string]$group.name
        pivot = Convert-CoordinateVector @($group.origin) 'pivot'
    }
    if (-not [string]::IsNullOrWhiteSpace($parentName)) {
        $bone.parent = $parentName
    }
    if (Has-NonZero @($group.rotation)) {
        $bone.rotation = Convert-CoordinateVector @($group.rotation) 'rotation'
    }

    $cubes = [System.Collections.Generic.List[object]]::new()
    foreach ($child in @($node.children)) {
        if ($child -isnot [string]) {
            continue
        }
        $elementId = $child.Trim()
        if ([string]::IsNullOrWhiteSpace($elementId)) {
            continue
        }
        $element = $elements[$elementId]
        if ($null -eq $element -or $element.export -eq $false) {
            continue
        }

        $from = @($element.from)
        $to = @($element.to)
        $uvOffset = if ($null -eq $element.uv_offset) { @(0.0D, 0.0D) } else { @($element.uv_offset) }
        $cube = [ordered]@{
            origin = @((-1.0D * [double]$to[0]), ([double]$from[1]), ([double]$from[2]))
            size = @(
                ([double]$to[0] - [double]$from[0]),
                ([double]$to[1] - [double]$from[1]),
                ([double]$to[2] - [double]$from[2])
            )
            uv = @([double]$uvOffset[0], [double]$uvOffset[1])
        }
        if (Has-NonZero @($element.rotation)) {
            $cube.pivot = Convert-CoordinateVector @($element.origin) 'pivot'
            $cube.rotation = Convert-CoordinateVector @($element.rotation) 'rotation'
        }
        if ([math]::Abs([double]$element.inflate) -gt 0.000001D) {
            $cube.inflate = [double]$element.inflate
        }
        if ($element.mirror_uv -eq $true) {
            $cube.mirror = $true
        }
        $cubes.Add($cube)
    }
    if ($cubes.Count -gt 0) {
        $bone.cubes = $cubes
    }
    $bones.Add($bone)

    foreach ($child in @($node.children)) {
        if ($child -is [string]) {
            continue
        }
        Add-Bone $child ([string]$group.name)
    }
}

foreach ($root in @($source.outliner)) {
    Add-Bone $root ''
}

$geometry = [ordered]@{
    format_version = '1.12.0'
    'minecraft:geometry' = @(
        [ordered]@{
            description = [ordered]@{
                identifier = 'geometry.ibis'
                texture_width = [int]$source.resolution.width
                texture_height = [int]$source.resolution.height
                visible_bounds_width = 3
                visible_bounds_height = 2.5
                visible_bounds_offset = @(0, 0.75, -0.4)
            }
            bones = $bones
        }
    )
}

function Convert-AnimationValue([object]$value) {
    $number = 0.0D
    if ([double]::TryParse(
            [string]$value,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$number)) {
        return $number
    }
    return [string]$value
}

function Convert-AnimationVector([object]$dataPoint, [string]$channel) {
    $values = @(
        (Convert-AnimationValue $dataPoint.x),
        (Convert-AnimationValue $dataPoint.y),
        (Convert-AnimationValue $dataPoint.z)
    )
    if ($values | Where-Object { $_ -is [string] }) {
        return $values
    }
    switch ($channel) {
        'rotation' { return @($values[0], -[double]$values[1], -[double]$values[2]) }
        'position' { return @(-[double]$values[0], $values[1], $values[2]) }
        default { return $values }
    }
}

$convertedAnimations = [ordered]@{}
foreach ($animation in $source.animations) {
    $converted = [ordered]@{
        loop = ([string]$animation.loop -eq 'loop')
        animation_length = [double]$animation.length
    }
    $boneAnimations = [ordered]@{}
    foreach ($animatorProperty in $animation.animators.PSObject.Properties) {
        $animator = $animatorProperty.Value
        if ([string]$animator.type -ne 'bone') {
            continue
        }
        $group = $groups[[string]$animatorProperty.Name]
        $boneName = if ($null -ne $group) { [string]$group.name } else { [string]$animator.name }
        $channels = [ordered]@{}
        foreach ($keyframe in @($animator.keyframes)) {
            $channel = [string]$keyframe.channel
            if ($channel -notin @('rotation', 'position', 'scale')) {
                continue
            }
            if (-not $channels.Contains($channel)) {
                $channels[$channel] = [ordered]@{}
            }
            $time = ([double]$keyframe.time).ToString('0.#####', [System.Globalization.CultureInfo]::InvariantCulture)
            $channels[$channel][$time] = [ordered]@{
                vector = Convert-AnimationVector (@($keyframe.data_points)[0]) $channel
            }
        }
        if ($channels.Count -gt 0) {
            $boneAnimations[$boneName] = $channels
        }
    }
    $converted.bones = $boneAnimations
    $convertedAnimations["animation.model.$($animation.name)"] = $converted
}

$animationJson = [ordered]@{
    format_version = '1.8.0'
    animations = $convertedAnimations
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText(
    $geoTarget,
    ($geometry | ConvertTo-Json -Depth 100),
    $utf8NoBom)
[System.IO.File]::WriteAllText(
    $animationTarget,
    ($animationJson | ConvertTo-Json -Depth 100),
    $utf8NoBom)
[System.IO.File]::Copy($sourceTexture, $whiteTarget, $true)

# The image-edit pass supplied the scarlet palette. Apply it deterministically to connected,
# neutral plumage pixels so the source UV coordinates and alpha bytes remain exact. Small neutral
# components are retained, preserving eye highlights; saturated beak/leg details never enter the mask.
Add-Type -AssemblyName System.Drawing
$white = [System.Drawing.Bitmap]::new($sourceTexture)
$scarlet = [System.Drawing.Bitmap]::new($white.Width, $white.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$candidate = [bool[]]::new($white.Width * $white.Height)
for ($y = 0; $y -lt $white.Height; $y++) {
    for ($x = 0; $x -lt $white.Width; $x++) {
        $color = $white.GetPixel($x, $y)
        $scarlet.SetPixel($x, $y, $color)
        if ($color.A -eq 0) {
            continue
        }
        $maximum = [math]::Max($color.R, [math]::Max($color.G, $color.B))
        $minimum = [math]::Min($color.R, [math]::Min($color.G, $color.B))
        $luminance = [int](0.2126D * $color.R + 0.7152D * $color.G + 0.0722D * $color.B)
        if (($maximum - $minimum) -le 38 -and $luminance -ge 45) {
            $candidate[$y * $white.Width + $x] = $true
        }
    }
}

$visited = [bool[]]::new($candidate.Length)
$directions = @(@(-1, 0), @(1, 0), @(0, -1), @(0, 1))
for ($start = 0; $start -lt $candidate.Length; $start++) {
    if (-not $candidate[$start] -or $visited[$start]) {
        continue
    }
    $queue = [System.Collections.Generic.Queue[int]]::new()
    $component = [System.Collections.Generic.List[int]]::new()
    $queue.Enqueue($start)
    $visited[$start] = $true
    while ($queue.Count -gt 0) {
        $index = $queue.Dequeue()
        $component.Add($index)
        $cx = $index % $white.Width
        $cy = [math]::Floor($index / $white.Width)
        foreach ($direction in $directions) {
            $nx = $cx + $direction[0]
            $ny = $cy + $direction[1]
            if ($nx -lt 0 -or $nx -ge $white.Width -or $ny -lt 0 -or $ny -ge $white.Height) {
                continue
            }
            $neighbor = [int]($ny * $white.Width + $nx)
            if ($candidate[$neighbor] -and -not $visited[$neighbor]) {
                $visited[$neighbor] = $true
                $queue.Enqueue($neighbor)
            }
        }
    }
    if ($component.Count -lt 8) {
        continue
    }
    foreach ($index in $component) {
        $x = $index % $white.Width
        $y = [math]::Floor($index / $white.Width)
        $color = $white.GetPixel($x, $y)
        $luminance = [int](0.2126D * $color.R + 0.7152D * $color.G + 0.0722D * $color.B)
        $red = [math]::Min(255, [int](45 + 0.82D * $luminance))
        $green = [math]::Min(255, [int](10 + 0.20D * $luminance))
        $blue = [math]::Min(255, [int](12 + 0.17D * $luminance))
        $scarlet.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($color.A, $red, $green, $blue))
    }
}

$scarlet.Save($scarletTarget, [System.Drawing.Imaging.ImageFormat]::Png)
$scarlet.Dispose()
$white.Dispose()

Write-Output 'Milestone 6 ibis geometry, animations, and white/scarlet textures imported successfully.'
