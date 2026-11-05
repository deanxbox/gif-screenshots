# Hardware smoke test: synthetic clip through each advertised encoder, then GIF conversion.
param([string]$Ffmpeg = "ffmpeg")
$ErrorActionPreference = "Stop"
$oldOrder = $env:CUDA_DEVICE_ORDER
$video = Join-Path ([IO.Path]::GetTempPath()) ("gif-devices-" + [guid]::NewGuid() + ".mkv")
$gif = "$video.gif"
try {
    $env:CUDA_DEVICE_ORDER = "PCI_BUS_ID"
    $cards = @()
    if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) {
        $rows = @(nvidia-smi --query-gpu=pci.bus_id,name --format=csv,noheader)
        if ($LASTEXITCODE -eq 0) { $cards = @($rows | Sort-Object) }
    }
    $devices = @(-1)
    for ($i = 0; $i -lt $cards.Count; $i++) { $devices += $i }
    foreach ($device in $devices) {
        if ($device -lt 0) {
            $label = "CPU"
            $encoder = @("-c:v", "libx264", "-preset", "ultrafast", "-crf", "10", "-threads", "2")
        } else {
            $label = "$device`: $($cards[$device].Split(',')[1].Trim())"
            $encoder = @("-c:v", "h264_nvenc", "-gpu", "$device", "-preset", "p1", "-rc", "constqp", "-qp", "10")
        }
        & $Ffmpeg -y -loglevel error -f lavfi -i "testsrc2=s=640x360:r=15:d=1" -vf "scale=320:-2:flags=bicubic" -pix_fmt yuv444p @encoder $video
        if ($LASTEXITCODE -ne 0) { throw "Video encoding failed: $label" }
        & $Ffmpeg -y -loglevel error -i $video -vf "split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle" -loop 0 $gif
        if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $gif).Length -eq 0) { throw "GIF conversion failed: $label" }
        & $Ffmpeg -v error -i $gif -f null -
        if ($LASTEXITCODE -ne 0) { throw "GIF decoding failed: $label" }
        Write-Output "PASS: $label -> video -> GIF -> decode"
    }
} finally {
    $env:CUDA_DEVICE_ORDER = $oldOrder
    Remove-Item -LiteralPath $video, $gif -Force -ErrorAction SilentlyContinue
}
