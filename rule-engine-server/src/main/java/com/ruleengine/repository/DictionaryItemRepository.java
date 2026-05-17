package com.ruleengine.repository;

import com.ruleengine.domain.DictionaryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DictionaryItemRepository extends JpaRepository<DictionaryItem, Long> {
    List<DictionaryItem> findByDictCode(String dictCode);
    List<DictionaryItem> findByDictionaryId(Long dictionaryId);

    @Query("SELECT d FROM DictionaryItem d WHERE d.dictCode = :dictCode " +
           "AND (:keyword IS NULL OR :keyword = '' OR " +
           "  (:attr = 'itemCode' AND d.itemCode LIKE %:keyword%) OR " +
           "  (:attr = 'itemValue' AND d.itemValue LIKE %:keyword%) OR " +
           "  ((:attr IS NULL OR :attr = '' OR :attr = 'itemName') AND d.itemName LIKE %:keyword%))" +
           "AND (:status IS NULL OR :status = '' OR d.status = :status)")
    Page<DictionaryItem> searchByDictCode(@Param("dictCode") String dictCode,
                                          @Param("keyword") String keyword,
                                          @Param("status") String status,
                                          @Param("attr") String attr,
                                          Pageable pageable);

    @Query("SELECT d.dictCode, COUNT(d) FROM DictionaryItem d GROUP BY d.dictCode")
    List<Object[]> countByDictCode();
}
