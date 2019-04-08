/** ========================================================================= *
 * Copyright (C)  2017, 2018 Salesforce Inc ( http://www.salesforce.com/      *
 *                            All rights reserved.                            *
 *                                                                            *
 *  @author     Kim Galant  <kgalant@salesforce.com>                          *
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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.kgal.migrationtoolutils.Utils;

/**
 * Command line and parameter frontend for package builder core application
 *
 */
public class PackageBuilderCommandLine {

	// command line static strings

	public static final String APIVERSION = "a";
	public static final String APIVERSION_LONGNAME = "apiversion";
	public static final String METADATAITEMS = "mi";
	public static final String METADATAITEMS_LONGNAME = "metadataitems";
	public static final String ORGFILE = "o";
	public static final String ORGFILE_LONGNAME = "orgfile";
	public static final String USERNAME = "u";
	public static final String USERNAME_LONGNAME = "username";
	public static final String PASSWORD = "p";
	public static final String PASSWORD_LONGNAME = "password";
	public static final String SERVERURL = "s";
	public static final String SERVERURL_LONGNAME = "serverurl";
	public static final String SKIPPATTERNS = "sp";
	public static final String SKIPPATTERNS_LONGNAME = "skippatterns";
	public static final String INCLUDEPATTERNS = "ip";
	public static final String INCLUDEPATTERNS_LONGNAME = "includepatterns";
	public static final String SKIPEMAIL = "se";
	public static final String SKIPEMAIL_LONGNAME = "skipemail";
	public static final String INCLUDEEMAIL = "ie";
	public static final String INCLUDEEMAIL_LONGNAME = "includeemail";
	public static final String SKIPUSERNAME = "su";
	public static final String SKIPUSERNAME_LONGNAME = "skipusername";
	public static final String INCLUDEUSERNAME = "iu";
	public static final String INCLUDEUSERNAME_LONGNAME = "includeusername";
	public static final String DESTINATION = "d";
	public static final String DESTINATION_LONGNAME = "destination";
	public static final String BASEDIRECTORY = "b";
	public static final String BASEDIRECTORY_LONGNAME = "basedirectory";
	public static final String METADATATARGETDIR = "mt";
	public static final String METADATATARGETDIR_LONGNAME = "metadatatargetdir";
	public static final String LOGLEVEL = "ll";
	public static final String LOGLEVEL_LONGNAME = "loglevel";
	public static final String INCLUDECHANGEDATA = "c";
	public static final String INCLUDECHANGEDATA_LONGNAME = "includechangedata";
	public static final String DOWNLOAD = "do";
	public static final String DOWNLOAD_LONGNAME = "download";
	public static final String GITCOMMIT = "g";
	public static final String GITCOMMIT_LONGNAME = "gitcommit";
	public static final String MAXITEMS = "mx";
	public static final String MAXITEMS_LONGNAME = "maxitems";
	public static final String FROMDATE = "fd";
	public static final String FROMDATE_LONGNAME = "fromdate";
	public static final String TODATE = "td";
	public static final String TODATE_LONGNAME = "todate";
	public static final String STRIPUSERPERMISSIONS = "spp";
	public static final String STRIPUSERPERMISSIONS_LONGNAME = "stripprofileuserpermissions";
	public static final String LOCALONLY = "lo";
	public static final String LOCALONLY_LONGNAME = "localonly";
	public static final String UNZIP = "u";
	public static final String UNZIP_LONGNAME = "unzip";
	public static final String RETAINTARGETDIR = "rt";
	public static final String RETAINTARGETDIR_LONGNAME = "retaintargetdir";
	
	Map<String, Map<String, String>> paramDefinitions = new HashMap<>();

	/**
	 * @param args
	 * @throws Exception
	 * @throws RemoteException
	 */
	public static void main(final String[] args) throws RemoteException, Exception {
		displayVersionNumber();
		final PackageBuilderCommandLine pbc = new PackageBuilderCommandLine();

		if (pbc.parseCommandLine(args)) {
			final PackageBuilder pb = new PackageBuilder(pbc.getParameters());
			pb.run();
			System.out.println("Done");
		}
		System.exit(0);
	}

	private final Map<String, String> parameters = new HashMap<>();

	private final Options options = new Options();

	public PackageBuilderCommandLine() {
		this.setupOptions();
	}

	/**
	 * @return the parameters collected from command line or parameter file
	 */
	public Map<String, String> getParameters() {
		return this.parameters;
	}

