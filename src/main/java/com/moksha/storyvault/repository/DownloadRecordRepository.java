package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.DownloadRecord;
import com.moksha.storyvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DownloadRecordRepository extends JpaRepository<DownloadRecord, Long> {

    List<DownloadRecord> findByStoryIdOrderByDownloadedAtDesc(Long storyId);

    @Query("SELECT dr FROM DownloadRecord dr WHERE dr.story.user = :user ORDER BY dr.downloadedAt DESC")
    List<DownloadRecord> findAllByUserOrderByDownloadedAtDesc(@Param("user") User user);

    Optional<DownloadRecord> findByIdAndStoryId(Long id, Long storyId);
}
