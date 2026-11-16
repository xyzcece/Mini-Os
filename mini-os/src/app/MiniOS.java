package app;

import modules.banker.BankerModule;
import modules.cpu.CpuModule;
import modules.disk.DiskModule;
import modules.fragmentation.FragmentationModule;
import modules.multiprogramming.MultiprogrammingModule;
import modules.paging.PagingModule;
import modules.replacement.ReplacementModule;
import modules.segmentation.SegmentationModule;
import shared.AnimatedBackground;
import shared.Fonts;
import shared.ModulePanel;
import shared.Theme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The Mini OS desktop: wallpaper + app window (title bar, sidebar, module area) + taskbar.
 * OWNER: Member 3. This is the ONLY file where modules are plugged in (see MODULES below).
 */
public class MiniOS extends JFrame {

    // ---- the 8 modules: name, folder icon, screen ----
    private final ModulePanel[] MODULES = {
            new CpuModule(), new BankerModule(), new PagingModule(), new ReplacementModule(),
            new SegmentationModule(), new FragmentationModule(), new DiskModule(), new MultiprogrammingModule()
    };
    private final String[] FOLDERS = {"cpu", "banker", "paging", "replacement", "segments", "fragmentation", "disk", "multi"};

    private final AnimatedBackground desktop = new AnimatedBackground();
    private final JPanel window = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel sidebar = new JPanel() {
        @Override protected void paintComponent(Graphics g) { paintSidebar((Graphics2D) g, this); }
    };
    private boolean sidebarCollapsed = false;
    private final JLabel titleLabel = new JLabel();
    private final TaskButton taskButton = new TaskButton();
    private final JLabel taskFolder = new JLabel();   // folder of the open module, next to start
    private int active = 0;

    public MiniOS() {
        super("Mini OS – OS Lab");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 860);
        setLocationRelativeTo(null);
        try { setIconImage(ImageIO.read(Fonts.file("assets/images/app_icon.png"))); } catch (Exception ignored) {}

