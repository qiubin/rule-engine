package com.ruleengine.service;

import com.ruleengine.domain.DictionaryItem;
import com.ruleengine.repository.DictionaryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictionaryItemService {

    private final DictionaryItemRepository repository;

    public List<DictionaryItem> findAll() {
        return repository.findAll();
    }

    public DictionaryItem findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("字典项不存在: " + id));
    }

    public List<DictionaryItem> findByDictCode(String dictCode) {
        return repository.findByDictCode(dictCode);
    }

    public List<DictionaryItem> findByDictionaryId(Long dictionaryId) {
        return repository.findByDictionaryId(dictionaryId);
    }

    public Page<DictionaryItem> searchByDictCode(String dictCode, String keyword, String status, String attr, Pageable pageable) {
        return repository.searchByDictCode(dictCode, keyword, status, attr, pageable);
    }

    @Transactional
    public DictionaryItem save(DictionaryItem item) {
        return repository.save(item);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
