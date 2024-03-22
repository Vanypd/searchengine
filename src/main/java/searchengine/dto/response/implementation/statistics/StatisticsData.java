package searchengine.dto.response.implementation.statistics;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsData {
    private TotalStatistics total;
    private List<DetailedStatisticsItem> detailed;
}