        setContentPane(desktop);
        desktop.setLayout(new BorderLayout());

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        // margins around the window as in the Figma desktop (1280 x 1037 above the taskbar):
        // 32px top, 33px above the taskbar, ~90px each side -> kept as proportions for any screen size
        center.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int w = center.getWidth(), h = center.getHeight();
                int top = (int) Math.round(h * 32 / 1037.0), bottom = (int) Math.round(h * 33 / 1037.0);
                int side = (int) Math.round(w * 90 / 1280.0);
                center.setBorder(new EmptyBorder(top, side, bottom, side));
                center.revalidate();
            }
        });
        center.setBorder(new EmptyBorder(32, 90, 33, 90));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH; gc.weightx = 1; gc.weighty = 1;
        center.add(window, gc);
        desktop.add(center, BorderLayout.CENTER);
        desktop.add(taskbar(), BorderLayout.SOUTH);

        for (int i = 0; i < MODULES.length; i++) {
            JScrollPane sp = new JScrollPane(MODULES[i]);
            sp.setBorder(null); sp.getViewport().setOpaque(false); sp.setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            content.add(sp, String.valueOf(i));
        }
        content.setOpaque(false);

        buildWindow();
        Theme.onChange(this::buildWindow);
        open(0);
    }

    private void buildWindow() {
        window.removeAll();
        window.setBackground(Theme.panel());
        window.setBorder(BorderFactory.createLineBorder(Theme.pink(), 2, true));
        window.add(titleBar(), BorderLayout.NORTH);
        buildSidebar();
        window.add(sidebar, BorderLayout.WEST);
        JPanel body = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                ((Graphics2D) g).setPaint(new GradientPaint(0, 0, Theme.bg(), getWidth(), getHeight(), Theme.panel2()));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        body.add(content);
        window.add(body, BorderLayout.CENTER);
        titleLabel.setText(MODULES[active].title());
        titleLabel.setIcon(LineIcons.MODULE[active].icon(15, Color.WHITE));
        titleLabel.setIconTextGap(8);
        window.revalidate(); window.repaint();
    }

    private JComponent titleBar() {
        JPanel bar = gradientPanel(Theme.barTop(), Theme.barBottom());
        bar.setLayout(new BorderLayout());
        bar.setBorder(new EmptyBorder(6, 12, 6, 8));
        titleLabel.setFont(Fonts.ui(14f)); titleLabel.setForeground(Color.WHITE);
        bar.add(titleLabel, BorderLayout.WEST);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controls.setOpaque(false);
        JButton min = windowButton("_", new Color(0xe2408f)), close = windowButton("X", new Color(0xe23b4b));
        min.addActionListener(e -> window.setVisible(false));                                   // minimize
        close.addActionListener(e -> { window.setVisible(false); taskButton.setVisible(false); }); // close
        controls.add(min); controls.add(close);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    // ---------- sidebar (Figma "Navigation": 252px, gradient, heron + OS Lab, folder buttons) ----------

    private void buildSidebar() {
        sidebar.removeAll();
        sidebar.setLayout(new SidebarLayout());
        sidebar.setOpaque(true);
        sidebar.setBorder(new EmptyBorder(15, 10, 15, 10));
        sidebar.setPreferredSize(new Dimension(sidebarCollapsed ? 92 : 252, 0));

        // brand row: heron, "OS Lab", hide-modules button
        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.X_AXIS));
        brand.setBorder(new EmptyBorder(0, 6, 10, 4));
        brand.setAlignmentX(LEFT_ALIGNMENT);
        if (!sidebarCollapsed) {
            brand.add(new JLabel(icon("heron_bust", 70, 44)));
            brand.add(Box.createHorizontalStrut(14));
            brand.add(new ShadowLabel("OS Lab", Fonts.pixel(28f), Theme.dark ? new Color(0xd7b4ff) : new Color(0x6a2ca0),
                    Theme.dark ? new Color(0, 0, 0, 153) : new Color(255, 255, 255, 230), Theme.dark ? 2 : 1));
            brand.add(Box.createHorizontalGlue());
        }
        JButton hide = new JButton(icon(Theme.dark ? "button_hide_dark" : "button_hide", 36, 38, sidebarCollapsed));
        hide.setContentAreaFilled(false); hide.setBorderPainted(false); hide.setFocusPainted(false);
        hide.setBorder(new EmptyBorder(0, 0, 0, 0));
        hide.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hide.setToolTipText(sidebarCollapsed ? "Show modules" : "Hide modules");
        hide.addActionListener(e -> { sidebarCollapsed = !sidebarCollapsed; buildWindow(); });
        brand.add(hide);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, brand.getPreferredSize().height));
        sidebar.add(brand);

        for (int i = 0; i < MODULES.length; i++) {
            final int idx = i;
            NavItem b = new NavItem(sidebarCollapsed ? "" : MODULES[i].title(), icon("folder_" + FOLDERS[i], 48, 36), i == active);
            if (sidebarCollapsed) b.setToolTipText(MODULES[i].title());
            b.addActionListener(e -> open(idx));
            sidebar.add(b);
        }
    }

    /**
     * Brand row on top, then the module buttons. Each button gets the design height (60px + 4px above and below)
     * when there is room; on a short window (e.g. a laptop with 150% scaling) they share the space evenly,
     * down to 40px, so all eight modules stay visible.
     */
    private static class SidebarLayout implements LayoutManager {
        static final int GAP = 4, MIN_ITEM = 40;
        @Override public void addLayoutComponent(String name, Component comp) { }
        @Override public void removeLayoutComponent(Component comp) { }
        @Override public Dimension preferredLayoutSize(Container c) {
            Insets in = c.getInsets();
            int h = in.top + in.bottom;
            for (Component k : c.getComponents()) h += k.getPreferredSize().height + (k instanceof NavItem ? 2 * GAP : 0);
            return new Dimension(c.getPreferredSize().width, h);
        }
        @Override public Dimension minimumLayoutSize(Container c) { return new Dimension(0, 0); }
        @Override public void layoutContainer(Container c) {
            Insets in = c.getInsets();
            int w = c.getWidth() - in.left - in.right, y = in.top, items = 0, itemPref = 0;
            for (Component k : c.getComponents())
                if (k instanceof NavItem) { items++; itemPref = Math.max(itemPref, k.getPreferredSize().height); }
            Component brand = c.getComponentCount() > 0 ? c.getComponent(0) : null;
            if (brand != null && !(brand instanceof NavItem)) {
                int bh = brand.getPreferredSize().height;
                brand.setBounds(in.left, y, w, bh);
                y += bh;
            }
            int room = c.getHeight() - in.bottom - y;
            int slot = items == 0 ? 0 : Math.min(itemPref + 2 * GAP, room / items);
            int itemH = Math.max(MIN_ITEM, slot - 2 * GAP);
            int gap = Math.max(0, (slot - itemH) / 2);
            for (Component k : c.getComponents()) {
                if (!(k instanceof NavItem)) continue;
                k.setBounds(in.left, y + gap, w, itemH);
                y += itemH + 2 * gap;
            }
        }
    }

    /** Sidebar background: diagonal gradient, a soft light beam (light mode), line on the right. */
    private static void paintSidebar(Graphics2D g0, JComponent c) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = c.getWidth(), h = c.getHeight();
        // 105deg CSS gradient ≈ left-to-right, slightly downward
        g.setPaint(new GradientPaint(0, 0, Theme.sideTop(), (float) (w * 0.833), (float) (w * 0.833 * Math.tan(Math.toRadians(15))), Theme.sideBottom()));
        g.fillRect(0, 0, w, h);
        if (!Theme.dark) {                     // "Light Beam": 90px band, rotated 12deg, across the top left
            Graphics2D b = (Graphics2D) g.create();
            b.rotate(Math.toRadians(12), -86, 752);
            b.setPaint(new LinearGradientPaint(-131, 0, -41, 0, new float[]{0f, .35f, 1f},
                    new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 140), new Color(255, 255, 255, 51)}));
            b.fillRect(-131, -40, 90, 1600);
            b.dispose();
        }
        g.setColor(Theme.inputLine());
        g.fillRect(w - 1, 0, 1, h);
        g.dispose();
    }

    /** One module button: folder + Nunito label; the open module is a pink pill with a thick left edge. */
    private static class NavItem extends JButton {
        private final boolean selected;
        NavItem(String text, Icon folder, boolean selected) {
            super(text, folder);
            this.selected = selected;
            setFont(selected ? Fonts.uiExtraBold(14f) : Fonts.uiBold(14f));
            setForeground(Theme.dark ? (selected ? Color.WHITE : new Color(0xefe9ff)) : (selected ? new Color(0x3a1f4f) : new Color(0x4a3d5e)));
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(12);
            setBorder(new EmptyBorder(12, selected ? 16 : 12, 12, 8));
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(LEFT_ALIGNMENT);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (selected) {
                for (int i = 1; i <= 4; i++) {         // drop shadow 0 4px 5px
                    g.setColor(Theme.dark ? new Color(0, 0, 0, 30) : new Color(176, 80, 143, 16));
                    g.fill(new java.awt.geom.RoundRectangle2D.Double(i * 0.25, 2 + i, w - i * 0.5, h - 4, 24, 24));
                }
                java.awt.geom.RoundRectangle2D pill = new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h - 4, 24, 24);
                g.setColor(new Color(0xff6fb8)); g.fill(pill);                               // border colour (5px left, 1px rest)
                java.awt.geom.RoundRectangle2D inner = new java.awt.geom.RoundRectangle2D.Double(5, 1, w - 6, h - 6, 20, 20);
                Color[] stops = Theme.dark
                        ? new Color[]{new Color(0x6a2270), new Color(0x33235a), new Color(0x23284a)}
                        : new Color[]{new Color(0xffc4e2), Color.WHITE, new Color(0xf3fbff)};
                g.setPaint(new LinearGradientPaint(0, 0, w, 0, new float[]{0f, .62f, 1f}, stops));
                g.fill(inner);
                g.translate(0, -2);                      // centre the content in the pill (shadow space is below)
            } else if (getModel().isRollover()) {
                g.setColor(Theme.dark ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, 120));
                g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h - 4, 24, 24));
            }
            g.dispose();
            if (selected) { Graphics2D t = (Graphics2D) g0.create(); t.translate(0, -2); super.paintComponent(t); t.dispose(); }
            else super.paintComponent(g0);
        }
    }

    /** Text with a small hard drop shadow (the "OS Lab" logo). */
    private static class ShadowLabel extends JComponent {
        private final String s; private final Color color, shadow; private final int dy;
        ShadowLabel(String s, Font f, Color color, Color shadow, int dy) {
            this.s = s; this.color = color; this.shadow = shadow; this.dy = dy; setFont(f);
        }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(s) + 4, fm.getHeight() + 2);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(shadow); g.drawString(s, 1, y + dy);
            g.setColor(color);  g.drawString(s, 0, y);
            g.dispose();
        }
    }

    private void open(int i) {
        active = i;
        cards.show(content, String.valueOf(i));
        window.setVisible(true);
        taskButton.setVisible(true);
        taskButton.setText(MODULES[i].title());
        taskButton.setIcon(LineIcons.MODULE[i].icon(15, Color.WHITE));
        taskFolder.setIcon(icon("folder_" + FOLDERS[i], 26, 20));   // the taskbar folder follows the open module
        buildWindow();
    }

    // ---------- taskbar (Figma "Footer": 43px pink bar, start, folder, open-module button, clock tray) ----------

    private JComponent taskbar() {
        JPanel bar = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Color[] c = Theme.dark
                        ? new Color[]{new Color(0xd8338f), new Color(0x9a1668), new Color(0x5e0d45)}
                        : new Color[]{new Color(0xffa6d5), new Color(0xff5fb0), new Color(0xd82b85)};
                g.setPaint(new LinearGradientPaint(0, 0, 0, getHeight(), new float[]{0f, .5f, 1f}, c));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(255, 255, 255, 153)); g.fillRect(0, 0, getWidth(), 1);   // top highlight
                g.dispose();
            }
        };
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(new EmptyBorder(4, 6, 4, 6));
        bar.setPreferredSize(new Dimension(0, 43));

        StartButton start = new StartButton();
        start.addActionListener(e -> open(window.isVisible() ? active : 0)); // start opens the window
        bar.add(start);
        bar.add(Box.createHorizontalStrut(14));

        // open module's folder with a thin divider on its right
        JPanel folderBox = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(255, 255, 255, 115)); g.fillRect(getWidth() - 1, 2, 1, getHeight() - 4);
            }
        };
        folderBox.setOpaque(false);
        folderBox.setBorder(new EmptyBorder(0, 8, 0, 8));
        taskFolder.setIcon(icon("folder_" + FOLDERS[active], 26, 20));
        folderBox.add(taskFolder);
        folderBox.setMaximumSize(new Dimension(42 + 2, 31));
        bar.add(folderBox);
        bar.add(Box.createHorizontalStrut(14));

        taskButton.setText(MODULES[active].title());
        taskButton.setIcon(LineIcons.MODULE[active].icon(15, Color.WHITE));
        for (var l : taskButton.getActionListeners()) taskButton.removeActionListener(l);
        taskButton.addActionListener(e -> window.setVisible(!window.isVisible()));
        bar.add(taskButton);
        bar.add(Box.createHorizontalGlue());

        bar.add(new ClockTray(() -> { Theme.toggle(); bar.repaint(); desktop.repaint(); }));
        return bar;
    }

    /** Green rounded "start" button with the window logo. */
    private static class StartButton extends JButton {
        StartButton() {
            super("start", LineIcons.START.icon(20, Color.WHITE));
            setFont(Fonts.body(18f)); setForeground(Color.WHITE);
            setIconTextGap(5);
            setBorder(new EmptyBorder(0, 10, 2, 15));
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(d.width, 35); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight() - 2;
            g.setColor(new Color(29, 77, 23, 70));                                     // drop shadow
            g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 2, w, h, 24, 24));
            java.awt.geom.RoundRectangle2D box = new java.awt.geom.RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 24, 24);
            g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, .55f, 1f},
                    new Color[]{new Color(0xb6f285), new Color(0x5cc23a), new Color(0x3a9a2c)}));
            g.fill(box);
            g.setColor(new Color(0x2f7d25)); g.draw(box);
            // text shadow 0 1px
            g.setFont(getFont()); g.setColor(new Color(29, 77, 23, 153));
            FontMetrics fm = g.getFontMetrics();
            int tx = getInsets().left + 20 + getIconTextGap(), ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(getText(), tx, ty + 1);
            g.dispose();
            Graphics2D t = (Graphics2D) g0.create();
            t.translate(0, -1);
            super.paintComponent(t);
            t.dispose();
        }
    }

    /** The open module's button: translucent dark pink, icon tile, module name. */
    private static class TaskButton extends JButton {
        TaskButton() {
            setFont(Fonts.body(14f)); setForeground(Color.WHITE);
            setHorizontalAlignment(SwingConstants.LEFT); setIconTextGap(9);
            setBorder(new EmptyBorder(0, 9 + 1, 0, 12));
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override public Dimension getPreferredSize() { return new Dimension(240, 30); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            java.awt.geom.RoundRectangle2D box = new java.awt.geom.RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 14, 14);
            g.setColor(new Color(163, 22, 95, getModel().isRollover() ? 150 : 115)); g.fill(box);
            g.setColor(new Color(255, 255, 255, 89)); g.draw(box);
            // light tile behind the icon (18x18, radius 4)
            g.setColor(new Color(255, 255, 255, 64));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(getInsets().left - 1.5, (h - 18) / 2.0, 18, 18, 8, 8));
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Cyan tray: yellow light/dark toggle, time and date. */
    private static class ClockTray extends JPanel {
        ClockTray(Runnable toggleTheme) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setBorder(new EmptyBorder(0, 12, 0, 12));
            JButton toggle = new JButton(LineIcons.MOON.icon(14, null)) {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    java.awt.geom.Ellipse2D c = new java.awt.geom.Ellipse2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1);
                    g.setPaint(new GradientPaint(0, 0, new Color(0xfff9c4), 0, getHeight(), new Color(0xffd95e)));
                    g.fill(c);
                    g.setColor(Color.WHITE); g.draw(c);
                    g.dispose();
                    super.paintComponent(g0);
                }
            };
            Dimension d25 = new Dimension(25, 25);
            toggle.setPreferredSize(d25); toggle.setMaximumSize(d25); toggle.setMinimumSize(d25);
            toggle.setContentAreaFilled(false); toggle.setBorderPainted(false); toggle.setFocusPainted(false);
            toggle.setBorder(new EmptyBorder(0, 0, 0, 0));
            toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggle.setToolTipText("Switch light / dark");
            toggle.addActionListener(e -> toggleTheme.run());
            add(toggle);
            add(Box.createHorizontalStrut(9));

            add(clock);
            new Timer(1000, e -> clock.repaint()).start();
        }

        /** Time over date, right-aligned, measured with the same font settings it is drawn with. */
        private final JComponent clock = new JComponent() {
            private final Font timeF = Fonts.body(14f), dateF = Fonts.body(10f);
            private final java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
            @Override public Dimension getPreferredSize() {
                double w = Math.max(timeF.getStringBounds("00:00", frc).getWidth(), dateF.getStringBounds("Wed 30 Sep", frc).getWidth());
                return new Dimension((int) Math.ceil(w * 1.08) + 2, 28);
            }
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                String t = now.format(DateTimeFormatter.ofPattern("HH:mm"));
                String d = now.format(DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH));
                int w = getWidth();
                g.setFont(timeF); g.setColor(new Color(0x173a5c));
                FontMetrics ft = g.getFontMetrics();
                g.drawString(t, w - ft.stringWidth(t), 13);          // 16.5px line, like the design
                g.setFont(dateF); g.setColor(new Color(23, 58, 92, 230));
                FontMetrics fd = g.getFontMetrics();
                g.drawString(d, w - fd.stringWidth(d), 25);          // 12px line under it
                g.dispose();
            }
        };
        @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(d.width, 34); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.geom.RoundRectangle2D box = new java.awt.geom.RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 18, 18);
            g.setPaint(new GradientPaint(0, 0, new Color(0xa8eefc), 0, getHeight(), new Color(0x3fc6e8)));
            g.fill(box);
            g.setColor(new Color(255, 255, 255, 204)); g.draw(box);
            g.dispose();
        }
    }

    // ---------- helpers ----------
    private static JPanel gradientPanel(Color top, Color bottom) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                ((Graphics2D) g).setPaint(new GradientPaint(0, 0, Theme.barTop(), 0, getHeight(), Theme.barBottom()));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
    }

    private static JButton windowButton(String s, Color c) {
        JButton b = new JButton(s); b.setFont(Fonts.ui(11f)); b.setForeground(Color.WHITE); b.setBackground(c);
        b.setFocusPainted(false); b.setMargin(new Insets(1, 6, 1, 6)); return b;
    }

    /** Icon drawn at w x h from the sharpest file available: name@8x/@4x/@2x.png, or name.png. */
    private static Icon icon(String name, int w, int h) { return icon(name, w, h, false); }

    private static Icon icon(String name, int w, int h, boolean flip) {
        java.awt.image.BufferedImage img = null;
        for (String suffix : new String[]{"@8x", "@4x", "@3x", "@2x", "@1x", ""}) {
            File f = Fonts.file("assets/images/" + name + suffix + ".png");
            if (!f.exists()) continue;
            try { img = ImageIO.read(f); break; } catch (Exception ignored) { }
        }
        if (img == null) return null;
        final java.awt.image.BufferedImage art = img;
        return new Icon() {
            @Override public int getIconWidth()  { return w; }
            @Override public int getIconHeight() { return h; }
            @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                if (flip) g.drawImage(art, x + w, y, -w, h, null);
                else g.drawImage(art, x, y, w, h, null);
                g.dispose();
            }
        };
    }
}
