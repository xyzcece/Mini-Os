package modules.common;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import shared.Fonts;
import shared.ModulePanel;
import shared.Theme;

/**
 * Page layout + building blocks from the Figma "mini-os" file, used by Member 1's modules
 * (CPU Scheduling, Segmentation). OWNER: Member 1.
 *
 * Page:  HYPERPUFF bar · breadcrumb / gradient title / subtitle / heron sticker · lead sentence
 *        · inputs · RUN SIMULATION · result card.
 * A module extends this instead of ModulePanel and fills in crumb(), lead(), buildInputs(), runSimulation().
 */
public abstract class FigmaModule extends ModulePanel {
    private static final long serialVersionUID = 1L;

    /** Fixed colours from the design (not theme tokens). */
    public static final Color INK = new Color(0x3d3150);          // dark text on coloured blocks ("glyph")
    public static final Color CRUMB = new Color(0x8b4f7a);        // breadcrumb, light mode
    public static final Color RUN_EDGE = new Color(0xb81f72), RUN_DROP = new Color(0x8e1556);

    /** Breadcrumb after "OS SIMULATOR / ", e.g. "CPU". */
    protected abstract String crumb();
    /** One sentence above the inputs. */
    protected abstract String lead();
    /** Space between the lead sentence and the inputs. */
    protected int inputsTop() { return 17; }

    // ============================ PAGE LAYOUT ============================

    @Override public void rebuild() {
        removeAll();
        setBorder(new EmptyBorder(26, 30, 50, 30));

        Row arcade = new Row(new FlowLayout(FlowLayout.CENTER, 0, 0));
        arcade.setBorder(new EmptyBorder(0, 0, 14, 0));
        arcade.add(pixelText("HYPERPUFF", 7f, Theme.pink(), 0f));
        add(arcade);
        add(header());

        add(pad(text(lead(), 15, Theme.text()), 23, 0, false));

        inputs.removeAll(); inputs.setOpaque(false); inputs.setLayout(new BorderLayout());
        buildInputs(inputs);
        add(pad(inputs, inputsTop(), 0, true));

        RunButton run = new RunButton();
        run.addActionListener(e -> onRunPressed());
        add(pad(run, 15, 24, false));

        results.removeAll(); results.setOpaque(false);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBorder(new EmptyBorder(14, 16, 14, 16));
        resultsCard = new Card(results);
        add(resultsCard);
        runSimulation(results);

        PixelButton saveBtn = new PixelButton("SAVE PNG", false);
        saveBtn.setToolTipText("Export simulation result card as PNG image");
        saveBtn.addActionListener(e -> saveResultsAsPng());

        PixelButton copyBtn = new PixelButton("COPY OUTPUT", false);
        copyBtn.setToolTipText("Copy simulation summary to clipboard");
        copyBtn.addActionListener(e -> copyResultsSummary());

        PixelButton topBtn = new PixelButton("BACK TO TOP", true);
        topBtn.setToolTipText("Scroll smoothly back to the top");
        topBtn.addActionListener(e -> smoothScrollToTop());

        Row topRow = new Row(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topRow.setBorder(new EmptyBorder(12, 0, 0, 0));
        topRow.add(saveBtn);
        topRow.add(copyBtn);
        topRow.add(topBtn);
        add(topRow);

        // Ctrl+Enter runs simulation from anywhere in the module
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "runSim");
        getActionMap().put("runSim", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { onRunPressed(); }
        });

