package ru.sherb.printer;

/**
 * @author maksim
 * @since 31.12.2020
 */
public interface Printer {

    class PrintException extends RuntimeException {}

    void print(Printable document) throws PrintException, InterruptedException;

    void stop();
}
