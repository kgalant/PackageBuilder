package com.kgal.packagebuilder.inventory;

import java.util.ArrayList;
import java.util.HashMap;

public class InventoryDatabase {
	String orgId;
	private HashMap<String, ArrayList<InventoryItem>> theDatabase = new HashMap<String, ArrayList<InventoryItem>>();
	private HashMap<String, ArrayList<InventoryItem>> updatedItems = new HashMap<String, ArrayList<InventoryItem>>();

	// checks the database and adds the item if it's newer than the latest one we had or if we didn't have it at all
	// returns true if the item was added or updated, false if not

	public InventoryDatabase (String orgId) {
		this.orgId = orgId;
	}

	public HashMap<String, ArrayList<InventoryItem>> getDatabase() {
		return theDatabase;
	}
	
	public HashMap<String, ArrayList<InventoryItem>> getUpdatedItemsDatabase() {
		return updatedItems;
	}
	
	private InventoryDatabase () {}
	
	

//	adds an item to a metadata type and returns true if it were new or updated, else false
	
	public boolean addIfNewOrUpdated(String metadataType, InventoryItem item) {
		boolean retval = false;

		InventoryItem latest = itemExistsAlready(item);

		if (latest == null) {
			addItem(theDatabase, metadataType, item);
			item.isNew = true;
			item.itemVersion = 1;
			addItem(updatedItems, metadataType, item);
			retval = true;
		} else {
			if (!latest.getLastModifiedDate().equals(item.getLastModifiedDate())) {
				// the latest one we have is older than the current inventory, add the one in inventory
				addItem(theDatabase, metadataType, item);
				item.isUpdated = true;
				item.itemVersion = latest.itemVersion + 1;
				addItem(updatedItems, metadataType, item);
				retval = true;
			}
		}
		return retval;
	}

	private InventoryItem getLatestItem(InventoryItem item) {
		// TODO Auto-generated method stub
		return null;
	}

	//	return the item if an item of that type & name & lastmodifieddate exists in theDatabase, else 
	//	if an item of that type & name exists (but last modified date differs), returns the latest from the db
	//	else returns null	

	private InventoryItem itemExistsAlready(InventoryItem item) {
		InventoryItem foundItem = null;

		// first, check if we even have the type

		ArrayList<InventoryItem> typeDb = theDatabase.get(item.getType());
		if (typeDb != null) {
			//			have seen that type before, so we keep looking
			for (InventoryItem i : typeDb) {
				// check if the name matches

				if (item.itemName == i.itemName) {
					foundItem = i;
					if (foundItem.getLastModifiedDate().equals(item.getLastModifiedDate())) {
						// we have an exact match, stop looking
						break;
					}
				}
			}
			// if we're here that means we haven't found an exact match, so will return the latest one matching name
			// or null if we didn't even find a name match
		}

		return foundItem;
	}

	public static void saveDatabaseToFile(String location, InventoryDatabase db) {

	}

	public static InventoryDatabase readDatabaseFromFile(String location) {
		InventoryDatabase db = new InventoryDatabase();
		return db;
	}

	private void addItem (HashMap<String, ArrayList<InventoryItem>> db, String metadataType, InventoryItem item) {
		ArrayList<InventoryItem> typeDb = db.get(metadataType);
		if (typeDb == null) {
			typeDb = new ArrayList<InventoryItem>();
			theDatabase.put(metadataType, typeDb);
		}
		typeDb.add(item);
	}

}
