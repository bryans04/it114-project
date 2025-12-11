package Project.Common;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public class TimedEvent {
    private final Timer timer = new Timer();
    private final AtomicInteger remaining;
    private final Runnable callback;
    private IntConsumer tickCallback = null;

    public TimedEvent(int seconds, Runnable callback) {
        this.remaining = new AtomicInteger(seconds);
        this.callback = callback;
        // schedule tick every 1 second
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int timeLeft = remaining.getAndDecrement();
                if (timeLeft >= 0) {
                    if (tickCallback != null) {
                        try {
                            tickCallback.accept(timeLeft);
                        } catch (Exception e) {
                            // swallow
                        }
                    }
                }
                if (timeLeft <= 0) {
                    try {
                        callback.run();
                    } finally {
                        timer.cancel();
                    }
                }
            }
        }, 0L, 1000L);
    }

    public void setTickCallback(IntConsumer tickCallback) {
        this.tickCallback = tickCallback;
    }

    public void cancel() {
        timer.cancel();
    }
}
