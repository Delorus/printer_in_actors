package ru.sherb.actors;

import ru.sherb.printer.Printable;
import ru.sherb.printer.Printer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

/**
 * @author maksim
 * @since 01.01.2021
 */
class MockPrinter implements Printer {

    private final TransferQueue<Printable> queue = new LinkedTransferQueue<>();

    private volatile boolean cancelled = false;

    private volatile CountDownLatch startPrintingNotifier = new CountDownLatch(1);
    private volatile CountDownLatch cancelPrintingNotifier = new CountDownLatch(1);

    @Override
    public void print(Printable document) throws InterruptedException {
        rechargeCancelPrintingBarrier();
        startPrintingNotifier.countDown();
        queue.transfer(document);
        rechargeStartPrintingBarrier();
    }

    private void rechargeStartPrintingBarrier() {
        if (startPrintingNotifier.getCount() == 0) {
            startPrintingNotifier = new CountDownLatch(1);
        }
    }

    private void rechargeCancelPrintingBarrier() {
        if (cancelPrintingNotifier.getCount() == 0) {
            cancelPrintingNotifier = new CountDownLatch(1);
        }
    }

    public void skip() throws InterruptedException {
        queue.poll(100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        queue.clear();
        cancelPrintingNotifier.countDown();
    }

    public void waitForCancel() throws InterruptedException {
        cancelPrintingNotifier.await(100, TimeUnit.MILLISECONDS);
    }

    public Printable printedDocument() throws InterruptedException {
        return queue.poll(100, TimeUnit.MILLISECONDS);
    }

    public void waitForStartPrinting() throws InterruptedException {
        startPrintingNotifier.await(100, TimeUnit.MILLISECONDS);
        while (queue.peek() == null) {
            Thread.yield();
        }
    }
}
