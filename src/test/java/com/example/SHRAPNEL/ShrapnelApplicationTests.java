package com.example.SHRAPNEL;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ShrapnelApplicationTests {

	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private com.example.SHRAPNEL.service.BlockchainFingerprintService fingerprintService;

	@Test
	void contextLoads() {
	}

}
