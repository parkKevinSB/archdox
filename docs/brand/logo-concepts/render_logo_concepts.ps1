param(
  [string]$OutDir = "."
)

Add-Type -AssemblyName System.Drawing

$root = Resolve-Path $OutDir
$pngDir = Join-Path $root "png"
$svgDir = Join-Path $root "svg"
New-Item -ItemType Directory -Force -Path $pngDir, $svgDir | Out-Null

$colors = @{
  offWhite = [System.Drawing.Color]::FromArgb(255, 244, 248, 246)
  mint = [System.Drawing.Color]::FromArgb(255, 143, 214, 200)
  teal = [System.Drawing.Color]::FromArgb(255, 30, 104, 88)
  dark = [System.Drawing.Color]::FromArgb(255, 17, 33, 31)
  bronze = [System.Drawing.Color]::FromArgb(255, 184, 135, 47)
  muted = [System.Drawing.Color]::FromArgb(255, 112, 142, 136)
}

function New-Canvas($w, $h, [bool]$transparent = $true) {
  $bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
  if ($transparent) {
    $g.Clear([System.Drawing.Color]::Transparent)
  } else {
    $g.Clear($colors.dark)
  }
  return @{ Bitmap = $bmp; Graphics = $g }
}

function Save-Png($canvas, $path) {
  $canvas.Graphics.Dispose()
  $canvas.Bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $canvas.Bitmap.Dispose()
}

