/** ========================================================================= *
 * Copyright (C)  2017, 2019 Salesforce Inc ( http://www.salesforce.com/      *
 *                            All rights reserved.                            *
 *                                                                            *
 *  @author     Stephan H. Wissel (stw) <swissel@salesforce.com>              *
 *                                       @notessensei                         *
 * @version     1.0                                                           *
 * ========================================================================== *
 *                                                                            *
 * Licensed under the  Apache License, Version 2.0  (the "License").  You may *
 * not use this file except in compliance with the License.  You may obtain a *
 * copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.       *
 *                                                                            *
 * Unless  required  by applicable  law or  agreed  to  in writing,  software *
 * distributed under the License is distributed on an  "AS IS" BASIS, WITHOUT *
 * WARRANTIES OR  CONDITIONS OF ANY KIND, either express or implied.  See the *
 * License for the  specific language  governing permissions  and limitations *
 * under the License.                                                         *
 *                                                                            *
 * ========================================================================== *
 */
package com.kgal.packagebuilder;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.kgal.packagebuilder.inventory.InventoryItem;

/**
 * Extracts a given zip file to a target and adjusts the file date to value
 * given in a map
 *
 * @author swissel
 *
 */
public class ZipAndFileFixer {

	private final Logger logger;

	public ZipAndFileFixer(HashMap<String, InventoryItem> totalInventory, Logger l) {
		this.totalInventory = totalInventory;
		logger = l;
	}

	HashMap<String, InventoryItem> totalInventory;

	public void adjustFileDates(final String targetDirName) throws IOException {
		final File targetDir = new File((targetDirName == null) ? "." : targetDirName);
		if (!targetDir.exists() || !targetDir.isDirectory()) {
			throw new IOException("Target dir doesn't exist or is not a directory:" + targetDirName);
		}

		Collection<File> myFiles = FileUtils.listFiles(targetDir, null, true);
		Iterator<File> i = myFiles.iterator(); 
		while (i.hasNext()) {
			File f = i.next();
			if (f.getName().toLowerCase().equals("package.xml")) {
				continue;
			}

			InventoryItem item = PackageBuilder.getInventoryItemForFile(totalInventory, f, targetDirName);

			fixFileDate(f, item);
		}


	}

	/*
	 * This method will take a file and its base folder and match it to the keyed inventory so we know which
	 * InventoryItem it corresponds to
	 * 
	 * e.g. CustomObject is easy	: file objects/MyObject__c.object will need to be matched to a key objects/MyObject__c
	 * 
	 * classes are a little harder	: file classes/MyClass.cls will match classes/MyClass, but also
	 * 								: file classes/MyClass.cls-meta.xml will also have to match classes/MyClass
	 * 
	 * components get hairy			: file aura/MyComp/MyComp.cmp will have to match aura/MyComp
	 * 								: file aura/MyComp/MyComp.cmp-meta.xml will have to match aura/MyComp
	 * 								: file lwc/MyComp/MyComp.js will have to match lwc/MyComp
	 * 								: file lwc/MyComp/MyComp.js-meta.xml will have to match lwc/MyComp
	 * 								: file lwc/MyComp/MyCompHelper.js-meta.xml will have to match lwc/MyComp
	 * 
	 * reports, etc. also special	: file reports/myfolder/MyReport.report must match reports/myfolder/MyReport
	 * 								: file reports/myfolder/myfolder-meta.xml must match reports/myfolder
	 * 
	 * and of course emailtempl.    : file email/myfolder/MyTemplate.email must match email/myfolder/MyTemplate
	 * 								: file email/myfolder/MyTemplate.email must match email/myfolder/MyTemplate
	 */

	private void fixFileDate(final File file, final InventoryItem item) throws IOException {
		if (file.exists() && item != null) {
			if (item.getLastModifiedDate() != null ) {
				try {
					final Calendar newDate = item.getLastModifiedDate();
					long theTime = newDate.getTimeInMillis();
					if (!file.setLastModified(theTime)) {
						logger.log(Level.INFO,"Couldn't update file date of " + file.getCanonicalPath());
					} else {
						logger.log(Level.FINE,"Updated file date of " + file.getCanonicalPath());
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			} else {
				logger.log(Level.FINE,"No LastModifiedDate for item " + file.getCanonicalPath());
			}
		} else {
			logger.log(Level.WARNING,"Couldn't find file " + file.getCanonicalPath());
		}
	}
}