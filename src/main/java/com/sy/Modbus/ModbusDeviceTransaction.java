package com.sy.Modbus;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import com.sy.Modbus.Entity.AlarmLog;
import com.sy.Modbus.Repo.AlarmLogRepo;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import net.wimpi.modbus.ModbusException;
import net.wimpi.modbus.ModbusIOException;
import net.wimpi.modbus.ModbusSlaveException;
import net.wimpi.modbus.io.ModbusTCPTransaction;
import net.wimpi.modbus.msg.ReadCoilsRequest;
import net.wimpi.modbus.msg.ReadCoilsResponse;
import net.wimpi.modbus.msg.ReadInputDiscretesRequest;
import net.wimpi.modbus.msg.ReadInputDiscretesResponse;
import net.wimpi.modbus.msg.ReadInputRegistersRequest;
import net.wimpi.modbus.msg.ReadInputRegistersResponse;
import net.wimpi.modbus.msg.WriteCoilRequest;
import net.wimpi.modbus.net.TCPMasterConnection;

public abstract class ModbusDeviceTransaction extends WebSocketServer {
	public String ipAddr;
	public int modbusPort;
	public int webSocketPort;
	TCPMasterConnection connection;
	ModbusTCPTransaction trans;
	// -------------------------------------------addresses-----------------------------
	protected HashMap<String, Integer> diAddr = new HashMap<>();// boolean for Reading Digital Input
	protected HashMap<String, Integer> aiAddr = new HashMap<>();// Unsigned 16 bits data
	protected HashMap<String, Integer> aiSAddr = new HashMap<>();// Signed 16 bits data
	protected HashMap<String, Integer> ai32Addr = new HashMap<>();// There is no 32 bits Unsigned data Type in java
	protected HashMap<String, Integer> coilAddr = new HashMap<>();// Boolean but for reading Coil status not Digital
	// Inputs
	protected HashMap<Integer, Boolean> writeCoil = new HashMap<>();// address for writing Coil
	protected HashMap<String, HashMap<String, Integer>> textAddr = new HashMap<>();
	protected HashMap<String, Integer> floatAddr = new HashMap<>();
	// -------------------------------------address that will be executed to record
	// the data------------------------
	protected ArrayList<String> tagNeedToRecordAnalog = new ArrayList<>();
	// ----------------------------------------------output----------------------------
	protected HashMap<String, Boolean> outputDi = new HashMap<>();
	protected HashMap<String, Integer> outputAi = new HashMap<>();
	protected HashMap<String, String> outputText = new HashMap<>();
	protected HashMap<String, Float> outputFloat = new HashMap<>();
	protected HashMap<String, Integer> notAvail = new HashMap<>();
	public JSONObject data = new JSONObject();// all data containing the all output for the first time webSocket
	// initialized
	// ------------------------------------------------oldData----------------------------------
	protected HashMap<String, Boolean> oldDi = new HashMap<>();
	protected HashMap<String, Integer> oldAi = new HashMap<>();
	protected HashMap<String, String> oldText = new HashMap<>();
	protected HashMap<String, Float> oldFloat = new HashMap<>();
	// ----------------------------------------------------alarm
	// Map----------------------------
	protected HashMap<String, Boolean> alarmDiMap = new HashMap<>();
	protected HashMap<String, Integer> alarmAiMap = new HashMap<>();
	protected HashMap<String, Float> alarmFloatMap = new HashMap<>();
	protected HashMap<String, String> alarmTextMap = new HashMap<>();
	public JSONObject alarmObj = new JSONObject();
	// --------------------------------------------------alarm Text and
	// Threshold-------------------
	protected HashMap<String, String> digitalAlarmTextIfFalse = new HashMap<>();
	protected HashMap<String, String> digitalAlarmTextIfTrue = new HashMap<>();
	protected HashMap<String, Boolean> analogAllowWrite = new HashMap<>();
	protected HashMap<String, Integer> analogTrigThresholdMin = new HashMap<>();
	protected HashMap<String, Integer> analogTrigThresholdMax = new HashMap<>();
	protected HashMap<String, String> analogAlarmTextIfMin = new HashMap<>();
	protected HashMap<String, String> analogAlarmTextIfMax = new HashMap<>();
	protected HashMap<String, String> analogAlarmTextIfNormal = new HashMap<>();
	protected HashMap<String, Boolean> floatAllowWrite = new HashMap<>();
	protected HashMap<String, Float> floatTrigThresholdMin = new HashMap<>();
	protected HashMap<String, Float> floatTrigThresholdMax = new HashMap<>();
	protected HashMap<String, String> floatAlarmTextIfMin = new HashMap<>();
	protected HashMap<String, String> floatAlarmTextIfMax = new HashMap<>();
	protected HashMap<String, String> floatAlarmTextIfNormal = new HashMap<>();
	// ------------------------------------------------------misc--------------------------------
	protected volatile boolean isAlive = false;
	protected volatile boolean allowWritePing = true;
	protected volatile boolean allowConnErrorWrite = true;
	protected volatile boolean allowConnRefuseWrite = true;
	public String deviceName;
	protected JSONObject deviceData;
	public Semaphore permit = new Semaphore(2);
	protected Semaphore wrAddrrPermit = new Semaphore(1);
	// -------------------------------------------------------webSockets--------------------------
	public HashMap<String, WebSocket> clients = new HashMap<>();
	public volatile boolean isWatching;
	@Autowired
	private AlarmLogRepo alarmLogRepo;

