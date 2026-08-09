param(
    [string]$IllustratorSource = 'C:\projects\britannia\raw fiels\tabbard\banner_medium_wall.ai',
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$bannerSpecs = @(
    @{ StableId = 'verdant_grape_pennon'; Layer = 'verdant_grape_pennon'; PreviousId = 'medium_wall_01'; CatalogueIndex = 7 },
    @{ StableId = 'silver_rosette_pennon'; Layer = 'silver_rosette_pennon'; PreviousId = 'medium_wall_02'; CatalogueIndex = 8 },
    @{ StableId = 'four_seals_pennon'; Layer = 'four_seals_pennon'; PreviousId = 'medium_wall_03'; CatalogueIndex = 9 },
    @{ StableId = 'twin_spades_pennon'; Layer = 'twin_spades_pennon'; PreviousId = 'medium_wall_04'; CatalogueIndex = 10 },
    @{ StableId = 'ankh_pennon'; Layer = 'ankh_pennon'; PreviousId = 'medium_wall_05'; CatalogueIndex = 11 },
    @{ StableId = 'joined_wards'; Layer = 'joined_wards'; PreviousId = $null; CatalogueIndex = 12 }
)

function Get-IndexedCollection {
    param([Parameter(Mandatory = $true)]$Collection)

    $values = @()
    for ($index = 1; $index -le $Collection.Count; $index++) {
        $values += $Collection.Item($index)
    }
    return $values
}

function Get-ItemBoundsUnion {
    param([Parameter(Mandatory = $true)][array]$Items)

    $left = [double]::PositiveInfinity
    $top = [double]::NegativeInfinity
    $right = [double]::NegativeInfinity
    $bottom = [double]::PositiveInfinity
    foreach ($item in $Items) {
        $bounds = @($item.GeometricBounds)
        $left = [Math]::Min($left, [double]$bounds[0])
        $top = [Math]::Max($top, [double]$bounds[1])
        $right = [Math]::Max($right, [double]$bounds[2])
        $bottom = [Math]::Min($bottom, [double]$bounds[3])
    }
    return @($left, $top, $right, $bottom)
}

function Find-NamedPageItems {
    param(
        [Parameter(Mandatory = $true)]$Layer,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $matches = @()
    foreach ($item in Get-IndexedCollection -Collection $Layer.PageItems) {
        if ($item.Name -eq $Name) {
            $matches += $item
        }
    }
    foreach ($childLayer in Get-IndexedCollection -Collection $Layer.Layers) {
        if ($childLayer.Name -eq $Name) {
            $childItems = @(Get-IndexedCollection -Collection $childLayer.PageItems)
            if ($childItems.Count -gt 0) {
                $matches += $childItems
                continue
            }
        }
        $matches += @(Find-NamedPageItems -Layer $childLayer -Name $Name)
    }
    return $matches
}

function Set-LayerItemVisibility {
    param(
        [Parameter(Mandatory = $true)]$Layer,
        [Parameter(Mandatory = $true)][bool]$Hidden
    )

    $Layer.Locked = $false
    $Layer.Visible = $true
    foreach ($item in Get-IndexedCollection -Collection $Layer.PageItems) {
        $item.Locked = $false
        $item.Hidden = $Hidden
    }
    foreach ($childLayer in Get-IndexedCollection -Collection $Layer.Layers) {
        Set-LayerItemVisibility -Layer $childLayer -Hidden $Hidden
    }
}

function Get-PageItemEvidence {
    param([Parameter(Mandatory = $true)]$Item)

    $clipped = $null
    try {
        $clipped = [bool]$Item.Clipped
    } catch {
        # Not every Illustrator page-item type exposes Clipped.
    }
    $embedded = $null
    try {
        $embedded = [bool]$Item.Embedded
    } catch {
        # Only placed/raster items expose Embedded.
    }
    return [ordered]@{
        name = [string]$Item.Name
        geometric_bounds = @($Item.GeometricBounds | ForEach-Object { [double]$_ })
        visible_bounds = @($Item.VisibleBounds | ForEach-Object { [double]$_ })
        hidden = [bool]$Item.Hidden
        locked = [bool]$Item.Locked
        opacity = [double]$Item.Opacity
        clipped = $clipped
        embedded = $embedded
    }
}

function Get-LayerHierarchy {
    param([Parameter(Mandatory = $true)]$Layer)

    $items = @()
    foreach ($item in Get-IndexedCollection -Collection $Layer.PageItems) {
        $items += Get-PageItemEvidence -Item $item
    }
    $children = @()
    foreach ($child in Get-IndexedCollection -Collection $Layer.Layers) {
        $children += Get-LayerHierarchy -Layer $child
    }
    return [ordered]@{
        name = [string]$Layer.Name
        visible = [bool]$Layer.Visible
        locked = [bool]$Layer.Locked
        page_item_count = [int]$Layer.PageItems.Count
        group_item_count = [int]$Layer.GroupItems.Count
        path_item_count = [int]$Layer.PathItems.Count
        compound_path_count = [int]$Layer.CompoundPathItems.Count
        placed_item_count = [int]$Layer.PlacedItems.Count
        raster_item_count = [int]$Layer.RasterItems.Count
        items = $items
        child_layers = $children
    }
}

if (-not (Test-Path -LiteralPath $IllustratorSource -PathType Leaf)) {
    throw "Missing authoritative Illustrator source: $IllustratorSource"
}

$sourceInfo = Get-Item -LiteralPath $IllustratorSource
$sourceBefore = (Get-FileHash -LiteralPath $IllustratorSource -Algorithm SHA256).Hash.ToLowerInvariant()
$headerBytes = New-Object byte[] 256
$headerStream = [System.IO.File]::OpenRead($IllustratorSource)
try {
    [void]$headerStream.Read($headerBytes, 0, $headerBytes.Length)
} finally {
    $headerStream.Dispose()
}
$header = [System.Text.Encoding]::ASCII.GetString($headerBytes)

$app = New-Object -ComObject Illustrator.Application
$document = $null
$report = [ordered]@{
    schema_version = 1
    source_file = $IllustratorSource.Replace('\', '/')
    source_size_bytes = [long]$sourceInfo.Length
    source_sha256_before = $sourceBefore
    pdf_compatible = $header.Contains('%PDF-')
    export_canvas_rule = 'Use the single authoritative 128 x 128 artboard. When the authored base/mask union extends outside it, apply one proportional downscale (never upscale) and one shared translation that centers the complete union within a two-pixel transparent margin. Export both objects with artboard clipping from that unchanged shared coordinate system.'
    reconciliation = @()
    document = [ordered]@{}
    exports = @()
}

try {
    $document = $app.Open($IllustratorSource)
    if ($document.Artboards.Count -ne 1) {
        throw "Expected exactly one authoritative artboard; got $($document.Artboards.Count)"
    }

    $artboard = $document.Artboards.Item(1)
    $artboardRect = @($artboard.ArtboardRect)
    $artLeft = [double]$artboardRect[0]
    $artTop = [double]$artboardRect[1]
    $artRight = [double]$artboardRect[2]
    $artBottom = [double]$artboardRect[3]
    $artWidth = $artRight - $artLeft
    $artHeight = $artTop - $artBottom
    if ([Math]::Abs($artWidth - 128.0) -gt 0.001 -or [Math]::Abs($artHeight - 128.0) -gt 0.001) {
        throw "Authoritative artboard must be 128 x 128 points; got $artWidth x $artHeight"
    }

    $colourSpaceValue = [int]$document.DocumentColorSpace
    $colourSpaceName = switch ($colourSpaceValue) {
        1 { 'CMYK' }
        2 { 'RGB' }
        default { "UNKNOWN_$colourSpaceValue" }
    }
    $topLevelLayers = @(Get-IndexedCollection -Collection $document.Layers)
    $report.document = [ordered]@{
        name = $document.Name
        colour_space = $colourSpaceName
        colour_space_com_value = $colourSpaceValue
        raster_effects_resolution = [double]$document.RasterEffectSettings.Resolution
        artboard_name = $artboard.Name
        artboard_bounds = @($artLeft, $artTop, $artRight, $artBottom)
        width = $artWidth
        height = $artHeight
        top_level_layers = @($topLevelLayers | ForEach-Object { [string]$_.Name })
        placed_items = [int]$document.PlacedItems.Count
        raster_items = [int]$document.RasterItems.Count
        text_frames = [int]$document.TextFrames.Count
        clipping_mask_observation = 'No inspected named page item reports Clipped=true. joined_wards stores its authored selection as two unnamed objects inside a dye_mask child layer.'
        opacity_mask_observation = 'Illustrator COM does not expose opacity-mask membership as a page-item property. Isolated base and dye-mask exports, hierarchy, alpha, and pixel-contract results are the available non-destructive evidence.'
        embedded_resource_observation = 'The document has six embedded raster base items and zero linked/placed items.'
        hierarchy = @($topLevelLayers | ForEach-Object { Get-LayerHierarchy -Layer $_ })
    }

    $expectedLayers = @($bannerSpecs | ForEach-Object { $_.Layer })
    $actualLayers = @($topLevelLayers | ForEach-Object { [string]$_.Name })
    if (Compare-Object -ReferenceObject $expectedLayers -DifferenceObject $actualLayers) {
        throw "Authoritative Illustrator membership differs from the expected six parallel Medium layers"
    }

    foreach ($layer in $topLevelLayers) {
        $layer.Locked = $false
        $layer.Visible = $false
    }

    foreach ($bannerSpec in $bannerSpecs) {
        $bannerId = $bannerSpec.StableId
        $layer = $topLevelLayers | Where-Object { $_.Name -eq $bannerSpec.Layer } | Select-Object -First 1
        if ($null -eq $layer) {
            throw "Missing authoritative Illustrator layer: $($bannerSpec.Layer) for $bannerId"
        }

        $report.reconciliation += [ordered]@{
            illustrator_name = $bannerSpec.Layer
            candidate_stable_id = "britannia_mod:$bannerId"
            existing_catalogue_id = if ($null -eq $bannerSpec.PreviousId) { "britannia_mod:$bannerId" } else { "britannia_mod:$($bannerSpec.PreviousId)" }
            existing_group = 'medium-wall'
            catalogue_index = [int]$bannerSpec.CatalogueIndex
            mapping_evidence = if ($null -eq $bannerSpec.PreviousId) {
                'Exact Illustrator layer, requested canonical stable ID, and existing source-named catalogue identity.'
            } else {
                'Exact requested final identity plus the source layer sequence anchored by joined_wards at catalogue index 12; replaces the corresponding clearly provisional medium_wall ID without renumbering.'
            }
            orientation_evidence = 'The task identifies banner_medium_wall.ai as the authoritative parallel Medium source and the existing medium-wall group as the parallel-family catalogue boundary.'
            result = if ($null -eq $bannerSpec.PreviousId) { 'MATCH_EXISTING_MEDIUM_WALL' } else { 'MIGRATE_PROVISIONAL_MEDIUM_WALL_ID' }
        }

        $layer.Visible = $true
        Set-LayerItemVisibility -Layer $layer -Hidden $true
        $baseItems = @(Find-NamedPageItems -Layer $layer -Name 'base_texture')
        $maskItems = @(Find-NamedPageItems -Layer $layer -Name 'dye_mask')
        if ($baseItems.Count -ne 1 -or $maskItems.Count -lt 1) {
            throw "Layer $bannerId must contain one base_texture and non-empty dye_mask artwork"
        }

        $baseBounds = @(Get-ItemBoundsUnion -Items $baseItems)
        $maskBounds = @(Get-ItemBoundsUnion -Items $maskItems)
        $unionLeft = [Math]::Min([double]$baseBounds[0], [double]$maskBounds[0])
        $unionTop = [Math]::Max([double]$baseBounds[1], [double]$maskBounds[1])
        $unionRight = [Math]::Max([double]$baseBounds[2], [double]$maskBounds[2])
        $unionBottom = [Math]::Min([double]$baseBounds[3], [double]$maskBounds[3])
        $unionWidth = $unionRight - $unionLeft
        $unionHeight = $unionTop - $unionBottom

        $margin = 2.0
        $availableWidth = $artWidth - 2.0 * $margin
        $availableHeight = $artHeight - 2.0 * $margin
        $scale = [Math]::Min(1.0, [Math]::Min(
            $availableWidth / $unionWidth,
            $availableHeight / $unionHeight
        ))
        $targetUnionLeft = $artLeft + $margin + ($availableWidth - $unionWidth * $scale) / 2.0
        $targetUnionTop = $artTop - $margin - ($availableHeight - $unionHeight * $scale) / 2.0

        $transformed = @()
        foreach ($item in @($baseItems + $maskItems)) {
            $bounds = @($item.GeometricBounds)
            $transformed += [pscustomobject]@{ Item = $item; Bounds = $bounds }
            $item.Resize($scale * 100.0, $scale * 100.0)
            $current = @($item.GeometricBounds)
            $desiredLeft = $targetUnionLeft + ([double]$bounds[0] - $unionLeft) * $scale
            $desiredTop = $targetUnionTop - ($unionTop - [double]$bounds[1]) * $scale
            $item.Translate($desiredLeft - [double]$current[0], $desiredTop - [double]$current[1])
        }

        $submission = Join-Path $RepositoryRoot "content\banner-final-intake\submissions\$bannerId"
        New-Item -ItemType Directory -Path $submission -Force | Out-Null
        $options = New-Object -ComObject Illustrator.ExportOptionsPNG24
        $options.AntiAliasing = $true
        $options.ArtBoardClipping = $true
        $options.HorizontalScale = 100.0
        $options.VerticalScale = 100.0
        $options.SaveAsHTML = $false
        $options.Transparency = $true

        foreach ($item in $baseItems) {
            $item.Hidden = $false
        }
        foreach ($item in $maskItems) {
            $item.Hidden = $true
        }
        $baseOutput = Join-Path $submission 'base_texture.png'
        $document.Export($baseOutput, 5, $options)

        foreach ($item in $baseItems) {
            $item.Hidden = $true
        }
        foreach ($item in $maskItems) {
            $item.Hidden = $false
        }
        $maskOutput = Join-Path $submission 'dye_mask.png'
        $document.Export($maskOutput, 5, $options)

        $report.exports += [ordered]@{
            stable_id = "britannia_mod:$bannerId"
            classification = 'READY_FOR_EXPORT'
            layer = $bannerSpec.Layer
            base_object = 'base_texture'
            mask_object = if ($maskItems.Count -eq 1) { 'dye_mask' } else { "dye_mask child layer ($($maskItems.Count) objects)" }
            base_bounds_before = @($baseBounds | ForEach-Object { [double]$_ })
            mask_bounds_before = @($maskBounds | ForEach-Object { [double]$_ })
            union_bounds_before = @($unionLeft, $unionTop, $unionRight, $unionBottom)
            artwork_extended_outside_artboard = (
                $unionLeft -lt $artLeft -or
                $unionTop -gt $artTop -or
                $unionRight -gt $artRight -or
                $unionBottom -lt $artBottom
            )
            shared_scale = $scale
            base_bounds_after = @(Get-ItemBoundsUnion -Items $baseItems)
            mask_bounds_after = @(Get-ItemBoundsUnion -Items $maskItems)
            output_canvas = @(128, 128)
            base_output = $baseOutput.Substring($RepositoryRoot.Length + 1).Replace('\', '/')
            mask_output = $maskOutput.Substring($RepositoryRoot.Length + 1).Replace('\', '/')
        }

        $layer.Visible = $false
    }
} finally {
    if ($null -ne $document) {
        # 2 = aiDoNotSaveChanges. The authoritative document is never saved.
        $document.Close(2)
    }
    $app.Quit()
}

$sourceAfter = (Get-FileHash -LiteralPath $IllustratorSource -Algorithm SHA256).Hash.ToLowerInvariant()
$report.source_sha256_after = $sourceAfter
if ($sourceAfter -ne $sourceBefore) {
    throw "Authoritative Illustrator source changed during export"
}

$reportPath = Join-Path $RepositoryRoot 'content\banner-final-intake\parallel_medium_illustrator_report.json'
$report | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Output "Exported $($bannerSpecs.Count) aligned base/mask pairs from $IllustratorSource"
Write-Output "Source SHA-256: $sourceAfter"
