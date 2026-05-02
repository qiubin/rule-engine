package com.ruleengine.service;

import com.ruleengine.domain.DataElement;
import com.ruleengine.repository.DataElementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataElementService {

    private final DataElementRepository repository;

    public List<DataElement> findAll() {
        return repository.findAll();
    }

    public DataElement findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据元不存在: " + id));
    }

    public List<DataElement> findByDictCode(String dictCode) {
        return repository.findByDictCode(dictCode);
    }

    public List<DataElement> findByDatasetId(Long datasetId) {
        return repository.findByDatasetId(datasetId);
    }

    @Transactional
    public DataElement save(DataElement element) {
        return repository.save(element);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
