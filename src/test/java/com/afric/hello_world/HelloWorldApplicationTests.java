package com.afric.hello_world;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HelloWorldApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void helloEndpointShouldReturnCorrectMessage() throws Exception {
		mockMvc.perform(get("/api/hello")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Hello from @fric Payment Solutions!"))
				.andExpect(jsonPath("$.version").exists())
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void healthEndpointShouldReturnUp() throws Exception {
		mockMvc.perform(get("/api/health")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.service").value("afric-hello-world"));
	}

	@Test
	void actuatorHealthShouldBeAccessible() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}
}
