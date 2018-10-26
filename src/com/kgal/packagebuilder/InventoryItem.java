package com.kgal.packagebuilder;

import java.util.Calendar;

import com.sforce.soap.metadata.FileProperties;

public class InventoryItem {
	String itemName;
	FileProperties fp;
	boolean isFolder;
	int itemVersion;
	boolean isNew;
	boolean isUpdated;
	
	
	
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

	public String getId() {
		return fp.getId();
	}
	
	public String getCreatedById() {
		return fp.getCreatedById();
	}
	
	public String getCreatedByName() {
		return fp.getCreatedByName();
	}
	
	public String getFileName() {
		return fp.getFileName();
	}
	
	public String getFullName() {
		return fp.getFullName();
	}
	
	public String getLastModifiedById() {
		return fp.getLastModifiedById();
	}
	
	public String getLastModifiedByName() {
		return fp.getLastModifiedByName();
	}
	
	public Calendar getLastModifiedDate() {
		return fp.getLastModifiedDate();
	}
	
	public Calendar getCreatedDate() {
		return fp.getCreatedDate();
	}
	
	public String getType() {
		return fp.getType();
	}
	
	public String toCSV() {
		return  getType() + "," +
				itemName + "," +
				itemVersion + "," +
				getLastModifiedDate() + "," +
				getLastModifiedByName() + "," +
				getCreatedDate() + "," +
				getCreatedByName() + "," +
				fp.getId() + ",";
				
	}
}
