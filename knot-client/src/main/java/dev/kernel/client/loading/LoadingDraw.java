package dev.kernel.client.loading;

import java.util.Arrays;
import java.util.Locale;

/** Original small bitmap lettering and rectangle commands, available before Minecraft loads any font. */
public final class LoadingDraw {
    private static final ThreadLocal<Canvas> CANVAS = ThreadLocal.withInitial(Canvas::new);
    private LoadingDraw() {}

    /** Index zero is the used length; following commands are x, y, width, height, ARGB. Thread-confined scratch. */
    public static int[] frame(int width, int height, double progress, long millis) {
        Canvas canvas = CANVAS.get(); canvas.length = 1;
        canvas.rect(0, 0, width, height, 0xFF090A0C);
        int scale = Math.max(1, Math.min(3, Math.min(width / 420, height / 220)));
        int center = width / 2, top = Math.max(12, height / 2 - 86 * scale);
        int size = 58 * scale;
        // The approved bolt's six-point silhouette, scan-converted into horizontal runs.
        double[] xs = {0.628, 0.536, 0.688, 0.365, 0.459, 0.304};
        double[] ys = {0.042, 0.390, 0.390, 0.900, 0.565, 0.565};
        for (int y = 0; y < size; y++) {
            double scan = (y + 0.5) / size, min = 1, max = 0;
            for (int edge = 0; edge < 6; edge++) {
                int next = (edge + 1) % 6;
                if ((ys[edge] <= scan && ys[next] > scan) || (ys[next] <= scan && ys[edge] > scan)) {
                    double hit = xs[edge] + (scan - ys[edge]) * (xs[next] - xs[edge]) / (ys[next] - ys[edge]);
                    min = Math.min(min, hit); max = Math.max(max, hit);
                }
            }
            if (max > min) canvas.rect(center - size / 2 + (int) (min * size), top + y, Math.max(1, (int) ((max - min) * size)), 1, 0xFFFFFFFF);
        }
        canvas.center("K E R N E L", center, top + size + 8 * scale, scale * 2, 0xFFFFFFFF);
        boolean determinate = Double.isFinite(progress) && progress >= 0;
        String phase = determinate ? "LOADING RESOURCES  " + (int) (Math.clamp(progress, 0, 1) * 100) + "%" : StartupProgress.phase();
        canvas.center(phase, center, top + size + 40 * scale, scale, 0xFFE4E6E9);
        int barWidth = Math.min(width - 48, 240 * scale), barX = center - barWidth / 2, barY = top + size + 58 * scale;
        canvas.rect(barX, barY, barWidth, 3 * scale, 0xFF35383E);
        if (determinate) canvas.rect(barX, barY, (int) (barWidth * Math.clamp(progress, 0, 1)), 3 * scale, 0xFFFFFFFF);
        else {
            int segment = Math.max(1, barWidth / 5), range = Math.max(1, barWidth - segment);
            int offset = (int) ((millis / 6) % (range * 2)); if (offset > range) offset = range * 2 - offset;
            canvas.rect(barX + offset, barY, segment, 3 * scale, 0xFFFFFFFF);
        }
        canvas.center(shorten(StartupProgress.detail(), Math.max(8, (width - 40) / (6 * scale))), center, barY + 18 * scale, scale, 0xFFAEB4BE);
        if (!determinate) canvas.center(StartupProgress.classes() + " CLASSES LOADED", center, barY + 33 * scale, scale, 0xFF747C87);
        canvas.data[0] = canvas.length;
        return canvas.data;
    }

    private static String shorten(String text, int limit) {
        if (text.length() <= limit) return text;
        int separator = text.indexOf(": ");
        String prefix = separator >= 0 && separator < limit / 2 ? text.substring(0, separator + 2) : "";
        return prefix + "..." + text.substring(text.length() - limit + prefix.length() + 3);
    }

