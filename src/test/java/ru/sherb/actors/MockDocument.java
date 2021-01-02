package ru.sherb.actors;

import lombok.Data;
import ru.sherb.printer.ISOPaperSizes;
import ru.sherb.printer.PaperSize;
import ru.sherb.printer.Printable;

import java.time.Duration;

/**
 * @author maksim
 * @since 01.01.2021
 */
@Data
public class MockDocument implements Printable {

    private String name = "";
    private PaperSize paperSize = ISOPaperSizes.A4;
    private Duration printDuration = Duration.ZERO;

    @Override
    public String name() {
        return name;
    }

    @Override
    public PaperSize size() {
        return paperSize;
    }

    @Override
    public Duration printDuration() {
        return printDuration;
    }

    public MockDocument name(String name) {
        this.name = name;
        return this;
    }

    public MockDocument paperSize(PaperSize paperSize) {
        this.paperSize = paperSize;
        return this;
    }

    public MockDocument printDuration(Duration printDuration) {
        this.printDuration = printDuration;
        return this;
    }
}
