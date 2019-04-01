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
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.kgal.packagebuilder.PersistResult.Status;
import com.kgal.packagebuilder.inventory.InventoryItem;
import com.kgal.migrationtoolutils.Utils;
import com.sforce.soap.metadata.MetadataConnection;

/**
 * Download packageXML and eventually files in a background thread
 *
 * @author swissel
 *
 */
public class PackageAndFilePersister implements Callable<PersistResult> {

	private final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private final Map<String, ArrayList<InventoryItem>> theMap;
	private final String                                    filename;
	private final boolean                                   includeChangeData;
	private final boolean                                   downloadData;
	private final boolean                                   unzipDownload;
	private final double                                    myApiVersion;
	private final String                                    targetDir;
	private final String                                    metaSourceDownloadDir;
	private final String                                    destinationDir;
	private final MetadataConnection                        metadataConnection;
	private final PersistResult                             result;
	private final boolean 									gitCommit;

	private String											zipFileName;
	private File 											zipResult;

	private OrgRetrieve myRetrieve = null;
	private boolean     localOnly   = false;



	public PackageAndFilePersister(final double myApiVersion,
			final String targetDir,
			final String metaSourceDownloadDir,
			final String destinationDir,
			final Map<String, ArrayList<InventoryItem>> theMap,
			final String filename,
			final boolean includeChangeData, final boolean download,
			final boolean unzip,
			final MetadataConnection metadataConnection,
			final boolean gitCommit) {
		this.myApiVersion = myApiVersion;
		this.targetDir = targetDir;
		this.metaSourceDownloadDir = metaSourceDownloadDir;
		this.destinationDir = destinationDir;
		this.theMap = theMap;
		this.filename = filename;
		this.includeChangeData = includeChangeData;
		this.downloadData = download;
		this.unzipDownload = unzip;
		this.metadataConnection = metadataConnection;
		this.result = new PersistResult(filename);
		this.gitCommit = gitCommit;
	}

	/**
	 * Switch the persister to local only operation mainly used when you have
	 * both a local ZIP and XML
	 */
	public void setLocalOnly() {
		this.localOnly = true;
	}

	/**
	 * @see java.lang.Callable#call()
	 */
	@Override
	public PersistResult call() throws Exception {
		boolean itworked = true;
		try {
			writePackageXML();
		} catch (Exception e) {
			this.result.setStatus(PersistResult.Status.FAILURE);
			itworked = false;
			e.printStackTrace();
		}

		if (itworked && this.downloadData && !this.localOnly) {
			try {
				this.downloadPackage();
			} catch (Exception e) {
				this.result.setStatus(PersistResult.Status.FAILURE);
				itworked = false;
				e.printStackTrace();
			}
		}

		if (itworked && this.unzipDownload) {
			try {
				this.unzipPackage();
			} catch (Exception e) {
				this.result.setStatus(PersistResult.Status.FAILURE);
				itworked = false;
				e.printStackTrace();
			}
		}

		if (itworked && this.gitCommit) {
			try {
				this.prepareForGit();
			} catch (Exception e) {
				this.result.setStatus(PersistResult.Status.FAILURE);
				itworked = false;
				e.printStackTrace();
			}
		}


		if (itworked) {
			this.result.setStatus(Status.SUCCESS);
		} else {
			this.logger.log(Level.INFO, "Cancel requested or download ZIP file doesn't exist:" + zipFileName);
			this.result.setStatus(Status.FAILURE);
		}

		return this.result;
	}

	private void prepareForGit() throws IOException {
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

	private void unzipPackage() throws Exception {
		final String zipFileNameWithPath = this.destinationDir + File.separator + zipFileName;
		zipResult = new File(zipFileNameWithPath);

		if (zipResult.exists()) {
			Utils.unzip(zipFileName, this.metaSourceDownloadDir);
		} else {
			throw new Exception("Asked to unzip " + zipFileNameWithPath + " but file not found, something is wrong.");
		}		
	}

	private void downloadPackage() throws Exception {
		zipFileName = this.filename.replace("xml", "zip");

		this.logger.log(Level.INFO,
				"Asked to retrieve this package " + this.filename + "from org - will do so now.");
		myRetrieve = new OrgRetrieve(Level.FINE);
		myRetrieve.setMetadataConnection(this.metadataConnection);
		Utils.checkDir(this.destinationDir);
		myRetrieve.setZipFile(this.destinationDir + File.separator + zipFileName);
		myRetrieve.setManifestFile(this.targetDir + this.filename);
		myRetrieve.setApiVersion(this.myApiVersion);
		myRetrieve.retrieveZip();


	}

	private void writePackageXML() throws ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException, IOException {
		final SimpleDateFormat format1 = new SimpleDateFormat(PackageBuilder.DEFAULT_DATE_FORMAT);

		DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();

		DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();

		Document document = documentBuilder.newDocument();

		Element root = document.createElement("Package");
		root.setAttribute("xmlns","http://soap.sforce.com/2006/04/metadata");
		document.appendChild(root);

		final ArrayList<String> mdTypes = new ArrayList<>(theMap.keySet());
		Collections.sort(mdTypes);

		// get list of types for comment line
		ArrayList<String> typesInPackage = new ArrayList<String>();
		int count = 0;
		for (final String mdType : mdTypes) {
			if (theMap.get(mdType).size() == 0) {
				continue;
			} else{
				typesInPackage.add(mdType + "(" + theMap.get(mdType).size() + ")");
				count += theMap.get(mdType).size();
			}
		}

		String[] typesArray = new String[typesInPackage.size()];

		typesArray = typesInPackage.toArray(typesArray);

		Comment comment = document.createComment("Types packaged: " + String.join(", ", typesArray));
		root.appendChild(comment);
		Comment comment2 = document.createComment("Items packaged total: " + count);
		root.appendChild(comment2);

		Element version = document.createElement("version");
		version.setTextContent(String.valueOf(this.myApiVersion));
		root.appendChild(version);

		for (final String mdType : mdTypes) {
			if (theMap.get(mdType).size() == 0) {
				continue;
			}

			Element types = document.createElement("types");
			root.appendChild(types);
			Element name = document.createElement("name");
			name.setTextContent(mdType);
			types.appendChild(name);

			for (final InventoryItem item : theMap.get(mdType)) {

				Element member = document.createElement("members");
				member.setTextContent(item.itemName);

				if (this.includeChangeData) {
					member.setAttribute("lastmodifiedby", item.getLastModifiedByName());
					member.setAttribute("lastmodified", format1.format(item.getLastModifiedDate() == null ? 0 : item.getLastModifiedDate().getTime()));
					member.setAttribute("lastmodifiedemail", item.lastModifiedByEmail);
				}
				types.appendChild(member);
			}
		}


		Transformer tf = TransformerFactory.newInstance().newTransformer();
		tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		tf.setOutputProperty(OutputKeys.INDENT, "yes");
		tf.setOutputProperty(OutputKeys.METHOD, "xml");
		tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		Writer out = new StringWriter();
		tf.transform(new DOMSource(document), new StreamResult(out));

		Utils.writeFile(this.targetDir + filename, out.toString());
		this.logger.log(Level.INFO, "Writing " + new File(this.targetDir + filename).getCanonicalPath());
	}
}