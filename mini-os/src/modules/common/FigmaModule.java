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
        run.addActionListener(e -> rerun());
        add(pad(run, 15, 24, false));

        results.removeAll(); results.setOpaque(false);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBorder(new EmptyBorder(14, 16, 14, 16));
        add(new Card(results));
        runSimulation(results);

        revalidate(); repaint();
    }

    /** Runs the simulation again into the result card. */
    protected void rerun() {
        results.removeAll(); runSimulation(results); results.revalidate(); results.repaint();
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
        h.add(new Sticker(), BorderLayout.EAST);
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
            try { art = ImageIO.read(new File("assets/images/heron_sticker_simulating@3x.png")); }
            catch (Exception e) { art = null; }
        }
        static final int ART_W = 96;
        static final double TILT = Math.toRadians(8);

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
            double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
            g2.rotate(TILT, cx, cy);
            g2.drawImage(art, (int) Math.round(cx - w / 2.0), (int) Math.round(cy - h / 2.0), w, h, null);
            g2.dispose();
        }
    }

    /** Rounded text box. Full-width 40px field by default; small fixed-size boxes for tables. */
    public static class RoundField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final int arc, prefW, prefH; private final boolean fixed;

        /** Full-width field, 40px high, radius 9. */
        public RoundField(String v) { this(v, 9, 60, 40, false); }
        /** Fixed-size box (e.g. 46x30, radius 6) with centred text. */
        public RoundField(String v, int radius, int w, int h) { this(v, radius, w, h, true); setHorizontalAlignment(CENTER); }

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
            g2.setColor(Theme.inputLine()); g2.setStroke(new BasicStroke(1.5f)); g2.draw(box);
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
            if (getModel().isRollover()) { g2.setColor(Theme.panel2()); g2.fill(box); }
            g2.setColor(Theme.inputLine());
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{4f, 3f}, 0f));
            g2.draw(box);
            g2.setFont(getFont()); g2.setColor(getForeground());
            centerText(g2, getText(), getWidth() / 2.0, middle(g2, getHeight() / 2.0));
            g2.dispose();
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
}
