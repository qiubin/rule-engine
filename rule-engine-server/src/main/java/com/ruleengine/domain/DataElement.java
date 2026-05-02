package com.ruleengine.domain;

import com.ruleengine.domain.enums.CommonStatus;
import com.ruleengine.domain.enums.DataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "data_element")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "data_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private DataType dataType;

    @Column(name = "dict_code", length = 64)
    private String dictCode;

    @Column(name = "description", length = 512)
    private String description;

    @Lob
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "dataset_id")
    private Long datasetId;

    @Column(name = "status", length = 16)
    @Enumerated(EnumType.STRING)
    private CommonStatus status = CommonStatus.ENABLED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
