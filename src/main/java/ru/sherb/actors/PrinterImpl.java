package ru.sherb.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.Value;
import ru.sherb.printer.Printable;
import ru.sherb.printer.Printer;
import scala.concurrent.ExecutionContextExecutor;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * @author maksim
 * @since 31.12.2020
 */
public class PrinterImpl extends AbstractBehavior<PrinterImpl.Command> {

    interface Command { }

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

    private volatile boolean isWorking = false;


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
        cmd.consumer.tell(new DocumentAddedToQueue());
        queue.add(cmd);
        getContext().getLog().info("added to queue: {}", cmd.document.name());

        getContext().getSelf().tell(new PrintNext());
        return this;
    }

    private CompletionStage<Void> printAsync(Print cmd) {
        var log = getContext().getLog();
        var ref = getContext().getSelf();
        isWorking = true;
        //todo use circuit breaker here (akka.pattern.CircuitBreaker does not work)
        return CompletableFuture
                .runAsync(() -> {
                    log.info("start printing document: {}", cmd.document.name());
                    cmd.consumer.tell(new PrintStarting());
                    tryPrint(cmd);
                }, blockingExecutor)
                .thenRun(() -> {
                    isWorking = false;
                    log.info("print complete: {}", cmd.document.name());
                    cmd.consumer.tell(new PrintComplete());
                    ref.tell(new PrintNext());
                })
                .exceptionally(err -> {
                    isWorking = false;
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
        if (queue.isEmpty() || isWorking) {
            return this;
        }

        Print cmd = queue.poll();
        getContext().getLog().info("print next: {}", cmd.document.name());
        printAsync(cmd);
        return this;
    }

    private Behavior<PrinterImpl.Command> onCancel() {
        if (isWorking) {
            printer.stop();
        }
        return this;
    }

    private Behavior<PrinterImpl.Command> onPostStop() {
        printer.stop();
        return this;
    }
}
