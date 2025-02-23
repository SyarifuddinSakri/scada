package com.sy.Modbus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sy.Modbus.Entity.RecordLog;
import com.sy.Modbus.Repo.AlarmLogRepo;
import com.sy.Modbus.Repo.RecordLogRepo;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@EnableScheduling
@Component
@Scope("prototype")
public class ModbusDevice extends ModbusDeviceTransaction {
	@Autowired
	Server server;
	@Autowired
	RecordLog recordLog;
	@Autowired
	RecordLogRepo recordLogRepo;
	ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	ScheduledExecutorService scheduler2 = Executors.newSingleThreadScheduledExecutor();

	@Autowired
	public ModbusDevice(String deviceName, JSONObject deviceData, AlarmLogRepo alarmLogRepo, RecordLogRepo recordLogRepo)
			throws JSONException {
		super(deviceName, deviceData, alarmLogRepo);
		this.recordLogRepo = recordLogRepo;
		samplingAnalog(scheduler);
		samplingFloat(scheduler2);

	}

	@Override
	public void onValueChangeDi(String key, boolean currentValue) {
		if (currentValue) {
			if (digitalAlarmTextIfTrue.containsKey(key)) {
				writeLog(digitalAlarmTextIfTrue.get(key));
			}
		} else {
			if (digitalAlarmTextIfFalse.containsKey(key)) {
				writeLog(digitalAlarmTextIfFalse.get(key));
			}
		}
	}

	@Override
	public void onValueChangeAi(String key, Integer currentValue) {
		try {
			Integer lowerLimit = analogTrigThresholdMin.get(key);
			Integer upperLimit = analogTrigThresholdMax.get(key);

			if (lowerLimit != null && currentValue < lowerLimit && analogAllowWrite.get(key)) {
				analogAllowWrite.put(key, false);
				writeLog(analogAlarmTextIfMin.get(key));
			} else if (upperLimit != null && currentValue > upperLimit && analogAllowWrite.get(key)) {
				analogAllowWrite.put(key, false);
				writeLog(analogAlarmTextIfMax.get(key));
			} else if ((lowerLimit == null || currentValue > lowerLimit) && (upperLimit == null || currentValue < upperLimit)
					&& !analogAllowWrite.get(key)) {
				// reset allowing write for the analog alarm
				analogAllowWrite.put(key, true);
				writeLog(analogAlarmTextIfNormal.get(key));
			}
		} catch (Exception e) {
			System.out.println("Exception occurs during checking the Analog alarm in onValueChangeAi method" + e);
		}
	}

	@Override
	public void onValueChangeText(String key, String currentValue) {

	}

	@Override
	public void onTick() {
		// server.dataList.put(deviceName, data);

	}

	@Override
	public void onValueChangeFloat(String key, Float currentValue) {
		try {
			Float lowerLimit = floatTrigThresholdMin.get(key);
			Float upperLimit = floatTrigThresholdMax.get(key);

			if (lowerLimit != null && currentValue < lowerLimit && floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, false);
				writeLog(floatAlarmTextIfMin.get(key));
			} else if (upperLimit != null && currentValue > upperLimit && floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, false);
				writeLog(floatAlarmTextIfMax.get(key));
			} else if ((lowerLimit == null || currentValue > lowerLimit) && (upperLimit == null || currentValue < upperLimit)
					&& !floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, true);
				writeLog(floatAlarmTextIfNormal.get(key));
			}

		} catch (Exception e) {

		}
	}

	protected void samplingAnalog(ScheduledExecutorService scheduler) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextHour = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
		// LocalDateTime nextHour = now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
		long delay = now.until(nextHour, ChronoUnit.MILLIS);

		scheduler.schedule(() -> {
			try {
				this.wrAddrrPermit.acquire();
				if (data.has("Analog") && !data.isNull("Analog")) {
					// need to be casted into string as some error occurs if directly taken from
					// JSONObject
					String dataString = data.toString();
					JSONObject dataJson = new JSONObject(dataString);
					JSONObject dataAnalog = dataJson.getJSONObject("Analog");

					// loop through all the processNeedToRecord() return function in Transaction
					for (String tag : tagNeedToRecordAnalog) {
						// Please make database transaction for the sampling Analog
						RecordLog record = new RecordLog();
						record.setData(dataAnalog.get(tag).toString());
						record.setTagName(tag);
						record.setSiteName(this.deviceName);
						recordLogRepo.save(record);
					}
				}
				this.wrAddrrPermit.release();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				this.wrAddrrPermit.release();
			} finally {
				this.wrAddrrPermit.release();
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	protected void samplingFloat(ScheduledExecutorService scheduler) {
		LocalDateTime now = LocalDateTime.now();
		// LocalDateTime nextHour = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
		LocalDateTime nextHour = now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
		long delay = now.until(nextHour, ChronoUnit.MILLIS);

		scheduler.schedule(() -> {
			try {
				this.wrAddrrPermit.acquire();
				if (data.has("Float") && !data.isNull("Float")) {
					// need to be casted into string as some error occurs if directly taken from
					// JSONObject
					String dataString = data.toString();
					JSONObject dataJson = new JSONObject(dataString);
					JSONObject dataFloat = dataJson.getJSONObject("Float");

					// loop through all the processNeedToRecord() return function in Transaction
					for (String tag : tagNeedToRecordFloat) {
						// Please make database transaction for the sampling Analog
						RecordLog record = new RecordLog();
						record.setData(dataFloat.get(tag).toString());
						record.setTagName(tag);
						record.setSiteName(this.deviceName);
						recordLogRepo.save(record);
					}
				}
				this.wrAddrrPermit.release();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				this.wrAddrrPermit.release();
			} finally {
				this.wrAddrrPermit.release();
			}
		}, delay, TimeUnit.MILLISECONDS);
	}
}
