package ru.sherb.actors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import ru.sherb.printer.ISOPaperSizes;
import ru.sherb.printer.Printable;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author maksim
 * @since 01.01.2021
 */
class PrintDispatchActorFacadeTest {

    @Test
    public void testPrintOneFile() throws InterruptedException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var expectedDocument = new MockDocument().name("test document");

        // When
        printDispatcher.addToPrint(expectedDocument);

        // Then
        assertEquals(expectedDocument, printer.printedDocument());

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testPrintMultipleFiles() throws InterruptedException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);

        var documents = Stream
                .iterate(0, i -> i + 1)
                .limit(10)
                .map(i -> new MockDocument().name(String.valueOf(i)))
                .collect(Collectors.toList());

        // When
        documents.forEach(printDispatcher::addToPrint); // from 0 to 9

        // Then
        assertEquals(documents.get(0), printer.printedDocument());
        assertEquals(documents.get(1), printer.printedDocument());
        assertEquals(documents.get(2), printer.printedDocument());
        assertEquals(documents.get(3), printer.printedDocument());
        assertEquals(documents.get(4), printer.printedDocument());
        assertEquals(documents.get(5), printer.printedDocument());
        assertEquals(documents.get(6), printer.printedDocument());
        assertEquals(documents.get(7), printer.printedDocument());
        assertEquals(documents.get(8), printer.printedDocument());
        assertEquals(documents.get(9), printer.printedDocument());

        List<Printable> printed = printDispatcher.listPrinted();
        assertEquals(10, printed.size());

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testStopInFilledQueue() throws InterruptedException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var first = new MockDocument().name("first");
        var second = new MockDocument().name("second");
        printDispatcher.addToPrint(first);
        printDispatcher.addToPrint(second);
        printer.waitForStartPrinting();

        // When
        var actualDocs = printDispatcher.stopPrint();

        // Then
        assertEquals(2, actualDocs.size(), actualDocs::toString);
        assertEquals(first, actualDocs.get(0));
        assertEquals(second, actualDocs.get(1));

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testStopEmptyQueue() {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);

        // When
        var actualDocs = printDispatcher.stopPrint();

        // Then
        assertNotNull(actualDocs);
        assertEquals(0, actualDocs.size());

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testCancelActiveDoc() throws InterruptedException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var expectedDocument = new MockDocument().name("test document");
        printDispatcher.addToPrint(expectedDocument);
        printer.waitForStartPrinting();

        // When
        printDispatcher.cancelCurrent();

        // Then
        assertTrue(printDispatcher.listPrinted().stream()
            .noneMatch(Predicate.isEqual(expectedDocument)));

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testSuccessPrintAfterCancelDoc() throws InterruptedException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var cancelled = new MockDocument().name("cancelled");
        var printed = new MockDocument().name("printed");

        printDispatcher.addToPrint(cancelled);
        printer.waitForStartPrinting();
        printDispatcher.cancelCurrent();
        printer.waitForCancel();

        // When
        printDispatcher.addToPrint(printed);

        // Then
        assertEquals(printed, printer.printedDocument());

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testGetPrintedOrderDocs() throws InterruptedException, ExecutionException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var first = new MockDocument().name("a");
        var second = new MockDocument().name("b");
        printDispatcher.addToPrint(first);
        var handle = printDispatcher.addToPrintAndWait(second);
        printer.skip();
        printer.skip();
        handle.get();

        // When
        var printedList = printDispatcher.listPrinted();

        // Then
        assertEquals(2, printedList.size());
        assertEquals(first, printedList.get(0));
        assertEquals(second, printedList.get(1));

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testReturnOnlyPrintedDocs() throws InterruptedException, ExecutionException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var printed = new MockDocument().name("a");
        var notPrinted = new MockDocument().name("b");
        printDispatcher.addToPrint(printed);
        printDispatcher.addToPrint(notPrinted);
        printer.skip();
        printer.waitForStartPrinting();
        printDispatcher.cancelCurrent();

        // When
        var printedList = printDispatcher.listPrinted();

        // Then
        assertEquals(1, printedList.size(), printedList::toString);
        assertEquals(printed, printedList.get(0));

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testReturnEmptyPrintedList() {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);

        // When
        var printedList = printDispatcher.listPrinted();

        // Then
        assertNotNull(printedList);
        assertEquals(0, printedList.size());

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testReturnCustomSortedPrintedList() throws InterruptedException, ExecutionException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var first = new MockDocument()
                .printDuration(Duration.ofSeconds(1))
                .name("a")
                .paperSize(ISOPaperSizes.A4);
        var second = new MockDocument()
                .printDuration(Duration.ofSeconds(0))
                .name("b")
                .paperSize(ISOPaperSizes.A5);
        var third = new MockDocument()
                .printDuration(Duration.ofSeconds(2))
                .name("c")
                .paperSize(ISOPaperSizes.A3);

        printDispatcher.addToPrint(first);
        printDispatcher.addToPrint(second);
        printDispatcher.addToPrint(third);
        printer.skip();
        printer.skip();
        printer.skip();

        // When
        var paperSizeSorted = printDispatcher.listPrinted(Comparator.comparing(d -> d.size().width()));

        // Then
        assertEquals(3, paperSizeSorted.size(), paperSizeSorted::toString);
        assertEquals(second, paperSizeSorted.get(0));
        assertEquals(first, paperSizeSorted.get(1));
        assertEquals(third, paperSizeSorted.get(2));

        // When
        var printDurationSorted = printDispatcher.listPrinted(Comparator.comparing(Printable::printDuration));

        // Then
        assertEquals(3, printDurationSorted.size(), printDurationSorted::toString);
        assertEquals(second, printDurationSorted.get(0));
        assertEquals(first, printDurationSorted.get(1));
        assertEquals(third, printDurationSorted.get(2));

        // When
        var nameSorted = printDispatcher.listPrinted(Comparator.comparing(Printable::name));

        // Then
        assertEquals(3, nameSorted.size(), nameSorted::toString);
        assertEquals(first, nameSorted.get(0));
        assertEquals(second, nameSorted.get(1));
        assertEquals(third, nameSorted.get(2));

        // Cleanup
        printDispatcher.stop();
    }

    @Test
    public void testCorrectCalcPrintDurationAvg() throws InterruptedException, ExecutionException {
        // Setup
        var printer = new MockPrinter();
        var printDispatcher = PrintDispatchActorFacade.start(printer);
        var first = new MockDocument()
                .name("first")
                .printDuration(Duration.ofSeconds(1));
        var second = new MockDocument()
                .name("second")
                .printDuration(Duration.ofSeconds(2));

        printDispatcher.addToPrint(first);
        printDispatcher.addToPrint(second);
        printer.skip();
        printer.skip();

        // When
        Duration avg = printDispatcher.avgPrintedTime();

        // Then
        assertEquals(1500, avg.toMillis());

        // Cleanup
        printDispatcher.stop();
    }
}
