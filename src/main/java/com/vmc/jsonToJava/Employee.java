package com.vmc.jsonToJava;

import java.util.List;

public class Employee {
	private String name;
	private String city;
	List<String> cars;
	private String job;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public List<String> getCars() {
		return cars;
	}

	public void setCars(List<String> cars) {
		this.cars = cars;
	}

	public String getJob() {
		return job;
	}

	public void setJob(String job) {
		this.job = job;
	}

	@Override
	public String toString() {
		return "Employee [name=" + name + ", city=" + city + ", cars=" + cars + ", job=" + job + "]";
	}
	
	

}