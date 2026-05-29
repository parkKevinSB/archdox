param(
  [string]$OutDir = "."
)

Add-Type -AssemblyName System.Drawing

$root = Resolve-Path $OutDir
$pngDir = Join-Path $root "png"
New-Item -ItemType Directory -Force -Path $pngDir | Out-Null

$gray = [System.Drawing.Color]::FromArgb(255, 124, 126, 128)
$black = [System.Drawing.Color]::FromArgb(255, 0, 0, 0)
$pageDark = [System.Drawing.Color]::FromArgb(255, 17, 33, 31)
$mint = [System.Drawing.Color]::FromArgb(255, 143, 214, 200)
$offWhite = [System.Drawing.Color]::FromArgb(255, 244, 248, 246)

function New-Canvas($w, $h, $background = $null) {
  $bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
  if ($null -eq $background) {
    $g.Clear([System.Drawing.Color]::Transparent)
  } else {
    $g.Clear($background)
  }
  return @{ Bitmap = $bmp; Graphics = $g }
}

function Save-Png($canvas, $path) {
  $canvas.Graphics.Dispose()
  $canvas.Bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $canvas.Bitmap.Dispose()
}

function Font($family, $size, $style) {
  return New-Object System.Drawing.Font($family, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

function Draw-Logo($g, [float]$x, [float]$y, [float]$scale) {
  $brush = New-Object System.Drawing.SolidBrush($gray)
  $archFont = Font "Segoe UI" (38 * $scale) ([System.Drawing.FontStyle]::Bold)
  $doFont = Font "Segoe UI" (32 * $scale) ([System.Drawing.FontStyle]::Bold)
  $xFont = Font "Segoe UI" (122 * $scale) ([System.Drawing.FontStyle]::Bold)
  $fmt = New-Object System.Drawing.StringFormat

  # Arch sits directly over do. The bottom line reads as doX.
  $g.DrawString("Arch", $archFont, $brush, $x, $y, $fmt)
  $g.DrawString("do", $doFont, $brush, ($x + 30 * $scale), ($y + 50 * $scale), $fmt)
  $g.DrawString("X", $xFont, $brush, ($x + 72 * $scale), ($y - 2 * $scale), $fmt)

  $fmt.Dispose()
  $archFont.Dispose()
  $doFont.Dispose()
  $xFont.Dispose()
  $brush.Dispose()
}

function Render-Transparent {
  $c = New-Canvas 620 310
  Draw-Logo $c.Graphics 92 54 1.35
  Save-Png $c (Join-Path $pngDir "archdox-arch-over-dox-transparent.png")
}

function Render-Black {
  $c = New-Canvas 620 310 $black
  Draw-Logo $c.Graphics 92 54 1.35
  Save-Png $c (Join-Path $pngDir "archdox-arch-over-dox-black.png")
}

function Render-Preview {
  $c = New-Canvas 1000 520 $pageDark
  $g = $c.Graphics
  $titleFont = Font "Segoe UI" 34 ([System.Drawing.FontStyle]::Bold)
  $bodyFont = Font "Segoe UI" 21 ([System.Drawing.FontStyle]::Regular)
  $white = New-Object System.Drawing.SolidBrush($offWhite)
  $body = New-Object System.Drawing.SolidBrush($mint)
  $panel = New-Object System.Drawing.SolidBrush($black)

  $g.DrawString("Arch over doX reference layout", $titleFont, $white, 48, 42)
  $g.DrawString("Arch is attached above do, and X stays close to form doX.", $bodyFont, $body, 50, 88)
  $g.FillRectangle($panel, 50, 145, 900, 275)
  Draw-Logo $g 285 204 1.35

  $titleFont.Dispose()
  $bodyFont.Dispose()
  $white.Dispose()
  $body.Dispose()
  $panel.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-arch-over-dox-preview.png")
}

Render-Transparent
Render-Black
Render-Preview

Write-Host "Generated Arch-over-doX layout in $pngDir"
