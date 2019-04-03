package com.kgal.packagebuilder.inventory;

import java.io.File;

public class GitInventoryItem {
	public InventoryItem inventoryItem;
	public File file;
	
	public GitInventoryItem (InventoryItem i, File f) {
		inventoryItem = i;
		file = f;
	}
}
