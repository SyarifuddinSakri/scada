package com.sy.Modbus.Repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import com.sy.Modbus.Entity.AlarmLog;

@Repository
public interface AlarmLogRepo extends JpaRepository<AlarmLog, Long> {

	@Transactional
	List<AlarmLog> findBySiteName(String siteName);

	// Search by siteName and date Range
	List<AlarmLog> findBySiteNameAndLogDateBetween(String siteName, LocalDateTime startDate, LocalDateTime endDate);

	@Transactional
	@Modifying
	@Query("DELETE FROM AlarmLog r WHERE r.logDate < :cutoffDate")
	void deleteOlderThanTwoMonths(@Param("cutoffDate") LocalDateTime cutoffDate);

}
