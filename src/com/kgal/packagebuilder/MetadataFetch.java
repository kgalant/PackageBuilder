package com.kgal.packagebuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.sforce.soap.metadata.FileProperties;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.soap.metadata.*;

import com.salesforce.migrationtoolutils.Utils;

public class MetadataFetch {

	String authEndPoint = "";

	private long timeStart;
	
	private MetadataConnection srcMetadataConnection;
	private String srcUrl ;
	private String srcUser;
	private String srcPwd;

	private Properties sourceProps;
	private Properties fetchProps;
	private static final String urlBase = "/services/Soap/u/";
	private String targetDir = "";
	
	private static final double API_VERSION = 33.0;
	private static double myApiVersion;
	private static final int MAX_NUM_POLL_REQUESTS = 20000;
	private static final int MAX_ITEMS=5000;
	private static final String UNZIP = "false";
	private static String myUnzip;
	private static int myMaxItems;
	private static HashMap<String, DescribeMetadataObject> describeMetadataObjectsMap;
	
	private static final String[] allMdTypes = new String[] { "AccountSettings", "ActionOverride", "ActivitiesSettings", "AddressSettings", "AnalyticSnapshot",
			"ApexClass", "ApexComponent", "ApexPage", "ApexTrigger", "AppMenu", "ApprovalProcess", "ArticleType", "AssignmentRules", "AuthProvider",
			"AutoResponseRules", "BaseSharingRule", "BusinessHoursSettings", "BusinessProcess", "CallCenter", "CaseSettings", "ChatterAnswersSettings",
			"CompanySettings", "Community", "CompactLayout", "ConnectedApp", "ContractSettings", "CriteriaBasedSharingRule", "CustomApplication",
			"CustomApplicationComponent", "CustomField", "CustomLabel", "CustomLabels", "CustomObject", "CustomObjectTranslation", "CustomPageWebLink",
			"CustomPermission", "CustomSite", "CustomTab", "Dashboard", "DataCategoryGroup", "Document", "EmailTemplate", "EntitlementProcess",
			"EntitlementSettings", "EntitlementTemplate", "ExternalDataSource", "FieldSet", "FlexiPage", "Flow", "Folder", "FolderShare",
			"ForecastingSettings", "Group", "HomePageComponent", "HomePageLayout", "IdeasSettings", "InstalledPackage", "KnowledgeSettings", "Layout",
			"Letterhead", "ListView", "LiveAgentSettings", "LiveChatAgentConfig", "LiveChatButton", "LiveChatDeployment", "Metadata", "MetadataWithContent",
			"MilestoneType", "MobileSettings", "NamedFilter", "NameSettings", "Network", "OpportunitySettings", "OrderSettings", "OwnerSharingRule", "Package",
			"PermissionSet", "Picklist", "Portal", "PostTemplate", "ProductSettings", "Profile", "Queue", "QuickAction", "QuoteSettings", "RecordType",
			"RemoteSiteSetting", "Report", "ReportType", "Role", "SamlSsoConfig", "Scontrol", "SearchLayouts", "SecuritySettings", "SharingReason",
			"SharingRecalculation", "SharingRules", "SharingSet", "SiteDotCom", "Skill", "StaticResource", "Territory", "SynonymDictionary", "Translations",
			"ValidationRule", "Weblink", "Workflow","WorkflowAlert","WorkflowFieldUpdate","WorkflowKnowledgePublish","WorkflowOutboundMessage","WorkflowRule",
			"WorkflowSend","WorkflowTask"};
	
	public static void main(String[] args) throws RemoteException, Exception {

		MetadataFetch sample = new MetadataFetch();
		
		if (args.length < 2) {
			System.out.println("Usage parameters: <org property file path> <fetch property path>");
			System.out.println("Example: c:\\temp\\migration\\test.properties c:\\temp\\migration\\fetch.properties - will fetch the items defined in the fetch.properties file from the org "
			+ "specified in the file c:\\temp\\migration\\test.properties and put them in the target directory specified in the properties file, subfolder 'retrieved'");
			System.out.println("if metadataitems property is blank in the fetch-file - will fetch all metadata from the org specified in test.properties.");
			System.out.println("Parameters not supplied - exiting.");
			System.exit(0);
		}
		
		if (args.length > 0) {
			sample.sourceProps = Utils.initProps(args[0]);
		}
		
		if (args.length > 0) {
			sample.fetchProps = Utils.initProps(args[1]);
		}
		
		sample.run();
	}

