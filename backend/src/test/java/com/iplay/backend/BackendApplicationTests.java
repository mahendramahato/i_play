package com.iplay.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole application context starts and every bean wires up. Runs on the
 * "test" profile so it needs no local MySQL or Redis — without it this passes
 * only on a machine that happens to have both running.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
