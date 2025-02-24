package com.sy.Modbus.Repo;

import java.time.LocalDateTime;
import java.util.List;

import com.sy.Modbus.Entity.RecordLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;

@Repository
public interface RecordLogRepo extends JpaRepository<RecordLog, Long> {

	@Transactional
	List<RecordLog> findBySiteName(String siteName);

	// Search by siteName, tag and date Range
	List<RecordLog> findBySiteNameAndTagNameAndCreatedDateBetween(String siteName, String tagName,
			LocalDateTime startDate,
			LocalDateTime endDate);

	@Transactional
	@Modifying
	@Query("DELETE FROM RecordLog r WHERE r.createdDate < :cutoffDate")
	void deleteOlderThanTwoMonths(@Param("cutoffDate") LocalDateTime cutoffDate);

}
