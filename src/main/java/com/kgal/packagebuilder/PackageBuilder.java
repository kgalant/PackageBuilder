package com.kgal.packagebuilder;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.ArrayUtils;

import com.kgal.packagebuilder.inventory.InventoryItem;
import com.kgal.packagebuilder.output.GitOutputManager;
import com.kgal.packagebuilder.output.SimpleXMLDoc;
import com.salesforce.migrationtoolutils.Utils;
import com.sforce.soap.metadata.DescribeMetadataObject;
import com.sforce.soap.metadata.DescribeMetadataResult;
import com.sforce.soap.metadata.FileProperties;
import com.sforce.soap.metadata.ListMetadataQuery;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;

public class PackageBuilder {

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

	public enum OperationMode {
		DIR(0), ORG(1);

		private final int level;

		OperationMode(final int level) {
			this.level = level;
		}

		int getLevel() {
			return this.level;
		}
	}

	private enum PatternField {
		ITEMNAME(0), EMAIL(1), USERNAME(2);
		private final int level;

		PatternField(final int level) {
			this.level = level;
		}
		int getLevel() {
			return this.level;
		}

	}

	// Static values that don't change
	private static final String  DEFAULT_DATE_FORMAT    = "yyyy-MM-dd'T'HH:mm:ss";
	private static final String  URLBASE                = "/services/Soap/u/";
	public static final int     MAXITEMSINPACKAGE      = 10000;
	public static final double   API_VERSION            = 45.0;
	public static final boolean  INCLUDECHANGEDATA      = false;

	private static final String[] STANDARDVALUETYPESARRAY = new String[] { "AccountContactMultiRoles",
			"AccountContactRole", "AccountOwnership", "AccountRating", "AccountType", "AddressCountryCode",
			"AddressStateCode",
			"AssetStatus", "CampaignMemberStatus", "CampaignStatus", "CampaignType", "CaseContactRole", "CaseOrigin",
			"CasePriority", "CaseReason",
			"CaseStatus", "CaseType", "ContactRole", "ContractContactRole", "ContractStatus", "EntitlementType",
			"EventSubject", "EventType",
			"FiscalYearPeriodName", "FiscalYearPeriodPrefix", "FiscalYearQuarterName", "FiscalYearQuarterPrefix",
			"IdeaCategory1",
			"IdeaMultiCategory", "IdeaStatus", "IdeaThemeStatus", "Industry", "InvoiceStatus", "LeadSource",
			"LeadStatus", "OpportunityCompetitor",
			"OpportunityStage", "OpportunityType", "OrderStatus1", "OrderType", "PartnerRole", "Product2Family",
			"QuestionOrigin1", "QuickTextCategory",
			"QuickTextChannel", "QuoteStatus", "SalesTeamRole", "Salutation", "ServiceContractApprovalStatus",
			"SocialPostClassification",
			"SocialPostEngagementLevel", "SocialPostReviewedStatus", "SolutionStatus", "TaskPriority", "TaskStatus",
			"TaskSubject", "TaskType",
			"WorkOrderLineItemStatus", "WorkOrderPriority", "WorkOrderStatus" };

	private static final String[] ADDITIONALTYPESTOADD = new String[] { "CustomLabel", "AssignmentRule",
			"BusinessProcess","CompactLayout","CustomField","FieldSet","Index","ListView","NamedFilter","RecordType","SharingReason","ValidationRule","WebLink", // CustomObject components
			"WorkflowActionReference","WorkflowAlert","WorkflowEmailRecipient","WorkflowFieldUpdate","WorkflowFlowAction","WorkflowFlowActionParameter", 		// Workflow components
			"WorkflowKnowledgePublish","WorkflowOutboundMessage","WorkflowRule","WorkflowTask","WorkflowTimeTrigger"											// Workflow components

	};

	private static final String[] ITEMSTOINCLUDEWITHPROFILESPERMSETS = new String[] { "ApexClass", "CustomApplication", "CustomField", 
			"CustomObject", "CustomTab", "ExternalDataSource", "RecordType", "ApexPage"};

	private static final String[] SPECIALTREATMENTPERMISSIONTYPES = new String[] { "Profile", "PermissionSet"};


	// Collections
	private final ArrayList<Pattern>  skipPatterns  = new ArrayList<>();
	private final ArrayList<Pattern>  includePatterns  = new ArrayList<>();
	private final ArrayList<Pattern>  skipEmail  = new ArrayList<>();
	private final ArrayList<Pattern>  includeEmail  = new ArrayList<>();
	private final ArrayList<Pattern>  skipUsername  = new ArrayList<>();
	private final ArrayList<Pattern>  includeUsername  = new ArrayList<>();

	private final HashSet<String>     existingTypes = new HashSet<>();
	private final Map<String, String> parameters    = new HashMap<>();

	// Variables changing per parameter or properties
	private double                                  myApiVersion;
	private String                                  skipItems;
	private HashMap<String, DescribeMetadataObject> describeMetadataObjectsMap;
	String                                          authEndPoint = "";
	private long                                    timeStart;
	private MetadataConnection                      srcMetadataConnection;
	private String                                  srcUrl;
	private String                                  srcUser;
	private String                                  srcPwd;
	// added for database handling
	//	private String            dbFilename;
	private String            targetDir = "";
	private Loglevel          loglevel;
	private OperationMode     mode;
	private PartnerConnection srcPartnerConnection;

	private boolean includeChangeData = false;
	private boolean downloadData      = false;
	private boolean gitCommit         = false;
	private int		maxItemsInPackage = MAXITEMSINPACKAGE;
	private int itemCount;

	// Constructor that gets all settings as map
	public PackageBuilder(final Map<String, String> parameters) {
		this.parameters.putAll(parameters);

	}

