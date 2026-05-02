package com.ruleengine.repository;

import com.ruleengine.domain.ResultConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultConfigRepository extends JpaRepository<ResultConfig, Long> {
    List<ResultConfig> findByConditionModelId(Long conditionModelId);
}