	public ModbusDeviceTransaction(String deviceName, JSONObject deviceData, AlarmLogRepo alarmLogRepo)
			throws JSONException {
		super(new InetSocketAddress(deviceData.getInt("portWebSocket")));
		try {
			this.deviceData = deviceData;
			this.ipAddr = deviceData.getString("ipAddress");
			this.modbusPort = deviceData.getInt("portModbus");
			this.webSocketPort = deviceData.getInt("portWebSocket");
			this.deviceName = deviceName;
			this.alarmLogRepo = alarmLogRepo;
		} catch (JSONException e) {
			System.out.println("Error in Initilizing a Device");
		}
	}

	@Override
	public void start() {
		putAllAddressAlarm();
		System.out.println(getTime() + deviceName + " is starting.");
		writeLog(deviceName + " is starting.");
		super.start();
		timerTask();
	}

	@Override
	public void onClose(WebSocket arg0, int arg1, String arg2, boolean arg3) {
		String clientId = arg0.getRemoteSocketAddress().getAddress().getHostAddress().toString();
		System.out.println(clientId + " closed");
		clients.remove(clientId);
		if (clients.isEmpty()) {
			isWatching = false;
		}
	}

	@Override
	public void onError(WebSocket arg0, Exception arg1) {
		if (arg0 != null) {
			String clientId = arg0.getRemoteSocketAddress().getAddress().getHostAddress().toString();
			clients.remove(clientId);
			if (clients.isEmpty()) {
				isWatching = false;
			}
		}
	}

