package com.sy.Modbus.Repo;

import java.util.List;

import com.sy.Modbus.Entity.RecordLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;

@Repository
public interface RecordLogRepo extends JpaRepository<RecordLog, Long> {

	@Transactional
	List<RecordLog> findBySiteName(String siteName);

}
