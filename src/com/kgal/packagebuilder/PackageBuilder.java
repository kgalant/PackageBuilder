package com.kgal.packagebuilder;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.sforce.soap.metadata.FileProperties;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.soap.metadata.*;

import com.salesforce.migrationtoolutils.Utils;

public class PackageBuilder {

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
	private static final int MAX_ITEMS=5000;
	private static int myMaxItems;
	private static String skipItems;
	private static ArrayList<Pattern> skipPatterns = new ArrayList<Pattern>();
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

		PackageBuilder sample = new PackageBuilder();
		
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
		srcUrl = sourceProps.getProperty("sf_url") + urlBase + myApiVersion;
		srcUser = sourceProps.getProperty("sf_username");
		srcPwd = sourceProps.getProperty("sf_password");
		myMaxItems = fetchProps.getProperty("maxItems") == null ? MAX_ITEMS : Integer.parseInt(fetchProps.getProperty("maxItems"));
		skipItems = fetchProps.getProperty("skipItems");
		
		
		this.targetDir = Utils.checkPathSlash(Utils.checkPathSlash(fetchProps.getProperty("targetdirectory")) + "retrieved");
		
		System.out.println("target directory: " + this.targetDir);
		
		Utils.checkDir(targetDir);
		
		HashMap<String,ArrayList<String>> inventory = new HashMap<String,ArrayList<String>>(); 
		
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
			ArrayList<String> mdTypeItemList = fetchMetadata(mdType);
			
			Collections.sort(mdTypeItemList);
			
			inventory.put(mdType, mdTypeItemList);
			
			System.out.println("---------------------------------------------");
			System.out.println("Finished processing: " + mdType);
			System.out.println("---------------------------------------------");
			
		}
		
		generatePackageXML(inventory);
	}

	private void generatePackageXML(HashMap<String, ArrayList<String>> inventory) {
		StringBuffer packageXML = new StringBuffer();
		packageXML.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		packageXML.append("<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">\n");
		
		ArrayList<String> types = new ArrayList<String>();
		types.addAll(inventory.keySet());
		Collections.sort(types);
		
//		Initiate patterns array
		
		for (String p : skipItems.split(",")) {
			try {
				skipPatterns.add(Pattern.compile(p));
			} catch (PatternSyntaxException  e) {
				System.out.println("Tried to compile pattern: " + p + " but got exception: ");
				e.printStackTrace();
			}
		}
		boolean shouldSkip = false;
		
		for (String mdType : types) {
			shouldSkip = false;
//			first, check if any of the patterns match the whole type
			String mdTypeFullName = mdType + ":";
			for (Pattern p : skipPatterns) {
				
				Matcher m = p.matcher(mdTypeFullName);
				if (m.matches()) {
					System.out.println("Skip pattern: " + p.pattern() + " matches the metadata type: " + mdTypeFullName + ", entire type will be skipped.");
					shouldSkip = true;
					break;
				}
			}
			if (shouldSkip) {
				continue;
			}
			
			ArrayList<String> items = inventory.get(mdType);
			
			packageXML.append("\t<types>\n");
			for (String item : items) {
				shouldSkip = false;
				mdTypeFullName = mdType + ":" + item;
				for (Pattern p : skipPatterns) {
					Matcher m = p.matcher(mdTypeFullName);
					if (m.matches()) {
						System.out.println("Skip pattern: " + p.pattern() + " matches the metadata item: " + mdTypeFullName + ", item will be skipped.");
						shouldSkip = true;
						break;
					}
				}
				if (!shouldSkip) {
					packageXML.append("\t\t<members>" + item + "</members>\n");	
				}
					
			}
			
			packageXML.append("\t\t<name>" + mdType + "</name>\n");
			packageXML.append("\t</types>\n");
		}
		
		packageXML.append("\t<version>" + myApiVersion + "</version>\n");
		packageXML.append("</Package>\n");
		
		Utils.writeFile(targetDir + "package.xml", packageXML.toString());
		
	}

	private ArrayList<String> fetchMetadata (String metadataType) throws RemoteException, Exception {
		startTiming();
		ArrayList<String> packageMap = new ArrayList<String>();
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
					packageMap.add(folderName);
					ArrayList<FileProperties> filenameList = new ArrayList<FileProperties>();
					filenameList.add(folderProperties);
					metadataMap.put(folderProperties.getFileName(), filenameList);
					itemCount++;
				}
				itemCount += srcMd.length;
				System.out.println("Metadata items: " + srcMd.length + "\tCurrent total: " + itemCount);
				
				
				if (srcMd != null && srcMd.length > 0) {
					for (FileProperties n : srcMd) {
						if (n.getNamespacePrefix() == null) {
							packageMap.add(n.getFullName());	
						}
						
					}
				} else {
					if (!isFolder) {
						System.out.println("No items of this type, skipping...");
						endTiming();
						return packageMap;
					}
					if (!folder.hasNext()) {
						endTiming();
						return packageMap;
					}
				}
				if (isFolder == true && folder.hasNext()) {
					continue;
				}

			} while (folder.hasNext());
            
		} catch (ConnectionException ce) {
//			ce.printStackTrace();
			System.out.println("Exception processing: " + metadataType);
			System.out.println(ce.getMessage());
		}
		
		endTiming();
		return packageMap;
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
