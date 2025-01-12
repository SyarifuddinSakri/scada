package com.sy.Modbus;

import com.sy.Modbus.Repo.AlarmLogRepo;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class ModbusDevice extends ModbusDeviceTransaction {
	@Autowired
	Server server;

	@Autowired
	public ModbusDevice(String deviceName, JSONObject deviceData, AlarmLogRepo alarmLogRepo) throws JSONException {
		super(deviceName, deviceData, alarmLogRepo);
	}

	@Override
	public void onValueChangeDi(String key, boolean currentValue) {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub

	}

	@Override
	public void onTick() {
		// TODO Auto-generated method stub
		server.dataList.put(deviceName, data);

	}

	@Override
	public void onValueChangeFloat(String key, Float currentValue) {
		// TODO Auto-generated method stub
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
}
