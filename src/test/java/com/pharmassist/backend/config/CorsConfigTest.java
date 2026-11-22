package com.pharmassist.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmassist.backend.controller.PingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
@Import(CorsConfig.class)
class CorsConfigTest {

	private static final String EXPO_WEB_ORIGIN = "http://localhost:8081";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void preflightFromAllowedOriginIsAccepted() throws Exception {
		mockMvc.perform(options("/api/ping")
						.header(HttpHeaders.ORIGIN, EXPO_WEB_ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXPO_WEB_ORIGIN));
	}

	@Test
	void simpleRequestFromAllowedOriginGetsCorsHeader() throws Exception {
		mockMvc.perform(get("/api/ping").header(HttpHeaders.ORIGIN, EXPO_WEB_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXPO_WEB_ORIGIN));
	}

	@Test
	void preflightFromUnknownOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/ping")
						.header(HttpHeaders.ORIGIN, "http://evil.example.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void requestFromUnknownOriginIsRejected() throws Exception {
		mockMvc.perform(get("/api/ping").header(HttpHeaders.ORIGIN, "http://evil.example.com"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}
}
