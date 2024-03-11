package searchengine.concurrency.implementation;

import org.springframework.stereotype.Component;
import searchengine.concurrency.ApplicationConcurrency;


@Component
public class ThreadPoolManager extends ApplicationConcurrency {

    public ThreadPoolManager() {
        threadPoolExecutor = getNewThreadPool();
    }


    public void execute(Runnable task) {
        threadPoolExecutor.execute(task);
    }


    public static void executeDelay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            LOGGER.error("Поток прерван во время выполнения задержки", e);
            throw new RuntimeException(e);
        }
    }
}
