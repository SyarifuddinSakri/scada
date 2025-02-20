package com.sy.Modbus.Entity;


import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
public class RecordLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(updatable = false) // Prevent updates to Genereated Date
	private LocalDateTime createdDate;

	public LocalDateTime getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(LocalDateTime createdDate) {
		this.createdDate = createdDate;
	}

	@Column
	private String tagName;

	@Column
	private String data;

	public String getTagName() {
		return tagName;
	}

	public void setTagName(String tagName) {
		this.tagName = tagName;
	}

	@PrePersist
	protected void onCreate(){
		this.createdDate = LocalDateTime.now();
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

}
