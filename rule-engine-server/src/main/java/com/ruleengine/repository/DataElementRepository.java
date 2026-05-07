package com.ruleengine.repository;

import com.ruleengine.domain.DataElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataElementRepository extends JpaRepository<DataElement, Long> {
    Optional<DataElement> findByCode(String code);
    Optional<DataElement> findByCamelNameAndDatasetId(String camelName, Long datasetId);
    List<DataElement> findByDictCode(String dictCode);
    List<DataElement> findByDatasetId(Long datasetId);
    long countByDatasetId(Long datasetId);
}
