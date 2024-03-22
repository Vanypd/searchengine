package searchengine.dto.response.implementation.indexing;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private Float relevance;
}
