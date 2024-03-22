package searchengine.concurrency.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadUtil {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ThreadUtil.class);

    private ThreadUtil() {}

    public static void executeDelay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            LOGGER.error("Поток прерван во время выполнения задержки", e);
            throw new RuntimeException(e);
        }
    }
}
