package searchengine.services;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SiteProps;
import searchengine.config.SitesList;
import searchengine.dto.response.implementation.statistics.DetailedStatisticsItem;
import searchengine.dto.response.implementation.statistics.StatisticsData;
import searchengine.dto.response.implementation.statistics.StatisticsResponse;
import searchengine.dto.response.implementation.statistics.TotalStatistics;
import searchengine.model.implementation.Site;
import searchengine.repository.RepositoryManager;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatisticsService extends DefaultService {
    private final SitesList sites;


    public StatisticsService(RepositoryManager repositoryManager, SitesList sites) {
        super(repositoryManager);
        this.sites = sites;
    }


    public ResponseEntity<StatisticsResponse> getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteProps> sitesList = sites.getSites();

        for (SiteProps siteProps : sitesList) {
            Site siteEntity = repositoryManager.getSiteRepository().findByUrl(siteProps.getUrl());
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            int pages;
            int lemmas;
            String status;
            String error;
            long statusTime;

            if (!(siteEntity == null)) {
                pages = (int) repositoryManager.getPageRepository().countPagesBySiteUrl(siteProps.getUrl());
                Long lemmasCount = repositoryManager.getLemmaRepository().countLemmasBySiteUrl(siteProps.getUrl());

                if (lemmasCount != null) {
                    lemmas = lemmasCount.intValue();
                } else {
                    lemmas = 0;
                }

                status = siteEntity.getIndexStatus().name();
                error = siteEntity.getLastError();
                statusTime = siteEntity.getStatusTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();

            } else {
                pages = 0;
                lemmas = 0;
                status = "NOT INDEXED";
                error = "Ошибка индексации: сайт не проиндексирован";
                statusTime = System.currentTimeMillis();
            }

            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setName(siteProps.getName());
            item.setUrl(siteProps.getUrl());
            item.setStatus(status);
            item.setError(error);
            item.setStatusTime(statusTime);

            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return getSuccessResponse(response);
    }
}
