package ru.sherb.printer;

import java.time.Duration;

/**
 * @author maksim
 * @since 31.12.2020
 */
public interface Printable {

    String name();

    PaperSize size();

    Duration printDuration();
}
