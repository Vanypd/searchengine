package searchengine.model.implementation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.DefaultModel;

@Getter
@Setter
@Entity
@Table(name = "page")
public class Page extends DefaultModel {

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site siteId;

    @Column(name = "path", columnDefinition = "TEXT", nullable = false)
    private String path;

    @Column(name = "code", columnDefinition = "INT", nullable = false)
    private Integer code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
