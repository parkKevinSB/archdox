param(
  [string]$OutDir = "."
)

Add-Type -AssemblyName System.Drawing

$root = Resolve-Path $OutDir
$pngDir = Join-Path $root "png"
$svgDir = Join-Path $root "svg"
New-Item -ItemType Directory -Force -Path $pngDir, $svgDir | Out-Null

$colors = @{
  ink = [System.Drawing.Color]::FromArgb(255, 22, 27, 31)
  offWhite = [System.Drawing.Color]::FromArgb(255, 244, 248, 246)
  deepTeal = [System.Drawing.Color]::FromArgb(255, 12, 73, 86)
  mint = [System.Drawing.Color]::FromArgb(255, 143, 214, 200)
  bronze = [System.Drawing.Color]::FromArgb(255, 184, 135, 47)
  blueprint = [System.Drawing.Color]::FromArgb(255, 57, 92, 107)
  previewDark = [System.Drawing.Color]::FromArgb(255, 17, 33, 31)
}

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

function Font($family, $size, $style = [System.Drawing.FontStyle]::Regular) {
  try {
    return New-Object System.Drawing.Font($family, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
  } catch {
    return New-Object System.Drawing.Font("Segoe UI", $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
  }
}

function Brush($color) {
  return New-Object System.Drawing.SolidBrush($color)
}

function PenOf($color, $width) {
  $pen = New-Object System.Drawing.Pen($color, $width)
  $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
  return $pen
}

function Draw-DoorMark($g, [float]$x, [float]$y, [float]$w, [float]$h, [bool]$lightLines = $false) {
  $stroke = $w * 0.18
  $archPath = New-Object System.Drawing.Drawing2D.GraphicsPath
  $left = $x + ($stroke / 2)
  $right = $x + $w - ($stroke / 2)
  $bottom = $y + $h
  $top = $y + ($stroke / 2)
  $mid = $x + ($w / 2)
  $archPath.StartFigure()
  $archPath.AddLine($left, $bottom, $left, $y + ($h * 0.43))
  $archPath.AddBezier($left, $y + ($h * 0.43), $left, $y, $mid, $top, $mid, $top)
  $archPath.AddBezier($mid, $top, $right, $y, $right, $y + ($h * 0.43), $right, $y + ($h * 0.43))
  $archPath.AddLine($right, $y + ($h * 0.43), $right, $bottom)
  $g.DrawPath((PenOf $colors.deepTeal $stroke), $archPath)
  $archPath.Dispose()

  $paperBrush = Brush ([System.Drawing.Color]::FromArgb(242, 255, 255, 255))
  $paper = New-Object System.Drawing.RectangleF(($x + $w * 0.34), ($y + $h * 0.36), ($w * 0.42), ($h * 0.52))
  $g.FillRectangle($paperBrush, $paper)
  $paperBrush.Dispose()

  $fold = New-Object System.Drawing.Drawing2D.GraphicsPath
  $fold.AddPolygon(@(
    (New-Object System.Drawing.PointF(($x + $w * 0.58), ($y + $h * 0.36))),
    (New-Object System.Drawing.PointF(($x + $w * 0.76), ($y + $h * 0.55))),
    (New-Object System.Drawing.PointF(($x + $w * 0.58), ($y + $h * 0.55)))
  ))
  $foldBrush = Brush ([System.Drawing.Color]::FromArgb(125, 118, 140, 154))
  $g.FillPath($foldBrush, $fold)
  $foldBrush.Dispose()
  $fold.Dispose()

  if ($lightLines) {
    $lineColor = [System.Drawing.Color]::FromArgb(210, $colors.mint)
  } else {
    $lineColor = $colors.blueprint
  }
  $axisPen = PenOf $lineColor ([Math]::Max(2, $w * 0.018))
  $axisX = $x + ($w * 0.36)
  $axisY = $y + ($h * 0.80)
  $g.DrawLine($axisPen, $axisX, $y + ($h * 0.43), $axisX, $y + ($h * 0.96))
  $g.DrawLine($axisPen, $x + ($w * 0.24), $axisY, $x + ($w * 0.77), $axisY)
  $axisPen.Dispose()

  $arcPen = PenOf $lineColor ([Math]::Max(2, $w * 0.014))
  $arcPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
  $rect = New-Object System.Drawing.RectangleF(($x + $w * 0.24), ($y + $h * 0.58), ($w * 0.54), ($h * 0.54))
  $g.DrawArc($arcPen, $rect, 200, 120)
  $arcPen.Dispose()

  $dotBrush = Brush $colors.bronze
  $dot = [Math]::Max(6, $w * 0.055)
  $g.FillRectangle($dotBrush, $axisX - ($dot / 2), $axisY - ($dot / 2), $dot, $dot)
  $dotBrush.Dispose()
}

function Draw-CustomX($g, [float]$x, [float]$y, [float]$w, [float]$h, [float]$stroke, $main, $accent) {
  $p1 = PenOf $main $stroke
  $p2 = PenOf $accent $stroke
  $p1.StartCap = [System.Drawing.Drawing2D.LineCap]::Square
  $p1.EndCap = [System.Drawing.Drawing2D.LineCap]::Square
  $p2.StartCap = [System.Drawing.Drawing2D.LineCap]::Square
  $p2.EndCap = [System.Drawing.Drawing2D.LineCap]::Square
  $g.DrawLine($p1, $x, $y, $x + $w, $y + $h)
  $g.DrawLine($p2, $x + $w, $y, $x, $y + $h)
  $p1.Dispose()
  $p2.Dispose()
}

function Draw-Wordmark($g, [float]$x, [float]$y, $textColor, $xAccent) {
  $font = Font "Segoe UI" 130 ([System.Drawing.FontStyle]::Bold)
  $brush = Brush $textColor
  $fmt = New-Object System.Drawing.StringFormat
  $g.DrawString("archdo", $font, $brush, $x, $y, $fmt)
  Draw-CustomX $g ($x + 590) ($y + 18) 116 132 24 $textColor $xAccent
  $fmt.Dispose()
  $brush.Dispose()
  $font.Dispose()
}

function Write-Svg($path, $dark = $false) {
  if ($dark) {
    $word = "#f4f8f6"
    $xAccent = "#8fd6c8"
    $line = "#8fd6c8"
  } else {
    $word = "#161b1f"
    $xAccent = "#0c4956"
    $line = "#395c6b"
  }
  $svg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="1100" height="520" viewBox="0 0 1100 520">
  <title>archdox centered mark logo</title>
  <g fill="none" stroke-linecap="square" stroke-linejoin="round">
    <path d="M501 170V109C501 62 535 44 550 44C565 44 599 62 599 109V170" stroke="#0c4956" stroke-width="22"/>
    <rect x="531" y="98" width="46" height="60" fill="#ffffff" opacity=".95" stroke="none"/>
    <path d="M557 98L577 120H557Z" fill="#768c9a" opacity=".55" stroke="none"/>
    <path d="M532 116V183M519 153H584" stroke="$line" stroke-width="2.4" opacity=".9"/>
    <path d="M519 138A57 57 0 0 1 584 196" stroke="$line" stroke-width="2.1" stroke-dasharray="8 8" opacity=".9"/>
    <rect x="527" y="148" width="8" height="8" fill="#b8872f" stroke="none"/>
    <text x="185" y="374" fill="$word" font-family="Segoe UI, Arial, sans-serif" font-size="130" font-weight="700" stroke="none">archdo</text>
    <path d="M775 251L891 383" stroke="$word" stroke-width="24"/>
    <path d="M891 251L775 383" stroke="$xAccent" stroke-width="24"/>
  </g>
</svg>
"@
  Set-Content -Path $path -Value $svg -Encoding UTF8
}

function Render-Primary {
  $c = New-Canvas 1100 520
  $g = $c.Graphics
  Draw-DoorMark $g 489 44 122 126 $false
  Draw-Wordmark $g 185 232 $colors.ink $colors.deepTeal
  Save-Png $c (Join-Path $pngDir "archdox-centered-mark-primary.png")
  Write-Svg (Join-Path $svgDir "archdox-centered-mark-primary.svg") $false
}

function Render-Dark {
  $c = New-Canvas 1100 520
  $g = $c.Graphics
  Draw-DoorMark $g 489 44 122 126 $true
  Draw-Wordmark $g 185 232 $colors.offWhite $colors.mint
  Save-Png $c (Join-Path $pngDir "archdox-centered-mark-dark.png")
  Write-Svg (Join-Path $svgDir "archdox-centered-mark-dark.svg") $true
}

function Render-Preview {
  $c = New-Canvas 1500 940 $colors.previewDark
  $g = $c.Graphics
  $title = Font "Segoe UI" 42 ([System.Drawing.FontStyle]::Bold)
  $body = Font "Segoe UI" 24 ([System.Drawing.FontStyle]::Regular)
  $titleBrush = Brush $colors.offWhite
  $bodyBrush = Brush $colors.mint
  $g.DrawString("archdox centered mark refinement", $title, $titleBrush, 72, 58)
  $g.DrawString("Preview backgrounds are for viewing only. Individual logo PNG files are transparent.", $body, $bodyBrush, 76, 112)

  $lightPanel = Brush $colors.offWhite
  $darkPanel = Brush ([System.Drawing.Color]::FromArgb(255, 22, 42, 39))
  $g.FillRectangle($lightPanel, 72, 178, 1356, 300)
  $g.FillRectangle($darkPanel, 72, 534, 1356, 300)

  $primary = [System.Drawing.Image]::FromFile((Join-Path $pngDir "archdox-centered-mark-primary.png"))
  $dark = [System.Drawing.Image]::FromFile((Join-Path $pngDir "archdox-centered-mark-dark.png"))
  $g.DrawImage($primary, 320, 214, 704, 333)
  $g.DrawImage($dark, 320, 570, 704, 333)
  $primary.Dispose()
  $dark.Dispose()

  $g.DrawString("primary / light surface", $body, (Brush $colors.deepTeal), 104, 202)
  $g.DrawString("reverse / dark surface", $body, $bodyBrush, 104, 558)

  $title.Dispose()
  $body.Dispose()
  $titleBrush.Dispose()
  $bodyBrush.Dispose()
  $lightPanel.Dispose()
  $darkPanel.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-centered-mark-preview.png")
}

Render-Primary
Render-Dark
Render-Preview

Write-Host "Generated centered archdox logo assets in $root"
