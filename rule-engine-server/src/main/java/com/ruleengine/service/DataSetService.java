package com.ruleengine.service;

import com.ruleengine.domain.DataSet;
import com.ruleengine.repository.DataElementRepository;
import com.ruleengine.repository.DataSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataSetService {

    private final DataSetRepository repository;
    private final DataElementRepository dataElementRepository;

    public List<DataSet> findAll() {
        return repository.findAll();
    }

    public DataSet findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据集不存在: " + id));
    }

    @Transactional
    public DataSet save(DataSet dataSet) {
        return repository.save(dataSet);
    }

    @Transactional
    public void deleteById(Long id) {
        long count = dataElementRepository.countByDatasetId(id);
        if (count > 0) {
            throw new RuntimeException("该数据集下存在 " + count + " 个数据元，无法删除");
        }
        repository.deleteById(id);
    }
}