	@Override
	public void onMessage(WebSocket arg0, String arg1) {
		System.out.println(arg1);
		String action;
		int address;
		boolean valueDigital;
		// int valueAnalog;
		JSONObject arg1Obj;
		try {
			arg1Obj = new JSONObject(arg1);
			action = arg1Obj.getString("action");
			address = arg1Obj.getInt("address");
			if (action.equalsIgnoreCase("digital")) {
				valueDigital = arg1Obj.getBoolean("value");
				System.out.println(getTime() + arg0.getRemoteSocketAddress().getAddress().getHostName() + " Wrote "
						+ valueDigital + " to address " + address);
				writeLog(arg0.getRemoteSocketAddress().getAddress().getHostName() + " Wrote " + valueDigital
						+ " to address " + address);
				writeCoil.put(address, valueDigital);
			} else if (action.equalsIgnoreCase("analog")) {// if sending analog data to device is needed, enable this
				// valueAnalog = arg1Obj.getInt("value");
			} else if (action.equalsIgnoreCase("ReadAddress")) {
				putAllAddressAlarm();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onOpen(WebSocket arg0, ClientHandshake arg1) {
		isWatching = true;
		clients.put(arg0.getRemoteSocketAddress().getAddress().getHostAddress().toString(), arg0);
		System.out.println(getTime() + arg0.getRemoteSocketAddress().getAddress().getHostAddress().toString()
				+ "connected with webSocket from " + this.deviceName);
		sendMessage(data.toString());
	}

	public void timerTask() {
		TimerTask checkPing = new TimerTask() {
			@Override
			public void run() {
				try {
					alive();
				} catch (IOException e) {
					System.out.println(getTime() + deviceName + " is Unreachable");
					writeLog(deviceName + " is Unreachable");
				}
			}
		};
		Timer timerPing = new Timer();
		timerPing.scheduleAtFixedRate(checkPing, 0, 10000);
		// TimerTask query = new TimerTask() {
		Thread thread = new Thread() {

			@Override
			public void run() {
				for (;;) {
					// while(isWatching) {//enabling this will make the data pulling occurs only
					// when user isWatching the data through the web
					try {
						if (isAlive && (connection == null)) {
							if (allowConnErrorWrite) {
								// System.out.println(getTime()+"Communication to "+deviceName+" has changed");
								// writeLog(deviceName+"Communication to "+deviceName+" has changed");
								allowConnErrorWrite = false;
							}
							connect();
						}
						if (connection != null) {
							allowConnErrorWrite = true;
							try {
								sendRequest(1);// function 02 Read Discrete Input (1 bit)
								sendRequest(2);// function 04 Read Input Register (16 bits)
								sendRequest(3);// function 04 Read Input Register (32 bits)
								sendRequest(4);// function 04 Read Input Register (16 bits Signed)
								sendRequest(5);// function 01 Read Coil Status (1 bit)
								sendRequest(6);// function 04 Read Input Register (depends on how long text Address set
								// and change it to Text for each 8 bits)
								sendRequest(7);// function 04 Read Input Register (32 bits) and parse it into Float
								// value
								System.gc();
							} catch (JSONException e) {
								System.out.println(getTime() + "Error generating JSON data : " + ipAddr);
							} catch (SocketTimeoutException e) {
								System.out.println(getTime() + "Timeout : " + ipAddr);
								connect();
							}
							// Gather,combine and print the outputs here
							// Allows only the useful data to be displayed
							try {
								data.put("Digital", outputDi);
								data.put("Analog", outputAi);
								data.put("Text", outputText);
								data.put("Float", outputFloat);
								data.put("NotAvailable", notAvail);
								onTick();
								// System.out.println(data.toString()); //to Display all data output even
								// without any change
								// server.angData = data;//<----------to output the data
								if (alarmObj.length() != 0) {
									sendMessage(alarmObj.toString());// data that will be sent to webSocket
									System.out.println(alarmObj.toString());// data that display on Change only
									alarmObj.remove("Analog");// clear analog data from alarm
									alarmObj.remove("Digital");// clear digital data from alarm
									alarmObj.remove("Text");// clear Text data from alarm
									alarmObj.remove("Float");// clear Float data from alarm
								}
							} catch (JSONException e) {
								System.out.println(getTime() + "Fail to create JSON data from : " + ipAddr);
								e.printStackTrace();
							}
						}
					} catch (IOException e) {
						connection = null;
						if (allowConnRefuseWrite) {
							System.out.println(getTime() + deviceName + " " + e.getMessage());
							writeLog(deviceName + " " + e.getMessage());
							allowConnRefuseWrite = false;
						}
						// System.out.println(e.getMessage());
					} catch (Exception e) {
						connection = null;
						System.out.println(e.getMessage());
					}
				}
			}
			// } //isWatching stop bracket
		};
		thread.start();
		//
		// Timer timer = new Timer();
		// timer.scheduleAtFixedRate(query,0,1000);
	}

	public void alive() throws IOException {
		InetAddress address = InetAddress.getByName(ipAddr);
		isAlive = address.isReachable(10000);
		if (!isAlive && allowWritePing) {
			System.out.println(getTime() + deviceName + " is Unreachable");
			writeLog(deviceName + " is Unreachable");
			allowWritePing = false;
		} else if (isAlive & !allowWritePing) {
			System.out.println(getTime() + deviceName + " is Reachable");
			writeLog(deviceName + " is Reachable");
			allowWritePing = true;
		}
	}

	private void connect() throws Exception {
		connection = new TCPMasterConnection(InetAddress.getByName(ipAddr));
		connection.setPort(modbusPort);
		connection.connect();
		connection.setTimeout(5000);
		System.out.println(getTime() + "Connected to : " + deviceName);
		writeLog("Connected to : " + deviceName);
		allowConnRefuseWrite = true;
	}

	public synchronized void sendRequest(int transCode) throws JSONException, SocketTimeoutException {
		if (connection == null) {
			return;
		}
		try {
			trans = new ModbusTCPTransaction(connection);
			if (transCode == 1) {
				if (!(diAddr.isEmpty())) {
					try {
						wrAddrrPermit.acquire();
						for (String key : diAddr.keySet()) {
							if (writeCoil.isEmpty()) {
								permit.acquire();
								ReadInputDiscretesRequest readDi = new ReadInputDiscretesRequest(diAddr.get(key), 1);
								trans.setRequest(readDi);
								try {
									trans.execute();
									ReadInputDiscretesResponse response = (ReadInputDiscretesResponse) trans
											.getResponse();
									boolean result = response.getDiscreteStatus(0);
									outputDi.put(key, result);
									alarmDi(key, result);
									response = null;
								} catch (ModbusIOException e) {
									connection = null;
								} catch (ModbusSlaveException e) {
									System.out.println(getTime() + "Warning, the address " + diAddr.get(key)
											+ " named \"" + key + "\" is not available in the device : " + ipAddr
											+ " Please check your Digital Address" + e.getMessage());
									writeLog("Warning, the address " + diAddr.get(key) + "  named \""
											+ key + "\" is not available in the device : " + ipAddr
											+ " Please check your Digital Address" + e.getMessage());
									notAvail.put(key, diAddr.get(key));
									diAddr.remove(key);
								}
								readDi = null;
								permit.release();
							} else {
								for (int coilAddr : writeCoil.keySet()) {
									setCoil(coilAddr, writeCoil.get(coilAddr));
									writeCoil.remove(coilAddr);
								}
							}
						}
					} catch (InterruptedException e) {

					} finally {
						wrAddrrPermit.release();
					}
				}
			} else if (transCode == 2) {
				if (!(aiAddr.isEmpty())) {
					try {
						wrAddrrPermit.acquire();
						for (String key : aiAddr.keySet()) {
							if (writeCoil.isEmpty()) {
								permit.acquire();
								ReadInputRegistersRequest readAi = new ReadInputRegistersRequest(aiAddr.get(key), 1);
								trans.setRequest(readAi);
								try {
									trans.execute();
									ReadInputRegistersResponse response = (ReadInputRegistersResponse) trans
											.getResponse();
									int result = response.getRegisterValue(0);
									outputAi.put(key, result);
									alarmAi(key, result);
									response = null;
								} catch (ModbusIOException e) {
									connection = null;
								} catch (ModbusSlaveException e) {
									System.out.println(getTime() + "Warning, the address " + aiAddr.get(key)
											+ " named \"" + key + "\" is not available in the device : "
											+ ipAddr + " Please check your Analog 16 bits Address");
									writeLog("Warning, the address " + aiAddr.get(key) + " named \""
											+ key + "\" is not available in the device : " + ipAddr
											+ " Please check your Analog 16 bits Address");
									notAvail.put(key, aiAddr.get(key));
									aiAddr.remove(key);
								}
								readAi = null;
								permit.release();
							} else {
								for (int coilAddr : writeCoil.keySet()) {
									setCoil(coilAddr, writeCoil.get(coilAddr));
									writeCoil.remove(coilAddr);
								}
							}
						}
					} catch (InterruptedException e) {

					} finally {
						wrAddrrPermit.release();
					}
				}
			} else if (transCode == 3) {
				if (!(ai32Addr.isEmpty())) {
					try {
						wrAddrrPermit.acquire();
						for (String key : ai32Addr.keySet()) {
							if (writeCoil.isEmpty()) {
								permit.acquire();
								ReadInputRegistersRequest readAi = new ReadInputRegistersRequest(ai32Addr.get(key), 2);
								trans.setRequest(readAi);
								try {
									trans.execute();
									ReadInputRegistersResponse response = (ReadInputRegistersResponse) trans
											.getResponse();
									short short1 = (short) response.getRegisterValue(0);
									short short2 = (short) response.getRegisterValue(1);
									int result = ((int) short1 << 16) | (short2 & 0xFFFF);
									outputAi.put(key, result);
									alarmAi(key, result);
									response = null;
								} catch (ModbusIOException e) {
									connection = null;
								} catch (ModbusSlaveException e) {
									System.out.println(getTime() + "Warning, the address " + ai32Addr.get(key)
											+ " named \"" + key + "\" is not available in the device : "
											+ ipAddr + " Please check your Analog 32 bits Address");
									writeLog("Warning, the address " + ai32Addr.get(key)
											+ " named \"" + key + "\" is not available in the device : "
											+ ipAddr + " Please check your Analog 32 bits Address");
									notAvail.put(key, ai32Addr.get(key));
									ai32Addr.remove(key);
								}
								readAi = null;
								permit.release();
							} else {
								for (int coilAddr : writeCoil.keySet()) {
									setCoil(coilAddr, writeCoil.get(coilAddr));
									writeCoil.remove(coilAddr);
								}
							}
						}
					} catch (InterruptedException e) {

					} finally {
						wrAddrrPermit.release();
					}
				}
			} else if (transCode == 4) {
				if (!(aiSAddr.isEmpty())) {
					try {
						wrAddrrPermit.acquire();
						for (String key : aiSAddr.keySet()) {
							if (writeCoil.isEmpty()) {
								permit.acquire();
								ReadInputRegistersRequest readAi = new ReadInputRegistersRequest(aiSAddr.get(key), 1);
								trans.setRequest(readAi);
								try {
									trans.execute();
									ReadInputRegistersResponse response = (ReadInputRegistersResponse) trans
											.getResponse();
									short result = (short) response.getRegisterValue(0);
									outputAi.put(key, (int) result);
									alarmAi(key, (int) result);
									response = null;
								} catch (ModbusIOException e) {
									connection = null;
								} catch (ModbusSlaveException e) {
									System.out.println(getTime() + "Warning, the address " + aiSAddr.get(key)
											+ " named \"" + key + "\" is not available in the device : "
											+ ipAddr + " Please check your Analog 16 bits Signed Address");
									writeLog("Warning, the address " + aiSAddr.get(key) + " named \""
											+ key + "\" is not available in the device : " + ipAddr
											+ " Please check your Analog 16 Signed bits Address");
									notAvail.put(key, aiSAddr.get(key));
									aiSAddr.remove(key);
								}
								readAi = null;
								permit.release();
							} else {
								for (int coilAddr : writeCoil.keySet()) {
									setCoil(coilAddr, writeCoil.get(coilAddr));
									writeCoil.remove(coilAddr);
								}
							}
						}
					} catch (InterruptedException e) {

					} finally {
						wrAddrrPermit.release();
					}
				}
			} else if (transCode == 5) {
				if (!(coilAddr.isEmpty())) {
					try {
						wrAddrrPermit.acquire();
						for (String key : coilAddr.keySet()) {
							if (writeCoil.isEmpty()) {
								permit.acquire();
								ReadCoilsRequest readCoil = new ReadCoilsRequest(coilAddr.get(key), 1);
								trans.setRequest(readCoil);
								try {
									trans.execute();
									ReadCoilsResponse response = (ReadCoilsResponse) trans.getResponse();
									boolean result = response.getCoilStatus(0);
									outputDi.put(key, result);
									alarmDi(key, result);
									response = null;
								} catch (ModbusIOException e) {
									connection = null;
								} catch (ModbusSlaveException e) {
									System.out.println(getTime() + "Warning, the address " + coilAddr.get(key)
											+ " named \"" + key
											+ "\" is not available in the device. Please check your Read Coil Status Address");
									writeLog("Warning, the address " + coilAddr.get(key)
											+ " named \"" + key
											+ "\" is not available in the device. Please check your Read Coil Status Address");
									notAvail.put(key, coilAddr.get(key));
									coilAddr.remove(key);
								}
								readCoil = null;
								permit.release();
							} else {
								for (int coilAddr : writeCoil.keySet()) {
									setCoil(coilAddr, writeCoil.get(coilAddr));
									writeCoil.remove(coilAddr);
								}
							}
						}
					} catch (InterruptedException e) {

					} finally {
						wrAddrrPermit.release();
					}
				}
			} else if (transCode == 6) {
				if (!(textAddr.isEmpty())) {
					try {
						wrAddrrPermit.acquire();
						for (String key : textAddr.keySet()) {
							if (writeCoil.isEmpty()) {
								permit.acquire();
								int address = textAddr.get(key).get("Address");
								int length = textAddr.get(key).get("Length");
								ReadInputRegistersRequest readText = new ReadInputRegistersRequest(address, length);
								trans.setRequest(readText);
								try {
									trans.execute();
									ReadInputRegistersResponse response = (ReadInputRegistersResponse) trans
											.getResponse();
									StringBuilder asciiText = new StringBuilder();
									short[] shortArray = new short[length];
									for (int i = 0; i < shortArray.length; i++) {
										shortArray[i] = (short) response.getRegisterValue(i);
										char char1 = (char) (shortArray[i] >> 8);// get the high byte
										char char2 = (char) (shortArray[i] & 0xff);// get the lower byte
										if (char1 != '\u0000') {
											asciiText.append(char1);
										}
										if (char2 != '\u0000') {
											asciiText.append(char2);
										}
									}
									String result = asciiText.toString();
									// System.out.println(result);//for test purpose can delete later
									outputText.put(key, result);
									alarmText(key, result);
									response = null;
								} catch (ModbusIOException e) {
									connection = null;
								} catch (ModbusSlaveException e) {
									System.out.println(getTime() + "Warning, the address "
											+ textAddr.get(key).get("Address") + " named \"" + key
											+ "\" is not available in the device. Please check your Read Text Address");
									writeLog("Warning, the address " + textAddr.get(key).get("Address")
											+ " named \"" + key
											+ "\" is not available in the device. Please check your Read Text Address");
									notAvail.put(key, textAddr.get(key).get("Address"));
									textAddr.remove(key);
								}
								readText = null;
								permit.release();
							} else {
								for (int coilAddr : writeCoil.keySet()) {
									setCoil(coilAddr, writeCoil.get(coilAddr));
									writeCoil.remove(coilAddr);
								}
							}
						}
					} catch (InterruptedException e) {

					} finally {
						wrAddrrPermit.release();
					}
				}
			} else if (transCode == 7) {
				if (!(floatAddr.isEmpty())) {
					try {
						wrAddrrPermit.acquire();
						for (String key : floatAddr.keySet()) {
							if (writeCoil.isEmpty()) {
								permit.acquire();
								ReadInputRegistersRequest readFloat = new ReadInputRegistersRequest(floatAddr.get(key),
										2);
								trans.setRequest(readFloat);
								try {
									trans.execute();
									ReadInputRegistersResponse response = (ReadInputRegistersResponse) trans
											.getResponse();
									short short1 = (short) response.getRegisterValue(0);
									short short2 = (short) response.getRegisterValue(1);
									int result = ((int) short1 << 16) | (short2 & 0xFFFF);
									float floatValue = Float.intBitsToFloat(result);
									outputFloat.put(key, floatValue);
									alarmFloat(key, floatValue);
									response = null;
								} catch (ModbusIOException e) {
									connection = null;
								} catch (ModbusSlaveException e) {
									System.out.println(getTime() + "Warning, the address " + floatAddr.get(key)
											+ " named \"" + key + "\" is not available in the device : " + ipAddr
											+ " Please check your Analog 32 bits Address");
									writeLog("Warning, the address " + floatAddr.get(key) + " named \""
											+ key + "\" is not available in the device : " + ipAddr
											+ " Please check your Analog 32 bits Address");
									notAvail.put(key, floatAddr.get(key));
									floatAddr.remove(key);
								}
								readFloat = null;
								permit.release();
							} else {
								for (int coilAddr : writeCoil.keySet()) {
									setCoil(coilAddr, writeCoil.get(coilAddr));
									writeCoil.remove(coilAddr);
								}
							}
						}
					} catch (InterruptedException e) {

					} finally {
						wrAddrrPermit.release();
					}
				}
			}
		} catch (ModbusIOException e) {
			System.out.println(getTime() + "Connection Failed : " + deviceName + " " + e.getMessage());
			writeLog("Connection Failed : " + deviceName + " " + e.getMessage());
			connection = null;
		} catch (ModbusException e) {// failed during execution
			writeLog("Failed during execution of the Modbus Transaction : " + e.getMessage());
			System.out.println(getTime() + "Failed during execution of the Modbus Transaction : " + e.getMessage());
		} catch (ConcurrentModificationException e) {// this error occurs when we tried to remove the addresses from
			// HashMap and try using the HashMap to loop at the same time
			// System.out.println(e.getMessage()); // printing this will creating "null" in
			// the Console
		}
	}

	public synchronized void sendMessage(String message) {
		if (!(this.clients == null)) {
			for (WebSocket i : clients.values()) {
				try {
					i.send(message);
				} catch (Exception e) {
					String clientId = i.getRemoteSocketAddress().getAddress().getHostAddress().toString();
					clients.remove(clientId);
				}
			}
		}
	}

	public String getTime() {
		String time;
		LocalDate date = LocalDate.now();
		LocalTime clock = LocalTime.now();
		time = "[" + date + " " + clock.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ";
		return time;
	}

	public void writeLog(String content) {
		AlarmLog alarmLog = new AlarmLog();
		alarmLog.setEvent(content);
		alarmLog.setSiteName(this.deviceName);
		alarmLogRepo.save(alarmLog);
	}

	public void alarmDi(String key, boolean currentValue) {
		if (!oldDi.containsKey(key)) {
			oldDi.put(key, currentValue);
		} else {
			if (oldDi.get(key) != currentValue) {
				oldDi.replace(key, currentValue);
				alarmDiMap.put(key, currentValue);
				try {
					alarmObj.put("Digital", alarmDiMap);
				} catch (JSONException e) {
					System.out.println(
							"Digital value has changed but an error has occured during parsing to Json to send data to the webSocket"
									+ e.getMessage());
				}
				onValueChangeDi(key, currentValue);
			} else {
				alarmDiMap.remove(key);
			}
		}
	}

	public void alarmAi(String key, int currentValue) {
		if (!oldAi.containsKey(key)) {
			oldAi.put(key, currentValue);
		} else {
			if (oldAi.get(key) != currentValue) {
				oldAi.replace(key, currentValue);
				alarmAiMap.put(key, currentValue);
				try {
					alarmObj.put("Analog", alarmAiMap);
				} catch (JSONException e) {
					System.out.println(
							"Analog value has changed but an error has occured during parsing to Json to send data to the webSocket "
									+ e.getMessage());
				}
				onValueChangeAi(key, currentValue);
			} else {
				alarmAiMap.remove(key);
			}
		}
	}

	public void alarmText(String key, String currentText) {
		if (!oldText.containsKey(key)) {
			oldText.put(key, currentText);
		} else {
			if (!(oldText.get(key).equals(currentText))) {
				oldText.replace(key, currentText);
				alarmTextMap.put(key, currentText);
				try {
					alarmObj.put("Text", alarmTextMap);
				} catch (JSONException e) {
					System.out.println(
							"Text Value has changed but an error has occured during parsing to Json to send data to the webSocket"
									+ e.getMessage());
				}
				onValueChangeText(key, currentText);
			} else {
				alarmTextMap.remove(key);
			}
		}
	}

	public void alarmFloat(String key, Float currentValue) {
		if (!oldFloat.containsKey(key)) {
			oldFloat.put(key, round(currentValue, 5));
		} else {
			if (!(Float.compare(oldFloat.get(key), currentValue) == 0)) {
				oldFloat.replace(key, round(currentValue, 5));
				alarmFloatMap.put(key, round(currentValue, 5));
				try {
					alarmObj.put("Float", alarmFloatMap);
				} catch (JSONException e) {
					System.out.println(
							"Float Value has changed but an error has occured during parsing to Json to send data to the webSocket"
									+ e.getMessage());
				}
				onValueChangeFloat(key, round(currentValue, 5));
			} else {
				alarmFloatMap.remove(key);
			}
		}
	}

	public synchronized void setCoil(int address, boolean valueDigital) {
		try {
			permit.acquire();
			WriteCoilRequest writeDo = new WriteCoilRequest(address, valueDigital);
			trans.setRequest(writeDo);
			trans.execute();
			trans.setRetries(0);
			trans.setCheckingValidity(false);
			permit.release();
		} catch (Exception e) {
			System.out.println(getTime() + "Cannot write " + e.getMessage());
			writeLog("Cannot write " + e.getMessage());
			permit.release();
		}
	}

	protected void diAlarmFormat(boolean currentValue, String ifTrue, String ifFalse) {
		if (currentValue) {
			writeLog(ifTrue);
		} else {
			writeLog(ifFalse);
		}
	}

	protected void aiAlarmFormat(String alarm) {
		writeLog(alarm);
	}

	protected void textAlarmFormat(String key, String text) {
		writeLog(key + " changed to : " + text);
	}

	private void putAllAddressAlarm() {
		// ------------------------------put all the
		// addressess----------------------------------------------------
		try {
			wrAddrrPermit.acquire();
			try {
				diAddr = processIntegerNode(deviceData.getJSONObject("diAddr"), "Address");
				aiAddr = processIntegerNode(deviceData.getJSONObject("aiAddr"), "Address");
				ai32Addr = processIntegerNode(deviceData.getJSONObject("ai32Addr"), "Address");
				aiSAddr = processIntegerNode(deviceData.getJSONObject("aiSAddr"), "Address");
				coilAddr = processIntegerNode(deviceData.getJSONObject("coilAddr"), "Address");
				floatAddr = processIntegerNode(deviceData.getJSONObject("floatAddr"), "Address");
				textAddr = processTextAddressNode(deviceData.getJSONObject("textAddr"));

			} catch (JSONException e) {
				System.out.println(getTime() + "Warning : " + e.getMessage()
						+ " If it is intentionally not available, ignore this message");
			}
			// --------------------------------------- put all the alarm
			// Threshold-----------------------------------------
			try {
				// put all alarm for the all digital, analog and text
				digitalAlarmTextIfFalse = processTextNode(deviceData.getJSONObject("diAddr"), "ifFalse");
				digitalAlarmTextIfTrue = processTextNode(deviceData.getJSONObject("diAddr"), "ifTrue");

				// for analog 16 bits, 16 signed and 32 bits value ifMin, ifMax and ifNormal
				analogAlarmTextIfMin = processTextNode(deviceData.getJSONObject("aiAddr"), "ifMin");
				analogAlarmTextIfMax = processTextNode(deviceData.getJSONObject("aiAddr"), "ifMax");
				analogAlarmTextIfNormal = processTextNode(deviceData.getJSONObject("aiAddr"), "ifNormal");

				analogAlarmTextIfMin.putAll(processTextNode(deviceData.getJSONObject("ai32Addr"), "ifMin"));
				analogAlarmTextIfMax.putAll(processTextNode(deviceData.getJSONObject("ai32Addr"), "ifMax"));
				analogAlarmTextIfNormal.putAll(processTextNode(deviceData.getJSONObject("ai32Addr"), "ifNormal"));

				analogAlarmTextIfMin.putAll(processTextNode(deviceData.getJSONObject("aiSAddr"), "ifMin"));
				analogAlarmTextIfMax.putAll(processTextNode(deviceData.getJSONObject("aiSAddr"), "ifMax"));
				analogAlarmTextIfNormal.putAll(processTextNode(deviceData.getJSONObject("aiSAddr"), "ifNormal"));

				// for Float ifMin, ifMax and ifNormal
				floatAlarmTextIfMin = processTextNode(deviceData.getJSONObject("floatAddr"), "ifMin");
				floatAlarmTextIfMax = processTextNode(deviceData.getJSONObject("floatAddr"), "ifMax");
				floatAlarmTextIfNormal = processTextNode(deviceData.getJSONObject("floatAddr"), "ifNormal");

				// put all Threshold for All analog
				analogTrigThresholdMin = processIntegerNode(deviceData.getJSONObject("aiAddr"), "min");
				analogTrigThresholdMax = processIntegerNode(deviceData.getJSONObject("aiAddr"), "max");
				analogTrigThresholdMin.putAll(processIntegerNode(deviceData.getJSONObject("ai32Addr"), "min"));
				analogTrigThresholdMax.putAll(processIntegerNode(deviceData.getJSONObject("ai32Addr"), "max"));
				analogTrigThresholdMin.putAll(processIntegerNode(deviceData.getJSONObject("aiSAddr"), "min"));
				analogTrigThresholdMax.putAll(processIntegerNode(deviceData.getJSONObject("aiSAddr"), "max"));

				// put all Threshold for All float
				floatTrigThresholdMin = processFloatNode(deviceData.getJSONObject("floatAddr"), "min");
				floatTrigThresholdMax = processFloatNode(deviceData.getJSONObject("floatAddr"), "max");

				// set all the allow write of the alarm to "true" for each tag
				analogAllowWrite = processBooleanAllowNode(deviceData.getJSONObject("aiAddr"));
				analogAllowWrite.putAll(processBooleanAllowNode(deviceData.getJSONObject("ai32Addr")));
				analogAllowWrite.putAll(processBooleanAllowNode(deviceData.getJSONObject("aiSAddr")));

				floatAllowWrite = processBooleanAllowNode(deviceData.getJSONObject("floatAddr"));

				this.tagNeedToRecordAnalog = processNeedToRecordAnalog();

			} catch (JSONException e) {
				System.out.println("Error in reading the Alarm Data" + e.getMessage());
			}
		} catch (InterruptedException e) {
			System.out.println("Cannot acquire the permit to put address and alarm into registers");
		} finally {
			wrAddrrPermit.release();
		}
	}

	private static HashMap<String, Boolean> processBooleanAllowNode(JSONObject integerNode) throws JSONException {
		HashMap<String, Boolean> booleanMap = new HashMap<>();
		@SuppressWarnings("unchecked")
		Iterator<String> keys = integerNode.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			booleanMap.put(key, true);
		}
		return booleanMap;
	}

	private static HashMap<String, Integer> processIntegerNode(JSONObject integerNode, String type)
			throws JSONException {
		HashMap<String, Integer> integerMap = new HashMap<>();
		@SuppressWarnings("unchecked")
		Iterator<String> keys = integerNode.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			JSONObject valueNode = integerNode.getJSONObject(key);
			try {
				integerMap.put(key, valueNode.getInt(type));
			} catch (Exception e) {
				integerMap.put(key, null);
			}
		}
		return integerMap;
	}

	private static HashMap<String, Float> processFloatNode(JSONObject floatNode, String type) throws JSONException {
		HashMap<String, Float> floatMap = new HashMap<>();
		@SuppressWarnings("unchecked")
		Iterator<String> keys = floatNode.keys();

		while (keys.hasNext()) {
			String key = keys.next();
			JSONObject valueNode = floatNode.getJSONObject(key);
			try {
				float floatValue = (float) valueNode.getDouble(type);
				// Store the key and the "float" value in the map
				floatMap.put(key, floatValue);
			} catch (Exception e) {
				floatMap.put(key, null);
			}

		}
		return floatMap;
	}

	private static HashMap<String, String> processTextNode(JSONObject textNode, String type) throws JSONException {
		HashMap<String, String> stringMap = new HashMap<>();
		@SuppressWarnings("unchecked")
		Iterator<String> keys = textNode.keys();

		while (keys.hasNext()) {
			String key = keys.next();
			JSONObject valueNode = textNode.getJSONObject(key);
			try {
				// Store the key and the "ifFalse" value in the map
				String text = valueNode.getString(type);
				stringMap.put(key, text);
			} catch (Exception e) {
				stringMap.put(key, null);
			}
		}
		return stringMap;
	}

	private static HashMap<String, HashMap<String, Integer>> processTextAddressNode(JSONObject textAddrNode)
			throws JSONException {
		HashMap<String, HashMap<String, Integer>> textAddrMap = new HashMap<>();
		@SuppressWarnings("unchecked")
		Iterator<String> keys = textAddrNode.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			JSONObject valueNode = textAddrNode.getJSONObject(key);
			HashMap<String, Integer> valueMap = new HashMap<>();
			valueMap.put("Address", valueNode.getInt("Address"));
			valueMap.put("Length", valueNode.getInt("Length"));
			textAddrMap.put(key, valueMap);
		}
		return textAddrMap;
	}