	public void run() throws RemoteException, Exception {

		// set loglevel based on parameters
		this.loglevel = ("verbose".equals(this.parameters.get("loglevel"))) ? Loglevel.NORMAL : Loglevel.BRIEF;

		// Check what to do based on parameters
		this.includeChangeData = this.isParamTrue(PackageBuilderCommandLine.INCLUDECHANGEDATA_LONGNAME);
		this.downloadData = this.isParamTrue(PackageBuilderCommandLine.DOWNLOAD_LONGNAME);
		this.gitCommit = this.isParamTrue(PackageBuilderCommandLine.GITCOMMIT_LONGNAME);

		this.maxItemsInPackage = Integer.valueOf(this.parameters.get(PackageBuilderCommandLine.MAXITEMS_LONGNAME));

		// initialize inventory - it will be used in both types of operations
		// (connect to org or run local)

		final HashMap<String, ArrayList<InventoryItem>> inventory = new HashMap<>();

		this.myApiVersion = Double.parseDouble(this.parameters.get(PackageBuilderCommandLine.APIVERSION_LONGNAME));
		this.targetDir = Utils.checkPathSlash(Utils.checkPathSlash(this.parameters.get(PackageBuilderCommandLine.DESTINATION_LONGNAME)));
		//		this.metaSourceDownloadDir = Utils.checkPathSlash(Utils.checkPathSlash(this.parameters.get(PackageBuilderCommandLine.BASEDIRECTORY_LONGNAME)));

		// handling for building a package from a directory
		// if we have a base directory set, ignore everything else and generate
		// from the directory

		if (this.parameters.get(PackageBuilderCommandLine.BASEDIRECTORY_LONGNAME) != null) {
			this.generateInventoryFromDir(inventory);
			this.mode = OperationMode.DIR;
		} else {
			this.generateInventoryFromOrg(inventory);
			this.mode = OperationMode.ORG;
		}
		this.generatePackageXML(inventory);

	}

	private HashMap<String, ArrayList<InventoryItem>>[] createPackageFiles(final HashMap<String, ArrayList<InventoryItem>> myCompleteInventory) {

		final ArrayList<HashMap<String, ArrayList<InventoryItem>>> files = new ArrayList<HashMap<String, ArrayList<InventoryItem>>>();
		HashMap<String, ArrayList<InventoryItem>> lastFile = null;
		
		// first, check how many items we have
		// if no more than one file, con't bother with any special treatment

		if (itemCount > maxItemsInPackage) {

			// we'll get more than one package, need to check if we need special treatment
			// for profiles/permission sets
			
			// first, check if we have any items that contain permissions
			// if we do, put them in the first package, then add all items that
			// add permissions

			boolean exportingPermissions = false;
			for (String mdType : SPECIALTREATMENTPERMISSIONTYPES) {
				if (myCompleteInventory.containsKey(mdType)) {
					exportingPermissions = true;
					break;
				}
			}

			// if we are exporting permission, check if we're bringing anything that needs to go into the permissions file along

			boolean exportingPermissionsDependentItems = false;
			for (String mdType : ITEMSTOINCLUDEWITHPROFILESPERMSETS) {
				if (myCompleteInventory.containsKey(mdType)) {
					exportingPermissionsDependentItems = true;
					break;
				}
			}

			// so now, if we're actually exporting permissions and dependent items, we need to process them first
			// else, just continue as normal

			if (exportingPermissions && exportingPermissionsDependentItems) {
				// try to add all the special treatment types first and see if we get one file back or more
				// if 1, process the rest as normal
				// if not, warn that security items (Profiles/PermissionSets) may be incomplete, warn and 
				// process everything else

				this.log("Asked to export permission settings (Profiles/PermSets). Will now try to bundle them and dependent items in one package.",
						Loglevel.NORMAL);

				// create inventory with all the special treatment items

				final HashMap<String, ArrayList<InventoryItem>> mySpecialTreatmentInventory = new HashMap<String, ArrayList<InventoryItem>>();

				for (final String mdType : ArrayUtils.addAll(SPECIALTREATMENTPERMISSIONTYPES, ITEMSTOINCLUDEWITHPROFILESPERMSETS)) {
					if (myCompleteInventory.get(mdType) != null && myCompleteInventory.get(mdType).size() >0) {
						mySpecialTreatmentInventory.put(mdType, myCompleteInventory.get(mdType));
						myCompleteInventory.remove(mdType);
					}
				}

				// now create files with just the special treatment types

				files.addAll(breakInventoryIntoFiles(mySpecialTreatmentInventory, null));

				// check if we got 1 file only, log if not

				if (files.size() != 1) {
					this.log("Permission settings (Profiles/PermSets) with dependent items over " + maxItemsInPackage + 
							" items - won't fit in one package. Please note that contents of profiles/permission sets may be incomplete.", Loglevel.BRIEF);
				}
				
				// get last file from files to continue adding to it
				
				lastFile = files.get(files.size()-1);
				
			}
		}
		// now add all the rest of the inventory

		files.addAll(breakInventoryIntoFiles(myCompleteInventory, lastFile));

		// generate return array

		@SuppressWarnings("unchecked")
		HashMap<String, ArrayList<InventoryItem>>[] retval = new HashMap[files.size()];

		retval = files.toArray(retval);

		return files.toArray(retval);
	}

	// need to break the inventory into packages of less than MAXITEMS items
	// also need to ensure that if we have profiles or permission sets, they ideally go in the same package as:
	// ApexClass, CustomApplication, CustomField, CustomObject, CustomTab, ExternalDataSource, RecordType, ApexPage

