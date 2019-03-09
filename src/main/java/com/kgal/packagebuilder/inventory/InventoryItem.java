package com.kgal.packagebuilder.inventory;

import java.util.Calendar;

import com.sforce.soap.metadata.FileProperties;

public class InventoryItem {
	public String itemName;
	public String itemExtendedName;
	public FileProperties fp;
	public boolean isFolder;
	public int itemVersion;
	public boolean isNew;
	public boolean isUpdated;
	public String lastModifiedByEmail;
	public String lastModifiedByUsername;
	public String localFileName;
	
	public InventoryItem(String i, FileProperties f, boolean isF) {
		this.itemName = i;
		this.fp = f;
		this.isFolder = isF;
	}
	
	public InventoryItem(String i, FileProperties f) {
		this.itemName = i;
		this.fp = f;
		this.isFolder = false;
	}
	
	public String getExtendedName() {
		if (fp == null) return itemName;
		else return fp.getType() + ":" + fp.getFullName();
	}

	public String getId() {
		return fp == null ? null : fp.getId();
	}
	
	public String getCreatedById() {
		return fp == null ? null : fp.getCreatedById();
	}
	
	public String getCreatedByName() {
		return fp == null ? null : fp.getCreatedByName();
	}
	
	public String getFileName() {
		return fp == null ? null : fp.getFileName();
	}
	
	public String getFullName() {
		return fp == null ? null : fp.getFullName();
	}
	
	public String getLastModifiedById() {
		return fp == null ? null : fp.getLastModifiedById();
	}
	
	public String getLastModifiedByName() {
		return fp == null ? null : fp.getLastModifiedByName();
	}
	
	public Calendar getLastModifiedDate() {
		return fp == null ? null : fp.getLastModifiedDate();
	}
	
	public Calendar getCreatedDate() {
		return fp == null ? null : fp.getCreatedDate();
	}
	
	public String getType() {
		return fp == null ? null : fp.getType();
	}
	
	public String toCSV() {
		return  getType() + "," +
				itemName + "," +
				itemVersion + "," +
				getLastModifiedDate() + "," +
				getLastModifiedByName() + "," +
				getCreatedDate() + "," +
				getCreatedByName() + "," +
				fp == null ? null : fp.getId() + ",";
				
	}
}
