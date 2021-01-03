package ru.sherb.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.Value;
import ru.sherb.printer.Printable;
import ru.sherb.printer.Printer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author maksim
 * @since 31.12.2020
 */
public class PrintDispatcherImpl extends AbstractBehavior<PrintDispatcherImpl.Command> {

    //region Public interface
    public interface Command { }

    @Value
    public static class AddToPrint implements Command {
        Printable document;
    }

    @Value
    public static class NotifyAfterAllComplete implements Command {
        ActorRef<AllDocumentsArePrintedEvent> observer;
    }

    public static class AllDocumentsArePrintedEvent { }

    @Value
    public static class CancelCurrentPrint implements Command { }

    @Value
    public static class StopPrint implements Command {
        ActorRef<NotPrintedDocuments> replyTo;
    }

    @Value
    public static class NotPrintedDocuments {
        List<Printable> documents;
    }

    @Value
    public static class GetPrintedList implements Command {
        ActorRef<PrintedDocuments> replyTo;
        Comparator<Printable> comparator;
    }

    @Value
    public static class PrintedDocuments {
        List<Printable> documents;
    }

    @Value
    public static class GetAvgPrintedTime implements Command {
        ActorRef<AvgPrintedTime> replyTo;
    }

    @Value
    public static class AvgPrintedTime {
        Duration value;
    }
    //endregion

    //region Private messages

    @Value
    static class AddToQueueDocument implements Command {
        long id;
    }

    @Value
    static class AddToProgressDocument implements Command {
        long id;
    }

    @Value
    static class AddToCompleteDocument implements Command {
        long id;
    }

    @Value
    static class RemoveInProgressDocument implements Command {
        long id;
    }

    //endregion

    public static Behavior<Command> create(Printer printer) {
        return Behaviors.setup(param -> new PrintDispatcherImpl(param, printer));
    }

    private final ActorRef<PrinterImpl.Command> printer;

    private final Map<Long, Printable> inWaiting  = new HashMap<>();
    private final Map<Long, Printable> inQueue    = new HashMap<>();
    private final Map<Long, Printable> inProgress = new HashMap<>();
    private final Map<Long, Printable> inComplete = new HashMap<>();

    private final List<ActorRef<AllDocumentsArePrintedEvent>> printCompleteListeners = new ArrayList<>();

    private long docIdIncrement = Long.MIN_VALUE;

    public PrintDispatcherImpl(ActorContext<Command> context, Printer printer) {
        super(context);
        this.printer = context.spawn(PrinterImpl.create(printer), "printer");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(AddToPrint.class, this::onAddToPrint)
                .onMessage(CancelCurrentPrint.class, this::onCancelCurrentPrint)
                .onMessage(StopPrint.class, this::onStopPrint)
                .onMessage(GetPrintedList.class, this::onGetPrintedList)
                .onMessage(GetAvgPrintedTime.class, this::onGetAvgPrintedTime)
                .onMessage(NotifyAfterAllComplete.class, this::onNotifyAfterAllComplete)
                // inner interface
                .onMessage(AddToQueueDocument.class, this::onAddToQueue)
                .onMessage(AddToProgressDocument.class, this::onAddToProgress)
                .onMessage(AddToCompleteDocument.class, this::onAddToComplete)
                .onMessage(RemoveInProgressDocument.class, this::onRemoveInProgress)
                .build();
    }

    private Behavior<Command> onAddToPrint(AddToPrint cmd) {
        inWaiting.put(docIdIncrement, cmd.document);

        var watcher = getContext().spawn(
                PrintStatusWatcher.create(getContext().getSelf(), docIdIncrement),
                "document-" + formatName(cmd.document.name()) + "-watcher");

        printer.tell(new PrinterImpl.Print(watcher, cmd.document));

        docIdIncrement += 1;
        return this;
    }

    private static String formatName(String docName) {
        if (docName.length() > 35) {
            docName = docName.substring(0, 35);
        }
        return URLEncoder.encode(docName, StandardCharsets.UTF_8);
    }

