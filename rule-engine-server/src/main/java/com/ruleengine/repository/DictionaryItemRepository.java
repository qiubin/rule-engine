package com.ruleengine.repository;

import com.ruleengine.domain.DictionaryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DictionaryItemRepository extends JpaRepository<DictionaryItem, Long> {
    List<DictionaryItem> findByDictCode(String dictCode);
    List<DictionaryItem> findByDictionaryId(Long dictionaryId);
}