	private ArrayList<String> processNeedToRecordAnalog() throws JSONException {
		ArrayList<String> dataNeedToScheduled = new ArrayList<>();
		String tagVariables[] = { "aiAddr", "ai32Addr", "aiSAddr" };

		for (String tagVariable : tagVariables) {
			JSONObject tagType = this.deviceData.getJSONObject(tagVariable);
			@SuppressWarnings("unchecked")
			Iterator<String> keys = tagType.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				JSONObject valueNode = tagType.getJSONObject(key);
				try {
					boolean needToPlot = valueNode.getBoolean("scheduled");
					if (needToPlot) {
						dataNeedToScheduled.add(key);
					}
				} catch (Exception e) {
					// this is where the code fall if the "scheduled" is not available for the tag
				}
			}
		}
		System.out.println(dataNeedToScheduled);
		return dataNeedToScheduled;
	}

	@SuppressWarnings("deprecation")
	public static float round(float value, int decimalPlaces) {
		if (decimalPlaces < 0)
			throw new IllegalArgumentException("Decimal places must be non-negative");

		BigDecimal bigDecimal = new BigDecimal(Float.toString(value));
		bigDecimal = bigDecimal.setScale(decimalPlaces, BigDecimal.ROUND_HALF_UP);
		return bigDecimal.floatValue();
	}

	public abstract void onValueChangeDi(String key, boolean currentValue);

	public abstract void onValueChangeAi(String key, Integer currentValue);

	public abstract void onValueChangeText(String key, String currentValue);

	public abstract void onValueChangeFloat(String key, Float currentValue);

	public abstract void onTick();
}
