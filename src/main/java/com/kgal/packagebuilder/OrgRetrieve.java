package com.kgal.packagebuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.kgal.packagebuilder.inventory.InventoryItem;
import com.sforce.soap.metadata.*;

/**
 * Sample that logs in and shows a menu of retrieve and deploy metadata options.
 */
public class OrgRetrieve {

    public enum Loglevel {
        VERBOSE(2), NORMAL(1), BRIEF(0);
        private final int level;

        Loglevel(final int level) {
            this.level = level;
        }

        int getLevel() {
            return this.level;
        }

    };

    private Loglevel           loglevel;
    private boolean            requestCancel = false;
    private MetadataConnection metadataConnection;

    private String zipFile;

    // manifest file that controls which components get retrieved
    private String manifestFile;

    private double apiVersion          = 45.0;
    private int    packageNumber       = 1;
    private int    secondsBetweenPolls = 30;

    // what to retrieve if not based on package.xml file

    private HashMap<String, ArrayList<InventoryItem>> inventoryToRetrieve;

    // one second in milliseconds
    private static final long ONE_SECOND = 1000;

    // maximum number of attempts to deploy the zip file
    private int maxPolls = 200;

    public OrgRetrieve(Loglevel level) {
        loglevel = level;
    }

    public void requestCancel() {
        this.requestCancel = true;
    }

    public void retrieveZip() throws Exception {

        // check parameters
        if (!checkParameters()) {
            return;
        }

        RetrieveRequest retrieveRequest = new RetrieveRequest();
        // The version in package.xml overrides the version in RetrieveRequest
        retrieveRequest.setApiVersion(apiVersion);
        if (manifestFile != null) {
            setUnpackaged(retrieveRequest);
        } else {
            generateRetrieveFilelistBasedOnInventory(retrieveRequest);
        }

        AsyncResult asyncResult = metadataConnection.retrieve(retrieveRequest);
        RetrieveResult result = waitForRetrieveCompletion(asyncResult);

        if (result.getStatus() == RetrieveStatus.Failed) {
            throw new Exception(result.getErrorStatusCode() + " msg: " +
                    result.getErrorMessage());
        } else if (result.getStatus() == RetrieveStatus.Succeeded) {
            // Print out any warning messages
            StringBuilder stringBuilder = new StringBuilder();
            if (result.getMessages() != null) {
                for (RetrieveMessage rm : result.getMessages()) {
                    stringBuilder.append(rm.getFileName() + " - " + rm.getProblem() + "\n");
                }
            }
            if (stringBuilder.length() > 0) {
                System.out.println("Retrieve warnings:\n" + stringBuilder);
            }

            System.out.println("Writing results to zip file:"+zipFile);
            File resultsFile = new File(zipFile);
            FileOutputStream os = new FileOutputStream(resultsFile);

            try {
                os.write(result.getZipFile());
            } finally {
                os.close();
            }
        }
    }

    private void generateRetrieveFilelistBasedOnInventory(RetrieveRequest retrieveRequest) {

        // generate the list of filenames to be retrieving

        ArrayList<String> filenames = new ArrayList<String>();

        for (String mdType : inventoryToRetrieve.keySet()) {
            for (InventoryItem i : inventoryToRetrieve.get(mdType)) {
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
            this.log("MetadataConnection not provided, cannot continue.", Loglevel.BRIEF);
            return false;
        }
        if (zipFile == null) {
            this.log("Output zipfile name not provided, cannot continue.", Loglevel.BRIEF);
            return false;
        }
        if (manifestFile == null && inventoryToRetrieve == null) {
            this.log("Neither input manifest nor inventory object provided, cannot continue.", Loglevel.BRIEF);
            return false;
        }

        this.log("API version for retrieve will be " + apiVersion, Loglevel.VERBOSE);
        this.log("Package running number will be " + packageNumber, Loglevel.VERBOSE);
        this.log("Poll interval for retrieve will be " + secondsBetweenPolls +
                ", max number of polls: " + maxPolls, Loglevel.VERBOSE);
        return true;
    }

    private RetrieveResult waitForRetrieveCompletion(AsyncResult asyncResult) throws Exception {
        // Wait for the retrieve to complete
        int poll = 0;
        long waitTimeMilliSecs = secondsBetweenPolls * ONE_SECOND;
        String asyncResultId = asyncResult.getId();
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
            System.out.println("Package " + packageNumber + " Status: " + result.getStatus());
        } while (!result.isDone());

        return result;
    }

    private void setUnpackaged(RetrieveRequest request) throws Exception {
        // Edit the path, if necessary, if your package.xml file is located
        // elsewhere
        File unpackedManifest = new File(manifestFile);
        System.out.println("Manifest file: " + unpackedManifest.getAbsolutePath());

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
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
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
        PackageTypeMembers[] packageTypesArray = new PackageTypeMembers[listPackageTypes.size()];
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

    public void setPackageNumber(int packageNumber) {
        this.packageNumber = packageNumber;
    }

    public void setSecondsBetweenPolls(int secondsBetweenPolls) {
        this.secondsBetweenPolls = secondsBetweenPolls;
    }

    public void setInventoryToRetrieve(HashMap<String, ArrayList<InventoryItem>> inventoryToRetrieve) {
        this.inventoryToRetrieve = inventoryToRetrieve;
    }

    public void setMaxPolls(int maxPolls) {
        this.maxPolls = maxPolls;
    }

    private void log(final String logText, final Loglevel level) {
        if ((this.loglevel == null) || (level.getLevel() <= this.loglevel.getLevel())) {
            System.out.println(logText);
        }
    }
    //
    // private void logPartialLine(final String logText, final Loglevel level) {
    // if (level.getLevel() <= this.loglevel.getLevel()) {
    // System.out.print(logText);
    // }
    // }
}