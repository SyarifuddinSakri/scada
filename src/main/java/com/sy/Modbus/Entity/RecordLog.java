package com.sy.Modbus.Entity;

import java.sql.Date;

import jakarta.persistence.*;

@Entity
public class RecordLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private Date createdDate;

	@Column
	private String data;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Date getCreateDate() {
		return createdDate;
	}

	public void setCreateDate(Date createDate) {
		this.createdDate = createDate;
	}

}
