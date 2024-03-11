package searchengine.concurrency.implementation;

import org.springframework.stereotype.Component;
import searchengine.concurrency.ApplicationConcurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Component
public class ForkJoinPoolManager extends ApplicationConcurrency {


    // CONSTRUCTORS //


    public ForkJoinPoolManager() {
        forkJoinPool = getNewForkJoinPool();
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
            LOGGER.error("Ошибка при попытке дождаться завершения потока: {}", e.getMessage(), e);
        }
    }
}