	public void run() throws RemoteException, Exception {
		
		HashSet<String> typesToFetch = new HashSet<String>();
		HashSet<String> allMdTypesSet = new HashSet<String>();
		
		String mdTypesToExamine = fetchProps.getProperty("metadataitems") == null ? "" : fetchProps.getProperty("metadataitems");
		
		for (String s : mdTypesToExamine.split(",")) {
			typesToFetch.add(s);
		}
		
		for (String s : allMdTypes) {
			allMdTypesSet.add(s);
			if (mdTypesToExamine == null || mdTypesToExamine.length() == 0) {
				typesToFetch.add(s);
			}
		}
		
		myApiVersion = sourceProps.getProperty("apiversion") == null ? API_VERSION : Double.parseDouble(sourceProps.getProperty("apiversion"));
		srcUrl = sourceProps.getProperty("serverurl") + urlBase + myApiVersion;
		srcUser = sourceProps.getProperty("username");
		srcPwd = sourceProps.getProperty("password");
		myMaxItems = fetchProps.getProperty("maxItems") == null ? MAX_ITEMS : Integer.parseInt(fetchProps.getProperty("maxItems"));
		myUnzip = fetchProps.getProperty("unzip") == null ? UNZIP : fetchProps.getProperty("unzip");
		this.targetDir = Utils.checkPathSlash(Utils.checkPathSlash(fetchProps.getProperty("targetdirectory")) + "retrieved");
		
		System.out.println("target directory: " + this.targetDir);
		
		Utils.checkDir(targetDir);
		
		// Make a login call to source
		this.srcMetadataConnection = MetadataLoginUtil.mdLogin(srcUrl, srcUser, srcPwd);
		
		// get a describe
		
		DescribeMetadataResult dmr = srcMetadataConnection.describeMetadata(myApiVersion);
		describeMetadataObjectsMap = new HashMap<String, DescribeMetadataObject>();
		
		 for (DescribeMetadataObject obj : dmr.getMetadataObjects()) {
			 describeMetadataObjectsMap.put(obj.getXmlName(), obj);
		 }

		Iterator<String> i = typesToFetch.iterator();
		
		while (i.hasNext()) {
			String mdType = i.next();
			if (!allMdTypesSet.contains(mdType)) {
				continue;
			}
			System.out.println("*********************************************");
			System.out.println("Processing: " + mdType);
			System.out.println("*********************************************");
			fetchMetadata(mdType);
			
			System.out.println("---------------------------------------------");
			System.out.println("Finished processing: " + mdType);
			System.out.println("---------------------------------------------");
			
		}
	}

