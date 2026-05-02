package com.ruleengine.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruleengine.domain.enums.DataType;
import com.ruleengine.domain.enums.NodeUsage;
import com.ruleengine.domain.enums.ValueSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "condition_model")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConditionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "data_type", length = 32)
    @Enumerated(EnumType.STRING)
    private DataType dataType;

    @ElementCollection
    @CollectionTable(name = "condition_model_operators", joinColumns = @JoinColumn(name = "condition_model_id"))
    @Column(name = "operator")
    private List<String> operators;

    @Column(name = "value_source", length = 16)
    @Enumerated(EnumType.STRING)
    private ValueSource valueSource;

    @Lob
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "data_element_id")
    private Long dataElementId;

    @Column(name = "category_id")
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "conditionModels"})
    private ConditionModelCategory category;

    @Column(name = "node_usage", length = 16)
    @Enumerated(EnumType.STRING)
    private NodeUsage nodeUsage = NodeUsage.BOTH;

    @OneToMany(mappedBy = "conditionModel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "conditionModel"})
    private List<ResultConfig> resultConfigs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
