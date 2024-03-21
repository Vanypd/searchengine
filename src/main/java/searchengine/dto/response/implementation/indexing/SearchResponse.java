package searchengine.dto.response.implementation.indexing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class SearchResponse extends IndexingResponse {
    long count;
    List<SearchResult> data;


    public SearchResponse(boolean result, long count, List<SearchResult> data) {
        super(result);
        this.count = count;
        this.data = data;
    }
}
