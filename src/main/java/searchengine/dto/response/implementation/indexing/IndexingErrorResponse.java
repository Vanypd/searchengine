package searchengine.dto.response.implementation.indexing;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndexingErrorResponse extends IndexingResponse {
    private String error;
}
