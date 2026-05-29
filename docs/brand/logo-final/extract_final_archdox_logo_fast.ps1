param(
  [string]$Source = "C:\Users\SBPark\.codex\generated_images\019e742a-2830-7c11-aa48-3ec166d7fa22\ig_09311c01d13b92bb016a19a5e58d9081919b43ed62b7b10358.png",
  [string]$OutDir = "."
)

Add-Type -AssemblyName System.Drawing

$root = Resolve-Path $OutDir
$pngDir = Join-Path $root "png"
New-Item -ItemType Directory -Force -Path $pngDir | Out-Null

$code = @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

public static class ArchdoxFinalLogoExtractor
{
    static double Luma(byte r, byte g, byte b)
    {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    static bool IsBronze(byte r, byte g, byte b)
    {
        return r > 100 && g > 70 && g < 180 && b < 95;
    }

    static bool IsTeal(byte r, byte g, byte b)
    {
        return (g > 64 && b > 58 && r < 170 && ((g - r) > 10 || (b - r) > 10)) ||
               (g > 110 && b > 105 && r < 150);
    }

    static byte LogoAlpha(byte r, byte g, byte b)
    {
        double l = Luma(r, g, b);
        bool teal = IsTeal(r, g, b);
        bool bronze = IsBronze(r, g, b);
        bool candidate = l > 48 || teal || bronze;
        if (!candidate) return 0;

        int a = (int)((l - 48) * 8.0);
        if (teal)
        {
            double tealMetric = ((g + b) / 2.0) - 68;
            a = Math.Max(a, (int)(tealMetric * 6.0));
        }
        if (bronze)
        {
            a = Math.Max(a, (int)((r - 96) * 5.8));
        }
        if (a < 24) return 0;
        if (a > 232) return 255;
        return (byte)Math.Max(0, Math.Min(255, a));
    }

    static Color LightColor(byte r, byte g, byte b, int globalX, int globalY)
    {
        Color ink = Color.FromArgb(22, 27, 31);
        Color deepTeal = Color.FromArgb(10, 78, 91);
        Color lineTeal = Color.FromArgb(48, 88, 99);
        Color paper = Color.FromArgb(226, 234, 238);
        Color fold = Color.FromArgb(142, 156, 166);
        Color bronze = Color.FromArgb(184, 135, 47);

        if (IsBronze(r, g, b)) return bronze;

        bool inDoor = globalX >= 380 && globalX <= 584 && globalY >= 245 && globalY <= 520;
        bool inGuide = globalY > 526;
        double l = Luma(r, g, b);

        if (inDoor)
        {
            if (l > 176 && !IsTeal(r, g, b)) return paper;
            if (r > 95 && g > 100 && b > 108 && !IsTeal(r, g, b)) return fold;
            if (IsTeal(r, g, b)) return deepTeal;
            return lineTeal;
        }

        if (inGuide)
        {
            if (IsTeal(r, g, b)) return lineTeal;
            if (l > 58) return Color.FromArgb(55, 73, 75);
        }

        if (IsTeal(r, g, b)) return deepTeal;
        return ink;
    }

    public static void Extract(string sourcePath, string pngDir)
    {
        using (Bitmap original = (Bitmap)Image.FromFile(sourcePath))
        using (Bitmap src = new Bitmap(original.Width, original.Height, PixelFormat.Format32bppArgb))
        {
            using (Graphics g = Graphics.FromImage(src))
            {
                g.DrawImage(original, 0, 0, original.Width, original.Height);
            }

            Rectangle srcRect = new Rectangle(0, 0, src.Width, src.Height);
            BitmapData data = src.LockBits(srcRect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            int stride = data.Stride;
            byte[] pixels = new byte[stride * src.Height];
            Marshal.Copy(data.Scan0, pixels, 0, pixels.Length);
            src.UnlockBits(data);

            int minX = src.Width, minY = src.Height, maxX = 0, maxY = 0;
            int yMax = Math.Min(src.Height, 585);
            for (int y = 230; y < yMax; y++)
            {
                int row = y * stride;
                for (int x = 0; x < src.Width; x++)
                {
                    int i = row + x * 4;
                    byte b = pixels[i], g = pixels[i + 1], r = pixels[i + 2];
                    byte a = LogoAlpha(r, g, b);
                    if (a > 42)
                    {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            int padX = 52, padTop = 44, padBottom = 38;
            int cropX = Math.Max(0, minX - padX);
            int cropY = Math.Max(0, minY - padTop);
            int cropW = Math.Min(src.Width - cropX, (maxX - minX + 1) + padX * 2);
            int cropH = Math.Min(src.Height - cropY, (maxY - minY + 1) + padTop + padBottom);

            Directory.CreateDirectory(pngDir);
            BuildLogo(pixels, stride, cropX, cropY, cropW, cropH, true, Path.Combine(pngDir, "archdox-logo-dark-bg.png"));
            BuildLogo(pixels, stride, cropX, cropY, cropW, cropH, false, Path.Combine(pngDir, "archdox-logo-light-bg.png"));
            RenderPreview(Path.Combine(pngDir, "archdox-logo-dark-bg.png"), Path.Combine(pngDir, "archdox-logo-light-bg.png"), Path.Combine(pngDir, "archdox-final-logo-preview.png"));
        }
    }

    static void BuildLogo(byte[] source, int srcStride, int cropX, int cropY, int cropW, int cropH, bool darkMode, string outPath)
    {
        using (Bitmap outBmp = new Bitmap(cropW, cropH, PixelFormat.Format32bppArgb))
        {
            Rectangle rect = new Rectangle(0, 0, cropW, cropH);
            BitmapData data = outBmp.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            int outStride = data.Stride;
            byte[] outPixels = new byte[outStride * cropH];

            for (int y = 0; y < cropH; y++)
            {
                int sy = cropY + y;
                int sourceRow = sy * srcStride;
                int outRow = y * outStride;
                for (int x = 0; x < cropW; x++)
                {
                    int sx = cropX + x;
                    int si = sourceRow + sx * 4;
                    byte b = source[si], g = source[si + 1], r = source[si + 2];
                    byte a = LogoAlpha(r, g, b);
                    int oi = outRow + x * 4;
                    if (a == 0)
                    {
                        outPixels[oi] = 0;
                        outPixels[oi + 1] = 0;
                        outPixels[oi + 2] = 0;
                        outPixels[oi + 3] = 0;
                        continue;
                    }

                    Color c;
                    if (darkMode)
                    {
                        c = Color.FromArgb(r, g, b);
                    }
                    else
                    {
                        c = LightColor(r, g, b, sx, sy);
                    }
                    outPixels[oi] = c.B;
                    outPixels[oi + 1] = c.G;
                    outPixels[oi + 2] = c.R;
                    outPixels[oi + 3] = a;
                }
            }

            Marshal.Copy(outPixels, 0, data.Scan0, outPixels.Length);
            outBmp.UnlockBits(data);
            outBmp.Save(outPath, ImageFormat.Png);
        }
    }

    static void RenderPreview(string darkPath, string lightPath, string previewPath)
    {
        using (Bitmap canvas = new Bitmap(1500, 780, PixelFormat.Format32bppArgb))
        using (Graphics gr = Graphics.FromImage(canvas))
        using (Font title = new Font("Segoe UI", 36, FontStyle.Bold, GraphicsUnit.Pixel))
        using (Font body = new Font("Segoe UI", 22, FontStyle.Regular, GraphicsUnit.Pixel))
        using (Brush white = new SolidBrush(Color.FromArgb(244, 248, 246)))
        using (Brush mint = new SolidBrush(Color.FromArgb(143, 214, 200)))
        using (Brush darkPanel = new SolidBrush(Color.FromArgb(8, 28, 25)))
        using (Brush lightPanel = new SolidBrush(Color.FromArgb(244, 248, 246)))
        using (Brush labelLight = new SolidBrush(Color.FromArgb(10, 78, 91)))
        using (Image darkLogo = Image.FromFile(darkPath))
        using (Image lightLogo = Image.FromFile(lightPath))
        {
            gr.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            gr.Clear(Color.FromArgb(17, 33, 31));
            gr.DrawString("archdox final PNG logos", title, white, 70, 52);
            gr.DrawString("Preview panels only. The two logo PNG files have transparent backgrounds.", body, mint, 72, 104);
            gr.FillRectangle(darkPanel, 70, 165, 1360, 235);
            gr.FillRectangle(lightPanel, 70, 465, 1360, 235);
            double scale = Math.Min(1040.0 / darkLogo.Width, 160.0 / darkLogo.Height);
            int drawW = (int)(darkLogo.Width * scale);
            int drawH = (int)(darkLogo.Height * scale);
            int x = 70 + (1360 - drawW) / 2;
            gr.DrawImage(darkLogo, x, 205, drawW, drawH);
            gr.DrawImage(lightLogo, x, 505, drawW, drawH);
            gr.DrawString("dark background version", body, mint, 95, 185);
            gr.DrawString("light background version", body, labelLight, 95, 485);
            canvas.Save(previewPath, ImageFormat.Png);
        }
    }
}
"@

Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing
[ArchdoxFinalLogoExtractor]::Extract($Source, $pngDir)

Write-Host "Generated final PNG logos in $pngDir"
