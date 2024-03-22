package searchengine.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public abstract class ApplicationConcurrency {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ApplicationConcurrency.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    protected ForkJoinPool forkJoinPool;
    private static final int FORK_JOIN_PARALLELISM = getCoresCount();


    protected ForkJoinPool getNewForkJoinPool() {
        return new ForkJoinPool(FORK_JOIN_PARALLELISM);
    }


    protected static int getCoresCount() {
        if (AVAILABLE_PROCESSORS % 2 == 0) {
            return AVAILABLE_PROCESSORS / 2;
        }
        else {
            return (AVAILABLE_PROCESSORS / 2) + 1;
        }
    }
}
