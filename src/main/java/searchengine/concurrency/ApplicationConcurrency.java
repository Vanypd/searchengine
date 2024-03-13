package searchengine.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public abstract class ApplicationConcurrency {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ApplicationConcurrency.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();


    // THREAD POOL PROPERTIES //
    protected ThreadPoolExecutor threadPoolExecutor;
    private static final int THREAD_POOL_CORE_POOL_SIZE = getCoresCountForThreadPool() / 2;
    private static final int CORES_COUNT_FOR_THREAD_POOL = getCoresCountForThreadPool();
    private static final int THREAD_POOL_KEEP_ALIVE_TIME = 1;
    private static final TimeUnit TIME_UNIT = TimeUnit.MINUTES;
    private static final BlockingQueue<Runnable> WORK_QUEUE = getNewWorkQueue();

    // FORK/JOIN POOL PROPERTIES //
    protected ForkJoinPool forkJoinPool;
    private static final int FORK_JOIN_PARALLELISM = getCoresCountForForkJoinPool();


    protected ThreadPoolExecutor getNewThreadPool() {
        return new ThreadPoolExecutor(
                THREAD_POOL_CORE_POOL_SIZE,
                CORES_COUNT_FOR_THREAD_POOL,
                THREAD_POOL_KEEP_ALIVE_TIME,
                TIME_UNIT,
                WORK_QUEUE
        );
    }


    protected ForkJoinPool getNewForkJoinPool() {
        return  new ForkJoinPool(FORK_JOIN_PARALLELISM);
    }


    protected static int getCoresCountForThreadPool() {
        if (AVAILABLE_PROCESSORS % 2 == 0) {
            return AVAILABLE_PROCESSORS / 2;
        }
        else {
            return (AVAILABLE_PROCESSORS / 2) + 1;
        }
    }


    protected static int getCoresCountForForkJoinPool() {
        return AVAILABLE_PROCESSORS - getCoresCountForThreadPool();
    }


    private static BlockingQueue<Runnable> getNewWorkQueue() {
        return new LinkedBlockingQueue<>();
    }
}
