package com.sy.Modbus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sy.Modbus.Repo.AlarmLogRepo;

import org.json.JSONArray;
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
	ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@Autowired
	public ModbusDevice(String deviceName, JSONObject deviceData, AlarmLogRepo alarmLogRepo) throws JSONException {
		super(deviceName, deviceData, alarmLogRepo);
		samplingAnalog(scheduler);

	}

	@Override
	public void onValueChangeDi(String key, boolean currentValue) {
		if (currentValue) {
			if (digitalAlarmTextIfTrue.containsKey(key)) {
				writeLog(getTime() + digitalAlarmTextIfTrue.get(key));
			}
		} else {
			if (digitalAlarmTextIfFalse.containsKey(key)) {
				writeLog(getTime() + digitalAlarmTextIfFalse.get(key));
			}
		}
	}

	@Override
	public void onValueChangeAi(String key, int currentValue) {
		try {
			int lowerLimit = analogTrigThresholdMin.get(key);
			int upperLimit = analogTrigThresholdMax.get(key);

			if (currentValue < lowerLimit && analogAllowWrite.get(key)) {
				analogAllowWrite.put(key, false);
				writeLog(getTime() + analogAlarmTextIfMin.get(key));
			} else if (currentValue > upperLimit && analogAllowWrite.get(key)) {
				analogAllowWrite.put(key, false);
				writeLog(getTime() + analogAlarmTextIfMax.get(key));
			} else if (currentValue > lowerLimit && currentValue < upperLimit && !analogAllowWrite.get(key)) {
				analogAllowWrite.put(key, true);
			}
		} catch (Exception e) {

		}
	}

	@Override
	public void onValueChangeText(String key, String currentValue) {

	}

	@Override
	public void onTick() {
		server.dataList.put(deviceName, data);

	}

	@Override
	public void onValueChangeFloat(String key, Float currentValue) {
		try {
			float lowerLimit = floatTrigThresholdMin.get(key);
			float upperLimit = floatTrigThresholdMax.get(key);

			if (currentValue < lowerLimit && floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, false);
				writeLog(getTime() + floatAlarmTextIfMin.get(key));
			} else if (currentValue > upperLimit && floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, false);
				writeLog(getTime() + floatAlarmTextIfMax.get(key));
			} else if (currentValue > lowerLimit && currentValue < upperLimit && !floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, true);
			}

		} catch (Exception e) {

		}
	}

	protected void samplingAnalog(ScheduledExecutorService scheduler) {
		LocalDateTime now = LocalDateTime.now();
		// LocalDateTime nextHour = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
		LocalDateTime nextHour = now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
		long delay = now.until(nextHour, ChronoUnit.MILLIS);

		scheduler.schedule(() -> {
			HashMap<String, HashMap<String, String>> finalValue = new HashMap<>();
			try {
				this.wrAddrrPermit.acquire();
				int index = 0;
				for (String tagType : this.tagNeedToRecord.keySet()) {
					if (data.has(tagType)) {
						JSONObject dataObject = data.getJSONObject(tagType);
						JSONObject dataObjectObj = dataObject.getJSONObject(tagNeedToRecord.get(tagType).get(index));
						index++;
						System.out.println(dataObjectObj);
					}
				}

				this.wrAddrrPermit.release();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				System.out.println("Cannot make sampling data " + e + deviceName);
			}

			samplingAnalog(scheduler);
		}, delay, TimeUnit.MILLISECONDS);
	}

}
