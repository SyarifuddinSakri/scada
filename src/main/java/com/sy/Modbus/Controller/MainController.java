package com.sy.Modbus.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import com.sy.Modbus.Entity.AlarmLog;
import com.sy.Modbus.Entity.RecordLog;
import com.sy.Modbus.Repo.AlarmLogRepo;
import com.sy.Modbus.Repo.RecordLogRepo;

@RestController
public class MainController {

	@Autowired
	AlarmLogRepo alarmLogRepo;

	@Autowired
	RecordLogRepo recordLogRepo;

	@GetMapping("/getAllAlarm/{site}")
	public List<AlarmLog> getAlarmBySite(@PathVariable String site) {
		return alarmLogRepo.findBySiteName(site);
	}

	@GetMapping("/getAllRecord/{site}")
	public List<RecordLog> getRecordBySite(@PathVariable String site) {
		return recordLogRepo.findBySiteName(site);
	}

	@GetMapping("/getAlarmBetweenDate/{site}")
	public List<AlarmLog> getAlarmBetweenDate(@PathVariable String site, @RequestParam String start,
			@RequestParam String end) {
		LocalDateTime startDate = LocalDateTime.parse(start);
		LocalDateTime endDate = LocalDateTime.parse(end);

		return alarmLogRepo.findBySiteNameAndLogDateBetween(site, startDate, endDate);
	}
}
