package searchengine.concurrency;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;
import searchengine.concurrency.implementation.ForkJoinPoolManager;
import searchengine.concurrency.implementation.ThreadPoolManager;

@Service
@AllArgsConstructor
@Getter
public class ConcurrencyManager {
    private ForkJoinPoolManager forkJoinPoolManager;
    private ThreadPoolManager threadPoolManager;
}
