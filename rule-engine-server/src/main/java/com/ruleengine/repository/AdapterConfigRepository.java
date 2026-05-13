package com.ruleengine.repository;

import com.ruleengine.domain.AdapterConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdapterConfigRepository extends JpaRepository<AdapterConfig, Long> {
}
