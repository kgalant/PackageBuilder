package com.kgal.packagebuilder;

import java.io.File;
import java.io.IOException;
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
//import com.sforce.soap.partner.PartnerConnection;
//import com.sforce.soap.partner.QueryResult;
//import com.sforce.soap.partner.sobject.SObject;
import com.sforce.soap.tooling.ToolingConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.soap.metadata.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
//import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.kgal.SFLogin.LoginUtil;
import com.salesforce.migrationtoolutils.Utils;

public class PackageBuilder {

	public enum Loglevel {
		VERBOSE (2), NORMAL (1), BRIEF (0);
		private final int level;
		
		Loglevel(int level) {
			this.level = level;
		}
		
		int getLevel() {return level;}
	
	};
	
	String authEndPoint = "";

	private long timeStart;

	private MetadataConnection srcMetadataConnection;
	private ToolingConnection srcToolingConnection;
	private String srcUrl ;
	private String srcUser;
	private String srcPwd;

//	private Properties sourceProps;
//	private Properties fetchProps;
	private static final String urlBase = "/services/Soap/u/";
	private String targetDir = "";

	private static final double API_VERSION = 38.0;
	private static double myApiVersion;
//	private static final int MAX_ITEMS=5000;
//	private static int myMaxItems;
	private static String skipItems;
	private static ArrayList<Pattern> skipPatterns = new ArrayList<Pattern>();
	private static HashMap<String, DescribeMetadataObject> describeMetadataObjectsMap;

	private static final boolean FILTERVERSIONLESSFLOWS = true;

	private static HashSet<String> existingTypes = new HashSet<String>();
	private static HashMap<String,String> parameters = new HashMap<String,String>();

	private static CommandLine line = null;
	private static Options options = new Options();
	
	private Loglevel loglevel;
//	private boolean isLoggingPartialLine = false;



	public static void main(String[] args) throws RemoteException, Exception {

		PackageBuilder sample = new PackageBuilder();
		setupOptions();
		parseCommandLine(args);




		//		if (args.length < 2) {
		//			System.out.println("Usage: java -jar PackageBuilder.jar <org property file path> <fetch property path> [metadataitems=item1,item2,...] [skipitems=skipPattern1,skipPatter2,...]");
		//			System.out.println("properties\test.properties properties\fetch.properties - will list the items defined in the fetch.properties file from the org specified in the file properties\test.properties and put them in the target directory specified in the properties\fetch.properties file ");
		//			System.out.println("providing metadataitems and/or skipitems parameters on command line will override the same from the fetch properties file");
		//			System.out.println("Parameters not supplied - exiting.");
		//			System.exit(0);
		//		}
		//		
		//		if (args.length > 0) {
		//			sample.sourceProps = Utils.initProps(args[0]);
		//		}
		//		
		//		if (args.length > 1) {
		//			sample.fetchProps = Utils.initProps(args[1]);
		//		}
		//		


		sample.run();
	}

