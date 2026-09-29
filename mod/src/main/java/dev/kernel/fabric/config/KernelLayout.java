package dev.kernel.fabric.config;

/**
 * The geometry every Kernel settings page is drawn into.
 *
 * <p>One frame, divided the way VulkanMod divides its video settings: a header band, a row of tabs
 * across the top, the scrolling option list filling the left of the body, a description panel down the
 * whole right-hand side, and an action bar along the bottom. The description sits beside the list rather
 * than under it so it can hold several lines without stealing rows from the list, and so the list keeps
 * the full height of the body.
 *
 * <p>Both the video settings and the shader browser use this, which is what keeps the two pages looking
 * like one screen. It is a pure function of the window size and holds no widget state, so a screen can
 * recompute it on every {@code init} without carrying layout fields of its own.
 */
public record KernelLayout(int left, int width, int headerTop, int headerBottom, int tabTop, int tabHeight,
                           int contentTop, int contentBottom, int listWidth, int trackX, int trackWidth,
                           int detailX, int detailWidth, int actionsY, int actionHeight) {
    /** Widest the frame is allowed to get, so the list does not stretch into unreadable rows. */
    private static final int MAX_WIDTH = 820;
    private static final int GAP = 8;

    public static KernelLayout of(int screenWidth, int screenHeight) {
        int width = Math.max(200, Math.min(MAX_WIDTH, screenWidth - 24));
        int left = (screenWidth - width) / 2;
        int headerTop = 10, headerBottom = 44;
        int tabTop = headerBottom + 6, tabHeight = 22;
        int contentTop = tabTop + tabHeight + 6;
        int actionHeight = 22;
        int actionsY = Math.max(contentTop + 24, screenHeight - 28);
        int contentBottom = Math.max(contentTop + 24, actionsY - GAP);
        // The panel takes a share of the frame rather than a fixed width, so a narrow window keeps a
        // usable list and a wide one keeps the description from running to unreadable line lengths.
        int detailWidth = Math.clamp(width * 3 / 10, 110, 260);
        int trackWidth = 4;
        int listWidth = Math.max(80, width - detailWidth - GAP - trackWidth - 2);
        return new KernelLayout(left, width, headerTop, headerBottom, tabTop, tabHeight, contentTop, contentBottom,
            listWidth, left + listWidth + 2, trackWidth, left + width - detailWidth, detailWidth, actionsY, actionHeight);
    }

    /** The right edge of the frame, where the action bar's last button ends. */
    public int right() { return left + width; }

    /** Height available to the list, and to the description panel beside it. */
    public int contentHeight() { return contentBottom - contentTop; }

    /** The x of one tab in a row of {@code count} evenly divided tabs. */
    public int tabX(int index, int count) { return left + index * (width + 2) / count; }

    /** The width of one tab, which absorbs the rounding so the last tab still ends at the frame edge. */
    public int tabWidth(int index, int count) { return tabX(index + 1, count) - tabX(index, count) - 2; }

    /** Width for one of {@code count} buttons sharing the right of the action bar. */
    public int buttonWidth(int count) { return Math.clamp((width - 100) / Math.max(1, count) - 4, 40, 78); }
}
