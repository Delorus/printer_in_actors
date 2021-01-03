package ru.sherb.actors;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import ru.sherb.printer.PrintDispatcher;
import ru.sherb.printer.Printable;
import ru.sherb.printer.Printer;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * @author maksim
 * @since 31.12.2020
 */
public class PrintDispatchActorFacade implements PrintDispatcher {

    public static PrintDispatchActorFacade start(Printer printer) {
        var system = ActorSystem.create(PrintDispatcherImpl.create(printer), "dispatcher");

        return new PrintDispatchActorFacade(system);
    }

    private final ActorSystem<PrintDispatcherImpl.Command> dispatcher;

    private PrintDispatchActorFacade(ActorSystem<PrintDispatcherImpl.Command> dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void addToPrint(Printable document) {
        dispatcher.tell(new PrintDispatcherImpl.AddToPrint(document));
    }

    @Override
    public void cancelCurrent() {
        dispatcher.tell(new PrintDispatcherImpl.CancelCurrentPrint());
    }

    @Override
    public List<Printable> stopPrint() {
        var result = AskPattern.ask(
                dispatcher,
                PrintDispatcherImpl.StopPrint::new,
                Duration.ofSeconds(1),
                dispatcher.scheduler());

        try {
            return result.toCompletableFuture().get().getDocuments();
        } catch (InterruptedException | ExecutionException ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Printable> listPrinted(Comparator<Printable> customComparator) {
        CompletionStage<PrintDispatcherImpl.PrintedDocuments> result = AskPattern.ask(
                dispatcher,
                replyTo -> new PrintDispatcherImpl.GetPrintedList(replyTo, customComparator),
                Duration.ofSeconds(1),
                dispatcher.scheduler());

        try {
            return result.toCompletableFuture().get().getDocuments();
        } catch (InterruptedException | ExecutionException ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    public Duration avgPrintedTime() {
        var result = AskPattern.ask(
                dispatcher,
                PrintDispatcherImpl.GetAvgPrintedTime::new,
                Duration.ofSeconds(1),
                dispatcher.scheduler());

        try {
            return result.toCompletableFuture().get().getValue();
        } catch (InterruptedException | ExecutionException ignored) {
            return Duration.ZERO;
        }
    }

    public void stop() {
        dispatcher.terminate();
    }

    void waitForAllComplete() throws ExecutionException, InterruptedException {
        AskPattern.ask(
                dispatcher,
                PrintDispatcherImpl.NotifyAfterAllComplete::new,
                Duration.ofSeconds(1),
                dispatcher.scheduler()
        ).toCompletableFuture().get();
    }
}