	public void run() throws RemoteException, Exception {

		HashSet<String> typesToFetch = new HashSet<String>();
		
		// set loglevel based on parameters
		
		if (parameters.get("loglevel") != null && parameters.get("loglevel").equals("verbose")) {
			loglevel = Loglevel.NORMAL;
		} else {
			loglevel = Loglevel.BRIEF;
		}

		String mdTypesToExamine = parameters.get("metadataitems");

		for (String s : mdTypesToExamine.split(",")) {
			typesToFetch.add(s.trim());
		}

		myApiVersion = Double.parseDouble(parameters.get("apiversion"));
		srcUrl = parameters.get("sf_url") + urlBase + myApiVersion;
		srcUser = parameters.get("sf_username");
		srcPwd = parameters.get("sf_password");
		skipItems = parameters.get("skipItems");

		log("Will fetch: " + mdTypesToExamine + " from: " + srcUrl, Loglevel.BRIEF);
		log("Using user: " + srcUser + " skipping: " + skipItems, Loglevel.NORMAL);


		this.targetDir = Utils.checkPathSlash(Utils.checkPathSlash(parameters.get("targetdirectory")));

		System.out.println("target directory: " + this.targetDir);

		Utils.checkDir(targetDir);

		HashMap<String,ArrayList<String>> inventory = new HashMap<String,ArrayList<String>>(); 

		// Make a login call to source
		this.srcMetadataConnection = LoginUtil.mdLogin(srcUrl, srcUser, srcPwd);

		// get a describe

		DescribeMetadataResult dmr = srcMetadataConnection.describeMetadata(myApiVersion);
		describeMetadataObjectsMap = new HashMap<String, DescribeMetadataObject>();

		for (DescribeMetadataObject obj : dmr.getMetadataObjects()) {
			describeMetadataObjectsMap.put(obj.getXmlName(), obj);
		}
		ArrayList<String> workToDo = new ArrayList<String>(typesToFetch);

		Collections.sort(workToDo);

		Iterator<String> i = workToDo.iterator();
		int counter = 0;
		while (i.hasNext()) {
			counter ++;
			String mdType = i.next();
			if (loglevel.getLevel() > Loglevel.BRIEF.getLevel()) {
				log("*********************************************", Loglevel.NORMAL);
				log("Processing type " + counter + " out of " + workToDo.size() + ": " + mdType, Loglevel.NORMAL);
				log("*********************************************", Loglevel.NORMAL);
			} else if (loglevel == Loglevel.BRIEF) {
				logPartialLine("Processing type " + counter + " out of " + workToDo.size() + ": " + mdType, Loglevel.BRIEF);
			}
			

			ArrayList<String> mdTypeItemList = fetchMetadata(mdType);
			Collections.sort(mdTypeItemList);
			inventory.put(mdType, mdTypeItemList);

			if (loglevel.getLevel() > Loglevel.BRIEF.getLevel()) {
				log("---------------------------------------------", Loglevel.NORMAL);
				log("Finished processing: " + mdType, Loglevel.NORMAL);
				log("---------------------------------------------", Loglevel.NORMAL);
			} else if (loglevel == Loglevel.BRIEF) {
				log(" items: " + mdTypeItemList.size(), Loglevel.BRIEF);
			}

		}

		generatePackageXML(inventory);
	}

	private void generatePackageXML(HashMap<String, ArrayList<String>> inventory) throws ConnectionException, IOException {
		StringBuffer packageXML = new StringBuffer();
		int itemCount = 0;
		int skipCount = 0;
		packageXML.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		packageXML.append("<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">\n");

		ArrayList<String> types = new ArrayList<String>();
		types.addAll(inventory.keySet());
		Collections.sort(types);

		//		Initiate patterns array
		//		TODO: handle non-existent parameter in the config files

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
					log("Skip pattern: " + p.pattern() + " matches the metadata type: " + mdTypeFullName + ", entire type will be skipped.", Loglevel.NORMAL);
					shouldSkip = true;
					break;
				}
			}

			//			check if we have any items in this category

			ArrayList<String> items = inventory.get(mdType);
			if (items.size() < 1) {
				shouldSkip = true;
			}

			if (shouldSkip) {
				continue;
			}

			packageXML.append("\t<types>\n");
			for (String item : items) {
				shouldSkip = false;
				mdTypeFullName = mdType + ":" + item;
				for (Pattern p : skipPatterns) {
					Matcher m = p.matcher(mdTypeFullName);
					if (m.matches()) {
						log("Skip pattern: " + p.pattern() + " matches the metadata item: " + mdTypeFullName + ", item will be skipped.", Loglevel.NORMAL);
						shouldSkip = true;
						skipCount++;
						break;
					}
				}

				// special treatment for flows
				// get rid of items returned without a version number
				//		<members>Update_Campaign_path_on_oppty</members>  ****  FILTER THIS ONE OUT SO IT DOESN'T APPEAR***
				//		<members>Update_Campaign_path_on_oppty-4</members>
				//		<members>Update_Campaign_path_on_oppty-5</members>

				if (mdType.toLowerCase().equals("flow") && FILTERVERSIONLESSFLOWS) {
					if (!item.contains("-")) {
						// we won't count this one as skipped, since it shouldn't be there in the first place
						shouldSkip = true;
					}
				}
				if (!shouldSkip) {
					packageXML.append("\t\t<members>" + item + "</members>\n");	
					itemCount++;
				}
			}

