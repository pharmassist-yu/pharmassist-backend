package com.pharmassist.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple connectivity check for clients: GET /api/ping returns {"status":"ok"}.
 */
@RestController
@RequestMapping("/api")
public class PingController {

	@GetMapping("/ping")
	public PingResponse ping() {
		return new PingResponse("ok");
	}

	public record PingResponse(String status) {
	}
}
