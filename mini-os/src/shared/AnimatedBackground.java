package shared;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

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

    public AnimatedBackground() {
        setLayout(null);
        try {
            dayBg = ImageIO.read(new File("assets/images/wallpaper_umak_morning.png"));
            nightBg = ImageIO.read(new File("assets/images/wallpaper_umak_night.png"));
        } catch (IOException e) {
            throw new RuntimeException("Run the app from the project root so /assets can be found", e);
        }
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
        g2.dispose();
    }

    private static void strip(Graphics2D g2, BufferedImage bg, double x1, double x2, int h, int from, int to, boolean mirror) {
        int dx1 = (int) Math.round(x1), dx2 = (int) Math.round(x2);
        if (mirror) g2.drawImage(bg, dx1, 0, dx2, h, to, 0, from, BASE_H, null);
        else        g2.drawImage(bg, dx1, 0, dx2, h, from, 0, to, BASE_H, null);
    }
}