    private Behavior<Command> onAddToQueue(AddToQueueDocument cmd) {
        var document = this.inWaiting.remove(cmd.id);
        this.inQueue.put(cmd.id, document);
        return this;
    }

    private Behavior<Command> onAddToProgress(AddToProgressDocument cmd) {
        var document = this.inQueue.remove(cmd.id);
        this.inProgress.put(cmd.id, document);
        return this;
    }

    private Behavior<Command> onAddToComplete(AddToCompleteDocument cmd) {
        var document = this.inProgress.remove(cmd.id);
        this.inComplete.put(cmd.id, document);

        notifyOnComplete();
        return this;
    }

    private void notifyOnComplete() {
        if (!printCompleteListeners.isEmpty() && isAllDocumentArePrinted()) {
            var event = new AllDocumentsArePrintedEvent();
            for (ActorRef<AllDocumentsArePrintedEvent> listener : printCompleteListeners) {
                listener.tell(event);
            }
        }
    }

    private Behavior<Command> onRemoveInProgress(RemoveInProgressDocument cmd) {
        this.inProgress.remove(cmd.id);

        notifyOnComplete();
        return this;
    }

    private Behavior<Command> onCancelCurrentPrint(CancelCurrentPrint cmd) {
        printer.tell(new PrinterImpl.Cancel());
        return this;
    }

    private Behavior<Command> onStopPrint(StopPrint cmd) {
        getContext().stop(printer);

        List<Printable> notPrinted = new ArrayList<>(inWaiting.values());
        notPrinted.addAll(inQueue.values());
        notPrinted.addAll(inProgress.values());
        notPrinted.sort(Comparator.comparing(Printable::name));

        var response = new NotPrintedDocuments(notPrinted);
        cmd.replyTo.tell(response);

        return newReadOnlyBehavior(response);
    }

    private Behavior<Command> newReadOnlyBehavior(NotPrintedDocuments cachedResponse) {
        return Behaviors.receive(Command.class)
                .onMessage(StopPrint.class, c -> {
                    c.replyTo.tell(cachedResponse);
                    return Behaviors.same();
                })
                .onMessage(GetPrintedList.class, c -> {
                    this.onGetPrintedList(c);
                    return Behaviors.same();
                })
                .onMessage(GetAvgPrintedTime.class, c -> {
                    this.onGetAvgPrintedTime(c);
                    return Behaviors.same();
                })
                .onMessage(NotifyAfterAllComplete.class, c -> {
                    c.observer.tell(new AllDocumentsArePrintedEvent());
                    return Behaviors.same();
                })
                .onAnyMessage(__ -> Behaviors.ignore()) //todo how it works?
                .build();
    }

    private Behavior<Command> onGetPrintedList(GetPrintedList cmd) {
        var printed = new ArrayList<>(inComplete.values());
        printed.sort(cmd.comparator);

        cmd.replyTo.tell(new PrintedDocuments(printed));
        return this;
    }

    private Behavior<Command> onGetAvgPrintedTime(GetAvgPrintedTime cmd) {
        var average = inComplete.values().stream()
                .mapToLong(d -> d.printDuration().toMillis())
                .average()
                .orElse(0);

        var avgWithoutNanos = Math.round(average);

        cmd.replyTo.tell(new AvgPrintedTime(Duration.ofMillis(avgWithoutNanos)));

        return this;
    }

    private Behavior<Command> onNotifyAfterAllComplete(NotifyAfterAllComplete cmd) {
        if (isAllDocumentArePrinted()) {
            cmd.observer.tell(new AllDocumentsArePrintedEvent());
        } else {
            this.printCompleteListeners.add(cmd.observer);
        }
        return this;
    }

    private boolean isAllDocumentArePrinted() {
        return inWaiting.isEmpty() && inQueue.isEmpty() && inProgress.isEmpty();
    }
}
