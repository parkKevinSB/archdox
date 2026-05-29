param(
  [string]$OutDir = "."
)

Add-Type -AssemblyName System.Drawing

$root = Resolve-Path $OutDir
$pngDir = Join-Path $root "png"
New-Item -ItemType Directory -Force -Path $pngDir | Out-Null

$colors = @{
  dark = [System.Drawing.Color]::FromArgb(255, 17, 33, 31)
  panel = [System.Drawing.Color]::FromArgb(255, 12, 29, 27)
  offWhite = [System.Drawing.Color]::FromArgb(255, 244, 248, 246)
  softWhite = [System.Drawing.Color]::FromArgb(255, 224, 232, 229)
  muted = [System.Drawing.Color]::FromArgb(255, 155, 171, 168)
  teal = [System.Drawing.Color]::FromArgb(255, 143, 214, 200)
  deepTeal = [System.Drawing.Color]::FromArgb(255, 10, 78, 91)
  bronze = [System.Drawing.Color]::FromArgb(255, 184, 135, 47)
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
  $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Square
  $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Square
  $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
  return $pen
}

function Draw-X($g, [float]$x, [float]$y, [float]$w, [float]$h, [float]$stroke, $main, $accent) {
  $p1 = PenOf $main $stroke
  $p2 = PenOf $accent $stroke
  $g.DrawLine($p1, $x, $y, $x + $w, $y + $h)
  $g.DrawLine($p2, $x + $w, $y, $x, $y + $h)
  $p1.Dispose()
  $p2.Dispose()
}

function Draw-Text($g, $text, $font, $brush, [float]$x, [float]$y) {
  $fmt = New-Object System.Drawing.StringFormat
  $fmt.FormatFlags = [System.Drawing.StringFormatFlags]::NoClip
  $g.DrawString($text, $font, $brush, $x, $y, $fmt)
  $fmt.Dispose()
}

function Draw-Concept01($path) {
  $c = New-Canvas 820 430
  $g = $c.Graphics
  $archFont = Font "Century Gothic" 76 ([System.Drawing.FontStyle]::Bold)
  $doFont = Font "Century Gothic" 58 ([System.Drawing.FontStyle]::Regular)
  $bArch = Brush $colors.offWhite
  $bDo = Brush $colors.teal
  Draw-Text $g "arch" $archFont $bArch 105 105
  Draw-Text $g "do" $doFont $bDo 222 214
  Draw-X $g 350 86 205 252 34 $colors.offWhite $colors.teal
  $linePen = PenOf ([System.Drawing.Color]::FromArgb(180, $colors.teal)) 3
  $linePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $linePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $g.DrawLine($linePen, 104, 220, 295, 220)
  $dot = Brush $colors.bronze
  $g.FillRectangle($dot, 306, 213, 12, 12)
  $linePen.Dispose()
  $dot.Dispose()
  $bArch.Dispose()
  $bDo.Dispose()
  $archFont.Dispose()
  $doFont.Dispose()
  Save-Png $c $path
}

function Draw-Concept02($path) {
  $c = New-Canvas 820 430
  $g = $c.Graphics
  $archFont = Font "Segoe UI" 70 ([System.Drawing.FontStyle]::Bold)
  $doFont = Font "Segoe UI" 64 ([System.Drawing.FontStyle]::Bold)
  $bArch = Brush $colors.softWhite
  $bDo = Brush $colors.offWhite
  Draw-Text $g "Arch" $archFont $bArch 112 98
  Draw-Text $g "do" $doFont $bDo 238 196
  Draw-X $g 360 80 215 260 35 $colors.offWhite $colors.teal
  $p = PenOf ([System.Drawing.Color]::FromArgb(150, $colors.muted)) 3
  $p.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $p.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $g.DrawLine($p, 112, 205, 328, 205)
  $dot = Brush $colors.bronze
  $g.FillEllipse($dot, 331, 199, 12, 12)
  $p.Dispose()
  $dot.Dispose()
  $bArch.Dispose()
  $bDo.Dispose()
  $archFont.Dispose()
  $doFont.Dispose()
  Save-Png $c $path
}

function Draw-Concept03($path) {
  $c = New-Canvas 820 430
  $g = $c.Graphics
  $archFont = Font "Bahnschrift" 72 ([System.Drawing.FontStyle]::Regular)
  $doFont = Font "Bahnschrift" 64 ([System.Drawing.FontStyle]::Regular)
  $bArch = Brush $colors.offWhite
  $bDo = Brush $colors.muted
  Draw-Text $g "arch" $archFont $bArch 118 106
  Draw-Text $g "do" $doFont $bDo 250 202
  Draw-X $g 350 78 224 270 32 $colors.offWhite $colors.deepTeal
  $p = PenOf ([System.Drawing.Color]::FromArgb(190, $colors.deepTeal)) 3
  $p.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $p.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat
  $g.DrawLine($p, 118, 214, 330, 214)
  $g.DrawLine($p, 118, 232, 210, 232)
  $dot = Brush $colors.bronze
  $g.FillRectangle($dot, 332, 207, 13, 13)
  $p.Dispose()
  $dot.Dispose()
  $bArch.Dispose()
  $bDo.Dispose()
  $archFont.Dispose()
  $doFont.Dispose()
  Save-Png $c $path
}

function Render-Preview {
  $c = New-Canvas 1500 980 $colors.dark
  $g = $c.Graphics
  $titleFont = Font "Segoe UI" 40 ([System.Drawing.FontStyle]::Bold)
  $bodyFont = Font "Segoe UI" 23 ([System.Drawing.FontStyle]::Regular)
  $title = Brush $colors.offWhite
  $body = Brush $colors.teal
  Draw-Text $g "arch / do / X stacked concepts" $titleFont $title 72 54
  Draw-Text $g "Transparent PNG concepts. X is intentionally close, not detached." $bodyFont $body 76 108

  $items = @(
    @{ file = "archdox-stacked-x-v1.png"; label = "01 lowercase, calm premium" },
    @{ file = "archdox-stacked-x-v2.png"; label = "02 closer to your Arch reference" },
    @{ file = "archdox-stacked-x-v3.png"; label = "03 sharper engineering tone" }
  )

  $y = 165
  foreach ($item in $items) {
    $panel = Brush $colors.panel
    $g.FillRectangle($panel, 70, $y, 1360, 230)
    $panel.Dispose()
    Draw-Text $g $item.label $bodyFont $body 98 ($y + 22)
    $img = [System.Drawing.Image]::FromFile((Join-Path $pngDir $item.file))
    $scale = [Math]::Min(([double]900 / $img.Width), ([double]160 / $img.Height))
    $drawW = [int]($img.Width * $scale)
    $drawH = [int]($img.Height * $scale)
    $g.DrawImage($img, 318, ($y + 45), $drawW, $drawH)
    $img.Dispose()
    $y += 265
  }

  $title.Dispose()
  $body.Dispose()
  $titleFont.Dispose()
  $bodyFont.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-stacked-x-preview.png")
}

Draw-Concept01 (Join-Path $pngDir "archdox-stacked-x-v1.png")
Draw-Concept02 (Join-Path $pngDir "archdox-stacked-x-v2.png")
Draw-Concept03 (Join-Path $pngDir "archdox-stacked-x-v3.png")
Render-Preview

Write-Host "Generated stacked X concepts in $pngDir"
