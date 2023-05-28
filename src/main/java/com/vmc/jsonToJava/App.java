package com.vmc.jsonToJava;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
	public static void main(String[] args) throws StreamReadException, DatabindException, IOException {
		System.out.println("Hello World!");
		String localDir = System.getProperty("user.dir");
		System.out.println(localDir);
		ObjectMapper om = new ObjectMapper();

		Employee emp=om.readValue(new File(localDir+"\\Sample.json"), Employee.class);
		System.out.println(emp);
	}
}
