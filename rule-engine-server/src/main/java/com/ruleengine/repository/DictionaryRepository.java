package com.ruleengine.repository;

import com.ruleengine.domain.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {
    Optional<Dictionary> findByCode(String code);
}