	private ArrayList<HashMap<String, ArrayList<InventoryItem>>> breakInventoryIntoFiles(
			final HashMap<String, ArrayList<InventoryItem>> myFile, HashMap<String, ArrayList<InventoryItem>> fileToContinue) {

		final ArrayList<HashMap<String, ArrayList<InventoryItem>>> files = new ArrayList<>();
		int fileIndex = 1;
		int fileCount = 0;
		
		HashMap<String, ArrayList<InventoryItem>> currentFile;
		if (fileToContinue == null) {
			currentFile = new HashMap<>();
		} else {
			currentFile = fileToContinue;
			fileCount = countItemsInFile(currentFile);
		}

		for (final String mdType : myFile.keySet()) {
			final ArrayList<InventoryItem> mdTypeList = myFile.get(mdType);
			final int mdTypeSize = mdTypeList.size();

			// do we have room in this file for the
			if ((fileCount + mdTypeSize) > maxItemsInPackage) {
				// no, we don't, finish file off, add to list, create new and
				// add to that

				this.log("Type " + mdType + ", won't fit into this file - #items: " + mdTypeSize + ".",
						Loglevel.NORMAL);

				//put part of this type into this file

				ArrayList<InventoryItem> mdTypeListPartial = new ArrayList<InventoryItem>(mdTypeList.subList(0, maxItemsInPackage - fileCount));
				currentFile.put(mdType, mdTypeListPartial);
				mdTypeList.removeAll(mdTypeListPartial);
				fileCount += mdTypeListPartial.size();
				this.log(
						"Adding type: " + mdType + "(" + mdTypeListPartial.size() + " items) to file " + fileIndex + ", total count now: "
								+ fileCount,
								Loglevel.NORMAL);
				files.add(currentFile);

				// finish and start new file

				this.log("Finished composing file " + fileIndex + ", total count: " + fileCount + "items.",
						Loglevel.NORMAL);
				currentFile = new HashMap<>();
				fileCount = 0;
				fileIndex++;
			}
			// now add this type to this file and continue
			// but need to check that this type isn't more than maxItems
			// if yes, then split this type into multiple pieces

			while (mdTypeList.size() > maxItemsInPackage) {
				// too much even for a single file just with that, 
				// break up into multiple files

				ArrayList<InventoryItem> mdTypeListPartial = new ArrayList<InventoryItem>(mdTypeList.subList(0, maxItemsInPackage));
				currentFile.put(mdType, mdTypeListPartial);
				fileCount += mdTypeListPartial.size();
				files.add(currentFile);
				currentFile = new HashMap<>();
				mdTypeList.removeAll(mdTypeListPartial);
				this.log(
						"Adding type: " + mdType + "(" + mdTypeListPartial.size() + " items) to file " + fileIndex + ", total count now: "
								+ fileCount,
								Loglevel.NORMAL);
				this.log("Finished composing file " + fileIndex + ", total count: " + fileCount + "items.",
						Loglevel.NORMAL);
				fileCount = 0;
				fileIndex++;

			}

			currentFile.put(mdType, mdTypeList);
			fileCount += mdTypeList.size();
			this.log(
					"Adding type: " + mdType + "(" + mdTypeList.size() + " items) to file " + fileIndex + ", total count now: "
							+ fileCount,
							Loglevel.NORMAL);
		}

		// finish off any last file
		files.add(currentFile);
		this.log("Finished composing file " + fileIndex + ", total count: " + fileCount + "items.", Loglevel.NORMAL);

		return files;
	}	

	private int countItemsInFile(HashMap<String, ArrayList<InventoryItem>> fileToCount) {
		int retval = 0;
		
		for (String mdType : fileToCount.keySet()) {
			if (fileToCount.get(mdType) != null) {
				retval += fileToCount.get(mdType).size();
			}
		}
		
		return retval;
	}

