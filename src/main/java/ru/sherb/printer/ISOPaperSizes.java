package ru.sherb.printer;

/**
 * Default piper sizes defined in ISO 216
 *
 * @author maksim
 * @since 31.12.2020
 */
public enum ISOPaperSizes implements PaperSize {
    A0(841, 1189),
    A1(594, 841),
    A2(420, 594),
    A3(297, 420),
    A4(210, 297),
    A5(148, 210),
    A6(105, 148),
    A7(74,  105),
    A8(52,  74),
    A9(37,  52),
    A10(26, 37);

    private final int width;
    private final int height;

    ISOPaperSizes(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }
}
