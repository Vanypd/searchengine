package searchengine.dto.response.implementation.statistics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import searchengine.dto.response.DefaultResponse;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsResponse extends DefaultResponse {
    private StatisticsData statistics;
}
