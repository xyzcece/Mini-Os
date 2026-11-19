package shared;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * UMak pixel-art desktop (the jeepney is part of the wallpaper art).
 * Morning scene in light mode, night scene in dark mode. OWNER: Member 3.
 *
 * Like the Figma desktop frames, the whole 1280x1080 scene is shown, from the sky and seal down to the road,
 * scaled to the height above the taskbar. On a wider screen the scene continues to the sides by mirroring
 * the outer edge strips of the wallpaper (strips chosen to contain no jeep or lamp post).
 */
public class AnimatedBackground extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int BASE_W = 1280, BASE_H = 1080;
    /**
     * Edge strips of the wallpaper used to extend it sideways: [from, to) in wallpaper pixels.
     * Left stops before the main building; right starts after the building and the sun / moon.
     */
    private static final int LEFT_FROM = 0, LEFT_TO = 165, RIGHT_FROM = 1170, RIGHT_TO = 1280;
    private transient BufferedImage dayBg, nightBg;
    private transient BufferedImage[] nightJeeps, dayJeeps;
    private double jeepX = BASE_W + 120, trikeX = BASE_W + 620;
    private int jeepVar = 1;
    private boolean rainy = false;
    private double tick = 0;
    private final Rectangle jeepBounds = new Rectangle(), trikeBounds = new Rectangle();
    private int jeepBoostTicks = 0, trikeBoostTicks = 0;
    private int jeepBubbleTicks = 0, trikeBubbleTicks = 0;
    private String jeepBubbleText = "Beep beep!", trikeBubbleText = "Kring kring!";
    private int bubbleCycle = 0;

    public AnimatedBackground() {
        setLayout(null);
        dayBg = Fonts.image("assets/images/wallpaper_umak_morning.png");
        nightBg = Fonts.image("assets/images/wallpaper_umak_night.png");
        BufferedImage baseNight = Fonts.image("assets/images/jeep_night.png");
        if (baseNight != null) {
            nightJeeps = new BufferedImage[]{
                    baseNight,
                    recolorJeep(baseNight, false, 1),
                    recolorJeep(baseNight, false, 2)
            };
            dayJeeps = new BufferedImage[]{
                    recolorJeep(baseNight, true, 0),
                    recolorJeep(baseNight, true, 1),
                    recolorJeep(baseNight, true, 2)
            };
        }

        new Timer(45, e -> {
            tick += 0.08;
            double jeepSpeed = jeepBoostTicks > 0 ? 5.6 : 2.8;
            if (jeepBoostTicks > 0) jeepBoostTicks--;
            double trikeSpeed = trikeBoostTicks > 0 ? 4.4 : 2.0;
            if (trikeBoostTicks > 0) trikeBoostTicks--;

            // Keep a safe following distance so the jeepney and tricycle never overlap in the same lane
            if (jeepX > trikeX && jeepX - trikeX < 195 && jeepSpeed > trikeSpeed) {
                jeepSpeed = trikeSpeed;
            } else if (trikeX > jeepX && trikeX - jeepX < 295 && trikeSpeed > jeepSpeed) {
                trikeSpeed = jeepSpeed;
            }

            jeepX -= jeepSpeed;
            if (jeepX < -240) {
                jeepX = Math.max(BASE_W + 140, trikeX + 260);
                jeepVar = (jeepVar + 1) % 3;
            }
            trikeX -= trikeSpeed;
            if (trikeX < -180) {
                trikeX = Math.max(BASE_W + 260, jeepX + 340);
            }
            if (jeepBubbleTicks > 0) jeepBubbleTicks--;
            if (trikeBubbleTicks > 0) trikeBubbleTicks--;
            repaint();
        }).start();
    }

    /** Handles clicks on the desktop wallpaper; triggers a speed boost + pixel speech bubble on vehicles. */
    public boolean handleDesktopClick(Point p) {
        if (p == null) return false;
        Rectangle jHit = new Rectangle(jeepBounds.x - 10, jeepBounds.y - 10, jeepBounds.width + 20, jeepBounds.height + 20);
        if (jeepBounds.width > 0 && jHit.contains(p)) {
            String[] msgs = {"Beep beep!", "Para po!", "Bayad po!"};
            jeepBubbleText = msgs[bubbleCycle++ % msgs.length];
            jeepBoostTicks = 35;
            jeepBubbleTicks = 50;
            repaint();
            return true;
        }
        Rectangle tHit = new Rectangle(trikeBounds.x - 10, trikeBounds.y - 10, trikeBounds.width + 20, trikeBounds.height + 20);
        if (trikeBounds.width > 0 && tHit.contains(p)) {
            String[] msgs = {"Kring kring!", "Sakay na!", "Tabi lang po!"};
            trikeBubbleText = msgs[bubbleCycle++ % msgs.length];
            trikeBoostTicks = 35;
            trikeBubbleTicks = 50;
            repaint();
            return true;
        }
        return false;
    }

    public boolean isRainy() { return rainy; }
    public void toggleRain() { rainy = !rainy; repaint(); }

    /** Creates daytime & color-variety jeepneys (0 = UMak Red/Gold, 1 = Hyperpuff Pink/Cyan, 2 = Royal Purple/Gold). */
    private static BufferedImage recolorJeep(BufferedImage src, boolean day, int variant) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                if (a == 0) continue;
                int r = (argb >>> 16) & 0xff, g = (argb >>> 8) & 0xff, b = argb & 0xff;
                // Strip the front headlight dither pixels in daytime mode
                if (day && x < w * 0.40 && a < 245) continue;

                // Swap red body pixels (r > g + 35 && r > b + 35) for variety 1 (Pink) or 2 (Purple)
                if (variant == 1 && r > g + 35 && r > b + 35) {
                    int nr = Math.min(255, r + 35);
                    int ng = Math.min(255, g + 30);
                    int nb = Math.min(255, b + 115);
                    r = nr; g = ng; b = nb;
                } else if (variant == 2 && r > g + 35 && r > b + 35) {
                    int nr = Math.min(255, (int) (r * 0.58) + 30);
                    int ng = Math.min(255, g + 15);
                    int nb = Math.min(255, b + 140);
                    r = nr; g = ng; b = nb;
                }

                if (day) {
                    r = Math.min(255, r + 42);
                    g = Math.min(255, g + 42);
                    b = Math.min(255, b + 36);
                }
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /** Height of the taskbar (the component at the bottom of this panel), so the road sits right above it. */
    private int taskbarHeight() {
        if (getLayout() instanceof BorderLayout) {
            Component south = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.SOUTH);
            if (south != null && south.isVisible()) return south.getHeight();
        }
        return 0;
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage bg = Theme.dark ? nightBg : dayBg;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int h = Math.max(1, getHeight() - taskbarHeight());
        double s = h / (double) BASE_H;                       // whole scene top to bottom
        double imgW = BASE_W * s, x0 = (getWidth() - imgW) / 2;
        g2.drawImage(bg, (int) Math.round(x0), 0, (int) Math.round(x0 + imgW), h, 0, 0, BASE_W, BASE_H, null);

        // extend sideways: tiles of the edge strip, mirrored then normal, so every seam lines up
        double leftW = (LEFT_TO - LEFT_FROM) * s, rightW = (RIGHT_TO - RIGHT_FROM) * s;
        boolean mirror = true;
        for (double right = x0; right > 0; right -= leftW, mirror = !mirror)
            strip(g2, bg, right - leftW, right, h, LEFT_FROM, LEFT_TO, mirror);
        mirror = true;
        for (double left = x0 + imgW; left < getWidth(); left += rightW, mirror = !mirror)
            strip(g2, bg, left, left + rightW, h, RIGHT_FROM, RIGHT_TO, mirror);

        // Ambient sky life + driving jeepney in both modes
        if (Theme.dark) {
            int[][] stars = {{120, 72}, {260, 110}, {410, 58}, {680, 92}, {890, 64}, {1040, 118}, {1180, 76}};
            for (int i = 0; i < stars.length; i++) {
                int alpha = 90 + (int) Math.round(130 * (0.5 + 0.5 * Math.sin(tick * 1.4 + i * 1.3)));
                g2.setColor(new Color(255, 248, 210, alpha));
                int sx = (int) Math.round(x0 + stars[i][0] * s), sy = (int) Math.round(stars[i][1] * s);
                int sz = Math.max(2, (int) Math.round(3 * s));
                g2.fillRect(sx, sy, sz, sz);
            }
            // Subtle shooting star cycle
            double shootPhase = (tick * 0.18) % 10.0;
            if (shootPhase < 1.0) {
                int sx = (int) Math.round(x0 + (980 - shootPhase * 340) * s);
                int sy = (int) Math.round((45 + shootPhase * 120) * s);
                g2.setColor(new Color(255, 245, 190, (int) Math.round(220 * (1.0 - shootPhase))));
                g2.setStroke(new BasicStroke(Math.max(1.5f, (float) (2.5 * s))));
                g2.drawLine(sx, sy, sx + (int) Math.round(26 * s), sy - (int) Math.round(9 * s));
            }
            drawCelestial(g2, x0, s, true);
            drawNightCampusLights(g2, x0, s);
            if (nightJeeps != null) {
                drawJeep(g2, nightJeeps[jeepVar], x0, s, jeepX, 952, 0.0, true);
            }
            drawTricycle(g2, x0, s, trikeX, 950, true);
        } else {
            drawCelestial(g2, x0, s, false);
            g2.setColor(new Color(255, 255, 255, 115));
            for (int i = 0; i < 3; i++) {
                double cx = ((tick * (10 + i * 4) + i * 430) % (getWidth() + 180)) - 120;
                int cy = (int) Math.round((56 + i * 42) * s);
                int cw = (int) Math.round((96 - i * 12) * s), ch = Math.max(8, (int) Math.round(16 * s));
                g2.fillRoundRect((int) cx, cy, cw, ch, ch, ch);
            }
            // Tiny pixel birds
            g2.setColor(new Color(0x4a3d5e));
            g2.setStroke(new BasicStroke(Math.max(1.2f, (float) (1.8 * s))));
            for (int b = 0; b < 2; b++) {
                double bx = ((tick * 16 + b * 240) % (getWidth() + 120)) - 60;
                int by = (int) Math.round((110 + b * 24 + Math.sin(tick * 2 + b) * 4) * s);
                int flap = (int) Math.round(Math.sin(tick * 5 + b) * 3 * s);
                int bw = Math.max(4, (int) Math.round(6 * s));
                g2.drawLine((int) bx - bw, by - flap, (int) bx, by);
                g2.drawLine((int) bx, by, (int) bx + bw, by - flap);
            }
            if (dayJeeps != null) {
                drawJeep(g2, dayJeeps[jeepVar], x0, s, jeepX, 952, 0.0, false);
            }
            drawTricycle(g2, x0, s, trikeX, 950, false);
        }

        // Rainy weather overlay
        if (rainy) {
            g2.setColor(Theme.dark ? new Color(10, 16, 38, 60) : new Color(40, 60, 95, 65));
            g2.fillRect(0, 0, getWidth(), h);
            if (Theme.dark) {
                double lp = (tick * 0.14) % 14.0;
                int flashA = (lp < 0.16) ? 44 : (lp > 0.26 && lp < 0.36) ? 28 : 0;
                if (flashA > 0) {
                    g2.setPaint(new GradientPaint(0, 0, new Color(215, 232, 255, flashA),
                            0, (int) (h * 0.65), new Color(215, 232, 255, 0)));
                    g2.fillRect(0, 0, getWidth(), (int) (h * 0.65));
                }
            }
            g2.setColor(new Color(205, 232, 255, Theme.dark ? 130 : 165));
            g2.setStroke(new BasicStroke(Math.max(1.2f, (float) (1.6 * s))));
            for (int i = 0; i < 65; i++) {
                double rx = ((i * 97 + tick * 65) % (getWidth() + 80)) - 40;
                double ry = ((i * 73 + tick * 210) % (h + 40)) - 20;
                int len = Math.max(8, (int) Math.round(14 * s));
                g2.drawLine((int) rx, (int) ry, (int) (rx - len * 0.35), (int) (ry + len));
            }
            // Pixel rain splashes on the road surface
            int roadTop = (int) Math.round(985 * s);
            int roadRange = Math.max(12, h - roadTop - 6);
            for (int i = 0; i < 14; i++) {
                double phase = (tick * 1.8 + i * 0.41) % 1.0;
                int sx = (i * 173 + (int) (tick * 3)) % Math.max(100, getWidth());
                int sy = roadTop + (i * 37) % roadRange;
                int rw = Math.max(4, (int) Math.round((4 + phase * 10) * s));
                int rh = Math.max(2, (int) Math.round((2 + phase * 3) * s));
                int alpha = Math.max(0, (int) Math.round(140 * (1.0 - phase)));
                g2.setColor(new Color(190, 228, 255, alpha));
                g2.drawOval(sx - rw / 2, sy - rh / 2, rw, rh);
            }
        }
        g2.dispose();
    }

    /** Pixel crescent moon (night) or warm pixel sun (day) tucked in the upper-right sky. */
    private void drawCelestial(Graphics2D g2, double x0, double s, boolean night) {
        int cx = (int) Math.round(x0 + 1110 * s);
        int cy = (int) Math.round(92 * s);
        int px = Math.max(3, (int) Math.round(5 * s));
        double pulse = 0.5 + 0.5 * Math.sin(tick * 1.1);
        if (night) {
            int haloR = (int) Math.round((44 + pulse * 6) * s);
            g2.setColor(new Color(255, 240, 185, (int) (20 + pulse * 14)));
            g2.fillOval(cx - haloR, cy - haloR, haloR * 2, haloR * 2);
            int midR = (int) Math.round(28 * s);
            g2.setColor(new Color(255, 244, 200, 38));
            g2.fillOval(cx - midR, cy - midR, midR * 2, midR * 2);
            // Crisp pixel crescent moon
            int[][] cells = {
                    {0, -3}, {1, -3}, {-1, -2}, {0, -2}, {-2, -1}, {-1, -1},
                    {-2, 0}, {-1, 0}, {-2, 1}, {-1, 1}, {-1, 2}, {0, 2}, {0, 3}, {1, 3}
            };
            g2.setColor(new Color(0xfff4c2));
            for (int[] c : cells) {
                g2.fillRect(cx + c[0] * px, cy + c[1] * px, px, px);
            }
        } else {
            int haloR = (int) Math.round((48 + pulse * 8) * s);
            g2.setColor(new Color(255, 226, 122, (int) (28 + pulse * 18)));
            g2.fillOval(cx - haloR, cy - haloR, haloR * 2, haloR * 2);
            // 8 gentle pulsing pixel sun rays
            int rayDist = (int) Math.round((27 + pulse * 4) * s);
            g2.setColor(new Color(255, 220, 110, 185));
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0 + tick * 0.06;
                int rx = cx + (int) Math.round(Math.cos(ang) * rayDist) - px / 2;
                int ry = cy + (int) Math.round(Math.sin(ang) * rayDist) - px / 2;
                g2.fillRect(rx, ry, px, px);
            }
            // Warm pixel sun disc
            int r = px * 3;
            g2.setColor(new Color(0xffe27a));
            g2.fillRect(cx - r, cy - r + px, r * 2, r * 2 - px * 2);
            g2.fillRect(cx - r + px, cy - r, r * 2 - px * 2, r * 2);
            g2.setColor(new Color(0xfff6cc));
            g2.fillRect(cx - px, cy - px, px * 2, px * 2);
        }
    }

    /** Nighttime streetlamp glow, breathing UMak building windows, and floating fireflies. */
    private void drawNightCampusLights(Graphics2D g2, double x0, double s) {
        double breathe = 0.5 + 0.5 * Math.sin(tick * 1.2);

        // 1) Breathing / blinking UMak building windows
        int[][] wins = {{382, 420}, {468, 468}, {556, 396}, {642, 492}, {748, 432}, {824, 480}};
        int ww = Math.max(4, (int) Math.round(12 * s));
        int wh = Math.max(5, (int) Math.round(16 * s));
        for (int i = 0; i < wins.length; i++) {
            double wave = Math.sin(tick * (0.45 + i * 0.11) + i * 2.1);
            if (wave > 0.15) {
                int a = (int) Math.round(Math.min(1.0, (wave - 0.15) * 1.4) * 115);
                int wx = (int) Math.round(x0 + wins[i][0] * s);
                int wy = (int) Math.round(wins[i][1] * s);
                g2.setColor(new Color(255, 224, 130, a));
                g2.fillRect(wx, wy, ww, wh);
            }
        }

        // 2) Warm streetlamp halos along the UMak campus lawn
        int[] lampX = {352, 916};
        int lampY = (int) Math.round(654 * s);
        for (int lxWorld : lampX) {
            int lx = (int) Math.round(x0 + lxWorld * s);
            int rOuter = (int) Math.round((44 + breathe * 6) * s);
            int rMid = (int) Math.round(24 * s);
            int rCore = Math.max(4, (int) Math.round(9 * s));
            g2.setColor(new Color(255, 224, 130, (int) (20 + breathe * 10)));
            g2.fillOval(lx - rOuter, lampY - rOuter, rOuter * 2, rOuter * 2);
            g2.setColor(new Color(255, 232, 155, 42));
            g2.fillOval(lx - rMid, lampY - rMid, rMid * 2, rMid * 2);
            g2.setColor(new Color(255, 246, 200, 90));
            g2.fillOval(lx - rCore, lampY - rCore, rCore * 2, rCore * 2);
        }

        // 3) Floating golden pixel fireflies over the bushes and lawn
        int fsz = Math.max(2, (int) Math.round(3.5 * s));
        for (int i = 0; i < 8; i++) {
            double fxW = 240 + ((i * 123 + Math.sin(tick * 0.5 + i * 1.4) * 38) % 820);
            double fyW = 770 + (i % 3) * 28 + Math.cos(tick * 0.8 + i * 1.9) * 14;
            double glow = 0.5 + 0.5 * Math.sin(tick * 1.8 + i * 1.7);
            int fx = (int) Math.round(x0 + fxW * s);
            int fy = (int) Math.round(fyW * s);
            g2.setColor(new Color(255, 240, 145, (int) (35 * glow)));
            g2.fillOval(fx - fsz * 2, fy - fsz * 2, fsz * 5, fsz * 5);
            g2.setColor(new Color(255, 248, 175, (int) (110 + 145 * glow)));
            g2.fillRect(fx, fy, fsz, fsz);
        }
    }

    /** Pixel-art Philippine tricycle driving along the lower lane (scaled to match jeepney). */
    private void drawTricycle(Graphics2D g2, double x0, double s, double worldX, double worldY, boolean night) {
        double bob = (Math.sin(tick * 8.0 + 1.5) > 0.35) ? -1.5 : 0.0;
        int tx = (int) Math.round(x0 + worldX * s);
        int ty = (int) Math.round((worldY + bob) * s);
        int px = Math.max(4, (int) Math.round(6.5 * s));

        // Canopy / roof (cyan/blue)
        g2.setColor(new Color(0x3fc6e8));
        g2.fillRect(tx + px * 2, ty, px * 11, px * 2);
        // Sidecar body (yellow/gold)
        g2.setColor(new Color(0xffd95e));
        g2.fillRect(tx + px * 2, ty + px * 5, px * 10, px * 5);
        // Sidecar window & support posts
        g2.setColor(new Color(0x2a1a4a));
        g2.fillRect(tx + px * 2, ty + px * 2, px, px * 3);
        g2.fillRect(tx + px * 11, ty + px * 2, px, px * 3);
        g2.setColor(night ? new Color(0xffe27a) : new Color(0xc9f2ff));
        g2.fillRect(tx + px * 3, ty + px * 2, px * 4, px * 3);
        // Front handlebar & rider silhouette
        g2.setColor(new Color(0xff4fa8));
        g2.fillRect(tx + px * 8, ty + px * 2, px * 3, px * 3);
        g2.setColor(new Color(0x3d3150));
        g2.fillRect(tx, ty + px * 6, px * 2, px * 2);
        // Headlight beam & rainy road reflection at night
        if (night) {
            int beamLen = px * 11;
            int hy = ty + px * 6 + px / 2;
            Polygon cone = new Polygon(
                    new int[]{tx, tx - beamLen, tx - beamLen},
                    new int[]{hy, hy - px * 2, hy + px * 3},
                    3);
            g2.setColor(new Color(255, 238, 160, 42));
            g2.fillPolygon(cone);
            if (rainy) {
                int shimmer = (int) (24 + 14 * Math.sin(tick * 3.2));
                g2.setColor(new Color(255, 234, 155, shimmer));
                g2.fillRect(tx - beamLen, ty + px * 10, beamLen + px * 11, Math.max(3, px / 2));
            }
            g2.setColor(new Color(255, 240, 150));
            g2.fillRect(tx, ty + px * 6, px, px);
        }
        // Two wheels
        g2.setColor(new Color(0x1b1726));
        g2.fillOval(tx + px * 1, ty + px * 8, px * 3, px * 3);
        g2.fillOval(tx + px * 9, ty + px * 8, px * 3, px * 3);
        g2.setColor(new Color(0xd8d8e8));
        g2.fillRect(tx + px * 2, ty + px * 9, px, px);
        g2.fillRect(tx + px * 10, ty + px * 9, px, px);

        trikeBounds.setBounds(tx, ty, px * 13, px * 11);
        if (trikeBubbleTicks > 0) {
            drawPixelBubble(g2, trikeBubbleText, tx + px * 6, ty - 6, trikeBubbleTicks);
        }
    }

    private void drawJeep(Graphics2D g2, BufferedImage jeep, double x0, double s, double worldX, double worldY, double phase, boolean night) {
        double bob = (Math.sin(tick * 7.0 + phase) > 0.35) ? -1.5 : 0.0;
        int jx = (int) Math.round(x0 + worldX * s);
        int jy = (int) Math.round((worldY + bob) * s);
        int jw = (int) Math.round(jeep.getWidth() * s);
        int jh = (int) Math.round(jeep.getHeight() * s);

        if (night) {
            int beamLen = (int) Math.round(135 * s);
            int frontX = jx + (int) Math.round(jw * 0.24);
            int hy = jy + (int) Math.round(jh * 0.66);
            Polygon cone = new Polygon(
                    new int[]{frontX, frontX - beamLen, frontX - beamLen},
                    new int[]{hy, hy - (int) Math.round(18 * s), hy + (int) Math.round(28 * s)},
                    3);
            g2.setColor(new Color(255, 238, 160, 42));
            g2.fillPolygon(cone);
            if (rainy) {
                int shimmer = (int) (26 + 14 * Math.sin(tick * 2.8));
                g2.setColor(new Color(255, 234, 155, shimmer));
                g2.fillRect(frontX - beamLen, jy + jh - Math.max(3, (int) Math.round(4 * s)),
                        beamLen + (int) Math.round(jw * 0.68), Math.max(3, (int) Math.round(5 * s)));
            }
        }
        g2.drawImage(jeep, jx, jy, jw, jh, null);
        jeepBounds.setBounds(jx, jy, jw, jh);

        // Subtle pixel exhaust puffs behind the tailpipe
        for (int p = 0; p < 3; p++) {
            double age = (tick * 2.2 + p * 0.33 + phase * 0.2) % 1.0;
            int alpha = Math.max(0, (int) Math.round(110 * (1.0 - age)));
            int px = jx + jw + (int) Math.round((4 + age * 26) * s);
            int py = jy + (int) Math.round((jh * 0.72 - age * 14 * s));
            int psz = Math.max(3, (int) Math.round((4 + age * 4) * s));
            g2.setColor(new Color(240, 240, 255, alpha));
            g2.fillRect(px, py, psz, psz);
        }

        if (jeepBubbleTicks > 0) {
            drawPixelBubble(g2, jeepBubbleText, jx + jw / 2, jy - 6, jeepBubbleTicks);
        }
    }

    private static void drawPixelBubble(Graphics2D g2, String text, int cx, int bottomY, int ticksLeft) {
        float alpha = Math.min(1f, ticksLeft / 10f);
        Graphics2D g = (Graphics2D) g2.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setFont(Fonts.arcade(7f));
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(text) + 18;
        int bh = fm.getHeight() + 12;
        int bx = cx - bw / 2;
        int by = bottomY - bh - 6;

        // Hard pixel drop shadow
        g.setColor(new Color(20, 12, 40, 160));
        g.fillRect(bx + 2, by + 2, bw, bh);
        // Bubble body
        g.setColor(Theme.dark ? new Color(0x261c4a) : Color.WHITE);
        g.fillRect(bx, by, bw, bh);
        // Pink pixel border
        g.setColor(Theme.pink());
        g.fillRect(bx, by, bw, 2);
        g.fillRect(bx, by + bh - 2, bw, 2);
        g.fillRect(bx, by, 2, bh);
        g.fillRect(bx + bw - 2, by, 2, bh);
        // Tail
        g.setColor(Theme.dark ? new Color(0x261c4a) : Color.WHITE);
        g.fillRect(cx - 3, by + bh - 2, 6, 4);
        g.setColor(Theme.pink());
        g.fillRect(cx - 5, by + bh, 2, 2);
        g.fillRect(cx + 3, by + bh, 2, 2);
        g.fillRect(cx - 3, by + bh + 2, 6, 2);

        g.setColor(Theme.dark ? Color.WHITE : new Color(0x3d3150));
        g.drawString(text, bx + 9, by + 6 + fm.getAscent());
        g.dispose();
    }

    private static void strip(Graphics2D g2, BufferedImage bg, double x1, double x2, int h, int from, int to, boolean mirror) {
        int dx1 = (int) Math.round(x1), dx2 = (int) Math.round(x2);
        if (mirror) g2.drawImage(bg, dx1, 0, dx2, h, to, 0, from, BASE_H, null);
        else        g2.drawImage(bg, dx1, 0, dx2, h, from, 0, to, BASE_H, null);
    }
}
