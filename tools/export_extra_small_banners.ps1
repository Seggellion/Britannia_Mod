param(
    [string]$IllustratorSource = 'C:\projects\britannia\raw fiels\tabbard\banner.ai',
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$bannerIds = @(
    'road_guard',
    'pale_road_guard',
    'red_crosslets',
    'captains_red_crosslets',
    'scarlet_court',
    'verdant_court',
    'small_curtain',
    'prosperity_standard',
    'guardian_standard'
)

if (-not (Test-Path -LiteralPath $IllustratorSource -PathType Leaf)) {
    throw "Missing authoritative Illustrator source: $IllustratorSource"
}

$sourceBefore = (Get-FileHash -LiteralPath $IllustratorSource -Algorithm SHA256).Hash.ToLowerInvariant()
$app = New-Object -ComObject Illustrator.Application
$document = $null
$report = [ordered]@{
    schema_version = 1
    source_file = $IllustratorSource.Replace('\', '/')
    source_sha256_before = $sourceBefore
    document = [ordered]@{}
    exports = @()
}

try {
    $document = $app.Open($IllustratorSource)
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

    $report.document = [ordered]@{
        name = $document.Name
        colour_space = [string]$document.DocumentColorSpace
        raster_effects_resolution = [double]$document.RasterEffectSettings.Resolution
        artboard_name = $artboard.Name
        artboard_bounds = @($artLeft, $artTop, $artRight, $artBottom)
        width = $artWidth
        height = $artHeight
        placed_items = $document.PlacedItems.Count
        raster_items = $document.RasterItems.Count
    }

    foreach ($layer in @($document.Layers)) {
        $layer.Visible = $false
        $layer.Locked = $false
    }

    foreach ($bannerId in $bannerIds) {
        $layer = $null
        foreach ($candidate in @($document.Layers)) {
            if ($candidate.Name -eq $bannerId) {
                $layer = $candidate
                break
            }
        }
        if ($null -eq $layer) {
            throw "Missing authoritative Illustrator layer: $bannerId"
        }

        $layer.Visible = $true
        $base = $null
        $mask = $null
        foreach ($item in @($layer.PageItems)) {
            $item.Locked = $false
            $item.Hidden = $true
            if ($item.Name -eq 'base_texture') {
                $base = $item
            } elseif ($item.Name -eq 'dye_mask') {
                $mask = $item
            }
        }
        if ($null -eq $base -or $null -eq $mask) {
            throw "Layer $bannerId must contain top-level base_texture and dye_mask artwork"
        }

        $baseBounds = @($base.GeometricBounds)
        $maskBounds = @($mask.GeometricBounds)
        $unionLeft = [Math]::Min([double]$baseBounds[0], [double]$maskBounds[0])
        $unionTop = [Math]::Max([double]$baseBounds[1], [double]$maskBounds[1])
        $unionRight = [Math]::Max([double]$baseBounds[2], [double]$maskBounds[2])
        $unionBottom = [Math]::Min([double]$baseBounds[3], [double]$maskBounds[3])
        $unionWidth = $unionRight - $unionLeft
        $unionHeight = $unionTop - $unionBottom

        # Retain two transparent pixels around the authored union. Both objects receive the
        # same scale and origin transform so base/mask registration cannot drift.
        $margin = 2.0
        $availableWidth = $artWidth - 2.0 * $margin
        $availableHeight = $artHeight - 2.0 * $margin
        $scale = [Math]::Min(1.0, [Math]::Min($availableWidth / $unionWidth, $availableHeight / $unionHeight))
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

        $report.exports += [ordered]@{
            stable_id = "britannia_mod:$bannerId"
            layer = $bannerId
            base_object = 'base_texture'
            mask_object = 'dye_mask'
            base_bounds_before = @($baseBounds | ForEach-Object { [double]$_ })
            mask_bounds_before = @($maskBounds | ForEach-Object { [double]$_ })
            union_bounds_before = @($unionLeft, $unionTop, $unionRight, $unionBottom)
            shared_scale = $scale
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

$reportPath = Join-Path $RepositoryRoot 'content\banner-final-intake\extra_small_illustrator_report.json'
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Output "Exported $($bannerIds.Count) aligned base/mask pairs from $IllustratorSource"
Write-Output "Source SHA-256: $sourceAfter"