	public boolean parseCommandLine(final String[] args) {

		boolean canProceed = false;

		// put in default parameters
		this.parameters.put(APIVERSION_LONGNAME, "" + PackageBuilder.API_VERSION);

		// now parse the command line

		final CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			// parse the command line arguments
			line = parser.parse(this.options, args);
		} catch (final ParseException exp) {
			// oops, something went wrong
			System.err.println("Command line parsing failed.  Reason: " + exp.getMessage());
			System.exit(-1);
		}

		// first, add any parameters from any property files provided on command line

		if (line != null) {
			// first initialize any parameter files provided

			List<Properties> propFileList = getPropsFromFiles(line.getOptionValue(ORGFILE));

			// now populate parameter array with parameters
			
			for (Map<String, String> paramDefinition : paramDefinitions.values()) {
				readParameter(propFileList, 
								line, 
								paramDefinition.get("propFileParamName"), 
								paramDefinition.get("shortParamName"), 
								paramDefinition.get("longParamName"));
			}
		}	


		////////////////////////////////////////////////////////////////////////
		//
		// from here on down, any special treatment for individual parameters
		//
		////////////////////////////////////////////////////////////////////////


//		skipping this, going to setting LOGLEVEL directly from command/property
// 		if LOGLEVEL parameter is provided, set loglevel to LOGLEVEL, else it will default to normal
//		if (isOptionSet(LOGLEVEL_LONGNAME)) {
//			this.parameters.put("loglevel", LOGLEVEL_LONGNAME);
//		}        

		// add default to current directory if no target directory given
		if (!this.isParameterProvided(DESTINATION_LONGNAME)) {
			System.out.println("No target directory provided, will default to current directory.");
			this.parameters.put(DESTINATION_LONGNAME, ".");
		}

		// GIT needs download and changedata
		if (isOptionSet(GITCOMMIT_LONGNAME)) {
			this.parameters.put(INCLUDECHANGEDATA_LONGNAME, "true");
			this.parameters.put(DOWNLOAD_LONGNAME, "true");
			this.parameters.put(UNZIP_LONGNAME, "true");
		}

		// default download target to current directory if no explicit destination provided
		if ((isOptionSet(GITCOMMIT_LONGNAME) || isOptionSet(DOWNLOAD_LONGNAME)) && !this.isParameterProvided(METADATATARGETDIR_LONGNAME)) {
			System.out.println("No directory provided as download destination, will default to current directory");
			this.parameters.put(METADATATARGETDIR_LONGNAME, ".");
		}     

		// set maxitems to default value if nothing provided
		if (!this.isParameterProvided(MAXITEMS_LONGNAME)) {
			//System.out.println("No maxitems parameter provided, will default to " + PackageBuilder.MAXITEMSINPACKAGE + ".");
			this.parameters.put(MAXITEMS_LONGNAME, String.valueOf(PackageBuilder.MAXITEMSINPACKAGE));
		} 

		////////////////////////////////////////////////////////////////////////
		//
		// now check that we have minimum parameters needed to run
		//
		////////////////////////////////////////////////////////////////////////

		// check that we have the minimum parameters
		// either b(asedir) and d(estinationdir)
		// or s(f_url), p(assword), u(sername)

		if (this.isParameterProvided(BASEDIRECTORY_LONGNAME) && this.isParameterProvided(DESTINATION_LONGNAME)) {
			canProceed = true;
		} else {
			if (this.isParameterProvided(SERVERURL_LONGNAME) &&
					this.isParameterProvided(USERNAME_LONGNAME) &&
					this.isParameterProvided(PASSWORD_LONGNAME)) {
				canProceed = true;
			} else {
				System.out.println("Mandatory parameters not provided in files or commandline -"
						+ " either basedir and destination or serverurl, username and password required as minimum");
			}
		}
		
		List<String> parameters = new ArrayList<String>(this.parameters.keySet());
		Collections.sort(parameters);

		for (final String key : parameters) {
			if (key.equals("password")) {
				System.out.println(key + ":" + this.parameters.get(key).replaceAll(".", "*"));
			} else {
				System.out.println(key + ":" + this.parameters.get(key));
			}
		}

