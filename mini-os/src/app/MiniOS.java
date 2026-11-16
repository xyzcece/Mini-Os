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
    private final JScrollPane[] scrollPanes = new JScrollPane[MODULES.length];
    private JComponent taskbarPanel;
    private final JPanel sidebar = new JPanel() {
        @Override protected void paintComponent(Graphics g) { paintSidebar((Graphics2D) g, this); }
    };
    private boolean sidebarCollapsed = false;
    private boolean manualCollapse = false;
    private boolean maximized = false;
    private JPanel centerPanel;
    private final JLabel titleLabel = new JLabel();
    private final TaskButton taskButton = new TaskButton();
    private final JLabel taskFolder = new JLabel();   // folder of the open module, next to start
    private int active = 0;

    public MiniOS() {
        super("Mini OS – OS Lab");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 860);
        setLocationRelativeTo(null);
        setBackground(Theme.bg());
        desktop.setBackground(Theme.bg());
        addWindowStateListener(e -> SwingUtilities.invokeLater(() -> {
            desktop.revalidate();
            desktop.repaint();
        }));
        try { setIconImage(ImageIO.read(Fonts.file("assets/images/app_icon.png"))); } catch (Exception ignored) {}

        setContentPane(desktop);
        desktop.setLayout(new BorderLayout());
        startSpriteClock();

        JLayeredPane stage = new JLayeredPane() {
            @Override public void doLayout() {
                for (Component c : getComponents()) c.setBounds(0, 0, getWidth(), getHeight());
            }
        };
        stage.setOpaque(false);

        JPanel iconsLayer = buildDesktopIcons();
        iconsLayer.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (!window.isVisible()) {
                    Point p = SwingUtilities.convertPoint(iconsLayer, e.getPoint(), desktop);
                    desktop.handleDesktopClick(p);
                }
            }
        });
        stage.add(iconsLayer, JLayeredPane.DEFAULT_LAYER);

        JPanel center = new JPanel(new GridBagLayout());
        this.centerPanel = center;
        center.setOpaque(false);
        // margins around the window as in the Figma desktop (1280 x 1037 above the taskbar):
        // 32px top, 33px above the taskbar, ~90px each side -> kept as proportions for any screen size
        center.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (maximized) {
                    center.setBorder(new EmptyBorder(4, 4, 4, 4));
                } else {
                    int w = center.getWidth(), h = center.getHeight();
                    int top = (int) Math.round(h * 32 / 1037.0), bottom = (int) Math.round(h * 33 / 1037.0);
                    int side = (int) Math.round(w * 90 / 1280.0);
                    center.setBorder(new EmptyBorder(top, side, bottom, side));
                }
                center.revalidate();

                // Auto-collapse sidebar on small / projector screens
                int frameW = MiniOS.this.getWidth();
                if (frameW > 0 && frameW < 1050 && !sidebarCollapsed) {
                    sidebarCollapsed = true;
                    buildWindow();
                } else if (frameW >= 1100 && sidebarCollapsed && !manualCollapse) {
                    sidebarCollapsed = false;
                    buildWindow();
                }
            }
        });
        center.setBorder(new EmptyBorder(32, 90, 33, 90));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH; gc.weightx = 1; gc.weighty = 1;
        center.add(window, gc);
        stage.add(center, JLayeredPane.PALETTE_LAYER);

        desktop.add(stage, BorderLayout.CENTER);
        taskbarPanel = taskbar();
        desktop.add(taskbarPanel, BorderLayout.SOUTH);

        for (int i = 0; i < MODULES.length; i++) {
            JScrollPane sp = new JScrollPane(MODULES[i]);
            sp.setBorder(null); sp.getViewport().setOpaque(false); sp.setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            modules.common.FigmaModule.styleScrollPane(sp);
            scrollPanes[i] = sp;
            content.add(sp, String.valueOf(i));
        }
        content.setOpaque(false);

        buildWindow();
        Theme.onChange(() -> {
            setBackground(Theme.bg());
            desktop.setBackground(Theme.bg());
            buildWindow();
            if (taskbarPanel != null) taskbarPanel.repaint();
            desktop.repaint();
        });
        window.setVisible(false);
        taskButton.setVisible(false);

        // Global shortcuts: Ctrl+1..8 switch modules, Ctrl+D toggles Dark/Light, F11 maximize, Ctrl+0/+/- zoom
        JRootPane rp = getRootPane();
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();
        for (int i = 0; i < MODULES.length; i++) {
            final int idx = i;
            String key = "openMod" + i;
            im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1 + i, java.awt.event.InputEvent.CTRL_DOWN_MASK), key);
            am.put(key, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { open(idx); }
            });
        }
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.CTRL_DOWN_MASK), "toggleDark");
        am.put("toggleDark", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleThemeWithToast(); }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0), "toggleMax");
        am.put("toggleMax", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (window.isVisible()) toggleMaximize(); }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomReset");
        am.put("zoomReset", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { setZoom(1.0f); }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomIn");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ADD, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomIn");
        am.put("zoomIn", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { adjustZoom(0.15f); }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomOut");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SUBTRACT, java.awt.event.InputEvent.CTRL_DOWN_MASK), "zoomOut");
        am.put("zoomOut", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { adjustZoom(-0.15f); }
        });
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
        JButton help = windowButton("?", new Color(0x3fc6e8));
        help.setToolTipText("Quick Help & Formulas");
        help.addActionListener(e -> showHelpDialog());
        JButton zoomOut = windowButton("A-", new Color(0x9b4dca)), zoomIn = windowButton("A+", new Color(0x9b4dca));
        zoomOut.setToolTipText("Zoom out (smaller text)");
        zoomIn.setToolTipText("Zoom in (projector text)");
        zoomOut.addActionListener(e -> adjustZoom(-0.15f));
        zoomIn.addActionListener(e -> adjustZoom(0.15f));
        JButton min = windowButton("_", new Color(0xe2408f)), maxBtn = windowButton("[]", new Color(0xc73896)), close = windowButton("X", new Color(0xe23b4b));
        min.setToolTipText("Minimize window");
        min.addActionListener(e -> window.setVisible(false));                                   // minimize
        maxBtn.setToolTipText("Maximize / Restore window (F11)");
        maxBtn.addActionListener(e -> toggleMaximize());
        close.setToolTipText("Close window");
        close.addActionListener(e -> { window.setVisible(false); taskButton.setVisible(false); }); // close
        controls.add(help); controls.add(zoomOut); controls.add(zoomIn); controls.add(Box.createHorizontalStrut(4));
        controls.add(min); controls.add(maxBtn); controls.add(close);
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
        hide.addActionListener(e -> {
            sidebarCollapsed = !sidebarCollapsed;
            manualCollapse = sidebarCollapsed;
            buildWindow();
        });
        brand.add(hide);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, brand.getPreferredSize().height));
        sidebar.add(brand);

        for (int i = 0; i < MODULES.length; i++) {
            final int idx = i;
            NavItem b = new NavItem(sidebarCollapsed ? "" : MODULES[i].title(), moduleIcon(i, 34, false), i == active);
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

    private void toggleMaximize() {
        maximized = !maximized;
        if (centerPanel != null) {
            if (maximized) {
                centerPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
            } else {
                int w = centerPanel.getWidth(), h = centerPanel.getHeight();
                int top = (int) Math.round(h * 32 / 1037.0), bottom = (int) Math.round(h * 33 / 1037.0);
                int side = (int) Math.round(w * 90 / 1280.0);
                centerPanel.setBorder(new EmptyBorder(top, side, bottom, side));
            }
            centerPanel.revalidate();
            centerPanel.repaint();
        }
    }

    private void showHelpDialog() {
        String helpText = """
                1. CPU Scheduling   — FIFO, SJF, SRT, Round Robin (WT = TAT - RT, TAT = CT - AT)
                2. Banker's Algo    — Need = Max - Allocation; checks Safe Sequence
                3. Paging Lab       — Page Table Entries = Address Space / Page Size
                4. Page Replacement — FIFO, LRU, Optimal (Frame Grid + #FAULT / #HIT)
                5. Segmentation     — Physical = Base + Offset (valid when 0 <= Offset < Limit)
                6. Fragmentation    — First / Best / Worst Fit memory allocation
                7. Disk Scheduling  — FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK (Seek Graph)
                8. Multiprogramming — %CPU/J = (1 - p^NJ) / NJ; HW/J = ET * (%CPU/J)
                
                KEYBOARD SHORTCUTS & TIPS
                • Ctrl + 1..8   : Switch directly between the 8 OS modules
                • Ctrl + Enter  : Run simulation in any module
                • Ctrl + D      : Toggle Light / Dark arcade theme
                • Ctrl + + / -  : Zoom in / out (85% – 130%)   • Ctrl + 0 : Reset 100%
                • F11           : Maximize / Restore OS window
                • Sample pills  : Load small or large test datasets in one click
                • Copy Output   : Copy simulation summary straight to clipboard
                """;
        JDialog dlg = new JDialog(this, "Mini OS — Quick Help", true);
        dlg.setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout(0, 12)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, Theme.bg(), 0, getHeight(), Theme.panel2()));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.pink(), 2),
                new EmptyBorder(16, 18, 16, 18)));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        JLabel title = new JLabel("MINI OS — QUICK HELP & FORMULAS");
        title.setFont(Fonts.arcade(9f));
        title.setForeground(Theme.pink());
        hdr.add(title, BorderLayout.WEST);
        root.add(hdr, BorderLayout.NORTH);

        JTextArea area = new JTextArea(helpText);
        area.setEditable(false);
        area.setFont(Fonts.body(13f));
        area.setBackground(Theme.panel());
        area.setForeground(Theme.text());
        area.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(620, 340));
        sp.setBorder(BorderFactory.createLineBorder(Theme.inputLine(), 1));
        modules.common.FigmaModule.styleScrollPane(sp);
        root.add(sp, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        modules.common.FigmaModule.PixelButton closeBtn = new modules.common.FigmaModule.PixelButton("CLOSE", false);
        closeBtn.addActionListener(e -> dlg.dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        dlg.getRootPane().registerKeyboardAction(e -> dlg.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void toggleThemeWithToast() {
        Theme.toggle();
        modules.common.FigmaModule.showToast(window.isVisible() ? window : desktop,
                Theme.dark ? "Dark Mode enabled" : "Light Mode enabled");
    }

    private void adjustZoom(float delta) {
        setZoom(Fonts.scale + delta);
    }

    private void setZoom(float target) {
        float next = Math.max(0.85f, Math.min(1.30f, Math.round(target * 100f) / 100f));
        if (Math.abs(next - Fonts.scale) < 0.01f) return;
        Fonts.scale = next;
        for (ModulePanel m : MODULES) m.rebuild();
        buildWindow();
        modules.common.FigmaModule.showToast(window.isVisible() ? window : desktop,
                String.format("Zoom: %d%%", Math.round(Fonts.scale * 100)));
    }

    private JPanel buildDesktopIcons() {
        JPanel layer = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 18));
        layer.setOpaque(false);
        layer.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel grid = new JPanel(new GridLayout(4, 2, 16, 18));
        grid.setOpaque(false);
        for (int i = 0; i < MODULES.length; i++) {
            final int idx = i;
            JButton b = new JButton(MODULES[i].title(), moduleIcon(i, 44, true)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (getModel().isRollover()) {
                        g2.setColor(new Color(255, 255, 255, 75));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                    }
                    // Subtle 1px drop shadow behind the white text only (no box)
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                    int ty = getHeight() - getInsets().bottom - fm.getDescent() + 1;
                    g2.setColor(new Color(20, 12, 40, 165));
                    g2.drawString(getText(), tx + 1, ty);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            b.setVerticalTextPosition(SwingConstants.BOTTOM);
            b.setHorizontalTextPosition(SwingConstants.CENTER);
            b.setFont(Fonts.uiBold(12f));
            b.setForeground(Color.WHITE);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setBorder(new EmptyBorder(8, 10, 8, 10));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setToolTipText("Open " + MODULES[i].title() + " (Ctrl+" + (i + 1) + ")");
            b.addActionListener(e -> open(idx));
            grid.add(b);
        }
        layer.add(grid);
        return layer;
    }

    private void showStartMenu(JButton start, JPanel bar) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Theme.pink(), 2));
        menu.setBackground(Theme.panel());
        for (int i = 0; i < MODULES.length; i++) {
            final int idx = i;
            JMenuItem item = new JMenuItem(MODULES[i].title(), moduleIcon(i, 20, false));
            item.setFont(Fonts.ui(13f));
            item.setBackground(i == active ? Theme.panel2() : Theme.panel());
            item.setForeground(Theme.text());
            item.setBorder(new EmptyBorder(6, 12, 6, 16));
            item.addActionListener(ev -> open(idx));
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem themeItem = new JMenuItem(Theme.dark ? "Switch to Light Mode" : "Switch to Dark Mode");
        themeItem.setFont(Fonts.ui(12f));
        themeItem.setBackground(Theme.panel());
        themeItem.setForeground(Theme.text());
        themeItem.setBorder(new EmptyBorder(6, 12, 6, 16));
        themeItem.addActionListener(ev -> { Theme.toggle(); bar.repaint(); desktop.repaint(); });
        menu.add(themeItem);

        JMenuItem desktopItem = new JMenuItem("Show Desktop");
        desktopItem.setFont(Fonts.ui(12f));
        desktopItem.setBackground(Theme.panel());
        desktopItem.setForeground(Theme.text());
        desktopItem.setBorder(new EmptyBorder(6, 12, 6, 16));
        desktopItem.addActionListener(ev -> window.setVisible(false));
        menu.add(desktopItem);

        Dimension pref = menu.getPreferredSize();
        menu.show(start, 0, -pref.height - 4);
    }

    private void open(int i) {
        boolean wasHidden = !window.isVisible();
        active = i;
        cards.show(content, String.valueOf(i));
        if (scrollPanes[i] != null) scrollPanes[i].getVerticalScrollBar().setValue(0);
        window.setVisible(true);
        taskButton.setVisible(true);
        taskButton.setText(MODULES[i].title());
        taskButton.setIcon(LineIcons.MODULE[i].icon(15, Color.WHITE));
        taskFolder.setIcon(moduleIcon(i, 22, false));   // the taskbar folder follows the open module
        buildWindow();
        if (MODULES[i] instanceof modules.common.FigmaModule fm) {
            SwingUtilities.invokeLater(fm::bounceSticker);
        }
        if (wasHidden && centerPanel != null && !maximized) {
            animateWindowPopIn();
        }
    }

    private void animateWindowPopIn() {
        int w = centerPanel.getWidth(), h = centerPanel.getHeight();
        if (w <= 0 || h <= 0) return;
        int targetTop = (int) Math.round(h * 32 / 1037.0);
        int targetBottom = (int) Math.round(h * 33 / 1037.0);
        int side = (int) Math.round(w * 90 / 1280.0);
        final int[] step = {0};
        final int steps = 6;
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            step[0]++;
            double prog = step[0] / (double) steps;
            int extra = (int) Math.round(18 * (1.0 - prog));
            centerPanel.setBorder(new EmptyBorder(targetTop + extra, side, Math.max(0, targetBottom - extra / 2), side));
            centerPanel.revalidate();
            if (step[0] >= steps) {
                t.stop();
                centerPanel.setBorder(new EmptyBorder(targetTop, side, targetBottom, side));
                centerPanel.revalidate();
            }
        });
        t.start();
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
        start.addActionListener(e -> showStartMenu(start, bar));
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
        taskFolder.setIcon(moduleIcon(active, 22, false));
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

        bar.add(new ClockTray(
                this::toggleThemeWithToast,
                () -> {
                    desktop.toggleRain();
                    modules.common.FigmaModule.showToast(window.isVisible() ? window : desktop,
                            desktop.isRainy() ? "Rainy Weather: ON" : "Rainy Weather: OFF");
                }
        ));
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
            int pressY = getModel().isPressed() ? 1 : 0;
            g.setColor(new Color(29, 77, 23, 70));                                     // drop shadow
            g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 2, w, h, 24, 24));
            g.translate(0, pressY);
            java.awt.geom.RoundRectangle2D box = new java.awt.geom.RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 24, 24);
            Color topC = getModel().isRollover() ? new Color(0xc7f998) : new Color(0xb6f285);
            Color midC = getModel().isRollover() ? new Color(0x6fd44a) : new Color(0x5cc23a);
            g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, .55f, 1f},
                    new Color[]{topC, midC, new Color(0x3a9a2c)}));
            g.fill(box);
            g.setColor(new Color(0x2f7d25)); g.draw(box);
            // text shadow 0 1px
            g.setFont(getFont()); g.setColor(new Color(29, 77, 23, 153));
            FontMetrics fm = g.getFontMetrics();
            int tx = getInsets().left + 20 + getIconTextGap(), ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(getText(), tx, ty + 1);
            g.dispose();
            Graphics2D t = (Graphics2D) g0.create();
            t.translate(0, -1 + pressY);
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

    /** Cyan tray: weather toggle, yellow light/dark toggle, time and date. */
    private static class ClockTray extends JPanel {
        private boolean rainOn = false;
        ClockTray(Runnable toggleTheme, Runnable toggleWeather) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setBorder(new EmptyBorder(0, 12, 0, 12));

            JButton weather = new JButton() {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    java.awt.geom.Ellipse2D c = new java.awt.geom.Ellipse2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1);
                    g.setColor(rainOn ? new Color(0x4f8ef7) : new Color(255, 255, 255, getModel().isRollover() ? 235 : 190));
                    g.fill(c);
                    g.setColor(getModel().isRollover() ? Theme.pink() : Color.WHITE);
                    g.draw(c);
                    // Draw mini cloud + rain drops or sun
                    g.setColor(rainOn ? Color.WHITE : new Color(0x4a7bb8));
                    g.fillRoundRect(6, 8, 13, 6, 6, 6);
                    g.fillOval(8, 5, 7, 6);
                    if (rainOn) {
                        g.setColor(new Color(0xb6e8ff));
                        g.drawLine(9, 16, 8, 19);
                        g.drawLine(13, 16, 12, 19);
                        g.drawLine(17, 16, 16, 19);
                    }
                    g.dispose();
                }
            };
            Dimension d25 = new Dimension(25, 25);
            weather.setPreferredSize(d25); weather.setMaximumSize(d25); weather.setMinimumSize(d25);
            weather.setContentAreaFilled(false); weather.setBorderPainted(false); weather.setFocusPainted(false);
            weather.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            weather.setToolTipText("Toggle rainy weather");
            weather.addActionListener(e -> { rainOn = !rainOn; toggleWeather.run(); weather.repaint(); });
            add(weather);
            add(Box.createHorizontalStrut(6));

            JButton toggle = new JButton() {
                private final Icon moonIcon = LineIcons.MOON.icon(14, null);
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    java.awt.geom.Ellipse2D c = new java.awt.geom.Ellipse2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1);
                    g.setPaint(new GradientPaint(0, 0,
                            getModel().isRollover() ? Color.WHITE : new Color(0xfff9c4),
                            0, getHeight(), new Color(0xffd95e)));
                    g.fill(c);
                    g.setColor(getModel().isRollover() ? Theme.pink() : Color.WHITE); g.draw(c);
                    if (Theme.dark) {
                        // Crisp sun icon in dark mode (click to switch to Light Mode)
                        g.setColor(new Color(0xd86b00));
                        g.fillOval(8, 8, 9, 9);
                        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        for (int a = 0; a < 8; a++) {
                            double ang = a * Math.PI / 4.0;
                            int x1 = (int) Math.round(12.5 + Math.cos(ang) * 6.0);
                            int y1 = (int) Math.round(12.5 + Math.sin(ang) * 6.0);
                            int x2 = (int) Math.round(12.5 + Math.cos(ang) * 8.0);
                            int y2 = (int) Math.round(12.5 + Math.sin(ang) * 8.0);
                            g.drawLine(x1, y1, x2, y2);
                        }
                    } else {
                        moonIcon.paintIcon(this, g, (getWidth() - 14) / 2, (getHeight() - 14) / 2);
                    }
                    g.dispose();
                }
            };
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
        JButton b = new JButton(s) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean hover = getModel().isRollover();
                boolean pressed = getModel().isPressed();
                if (pressed) g.translate(0, 1);
                Color fill = hover ? c.brighter() : c;
                g.setColor(fill);
                g.fillRoundRect(0, 0, w - 1, h - 2, 10, 10);
                g.setColor(new Color(255, 255, 255, hover ? 200 : 120));
                g.drawRoundRect(0, 0, w - 1, h - 2, 10, 10);
                g.setFont(getFont());
                g.setColor(Color.WHITE);
                FontMetrics fm = g.getFontMetrics();
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h - 2 - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(getText(), tx, ty);
                g.dispose();
            }
        };
        b.setFont(Fonts.uiBold(11f));
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMargin(new Insets(2, 8, 3, 8));
        return b;
    }

    // ======================= ANIMATED MODULE SPRITES =======================

    /** Seconds since start-up; every sprite reads the same clock so they stay in step. */
    private static final long T0 = System.nanoTime();
    private static double clock() { return (System.nanoTime() - T0) / 1_000_000_000.0; }

    private static final java.util.List<Sprite> SPRITES = new java.util.ArrayList<>();
    private static javax.swing.Timer spriteTimer;

    /** Repaints only the buttons whose sprite is actually moving, at the desktop's own cadence. */
    private static void startSpriteClock() {
        if (spriteTimer != null) return;
        spriteTimer = new javax.swing.Timer(45, e -> {
            java.util.Set<Component> dirty = new java.util.LinkedHashSet<>();
            boolean crowded = SPRITES.size() > 64;     // many rebuilds (theme toggles) have piled up
            for (java.util.Iterator<Sprite> it = SPRITES.iterator(); it.hasNext(); ) {
                Sprite s = it.next();
                if (s.host == null) {
                    if (crowded) it.remove();          // built but never shown, e.g. a start menu that was closed
                    continue;
                }
                if (!s.host.isShowing()) { it.remove(); continue; }   // sidebar/desktop was rebuilt
                if (s.moving()) dirty.add(s.host);
            }
            for (Component c : dirty) c.repaint();
        });
        spriteTimer.start();
    }

    /**
     * A module's pixel-art sprite, fitted to a square box so every icon is the same size
     * whatever shape the artwork is. Desktop icons drift up and down on their own; every
     * sprite leans in and tips a little while the pointer is on it, and squashes on click.
     */
    private static class Sprite implements Icon {
        private static final java.util.Map<String, java.awt.image.BufferedImage> CACHE = new java.util.HashMap<>();
        private final java.awt.image.BufferedImage art;
        private final int box;
        private final double phase;      // keeps the eight icons from bobbing in lockstep
        private final boolean drifts;
        private Component host;
        private double scale = 1, lean;

        Sprite(String name, int box, int index, boolean drifts) {
            this.box = box;
            this.drifts = drifts;
            this.phase = index * 0.8;
            String key = name + "@" + box;
            this.art = CACHE.computeIfAbsent(key, k -> {
                java.awt.image.BufferedImage src = null;
                for (String suffix : new String[]{"@8x", "@4x", "@3x", "@2x", "@1x", ""}) {
                    File f = Fonts.file("assets/images/" + name + suffix + ".png");
                    if (!f.exists()) continue;
                    try { src = ImageIO.read(f); break; } catch (Exception ignored) { }
                }
                return src == null ? null : fit(src, box * 2);
            });
        }

        /** Halving step by step keeps a 500px sprite readable at 40px; one big jump aliases it. */
        private static java.awt.image.BufferedImage fit(java.awt.image.BufferedImage src, int side) {
            double k = side / (double) Math.max(src.getWidth(), src.getHeight());
            int w = Math.max(1, (int) Math.round(src.getWidth() * k));
            int h = Math.max(1, (int) Math.round(src.getHeight() * k));
            java.awt.image.BufferedImage cur = src;
            while (cur.getWidth() / 2 > w && cur.getHeight() / 2 > h) {
                cur = step(cur, cur.getWidth() / 2, cur.getHeight() / 2);
            }
            return step(cur, w, h);
        }

        private static java.awt.image.BufferedImage step(java.awt.image.BufferedImage src, int w, int h) {
            java.awt.image.BufferedImage out =
                    new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            return out;
        }

        private boolean hot() {
            return host instanceof AbstractButton b && b.getModel().isRollover() && b.isEnabled();
        }

        private boolean pressed() {
            return host instanceof AbstractButton b && b.getModel().isPressed();
        }

        /** True while there is still something to redraw, so idle icons cost nothing. */
        boolean moving() {
            return drifts || hot() || pressed() || Math.abs(scale - 1) > 0.002 || Math.abs(lean) > 0.001;
        }

        @Override public int getIconWidth()  { return box; }
        @Override public int getIconHeight() { return box; }

        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            host = c;
            if (art == null) return;
            double t = clock();

            double wantScale = pressed() ? 0.90 : hot() ? 1.16 : 1.0;
            double wantLean = hot() ? Math.sin(t * 7.5 + phase) * 0.09 : 0;
            scale += (wantScale - scale) * 0.28;          // ease, so it never snaps
            lean += (wantLean - lean) * 0.35;

            double bob = drifts ? Math.sin(t * 1.7 + phase) * 1.8 : 0;
            if (hot()) bob -= 2.0;                        // lifts towards the pointer

            double side = box * scale;
            double cx = x + box / 2.0, cy = y + box / 2.0 + bob;

            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (Math.abs(lean) > 0.001) g.rotate(lean, cx, cy);
            g.drawImage(art, (int) Math.round(cx - side / 2), (int) Math.round(cy - side / 2),
                    (int) Math.round(side), (int) Math.round(side), null);
            g.dispose();
        }
    }

    /** The sprite for module i, sized to a square box. Desktop icons drift, list icons do not. */
    private Icon moduleIcon(int i, int box, boolean drifts) {
        Sprite s = new Sprite("icon_" + FOLDERS[i], box, i, drifts);
        SPRITES.add(s);
        return s;
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
