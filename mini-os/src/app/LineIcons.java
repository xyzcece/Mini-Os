package app;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * Line icons from the Figma "mini-os" file (taskbar / title bar), drawn as vectors so they stay sharp
 * at any Windows scaling. Path data is copied from the design's SVG exports (absolute M H V L C Z only).
 * OWNER: Member 3. Generated from the Figma SVGs.
 */
public final class LineIcons {
    /** One SVG path: path data, stroke width, stroke colour, fill colour (-1 = none). */
    record P(String d, float strokeWidth, int stroke, int fill) { }

    public static final LineIcons CPU = new LineIcons(15.0f, 15.0f,
            new P("M4.375 3.125H10.625C11.3154 3.125 11.875 3.68464 11.875 4.375V10.625C11.875 11.3154 11.3154 11.875 10.625 11.875H4.375C3.68464 11.875 3.125 11.3154 3.125 10.625V4.375C3.125 3.68464 3.68464 3.125 4.375 3.125Z", 1.125f, 0xFFFFFF, -1),
            new P("M5.625 0.625V3.125M9.375 0.625V3.125M5.625 11.875V14.375M9.375 11.875V14.375M0.625 5.625H3.125M0.625 9.375H3.125M11.875 5.625H14.375M11.875 9.375H14.375M5.625 5.625H9.375V9.375H5.625V5.625Z", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons BANKER = new LineIcons(15.0f, 15.0f,
            new P("M4.375 6.25H2.5V12.5H12.5V6.25H10.625M4.375 6.25H10.625M4.375 6.25V4.375C4.375 3.5462 4.70424 2.75134 5.29029 2.16529C5.87634 1.57924 6.6712 1.25 7.5 1.25C8.3288 1.25 9.12366 1.57924 9.70971 2.16529C10.2958 2.75134 10.625 3.5462 10.625 4.375V6.25", 1.125f, 0xFFFFFF, -1),
            new P("M5.625 9.375H7.5M7.5 9.375H9.375M7.5 9.375V7.5M7.5 9.375V11.25", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons PAGING = new LineIcons(15.0f, 15.0f,
            new P("M3.125 1.875H10L11.875 3.75V13.125H3.125V1.875Z", 1.125f, 0xFFFFFF, -1),
            new P("M10 1.875V4.375H12.5M5 6.875H10M5 9.375H10", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons REPLACEMENT = new LineIcons(15.0f, 15.0f,
            new P("M2.5 3.125H5.625C5.97018 3.125 6.25 3.40482 6.25 3.75V11.25C6.25 11.5952 5.97018 11.875 5.625 11.875H2.5C2.15482 11.875 1.875 11.5952 1.875 11.25V3.75C1.875 3.40482 2.15482 3.125 2.5 3.125Z", 1.125f, 0xFFFFFF, -1),
            new P("M9.375 3.125H12.5C12.8452 3.125 13.125 3.40482 13.125 3.75V11.25C13.125 11.5952 12.8452 11.875 12.5 11.875H9.375C9.02982 11.875 8.75 11.5952 8.75 11.25V3.75C8.75 3.40482 9.02982 3.125 9.375 3.125Z", 1.125f, 0xFFFFFF, -1),
            new P("M6.875 5.625L8.125 4.375M6.875 9.375L8.125 10.625", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons SEGMENTS = new LineIcons(15.0f, 15.0f,
            new P("M2.5 2.5H12.5C12.8452 2.5 13.125 2.77982 13.125 3.125V4.375C13.125 4.72018 12.8452 5 12.5 5H2.5C2.15482 5 1.875 4.72018 1.875 4.375V3.125C1.875 2.77982 2.15482 2.5 2.5 2.5Z", 1.125f, 0xFFFFFF, -1),
            new P("M2.5 6.25H8.75C9.09518 6.25 9.375 6.52982 9.375 6.875V8.125C9.375 8.47018 9.09518 8.75 8.75 8.75H2.5C2.15482 8.75 1.875 8.47018 1.875 8.125V6.875C1.875 6.52982 2.15482 6.25 2.5 6.25Z", 1.125f, 0xFFFFFF, -1),
            new P("M2.5 10H11.25C11.5952 10 11.875 10.2798 11.875 10.625V11.875C11.875 12.2202 11.5952 12.5 11.25 12.5H2.5C2.15482 12.5 1.875 12.2202 1.875 11.875V10.625C1.875 10.2798 2.15482 10 2.5 10Z", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons FRAGMENTATION = new LineIcons(15.0f, 15.0f,
            new P("M3.125 2.5H11.875C12.5654 2.5 13.125 3.05964 13.125 3.75V11.25C13.125 11.9404 12.5654 12.5 11.875 12.5H3.125C2.43464 12.5 1.875 11.9404 1.875 11.25V3.75C1.875 3.05964 2.43464 2.5 3.125 2.5Z", 1.125f, 0xFFFFFF, -1),
            new P("M1.875 5.625H6.875M6.875 5.625V8.75H9.375M6.875 5.625V2.5M9.375 8.75H13.125M9.375 8.75V12.5", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons DISK = new LineIcons(15.0f, 15.0f,
            new P("M13.125 7.5C13.125 10.6066 10.6066 13.125 7.5 13.125C4.3934 13.125 1.875 10.6066 1.875 7.5C1.875 4.3934 4.3934 1.875 7.5 1.875C10.6066 1.875 13.125 4.3934 13.125 7.5Z", 1.125f, 0xFFFFFF, -1),
            new P("M8.75 7.5C8.75 8.19036 8.19036 8.75 7.5 8.75C6.80964 8.75 6.25 8.19036 6.25 7.5C6.25 6.80964 6.80964 6.25 7.5 6.25C8.19036 6.25 8.75 6.80964 8.75 7.5Z", 1.125f, 0xFFFFFF, -1),
            new P("M11.25 3.75L8.125 6.875", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons MULTI = new LineIcons(15.0f, 15.0f,
            new P("M2.5 2.5H6.25C6.59518 2.5 6.875 2.77982 6.875 3.125V6.25C6.875 6.59518 6.59518 6.875 6.25 6.875H2.5C2.15482 6.875 1.875 6.59518 1.875 6.25V3.125C1.875 2.77982 2.15482 2.5 2.5 2.5Z", 1.125f, 0xFFFFFF, -1),
            new P("M8.75 2.5H12.5C12.8452 2.5 13.125 2.77982 13.125 3.125V6.25C13.125 6.59518 12.8452 6.875 12.5 6.875H8.75C8.40482 6.875 8.125 6.59518 8.125 6.25V3.125C8.125 2.77982 8.40482 2.5 8.75 2.5Z", 1.125f, 0xFFFFFF, -1),
            new P("M2.5 8.125H6.25C6.59518 8.125 6.875 8.40482 6.875 8.75V11.875C6.875 12.2202 6.59518 12.5 6.25 12.5H2.5C2.15482 12.5 1.875 12.2202 1.875 11.875V8.75C1.875 8.40482 2.15482 8.125 2.5 8.125Z", 1.125f, 0xFFFFFF, -1),
            new P("M8.75 8.125H12.5C12.8452 8.125 13.125 8.40482 13.125 8.75V11.875C13.125 12.2202 12.8452 12.5 12.5 12.5H8.75C8.40482 12.5 8.125 12.2202 8.125 11.875V8.75C8.125 8.40482 8.40482 8.125 8.75 8.125Z", 1.125f, 0xFFFFFF, -1));
    public static final LineIcons START = new LineIcons(20.0f, 20.0f,
            new P("M2.5 3.75L8.33333 2.5V9.16667H2.5V3.75ZM10 2.25L17.5 1.25V9.16667H10V2.25ZM2.5 10.8333H8.33333V17.5L2.5 16.25V10.8333ZM10 10.8333H17.5V18.75L10 17.75V10.8333Z", 1.5f, 0xFFFFFF, -1));
    public static final LineIcons MOON = new LineIcons(14.0f, 14.0f,
            new P("M11.6667 8.86667C10.7525 9.26805 9.73841 9.38409 8.75718 9.1996C7.77595 9.01512 6.87327 8.53871 6.16728 7.83272C5.46129 7.12673 4.98488 6.22405 4.8004 5.24282C4.61591 4.26159 4.73195 3.24752 5.13333 2.33333C4.39479 2.65759 3.74651 3.15713 3.24471 3.78862C2.7429 4.42011 2.40273 5.16448 2.25369 5.95718C2.10466 6.74988 2.15125 7.56697 2.38945 8.33759C2.62764 9.1082 3.05023 9.80908 3.62058 10.3794C4.19092 10.9498 4.8918 11.3724 5.66241 11.6106C6.43303 11.8487 7.25012 11.8953 8.04282 11.7463C8.83552 11.5973 9.57989 11.2571 10.2114 10.7553C10.8429 10.2535 11.3424 9.60521 11.6667 8.86667Z", 1.05f, 0x5B3A6E, -1));

    /** Module icons in the same order as MiniOS.MODULES. */
    public static final LineIcons[] MODULE = {CPU, BANKER, PAGING, REPLACEMENT, SEGMENTS, FRAGMENTATION, DISK, MULTI};

    private final float w, h; private final P[] parts; private final Shape[] shapes;

    private LineIcons(float w, float h, P... parts) {
        this.w = w; this.h = h; this.parts = parts;
        shapes = new Shape[parts.length];
        for (int i = 0; i < parts.length; i++) shapes[i] = parse(parts[i].d());
    }

    /** An Icon of this drawing at size x size pixels (colour == null keeps the design colours). */
    public Icon icon(int size, Color colour) {
        return new Icon() {
            @Override public int getIconWidth()  { return size; }
            @Override public int getIconHeight() { return size; }
            @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.translate(x, y);
                g.scale(size / w, size / h);
                for (int i = 0; i < parts.length; i++) {
                    P p = parts[i];
                    if (p.fill() >= 0) { g.setColor(colour != null ? colour : new Color(p.fill())); g.fill(shapes[i]); }
                    if (p.stroke() >= 0 && p.strokeWidth() > 0) {
                        g.setColor(colour != null ? colour : new Color(p.stroke()));
                        g.setStroke(new BasicStroke(p.strokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.draw(shapes[i]);
                    }
                }
                g.dispose();
            }
        };
    }

    /** Minimal SVG path parser for absolute M, L, H, V, C and Z commands. */
    private static Shape parse(String d) {
        Path2D.Double p = new Path2D.Double();
        java.util.regex.Matcher t = java.util.regex.Pattern.compile("[MLHVCZ]|-?\\d*\\.?\\d+(?:e-?\\d+)?").matcher(d);
        java.util.List<String> tok = new java.util.ArrayList<>();
        while (t.find()) tok.add(t.group());
        char cmd = 'M'; double cx = 0, cy = 0;
        for (int i = 0; i < tok.size(); ) {
            String s = tok.get(i);
            if (Character.isLetter(s.charAt(0))) { cmd = s.charAt(0); i++; if (cmd == 'Z') { p.closePath(); } continue; }
            switch (cmd) {
                case 'M' -> { cx = num(tok, i); cy = num(tok, i + 1); p.moveTo(cx, cy); i += 2; cmd = 'L'; }
                case 'L' -> { cx = num(tok, i); cy = num(tok, i + 1); p.lineTo(cx, cy); i += 2; }
                case 'H' -> { cx = num(tok, i); p.lineTo(cx, cy); i += 1; }
                case 'V' -> { cy = num(tok, i); p.lineTo(cx, cy); i += 1; }
                case 'C' -> { p.curveTo(num(tok, i), num(tok, i + 1), num(tok, i + 2), num(tok, i + 3), num(tok, i + 4), num(tok, i + 5));
                              cx = num(tok, i + 4); cy = num(tok, i + 5); i += 6; }
                default -> i++;
            }
        }
        return p;
    }
    private static double num(java.util.List<String> t, int i) { return Double.parseDouble(t.get(i)); }
}