        revalidate(); repaint();
    }

    private Sticker headerSticker;
    private Card resultsCard;

    /** Bounces the header heron sticker (called on simulation run or module switch). */
    public void bounceSticker() {
        if (headerSticker != null) headerSticker.bounce();
    }

    /** Called when RUN SIMULATION (or Ctrl+Enter) is pressed. */
    protected void onRunPressed() { rerun(); }

    /** Runs the simulation again into the result card. */
    protected void rerun() {
        if (headerSticker != null) headerSticker.bounce();
        results.removeAll(); runSimulation(results); results.revalidate(); results.repaint();
    }

    /** Smoothly animates the enclosing JScrollPane back to the top. */
    protected void smoothScrollToTop() {
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (sp == null) return;
        JScrollBar vsb = sp.getVerticalScrollBar();
        if (vsb.getValue() <= 0) return;
        Timer t = new Timer(14, null);
        t.addActionListener(e -> {
            int cur = vsb.getValue();
            if (cur <= 2) {
                vsb.setValue(0);
                t.stop();
            } else {
                int step = Math.max(8, (int) Math.ceil(cur * 0.25));
                vsb.setValue(Math.max(0, cur - step));
            }
        });
        t.start();
    }

    /** Exports the results card as a PNG image for lab reports or documentation. */
    private void saveResultsAsPng() {
        JComponent target = resultsCard != null ? resultsCard : results;
        int w = target.getWidth(), h = target.getHeight();
        if (w <= 0 || h <= 0) {
            showToast(this, "Run a simulation first!");
            return;
        }
        String slug = title().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Simulation Result as PNG");
        chooser.setSelectedFile(new File((slug.isEmpty() ? "simulation" : slug) + "-result.png"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images (*.png)", "png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File out = chooser.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".png")) {
            out = new File(out.getParentFile(), out.getName() + ".png");
        }
        BufferedImage img = new BufferedImage(w + 24, h + 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Theme.bg());
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.translate(12, 12);
        target.paint(g2);
        g2.dispose();

        try {
            ImageIO.write(img, "png", out);
            showToast(this, "Saved " + out.getName() + "!");
        } catch (Exception ex) {
            showToast(this, "Failed to save PNG");
        }
    }

    /** Copies visible text from the results card to the system clipboard. */
    private void copyResultsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title()).append(" ===\n");
        collectText(results, sb);
        String out = sb.toString().trim();
        java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(out);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        showToast(this, "Copied " + title() + " results!");
    }

    private static void collectText(Container c, StringBuilder sb) {
        for (Component k : c.getComponents()) {
            if (!k.isVisible()) continue;
            if (k instanceof JLabel l && l.getText() != null && !l.getText().isBlank()) {
                sb.append(l.getText().replaceAll("<[^>]+>", "")).append('\n');
            } else if (k instanceof JTextArea ta && ta.getText() != null && !ta.getText().isBlank()) {
                sb.append(ta.getText()).append('\n');
            } else if (k instanceof Container sub) {
                collectText(sub, sb);
            }
        }
    }

    /** Rebuilds only the input area (e.g. after adding a job) and keeps the rest of the page. */
    protected void rebuildInputs() {
        inputs.removeAll(); buildInputs(inputs); inputs.revalidate(); inputs.repaint();
        revalidate(); repaint();
    }

    private JComponent header() {
        Row h = new Row(new BorderLayout()) {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics g) {   // dashed pink line under the header
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.pink());
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        h.setBorder(new EmptyBorder(0, 0, 15, 0));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(pixelText("OS SIMULATOR / " + crumb(), 7f, Theme.dark ? Theme.pink() : CRUMB, 0.23f));
        GradientTitle t = new GradientTitle(title());
        t.setBorder(new EmptyBorder(4, 0, 4, 0));
        left.add(t);
        JLabel sub = text(subtitle(), 13, Theme.muted());
        sub.setBorder(new EmptyBorder(3, 0, 0, 0));
        sub.setMaximumSize(new Dimension(Integer.MAX_VALUE, sub.getPreferredSize().height));
        left.add(sub);
        h.add(left, BorderLayout.CENTER);
        headerSticker = new Sticker();
        h.add(headerSticker, BorderLayout.EAST);
        return h;
    }

    // ============================ HELPERS ============================

    /**
     * Label whose preferred width also fits the text as drawn with anti-aliasing / fractional metrics.
     * The pixel fonts draw a few percent wider on screen than JLabel measures, which cut text short.
     */
    public static class SafeLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        private static final java.awt.font.FontRenderContext[] FRCS = {
                new java.awt.font.FontRenderContext(null, true, true), new java.awt.font.FontRenderContext(null, true, false),
                new java.awt.font.FontRenderContext(null, false, true), new java.awt.font.FontRenderContext(null, false, false)};
        public SafeLabel(String s) { super(s); }
        public SafeLabel(String s, int align) { super(s, align); }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            if (isPreferredSizeSet() || getText() == null || getText().isEmpty()) return d;
            double w = 0;
            for (java.awt.font.FontRenderContext frc : FRCS) w = Math.max(w, getFont().getStringBounds(getText(), frc).getWidth());
            Insets in = getInsets();
            return new Dimension(Math.max(d.width, (int) Math.ceil(w * 1.04) + 2 + in.left + in.right), d.height);
        }
        // BoxLayout clamps to these, and the label UI would report the smaller, unpadded width
        @Override public Dimension getMaximumSize() { return atLeastPreferred(super.getMaximumSize()); }
        @Override public Dimension getMinimumSize() { return atLeastPreferred(super.getMinimumSize()); }
        private Dimension atLeastPreferred(Dimension d) {
            Dimension p = getPreferredSize();
            return new Dimension(Math.max(d.width, p.width), Math.max(d.height, p.height));
        }
    }

    /** DotGothic body text (same look as ModulePanel.text, but never cut short). */
    public static JLabel body(String s, float size, Color c) {
        JLabel l = new SafeLabel(s); l.setFont(Fonts.body(size)); l.setForeground(c); l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** Same as body(); hides ModulePanel.text so every label on these pages is a SafeLabel. */
    @Override protected JLabel text(String s, float size, Color c) { return body(s, size, c); }

    /** Press Start 2P label with letter spacing (tracking is in em). */
    public static JLabel pixelText(String s, float size, Color color, float tracking) {
        JLabel l = new SafeLabel(s);
        l.setFont(Fonts.arcade(size).deriveFont(Map.of(TextAttribute.TRACKING, tracking)));
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(3, 0, 0, 0));   // small pixel font draws above its reported ascent
        return l;
    }

    /** Field label: 7px pixel font, uppercase, ~15px line. */
    public static JLabel fieldLabel(String s) {
        JLabel l = pixelText(s.toUpperCase(), 7f, Theme.text(), 0.11f);
        l.setBorder(new EmptyBorder(3, 0, 3, 0));
        return l;
    }

    /** Input columns side by side with a 14px gap, widths in proportion to fr (like a CSS grid). */
    public static JPanel columns(double[] fr, JComponent... cols) {
        JPanel grid = new JPanel(null) {
            private static final long serialVersionUID = 1L;
            @Override public void doLayout() {
                double total = 0;
                for (double f : fr) total += f;
                int gap = 14, avail = getWidth() - gap * (getComponentCount() - 1), x = 0;
                for (int i = 0; i < getComponentCount(); i++) {
                    int w = (int) Math.round(avail * fr[i] / total);
                    Component c = getComponent(i);
                    c.setBounds(x, 0, w, c.getPreferredSize().height);
                    x += w + gap;
                }
            }
            @Override public Dimension getPreferredSize() {
                int h = 0;
                for (Component c : getComponents()) h = Math.max(h, c.getPreferredSize().height);
                return new Dimension(300, h);
            }
        };
        grid.setOpaque(false);
        for (JComponent c : cols) grid.add(c);
        return grid;
    }

    /** Field label, the input, and an optional small hint underneath. */
    public static JPanel labeled(String label, JComponent input, String hint) {
        JPanel c = new JPanel();
        c.setOpaque(false);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.add(fieldLabel(label));
        c.add(Box.createVerticalStrut(6));
        input.setAlignmentX(LEFT_ALIGNMENT);
        c.add(input);
        c.add(Box.createVerticalStrut(6));
        c.add(body(hint == null ? " " : hint, 10, Theme.muted()));
        return c;
    }

    /** Wraps a component with top/bottom spacing; stretch = fill the width (labels always do). */
    public static Row pad(JComponent c, int top, int bottom, boolean stretch) {
        Row r = new Row(new BorderLayout());
        r.setBorder(new EmptyBorder(top, 0, bottom, 0));
        r.add(c, stretch || c instanceof JLabel ? BorderLayout.CENTER : BorderLayout.WEST);
        return r;
    }

    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    public static void centerText(Graphics2D g2, String s, double cx, double baseline) {
        g2.drawString(s, (float) (cx - g2.getFontMetrics().stringWidth(s) / 2.0), (float) baseline);
    }

    /** Baseline that vertically centres the current font on midY. */
    public static double middle(Graphics2D g2, double midY) {
        FontMetrics fm = g2.getFontMetrics();
        return midY + (fm.getAscent() - fm.getDescent()) / 2.0;
    }

    /** Block colour by index: pink, cyan, yellow, green, purple, orange (repeats). */
    public static Color blockColor(int i, double alpha) {
        Color[] p = {Theme.pink(), Theme.cyan(), Theme.yellow(), Theme.green(), Theme.purple(), Theme.orange()};
        Color c = p[Math.floorMod(i, p.length)];
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(alpha * 255));
    }

    /** Text colour on top of a coloured block. */
    public static Color glyph() { return Theme.dark ? Theme.bg() : INK; }

    // ============================ COMPONENTS ============================

    /** Panel that fills the page width but keeps its preferred height (for BoxLayout). */
    public static class Row extends JPanel {
        private static final long serialVersionUID = 1L;
        public Row(LayoutManager l) { super(l); setOpaque(false); setAlignmentX(LEFT_ALIGNMENT); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    /** Module title in Yellowtail with the green gradient fill and a soft drop shadow. */
    public static class GradientTitle extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String s;
        public GradientTitle(String s) { this.s = s; setFont(Fonts.title(58f)); setAlignmentX(LEFT_ALIGNMENT); }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            Insets in = getInsets();
            // extra room for the outline and the drop shadow
            return new Dimension(fm.stringWidth(s) + 18 + in.left + in.right, (int) Math.ceil(58 * 1.1) + 6 + in.top + in.bottom);
        }

        /** Dark outline around the letters (drawn behind the fill, so it shows as an outer border). */
        static final Color OUTLINE = new Color(0x16210f);
        static final float OUTLINE_W = 4.5f;
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Insets in = getInsets();
            int boxH = getHeight() - in.top - in.bottom;
            TextLayout tl = new TextLayout(s, getFont(), g2.getFontRenderContext());
            float baseline = in.top + (boxH - (tl.getAscent() + tl.getDescent())) / 2f + tl.getAscent();
            Shape glyphs = tl.getOutline(AffineTransform.getTranslateInstance(in.left + 4, baseline));
            BasicStroke outline = new BasicStroke(OUTLINE_W, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            Shape shadow = AffineTransform.getTranslateInstance(2, 3).createTransformedShape(glyphs);
            g2.setColor(new Color(20, 31, 15, 64));                          // soft drop shadow
            g2.fill(shadow);
            g2.setStroke(outline); g2.draw(shadow);
            g2.setColor(OUTLINE);                                            // black border lines
            g2.setStroke(outline); g2.draw(glyphs);
            g2.setPaint(new LinearGradientPaint(0, in.top, 0, in.top + boxH, new float[]{0f, .38f, .62f, 1f},
                    new Color[]{Color.WHITE, new Color(0xf7ffe5), new Color(0xb0f552), new Color(0x5ed112)}));
            g2.fill(glyphs);
            g2.dispose();
        }
    }

    /** Heron "SIMULATING" sticker, tilted 8 degrees, top right of the header. */
    public static class Sticker extends JComponent {
        private static final long serialVersionUID = 1L;
        private static BufferedImage art;
        static {
            try { art = ImageIO.read(Fonts.file("assets/images/heron_sticker_simulating@3x.png")); }
            catch (Exception e) { art = null; }
        }
        static final int ART_W = 96;
        static final double TILT = Math.toRadians(8);
        private double bouncePhase = 0;
        private Timer bounceTimer;

        public void bounce() {
            bouncePhase = 1.0;
            if (bounceTimer != null && bounceTimer.isRunning()) bounceTimer.stop();
            bounceTimer = new Timer(25, e -> {
                bouncePhase -= 0.08;
                if (bouncePhase <= 0) { bouncePhase = 0; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            bounceTimer.start();
        }

        /** Big enough for the tilted picture: rotating 96 x 88 by 8 degrees needs about 108 x 101. */
        @Override public Dimension getPreferredSize() {
            int h = art == null ? 88 : ART_W * art.getHeight() / art.getWidth();
            int bw = (int) Math.ceil(ART_W * Math.cos(TILT) + h * Math.sin(TILT)) + 4;
            int bh = (int) Math.ceil(ART_W * Math.sin(TILT) + h * Math.cos(TILT)) + 4;
            return new Dimension(bw, Math.max(104, bh));
        }

        @Override protected void paintComponent(Graphics g) {
            if (art == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = ART_W, h = w * art.getHeight() / art.getWidth();
            double dy = bouncePhase > 0 ? -Math.abs(Math.sin(bouncePhase * Math.PI * 2)) * 8.0 * bouncePhase : 0;
            double cx = getWidth() / 2.0, cy = getHeight() / 2.0 + dy;
            g2.rotate(TILT + dy * 0.015, cx, cy);
            g2.drawImage(art, (int) Math.round(cx - w / 2.0), (int) Math.round(cy - h / 2.0), w, h, null);
            g2.dispose();
        }
    }

    /** Rounded text box. Full-width 40px field by default; small fixed-size boxes for tables. */
    public static class RoundField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final int arc, prefW, prefH; private final boolean fixed;
        private boolean invalid = false;

        /** Full-width field, 40px high, radius 9. */
        public RoundField(String v) { this(v, 9, 60, 40, false); }
        /** Fixed-size box (e.g. 46x30, radius 6) with centred text. */
        public RoundField(String v, int radius, int w, int h) { this(v, radius, w, h, true); setHorizontalAlignment(CENTER); }

        public void setInvalid(boolean invalid) {
            if (this.invalid != invalid) { this.invalid = invalid; repaint(); }
        }

        private RoundField(String v, int radius, int w, int h, boolean fixed) {
            super(v);
            this.arc = radius * 2; this.prefW = w; this.prefH = h; this.fixed = fixed;
            setOpaque(false);
            setFont(Fonts.body(15f));
            setForeground(Theme.text()); setCaretColor(Theme.text());
            setBorder(new EmptyBorder(0, fixed ? 3 : 11, 2, fixed ? 3 : 11));
            setAlignmentX(LEFT_ALIGNMENT);
        }
        @Override public Dimension getPreferredSize() { return new Dimension(prefW, prefH); }
        @Override public Dimension getMinimumSize()   { return new Dimension(fixed ? prefW : 30, prefH); }
        @Override public Dimension getMaximumSize()   { return new Dimension(fixed ? prefW : Integer.MAX_VALUE, prefH); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            double w = getWidth(), h = getHeight() - 2;
            g2.setColor(new Color(184, 74, 140, 20));                        // soft shadow below
            g2.fill(new RoundRectangle2D.Double(0.75, 2.75, w - 1.5, h - 1.5, arc, arc));
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, arc, arc);
            g2.setColor(Theme.input()); g2.fill(box);
            g2.setColor(invalid ? Theme.pink() : Theme.inputLine());
            g2.setStroke(new BasicStroke(invalid ? 2.2f : 1.5f)); g2.draw(box);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Pink pill "RUN SIMULATION" button with the yellow arrow icon and a solid drop shadow. */
    public static class RunButton extends JButton {
        private static final long serialVersionUID = 1L;
        static final int H = 42, DROP = 4, ICON = 17;
        public RunButton() {
            super("RUN SIMULATION");
            setFont(Fonts.arcade(8f)); setForeground(Color.WHITE);
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override public Dimension getPreferredSize() {
            int tw = getFontMetrics(getFont()).stringWidth(getText());
            return new Dimension(1 + 19 + tw + 13 + ICON + 12 + 1, H + DROP);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            if (getModel().isPressed()) g2.translate(0, 2);
            g2.setColor(RUN_DROP);
            g2.fill(new RoundRectangle2D.Double(0, DROP, w, H, H, H));
            RoundRectangle2D pill = new RoundRectangle2D.Double(0.5, 0.5, w - 1, H - 1, H, H);
            g2.setPaint(new LinearGradientPaint(0, 0, 0, H, new float[]{0f, .5f, 1f},
                    new Color[]{new Color(0xff9ccf), new Color(0xff4fa8), new Color(0xd82b85)}));
            g2.fill(pill);
            g2.setColor(RUN_EDGE); g2.setStroke(new BasicStroke(1f)); g2.draw(pill);

            g2.setFont(getFont()); g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();
            int x = 20, tw = fm.stringWidth(getText());
            g2.drawString(getText(), x, (H - fm.getHeight()) / 2 + fm.getAscent());

            // icon: yellow circle, pink outline, chevron (geometry from the design's 17x17 icon)
            g2.translate(x + tw + 13, (H - ICON) / 2.0);
            Shape circle = new Ellipse2D.Double(0.5, 0.5, 16, 16);
            g2.setColor(new Color(0xffe27a)); g2.fill(circle);
            g2.setColor(RUN_EDGE); g2.draw(circle);
            Path2D chevron = new Path2D.Double();
            chevron.moveTo(7, 11.5); chevron.lineTo(9.75, 8.75); chevron.lineTo(7, 6);
            g2.setStroke(new BasicStroke(0.825f * 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(chevron);
            g2.dispose();
        }
    }

    /** Small dashed pill button, e.g. "+ Add job". */
    public static class DashedPill extends JButton {
        private static final long serialVersionUID = 1L;
        public DashedPill(String label) {
            super(label);
            setFont(Fonts.body(13f)); setForeground(Theme.muted());
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 24 + 3, fm.getHeight() + 10 + 3);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5, getHeight() - 1.5, 28, 28);
            boolean hover = getModel().isRollover();
            if (hover) { g2.setColor(Theme.panel2()); g2.fill(box); }
            g2.setColor(hover ? Theme.pink() : Theme.inputLine());
            g2.setStroke(new BasicStroke(hover ? 1.8f : 1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{4f, 3f}, 0f));
            g2.draw(box);
            g2.setFont(getFont()); g2.setColor(hover ? Theme.text() : getForeground());
            centerText(g2, getText(), getWidth() / 2.0, middle(g2, getHeight() / 2.0));
            g2.dispose();
        }
    }

    /** Pixel-art arcade button with stepped corners, hard pixel drop shadow, and optional up-arrow. */
    public static class PixelButton extends JButton {
        private static final long serialVersionUID = 1L;
        private static final int PX = 2, DROP = 3;
        private final boolean upArrow;

        public PixelButton(String label, boolean upArrow) {
            super(label);
            this.upArrow = upArrow;
            setFont(Fonts.arcade(7f));
            setForeground(Theme.text());
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int iconW = upArrow ? 14 : 0;
            int w = 14 + iconW + fm.stringWidth(getText()) + 14;
            int h = Math.max(30, fm.getHeight() + 14) + DROP;
            return new Dimension(w, h);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            int w = getWidth(), h = getHeight() - DROP;
            int yOff = pressed ? 2 : (hover ? -1 : 0);

            // Hard pixel drop shadow (not drawn when pressed down)
            if (!pressed) {
                g2.setColor(Theme.dark ? new Color(0x0b071a) : Theme.pink());
                fillPixelRect(g2, 0, DROP, w, h, PX);
            }

            g2.translate(0, Math.max(0, yOff));
            Color bg = hover
                    ? (Theme.dark ? new Color(0x362468) : new Color(0xffe3f2))
                    : Theme.panel();
            Color border = hover ? Theme.pink() : (Theme.dark ? Theme.purple() : Theme.inputLine());
            g2.setColor(bg);
            fillPixelRect(g2, 0, 0, w, h, PX);
            drawPixelBorder(g2, 0, 0, w, h, PX, border);

            // Top inner pixel highlight
            g2.setColor(Theme.dark ? new Color(255, 255, 255, 28) : new Color(255, 255, 255, 190));
            g2.fillRect(PX * 2, PX, w - PX * 4, PX);

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(getText());
            int iconW = upArrow ? 14 : 0;
            int startX = (w - (iconW + tw)) / 2;
            int midY = h / 2;

            if (upArrow) {
                g2.setColor(Theme.pink());
                int ax = startX + 3, ay = midY - 4;
                // Crisp 2px-stepped pixel up arrow
                g2.fillRect(ax + 2, ay, 2, 2);
                g2.fillRect(ax, ay + 2, 6, 2);
                g2.fillRect(ax - 2, ay + 4, 10, 2);
                g2.fillRect(ax + 2, ay + 6, 2, 3);
            }

            g2.setColor(hover ? (Theme.dark ? Color.WHITE : INK) : getForeground());
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent() + 1;
            g2.drawString(getText(), startX + iconW, textY);
            g2.dispose();
        }

        private static void fillPixelRect(Graphics2D g, int x, int y, int w, int h, int p) {
            g.fillRect(x + p, y, w - p * 2, h);
            g.fillRect(x, y + p, w, h - p * 2);
        }

        private static void drawPixelBorder(Graphics2D g, int x, int y, int w, int h, int p, Color c) {
            g.setColor(c);
            g.fillRect(x + p, y, w - p * 2, p);
            g.fillRect(x + p, y + h - p, w - p * 2, p);
            g.fillRect(x, y + p, p, h - p * 2);
            g.fillRect(x + w - p, y + p, p, h - p * 2);
        }
    }

    /** Rounded box that groups inputs (e.g. the job table): panel colour, radius 12, padding 12. */
    public static class Box12 extends Row {
        private static final long serialVersionUID = 1L;
        public Box12(LayoutManager l) { super(l); setBorder(new EmptyBorder(12, 12, 12, 12)); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5, getHeight() - 1.5, 24, 24);
            g2.setColor(Theme.panel()); g2.fill(box);
            g2.setColor(Theme.inputLine()); g2.setStroke(new BasicStroke(1.5f)); g2.draw(box);
            g2.dispose();
        }
    }

    /** Rounded dropdown (40px, radius 9) that opens a menu of choices. */
    public static class Dropdown extends JButton {
        private static final long serialVersionUID = 1L;
        public Dropdown(String value, String[] options, java.util.function.Consumer<String> onPick) {
            super(value);
            setFont(Fonts.body(15f)); setForeground(Theme.text());
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(LEFT_ALIGNMENT);
            addActionListener(e -> {
                JPopupMenu menu = new JPopupMenu();
                menu.setBorder(new javax.swing.border.LineBorder(Theme.inputLine(), 1));
                for (String o : options) {
                    JMenuItem item = new JMenuItem(o);
                    item.setFont(Fonts.body(15f));
                    item.setBackground(o.equals(getText()) ? Theme.panel2() : Theme.input());
                    item.setForeground(Theme.text());
                    item.setBorder(new EmptyBorder(6, 14, 6, 14));
                    item.addActionListener(ev -> onPick.accept(o));
                    menu.add(item);
                }
                menu.setPopupSize(getWidth(), menu.getPreferredSize().height);
                menu.show(this, 0, getHeight());
            });
        }
        @Override public Dimension getPreferredSize() { return new Dimension(160, 40); }
        @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, 40); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            double w = getWidth(), h = getHeight() - 2;
            g2.setColor(new Color(0, 0, 0, 20));
            g2.fill(new RoundRectangle2D.Double(0.75, 2.75, w - 1.5, h - 1.5, 18, 18));
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 18, 18);
            g2.setColor(Theme.input()); g2.fill(box);
            g2.setColor(Theme.inputLine()); g2.setStroke(new BasicStroke(1.5f)); g2.draw(box);
            g2.setFont(getFont()); g2.setColor(getForeground());
            g2.drawString(getText(), 14, (float) middle(g2, h / 2));
            double cx = w - 12.5, cy = h / 2;                  // chevron 10x6
            Path2D v = new Path2D.Double();
            v.moveTo(cx - 5, cy - 3); v.lineTo(cx, cy + 2); v.lineTo(cx + 5, cy - 3);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(v);
            g2.dispose();
        }
    }

    /** Result card: lilac panel, rounded 14, soft pink shadow. */
    public static class Card extends Row {
        private static final long serialVersionUID = 1L;
        public Card(JComponent content) {
            super(new BorderLayout());
            setBorder(new EmptyBorder(0, 0, 8, 0));     // room for the shadow
            add(content);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            double w = getWidth(), h = getHeight() - 8;
            for (int i = 1; i <= 6; i++) {               // shadow: 0 6px 16px
                g2.setColor(Theme.dark ? new Color(0, 0, 0, 8) : new Color(184, 74, 140, 6));
                g2.fill(new RoundRectangle2D.Double(-i / 2.0, 6 - i / 2.0 + i * 0.4, w + i, h + i / 2.0, 28 + i, 28 + i));
            }
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 28, 28);
            g2.setColor(Theme.panel2()); g2.fill(box);
            g2.setColor(Theme.inputLine()); g2.setStroke(new BasicStroke(1.5f)); g2.draw(box);
            g2.dispose();
        }
    }

    /** Green (ok) or pink (problem) message strip with a 4px bar on the left. */
    public static class Notice extends Row {
        private static final long serialVersionUID = 1L;
        private final Color accent;
        public Notice(boolean ok, String verdict, String detail) {
            this(ok ? Theme.green() : Theme.pink(), (ok ? "✓ " : "✗ ") + verdict, detail);
        }
        private Notice(Color accent, String verdict, String detail) {
            super(new BorderLayout(14, 0));
            this.accent = accent;
            setBorder(new EmptyBorder(10, 18, 10, 14));
            if (verdict != null) {
                JLabel v = body(verdict, 15, Theme.text());
                if (v.getFont().canDisplayUpTo(v.getText()) != -1) v.setFont(new Font(Font.DIALOG, Font.PLAIN, 15));
                add(v, BorderLayout.WEST);
            }
            add(body(detail, 14, Theme.text()), BorderLayout.CENTER);   // takes the rest of the strip, never clipped short
        }
        /** Neutral cyan prompt, e.g. before the person has typed anything. */
        public static Notice prompt(String detail) { return new Notice(Theme.cyan(), null, detail); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 36)); g2.fill(box);
            g2.clip(box);
            g2.setColor(accent); g2.fillRect(0, 0, 4, getHeight());
            g2.dispose();
        }
    }

    /** True when every given field is empty (nothing typed yet). */
    public static boolean allBlank(JTextField... fields) {
        for (JTextField f : fields) if (f != null && !f.getText().trim().isEmpty()) return false;
        return true;
    }

    private static JComponent activeToast;
    private static Timer toastTimer;

    /** Shows a subtle non-blocking toast pill near the bottom-right of the window. */
    public static void showToast(Component anchor, String message) {
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) return;
        JLayeredPane lp = root.getLayeredPane();
        if (activeToast != null) {
            lp.remove(activeToast);
            activeToast = null;
        }
        if (toastTimer != null) toastTimer.stop();

        final float[] alpha = {0.0f};
        final int[] ticks = {0};
        final int[] yOffset = {10};
        JLabel toast = new SafeLabel("  " + message) {
            private static final long serialVersionUID = 1L;
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(d.width + 18, Math.max(34, d.height));
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha[0]));
                int w = getWidth(), h = getHeight();
                // Soft drop shadow
                g2.setColor(new Color(0, 0, 0, 50));
                g2.fill(new RoundRectangle2D.Double(1, 3, w - 2, h - 3, 22, 22));
                RoundRectangle2D pill = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 3, 22, 22);
                g2.setColor(Theme.dark ? new Color(0x261c4a) : new Color(0x3d3150));
                g2.fill(pill);
                g2.setColor(Theme.pink());
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(pill);
                // Small green accent dot on the left
                g2.setColor(Theme.green());
                g2.fillOval(12, (h - 10) / 2, 8, 8);
                g2.dispose();
                Graphics2D tg = (Graphics2D) g.create();
                tg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha[0]));
                super.paintComponent(tg);
                tg.dispose();
            }
        };
        toast.setFont(Fonts.body(13f));
        toast.setForeground(Color.WHITE);
        toast.setBorder(new EmptyBorder(6, 20, 8, 14));
        Dimension d = toast.getPreferredSize();
        int x = Math.max(16, lp.getWidth() - d.width - 24);
        int baseY = Math.max(16, lp.getHeight() - d.height - 58);
        toast.setBounds(x, baseY + yOffset[0], d.width, d.height);
        lp.add(toast, JLayeredPane.POPUP_LAYER);
        lp.revalidate();
        lp.repaint();
        activeToast = toast;

        toastTimer = new Timer(32, e -> {
            ticks[0]++;
            if (ticks[0] <= 6) {
                alpha[0] = Math.min(1f, ticks[0] / 6f);
                yOffset[0] = Math.max(0, 10 - ticks[0] * 2);
                toast.setLocation(x, baseY + yOffset[0]);
                toast.repaint();
            } else if (ticks[0] > 52) {
                alpha[0] = Math.max(0f, alpha[0] - 0.1f);
                if (alpha[0] <= 0.01f) {
                    ((Timer) e.getSource()).stop();
                    if (activeToast != null) {
                        lp.remove(activeToast);
                        lp.revalidate();
                        lp.repaint();
                        activeToast = null;
                    }
                    return;
                }
                toast.repaint();
            }
        });
        toastTimer.start();
    }

    /** Wraps a result section with a clickable collapsible header (▾ / ▸). */
    public static JPanel collapsibleSection(String title, JComponent body) {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setAlignmentX(LEFT_ALIGNMENT);

        JButton toggle = new JButton("[-]  " + title.toUpperCase()) {
            private static final long serialVersionUID = 1L;
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            @Override protected void paintComponent(Graphics g) {
                if (getModel().isRollover()) {
                    Graphics2D g2 = smooth(g);
                    g2.setColor(Theme.dark ? new Color(255, 255, 255, 16) : new Color(255, 79, 168, 22));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                }
                setForeground(getModel().isRollover() ? Theme.pink() : Theme.muted());
                super.paintComponent(g);
            }
        };
        toggle.setFont(Fonts.arcade(7f));
        toggle.setForeground(Theme.muted());
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setFocusPainted(false);
        toggle.setBorder(new EmptyBorder(2, 0, 6, 0));
        toggle.setAlignmentX(LEFT_ALIGNMENT);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.addActionListener(e -> {
            boolean show = !body.isVisible();
            body.setVisible(show);
            toggle.setText((show ? "[-]  " : "[+]  ") + title.toUpperCase());
            wrap.revalidate();
            wrap.repaint();
        });

        body.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(toggle);
        wrap.add(body);
        return wrap;
    }

    /** Applies theme-matched thin rounded scrollbars and forwards wheel events to parent scrollpane when at edge. */
    public static void styleScrollPane(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new ThemeScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ThemeScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 8));
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setOpaque(false);
        sp.addMouseWheelListener(e -> {
            JScrollBar vsb = sp.getVerticalScrollBar();
            boolean noVert = sp.getVerticalScrollBarPolicy() == ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
                    || vsb.getMaximum() <= vsb.getVisibleAmount();
            boolean atTop = e.getWheelRotation() < 0 && vsb.getValue() <= vsb.getMinimum();
            boolean atBottom = e.getWheelRotation() > 0 && vsb.getValue() + vsb.getVisibleAmount() >= vsb.getMaximum();
            if (noVert || atTop || atBottom) {
                JScrollPane parent = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, sp.getParent());
                if (parent != null) {
                    parent.dispatchEvent(SwingUtilities.convertMouseEvent(sp, e, parent));
                }
            }
        });
    }

    private static class ThemeScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = Theme.pink();
            trackColor = new Color(0, 0, 0, 0);
        }
        @Override protected JButton createDecreaseButton(int o) { return zeroBtn(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroBtn(); }
        private JButton zeroBtn() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = smooth(g);
            Color base = isThumbRollover() ? Theme.pink() : Theme.inputLine();
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 190));
            g2.fillRoundRect(r.x + 1, r.y + 1, Math.max(4, r.width - 2), Math.max(4, r.height - 2), 8, 8);
            g2.dispose();
        }
    }
}
