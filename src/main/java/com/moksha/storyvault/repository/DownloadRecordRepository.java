package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.DownloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DownloadRecordRepository extends JpaRepository<DownloadRecord, Long> {

    List<DownloadRecord> findByStoryIdOrderByDownloadedAtDesc(Long storyId);
}
