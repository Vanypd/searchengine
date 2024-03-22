package searchengine.dto.response.implementation.statistics;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TotalStatistics {
    private int sites;
    private int pages;
    private int lemmas;
    private boolean indexing;
}
