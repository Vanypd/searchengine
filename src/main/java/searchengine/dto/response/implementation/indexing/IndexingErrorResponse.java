package searchengine.dto.response.implementation.indexing;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class IndexingErrorResponse extends IndexingResponse {
    private String error;
}
