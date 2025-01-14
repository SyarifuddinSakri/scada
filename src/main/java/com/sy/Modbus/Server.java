package com.sy.Modbus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.sy.Modbus.Repo.AlarmLogRepo;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Server {
	protected HashMap<String, JSONObject> dataList = new HashMap<>();
	private List<ModbusDevice> ModbusDevices = new ArrayList<>();
	private AppConfig appConfig;
	private AlarmLogRepo alarmLogRepo;

	@Autowired
	public Server(AppConfig appConfig, AlarmLogRepo alarmLogRepo) {
		this.appConfig = appConfig;
		this.alarmLogRepo = alarmLogRepo;
	}

	public void init() {

		try {
			Path path = appConfig.configFile().toPath();
			String content = new String(Files.readAllBytes(path));
			JSONObject jsonObject = new JSONObject(content);
			// Iterate over the keys (which are the device names) and extract the IP
			// addresses
			@SuppressWarnings("unchecked")
			Iterator<String> keys = jsonObject.keys();
			while (keys.hasNext()) {
				String deviceName = keys.next();
				JSONObject device = jsonObject.getJSONObject(deviceName);
				String ipAddress = device.getString("ipAddress");
				System.out.println("Device: " + deviceName + ", IP Address: " + ipAddress);
				ModbusDevice modbusDevice = new ModbusDevice(deviceName, device, alarmLogRepo);
				ModbusDevices.add(modbusDevice);
			}

		} catch (IOException e) {
			System.out.println("Cannot Read the Json address File : " + e.getMessage());
		} catch (JSONException e) {
			System.out.println(
					"Warning : " + e.getMessage() + " If it is intentionally not available, ignore this message");
		}
		for (ModbusDevice ModbusDevice : ModbusDevices) {
			ModbusDevice.start();
		}
	}
}
