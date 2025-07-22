# Recursively decompress all GZIP-compressed .nbt files in the structures folder

$basePath = "C:\projects\Britannia\mods\Britannia\Britannia_Mod\src\main\resources\data\britannia_mod\structures"

# GZIP magic numbers: 1F 8B 08
$gzipHeader = 0x1F,0x8B,0x08

Get-ChildItem -Path $basePath -Filter *.nbt -Recurse | ForEach-Object {

    $file   = $_.FullName
    $bytes  = Get-Content -Path $file -Encoding Byte -TotalCount 3

    $isGzip = ($bytes.Count -ge 3) -and
            ($bytes[0] -eq 0x1F) -and
            ($bytes[1] -eq 0x8B) -and
            ($bytes[2] -eq 0x08)

    if ($isGzip) {
        Write-Host "Decompressing: $file"
        $tmp = "$file.decompressed"

        try {
            $in  = [IO.File]::OpenRead($file)
            $out = [IO.File]::Create($tmp)
            $gz  = [IO.Compression.GzipStream]::new($in,[IO.Compression.CompressionMode]::Decompress)

            $gz.CopyTo($out)

            $gz.Dispose()
            $in.Dispose()
            $out.Dispose()

            Remove-Item $file -Force
            Rename-Item $tmp  $file -Force

            Write-Host "Decompressed: $file"
        }
        catch {
            Write-Warning "Failed to decompress: $file - $_"
        }
    }
    else {
        Write-Host "Skipped (not GZIP): $file"
    }
}