    private static final class Canvas {
        int[] data = new int[8192]; int length = 1;
        void rect(int x, int y, int width, int height, int color) {
            if (width <= 0 || height <= 0) return;
            if (length + 5 > data.length) data = Arrays.copyOf(data, data.length * 2);
            data[length++] = x; data[length++] = y; data[length++] = width; data[length++] = height; data[length++] = color;
        }
        void center(String text, int center, int y, int scale, int color) {
            String upper = text.toUpperCase(Locale.ROOT);
            int x = center - (upper.length() * 6 - 1) * scale / 2;
            for (int i = 0; i < upper.length(); i++) {
                long glyph = glyph(upper.charAt(i));
                for (int row = 0; row < 7; row++) {
                    int bits = (int) (glyph >>> (row * 5)) & 31;
                    for (int column = 0; column < 5;) {
                        if ((bits & (1 << (4 - column))) == 0) { column++; continue; }
                        int start = column++;
                        while (column < 5 && (bits & (1 << (4 - column))) != 0) column++;
                        rect(x + (i * 6 + start) * scale, y + row * scale, (column - start) * scale, scale, color);
                    }
                }
            }
        }
    }

    private static long rows(int a, int b, int c, int d, int e, int f, int g) {
        return a | (long) b << 5 | (long) c << 10 | (long) d << 15 | (long) e << 20 | (long) f << 25 | (long) g << 30;
    }
    private static long glyph(char c) {
        return switch (c) {
            case 'A' -> rows(14,17,17,31,17,17,17); case 'B' -> rows(30,17,17,30,17,17,30);
            case 'C' -> rows(14,17,16,16,16,17,14); case 'D' -> rows(30,17,17,17,17,17,30);
            case 'E' -> rows(31,16,16,30,16,16,31); case 'F' -> rows(31,16,16,30,16,16,16);
            case 'G' -> rows(14,17,16,23,17,17,15); case 'H' -> rows(17,17,17,31,17,17,17);
            case 'I' -> rows(14,4,4,4,4,4,14); case 'J' -> rows(7,2,2,2,18,18,12);
            case 'K' -> rows(17,18,20,24,20,18,17); case 'L' -> rows(16,16,16,16,16,16,31);
            case 'M' -> rows(17,27,21,21,17,17,17); case 'N' -> rows(17,25,21,19,17,17,17);
            case 'O' -> rows(14,17,17,17,17,17,14); case 'P' -> rows(30,17,17,30,16,16,16);
            case 'Q' -> rows(14,17,17,17,21,18,13); case 'R' -> rows(30,17,17,30,20,18,17);
            case 'S' -> rows(15,16,16,14,1,1,30); case 'T' -> rows(31,4,4,4,4,4,4);
            case 'U' -> rows(17,17,17,17,17,17,14); case 'V' -> rows(17,17,17,17,17,10,4);
            case 'W' -> rows(17,17,17,21,21,21,10); case 'X' -> rows(17,17,10,4,10,17,17);
            case 'Y' -> rows(17,17,10,4,4,4,4); case 'Z' -> rows(31,1,2,4,8,16,31);
            case '0' -> rows(14,17,19,21,25,17,14); case '1' -> rows(4,12,4,4,4,4,14);
            case '2' -> rows(14,17,1,2,4,8,31); case '3' -> rows(30,1,1,14,1,1,30);
            case '4' -> rows(2,6,10,18,31,2,2); case '5' -> rows(31,16,16,30,1,1,30);
            case '6' -> rows(14,16,16,30,17,17,14); case '7' -> rows(31,1,2,4,8,8,8);
            case '8' -> rows(14,17,17,14,17,17,14); case '9' -> rows(14,17,17,15,1,1,14);
            case '.' -> rows(0,0,0,0,0,6,6); case ':' -> rows(0,6,6,0,6,6,0);
            case '-' -> rows(0,0,0,31,0,0,0); case '_' -> rows(0,0,0,0,0,0,31);
            case '/' -> rows(1,1,2,4,8,16,16); case '$' -> rows(4,15,20,14,5,30,4);
            case '%' -> rows(25,25,2,4,8,19,19); case ' ' -> 0;
            default -> rows(14,17,1,2,4,0,4);
        };
    }
}
