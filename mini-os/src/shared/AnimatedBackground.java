package shared;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * UMak pixel-art desktop with a jeepney driving across the road.
 * Morning scene in light mode, night scene in dark mode. OWNER: Member 3.
 */
public class AnimatedBackground extends JPanel {
    private static final int BASE_W = 1280, BASE_H = 1080, JEEP_Y = 892, SPEED = 3;
    private BufferedImage dayBg, nightBg, dayJeep, nightJeep;
    private double jeepX = BASE_W;

    public AnimatedBackground() {
        setLayout(null);
        try {
            dayBg = ImageIO.read(new File("assets/images/wallpaper_morning.png"));
            nightBg = ImageIO.read(new File("assets/images/wallpaper_night.png"));
            dayJeep = ImageIO.read(new File("assets/images/jeep_morning.png"));
            nightJeep = ImageIO.read(new File("assets/images/jeep_night.png"));
        } catch (IOException e) {
            throw new RuntimeException("Run the app from the project root so /assets can be found", e);
        }
        new Timer(16, e -> {
            jeepX -= SPEED;
            if (jeepX < -jeep().getWidth()) jeepX = BASE_W;
            repaint();
        }).start();
    }

    private BufferedImage jeep() { return Theme.dark ? nightJeep : dayJeep; }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // scale to cover the window, keep pixels crisp
        double s = Math.max(getWidth() / (double) BASE_W, getHeight() / (double) BASE_H);
        g2.translate((getWidth() - BASE_W * s) / 2, (getHeight() - BASE_H * s) / 2);
        g2.scale(s, s);
        g2.drawImage(Theme.dark ? nightBg : dayBg, 0, 0, BASE_W, BASE_H, null);
        g2.drawImage(jeep(), (int) jeepX, JEEP_Y, null);
        g2.dispose();
    }
}