	private HashMap<String, ArrayList<String>> fetchMetadata (String metadataType) throws RemoteException, Exception {
		startTiming();
		HashMap<String, ArrayList<String>> retval = new HashMap<String, ArrayList<String>>();
		try {
			
			ArrayList<FileProperties> foldersToProcess = new ArrayList<FileProperties>();
			boolean isFolder = false;
			// check if what we have here is in folders
			
			DescribeMetadataObject obj = describeMetadataObjectsMap.get(metadataType);
			if (obj != null && obj.getInFolder() == true) {
				isFolder = true;
				System.out.println(metadataType + " is stored in folders. Getting folder list.");
				ListMetadataQuery query = new ListMetadataQuery();
				// stupid hack for emailtemplate folder name
				String type;
				if (metadataType.toLowerCase().equals("emailtemplate")) {
					type = "EmailFolder";
				} else {
					type = metadataType + "Folder";
				}
				
				query.setType(type);
				FileProperties[] srcMd = srcMetadataConnection.listMetadata(new ListMetadataQuery[] { query }, myApiVersion);
				if (srcMd != null && srcMd.length > 0) {
					for (FileProperties n : srcMd) {
						foldersToProcess.add(n);
					}
				}
			}
			
			Iterator<FileProperties> folder = foldersToProcess.iterator();
			
			HashMap<String, ArrayList<FileProperties>> metadataMap = new HashMap<String, ArrayList<FileProperties>>();
			
			int numfetches = 0;
			int itemCount = 0;
			
			
			do {
				FileProperties folderProperties = null;
				ListMetadataQuery query = new ListMetadataQuery();
				query.setType(metadataType);
				String folderName = null;
				if (isFolder) {
					folderProperties = folder.next(); 
					folderName = folderProperties.getFullName();
					query.setFolder(folderName);
				}
				
				// Assuming that the SOAP binding has already been established.
				
				// generate full metadata inventory
				
				
				FileProperties[] srcMd = srcMetadataConnection.listMetadata(new ListMetadataQuery[] { query }, myApiVersion);
				if (folderName != null) {
					System.out.printf("%-80.80s","Processing folder: " + folderName + " ");
					// fetch folders themselves
					ArrayList<FileProperties> filenameList = new ArrayList<FileProperties>();
					filenameList.add(folderProperties);
					metadataMap.put(folderProperties.getFileName(), filenameList);
					itemCount++;
				}
				itemCount += srcMd.length;
				System.out.println("Metadata items: " + srcMd.length + "\tCurrent total: " + itemCount);
				
				
				if (srcMd != null && srcMd.length > 0) {
					for (FileProperties n : srcMd) {
						ArrayList<FileProperties> filenameList = metadataMap.get(n.getFileName()); 
						if (filenameList == null) {
							filenameList = new ArrayList<FileProperties>();
							metadataMap.put(n.getFileName(), filenameList);
						} 
						filenameList.add(n);
					}
				} else {
					if (!isFolder) {
						System.out.println("No items of this type, skipping...");
						endTiming();
						return retval;
					}
					if (!folder.hasNext()) {
						endTiming();
						return retval;
					}
				}
				if (isFolder == true && folder.hasNext()) {
					continue;
				}

				// now start pulling things off the metadataMap and fetching in chunks
				
				ArrayList<FileProperties> srcMdList = new ArrayList<FileProperties> ();
				
				for (ArrayList<FileProperties> filenameList : metadataMap.values()) {
					// check if adding this list to the srcMdList would take it over the limit
					// if there are files that are over the limit by themselves, they'll be fetched over limit anyway
					
					if (srcMdList.size() + filenameList.size() > myMaxItems) {
						// yes, adding this would take current list over limit
						// so fetch current list
						
						packageAndFetch(srcMdList, metadataType + "_" + numfetches++, metadataType);
						
						// clear current list
						srcMdList.clear();
					}	
					// add the new item to the current list
					srcMdList.addAll(filenameList);
				}
				
				// last fetch 
				
				if (srcMdList.size() > 0) {
					packageAndFetch(srcMdList, metadataType + "_" + numfetches++, metadataType);
				}
				
				
//				if (overFlow.size() == 0 && isFolder == true && folder.hasNext()) {
//					// grab another folder
//					continue;
//				} else {
//					packageAndFetch(srcMdList, metadataType + "_" + numfetches++, metadataType);
//				}
//				
//				// now, if we have anything in overflow, remove it maxItems at a time and fetch
//				
//				srcMdList.clear();
//				
//				while (overFlow.size() > 0) {
//					srcMdList.add(overFlow.remove(0));
//					if (srcMdList.size() % myMaxItems == 0) {
//						packageAndFetch(srcMdList, metadataType + "_" + numfetches++, metadataType);
//						srcMdList.clear();
//					}
//				} 
//				
//				if (isFolder == true && folder.hasNext()) {
//					continue;
//				} else {
//					packageAndFetch(srcMdList, metadataType + "_" + numfetches++, metadataType);
//				}
				
			} while (folder.hasNext());
            
		} catch (ConnectionException ce) {
//			ce.printStackTrace();
			System.out.println("Exception processing: " + metadataType);
			System.out.println(ce.getMessage());
		}
		
		if (myUnzip.equals("true")) {
			unzipAll(metadataType);
		}
		
		endTiming();
		return retval;
	}

