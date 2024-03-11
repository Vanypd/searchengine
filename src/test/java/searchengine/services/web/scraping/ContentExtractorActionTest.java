package searchengine.services.web.scraping;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import searchengine.repository.RepositoryManager;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ContentExtractorActionTest {

    private ContentExtractorAction contentExtractorAction;
    private RepositoryManager repositoryManager;
    private PageRepository pageRepository;
    private SiteRepository siteRepository;


    @BeforeEach
    void setUp() {
        repositoryManager = mock(RepositoryManager.class);
        pageRepository = mock(PageRepository.class);
        siteRepository = mock(SiteRepository.class);

        Mockito.when(repositoryManager.getPageRepository()).thenReturn(pageRepository);
        Mockito.when(repositoryManager.getSiteRepository()).thenReturn(siteRepository);

        contentExtractorAction = new ContentExtractorAction(repositoryManager, "http://example.com");
    }


    @AfterEach
    void tearDown() {
        contentExtractorAction = null;
        repositoryManager = null;
        pageRepository = null;
        siteRepository = null;
    }


    @Test
    @DisplayName("Запуск рекурсионной цепочки задач")
    void testCompute() {
        contentExtractorAction.compute();
        verify(pageRepository, Mockito.atLeastOnce()).save(any());
        verify(siteRepository, Mockito.atLeastOnce()).updateStatusTimeById(any(), any());
    }


    @Test
    @DisplayName("Остановка всех рекурсионных задач")
    void testStop() {
        ContentExtractorAction.stop();
        Assertions.assertTrue(ContentExtractorAction.isStopped());
    }
}
