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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.kgal.packagebuilder.PackageBuilder.Loglevel;
import com.kgal.packagebuilder.inventory.InventoryItem;
import com.kgal.packagebuilder.output.SimpleXMLDoc;
import com.salesforce.migrationtoolutils.Utils;
import com.sforce.soap.metadata.MetadataConnection;

/**
 * Download packageXML and eventually files in a background thread
 *
 * @author swissel
 *
 */
// TODO: Change to runnableFuture
public class PackageAndFilePersister implements Runnable {

    private final HashMap<String, ArrayList<InventoryItem>> theMap;
    private final String                                    filename;
    private final int                                       packageNumber;
    private final boolean                                   includeChangeData;
    private final boolean                                   downloadData;
    private final double                                    myApiVersion;
    private final String                                    targetDir;
    private final MetadataConnection                        metadataConnection;
    private final Loglevel                                  loglevel = Loglevel.BRIEF;
    private final CountDownLatch latch;

    public PackageAndFilePersister(final CountDownLatch latch, final double myApiVersion, final String targetDir,
            final HashMap<String, ArrayList<InventoryItem>> theMap,
            final String filename,
            final int packageNumber, final boolean includeChangeData, final boolean download,
            final MetadataConnection metadataConnection) {
        this.latch = latch;
        this.myApiVersion = myApiVersion;
        this.targetDir = targetDir;
        this.theMap = theMap;
        this.filename = filename;
        this.packageNumber = packageNumber;
        this.includeChangeData = includeChangeData;
        this.downloadData = download;
        this.metadataConnection = metadataConnection;
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {

        try {
            final SimpleDateFormat format1 = new SimpleDateFormat(PackageBuilder.DEFAULT_DATE_FORMAT);
            final SimpleXMLDoc packageXML = new SimpleXMLDoc();
            packageXML.openTag("Package", "xmlns", "http://soap.sforce.com/2006/04/metadata");

            final ArrayList<String> mdTypes = new ArrayList<>(this.theMap.keySet());
            Collections.sort(mdTypes);

            for (final String mdType : mdTypes) {
                packageXML.openTag("types");
                packageXML.addTag("name", mdType);

                for (final InventoryItem item : this.theMap.get(mdType)) {

                    Map<String, String> attributes = null;
                    if (this.includeChangeData) {
                        attributes = new HashMap<>();
                        attributes.put("lastmodifiedby", item.getLastModifiedByName());
                        attributes.put("lastmodified", format1
                                .format(item.getLastModifiedDate() == null ? 0 : item.getLastModifiedDate().getTime()));
                        attributes.put("lastmodifiedemail", item.lastModifiedByEmail);
                    }

                    packageXML.addTag("members", item.itemName, attributes);
                }
                packageXML.closeTag(1);
            }
            packageXML.addTag("version", String.valueOf(this.myApiVersion));
            packageXML.closeDocument();

            Utils.writeFile(this.targetDir + this.filename, packageXML.toString());
            this.log("Writing " + new File(this.targetDir + this.filename).getCanonicalPath(), Loglevel.BRIEF);

            if (this.downloadData) {
                this.log("Asked to retrieve this package from org - will do so now.", Loglevel.BRIEF);
                final OrgRetrieve myRetrieve = new OrgRetrieve(OrgRetrieve.Loglevel.VERBOSE);
                myRetrieve.setMetadataConnection(this.metadataConnection);
                myRetrieve.setZipFile(this.filename.replace("xml", "zip"));
                myRetrieve.setManifestFile(this.targetDir + this.filename);
                myRetrieve.setApiVersion(this.myApiVersion);
                myRetrieve.setPackageNumber(this.packageNumber);
                myRetrieve.retrieveZip();
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        // Tell the threadpool guys they are done
        latch.countDown();
    }

    private void log(final String logText, final Loglevel level) {
        if ((this.loglevel == null) || (level.getLevel() <= this.loglevel.getLevel())) {
            System.out.println(logText);
        }
    }

}