			// special treatment for flows
			// make a callout to Tooling API to get latest version for Active flows (which the s..... Metadata API won't give you)

			if (mdType.toLowerCase().equals("flow")) {
				
				packageXML.append("\n\t\t<!-- Active flow versions below this comment -->\n\n");

				String flowQuery = 	"SELECT DeveloperName ,ActiveVersion.VersionNumber " +
						"FROM FlowDefinition " +
						"WHERE ActiveVersion.VersionNumber <> NULL";

				this.srcToolingConnection = LoginUtil.toolingLogin(srcUrl, srcUser, srcPwd);
				com.sforce.soap.tooling.QueryResult qr = srcToolingConnection.query(flowQuery);
				com.sforce.soap.tooling.sobject.SObject[] records = qr.getRecords();
				for (com.sforce.soap.tooling.sobject.SObject record : records) {
					com.sforce.soap.tooling.sobject.FlowDefinition fd = (com.sforce.soap.tooling.sobject.FlowDefinition) record;
					packageXML.append("\t\t<members>" + fd.getDeveloperName() + "-" + fd.getActiveVersion().getVersionNumber() + "</members>\n");	
					itemCount++;
				}


			}

			packageXML.append("\t\t<name>" + mdType + "</name>\n");
			packageXML.append("\t</types>\n");
		}

		packageXML.append("\t<version>" + myApiVersion + "</version>\n");
		packageXML.append("</Package>\n");

		Utils.writeFile(targetDir + "package.xml", packageXML.toString());
		log("Writing " + new File (targetDir + "package.xml").getCanonicalPath(), Loglevel.BRIEF);

		ArrayList<String> typesFound = new ArrayList<String>(existingTypes);
		Collections.sort(typesFound);

		log("Types found in org: " + typesFound.toString(), Loglevel.BRIEF);

		log("Total items in package.xml: " + itemCount, Loglevel.BRIEF);
		log("Total items skipped: " + skipCount + " (excludes count of items in type where entire type was skipped)", Loglevel.NORMAL);
	}

	private ArrayList<String> fetchMetadata (String metadataType) throws RemoteException, Exception {
		startTiming();
		//logPartialLine(", level);
		ArrayList<String> packageMap = new ArrayList<String>();
		try {

			ArrayList<FileProperties> foldersToProcess = new ArrayList<FileProperties>();
			boolean isFolder = false;
			// check if what we have here is in folders

			DescribeMetadataObject obj = describeMetadataObjectsMap.get(metadataType);
			if (obj != null && obj.getInFolder() == true) {
				isFolder = true;
				log(metadataType + " is stored in folders. Getting folder list.", Loglevel.VERBOSE);
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
//			int thisItemCount = 0;


			do {
				FileProperties folderProperties = null;
				ListMetadataQuery query = new ListMetadataQuery();


				query.setType(metadataType);
				String folderName = null;
				if (isFolder && folder.hasNext()) {
					folderProperties = folder.next(); 
					folderName = folderProperties.getFullName();
					query.setFolder(folderName);
				}

				// Assuming that the SOAP binding has already been established.

				// generate full metadata inventory


				FileProperties[] srcMd = srcMetadataConnection.listMetadata(new ListMetadataQuery[] { query }, myApiVersion);
				itemCount += srcMd.length;
//				thisItemCount = srcMd.length;
				if (folderName != null) {
					log("Processing folder: " + folderName + " " + " items: " + srcMd.length + "\tCurrent total: " + itemCount, Loglevel.NORMAL);
					// fetch folders themselves
					packageMap.add(folderName);
					ArrayList<FileProperties> filenameList = new ArrayList<FileProperties>();
					filenameList.add(folderProperties);
					metadataMap.put(folderProperties.getFileName(), filenameList);
					itemCount++;
				}
				
				if (itemCount > 0) {
					existingTypes.add(metadataType);
				}

				if (srcMd != null && srcMd.length > 0 
						|| metadataType.equals("StandardValueSet")) { // hack alert - currently listMetadata call will return nothing for StandardValueSet
					if (!metadataType.equals("StandardValueSet")) {
						for (FileProperties n : srcMd) {
							if (n.getNamespacePrefix() == null || n.getNamespacePrefix().equals("")) {
								packageMap.add(n.getFullName());	
							}

						}
					} else {
						for (String s : STANDARDVALUETYPESARRAY) packageMap.add(s);
					}
					
					
				} else {
					if (!isFolder) {
						log("No items of this type, skipping...", Loglevel.VERBOSE);
						break;
					}
					if (!folder.hasNext()) {
						break;
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
		log("Duration: " + hms, Loglevel.NORMAL);
	}

	private static void setupOptions() {

		options.addOption( Option.builder("o").longOpt( "orgfile" )
				.desc( "file containing org parameters (see below)" )
				.hasArg()
				.build() );
		options.addOption( Option.builder("u").longOpt( "username" )
				.desc( "username for the org (someuser@someorg.com)" )
				.hasArg()
				.build() );
		options.addOption( Option.builder("p").longOpt( "password" )
				.desc( "password for the org (t0pSecr3t)" )
				.hasArg()
				.build() );
		options.addOption( Option.builder("s").longOpt( "serverurl" )
				.desc( "server URL for the org (https://login.salesforce.com)" )
				.hasArg()
				.build() );
		options.addOption( Option.builder("a").longOpt( "apiversion" )
				.desc( "api version to use, will default to " + API_VERSION)
				.hasArg()
				.build() );
		options.addOption( Option.builder("mi").longOpt( "metadataitems" )
				.desc( "metadata items to fetch" )
				.hasArg()
				.build() );
		options.addOption( Option.builder("sp").longOpt( "skippatterns" )
				.desc( "patterns to skip when fetching" )
				.hasArg()
				.build() );
		options.addOption( Option.builder("d").longOpt( "destination" )
				.desc( "directory where the generated package.xml will be written" )
				.hasArg()
				.build() );
		
		// adding handling for brief output parameter
		
		options.addOption( Option.builder("v").longOpt( "verbose" )
				.desc( "output verbose logging instead of just core output" )
				.build() );
	}

	private static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.setOptionComparator(null);

		formatter.printHelp( "java -jar PackageBuilder.jar [-o <parameter file1>,<parameter file2>] [-u <SF username>] [-p <SF password>]", options );
	}

	private static void parseCommandLine(String[] args) {

		parameters.put("apiversion", "" + API_VERSION);
		parameters.put("metadataitems", null);
		parameters.put("skipItems", null);
		parameters.put("sf_url", null);
		parameters.put("sf_username", null);
		parameters.put("sf_password", null);
		parameters.put("targetdirectory", null);

		HashSet<String> nonMandatoryParams = new HashSet<String>();
		nonMandatoryParams.add("skipItems");

		CommandLineParser parser = new DefaultParser();
		try {
			// parse the command line arguments
			line = parser.parse( options, args );
		}
		catch( ParseException exp ) {
			// oops, something went wrong
			System.err.println( "Command line parsing failed.  Reason: " + exp.getMessage() );
			System.exit(-1);
		}

		if (line != null) {
			// first initialize parameters from any parameter files provided

			if (line.hasOption("o") && line.getOptionValue("o") != null && line.getOptionValue("o").length() > 0) {
				String paramFilesParameter = line.getOptionValue("o");
				for (String paramFileName : paramFilesParameter.split(",")) {
					Properties props = Utils.initProps(paramFileName.trim());
					System.out.println("Loading parameters from file: " + paramFileName);
					parameters.put("apiversion", props.getProperty("apiversion") == null ? parameters.get("apiversion") : "" + Double.parseDouble(props.getProperty("apiversion")));
					parameters.put("metadataitems", props.getProperty("metadataitems") == null ? parameters.get("metadataitems") : props.getProperty("metadataitems"));
					parameters.put("sf_url", props.getProperty("sf_url") == null ? parameters.get("sf_url") : props.getProperty("sf_url"));
					parameters.put("sf_username", props.getProperty("sf_username") == null ? parameters.get("sf_username") : props.getProperty("sf_username"));
					parameters.put("sf_password", props.getProperty("sf_password") == null ? parameters.get("sf_password") : props.getProperty("sf_password"));
					parameters.put("skipItems", props.getProperty("skipItems") == null ? parameters.get("skipItems") : props.getProperty("skipItems"));
					parameters.put("targetdirectory", props.getProperty("targetdirectory") == null ? parameters.get("targetdirectory") : props.getProperty("targetdirectory"));
				}
			}

			// now add all parameters form the commandline
			if (line.hasOption("a") && line.getOptionValue("a") != null && line.getOptionValue("a").length() > 0) {
				parameters.put("apiversion", line.getOptionValue("a"));
			}
			if (line.hasOption("u") && line.getOptionValue("u") != null && line.getOptionValue("u").length() > 0) {
				parameters.put("sf_username", line.getOptionValue("u"));
			}
			if (line.hasOption("s") && line.getOptionValue("s") != null && line.getOptionValue("s").length() > 0) {
				parameters.put("sf_url", line.getOptionValue("s"));
			}
			if (line.hasOption("p") && line.getOptionValue("p") != null && line.getOptionValue("p").length() > 0) {
				parameters.put("sf_password", line.getOptionValue("p"));
			}
			if (line.hasOption("mi") && line.getOptionValue("mi") != null && line.getOptionValue("mi").length() > 0) {
				parameters.put("metadataitems", line.getOptionValue("mi"));
			}
			if (line.hasOption("sp") && line.getOptionValue("sp") != null && line.getOptionValue("sp").length() > 0) {
				parameters.put("skipItems", line.getOptionValue("sp"));
			}
			if (line.hasOption("d") && line.getOptionValue("d") != null && line.getOptionValue("d").length() > 0) {
				parameters.put("targetdirectory", line.getOptionValue("d"));
			}
			
			// adding handling for brief output parameter
			
			if (line.hasOption("v")) {
				parameters.put("loglevel", "verbose");
			}
			
			// check that we have the minimum parameters 
			boolean canProceed = true;
			for (String key : parameters.keySet()) {
				if (!nonMandatoryParams.contains(key) && (parameters.get(key) == null || parameters.get(key).length() < 1)) {
					System.out.println("Parameter " + key + " not provided in properties files or on command line, cannot proceed.");
					canProceed = false;
				}
			}
			if (!canProceed) {
				printHelp();
				System.exit(1);
			}
		} else printHelp();
	}

	private void log (String logText, Loglevel level) {
		if (level.getLevel() <= loglevel.getLevel()) {
			System.out.println (logText);
		}
	}
	
	private void logPartialLine (String logText, Loglevel level) {
		if (level.getLevel() <= loglevel.getLevel()) {
			System.out.print(logText);
		}
	}
	
	private static final String[] STANDARDVALUETYPESARRAY = new String[]
			{"AccountContactMultiRoles","AccountContactRole","AccountOwnership","AccountRating","AccountType","AddressCountryCode","AddressStateCode",
					"AssetStatus","CampaignMemberStatus","CampaignStatus","CampaignType","CaseContactRole","CaseOrigin","CasePriority","CaseReason",
					"CaseStatus","CaseType","ContactRole","ContractContactRole","ContractStatus","EntitlementType","EventSubject","EventType",
					"FiscalYearPeriodName","FiscalYearPeriodPrefix","FiscalYearQuarterName","FiscalYearQuarterPrefix","IdeaCategory1",
					"IdeaMultiCategory","IdeaStatus","IdeaThemeStatus","Industry","InvoiceStatus","LeadSource","LeadStatus","OpportunityCompetitor",
					"OpportunityStage","OpportunityType","OrderStatus1","OrderType","PartnerRole","Product2Family","QuestionOrigin1","QuickTextCategory",
					"QuickTextChannel","QuoteStatus","SalesTeamRole","Salutation","ServiceContractApprovalStatus","SocialPostClassification",
					"SocialPostEngagementLevel","SocialPostReviewedStatus","SolutionStatus","TaskPriority","TaskStatus","TaskSubject","TaskType",
					"WorkOrderLineItemStatus","WorkOrderPriority","WorkOrderStatus"};
	
	/*private static final String STANDARDVALUETYPES = "<types>\n"
			+ "<members>AccountContactMultiRoles</members>\n"
			+ "<members>AccountContactRole</members>\n"
			+ "<members>AccountOwnership</members>\n"
			+ "<members>AccountRating</members>\n"
			+ "<members>AccountType</members>\n"
			+ "<members>AddressCountryCode</members>\n"
			+ "<members>AddressStateCode</members>\n"
			+ "<members>AssetStatus</members>\n"
			+ "<members>CampaignMemberStatus</members>\n"
			+ "<members>CampaignStatus</members>\n"
			+ "<members>CampaignType</members>\n"
			+ "<members>CaseContactRole</members>\n"
			+ "<members>CaseOrigin</members>\n"
			+ "<members>CasePriority</members>\n"
			+ "<members>CaseReason</members>\n"
			+ "<members>CaseStatus</members>\n"
			+ "<members>CaseType</members>\n"
			+ "<members>ContactRole</members>\n"
			+ "<members>ContractContactRole</members>\n"
			+ "<members>ContractStatus</members>\n"
			+ "<members>EntitlementType</members>\n"
			+ "<members>EventSubject</members>\n"
			+ "<members>EventType</members>\n"
			+ "<members>FiscalYearPeriodName</members>\n"
			+ "<members>FiscalYearPeriodPrefix</members>\n"
			+ "<members>FiscalYearQuarterName</members>\n"
			+ "<members>FiscalYearQuarterPrefix</members>\n"
			+ "<members>IdeaCategory1</members>\n"
			+ "<members>IdeaMultiCategory</members>\n"
			+ "<members>IdeaStatus</members>\n"
			+ "<members>IdeaThemeStatus</members>\n"
			+ "<members>Industry</members>\n"
			+ "<members>InvoiceStatus</members>\n"
			+ "<members>LeadSource</members>\n"
			+ "<members>LeadStatus</members>\n"
			+ "<members>OpportunityCompetitor</members>\n"
			+ "<members>OpportunityStage</members>\n"
			+ "<members>OpportunityType</members>\n"
			+ "<members>OrderStatus</members>\n"
			+ "<members>OrderType</members>\n"
			+ "<members>PartnerRole</members>\n"
			+ "<members>Product2Family</members>\n"
			+ "<members>QuestionOrigin</members>\n"
			+ "<members>QuickTextCategory</members>\n"
			+ "<members>QuickTextChannel</members>\n"
			+ "<members>QuoteStatus</members>\n"
			+ "<members>SalesTeamRole</members>\n"
			+ "<members>Salutation</members>\n"
			+ "<members>ServiceContractApprovalStatus</members>\n"
			+ "<members>SocialPostClassification</members>\n"
			+ "<members>SocialPostEngagementLevel</members>\n"
			+ "<members>SocialPostReviewedStatus</members>\n"
			+ "<members>SolutionStatus</members>\n"
			+ "<members>TaskPriority</members>\n"
			+ "<members>TaskStatus</members>\n"
			+ "<members>TaskSubject</members>\n"
			+ "<members>TaskType</members>\n"
			+ "<members>WorkOrderLineItemStatus</members>\n"
			+ "<members>WorkOrderPriority</members>\n"
			+ "<members>WorkOrderStatus</members>\n"
			+ "<name>StandardValueSet</name>\n"
			+ "</types>\n";
			*/
}
