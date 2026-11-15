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
    private final JPanel sidebar = new JPanel();
    private final JLabel titleLabel = new JLabel();
    private final JButton taskButton = new JButton();
    private int active = 0;

    public MiniOS() {
        super("Mini OS – OS Lab");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 860);
        setLocationRelativeTo(null);
        try { setIconImage(ImageIO.read(new File("assets/images/app_icon.png"))); } catch (Exception ignored) {}

        setContentPane(desktop);
        desktop.setLayout(new BorderLayout());

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(24, 60, 16, 60));
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

    private void buildSidebar() {
        sidebar.removeAll();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.sideTop());
        sidebar.setBorder(new EmptyBorder(14, 10, 14, 10));
        sidebar.setPreferredSize(new Dimension(250, 0));
        JLabel brand = new JLabel("OS Lab", icon("heron_bust.png"), SwingConstants.LEFT);
        brand.setFont(Fonts.pixel(24f)); brand.setForeground(Theme.dark ? new Color(0xd7b4ff) : new Color(0x6a2ca0));
        brand.setIconTextGap(12); brand.setBorder(new EmptyBorder(0, 4, 14, 0));
        sidebar.add(brand);
        for (int i = 0; i < MODULES.length; i++) {
            final int idx = i;
            JButton b = new JButton(MODULES[i].title(), icon("folder_" + FOLDERS[i] + ".png"));
            b.setFont(Fonts.ui(14f)); b.setForeground(Theme.text());
            b.setHorizontalAlignment(SwingConstants.LEFT); b.setIconTextGap(12);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            b.setFocusPainted(false); b.setBorderPainted(i == active);
            b.setContentAreaFilled(i == active); b.setBackground(Theme.panel());
            b.setBorder(BorderFactory.createMatteBorder(1, 5, 1, 1, Theme.pink()));
            b.addActionListener(e -> open(idx));
            sidebar.add(b); sidebar.add(Box.createVerticalStrut(4));
        }
    }

    private void open(int i) {
        active = i;
        cards.show(content, String.valueOf(i));
        window.setVisible(true);
        taskButton.setVisible(true);
        taskButton.setText(MODULES[i].title());
        buildWindow();
    }

    private JComponent taskbar() {
        JPanel bar = gradientPanel(Theme.barTop(), Theme.barBottom());
        bar.setLayout(new BorderLayout());
        bar.setBorder(new EmptyBorder(4, 6, 4, 6));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); left.setOpaque(false);
        JButton start = new JButton("start");
        start.setFont(Fonts.ui(16f)); start.setForeground(Color.WHITE); start.setBackground(new Color(0x5cc23a));
        start.setFocusPainted(false);
        start.addActionListener(e -> open(window.isVisible() ? active : 0)); // start opens the window
        taskButton.setFont(Fonts.body(13f)); taskButton.setFocusPainted(false);
        taskButton.addActionListener(e -> window.setVisible(!window.isVisible()));
        left.add(start); left.add(new JLabel(icon("folder_banker.png"))); left.add(taskButton);
        bar.add(left, BorderLayout.WEST);

        JPanel tray = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); tray.setOpaque(false);
        JButton theme = new JButton("☾");
        theme.setFocusPainted(false);
        theme.addActionListener(e -> { Theme.toggle(); theme.setText(Theme.dark ? "☀" : "☾"); bar.repaint(); desktop.repaint(); });
        JLabel clock = new JLabel(); clock.setFont(Fonts.ui(13f)); clock.setForeground(Color.WHITE);
        new Timer(1000, e -> clock.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))).start();
        tray.add(theme); tray.add(clock);
        bar.add(tray, BorderLayout.EAST);
        return bar;
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

    private static ImageIcon icon(String file) {
        try { return new ImageIcon(ImageIO.read(new File("assets/images/" + file))); }
        catch (Exception e) { return null; }
    }
}
