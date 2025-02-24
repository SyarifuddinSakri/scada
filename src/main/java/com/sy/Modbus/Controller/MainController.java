package com.sy.Modbus.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
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

@CrossOrigin(origins = "*")
@RestController
@EnableScheduling
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

	@GetMapping("/getRecordBetweenDate/{site}/{tagName}")
	public List<RecordLog> getRecordBetweenDate(@PathVariable String site, @PathVariable String tagName,
			@RequestParam String start,
			@RequestParam String end) {
		LocalDateTime startDate = LocalDateTime.parse(start);
		LocalDateTime endDate = LocalDateTime.parse(end);

		return recordLogRepo.findBySiteNameAndTagNameAndCreatedDateBetween(site, tagName, startDate, endDate);
	}

	@Scheduled(cron = "0 0 18 ? * MON-FRI", zone = "Asia/Kuala_Lumpur")
	// @Scheduled(cron = "0 * * * * ?", zone = "Asia/Kuala_Lumpur") // Every minutes
	public void deleteOldData() {
		alarmLogRepo.deleteOlderThanTwoMonths(LocalDateTime.now().minusMonths(2));
		recordLogRepo.deleteOlderThanTwoMonths(LocalDateTime.now().minusMonths(2));
	}
}
