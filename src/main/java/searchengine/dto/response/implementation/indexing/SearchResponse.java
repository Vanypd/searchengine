package searchengine.dto.response.implementation.indexing;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponse extends IndexingResponse {
    private long count;
    private List<SearchResult> data;


    public SearchResponse(boolean result, long count, List<SearchResult> data) {
        super(result);
        this.count = count;
        this.data = data;
    }
}
