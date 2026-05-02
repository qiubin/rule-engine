package com.ruleengine.service;

import com.ruleengine.domain.Dictionary;
import com.ruleengine.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictionaryService {

    private final DictionaryRepository repository;

    public List<Dictionary> findAll() {
        return repository.findAll();
    }

    public Dictionary findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("字典不存在: " + id));
    }

    @Transactional
    public Dictionary save(Dictionary dictionary) {
        return repository.save(dictionary);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
