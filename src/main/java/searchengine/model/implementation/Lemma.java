package searchengine.model.implementation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.DefaultModel;

@Getter
@Setter
@Entity
@Table(name = "lemma")
public class Lemma extends DefaultModel {

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site siteId;

    @Column(name = "lemma", columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(name = "frequency", columnDefinition = "INT", nullable = false)
    private Long frequency;
}
