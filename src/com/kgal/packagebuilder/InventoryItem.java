package com.kgal.packagebuilder;

import com.sforce.soap.metadata.FileProperties;

public class InventoryItem {
	String itemName;
	FileProperties fp;
	boolean isFolder;
	
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
}
