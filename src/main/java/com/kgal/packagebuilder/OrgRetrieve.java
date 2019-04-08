package com.kgal.packagebuilder;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.*;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import com.kgal.packagebuilder.inventory.InventoryItem;
import com.sforce.soap.metadata.*;

/**
 * Sample that logs in and shows a menu of retrieve and deploy metadata options.
 */
public class OrgRetrieve {

	// one second in milliseconds
	private static final long ONE_SECOND = 1000;

	private final Logger logger        = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	private boolean      requestCancel = false;
//	private int packageNumber = 1;

	private MetadataConnection metadataConnection;

	private String zipFile;

	// manifest file that controls which components get retrieved
	private String manifestFile;
	private double apiVersion    = 45.0;

	// what to retrieve if not based on package.xml file

	private int secondsBetweenPolls = 15;

	private HashMap<String, ArrayList<InventoryItem>> inventoryToRetrieve;

	// maximum number of attempts to deploy the zip file
	private int maxPolls = 200;

	public OrgRetrieve(final Level level) {
		this.logger.setLevel(level);
	}

	public void requestCancel() {
		this.requestCancel = true;
	}
	public void retrieveZip() throws Exception {

		// check parameters
		if (!checkParameters()) {
			return;
		}

		final RetrieveRequest retrieveRequest = new RetrieveRequest();
		// The version in package.xml overrides the version in RetrieveRequest
		retrieveRequest.setApiVersion(apiVersion);
		if (manifestFile != null) {
			setUnpackaged(retrieveRequest);
		} else {
			generateRetrieveFilelistBasedOnInventory(retrieveRequest);
		}


		final AsyncResult asyncResult = metadataConnection.retrieve(retrieveRequest);
		final RetrieveResult result = waitForRetrieveCompletion(asyncResult);

		if (result.getStatus() == RetrieveStatus.Failed) {
			throw new Exception(result.getErrorStatusCode() + " msg: " +
					result.getErrorMessage());
		} else if (result.getStatus() == RetrieveStatus.Succeeded) {  
			// Print out any warning messages
			if (result.getMessages() != null) {
				this.logger.log(Level.INFO, "Retrieve warnings:\n");
				for (final RetrieveMessage rm : result.getMessages()) {
					logger.log(Level.INFO, rm.getFileName() + " - " + rm.getProblem());
				}
			}

			File resultsFile = new File(zipFile);
			this.logger.log(Level.INFO,"Writing results to zip file: " + resultsFile.getCanonicalPath());
			FileOutputStream os = new FileOutputStream(resultsFile);

			try {
				os.write(result.getZipFile());
			} finally {
				os.close();
			}
		}
	}

	private void generateRetrieveFilelistBasedOnInventory(final RetrieveRequest retrieveRequest) {

		// generate the list of filenames to be retrieving

		final ArrayList<String> filenames = new ArrayList<String>();

		for (final String mdType : inventoryToRetrieve.keySet()) {
			for (final InventoryItem i : inventoryToRetrieve.get(mdType)) {
				filenames.add(i.getFileName());
			}
		}

		String[] specificFiles = new String[filenames.size()];

		specificFiles = filenames.toArray(specificFiles);

		retrieveRequest.setSpecificFiles(specificFiles);
		retrieveRequest.setSinglePackage(true);
		retrieveRequest.setPackageNames(null);
		retrieveRequest.setUnpackaged(null);

	}

	private boolean checkParameters() {
		if (metadataConnection == null) {
			this.logger.log(Level.INFO,"MetadataConnection not provided, cannot continue.");
			return false;
		}
		if (zipFile == null) {
			this.logger.log(Level.INFO,"Output zipfile name not provided, cannot continue.");
			return false;
		}
		if (manifestFile == null && inventoryToRetrieve == null) {
			this.logger.log(Level.INFO,"Neither input manifest nor inventory object provided, cannot continue.");
			return false;
		}

		this.logger.log(Level.FINE, "API version for retrieve will be " + apiVersion);
		this.logger.log(Level.FINE, "Package: " + zipFile);
		this.logger.log(Level.FINE, "Poll interval for retrieve will be " + secondsBetweenPolls + 
				", max number of polls: " + maxPolls);	
		return true;
	}

