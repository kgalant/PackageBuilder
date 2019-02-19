package com.kgal.packagebuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.kgal.packagebuilder.inventory.InventoryItem;
import com.sforce.soap.metadata.AsyncResult;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.PackageTypeMembers;
import com.sforce.soap.metadata.RetrieveMessage;
import com.sforce.soap.metadata.RetrieveRequest;
import com.sforce.soap.metadata.RetrieveResult;
import com.sforce.soap.metadata.RetrieveStatus;

/**
 * Sample that logs in and shows a menu of retrieve and deploy metadata options.
 */
public class OrgRetrieve {

    // one second in milliseconds
    private static final long ONE_SECOND = 1000;

    private final Logger logger        = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private boolean      requestCancel = false;

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
        if (!this.checkParameters()) {
            return;
        }

        final RetrieveRequest retrieveRequest = new RetrieveRequest();
        // The version in package.xml overrides the version in RetrieveRequest
        retrieveRequest.setApiVersion(this.apiVersion);
        if (this.manifestFile != null) {
            this.setUnpackaged(retrieveRequest);
        } else {
            this.generateRetrieveFilelistBasedOnInventory(retrieveRequest);
        }

        final AsyncResult asyncResult = this.metadataConnection.retrieve(retrieveRequest);
        final RetrieveResult result = this.waitForRetrieveCompletion(asyncResult);

