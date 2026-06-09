package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.TimelineEvent;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.TimelineEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TimelineEventRepository
        extends JpaRepository<TimelineEvent, Long>, JpaSpecificationExecutor<TimelineEvent> {

    @Modifying
    @Transactional
    @Query("DELETE FROM TimelineEvent e WHERE e.user = :user")
    void deleteAllByUser(@Param("user") User user);

    @Query("""
            SELECT COUNT(e) FROM TimelineEvent e
            WHERE e.user = :user
            AND e.eventType = :eventType
            AND e.eventTimestamp >= :from
            AND e.eventTimestamp <= :to
            """)
    long countByUserTypeAndRange(@Param("user") User user,
                                 @Param("eventType") TimelineEventType eventType,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(e) FROM TimelineEvent e
            WHERE e.user = :user
            AND e.eventType IN :eventTypes
            AND e.eventTimestamp >= :from
            AND e.eventTimestamp <= :to
            """)
    long countByUserTypesAndRange(@Param("user") User user,
                                  @Param("eventTypes") List<TimelineEventType> eventTypes,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(s.wordCount), 0) FROM TimelineEvent e
            JOIN e.story s
            WHERE e.user = :user
            AND e.eventType = com.moksha.storyvault.model.enums.TimelineEventType.STORY_FIRST_SEEN
            AND e.eventTimestamp >= :from
            AND e.eventTimestamp <= :to
            AND s.wordCount IS NOT NULL
            """)
    Long sumWordsForFirstSeenInRange(@Param("user") User user,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
