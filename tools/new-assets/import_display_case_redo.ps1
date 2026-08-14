param(
    [string]$SourceDirectory = 'C:\projects\britannia\raw fiels\display_case'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$modelDirectory = Join-Path $repoRoot 'src\main\resources\assets\britannia_mod\models\block\new_assets'
$texturePath = Join-Path $repoRoot 'src\main\resources\assets\britannia_mod\textures\block\new_assets\display_case.png'

$connectedPath = Join-Path $SourceDirectory 'display_case_redo.json'
$independentPath = Join-Path $SourceDirectory 'display_case_independant_redo.json'
$cornerPath = Join-Path $SourceDirectory 'display_case_corner_redo.bbmodel'

foreach ($source in @($connectedPath, $independentPath, $cornerPath)) {
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing authoritative display-case source: $source"
    }
}

function Remove-CoplanarDuplicateBounds([object]$model) {
    $seen = @{}
    $retained = [Collections.Generic.List[object]]::new()
    foreach ($element in @($model.elements)) {
        $signature = (@($element.from) -join ',') + '|' + (@($element.to) -join ',')
        if (-not $seen.ContainsKey($signature)) {
            $seen[$signature] = $true
            $retained.Add($element)
        }
    }
    $model.elements = @($retained)
    return $model
}

