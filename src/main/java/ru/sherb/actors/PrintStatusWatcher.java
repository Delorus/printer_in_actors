package ru.sherb.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * @author maksim
 * @since 31.12.2020
 */
class PrintStatusWatcher extends AbstractBehavior<PrinterImpl.PrintEvent> {

    public static Behavior<PrinterImpl.PrintEvent> create(ActorRef<PrintDispatcherImpl.Command> dispatcher, long id) {
        return Behaviors.setup(param -> new PrintStatusWatcher(param, dispatcher, id));
    }

    private final ActorRef<PrintDispatcherImpl.Command> dispatcher;
    private final long id;

    private PrintStatusWatcher(ActorContext<PrinterImpl.PrintEvent> context, ActorRef<PrintDispatcherImpl.Command> dispatcher, long id) {
        super(context);
        this.dispatcher = dispatcher;
        this.id = id;
    }

    @Override
    public Receive<PrinterImpl.PrintEvent> createReceive() {
        return newReceiveBuilder()
                .onMessage(PrinterImpl.DocumentAddedToQueue.class, this::onAddedToQueue)
                .onMessage(PrinterImpl.PrintStarting.class, this::onPrintStarting)
                .onMessage(PrinterImpl.PrintComplete.class, this::onPrintComplete)
                .onMessage(PrinterImpl.PrintCancelled.class, this::onPrintCancelled)
                .build();
    }

    private Behavior<PrinterImpl.PrintEvent> onAddedToQueue(PrinterImpl.DocumentAddedToQueue cmd) {
        dispatcher.tell(new PrintDispatcherImpl.AddToQueueDocument(id));
        return this;
    }

    private Behavior<PrinterImpl.PrintEvent> onPrintStarting(PrinterImpl.PrintStarting cmd) {
        dispatcher.tell(new PrintDispatcherImpl.AddToProgressDocument(id));
        return this;
    }

    private Behavior<PrinterImpl.PrintEvent> onPrintComplete(PrinterImpl.PrintComplete cmd) {
        dispatcher.tell(new PrintDispatcherImpl.AddToCompleteDocument(id));
        return Behaviors.stopped();
    }

    private Behavior<PrinterImpl.PrintEvent> onPrintCancelled(PrinterImpl.PrintCancelled cmd) {
        dispatcher.tell(new PrintDispatcherImpl.RemoveInProgressDocument(id));
        return Behaviors.stopped();
    }
}
