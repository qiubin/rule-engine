package com.ruleengine.repository;

import com.ruleengine.domain.DataSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataSetRepository extends JpaRepository<DataSet, Long> {
    Optional<DataSet> findByCode(String code);
    Optional<DataSet> findByCatL3Code(String catL3Code);
}
