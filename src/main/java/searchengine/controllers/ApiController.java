package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.implementation.indexing.IndexingResponse;
import searchengine.dto.request.UrlDto;
import searchengine.dto.response.implementation.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;


    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }


    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return statisticsService.getStatistics();
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return indexingService.startIndexing();
    }


    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return indexingService.stopIndexing();
    }


    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestBody UrlDto url) {
        return indexingService.indexPage(url);
    }


    @GetMapping("/search")
    public ResponseEntity<IndexingResponse> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(required = false, defaultValue = "0") Integer offset,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {

        return indexingService.search(query, site, offset, limit);
    }
}
