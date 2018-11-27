package com.kgal.packagebuilder.inventory;

import java.util.Comparator;

public class InventoryItemComparator implements Comparator<InventoryItem> {

	@Override
	public int compare(InventoryItem o1, InventoryItem o2) {
		// TODO Auto-generated method stub
		return o1.itemName.compareTo(o2.itemName);
	}

}