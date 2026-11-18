package app;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import shared.Fonts;
import shared.Theme;

/** Run this file to start Mini OS. Shows a 1-second pixel boot splash, then opens the desktop. */
public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(Main::showSplashAndLaunch);
    }

    private static void showSplashAndLaunch() {
        JWindow splash = new JWindow();
        splash.setBackground(new Color(0, 0, 0, 0));

        BufferedImage heron = null;
        try { heron = ImageIO.read(Fonts.file("assets/images/heron_full@3x.png")); } catch (Exception ignored) {}
        final BufferedImage mascot = heron;

        final double[] progress = {0.0};
        JPanel card = new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                RoundRectangle2D box = new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 28, 28);
                g.setPaint(new GradientPaint(0, 0, Theme.bg(), 0, h, Theme.panel2()));
                g.fill(box);
                g.setColor(Theme.pink());
                g.setStroke(new BasicStroke(2.5f));
                g.draw(box);

                int y = 24;
                if (mascot != null) {
                    int mh = 76, mw = mh * mascot.getWidth() / mascot.getHeight();
                    g.drawImage(mascot, (w - mw) / 2, y, mw, mh, null);
                    y += mh + 10;
                }

                g.setFont(Fonts.pixel(28f));
                g.setColor(Theme.text());
                String title = "MINI OS";
                int tw = g.getFontMetrics().stringWidth(title);
                g.drawString(title, (w - tw) / 2, y + g.getFontMetrics().getAscent());
                y += g.getFontMetrics().getHeight() + 4;

                g.setFont(Fonts.arcade(8f));
                g.setColor(Theme.pink());
                String sub = "UMAK OS LAB SIMULATOR";
                int sw = g.getFontMetrics().stringWidth(sub);
                g.drawString(sub, (w - sw) / 2, y + g.getFontMetrics().getAscent());
                y += 26;

                // Progress bar
                int barW = w - 80, barH = 14, barX = 40;
                RoundRectangle2D track = new RoundRectangle2D.Double(barX, y, barW, barH, 12, 12);
                g.setColor(Theme.panel());
                g.fill(track);
                g.setColor(Theme.inputLine());
                g.setStroke(new BasicStroke(1.5f));
                g.draw(track);

                int fillW = (int) Math.round((barW - 4) * Math.min(1.0, progress[0]));
                if (fillW > 4) {
                    RoundRectangle2D fill = new RoundRectangle2D.Double(barX + 2, y + 2, fillW, barH - 4, 10, 10);
                    g.setPaint(new GradientPaint(barX, y, Theme.pink(), barX + barW, y, Theme.green()));
                    g.fill(fill);
                }

                g.setFont(Fonts.body(11f));
                g.setColor(Theme.muted());
                String pct = "Booting... " + (int) Math.round(progress[0] * 100) + "%";
                int pw = g.getFontMetrics().stringWidth(pct);
                g.drawString(pct, (w - pw) / 2, y + barH + 18);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 12, 12, 12));
        card.setPreferredSize(new Dimension(380, 235));
        splash.setContentPane(card);
        splash.pack();
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        new Timer(22, e -> {
            progress[0] += 0.025;
            if (progress[0] >= 1.0) {
                progress[0] = 1.0;
                ((Timer) e.getSource()).stop();
                card.repaint();
                MiniOS os = new MiniOS();
                os.setBackground(Theme.bg());
                os.getContentPane().setBackground(Theme.bg());
                os.setExtendedState(os.getExtendedState() | JFrame.MAXIMIZED_BOTH);
                splash.dispose();
                os.setVisible(true);
                os.toFront();
                os.requestFocus();
            } else {
                card.repaint();
            }
        }).start();
    }
}
