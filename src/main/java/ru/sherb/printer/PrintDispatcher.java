package ru.sherb.printer;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * @author maksim
 * @since 31.12.2020
 */
public interface PrintDispatcher {

    void addToPrint(Printable document);

    void cancelCurrent();

    List<Printable> stopPrint();

    List<Printable> listPrinted(Comparator<Printable> customComparator);

    default List<Printable> listPrinted() {
        return listPrinted(Comparator.comparing(Printable::name));
    }

    Duration avgPrintedTime();
}
