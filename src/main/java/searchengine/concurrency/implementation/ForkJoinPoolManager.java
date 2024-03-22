package searchengine.concurrency.implementation;

import org.springframework.stereotype.Component;
import searchengine.concurrency.ApplicationConcurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Component
public class ForkJoinPoolManager extends ApplicationConcurrency {

    public ForkJoinPoolManager() {
        forkJoinPool = super.getNewForkJoinPool();
    }


    public void execute(Runnable task) {
        forkJoinPool.execute(task);
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
