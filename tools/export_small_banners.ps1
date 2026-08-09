param(
    [string]$IllustratorSource = 'C:\projects\britannia\raw fiels\tabbard\banner_small.ai',
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$bannerSpecs = @(
    @{ StableId = 'silver_and_gold_pennon'; Layer = 'silver_and_gold_pennon' },
    @{ StableId = 'star_standard'; Layer = 'end_01' },
    @{ StableId = 'ship_standard'; Layer = 'end_02' },
    @{ StableId = 'pennon_of_silver'; Layer = 'pennon_of_silver' },
    @{ StableId = 'iron_ward'; Layer = 'iron_ward' },
    @{ StableId = 'iron_ward_auxiliary'; Layer = 'iron_ward_auxiliary' }
)

function Find-NamedPageItem {
    param(
        [Parameter(Mandatory = $true)]$Layer,
        [Parameter(Mandatory = $true)][string]$Name
    )

    foreach ($item in @($Layer.PageItems)) {
        if ($item.Name -eq $Name) {
            return $item
        }
    }
    foreach ($childLayer in @($Layer.Layers)) {
        if ($childLayer.Name -eq $Name -and $childLayer.PageItems.Count -eq 1) {
            return $childLayer.PageItems.Item(1)
        }
        $match = Find-NamedPageItem -Layer $childLayer -Name $Name
        if ($null -ne $match) {
            return $match
        }
    }
    return $null
}

function Set-LayerItemVisibility {
    param(
        [Parameter(Mandatory = $true)]$Layer,
        [Parameter(Mandatory = $true)][bool]$Hidden
    )

    $Layer.Locked = $false
    $Layer.Visible = $true
    foreach ($item in @($Layer.PageItems)) {
        $item.Locked = $false
        $item.Hidden = $Hidden
    }
    foreach ($childLayer in @($Layer.Layers)) {
        Set-LayerItemVisibility -Layer $childLayer -Hidden $Hidden
    }
}

function Get-LayerHierarchy {
    param([Parameter(Mandatory = $true)]$Layer)

    $namedItems = @()
    foreach ($item in @($Layer.PageItems)) {
        if (-not [string]::IsNullOrWhiteSpace([string]$item.Name)) {
            $bounds = @($item.GeometricBounds)
            $clipped = $null
            try {
                $clipped = [bool]$item.Clipped
            } catch {
                # Not every Illustrator page-item type exposes Clipped.
            }
            $namedItems += [ordered]@{
                name = [string]$item.Name
                geometric_bounds = @($bounds | ForEach-Object { [double]$_ })
                hidden = [bool]$item.Hidden
                locked = [bool]$item.Locked
                opacity = [double]$item.Opacity
                clipped = $clipped
            }
        }
    }

    $childLayers = @()
    foreach ($childLayer in @($Layer.Layers)) {
        $childLayers += Get-LayerHierarchy -Layer $childLayer
    }

    return [ordered]@{
        name = [string]$Layer.Name
        visible = [bool]$Layer.Visible
        locked = [bool]$Layer.Locked
        page_item_count = $Layer.PageItems.Count
        group_item_count = $Layer.GroupItems.Count
        path_item_count = $Layer.PathItems.Count
        compound_path_count = $Layer.CompoundPathItems.Count
        placed_item_count = $Layer.PlacedItems.Count
        raster_item_count = $Layer.RasterItems.Count
        named_items = $namedItems
        child_layers = $childLayers
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
    $report.document = [ordered]@{
        name = $document.Name
        colour_space = $colourSpaceName
        colour_space_com_value = $colourSpaceValue
        raster_effects_resolution = [double]$document.RasterEffectSettings.Resolution
        artboard_name = $artboard.Name
        artboard_bounds = @($artLeft, $artTop, $artRight, $artBottom)
        width = $artWidth
        height = $artHeight
        top_level_layers = @($document.Layers | ForEach-Object { [string]$_.Name })
        placed_items = $document.PlacedItems.Count
        raster_items = $document.RasterItems.Count
        clipping_mask_observation = 'No named page item reported Clipped=true. The end_01 dye mask is one unnamed group inside the explicitly named dye_mask child layer.'
        opacity_mask_observation = 'Illustrator COM does not expose opacity-mask membership as a page-item property. Isolated base and dye-mask exports, hierarchy, alpha, and pixel-contract results are the available non-destructive evidence.'
        hierarchy = @($document.Layers | ForEach-Object { Get-LayerHierarchy -Layer $_ })
    }

    foreach ($layer in @($document.Layers)) {
        $layer.Locked = $false
        $layer.Visible = $false
    }

    foreach ($bannerSpec in $bannerSpecs) {
        $bannerId = $bannerSpec.StableId
        $sourceLayerName = $bannerSpec.Layer
        $layer = $null
        foreach ($candidate in @($document.Layers)) {
            if ($candidate.Name -eq $sourceLayerName) {
                $layer = $candidate
                break
            }
        }
        if ($null -eq $layer) {
            throw "Missing authoritative Illustrator layer: $sourceLayerName for $bannerId"
        }

        $layer.Visible = $true
        Set-LayerItemVisibility -Layer $layer -Hidden $true
        $base = Find-NamedPageItem -Layer $layer -Name 'base_texture'
        $mask = Find-NamedPageItem -Layer $layer -Name 'dye_mask'
        if ($null -eq $base -or $null -eq $mask) {
            throw "Layer $bannerId must contain named base_texture and dye_mask artwork"
        }

        $baseBounds = @($base.GeometricBounds)
        $maskBounds = @($mask.GeometricBounds)
        $unionLeft = [Math]::Min([double]$baseBounds[0], [double]$maskBounds[0])
        $unionTop = [Math]::Max([double]$baseBounds[1], [double]$maskBounds[1])
        $unionRight = [Math]::Max([double]$baseBounds[2], [double]$maskBounds[2])
        $unionBottom = [Math]::Min([double]$baseBounds[3], [double]$maskBounds[3])
        $unionWidth = $unionRight - $unionLeft
        $unionHeight = $unionTop - $unionBottom

        $margin = 2.0
        $availableWidth = $artWidth - 2.0 * $margin
        $availableHeight = $artHeight - 2.0 * $margin
        $scale = [Math]::Min(
            1.0,
            [Math]::Min($availableWidth / $unionWidth, $availableHeight / $unionHeight)
        )
        $targetUnionLeft = $artLeft + $margin + ($availableWidth - $unionWidth * $scale) / 2.0
        $targetUnionTop = $artTop - $margin - ($availableHeight - $unionHeight * $scale) / 2.0

        foreach ($entry in @(
            [pscustomobject]@{ Item = $base; Bounds = $baseBounds },
            [pscustomobject]@{ Item = $mask; Bounds = $maskBounds }
        )) {
            $item = $entry.Item
            $bounds = $entry.Bounds
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

        $base.Hidden = $false
        $mask.Hidden = $true
        $baseOutput = Join-Path $submission 'base_texture.png'
        $document.Export($baseOutput, 5, $options)

        $base.Hidden = $true
        $mask.Hidden = $false
        $maskOutput = Join-Path $submission 'dye_mask.png'
        $document.Export($maskOutput, 5, $options)

        $baseAfter = @($base.GeometricBounds)
        $maskAfter = @($mask.GeometricBounds)
        $report.exports += [ordered]@{
            stable_id = "britannia_mod:$bannerId"
            classification = 'READY_FOR_EXPORT'
            layer = $sourceLayerName
            base_object = 'base_texture'
            mask_object = 'dye_mask'
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
            base_bounds_after = @($baseAfter | ForEach-Object { [double]$_ })
            mask_bounds_after = @($maskAfter | ForEach-Object { [double]$_ })
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

$reportPath = Join-Path $RepositoryRoot 'content\banner-final-intake\small_illustrator_report.json'
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Output "Exported $($bannerSpecs.Count) aligned base/mask pairs from $IllustratorSource"
Write-Output "Source SHA-256: $sourceAfter"
