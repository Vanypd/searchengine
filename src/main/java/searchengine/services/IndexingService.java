package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.concurrency.implementation.ForkJoinPoolManager;
import searchengine.concurrency.tasks.ContentExtractorAction;
import searchengine.config.SiteProps;
import searchengine.config.SitesList;
import searchengine.dto.request.UrlDto;
import searchengine.dto.response.implementation.indexing.IndexingErrorResponse;
import searchengine.dto.response.implementation.indexing.IndexingResponse;
import searchengine.dto.response.implementation.indexing.SearchResponse;
import searchengine.dto.response.implementation.indexing.SearchResult;
import searchengine.model.implementation.IndexStatus;
import searchengine.model.implementation.Lemma;
import searchengine.model.implementation.Page;
import searchengine.model.implementation.Site;
import searchengine.repository.RepositoryManager;
import searchengine.services.utils.bean.Lemmatizator;
import searchengine.services.utils.notbean.HTMLManager;
import searchengine.services.utils.notbean.URLParser;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        repositoryManager.executeTransaction(() ->
            siteRepository.save(siteEntity)
        );

        return getSuccessResponse(new IndexingResponse(true));
    }


    public ResponseEntity<IndexingResponse> search(String query, String site, Integer offset, Integer limit) {
        LOGGER.info("Вызван поиск по запросу \"{}\"", query);

        if (query.isBlank()) {
            String errorMessage = "Задан пустой поисковый запрос";
            return getFailedResponse(new IndexingErrorResponse(errorMessage));
        }

        Site siteEntity = getSiteEntityFromUrl(site);
        List<String> lemmas = getFilteredSearchLemmas(query, siteEntity);

        if (lemmas.isEmpty()) {
            return getSuccessResponse(new SearchResponse(true, 0, new ArrayList<>()));
        }

        List<Page> pages = getPagesFromLemmasAndSite(lemmas, siteEntity);

        if (pages.isEmpty()) {
            return getSuccessResponse(new SearchResponse(true,0, new ArrayList<>()));
        }

        List<SearchResult> searchResults = mapSearchResultAndSortByRelevance(pages, lemmas);

        return getSuccessResponse(
                new SearchResponse(true, pages.size(), searchResults.subList(offset, offset + limit))
        );
    }


    // UTILS METHODS //


    /**
     * Метод является маппером DTO SearchResult из сущности Page. Так же метод ищет наивысшую
     * релевантность и сортирует список SearchResult относительно неё.
     * @param pagesList List сущностей Page
     * @param lemmas List<String>
     * @return List<SearchResult>
     */
    private List<SearchResult> mapSearchResultAndSortByRelevance(List<Page> pagesList,
                                                                 List<String> lemmas) {
        // TODO: Оптимизировать маппинг и сортировку
        List<SearchResult> searchResults = new ArrayList<>();

        for (Page page : pagesList) {
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

        return searchResults;
    }

    /**
     * Метод ищет сущности Page в базе данных по переданным в параметры значениям и возвращает в виде
     * списка. Изначально метод извлекает из базы данных страницы, которые содержат самый первый
     * элемент списка лемм. Затем при помощи цикла из списка страниц удаляются те страницы, которые не
     * имеют в себе остальные леммы.
     * @param lemmas Список строк лемм, предположительно отсортированный по возрастанию релевантности
     * @param site Сущность Site
     * @return List<Page>
     */
    private List<Page> getPagesFromLemmasAndSite(List<String> lemmas, Site site) {
        // TODO: Оптимизировать получение страниц
        List<Page> pages;

        if (site != null) {
            pages = pageRepository.searchPagesByLemmaAndSiteId(lemmas.get(0), site);
        } else {
            pages = pageRepository.searchPagesByLemma(lemmas.get(0));
        }

        for (int i = 1; i < lemmas.size(); i++) {
            String currentLemma = lemmas.get(i);
            pages.removeIf(currentPage -> !currentPage.getContent().contains(currentLemma));
        }

        return pages;
    }


    /**
     * Метод получает список лемм из переданного в параметры поискового запроса и ищет в базе данных
     * сущности данных лемм, если site == null, то леммы ищутся на всех страницах. Полученный список
     * очищается от возможных null-значений, затем и от лемм, которые встречаются на слишком большом
     * количестве страниц. Список сортируется в порядке увеличения частоты встречаемости, очищается
     * от повторов и возвращается в виде List.
     * @param query Строка поискового запроса
     * @param site Сущность Site
     * @return List<String>
     */
    private List<String> getFilteredSearchLemmas(String query, Site site) {
        long threshold = (long) (lemmaRepository.findMaxFrequency() * 0.9);

        return lemmatizator.collectLemmas(query).keySet().stream()
                .flatMap(lemma -> (site == null)
                        ? lemmaRepository.findByLemma(lemma).stream()
                        : Stream.of(lemmaRepository.findByLemmaAndSiteId(lemma, site))
                )
                .filter(Objects::nonNull)
                .filter(lemmaEntity -> lemmaEntity.getFrequency() <= threshold)
                .sorted(Comparator.comparingLong(Lemma::getFrequency))
                .map(Lemma::getLemma).distinct().collect(Collectors.toList());
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
            Pattern pattern = Pattern.compile(lemma, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(resultText);
            resultText = matcher.replaceAll("<b>$0</b>");
        }

        if (resultText.length() > 200) {
            resultText = resultText.substring(0, 197) + "...";
        }

        return resultText;
    }


    /**
     * Метод получает абсолютную релевантность - сумму всех rank всех найденных на странице лемм
     * и возвращает данное значение.
     * @param page Сущность Page
     * @return float
     */
    private float getAbsoluteRelevance(Page page) {
        return pageRepository.getRankSumForPage(page);
    }


    /**
     * Метод является маппером из SiteProps в Site.
     * @param site Сущность SiteProps
     * @return Site
     */
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


    /**
     * Метод принимает строку ссылки в параметры и проверяет наличие данной ссылки в конфигурационном
     * файле. При наличии файла, метод получает данный сайт из базы данных и возвращает его. При отсутствии
     * сайта в базе данных создаётся новый сайт, сохраняется в базе и возвращается. Если в парметры был
     * передан null, метод вернёт null.
     * @param url Базовый URL сайта
     * @return Site
     */
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


    /**
     * Метод выполняет переданную в него рекурсивную задачу и возвращает true если задача
     * завершилась с ошибками, в противном случае возвращает false.
     * @param action Рекурсивная задача
     * @param siteEntity Сущность Site
     * @return boolean
     */
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
}