function Write-Model([object]$model, [string]$name) {
    $destination = Join-Path $modelDirectory $name
    $json = $model | ConvertTo-Json -Compress -Depth 100
    [IO.File]::WriteAllText($destination, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

function Copy-Elements([object[]]$elements) {
    return @($elements | ForEach-Object {
        ConvertFrom-Json -InputObject ($_ | ConvertTo-Json -Compress -Depth 100)
    })
}

function Convert-FrameHeight([object[]]$elements, [switch]$UpperCell) {
    $converted = Copy-Elements $elements
    foreach ($element in $converted) {
        foreach ($bound in @('from', 'to')) {
            $scaledY = 16.0D + (([double]$element.$bound[1] - 16.0D) * 0.5D)
            $element.$bound[1] = if ($UpperCell) { $scaledY - 16.0D } else { $scaledY }
        }
        if ($null -ne $element.rotation -and $null -ne $element.rotation.origin) {
            $scaledOriginY = 16.0D + (([double]$element.rotation.origin[1] - 16.0D) * 0.5D)
            $element.rotation.origin[1] = if ($UpperCell) { $scaledOriginY - 16.0D } else { $scaledOriginY }
        }
    }
    return @($converted)
}

function New-DisplayCaseModel([object[]]$elements) {
    foreach ($element in $elements) {
        # The owner texture carries its own tonal detail. Minecraft's directional/AO shading made
        # interior rails render nearly black when cases enclosed one another in a grid.
        $element | Add-Member -NotePropertyName shade -NotePropertyValue $false -Force
    }
    return [pscustomobject][ordered]@{
        format_version = '1.21.11'
        credit = 'Made with Blockbench'
        ambientocclusion = $false
        textures = [ordered]@{
            '0' = 'britannia_mod:block/new_assets/display_case'
            particle = 'britannia_mod:block/new_assets/display_case'
        }
        elements = @($elements)
    }
}

# The connected source contains one exact-coincident post. Keeping the first cuboid preserves its
# authored UVs while preventing opaque coplanar faces from z-fighting at runtime. Its exact pieces
# are then recombined into open end/straight/tee frames. A four-way interior needs only the base.
$connected = Get-Content -Raw -LiteralPath $connectedPath | ConvertFrom-Json
$connected = Remove-CoplanarDuplicateBounds $connected
$independent = Get-Content -Raw -LiteralPath $independentPath | ConvertFrom-Json

$independentItemElements = @(Copy-Elements @($independent.elements[0])) + @(
    Convert-FrameHeight @($independent.elements | Select-Object -Skip 1))
Write-Model (New-DisplayCaseModel $independentItemElements) 'display_case_independent.json'
Write-Model (New-DisplayCaseModel @($independent.elements[0])) 'display_case_base_independent.json'
Write-Model (New-DisplayCaseModel @(
    Convert-FrameHeight @($independent.elements | Select-Object -Skip 1) -UpperCell)) 'display_case_frame_independent.json'
Write-Model (New-DisplayCaseModel @($connected.elements[0])) 'display_case_base_connected.json'

# Connected element order after exact-bound deduplication:
# 0 base; 1/10 west/east lower rails; 11/12 north/south lower rails;
# 6/5 north/south top rails; 8/7 west/east top rails; 2/3/9/4 corner posts.
$endIndices = @(1, 2, 3, 4, 6, 7, 8, 9, 10, 11) # canonical opening/connection south
$straightIndices = @(1, 2, 3, 4, 7, 8, 9, 10) # canonical openings north/south
$teeIndices = @(1, 2, 3, 8) # canonical exterior wall west; other sides open
Write-Model (New-DisplayCaseModel @(
    Convert-FrameHeight @($endIndices | ForEach-Object { $connected.elements[$_] }) -UpperCell)) 'display_case_frame_end.json'
Write-Model (New-DisplayCaseModel @(
    Convert-FrameHeight @($straightIndices | ForEach-Object { $connected.elements[$_] }) -UpperCell)) 'display_case_frame_straight.json'
Write-Model (New-DisplayCaseModel @(
    Convert-FrameHeight @($teeIndices | ForEach-Object { $connected.elements[$_] }) -UpperCell)) 'display_case_frame_tee.json'

# Export the authoritative Blockbench corner directly from its element/outliner structure. The
# source uses no groups, pivots, or non-zero rotations, but outliner order remains authoritative.
$cornerSource = Get-Content -Raw -LiteralPath $cornerPath | ConvertFrom-Json
$byUuid = @{}
foreach ($element in @($cornerSource.elements)) {
    if ($element.export -ne $false) { $byUuid[$element.uuid] = $element }
}

$cornerElements = [Collections.Generic.List[object]]::new()
foreach ($uuid in @($cornerSource.outliner)) {
    if ($uuid -isnot [string] -or -not $byUuid.ContainsKey($uuid)) { continue }
    $sourceElement = $byUuid[$uuid]
    $faces = [ordered]@{}
    foreach ($direction in @('north', 'east', 'south', 'west', 'up', 'down')) {
        $sourceFace = $sourceElement.faces.$direction
        if ($null -eq $sourceFace -or $sourceFace.enabled -eq $false) { continue }
        $face = [ordered]@{
            uv = @($sourceFace.uv)
            texture = '#' + $sourceFace.texture
        }
        if ($null -ne $sourceFace.rotation) { $face.rotation = $sourceFace.rotation }
        if ($sourceFace.cullface) { $face.cullface = $sourceFace.cullface }
        $faces[$direction] = $face
    }
    $cornerElements.Add([ordered]@{
        from = @($sourceElement.from)
        to = @($sourceElement.to)
        faces = $faces
    })
}

$corner = New-DisplayCaseModel @($cornerElements)
$corner = Remove-CoplanarDuplicateBounds $corner
Write-Model (New-DisplayCaseModel @(
    Convert-FrameHeight @($corner.elements | Select-Object -Skip 1) -UpperCell)) 'display_case_frame_corner.json'

$textureSource = [string]$cornerSource.textures[0].source
if (-not $textureSource.StartsWith('data:image/png;base64,')) {
    throw 'Authoritative corner texture is not an embedded PNG.'
}
$textureBytes = [Convert]::FromBase64String(($textureSource -split ',', 2)[1])
[IO.File]::WriteAllBytes($texturePath, $textureBytes)

foreach ($superseded in @('display_case_connected.json', 'display_case_corner.json')) {
    $supersededPath = Join-Path $modelDirectory $superseded
    if (Test-Path -LiteralPath $supersededPath) { Remove-Item -LiteralPath $supersededPath }
}

Write-Output 'Imported owner display-case bases, half-height upper-cell topology frames, item model, and texture.'
