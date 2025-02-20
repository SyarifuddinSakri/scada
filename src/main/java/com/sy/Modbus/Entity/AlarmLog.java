package com.sy.Modbus.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
public class AlarmLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String siteName;

	@Column
	private String event;

	@Column(updatable = false)
	private LocalDateTime logDate;

	public LocalDateTime getLogDate() {
		return logDate;
	}

	@PrePersist
	protected void onCreate() {
		this.logDate = LocalDateTime.now();
	}

	public void setLogDate(LocalDateTime logDate) {
		this.logDate = logDate;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public String getSiteName() {
		return siteName;
	}

	public void setSiteName(String siteName) {
		this.siteName = siteName;
	}

}
