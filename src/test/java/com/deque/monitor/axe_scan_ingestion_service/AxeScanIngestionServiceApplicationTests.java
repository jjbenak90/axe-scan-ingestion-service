package com.deque.monitor.axe_scan_ingestion_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AxeScanIngestionServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}