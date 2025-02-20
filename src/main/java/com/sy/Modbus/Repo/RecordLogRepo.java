package com.sy.Modbus.Repo;

import com.sy.Modbus.Entity.RecordLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecordLogRepo extends JpaRepository<RecordLog, Long>{

}
