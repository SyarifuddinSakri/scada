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

	@Column
	private String tagName;

	@Column
	private String data;

	@Column
	private String siteName;

	public String getSiteName() {
		return siteName;
	}

	public void setSiteName(String siteName) {
		this.siteName = siteName;
	}

	@PrePersist
	protected void onCreate() {
		this.createdDate = LocalDateTime.now();
	}

	public LocalDateTime getCreatedDate() {
		return createdDate;
	}

	public String getTagName() {
		return tagName;
	}

	public void setTagName(String tagName) {
		this.tagName = tagName;
	}

	public void setCreatedDate(LocalDateTime createdDate) {
		this.createdDate = createdDate;
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
