package com.ruleengine.service;

import com.ruleengine.domain.Dictionary;
import com.ruleengine.repository.DictionaryItemRepository;
import com.ruleengine.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictionaryService {

    private final DictionaryRepository repository;
    private final DictionaryItemRepository dictionaryItemRepository;

    public List<Dictionary> findAll() {
        List<Dictionary> dicts = repository.findAll();
        // 批量查询各字典的条目数，避免逐条触发延迟加载
        Map<String, Long> countMap = dictionaryItemRepository.countByDictCode()
                .stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0],
                    row -> (Long) row[1]
                ));
        for (Dictionary dict : dicts) {
            dict.setItemCount(countMap.getOrDefault(dict.getCode(), 0L).intValue());
        }
        return dicts;
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
