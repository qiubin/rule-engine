package com.ruleengine.domain;

import com.ruleengine.domain.enums.CommonStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "data_set")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "english_name", length = 128)
    private String englishName;

    @Column(name = "cat_l1_code", length = 32)
    private String catL1Code;

    @Column(name = "cat_l1_name", length = 128)
    private String catL1Name;

    @Column(name = "cat_l2_code", length = 32)
    private String catL2Code;

    @Column(name = "cat_l2_name", length = 128)
    private String catL2Name;

    @Column(name = "cat_l3_code", length = 32)
    private String catL3Code;

    @Column(name = "cat_l3_name", length = 128)
    private String catL3Name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "description", length = 512)
    private String description;

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
