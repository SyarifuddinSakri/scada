package com.sy.Modbus;

import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
public class ModbusApplication {

	public static void main(String[] args) throws Exception {
		ApplicationContext context = SpringApplication.run(ModbusApplication.class, args);

		Server server = context.getBean(Server.class);
		server.init();

	}
}