		if (!canProceed) {
			this.printHelp();
		}
		return canProceed;
	}

	private void readParameter(List<Properties> propFileList, CommandLine line, String propFileParamName, String shortParamName, String longParamName) {
		// first, add any parameters from files
		for (Properties propFile : propFileList) {
			addParameterFromProperty(propFile, propFileParamName);
		}
		// now add it from commandline if present
		addCmdlineParameter(line, shortParamName, longParamName);



	}

	private List<Properties> getPropsFromFiles(String propFilesParameter) {
		List<Properties> retval = new ArrayList<Properties>(); 
		for (final String paramFileName : propFilesParameter.split(",")) {
			retval.add(Utils.initProps(paramFileName.trim()));
		}			
		return retval;
	}

	/**
	 * Extract parameters if provided
	 *
	 * @param cmdLineName
	 * @param tagName
	 */
	private void addCmdlineParameter(final CommandLine line, final String cmdLineName, final String cmdLineLongName) {
		if (line.hasOption(cmdLineName)) {
			if (line.getOptionValue(cmdLineName) != null && (line.getOptionValue(cmdLineName).length() > 0)) {
				this.parameters.put(cmdLineLongName, line.getOptionValue(cmdLineName));
			} else {
				this.parameters.put(cmdLineLongName, "true");
			}
		}
	}

	private void addParameterFromProperty(final Properties props, final String propName) {
		// Some properties start with "sf.", but we only use the name behind
		final String paramName = (propName.startsWith("sf.")) ? propName.substring(3) : propName;
		if (props.getProperty(propName) != null) {
			this.parameters.put(paramName, props.getProperty(propName));
		}
	}

	// wraps the generations/fetching of an org id for database purposes

	private boolean isParameterProvided(final String parameterName) {
		return ((this.parameters.get(parameterName) != null) && (this.parameters.get(parameterName).length() > 0));
	}

	private boolean isOptionSet(final String parameterName) {
		return (this.parameters.get(parameterName) != null);
	}

	// returns a database - either one we could read from file, or a newly
	// initialized one

	private void printHelp() {
		final HelpFormatter formatter = new HelpFormatter();
		formatter.setOptionComparator(null);

		formatter.printHelp(
				"java -jar PackageBuilder.jar [-b basedirectory] [-o <parameter file1>,<parameter file2>] [-u <SF username>] [-p <SF password>]",
				this.options);
	}
	
	// add any new parameters here only

	private void setupOptions() {
		
		setupParameter(ORGFILE_LONGNAME, 		ORGFILE, 		ORGFILE_LONGNAME, 		"file containing org parameters (see below)", true);
		setupParameter("sf.apiversion", 		APIVERSION, 	APIVERSION_LONGNAME, 	"api version to use, will default to " + PackageBuilder.API_VERSION, true);
		setupParameter("sf.serverurl", 			SERVERURL, 		SERVERURL_LONGNAME, 	"server URL for the org (https://login.salesforce.com)", true);
		setupParameter("sf.password", 			PASSWORD, 		PASSWORD_LONGNAME,		"password for the org (t0pSecr3t)",true);
		setupParameter("sf.username", 			USERNAME, 		USERNAME_LONGNAME,		"username for the org (someuser@someorg.com)", true);
		setupParameter(METADATAITEMS_LONGNAME, 	METADATAITEMS, 	METADATAITEMS_LONGNAME, "metadata items to fetch (comma-separated, no spaces)", true);
		setupParameter(SKIPPATTERNS_LONGNAME, 	SKIPPATTERNS,  	SKIPPATTERNS_LONGNAME,	"patterns to skip when fetching. Will override include flags (pattern, username, email). Comma-separated java-style regexps.", true);
		setupParameter(INCLUDEPATTERNS_LONGNAME,INCLUDEPATTERNS,INCLUDEPATTERNS_LONGNAME,"patterns to include when fetching. Will be overridden by any exclude flags (pattern, username, email). Comma-separated java-style regexps.", true);
		setupParameter(SKIPEMAIL_LONGNAME,		SKIPEMAIL,  	SKIPEMAIL_LONGNAME,		"email(s) to skip when fetching. Will override include flags (pattern, username, email). Comma-separated java-style regexps.", true);
		setupParameter(INCLUDEEMAIL_LONGNAME, 	INCLUDEEMAIL,  	INCLUDEEMAIL_LONGNAME, 	"email(s) to include when fetching. Will be overridden by any exclude flags (pattern, username, email). Comma-separated java-style regexps.", true);
		setupParameter(SKIPUSERNAME_LONGNAME, 	SKIPUSERNAME,  	SKIPUSERNAME_LONGNAME,	"user name(s) to skip when fetching. Will override include flags (pattern, username, email). Comma-separated java-style regexps.", true);	
		setupParameter(INCLUDEUSERNAME_LONGNAME,INCLUDEUSERNAME,INCLUDEUSERNAME_LONGNAME,"user name(s) to include when fetching. Will be overridden by any exclude flags (pattern, username, email). Comma-separated java-style regexps.", true);
		setupParameter(DESTINATION_LONGNAME, 	DESTINATION,  	DESTINATION_LONGNAME,	"directory where the generated package.xml will be written", true);
		setupParameter(METADATATARGETDIR_LONGNAME, METADATATARGETDIR,  METADATATARGETDIR_LONGNAME, "Directory to download meta data source (different to where package.xml will go) to", true);
		setupParameter(MAXITEMS_LONGNAME, 		MAXITEMS,  		MAXITEMS_LONGNAME,		"max number of items to put in a single package xml (defaults to 10000 if not provided)", true);
		setupParameter(FROMDATE_LONGNAME, 		FROMDATE,  		FROMDATE_LONGNAME,		"only items last modified on or after this date (YYYY-MM-DD) will be included (in connecting user's local time zone)", true);
		setupParameter(TODATE_LONGNAME, 		TODATE,  		TODATE_LONGNAME,		"only items last modified on or before this date (YYYY-MM-DD) will be included (in connecting user's local time zone)", true);
		setupParameter(LOGLEVEL_LONGNAME, 		LOGLEVEL,  		LOGLEVEL_LONGNAME,		"output log level (INFO, FINE, FINER make sense) - defaults to INFO if not provided", true);
		setupParameter(INCLUDECHANGEDATA_LONGNAME, INCLUDECHANGEDATA,INCLUDECHANGEDATA_LONGNAME, "include lastmodifiedby and date fields in every metadataitem output", false);
		setupParameter(DOWNLOAD_LONGNAME, 		DOWNLOAD,  		DOWNLOAD_LONGNAME,		"directly download assets, removing the need for ANT or MDAPI call", false);
		setupParameter(GITCOMMIT_LONGNAME, 		GITCOMMIT,  	GITCOMMIT_LONGNAME,		"commits the changes to git. Requires -d -c options", false);
		setupParameter(LOCALONLY_LONGNAME, 		LOCALONLY,  	LOCALONLY_LONGNAME, 	"Don't re-download package.zip files, but process existing ones", false);
		setupParameter(UNZIP_LONGNAME, 			UNZIP,  		UNZIP_LONGNAME,			"unzip any retrieved package(s)", false);
		setupParameter(BASEDIRECTORY_LONGNAME, 	BASEDIRECTORY,  BASEDIRECTORY_LONGNAME,	"base directory from which to generate package.xml", true);
		setupParameter(STRIPUSERPERMISSIONS_LONGNAME,STRIPUSERPERMISSIONS,STRIPUSERPERMISSIONS_LONGNAME, "strip userPermissions tags from profile files (only applies if the -do switch is also used)", false);
		setupParameter(RETAINTARGETDIR_LONGNAME,RETAINTARGETDIR,RETAINTARGETDIR_LONGNAME,"do not clear the metadatatargetdir provided when unzipping", false);

	}

	private void setupParameter(String propFileParamName, String shortParamName, String longParamName, String paramDescription,	boolean hasArgs) {
		Map<String, String> thisParamMap = new HashMap<>();
		thisParamMap.put("propFileParamName", propFileParamName);
		thisParamMap.put("shortParamName", shortParamName);
		thisParamMap.put("longParamName", longParamName);
		paramDefinitions.put(longParamName, thisParamMap);
		if (hasArgs) {
			this.options.addOption(Option.builder(shortParamName).longOpt(longParamName)
					.desc(paramDescription)
					.hasArg()
					.build());		
		} else {
			this.options.addOption(Option.builder(shortParamName).longOpt(longParamName)
					.desc(paramDescription)
					.build());
		}
		
	}

	private static void displayVersionNumber() throws IOException, XmlPullParserException {
		MavenXpp3Reader reader = new MavenXpp3Reader();
		Model model;
		if ((new File("pom.xml")).exists())
			model = reader.read(new FileReader("pom.xml"));
		else
			model = reader.read(
					new InputStreamReader(
							PackageBuilderCommandLine.class.getResourceAsStream(
									"/META-INF/maven/com.kgal/PackageBuilder/pom.xml"
									)
							)
					);
		System.out.println(model.getArtifactId() + " " + model.getVersion());

	}

}