	private RetrieveResult waitForRetrieveCompletion(AsyncResult asyncResult) throws Exception {
		// Wait for the retrieve to complete
		int poll = 0;
		final long waitTimeMilliSecs = secondsBetweenPolls * ONE_SECOND;
		final String asyncResultId = asyncResult.getId();
		RetrieveResult result = null;
		do {
			if (this.requestCancel) {
				result = new RetrieveResult();
				result.setStatus(RetrieveStatus.Failed);
				return result;
			}
			Thread.sleep(waitTimeMilliSecs);
			if (poll++ > maxPolls) {
				throw new Exception("Request timed out.  If this is a large set " +
						"of metadata components, check that the time allowed " +
						"by maxPolls is sufficient.");
			}
			result = metadataConnection.checkRetrieveStatus(asyncResultId, true);
			this.logger.log(Level.FINE,"Package " + zipFile + " Status: " + result.getStatus());
		} while (!result.isDone());         

		return result;
	}

	private void setUnpackaged(RetrieveRequest request) throws Exception {
		// Edit the path, if necessary, if your package.xml file is located elsewhere
		File unpackedManifest = new File(manifestFile);
		this.logger.log(Level.FINE,"Manifest file: " + unpackedManifest.getCanonicalPath());

		if (!unpackedManifest.exists() || !unpackedManifest.isFile()) {
			throw new Exception("Should provide a valid retrieve manifest " +
					"for unpackaged content. Looking for " +
					unpackedManifest.getAbsolutePath());
		}

		// Note that we use the fully quualified class name because
		// of a collision with the java.lang.Package class
		com.sforce.soap.metadata.Package p = parsePackageManifest(unpackedManifest);
		request.setUnpackaged(p);
		request.setSinglePackage(true);
	}

	private com.sforce.soap.metadata.Package parsePackageManifest(File file)
			throws ParserConfigurationException, IOException, SAXException {
		com.sforce.soap.metadata.Package packageManifest = null;
		List<PackageTypeMembers> listPackageTypes = new ArrayList<PackageTypeMembers>();
		DocumentBuilder db =
				DocumentBuilderFactory.newInstance().newDocumentBuilder();
		InputStream inputStream = new FileInputStream(file);
		Element d = db.parse(inputStream).getDocumentElement();
		for (Node c = d.getFirstChild(); c != null; c = c.getNextSibling()) {
			if (c instanceof Element) {
				Element ce = (Element) c;
				NodeList nodeList = ce.getElementsByTagName("name");
				if (nodeList.getLength() == 0) {
					continue;
				}
				String name = nodeList.item(0).getTextContent();
				NodeList m = ce.getElementsByTagName("members");
				List<String> members = new ArrayList<String>();
				for (int i = 0; i < m.getLength(); i++) {
					Node mm = m.item(i);
					members.add(mm.getTextContent());
				}
				PackageTypeMembers packageTypes = new PackageTypeMembers();
				packageTypes.setName(name);
				packageTypes.setMembers(members.toArray(new String[members.size()]));
				listPackageTypes.add(packageTypes);
			}
		}
		packageManifest = new com.sforce.soap.metadata.Package();
		PackageTypeMembers[] packageTypesArray =
				new PackageTypeMembers[listPackageTypes.size()];
		packageManifest.setTypes(listPackageTypes.toArray(packageTypesArray));
		packageManifest.setVersion(apiVersion + "");
		return packageManifest;
	}

	public void setMetadataConnection(MetadataConnection metadataConnection) {
		this.metadataConnection = metadataConnection;
	}

	public void setZipFile(String zipFile) {
		this.zipFile = zipFile;
	}

	public void setManifestFile(String manifestFile) {
		this.manifestFile = manifestFile;
	}

	public void setApiVersion(double apiVersion) {
		this.apiVersion = apiVersion;
	}

//	public void setPackageNumber(int packageNumber) {
//		this.packageNumber = packageNumber;
//	}

	public void setSecondsBetweenPolls(int secondsBetweenPolls) {
		this.secondsBetweenPolls = secondsBetweenPolls;
	}

	public void setInventoryToRetrieve(HashMap<String, ArrayList<InventoryItem>> inventoryToRetrieve) {
		this.inventoryToRetrieve = inventoryToRetrieve;
	}

	public void setMaxPolls(int maxPolls) {
		this.maxPolls = maxPolls;
	}
}