	private void endTiming() {
		final long end = System.currentTimeMillis();
		final long diff = ((end - this.timeStart));
		final String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(diff),
				TimeUnit.MILLISECONDS.toMinutes(diff) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(diff)),
				TimeUnit.MILLISECONDS.toSeconds(diff)
				- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff)));
		this.log("Duration: " + hms, Loglevel.NORMAL);
	}

	private HashMap<String, InventoryItem> fetchMetadataType(final String metadataType)
			throws RemoteException, Exception {
		this.startTiming();
		// logPartialLine(", level);
		final HashMap<String, InventoryItem> packageInventoryList = new HashMap<>();
		try {

			final ArrayList<FileProperties> foldersToProcess = new ArrayList<>();
			boolean isFolder = false;
			// check if what we have here is in folders

			final DescribeMetadataObject obj = this.describeMetadataObjectsMap.get(metadataType);
			if ((obj != null) && (obj.getInFolder() == true)) {
				isFolder = true;
				this.log(metadataType + " is stored in folders. Getting folder list.", Loglevel.VERBOSE);
				final ListMetadataQuery query = new ListMetadataQuery();
				// stupid hack for emailtemplate folder name
				String type;
				if (metadataType.toLowerCase().equals("emailtemplate")) {
					type = "EmailFolder";
				} else {
					type = metadataType + "Folder";
				}

				query.setType(type);
				final FileProperties[] srcMd = this.srcMetadataConnection.listMetadata(
						new ListMetadataQuery[] { query },
						this.myApiVersion);
				if ((srcMd != null) && (srcMd.length > 0)) {
					for (final FileProperties n : srcMd) {
						foldersToProcess.add(n);
						// add folder to final inventory
						packageInventoryList.put(n.getFullName(), new InventoryItem(n.getFullName(), n, true));
					}
				}
			}

			final Iterator<FileProperties> folder = foldersToProcess.iterator();

			final HashMap<String, ArrayList<FileProperties>> metadataMap = new HashMap<>();

			int itemCount = 0;
			// int thisItemCount = 0;

			do {
				FileProperties folderProperties = null;
				final ListMetadataQuery query = new ListMetadataQuery();

				query.setType(metadataType);
				String folderName = null;
				if (isFolder && folder.hasNext()) {
					folderProperties = folder.next();
					folderName = folderProperties.getFullName();
					query.setFolder(folderName);
				}

				// Assuming that the SOAP binding has already been established.

				// generate full metadata inventory

				final FileProperties[] srcMd = this.srcMetadataConnection.listMetadata(
						new ListMetadataQuery[] { query },
						this.myApiVersion);
				itemCount += srcMd.length;
				// thisItemCount = srcMd.length;
				if (folderName != null) {
					this.log("Processing folder: " + folderName + " " + " items: " + srcMd.length + "\tCurrent total: "
							+ itemCount, Loglevel.NORMAL);
					// fetch folders themselves
					// packageMap.add(folderName);
					final ArrayList<FileProperties> filenameList = new ArrayList<>();
					filenameList.add(folderProperties);
					metadataMap.put(folderProperties.getFileName(), filenameList);
					itemCount++;
				}

				if (itemCount > 0) {
					this.existingTypes.add(metadataType);
				}

				if (((srcMd != null) && (srcMd.length > 0))
						|| metadataType.equals("StandardValueSet")) { // hack
					// alert -
					// currently
					// listMetadata
					// call
					// will
					// return
					// nothing
					// for
					// StandardValueSet
					if (!metadataType.equals("StandardValueSet")) {
						for (final FileProperties n : srcMd) {
							if ((n.getNamespacePrefix() == null) || n.getNamespacePrefix().equals("")) {
								// packageMap.add(n.getFullName());
								packageInventoryList.put(n.getFullName(), new InventoryItem(n.getFullName(), n));
							}

						}
					} else {
						for (final String s : PackageBuilder.STANDARDVALUETYPESARRAY) {
							// packageMap.add(s);
							packageInventoryList.put(s, new InventoryItem(s, null));
						}
					}

				} else {
					if (!isFolder) {
						this.log("No items of this type, skipping...", Loglevel.VERBOSE);
						break;
					}
					if (!folder.hasNext()) {
						break;
					}
				}
				if ((isFolder == true) && folder.hasNext()) {
					continue;
				}

			} while (folder.hasNext());

		} catch (final ConnectionException ce) {
			// ce.printStackTrace();
			this.log("\nException processing: " + metadataType, Loglevel.BRIEF);
			this.log("Error: " + ce.getMessage(), Loglevel.BRIEF);
		}

		this.endTiming();
		return packageInventoryList;
	}

	// inventory is a list of lists
	// keys are the metadata types
	// e.g. flow, customobject, etc.

	private Collection<String> generateFileList(final File node, final String baseDir) {

		final Collection<String> retval = new ArrayList<>();
		// add file only
		if (node.isFile()) {
			retval.add(this.generateZipEntry(node.getAbsoluteFile().toString(), baseDir));
			// retval.add(baseDir + "/" + node.getAbsoluteFile().toString());
			// retval.add(node.getName());
		} else if (node.isDirectory()) {
			final String[] subNote = node.list();
			for (final String filename : subNote) {
				retval.addAll(this.generateFileList(new File(node, filename), baseDir));
			}
		}
		return retval;
	}

	private void generateInventoryFromDir(final HashMap<String, ArrayList<InventoryItem>> inventory)
			throws IOException {
		final String basedir = this.parameters.get(PackageBuilderCommandLine.BASEDIRECTORY_LONGNAME);

		// check if the directory is valid

		final HashMap<String, HashSet<InventoryItem>> myInventory = new HashMap<>();

		if (!Utils.checkIsDirectory(basedir)) {
			// log error and exit

			this.log("Base directory parameter provided: " + basedir
					+ " invalid or is not a directory, cannot continue.",
					Loglevel.BRIEF);
			System.exit(1);
		}

		// directory valid - enumerate and generate inventory

		final Collection<String> filelist = this.generateFileList(new File(basedir), basedir);

		// so now we have a list of folders/files
		// need to convert to inventory for package.xml generator

		for (final String s : filelist) {
			// ignore -meta.xml

			if (s.contains("-meta.xml")) {
				continue;
			}

			// split into main folder + rest

			try {

				// ignore anything which doesn't have a path separator (i.e. not
				// a folder)

				final int separatorLocation = s.indexOf(File.separator);

				if (separatorLocation == -1) {
					this.log("No folder in: " + s + ",skipping...", Loglevel.VERBOSE);
					continue;
				}

				final String foldername = s.substring(0, separatorLocation);
				String filename = s.substring(separatorLocation + 1);

				// split off file name suffix

				filename = filename.substring(0, filename.lastIndexOf("."));

				// ignore anything starting with a .

				if (filename.startsWith(".")) {
					continue;
				}

				// figure out based on foldername what the metadatatype is

				String mdType = Utils.getMetadataTypeForDir(foldername);

				// if not found, try lowercase
				if (mdType == null) {
					mdType = Utils.getMetadataTypeForDir(foldername.toLowerCase());
				}

				if (mdType == null) {
					this.log("Couldn't find type mapping for item : " + mdType + " : " + filename + ", original path: "
							+ s
							+ ",skipping...", Loglevel.BRIEF);
					continue;
				}

				// generate inventory entry

				HashSet<InventoryItem> typeInventory = myInventory.get(mdType);
				if (typeInventory == null) {
					typeInventory = new HashSet<>();
					myInventory.put(mdType, typeInventory);
					System.out.println("Created inventory record for type: " + mdType);
				}

				// check if there is a folder in the filename and it's aura -
				// then we need to leave the folder, skip the item

				if (filename.contains("/") && mdType.equals("AuraDefinitionBundle")) {
					final String subFoldername = filename.substring(0, filename.indexOf("/"));
					typeInventory.add(new InventoryItem(subFoldername, null));
					this.log("Added: " + mdType + " : " + subFoldername + ", to inventory, original path: " + s,
							Loglevel.NORMAL);
					continue;
				}

				// check if there is a folder in the filename - then we need to
				// add the folder as well

				if (filename.contains("/")) {
					final String subFoldername = filename.substring(0, filename.indexOf("/"));
					typeInventory.add(new InventoryItem(subFoldername, null));
				}

				typeInventory.add(new InventoryItem(filename, null));
				this.log("Added: " + mdType + " : " + filename + ", to inventory, original path: " + s,
						Loglevel.NORMAL);

				// convert myinventory to the right return type

			} catch (final Exception e) {
				// Something bad happened
				System.out.println("Something bad happened on file: " + s + ", skipping...");
			}

		}
		for (final String myMdType : myInventory.keySet()) {
			final ArrayList<InventoryItem> invType = new ArrayList<>();
			invType.addAll(myInventory.get(myMdType));
			inventory.put(myMdType, invType);
		}

		//

	}

	/*
	 *
	 * this method will populate username (Salesforce user name in email format)
	 * and user email fields on the inventoryItems for use when outputting
	 * change telemetry
	 *
	 */

	private void generateInventoryFromOrg(final HashMap<String, ArrayList<InventoryItem>> inventory)
			throws RemoteException, Exception {

		// Initialize the metadata connection we're going to need

		this.srcUrl = this.parameters.get(PackageBuilderCommandLine.SERVERURL_LONGNAME) + PackageBuilder.URLBASE + this.myApiVersion;
		this.srcUser = this.parameters.get(PackageBuilderCommandLine.USERNAME_LONGNAME);
		this.srcPwd = this.parameters.get(PackageBuilderCommandLine.PASSWORD_LONGNAME);
		this.skipItems = this.parameters.get(PackageBuilderCommandLine.SKIPPATTERNS_LONGNAME);
		// Make a login call to source
		this.srcMetadataConnection = LoginUtil.mdLogin(this.srcUrl, this.srcUser, this.srcPwd);

		// Figure out what we are going to be fetching

		final ArrayList<String> workToDo = new ArrayList<>(this.getTypesToFetch());
		Collections.sort(workToDo);

		this.log("Will fetch: " + String.join(", ", workToDo) + " from: " + this.srcUrl, Loglevel.BRIEF);
		this.log("Using user: " + this.srcUser + " skipping: " + this.skipItems, Loglevel.NORMAL);

		System.out.println("target directory: " + this.targetDir);

		Utils.checkDir(this.targetDir);

		final Iterator<String> i = workToDo.iterator();
		int counter = 0;
		while (i.hasNext()) {
			counter++;
			final String mdType = i.next();
			if (this.loglevel.getLevel() > Loglevel.BRIEF.getLevel()) {
				this.log("*********************************************", Loglevel.NORMAL);
				this.log("Processing type " + counter + " out of " + workToDo.size() + ": " + mdType, Loglevel.NORMAL);
				this.log("*********************************************", Loglevel.NORMAL);
			} else if (this.loglevel == Loglevel.BRIEF) {
				this.logPartialLine("Processing type " + counter + " out of " + workToDo.size() + ": " + mdType,
						Loglevel.BRIEF);
			}

			final ArrayList<InventoryItem> mdTypeItemList = new ArrayList<>(
					this.fetchMetadataType(mdType).values());
			Collections.sort(mdTypeItemList, (o1, o2) -> o1.itemName.compareTo(o2.itemName));
			inventory.put(mdType, mdTypeItemList);

			if (this.loglevel.getLevel() > Loglevel.BRIEF.getLevel()) {
				this.log("---------------------------------------------", Loglevel.NORMAL);
				this.log("Finished processing: " + mdType, Loglevel.NORMAL);
				this.log("---------------------------------------------", Loglevel.NORMAL);
			} else if (this.loglevel == Loglevel.BRIEF) {
				this.log(" items: " + mdTypeItemList.size(), Loglevel.BRIEF);
			}
		}

	}

	private HashMap<String, ArrayList<InventoryItem>>[] generatePackageXML(
			final HashMap<String, ArrayList<InventoryItem>> inventory)
					throws Exception {

		int itemCount = 0;
		int skipCount = 0;

		final HashMap<String, ArrayList<InventoryItem>> myFile = new HashMap<>();

		final ArrayList<String> types = new ArrayList<>();
		types.addAll(inventory.keySet());
		Collections.sort(types);

		for (final String mdType : types) {

			// check if we have any items in this category

			final ArrayList<InventoryItem> items = inventory.get(mdType);
			if (items.size() < 1) {
				continue;
			}

			myFile.put(mdType, new ArrayList<InventoryItem>());

			Collections.sort(items, (o1, o2) -> o1.itemName.compareTo(o2.itemName));
			for (final InventoryItem item : items) {
				myFile.get(mdType).add(item);
				itemCount++;
			}
		}

		// if we're writing change telemetry into the package.xml, or skipping/including by email, need to get
		// user emails now
		if (includeChangeData || 
				this.parameters.containsKey(PackageBuilderCommandLine.SKIPEMAIL_LONGNAME) ||
				this.parameters.containsKey(PackageBuilderCommandLine.INCLUDEEMAIL_LONGNAME)) {
			this.populateUserEmails(myFile);
		}

		// now check if anything we have needs to be skipped

		skipCount = this.handleSkippingItems(myFile);

		// now break it up into files if needed

		final HashMap<String, ArrayList<InventoryItem>>[] files = this.createPackageFiles(myFile);




		for (int i = 1; i <= files.length; i++) {
			if (files.length == 1) {
				this.writePackageXmlFile(files[i-1], "package.xml", i);
			} else {
				this.writePackageXmlFile(files[i-1], "package." + i + ".xml", i);
			}
		}

		final ArrayList<String> typesFound = new ArrayList<>(this.existingTypes);
		Collections.sort(typesFound);

		this.log("Types found in org: " + typesFound.toString(), Loglevel.BRIEF);

		this.log("Total items in package.xml: " + (itemCount - skipCount), Loglevel.BRIEF);
		this.log("Total items overall: " + itemCount + ", items skipped: " + skipCount
				+ " (excludes count of items in type where entire type was skipped)",
				Loglevel.NORMAL);

		return files;

	}

	private String generateZipEntry(final String file, final String sourceFolder) {
		final int indexOfSourceFolder = file.lastIndexOf(sourceFolder);
		return file.substring(indexOfSourceFolder + sourceFolder.length() + 1, file.length());
	}

	//	private InventoryDatabase getDatabase(final String orgId) {
	//
	//		InventoryDatabase newDatabase = null;
	//		final boolean databaseFileExists = false;
	//
	//		// TODO find a database if it exists
	//
	//		// placeholder for loading database file if it exists
	//		if (databaseFileExists) {
	//			newDatabase = InventoryDatabase.readDatabaseFromFile(this.dbFilename);
	//			// TODO confirm that the orgid matches
	//		} else {
	//			newDatabase = new InventoryDatabase(orgId);
	//		}
	//		return newDatabase;
	//	}

	private String getOrgIdentifier() {
		// TODO Auto-generated method stub
		return "myOrg";
	}

	private HashSet<String> getTypesToFetch() throws ConnectionException {

		final HashSet<String> typesToFetch = new HashSet<>();
		final String mdTypesToExamine = this.parameters.get(PackageBuilderCommandLine.METADATAITEMS_LONGNAME);

		// get a describe

		final DescribeMetadataResult dmr = this.srcMetadataConnection.describeMetadata(this.myApiVersion);
		this.describeMetadataObjectsMap = new HashMap<>();

		for (final DescribeMetadataObject obj : dmr.getMetadataObjects()) {
			this.describeMetadataObjectsMap.put(obj.getXmlName(), obj);
		}

		// if a metadataitems parameter was provided, we use that

		if (mdTypesToExamine != null) {
			for (final String s : mdTypesToExamine.split(",")) {
				typesToFetch.add(s.trim());
			}
		} else {
			// no directions on what to fetch - go get everything
			this.log("No metadataitems (-mi) parameter found, will inventory the whole org", Loglevel.BRIEF);

			for (final String obj : this.describeMetadataObjectsMap.keySet()) {
				typesToFetch.add(obj.trim());
			}

			// now add the list of types to be added manually

			for (String manualType : ADDITIONALTYPESTOADD) {
				typesToFetch.add(manualType.trim());
			}

			// check API version - in 45+, remove FlowDefinition

			if (Double.valueOf(this.parameters.get(PackageBuilderCommandLine.APIVERSION_LONGNAME)) >= 45) {
				typesToFetch.remove("FlowDefinition");
			}

		}
		return typesToFetch;
	}

	// removes an item (i) from its mdTypeList if the item name/username/lastmodifiedby user email matches/doesn't
	// match the skip/include pattern
	// returns 1 if the item was skipped else 0

	private int checkItemAgainstPattern(Pattern p, InventoryItem i, String mdType, 
			ArrayList<InventoryItem> mdTypeList, boolean isSkipPattern, PatternField patternField) {
		String fieldToCheck = "";
		String mdTypeFullName = mdType + ":" + i.itemName;
		if (patternField == PatternField.EMAIL) {
			fieldToCheck = i.lastModifiedByEmail;
		} else if (patternField == PatternField.USERNAME) {
			fieldToCheck = i.getLastModifiedByName();
		} else if (patternField == PatternField.ITEMNAME) {
			fieldToCheck = mdTypeFullName;
		}
		final Matcher m = p.matcher(fieldToCheck);
		if (m.matches() && isSkipPattern) {
			this.log("Skip pattern: " + p.pattern() + " matches the metadata item: " + mdTypeFullName +
					(patternField != PatternField.ITEMNAME ? "(" + fieldToCheck + ")" : "") 
					+ ", item will be skipped.", Loglevel.NORMAL);
			//mdTypeList.remove(i);
			return 1;
		} else if (!m.matches() && !isSkipPattern) {
			this.log("Include pattern: " + p.pattern() + " doesn't match the metadata item: " + mdTypeFullName +
					(patternField != PatternField.ITEMNAME ? "(" + fieldToCheck + ")" : "") 
					+ ", item will be skipped.", Loglevel.NORMAL);
			//mdTypeList.remove(i);
			return 1;
		}
		return 0;


	}

	/* function to handle removing items from the result
	 * 
	 * will handle 	-sp (skipPatterns), -ip (includepatterns), -su (skipusers) -iu (includeusers)
	 * 				-se (skipEmail) -ie (includeemail) parameters to filter the list
	 */

	private int handleSkippingItems(final HashMap<String, ArrayList<InventoryItem>> myFile) {

		int skipCount = 0;
		itemCount = 0;

		SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");

		// Initiate patterns array

		initializePatternArray(this.parameters.get(PackageBuilderCommandLine.SKIPPATTERNS_LONGNAME), this.skipPatterns);
		initializePatternArray(this.parameters.get(PackageBuilderCommandLine.INCLUDEPATTERNS_LONGNAME), this.includePatterns);
		initializePatternArray(this.parameters.get(PackageBuilderCommandLine.SKIPEMAIL_LONGNAME), this.skipEmail);
		initializePatternArray(this.parameters.get(PackageBuilderCommandLine.INCLUDEEMAIL_LONGNAME), this.includeEmail);
		initializePatternArray(this.parameters.get(PackageBuilderCommandLine.SKIPUSERNAME_LONGNAME), this.skipUsername);
		initializePatternArray(this.parameters.get(PackageBuilderCommandLine.INCLUDEUSERNAME_LONGNAME), this.includeUsername);

		// initialize date ranges, if any

		String fromDateString = this.parameters.get(PackageBuilderCommandLine.FROMDATE_LONGNAME);
		String toDateString = this.parameters.get(PackageBuilderCommandLine.TODATE_LONGNAME);
		Date fromDate = null;
		Date toDate = null;

		if (fromDateString != null && fromDateString.length() >= 8) {
			try {
				fromDate = Date.valueOf(fromDateString);
			} 
			catch (IllegalArgumentException e) {
				this.log("FromDate value: " + fromDateString + " cannot be parsed to a proper date. Required format: YYYY-[M]M-[D]D. Continuing without FromDate parameter.", Loglevel.BRIEF);
			}
		}

		if (toDateString != null && toDateString.length() >= 8) {
			try {
				toDate = Date.valueOf(toDateString);
			} 
			catch (IllegalArgumentException e) {
				this.log("ToDate value: " + toDateString + " cannot be parsed to a proper date. Required format: YYYY-[M]M-[D]D. Continuing without ToDate parameter.", Loglevel.BRIEF);
			}
		}

		for (final String mdType : myFile.keySet()) {
			final ArrayList<InventoryItem> items = myFile.get(mdType);
			for (Iterator<InventoryItem> i = items.iterator(); i.hasNext();) {
				final InventoryItem mdItem = i.next();
				boolean itemSkipped = false;

				for (Pattern p :  this.skipPatterns) {
					if (checkItemAgainstPattern(p, mdItem, mdType, myFile.get(mdType), true, PatternField.ITEMNAME) == 1) {
						// item was skipped
						skipCount++;
						itemSkipped = true; 
						break;
					}
				}
				if (!itemSkipped) {
					for (Pattern p :  this.includePatterns) {
						if (checkItemAgainstPattern(p, mdItem, mdType, myFile.get(mdType), false, PatternField.ITEMNAME) == 1) {
							// item was skipped
							skipCount++;
							itemSkipped = true; 
							break;
						}
					}
				}
				if (!itemSkipped) {
					for (Pattern p :  this.skipUsername) {
						if (checkItemAgainstPattern(p, mdItem, mdType, myFile.get(mdType), true, PatternField.USERNAME) == 1) {
							// item was skipped
							skipCount++;
							itemSkipped = true; 
							break;
						}
					}
				}
				if (!itemSkipped) {
					for (Pattern p :  this.includeUsername) {
						if (checkItemAgainstPattern(p, mdItem, mdType, myFile.get(mdType), false, PatternField.USERNAME) == 1) {
							// item was skipped
							skipCount++;
							itemSkipped = true; 
							break;
						}
					}
				}
				if (!itemSkipped) {
					for (Pattern p :  this.skipEmail) {
						if (checkItemAgainstPattern(p, mdItem, mdType, myFile.get(mdType), true, PatternField.EMAIL) == 1) {
							// item was skipped
							skipCount++;
							itemSkipped = true; 
							break;
						}
					}
				}
				if (!itemSkipped) {
					for (Pattern p :  this.includeEmail) {
						if (checkItemAgainstPattern(p, mdItem, mdType, myFile.get(mdType), false, PatternField.EMAIL) == 1) {
							// item was skipped
							skipCount++;
							itemSkipped = true; 
							break;
						}
					}
				}

				// check against dates now, if defined
				if (!itemSkipped) {
					if (fromDate != null) {
						Calendar itemLastModified = mdItem.getLastModifiedDate();
						if (itemLastModified == null || fromDate.after(itemLastModified.getTime())) {
							skipCount++;
							itemSkipped = true; 
							this.log("Item: " + mdItem.getFullName() + " last modified (" + (itemLastModified == null || itemLastModified.getTimeInMillis() == 0 ? "null" : format1.format(itemLastModified.getTime())) + ") before provided FromDate (" 
									+ fromDateString + "), item will be skipped.", Loglevel.NORMAL);
						}
					}
				}
				if (!itemSkipped) {
					if (toDate != null) {
						Calendar itemLastModified = mdItem.getLastModifiedDate();
						if (itemLastModified == null || toDate.before(itemLastModified.getTime())) {
							skipCount++;
							itemSkipped = true; 
							this.log("Item: " + mdItem.getFullName() + " last modified (" + (itemLastModified == null || itemLastModified.getTimeInMillis() == 0 ? "null" : format1.format(itemLastModified.getTime())) + ") after provided ToDate (" 
									+ toDateString + "), item will be skipped.", Loglevel.NORMAL);
						}
					}
				}

				if (itemSkipped) {
					i.remove();
				} else {
					itemCount++;
				}

			}
		}

		//        for (final String mdType : myFile.keySet()) {
		//            // first, check if any of the patterns match the whole type
		//            String mdTypeFullName = mdType + ":";
		//            for (final Pattern p : this.skipPatterns) {
		//
		//                final Matcher m = p.matcher(mdTypeFullName);
		//                if (m.matches()) {
		//                    this.log("Skip pattern: " + p.pattern() + " matches the metadata type: " + mdTypeFullName
		//                            + ", entire type will be skipped.", Loglevel.NORMAL);
		//
		//                    // remove the whole key from the file
		//
		//                    skipCount += myFile.get(mdType).size();
		//
		//                    myFile.remove(mdType);
		//                    continue;
		//
		//                }
		//            }
		//
		//            final ArrayList<InventoryItem> items = myFile.get(mdType);
		//            Collections.sort(items, (o1, o2) -> o1.itemName.compareTo(o2.itemName));
		//            for (int i = 0; i < items.size(); i++) {
		//                mdTypeFullName = mdType + ":" + items.get(i).itemName;
		//                for (final Pattern p : this.skipPatterns) {
		//                    final Matcher m = p.matcher(mdTypeFullName);
		//                    if (m.matches()) {
		//                        this.log("Skip pattern: " + p.pattern() + " matches the metadata item: " + mdTypeFullName
		//                                + ", item will be skipped.", Loglevel.NORMAL);
		//                        items.remove(i);
		//                        skipCount++;
		//                    }
		//                }
		//            }
		//        }



		// then check all skip patterns


		return skipCount;
	}

	private void initializePatternArray(String parameter, ArrayList<Pattern> patternArray) {
		if (parameter != null) {
			for (final String p : parameter.split(",")) {
				try {
					patternArray.add(Pattern.compile(p));
				} catch (final PatternSyntaxException e) {
					System.out.println("Tried to compile pattern: " + p + " but got exception: ");
					e.printStackTrace();
				}
			}
		}
	}

	private boolean isParamTrue(final String paramName) {
		return "true".equals(this.parameters.get(paramName));
	}

	private void log(final String logText, final Loglevel level) {
		if ((this.loglevel == null) || (level.getLevel() <= this.loglevel.getLevel())) {
			System.out.println(logText);
		}
	}

	private void logPartialLine(final String logText, final Loglevel level) {
		if (level.getLevel() <= this.loglevel.getLevel()) {
			System.out.print(logText);
		}
	}

	private void populateUserEmails(final HashMap<String, ArrayList<InventoryItem>> myFile) throws ConnectionException {

		final Set<String> userIDs = new HashSet<>();

		for (final String mdName : myFile.keySet()) {
			for (final InventoryItem i : myFile.get(mdName)) {
				userIDs.add(i.getLastModifiedById());
			}
		}

		// remove the null ID if it appears

		userIDs.remove(null);

		// now call salesforce to get the emails and usernames

		final HashMap<String, HashMap<String, String>> usersBySalesforceID = new HashMap<>();

		// login
		this.srcPartnerConnection = LoginUtil.soapLogin(this.srcUrl, this.srcUser, this.srcPwd);

		// build the query

		final String queryStart = "SELECT Id, Name, Username, Email FROM User WHERE ID IN(";
		final String queryEnd = ")";
		final String[] myIDs = userIDs.toArray(new String[userIDs.size()]);
		final String queryMid = "'" + String.join("','", myIDs) + "'";

		final String query = queryStart + queryMid + queryEnd;

		this.log("Looking for emails for " + userIDs.size() + " users.", Loglevel.BRIEF);
		this.log("Query: " + query, Loglevel.NORMAL);

		// run the query

		QueryResult qResult = this.srcPartnerConnection.query(query);

		boolean done = false;
		if (qResult.getSize() > 0) {
			this.log("Logged-in user can see a total of " + qResult.getSize() + " contact records.", Loglevel.NORMAL);
			while (!done) {
				final SObject[] records = qResult.getRecords();
				for (final SObject o : records) {
					final HashMap<String, String> userMap = new HashMap<>();
					userMap.put("Name", (String) o.getField("Name"));
					userMap.put("Email", (String) o.getField("Email"));
					userMap.put("Username", (String) o.getField("Username"));
					usersBySalesforceID.put((String) o.getField("Id"), userMap);
				}
				if (qResult.isDone()) {
					done = true;
				} else {
					qResult = this.srcPartnerConnection.queryMore(qResult.getQueryLocator());
				}
			}
		} else {
			System.out.println("No records found.");
		}

		// now run through the InventoryItems again and update user data

		for (final String mdName : myFile.keySet()) {
			for (final InventoryItem i : myFile.get(mdName)) {
				final HashMap<String, String> userMap = usersBySalesforceID.get(i.getLastModifiedById());
				if (userMap != null) {
					i.lastModifiedByEmail = userMap.get("Email");
					i.lastModifiedByUsername = userMap.get("Username");
				} else {
					i.lastModifiedByEmail = "null";
					i.lastModifiedByUsername = "null";
				}

			}
		}

	}

	private void startTiming() {
		this.timeStart = System.currentTimeMillis();
	}

	/*
	 * private void writePackageXmlFile(HashMap<String, ArrayList<String>>
	 * theMap, String filename)
	 *
	 * this method will generate a package.xml file based on a HashMap<String,
	 * ArrayList<String>> and a filename used to cater for big orgs that burst
	 * limits input hashmap structure: ApexClass -> [Class1,Class2] ApexTrigger
	 * -> [Trigger1,Trigger2]
	 *
	 * expecting only to see types which are populated (i.e. have at least 1
	 * item)
	 *
	 * Expected output: <types> <name>ApexClass</name> <members>Class1</members>
	 * <members>Class2</members> </types> <types> <name>ApexTrigger</name>
	 * <members>Trigger1</members> <members>Trigger2</members> </types>
	 *
	 */
	private void writePackageXmlFile(final HashMap<String, ArrayList<InventoryItem>> theMap, final String filename, int packageNumber)
			throws Exception {

		final SimpleDateFormat format1 = new SimpleDateFormat(PackageBuilder.DEFAULT_DATE_FORMAT);

		final SimpleXMLDoc packageXML = new SimpleXMLDoc();
		packageXML.openTag("Package", "xmlns", "http://soap.sforce.com/2006/04/metadata");

		final ArrayList<String> mdTypes = new ArrayList<>(theMap.keySet());
		Collections.sort(mdTypes);
		
		// get list of types for comment line
		ArrayList<String> typesInPackage = new ArrayList<String>();
		for (final String mdType : mdTypes) {
			if (theMap.get(mdType).size() == 0) {
				continue;
			} else{
				typesInPackage.add(mdType + "(" + theMap.get(mdType).size() + ")");
			}
		}

		for (final String mdType : mdTypes) {
			if (theMap.get(mdType).size() == 0) {
				continue;
			}
			packageXML.openTag("types");
			packageXML.addTag("name", mdType);

			for (final InventoryItem item : theMap.get(mdType)) {

				Map<String, String> attributes = null;
				if (this.includeChangeData) {
					attributes = new HashMap<>();
					attributes.put("lastmodifiedby", item.getLastModifiedByName());
					attributes.put("lastmodified", format1.format(item.getLastModifiedDate() == null ? 0 : item.getLastModifiedDate().getTime()));
					attributes.put("lastmodifiedemail", item.lastModifiedByEmail);
				}

				packageXML.addTag("members", item.itemName, attributes);
			}
			packageXML.closeTag(1);
		}
		packageXML.addTag("version", String.valueOf(this.myApiVersion));
		packageXML.closeDocument();

		Utils.writeFile(this.targetDir + filename, packageXML.toString());
		this.log("Writing " + new File(this.targetDir + filename).getCanonicalPath(), Loglevel.BRIEF);

		if (downloadData) {
			this.log("Asked to retrieve this package from org - will do so now.", Loglevel.BRIEF);
			OrgRetrieve myRetrieve = new OrgRetrieve(OrgRetrieve.Loglevel.VERBOSE);
			myRetrieve.setMetadataConnection(srcMetadataConnection);
			myRetrieve.setZipFile(filename.replace("xml", "zip"));
			myRetrieve.setManifestFile(this.targetDir + filename);
			myRetrieve.setApiVersion(myApiVersion);
			myRetrieve.setPackageNumber(packageNumber);
			myRetrieve.retrieveZip();
		}
	}

}
