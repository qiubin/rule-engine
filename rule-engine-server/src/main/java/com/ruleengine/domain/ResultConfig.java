package com.ruleengine.domain;

import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "result_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_type", nullable = false, length = 64)
    private String resultType; // 结果类型编码

    @Column(name = "result_name", nullable = false, length = 128)
    private String resultName; // 结果类型名称

    @Column(name = "result_category", length = 64)
    private String resultCategory; // 结果分类：禁忌类别、药品禁忌级别等

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_model_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "resultConfigs"})
    private ConditionModel conditionModel;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
