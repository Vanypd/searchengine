package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.concurrency.implementation.ForkJoinPoolManager;
import searchengine.config.SiteProps;
import searchengine.config.SitesList;
import searchengine.dto.indexing.*;
import searchengine.model.implementation.IndexStatus;
import searchengine.model.implementation.Lemma;
import searchengine.model.implementation.Page;
import searchengine.model.implementation.Site;
import searchengine.repository.RepositoryManager;
import searchengine.services.utils.URLParser;
import searchengine.services.web.html.HTMLManager;
import searchengine.services.web.html.Lemmatizator;
import searchengine.services.web.scraping.ContentExtractorAction;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class IndexingService extends DefaultService {

    private final SitesList sitesList;
    private final ForkJoinPoolManager forkJoinPoolManager;
    private final Lemmatizator lemmatizator;
    private boolean isIndexing = false;


    // CONSTRUCTORS //


    @Autowired
    public IndexingService(SitesList sitesList, RepositoryManager repositoryManager,
                           ForkJoinPoolManager forkJoinPoolManager, Lemmatizator lemmatizator) {
        super(repositoryManager);
        this.sitesList = sitesList;
        this.forkJoinPoolManager = forkJoinPoolManager;
        this.lemmatizator = lemmatizator;
    }


    // API METHODS //


    public ResponseEntity<IndexingResponse> startIndexing() {
        LOGGER.info("Вызван запуск полной индексации");

        if (isIndexing) {
            return getFailedResponse(new IndexingErrorResponse("Индексация уже запущена"));
        }

        isIndexing = true;
        siteRepository.deleteAll();
        pageRepository.deleteAll();
        lemmaRepository.deleteAll();
        indexRepository.deleteAll();

        AtomicInteger errorsCount = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();

        sitesList.getSites().forEach(site -> tasks.add(() -> {
            Site siteEntity = mapSiteEntityFromSiteList(site);
            ContentExtractorAction action =
                    new ContentExtractorAction(repositoryManager, siteEntity.getUrl(), lemmatizator);

            if (hasErrorsDuringInvocation(action, siteEntity)) {
                errorsCount.getAndIncrement();
                return;
            }

            siteEntity.setIndexStatus(IndexStatus.INDEXED);
            repositoryManager.executeTransaction(() ->
                    siteRepository.save(siteEntity)
            );
        }));

        forkJoinPoolManager.executeAwait(tasks);
        isIndexing = false;

        if (errorsCount.get() != 0) {
            return getFailedResponse(
                    new IndexingErrorResponse("Индексация закончена с ошибкой на " + errorsCount.get() + " сайтах")
            );
        }

        return getSuccessResponse(new IndexingResponse(true));
    }


    public ResponseEntity<IndexingResponse> stopIndexing() {
        LOGGER.info("Вызвана остановка индексации");

        if (!isIndexing) {
            return getFailedResponse(new IndexingErrorResponse("Индексация не запущена"));
        }

        ContentExtractorAction.stop();

        repositoryManager.executeTransaction(() -> {
            String errorMessage = "Индексация остановлена пользователем";
            siteRepository.updateStatusAndErrorByStatus(IndexStatus.INDEXING, IndexStatus.FAILED, errorMessage);
        });

        return getSuccessResponse(new IndexingResponse(true));
    }


    public ResponseEntity<IndexingResponse> indexPage(UrlDto urlDto) {
        LOGGER.info("Вызвана индексация отдельной страницы: {}", urlDto.getUrl());
        Site siteEntity = getSiteEntityFromUrl(urlDto.getUrl());

        if (siteEntity == null) {
            String errorMessage = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
            return getFailedResponse(new IndexingErrorResponse(errorMessage));
        }

        siteEntity.setIndexStatus(IndexStatus.INDEXING);
        siteRepository.save(siteEntity);

        URL url = URLParser.mapStringToUrl(urlDto.getUrl());
        String path = URLParser.getPathFromUrl(url);
        Page pageEntity = pageRepository.findBySiteIdAndPath(siteEntity, path);
        Page newPageEntity = HTMLManager.getPageEntity(url, siteRepository);

        if (pageEntity != null) {
            pageEntity.setContent(newPageEntity.getContent());
            pageEntity.setCode(newPageEntity.getCode());
        } else {
            pageEntity = newPageEntity;
        }

        Page finalPageEntity = pageEntity;
        repositoryManager.executeTransaction(() -> {
            pageRepository.save(finalPageEntity);
            siteRepository.updateStatusTimeById(finalPageEntity.getId(), LocalDateTime.now());
        });

        lemmatizator.save(pageEntity);
        siteEntity.setIndexStatus(IndexStatus.INDEXED);
        siteRepository.save(siteEntity);
        return getSuccessResponse(new IndexingResponse(true));
    }


    public ResponseEntity<IndexingResponse> search(String query, String site, Integer offset, Integer limit) {
        // TODO реализовать offset и limit


        if (query.isBlank()) {
            String errorMessage = "Задан пустой поисковый запрос";
            return getFailedResponse(new IndexingErrorResponse(errorMessage));
        }

        Site siteEntity = getSiteEntityFromUrl(site);
        List<String> lemmas = getFilteredSearchLemmas(query, siteEntity);

        if (lemmas.isEmpty()) {
            return getSuccessResponse(
                    new SearchResponse(true, 0, new ArrayList<>())
            );
        }

        List<Page> rareLemmaPages = pageRepository.findPagesByLemma(lemmas.get(0));

        for (int i = 1; i < lemmas.size(); i++) {
            String currentLemma = lemmas.get(i);
            rareLemmaPages.removeIf(currentPage -> !currentPage.getContent().contains(currentLemma));
        }

        if (rareLemmaPages.isEmpty()) {
            return getSuccessResponse(
                    new SearchResponse(true,0, new ArrayList<>())
            );
        }

        List<SearchResult> searchResults = new ArrayList<>();

        for (Page page : rareLemmaPages) {
            SearchResult searchResult = new SearchResult();
            searchResult.setSite(page.getSiteId().getUrl());
            searchResult.setSiteName(page.getSiteId().getName());
            searchResult.setUri(page.getPath());
            searchResult.setTitle(HTMLManager.getTitleFromContent(page.getContent()));
            searchResult.setSnippet(getSnippet(page, lemmas));
            searchResult.setRelevance(getAbsoluteRelevance(page));
            searchResults.add(searchResult);
        }

        float highestRelevance = searchResults.stream()
                .map(SearchResult::getRelevance)
                .max(Float::compareTo)
                .orElseThrow();

        for (SearchResult searchResult : searchResults) {
            float absoluteRelevance = searchResult.getRelevance();
            searchResult.setRelevance(absoluteRelevance / highestRelevance);
        }

        return getSuccessResponse(
                new SearchResponse(true, rareLemmaPages.size(), searchResults)
        );
    }


    // UTILS METHODS //


    private List<String> getFilteredSearchLemmas(String query, Site site) {
        return lemmatizator.collectLemmas(query).keySet().stream()
                .map(lemma -> (site == null)
                ? lemmaRepository.findByLemma(lemma)
                : lemmaRepository.findByLemmaAndSiteId(lemma, site)
                )
                .filter(Objects::nonNull)
                .filter(lemmaEntity -> lemmaEntity.getFrequency() <= lemmaRepository.findMaxFrequency() * 0.8)
                .sorted(Comparator.comparingLong(Lemma::getFrequency))
                .map(Lemma::getLemma)
                .collect(Collectors.toList());
    }


    private String getSnippet(Page page, List<String> lemmas) {
        Document doc = Jsoup.parse(page.getContent());
        List<Element> elements = new ArrayList<>();

        for (String lemma : lemmas) {
            elements = doc.body().getElementsContainingOwnText(lemma);

            if (!elements.isEmpty()) {
                break;
            }
        }

        if (elements.isEmpty()) {
            return "";
        }

        for (String currentLemma : lemmas) {
            Iterator<Element> iterator = elements.iterator();

            while (iterator.hasNext()) {
                Element el = iterator.next();

                if (elements.size() == 1) {
                    break;
                }

                if (!el.text().contains(currentLemma)) {
                    iterator.remove();
                }
            }
        }

        String resultText = elements.get(0).text();

        for (String lemma : lemmas) {
            resultText = resultText.replaceAll(lemma, "<b>" + lemma + "</b>");
        }

        if (resultText.length() > 200) {
            resultText = resultText.substring(0, 197) + "...";
        }

        return resultText;
    }


    private float getAbsoluteRelevance(Page page) {
        return pageRepository.getRankSumForPage(page);
    }


    private Site mapSiteEntityFromSiteList(SiteProps site) {
        Site siteEntity = new Site();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setIndexStatus(IndexStatus.INDEXING);
        siteEntity.setLastError("");
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        return siteEntity;
    }


    private Site getSiteEntityFromUrl(String url) {
        Site siteEntity = null;

        if (url == null) {
            return null;
        }

        for (SiteProps site : sitesList.getSites()) {

            if (url.startsWith(site.getUrl())) {
                Site foundedSite = siteRepository.findByUrl(site.getUrl());

                if (foundedSite == null) {
                    Site finalFoundedSite = mapSiteEntityFromSiteList(site);
                    repositoryManager.executeTransaction(() ->
                            siteRepository.save(finalFoundedSite)
                    );
                    foundedSite = finalFoundedSite;
                }

                siteEntity = foundedSite;
            }
        }

        return siteEntity;
    }


    private static boolean hasErrorsDuringInvocation(RecursiveAction action, Site siteEntity) {
        try {
            action.invoke();
            return false;
        } catch (Exception e) {
            LOGGER.error("Ошибка во время индексации сайта {}: {}", siteEntity.getUrl(), e.getMessage(), e);
            siteEntity.setIndexStatus(IndexStatus.FAILED);
            return true;
        }
    }


    // RESPONSE GETTERS //


    private ResponseEntity<IndexingResponse> getSuccessResponse(IndexingResponse body) {
        return ResponseEntity.ok(body);
    }


    private ResponseEntity<IndexingResponse> getFailedResponse(IndexingResponse body) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
