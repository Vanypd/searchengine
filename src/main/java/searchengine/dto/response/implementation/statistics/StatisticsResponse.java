package searchengine.dto.response.implementation.statistics;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.response.DefaultResponse;

@Getter
@Setter
public class StatisticsResponse extends DefaultResponse {
    private StatisticsData statistics;
}
