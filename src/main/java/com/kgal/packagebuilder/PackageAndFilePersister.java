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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.kgal.packagebuilder.PersistResult.Status;
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
public class PackageAndFilePersister implements Callable<PersistResult> {

    private final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final HashMap<String, ArrayList<InventoryItem>> theMap;
    private final String                                    filename;
    private final int                                       packageNumber;
    private final boolean                                   includeChangeData;
    private final boolean                                   downloadData;
    private final boolean                                   unzipDownload;
    private final double                                    myApiVersion;
    private final String                                    targetDir;
    private final String                                    metaSourceDownloadDir;
    private final MetadataConnection                        metadataConnection;
    private final PersistResult                             result;

    private OrgRetrieve myRetrieve = null;
    private boolean     localOny   = false;

    public PackageAndFilePersister(final double myApiVersion,
            final String targetDir,
            final String metaSourceDownloadDir,
            final HashMap<String, ArrayList<InventoryItem>> theMap,
            final String filename,
            final int packageNumber, final boolean includeChangeData, final boolean download,
            final boolean unzip,
            final MetadataConnection metadataConnection) {
        this.myApiVersion = myApiVersion;
        this.targetDir = targetDir;
        this.metaSourceDownloadDir = metaSourceDownloadDir;
        this.theMap = theMap;
        this.filename = filename;
        this.packageNumber = packageNumber;
        this.includeChangeData = includeChangeData;
        this.downloadData = download;
        this.unzipDownload = unzip;
        this.metadataConnection = metadataConnection;
        this.result = new PersistResult(filename);
    }

    /**
     * Switch the persister to local only operation mainly used when you have
     * both a local ZIP and XML
     */
    public void setLocalOnly() {
        this.localOny = true;
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public PersistResult call() throws Exception {
        boolean itworked = true;
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
            this.logger.log(Level.INFO, "Writing " + new File(this.targetDir + this.filename).getCanonicalPath());

        } catch (final Exception e) {
            this.result.setStatus(PersistResult.Status.FAILURE);
            itworked = false;
            e.printStackTrace();
        } finally {
            if (itworked && this.downloadData) {
                this.downloadAndUnzip(this.localOny, this.unzipDownload);
            } else {
                this.result.setStatus((itworked) ? PersistResult.Status.SUCCESS : PersistResult.Status.FAILURE);

            }
            this.result.setDone();
        }
        return this.result;
    }

    /**
     * 
     * @param doNotDownLoad
     *            = Skip the download step - to repeat the unpackage and unzip
     *            activity mainly for testing
     * @throws Exception
     */
    private void downloadAndUnzip(final boolean doNotDownLoad, final boolean unzip) throws Exception {
        final String zipFileName = this.filename.replace("xml", "zip");
        if (doNotDownLoad) {
            this.logger.log(Level.INFO, "Working with local packages, no actual download");
        } else {
            this.logger.log(Level.INFO,
                    "Asked to retrieve this package " + this.filename + "from org - will do so now.");
            myRetrieve = new OrgRetrieve(Level.FINE);
            myRetrieve.setMetadataConnection(this.metadataConnection);
            myRetrieve.setZipFile(zipFileName);
            myRetrieve.setManifestFile(this.targetDir + this.filename);
            myRetrieve.setApiVersion(this.myApiVersion);
            myRetrieve.setPackageNumber(this.packageNumber);
            myRetrieve.retrieveZip();
        }

        final File zipResult = new File(zipFileName);

        if (zipResult.exists()) {
            if (unzip) {
                final Map<String, Calendar> fileDates = new HashMap<>();
                this.theMap.entrySet().forEach((entry) -> {
                    try {
                        final String curKey = String.valueOf(Utils.getDirForMetadataType(entry.getKey()));
                        entry.getValue().forEach(item -> {
                            fileDates.put(curKey + "/" + item.itemName.toLowerCase(), item.getLastModifiedDate());
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                final ZipAndFileFixer zff = new ZipAndFileFixer(zipResult, fileDates);
                zff.extractAndAdjust(this.metaSourceDownloadDir);
            }
            this.result.setStatus(Status.SUCCESS);
        } else {
            this.logger.log(Level.INFO, "Cancel requested or download ZIP file doesn't exist:" + zipFileName);
            this.result.setStatus(Status.FAILURE);
        }
    }

}
