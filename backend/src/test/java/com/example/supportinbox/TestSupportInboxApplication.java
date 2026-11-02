package com.example.supportinbox;

import org.springframework.boot.SpringApplication;

public class TestSupportInboxApplication {

	public static void main(String[] args) {
		SpringApplication.from(SupportInboxApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
