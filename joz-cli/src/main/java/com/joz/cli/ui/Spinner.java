package com.joz.cli.ui;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Animated terminal spinner for long-running operations. */
public class Spinner {

    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final long FRAME_DELAY_MS = 80;

    private final PrintStream out;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread spinnerThread;
    private String message;

    public Spinner(PrintStream out) {
        this.out = out;
    }

    public Spinner() {
        this(System.out);
    }

    /** Starts the spinner with a message. */
    public void start(String message) {
        this.message = message;
        if (running.compareAndSet(false, true)) {
            spinnerThread = Thread.ofVirtual().name("spinner").start(this::animate);
        } else {
            this.message = message; // Update message for running spinner
        }
    }

    /** Stops the spinner and optionally shows a completion message. */
    public void stop(String finalMessage) {
        running.set(false);
        if (spinnerThread != null) {
            try {
                spinnerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        out.print("\r" + Ansi.CLEAR_LINE);
        if (finalMessage != null && !finalMessage.isEmpty()) {
            out.println(finalMessage);
        }
    }

    /** Stops the spinner with no message. */
    public void stop() {
        stop(null);
    }

    /** Updates the spinner message without stopping. */
    public void update(String newMessage) {
        this.message = newMessage;
    }

    private void animate() {
        int frame = 0;
        while (running.get()) {
            out.print("\r" + Ansi.CLEAR_LINE
                    + Ansi.CYAN + FRAMES[frame % FRAMES.length] + Ansi.RESET
                    + " " + Ansi.DIM + message + Ansi.RESET);
            out.flush();
            frame++;
            try {
                Thread.sleep(FRAME_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
