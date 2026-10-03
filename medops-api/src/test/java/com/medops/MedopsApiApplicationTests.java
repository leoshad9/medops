package com.medops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// The database comes from sql-maven-plugin, which creates and drops
// medops_test around the build; the `test` profile points at it.
@SpringBootTest
@ActiveProfiles("test")
class MedopsApiApplicationTests {

	@Test
	void contextLoads() {
		// Verifies the Spring application context loads without errors
	}

}
