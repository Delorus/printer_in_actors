package ru.sherb.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.pattern.CircuitBreaker;
import lombok.Value;
import org.slf4j.Logger;
import ru.sherb.printer.Printable;
import ru.sherb.printer.Printer;
import scala.concurrent.ExecutionContextExecutor;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author maksim
 * @since 31.12.2020
 */
public class PrinterImpl extends AbstractBehavior<PrinterImpl.Command> {

    interface Command {
    }

    @Value
    static class Print implements Command {
        ActorRef<PrintEvent> consumer;
        Printable document;
    }

    static class Cancel implements Command { }

    private static class PrintNext implements Command { }

    interface PrintEvent { }

    static class DocumentAddedToQueue implements PrintEvent { }

    static class PrintStarting implements PrintEvent { }

    static class PrintComplete implements PrintEvent { }

    static class PrintCancelled implements PrintEvent { }


    public static Behavior<PrinterImpl.Command> create(Printer printer) {
        return Behaviors.setup(param -> new PrinterImpl(param, printer));
    }

    private final Printer printer;
    private final ExecutionContextExecutor blockingExecutor;

    private final Queue<Print> queue = new LinkedList<>();

    private CompletableFuture<Void> currentTask = CompletableFuture.completedFuture(null);


    public PrinterImpl(ActorContext<Command> ctx, Printer printer) {
        super(ctx);
        this.printer = printer;

        this.blockingExecutor = ctx.getSystem()
                .dispatchers().lookup(DispatcherSelector.blocking());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Print.class, this::onPrint)
                .onMessage(PrintNext.class, __ -> this.onPrintNext())
                .onMessage(Cancel.class, __ -> this.onCancel())
                .onSignal(PostStop.class, __ -> this.onPostStop())
                .build();
    }

    private Behavior<PrinterImpl.Command> onPrint(Print cmd) {
        Logger log = getContext().getLog();

        cmd.consumer.tell(new DocumentAddedToQueue());
        if (!currentTask.isDone()) {
            log.info("add document to queue: {}", cmd.document.name());
            queue.add(cmd);
            return this;
        }

        cmd.consumer.tell(new PrintStarting());

        currentTask = printAsync(cmd);

        return this;
    }

    private CompletableFuture<Void> printAsync(Print cmd) {
        Logger log = getContext().getLog();
        log.info("start printing document: {}", cmd.document.name());
        var ref = getContext().getSelf();
        //todo use circuit breaker here (akka.pattern.CircuitBreaker does not work)
        return CompletableFuture
                .runAsync(() -> tryPrint(cmd), blockingExecutor)
                .thenRun(() -> {
                    log.info("print complete: {}", cmd.document.name());
                    cmd.consumer.tell(new PrintComplete());
                    ref.tell(new PrintNext());
                })
                .exceptionally(err -> {
                    if (isCancelled(err)) {
                        log.info("print cancelled: {}", cmd.document.name());
                        cmd.consumer.tell(new PrintCancelled());
                        ref.tell(new PrintNext());
                        return null;

                    } else {
                        log.error("print failed: {}", cmd.document.name());
                        throw new CancellationException(err.getMessage());
                    }
                });
    }

    private void tryPrint(Print cmd) {
        try {
            printer.print(cmd.document);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    private boolean isCancelled(Throwable e) {
        return e instanceof CancellationException || e instanceof InterruptedException || e instanceof CompletionException;
    }

    private Behavior<PrinterImpl.Command> onPrintNext() {
        if (queue.isEmpty() || !currentTask.isDone()) {
            return this;
        }

        currentTask = printAsync(queue.poll());
        return this;
    }

    private Behavior<PrinterImpl.Command> onCancel() {
        if (!currentTask.isDone()) {
            printer.stop();
            currentTask.cancel(true);
        }
        return this;
    }

    private Behavior<PrinterImpl.Command> onPostStop() {
        printer.stop();
        return this;
    }
}