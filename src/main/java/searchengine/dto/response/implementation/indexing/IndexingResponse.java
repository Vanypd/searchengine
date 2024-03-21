package searchengine.dto.response.implementation.indexing;

import lombok.NoArgsConstructor;
import searchengine.dto.response.DefaultResponse;

@NoArgsConstructor
public class IndexingResponse extends DefaultResponse {
    public IndexingResponse(boolean result) {
        super(result);
    }
}
