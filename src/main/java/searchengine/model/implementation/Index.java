package searchengine.model.implementation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.DefaultModel;

@Getter
@Setter
@Entity
@Table(name = "`index`")
public class Index extends DefaultModel {

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page pageId;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemmaId;

    @Column(name = "`rank`", columnDefinition = "FLOAT", nullable = false)
    private Float rank;
}