	private void packageAndFetch(ArrayList<FileProperties> srcMdMap, String filename, String metadataType) throws Exception {
		// create a package
		
		System.out.println("Asked to fetch: " + srcMdMap.size() + " items.");
		
		com.sforce.soap.metadata.Package r = new com.sforce.soap.metadata.Package();
		
		ArrayList<PackageTypeMembers> pd = new ArrayList<PackageTypeMembers>();
		ArrayList<String> names = new ArrayList<String>();
		
		
		PackageTypeMembers pdi = new PackageTypeMembers();
		for (FileProperties n : srcMdMap) {				
            names.add(n.getFullName());
            
			// stupid hack for emailtemplate folder name
			String type = "";
			if (metadataType.toLowerCase().equals("emailfolder")) {
				type = "EmailTemplate";
			} else if (metadataType.equals("DocumentFolder") ||
					metadataType.equals("ReportFolder") ||
					metadataType.equals("DashboardFolder")){
				type = metadataType.substring(0, metadataType.indexOf("Folder"));
			}
			
            pdi.setName(type == "" ? n.getType() : type);
		}
		
		if (pdi != null && pdi.getName() != null && pdi.getName().equals("Flow")) {
			names.clear();
			names.add("*");
		}
		
        pdi.setMembers(names.toArray(new String[names.size()]));
        pd.add(pdi);
        
        r.setTypes(pd.toArray(new PackageTypeMembers[pd.size()]));
        r.setVersion(API_VERSION + "");
		
		// create request
        
        RetrieveRequest retrieveRequest = new RetrieveRequest(); 
        retrieveRequest.setApiVersion(myApiVersion);
        retrieveRequest.setUnpackaged(r);
		
     // Start the retrieve operation
        AsyncResult asyncResult = srcMetadataConnection.retrieve(retrieveRequest);
        String asyncResultId = asyncResult.getId();
        
        // Wait for the retrieve to complete
        int poll = 0;
        long waitTimeMilliSecs = 10000;
        RetrieveResult result = null;
        do {
            Thread.sleep(waitTimeMilliSecs);
            
            if (poll++ > MAX_NUM_POLL_REQUESTS) {
                throw new Exception("Request timed out.  If this is a large set " +
                "of metadata components, check that the time allowed " +
                "by MAX_NUM_POLL_REQUESTS is sufficient.");
            }
            result = srcMetadataConnection.checkRetrieveStatus(
                    asyncResultId, false);
            System.out.println("Retrieve Status: " + result.getStatus());
        } while (!result.isDone());

        if (result.getStatus() == RetrieveStatus.Failed) {
            throw new Exception(result.getErrorStatusCode() + " msg: " +
                    result.getErrorMessage());
        } else if (result.getStatus() == RetrieveStatus.Succeeded) {      
            // Print out any warning messages
            StringBuilder buf = new StringBuilder();
            if (result.getMessages() != null) {
                for (RetrieveMessage rm : result.getMessages()) {
                    buf.append(rm.getFileName() + " - " + rm.getProblem());
                }
            }
            if (buf.length() > 0) {
                System.out.println("Retrieve warnings:\n" + buf);
            }
    
            // Write the zip to the file system
            System.out.println("Writing results to zip file");
            ByteArrayInputStream bais = new ByteArrayInputStream(result.getZipFile());
            File resultsFile = new File(targetDir + filename +  ".zip");
            FileOutputStream os = new FileOutputStream(resultsFile);
            try {
                ReadableByteChannel src = Channels.newChannel(bais);
                FileChannel dest = os.getChannel();
                copy(src, dest);
                
                System.out.println("Results written to " + resultsFile.getAbsolutePath());
            } finally {
                os.close();
            }
            
            
        }
	}
	
	private void unzipAll(String newDirLocation) {
		FileFilter fileFilter = new WildcardFileFilter("*.zip");
		File[] files = new File(targetDir).listFiles(fileFilter);
		for (File f : files) {
			Utils.unzip(f.getAbsolutePath(), targetDir);
            String newFileName = Utils.checkPathSlash(targetDir) + newDirLocation;
            Utils.checkDir(newFileName);
            Utils.mergeTwoDirectories(new File(newFileName), new File(targetDir + "unpackaged"));
			f.renameTo(new File(Utils.checkPathSlash(newFileName) + f.getName()));
		}
	}
	
	
	private void copy(ReadableByteChannel src, WritableByteChannel dest) throws IOException {
		// Use an in-memory byte buffer
		ByteBuffer buffer = ByteBuffer.allocate(8092);
		while (src.read(buffer) != -1) {
			buffer.flip();
			while (buffer.hasRemaining()) {
				dest.write(buffer);
			}
			buffer.clear();
		}
	}
	
	private void startTiming() {
		timeStart = System.currentTimeMillis();
	}
	
	private void endTiming() {
		long end = System.currentTimeMillis();
		long diff = ((end - timeStart));
		String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(diff),
	            TimeUnit.MILLISECONDS.toMinutes(diff) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(diff)),
	            TimeUnit.MILLISECONDS.toSeconds(diff) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff)));
		System.out.println("Duration: " + hms);
	}
	
	
}
