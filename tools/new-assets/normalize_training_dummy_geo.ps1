param(
    [string]$Path = (Join-Path $PSScriptRoot '..\..\src\main\resources\assets\britannia_mod\geo\training_dummy.geo.json')
)

$ErrorActionPreference = 'Stop'
$resolvedPath = [System.IO.Path]::GetFullPath($Path)
if (-not (Test-Path -LiteralPath $resolvedPath)) {
    throw "Training Dummy geometry was not found: $resolvedPath"
}

$document = Get-Content -LiteralPath $resolvedPath -Raw | ConvertFrom-Json
$geometry = @($document.'minecraft:geometry')[0]
if ($null -eq $geometry) {
    throw "No minecraft:geometry entry was found in $resolvedPath"
}

$document.format_version = '1.12.0'
$geometry.description.identifier = 'geometry.training_dummy'

$cubes = @($geometry.bones | ForEach-Object {
    if ($null -ne $_.cubes) {
        $_.cubes
    }
})
if ($cubes.Count -eq 0) {
    throw "No cubes were found in $resolvedPath"
}

$minimumY = ($cubes | ForEach-Object { [double]$_.origin[1] } | Measure-Object -Minimum).Minimum
$shiftY = if ($minimumY -lt 0) { -$minimumY } else { 0.0 }
if ($shiftY -gt 0) {
    foreach ($bone in $geometry.bones) {
        if ($null -ne $bone.pivot) {
            $bone.pivot[1] = [double]$bone.pivot[1] + $shiftY
        }
        foreach ($cube in @($bone.cubes)) {
            $cube.origin[1] = [double]$cube.origin[1] + $shiftY
            if ($null -ne $cube.pivot) {
                $cube.pivot[1] = [double]$cube.pivot[1] + $shiftY
            }
        }
    }
}

# Blockbench exports a zero-depth element as a six-faced cube. For the front
# arrow decal this produces coincident triangles, while placing it almost on
# the sack surface also makes it fight with the sack's north face. Preserve
# only the intended north face and keep it a small, fixed distance in front.
$sack = @($geometry.bones | Where-Object { $_.name -eq 'sack2' })[0]
$arrow = $null
$sackBody = $null
if ($null -ne $sack) {
    $arrow = @($sack.cubes | Where-Object {
        $northUv = @($_.uv.north.uv)
        [double]$_.size[2] -eq 0.0 -and
        $northUv.Count -ge 2 -and
        [double]$northUv[0] -eq 64.0 -and
        [double]$northUv[1] -eq 0.0
    })[0]
    $sackBody = @($sack.cubes | Where-Object {
        [double]$_.size[0] -eq 10.0 -and
        [double]$_.size[1] -eq 13.0 -and
        [double]$_.size[2] -eq 8.0
    })[0]
}
if ($null -ne $arrow -and $null -ne $sackBody) {
    $arrow.origin[2] = [double]$sackBody.origin[2] - 0.2
    $northFace = $arrow.uv.north
    $arrow.uv = [pscustomobject][ordered]@{ north = $northFace }
}

$json = $document | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText($resolvedPath, $json + [Environment]::NewLine)
$decalStatus = if ($null -ne $arrow -and $null -ne $sackBody) { 'north-only' } else { 'not-found' }
Write-Host "Normalized Training Dummy GEO: format=1.12.0 identifier=geometry.training_dummy shiftY=$shiftY decal=$decalStatus"
