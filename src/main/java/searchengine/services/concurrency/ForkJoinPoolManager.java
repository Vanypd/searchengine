package searchengine.services.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

@Component
public class ForkJoinPoolManager {

    protected static final Logger logger = LoggerFactory.getLogger(ForkJoinPoolManager.class);
    private final ForkJoinPool forkJoinPool;


    // CONSTRUCTORS //


    public ForkJoinPoolManager() {
        forkJoinPool = new ForkJoinPool();
    }


    // FORK/JOIN POOL METHODS //


    public void execute(Runnable task) {
        forkJoinPool.execute(task);
    }


    public void executeAwait(Runnable task) {
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(task);
        executeAwait(tasks);
    }


    public void executeAwait(List<Runnable> tasks) {
        CountDownLatch latch = new CountDownLatch(tasks.size());

        for (Runnable task : tasks) {
            execute(() -> {
                task.run();
                latch.countDown();
            });
        }

        tryToAwaitLatch(latch);
    }


    private void tryToAwaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("Ошибка при попытке дождаться завершения потока: {}", e.getMessage(), e);
        }
    }


    public static void executeDelay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            logger.error("Поток прерван во время выполнения задержки", e);
            throw new RuntimeException(e);
        }
    }
}
