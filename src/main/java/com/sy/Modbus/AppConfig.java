
package com.sy.Modbus;

import java.io.File;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	@Bean
	public File configFile() {
		// Provide the path to your configuration file
		return new File("modbus_server_config.json");
	}
}
