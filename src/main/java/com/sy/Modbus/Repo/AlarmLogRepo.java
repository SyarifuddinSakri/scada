package com.sy.Modbus.Repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sy.Modbus.Entity.AlarmLog;

@Repository
public interface AlarmLogRepo extends JpaRepository<AlarmLog, Long> {

}
