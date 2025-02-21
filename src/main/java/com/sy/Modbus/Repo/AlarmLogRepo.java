package com.sy.Modbus.Repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;

import java.util.List;

import com.sy.Modbus.Entity.AlarmLog;

@Repository
public interface AlarmLogRepo extends JpaRepository<AlarmLog, Long> {

	@Transactional
	List<AlarmLog> findBySiteName(String siteName);

}
