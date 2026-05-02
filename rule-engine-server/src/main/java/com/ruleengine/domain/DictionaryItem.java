package com.ruleengine.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruleengine.domain.enums.CommonStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dictionary_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dict_code", nullable = false, length = 64)
    private String dictCode;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 128)
    private String itemName;

    @Column(name = "item_value", length = 128)
    private String itemValue;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "status", length = 16)
    @Enumerated(EnumType.STRING)
    private CommonStatus status = CommonStatus.ENABLED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dict_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "items"})
    private Dictionary dictionary;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