function Font($family, $size, $style = [System.Drawing.FontStyle]::Regular) {
  if ($null -eq $style) {
    $style = [System.Drawing.FontStyle]::Regular
  }
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

function Draw-ArchA($g, [float]$x, [float]$y, [float]$w, [float]$h, [float]$stroke, $main, $accent, [bool]$axis = $true) {
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $left = $x + ($stroke / 2)
  $right = $x + $w - ($stroke / 2)
  $bottom = $y + $h
  $top = $y + ($stroke / 2)
  $mid = $x + ($w / 2)
  $controlY = $y - ($h * 0.02)
  $path.StartFigure()
  $path.AddLine($left, $bottom, $left, $y + ($h * 0.48))
  $path.AddBezier($left, $y + ($h * 0.48), $left, $controlY, $mid, $top, $mid, $top)
  $path.AddBezier($mid, $top, $right, $controlY, $right, $y + ($h * 0.48), $right, $y + ($h * 0.48))
  $path.AddLine($right, $y + ($h * 0.48), $right, $bottom)
  $g.DrawPath((PenOf $main $stroke), $path)
  $path.Dispose()

  if ($axis) {
    $axisPen = PenOf ([System.Drawing.Color]::FromArgb(190, $main)) ([Math]::Max(2, $stroke * 0.08))
    $gx = $x + ($w * 0.31)
    $gy = $y + ($h * 0.82)
    $g.DrawLine($axisPen, $gx, $gy - ($h * 0.36), $gx, $gy + ($h * 0.12))
    $g.DrawLine($axisPen, $gx - ($w * 0.22), $gy, $gx + ($w * 0.36), $gy)
    $axisPen.Dispose()

    $b = Brush $accent
    $s = [Math]::Max(8, $stroke * 0.48)
    $g.FillRectangle($b, $gx - ($s / 2), $gy - ($s / 2), $s, $s)
    $b.Dispose()
  }
}

function Draw-CustomX($g, [float]$x, [float]$y, [float]$w, [float]$h, [float]$stroke, $leftColor, $rightColor) {
  $p1 = PenOf $leftColor $stroke
  $p2 = PenOf $rightColor $stroke
  $p1.StartCap = [System.Drawing.Drawing2D.LineCap]::Square
  $p1.EndCap = [System.Drawing.Drawing2D.LineCap]::Square
  $p2.StartCap = [System.Drawing.Drawing2D.LineCap]::Square
  $p2.EndCap = [System.Drawing.Drawing2D.LineCap]::Square
  $g.DrawLine($p1, $x, $y, $x + $w, $y + $h)
  $g.DrawLine($p2, $x + $w, $y, $x, $y + $h)
  $p1.Dispose()
  $p2.Dispose()
}

function Measure($g, $text, $font) {
  return $g.MeasureString($text, $font)
}

function Draw-Text($g, $text, $font, $brush, [float]$x, [float]$y) {
  $fmt = New-Object System.Drawing.StringFormat
  $fmt.FormatFlags = [System.Drawing.StringFormatFlags]::NoClip
  $g.DrawString($text, $font, $brush, $x, $y, $fmt)
  $fmt.Dispose()
}

function Write-SvgConcept($name, $body, $width = 1400, $height = 360) {
  $svg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">
  <title>archdox $name logo concept</title>
  $body
</svg>
"@
  Set-Content -Path (Join-Path $svgDir "$name.svg") -Value $svg -Encoding UTF8
}

function Render-V1 {
  $c = New-Canvas 1020 260
  $g = $c.Graphics
  $font = Font "Segoe UI" 122 ([System.Drawing.FontStyle]::Bold)
  Draw-ArchA $g 74 56 125 132 22 $colors.mint $colors.bronze $true
  $b = Brush $colors.offWhite
  Draw-Text $g "rchdo" $font $b 214 52
  Draw-CustomX $g 724 64 112 124 25 $colors.offWhite $colors.mint
  $b.Dispose()
  $font.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-v1-integrated-big-x.png")
  Write-SvgConcept "archdox-v1-integrated-big-x" @'
<g fill="none" stroke-linecap="square" stroke-linejoin="round">
  <path d="M85 188V119C85 68 121 45 137 45C153 45 188 68 188 119V188" stroke="#8fd6c8" stroke-width="22"/>
  <path d="M113 123V200M85 188H158" stroke="#8fd6c8" stroke-width="3" opacity=".72"/>
  <rect x="108" y="181" width="11" height="11" fill="#b8872f" stroke="none"/>
  <text x="214" y="167" fill="#f4f8f6" font-family="Segoe UI, Arial, sans-serif" font-size="122" font-weight="700">rchdo</text>
  <path d="M724 64L836 188" stroke="#f4f8f6" stroke-width="25"/>
  <path d="M836 64L724 188" stroke="#8fd6c8" stroke-width="25"/>
</g>
'@ 1020 260
}

function Render-V2 {
  $c = New-Canvas 980 300
  $g = $c.Graphics
  $font = Font "Segoe UI" 112 ([System.Drawing.FontStyle]::Bold)
  $b = Brush $colors.offWhite
  Draw-Text $g "archdo" $font $b 78 72
  Draw-CustomX $g 590 42 238 240 34 $colors.offWhite $colors.mint
  $axisPen = PenOf $colors.mint 3
  $axisPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
  $g.DrawLine($axisPen, 600, 270, 850, 270)
  $axisPen.Dispose()
  $dot = Brush $colors.bronze
  $g.FillEllipse($dot, 704, 263, 14, 14)
  $dot.Dispose()
  $b.Dispose()
  $font.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-v2-offset-structural-x.png")
  Write-SvgConcept "archdox-v2-offset-structural-x" @'
<g fill="none" stroke-linecap="square" stroke-linejoin="round">
  <text x="78" y="178" fill="#f4f8f6" font-family="Segoe UI, Arial, sans-serif" font-size="112" font-weight="700">archdo</text>
  <path d="M590 42L828 282" stroke="#f4f8f6" stroke-width="34"/>
  <path d="M828 42L590 282" stroke="#8fd6c8" stroke-width="34"/>
  <path d="M600 270H850" stroke="#8fd6c8" stroke-width="3" stroke-dasharray="9 9" opacity=".7"/>
  <circle cx="711" cy="270" r="7" fill="#b8872f" stroke="none"/>
</g>
'@ 980 300
}

function Render-V3 {
  $c = New-Canvas 760 390
  $g = $c.Graphics
  $fontArch = Font "Segoe UI" 104 ([System.Drawing.FontStyle]::Bold)
  $fontDo = Font "Segoe UI" 62 ([System.Drawing.FontStyle]::Bold)
  $b = Brush $colors.offWhite
  Draw-Text $g "arch" $fontArch $b 78 68
  Draw-Text $g "do" $fontDo $b 246 184
  Draw-CustomX $g 398 70 220 246 36 $colors.offWhite $colors.mint
  $p = PenOf $colors.mint 4
  $g.DrawLine($p, 78, 196, 348, 196)
  $g.DrawLine($p, 78, 216, 222, 216)
  $p.Dispose()
  $dot = Brush $colors.bronze
  $g.FillRectangle($dot, 358, 188, 15, 15)
  $dot.Dispose()
  $b.Dispose()
  $fontArch.Dispose()
  $fontDo.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-v3-asymmetric-initial-layout.png")
  Write-SvgConcept "archdox-v3-asymmetric-initial-layout" @'
<g fill="none" stroke-linecap="square" stroke-linejoin="round">
  <text x="78" y="166" fill="#f4f8f6" font-family="Segoe UI, Arial, sans-serif" font-size="104" font-weight="700">arch</text>
  <text x="246" y="244" fill="#f4f8f6" font-family="Segoe UI, Arial, sans-serif" font-size="62" font-weight="700">do</text>
  <path d="M398 70L618 316" stroke="#f4f8f6" stroke-width="36"/>
  <path d="M618 70L398 316" stroke="#8fd6c8" stroke-width="36"/>
  <path d="M78 196H348M78 216H222" stroke="#8fd6c8" stroke-width="4" opacity=".72"/>
  <rect x="358" y="188" width="15" height="15" fill="#b8872f" stroke="none"/>
</g>
'@ 760 390
}

function Render-V4 {
  $c = New-Canvas 720 210
  $g = $c.Graphics
  Draw-ArchA $g 58 48 78 86 14 $colors.mint $colors.bronze $true
  $font = Font "Segoe UI" 68 ([System.Drawing.FontStyle]::Bold)
  $b = Brush $colors.offWhite
  Draw-Text $g "archdo" $font $b 160 52
  Draw-CustomX $g 515 64 62 72 16 $colors.offWhite $colors.mint
  $b.Dispose()
  $font.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-v4-sidebar-lockup.png")
  Write-SvgConcept "archdox-v4-sidebar-lockup" @'
<g fill="none" stroke-linecap="square" stroke-linejoin="round">
  <path d="M65 134V91C65 56 92 45 97 45C102 45 128 56 128 91V134" stroke="#8fd6c8" stroke-width="14"/>
  <path d="M82 96V145M65 134H112" stroke="#8fd6c8" stroke-width="2" opacity=".72"/>
  <rect x="79" y="129" width="7" height="7" fill="#b8872f" stroke="none"/>
  <text x="160" y="116" fill="#f4f8f6" font-family="Segoe UI, Arial, sans-serif" font-size="68" font-weight="700">archdo</text>
  <path d="M515 64L577 136" stroke="#f4f8f6" stroke-width="16"/>
  <path d="M577 64L515 136" stroke="#8fd6c8" stroke-width="16"/>
</g>
'@ 720 210
}

function Render-V5 {
  $c = New-Canvas 780 230
  $g = $c.Graphics
  $font = Font "Segoe UI" 96 ([System.Drawing.FontStyle]::Bold)
  $darkBrush = Brush $colors.dark
  Draw-Text $g "archdo" $font $darkBrush 68 64
  Draw-CustomX $g 542 74 98 112 22 $colors.teal $colors.dark
  $p = PenOf $colors.teal 3
  $g.DrawLine($p, 70, 188, 340, 188)
  $g.DrawLine($p, 652, 188, 726, 188)
  $p.Dispose()
  $dot = Brush $colors.bronze
  $g.FillRectangle($dot, 360, 181, 13, 13)
  $dot.Dispose()
  $darkBrush.Dispose()
  $font.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-v5-light-surface.png")
  Write-SvgConcept "archdox-v5-light-surface" @'
<g fill="none" stroke-linecap="square" stroke-linejoin="round">
  <text x="68" y="154" fill="#11211f" font-family="Segoe UI, Arial, sans-serif" font-size="96" font-weight="700">archdo</text>
  <path d="M542 74L640 186" stroke="#1e6858" stroke-width="22"/>
  <path d="M640 74L542 186" stroke="#11211f" stroke-width="22"/>
  <path d="M70 188H340M652 188H726" stroke="#1e6858" stroke-width="3"/>
  <rect x="360" y="181" width="13" height="13" fill="#b8872f" stroke="none"/>
</g>
'@ 780 230
}

function Render-PreviewBoard {
  $c = New-Canvas 1600 1320 $false
  $g = $c.Graphics
  $titleFont = Font "Segoe UI" 42 ([System.Drawing.FontStyle]::Bold)
  $labelFont = Font "Segoe UI" 24 ([System.Drawing.FontStyle]::Regular)
  $titleBrush = Brush $colors.offWhite
  $labelBrush = Brush $colors.mint
  Draw-Text $g "archdox logo concepts - transparent PNG set" $titleFont $titleBrush 78 54
  Draw-Text $g "Preview board only has a dark background. Individual PNG files are transparent." $labelFont $labelBrush 82 108

  $items = @(
    @{ file = "archdox-v1-integrated-big-x.png"; label = "01 integrated a + larger x"; surface = "dark" },
    @{ file = "archdox-v2-offset-structural-x.png"; label = "02 user-layout inspired, strong offset x"; surface = "dark" },
    @{ file = "archdox-v3-asymmetric-initial-layout.png"; label = "03 asymmetric Arch / do / X composition"; surface = "dark" },
    @{ file = "archdox-v4-sidebar-lockup.png"; label = "04 compact product-sidebar lockup"; surface = "dark" },
    @{ file = "archdox-v5-light-surface.png"; label = "05 light-surface version"; surface = "light" }
  )
  $y = 182
  foreach ($item in $items) {
    if ($item.surface -eq "light") {
      $rectBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 244, 248, 246))
    } else {
      $rectBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 22, 42, 39))
    }
    $rect = New-Object System.Drawing.RectangleF(70, $y, 1460, 180)
    $g.FillRectangle($rectBrush, $rect)
    $rectBrush.Dispose()
    if ($item.surface -eq "light") {
      $labelOnSurface = Brush $colors.teal
    } else {
      $labelOnSurface = Brush $colors.mint
    }
    Draw-Text $g $item.label $labelFont $labelOnSurface 96 ($y + 20)
    $labelOnSurface.Dispose()
    $img = [System.Drawing.Image]::FromFile((Join-Path $pngDir $item.file))
    $scale = [Math]::Min(1.0, [Math]::Min(([double]980 / [double]$img.Width), ([double]118 / [double]$img.Height)))
    $w = [int]($img.Width * $scale)
    $h = [int]($img.Height * $scale)
    $g.DrawImage($img, 96, $y + 56, $w, $h)
    $img.Dispose()
    $y += 212
  }
  $titleBrush.Dispose()
  $labelBrush.Dispose()
  $titleFont.Dispose()
  $labelFont.Dispose()
  Save-Png $c (Join-Path $pngDir "archdox-logo-concepts-preview-board.png")
}

Render-V1
Render-V2
Render-V3
Render-V4
Render-V5
Render-PreviewBoard

Write-Host "Generated logo concepts in $root"
