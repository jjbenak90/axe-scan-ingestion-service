package com.deque.monitor.axe_scan_ingestion_service;

import org.springframework.boot.SpringApplication;

public class TestAxeScanIngestionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AxeScanIngestionServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