        if (result.getStatus() == RetrieveStatus.Failed) {
            throw new Exception(result.getErrorStatusCode() + " msg: " +
                    result.getErrorMessage());
        } else if (result.getStatus() == RetrieveStatus.Succeeded) {
            // Print out any warning messages
            final StringBuilder stringBuilder = new StringBuilder();
            if (result.getMessages() != null) {
                for (final RetrieveMessage rm : result.getMessages()) {
                    stringBuilder.append(rm.getFileName() + " - " + rm.getProblem() + "\n");
                }
            }
            if (stringBuilder.length() > 0) {
                System.out.println("Retrieve warnings:\n" + stringBuilder);
            }

            System.out.println("Writing results to zip file:" + this.zipFile);
            final File resultsFile = new File(this.zipFile);
            final FileOutputStream os = new FileOutputStream(resultsFile);

            try {
                os.write(result.getZipFile());
            } finally {
                os.close();
            }
        }
    }

    public void setApiVersion(final double apiVersion) {
        this.apiVersion = apiVersion;
    }

    public void setInventoryToRetrieve(final HashMap<String, ArrayList<InventoryItem>> inventoryToRetrieve) {
        this.inventoryToRetrieve = inventoryToRetrieve;
    }

    public void setManifestFile(final String manifestFile) {
        this.manifestFile = manifestFile;
    }

    public void setMaxPolls(final int maxPolls) {
        this.maxPolls = maxPolls;
    }

    public void setMetadataConnection(final MetadataConnection metadataConnection) {
        this.metadataConnection = metadataConnection;
    }

    public void setSecondsBetweenPolls(final int secondsBetweenPolls) {
        this.secondsBetweenPolls = secondsBetweenPolls;
    }

    public void setZipFile(final String zipFile) {
        this.zipFile = zipFile;
    }

    private boolean checkParameters() {
        if (this.metadataConnection == null) {
            this.logger.log(Level.SEVERE, "MetadataConnection not provided, cannot continue.");
            return false;
        }
        if (this.zipFile == null) {
            this.logger.log(Level.SEVERE, "Output zipfile name not provided, cannot continue.");
            return false;
        }
        if ((this.manifestFile == null) && (this.inventoryToRetrieve == null)) {
            this.logger.log(Level.SEVERE, "Neither input manifest nor inventory object provided, cannot continue.");
            return false;
        }

        this.logger.log(Level.FINE, "API version for retrieve will be " + this.apiVersion);
        this.logger.log(Level.FINE, "Package will be pulled into " + this.zipFile);
        this.logger.log(Level.FINE, "Poll interval for retrieve will be " + this.secondsBetweenPolls +
                ", max number of polls: " + this.maxPolls);
        return true;
    }

    private void generateRetrieveFilelistBasedOnInventory(final RetrieveRequest retrieveRequest) {

        // generate the list of filenames to be retrieving

        final ArrayList<String> filenames = new ArrayList<>();

        for (final String mdType : this.inventoryToRetrieve.keySet()) {
            for (final InventoryItem i : this.inventoryToRetrieve.get(mdType)) {
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

    private com.sforce.soap.metadata.Package parsePackageManifest(final File file)
            throws ParserConfigurationException, IOException, SAXException {
        com.sforce.soap.metadata.Package packageManifest = null;
        final List<PackageTypeMembers> listPackageTypes = new ArrayList<>();
        final DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        final InputStream inputStream = new FileInputStream(file);
        final Element d = db.parse(inputStream).getDocumentElement();
        for (Node c = d.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element) {
                final Element ce = (Element) c;
                final NodeList nodeList = ce.getElementsByTagName("name");
                if (nodeList.getLength() == 0) {
                    continue;
                }
                final String name = nodeList.item(0).getTextContent();
                final NodeList m = ce.getElementsByTagName("members");
                final List<String> members = new ArrayList<>();
                for (int i = 0; i < m.getLength(); i++) {
                    final Node mm = m.item(i);
                    members.add(mm.getTextContent());
                }
                final PackageTypeMembers packageTypes = new PackageTypeMembers();
                packageTypes.setName(name);
                packageTypes.setMembers(members.toArray(new String[members.size()]));
                listPackageTypes.add(packageTypes);
            }
        }
        packageManifest = new com.sforce.soap.metadata.Package();
        final PackageTypeMembers[] packageTypesArray = new PackageTypeMembers[listPackageTypes.size()];
        packageManifest.setTypes(listPackageTypes.toArray(packageTypesArray));
        packageManifest.setVersion(this.apiVersion + "");
        return packageManifest;
    }

    private void setUnpackaged(final RetrieveRequest request) throws Exception {
        // Edit the path, if necessary, if your package.xml file is located
        // elsewhere
        final File unpackedManifest = new File(this.manifestFile);
        System.out.println("Manifest file: " + unpackedManifest.getAbsolutePath());

        if (!unpackedManifest.exists() || !unpackedManifest.isFile()) {
            throw new Exception("Should provide a valid retrieve manifest " +
                    "for unpackaged content. Looking for " +
                    unpackedManifest.getAbsolutePath());
        }

        // Note that we use the fully quualified class name because
        // of a collision with the java.lang.Package class
        final com.sforce.soap.metadata.Package p = this.parsePackageManifest(unpackedManifest);
        request.setUnpackaged(p);
        request.setSinglePackage(true);
    }

    private RetrieveResult waitForRetrieveCompletion(final AsyncResult asyncResult) throws Exception {
        // Wait for the retrieve to complete
        int poll = 0;
        final long waitTimeMilliSecs = this.secondsBetweenPolls * OrgRetrieve.ONE_SECOND;
        final String asyncResultId = asyncResult.getId();
        RetrieveResult result = null;
        do {
            if (this.requestCancel) {
                result = new RetrieveResult();
                result.setStatus(RetrieveStatus.Failed);
                return result;
            }
            ;
            Thread.sleep(waitTimeMilliSecs);
            if (poll++ > this.maxPolls) {
                throw new Exception("Request timed out.  If this is a large set " +
                        "of metadata components, check that the time allowed " +
                        "by maxPolls is sufficient.");
            }
            result = this.metadataConnection.checkRetrieveStatus(asyncResultId, true);
            System.out.println(
                    String.valueOf(poll) + "/" + String.valueOf(this.maxPolls) + " Package " + this.zipFile
                            + " Status: " + result.getStatus());
        } while (!result.isDone());

        return result;
    }

}