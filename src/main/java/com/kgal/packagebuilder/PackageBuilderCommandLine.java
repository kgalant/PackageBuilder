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

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.salesforce.migrationtoolutils.Utils;

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
	public static final String VERBOSE = "v";
	public static final String VERBOSE_LONGNAME = "verbose";
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
	
    /**
     * @param args
     * @throws Exception
     * @throws RemoteException
     */
    public static void main(final String[] args) throws RemoteException, Exception {
        final PackageBuilderCommandLine pbc = new PackageBuilderCommandLine();

        if (pbc.parseCommandLine(args)) {
            final PackageBuilder pb = new PackageBuilder(pbc.getParameters());
            pb.run();
        }

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
            // first initialize parameters from any parameter files provided

            if (line.hasOption(ORGFILE) && (line.getOptionValue(ORGFILE) != null) && (line.getOptionValue(ORGFILE).length() > 0)) {
                final String paramFilesParameter = line.getOptionValue(ORGFILE);
                for (final String paramFileName : paramFilesParameter.split(",")) {
                    final Properties props = Utils.initProps(paramFileName.trim());
                    System.out.println("Loading parameters from file: " + paramFileName);
                    this.addParameterFromProperty(props, "sf.apiversion");
                    this.addParameterFromProperty(props, METADATAITEMS_LONGNAME);
                    this.addParameterFromProperty(props, "sf.serverurl");
                    this.addParameterFromProperty(props, "sf.username");
                    this.addParameterFromProperty(props, "sf.password");
                    this.addParameterFromProperty(props, SKIPPATTERNS_LONGNAME);
                    this.addParameterFromProperty(props, INCLUDEPATTERNS_LONGNAME);
                    this.addParameterFromProperty(props, SKIPEMAIL_LONGNAME);
                    this.addParameterFromProperty(props, INCLUDEEMAIL_LONGNAME);
                    this.addParameterFromProperty(props, SKIPUSERNAME_LONGNAME);
                    this.addParameterFromProperty(props, INCLUDEUSERNAME_LONGNAME);
                    this.addParameterFromProperty(props, BASEDIRECTORY_LONGNAME);
                    this.addParameterFromProperty(props, METADATATARGETDIR_LONGNAME);
                    this.addParameterFromProperty(props, INCLUDECHANGEDATA_LONGNAME);
                    this.addParameterFromProperty(props, MAXITEMS_LONGNAME);
                    this.addParameterFromProperty(props, FROMDATE_LONGNAME);
                    this.addParameterFromProperty(props, TODATE_LONGNAME);

                    // adding handling for building a package from a directory
                    this.addParameterFromProperty(props, DESTINATION_LONGNAME);
                    
                    // additional parameters to be available from property file
                    this.addBooleanParameterFromProperty(props, VERBOSE_LONGNAME);
                    this.addBooleanParameterFromProperty(props, DOWNLOAD_LONGNAME);
                    this.addBooleanParameterFromProperty(props, GITCOMMIT_LONGNAME);
                }
            }
        }
        
        // now add any parameters from command line
        // will supersede anything provided in property files
        
        this.addCmdlineParameter(line, APIVERSION, APIVERSION_LONGNAME);
        this.addCmdlineParameter(line, USERNAME, USERNAME_LONGNAME);
        this.addCmdlineParameter(line, SERVERURL, SERVERURL_LONGNAME);
        this.addCmdlineParameter(line, PASSWORD, PASSWORD_LONGNAME);
        this.addCmdlineParameter(line, METADATAITEMS, METADATAITEMS_LONGNAME);
        this.addCmdlineParameter(line, SKIPPATTERNS, SKIPPATTERNS_LONGNAME);
        this.addCmdlineParameter(line, INCLUDEPATTERNS, INCLUDEPATTERNS_LONGNAME);
        this.addCmdlineParameter(line, SKIPEMAIL, SKIPEMAIL_LONGNAME);
        this.addCmdlineParameter(line, INCLUDEEMAIL, INCLUDEEMAIL_LONGNAME);
        this.addCmdlineParameter(line, SKIPUSERNAME, SKIPUSERNAME_LONGNAME);
        this.addCmdlineParameter(line, INCLUDEUSERNAME, INCLUDEUSERNAME_LONGNAME);
        this.addCmdlineParameter(line, DESTINATION, DESTINATION_LONGNAME);
        this.addCmdlineParameter(line, METADATATARGETDIR, METADATATARGETDIR_LONGNAME);
        this.addCmdlineParameter(line, MAXITEMS, MAXITEMS_LONGNAME);
        this.addCmdlineParameter(line, FROMDATE, FROMDATE_LONGNAME);
        this.addCmdlineParameter(line, TODATE, TODATE_LONGNAME);
        this.addCmdlineParameter(line, VERBOSE, VERBOSE_LONGNAME);
        
        // add include change telemetry data and download
        this.addBooleanParameter(line, INCLUDECHANGEDATA, INCLUDECHANGEDATA_LONGNAME);
        this.addBooleanParameter(line, DOWNLOAD, DOWNLOAD_LONGNAME);
        this.addBooleanParameter(line, GITCOMMIT, GITCOMMIT_LONGNAME);

        // adding handling for building a package from a directory
        this.addCmdlineParameter(line, BASEDIRECTORY, BASEDIRECTORY_LONGNAME);

        ////////////////////////////////////////////////////////////////////////
        //
        // from here on down, any special treatment for individual parameters
        //
        ////////////////////////////////////////////////////////////////////////
        
        
        // if verbose parameter is provided, set loglevel to verbose, else it will default to normal
        if (isOptionSet(VERBOSE_LONGNAME)) {
            this.parameters.put("loglevel", VERBOSE_LONGNAME);
        }        

        // add default to current directory if no target directory given
        if (!this.isParameterProvided(DESTINATION_LONGNAME)) {
            System.out.println("No target directory provided, will default to current directory.");
            this.parameters.put(DESTINATION_LONGNAME, ".");
        }

     // GIT needs download and changedata
        if (isOptionSet(GITCOMMIT_LONGNAME)) {
            this.parameters.put(INCLUDECHANGEDATA_LONGNAME, "true");
            this.parameters.put(DOWNLOAD_LONGNAME, "true");
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
        
        for (final String key : this.parameters.keySet()) {
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

    /**
     * Extract parameters if provided
     *
     * @param cmdLineName
     * @param tagName
     */
    private void addCmdlineParameter(final CommandLine line, final String cmdLineName, final String tagName) {
        if (line.hasOption(cmdLineName) && (line.getOptionValue(cmdLineName) != null)
                && (line.getOptionValue(cmdLineName).length() > 0)) {
            this.parameters.put(tagName, line.getOptionValue(cmdLineName));
        }
    }

    private void addParameterFromProperty(final Properties props, final String propName) {
        // Some properties start with "sf.", but we only use the name behind
        final String paramName = (propName.startsWith("sf.")) ? propName.substring(3) : propName;
        if (props.getProperty(propName) != null) {
            this.parameters.put(paramName, props.getProperty(propName));
        }
    }
    
    private boolean addBooleanParameter(final CommandLine line, final String optionName, final String paramName) {
        boolean result = false;
        if (line.hasOption(optionName)) {
            this.parameters.put(paramName, "true");
            result = true;
        }
        return result;
    }
    
    private void addBooleanParameterFromProperty(final Properties props, final String propName) {
        // Some properties start with "sf.", but we only use the name behind
        final String paramName = (propName.startsWith("sf.")) ? propName.substring(3) : propName;
        if (props.getProperty(propName) != null) {
            this.parameters.put(paramName, "true");
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

    private void setupOptions() {
        this.options.addOption(Option.builder(ORGFILE).longOpt(ORGFILE_LONGNAME)
                .desc("file containing org parameters (see below)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder(USERNAME).longOpt(USERNAME_LONGNAME)
                .desc("username for the org (someuser@someorg.com)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder(PASSWORD).longOpt(PASSWORD_LONGNAME)
                .desc("password for the org (t0pSecr3t)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder(SERVERURL).longOpt(SERVERURL_LONGNAME)
                .desc("server URL for the org (https://login.salesforce.com)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder(APIVERSION).longOpt(APIVERSION_LONGNAME)
                .desc("api version to use, will default to " + PackageBuilder.API_VERSION)
                .hasArg()
                .build());
        this.options.addOption(Option.builder(METADATAITEMS).longOpt(METADATAITEMS_LONGNAME)
                .desc("metadata items to fetch")
                .hasArg()
                .build());
        this.options.addOption(Option.builder(SKIPPATTERNS).longOpt(SKIPPATTERNS_LONGNAME)
                .desc("patterns to skip when fetching. Will override include flags (pattern, username, email). Comma-separated java-style regexps.")
                .hasArg()
                .build());
        
        // handling of the various skip patterns
        
        this.options.addOption(Option.builder(INCLUDEPATTERNS).longOpt(INCLUDEPATTERNS_LONGNAME)
                .desc("patterns to include when fetching. Will be overridden by any exclude flags (pattern, username, email). Comma-separated java-style regexps.")
                .hasArg()
                .build());
        
        this.options.addOption(Option.builder(SKIPUSERNAME).longOpt(SKIPUSERNAME_LONGNAME)
                .desc("user name(s) to skip when fetching. Will override include flags (pattern, username, email). Comma-separated java-style regexps.")
                .hasArg()
                .build());       
        this.options.addOption(Option.builder(INCLUDEUSERNAME).longOpt(INCLUDEUSERNAME_LONGNAME)
                .desc("user name(s) to include when fetching. Will be overridden by any exclude flags (pattern, username, email). Comma-separated java-style regexps.")
                .hasArg()
                .build());
        
        this.options.addOption(Option.builder(SKIPEMAIL).longOpt(SKIPEMAIL_LONGNAME)
                .desc("email(s) to skip when fetching. Will override include flags (pattern, username, email). Comma-separated java-style regexps.")
                .hasArg()
                .build());       
        this.options.addOption(Option.builder(INCLUDEEMAIL).longOpt(INCLUDEEMAIL_LONGNAME)
                .desc("email(s) to include when fetching. Will be overridden by any exclude flags (pattern, username, email). Comma-separated java-style regexps.")
                .hasArg()
                .build());
        
        
        this.options.addOption(Option.builder(DESTINATION).longOpt(DESTINATION_LONGNAME)
                .desc("directory where the generated package.xml will be written")
                .hasArg()
                .build());
        
        // handling for filtering based on date
        
        this.options.addOption(Option.builder(FROMDATE).longOpt(FROMDATE_LONGNAME)
                .desc("only items last modified on or after this date (YYYY-MM-DD) will be included (in connecting user's local time zone)")
                .hasArg()
                .build());
        
        this.options.addOption(Option.builder(TODATE).longOpt(TODATE_LONGNAME)
                .desc("only items last modified on or before this date (YYYY-MM-DD) will be included (in connecting user's local time zone)")
                .hasArg()
                .build());

        // handling for building a package from a directory

        this.options.addOption(Option.builder(BASEDIRECTORY).longOpt(BASEDIRECTORY_LONGNAME)
                .desc("base directory from which to generate package.xml")
                .hasArg()
                .build());
        
        // When downloading source, where does it go:
        this.options.addOption(Option.builder(METADATATARGETDIR).longOpt(METADATATARGETDIR_LONGNAME)
                .desc("Directory to download meta data source to")
                .hasArg()
                .build());

        // adding handling for brief output parameter

        this.options.addOption(Option.builder(VERBOSE).longOpt(VERBOSE_LONGNAME)
                .desc("output verbose logging instead of just core output")
                .build());

        // adding handling for change telemetry parameter

        this.options.addOption(Option.builder(INCLUDECHANGEDATA).longOpt(INCLUDECHANGEDATA_LONGNAME)
                .desc("include lastmodifiedby and date fields in every metadataitem output")
                .build());

        // handling of direct download and git options
        this.options.addOption(Option.builder(DOWNLOAD).longOpt(DOWNLOAD_LONGNAME)
                .desc("directly download assets, removing the need for ANT or MDAPI call")
                .build());

        this.options.addOption(Option.builder(GITCOMMIT).longOpt(GITCOMMIT_LONGNAME)
                .desc("commits the changes to git. Requires -d -c options")
                .build());
        
        // handling of max items per package
        this.options.addOption(Option.builder(MAXITEMS).longOpt(MAXITEMS_LONGNAME)
                .desc("max number of items to put in a single package xml (defaults to 10000 if not provided)")
                .hasArg()
                .build());


    }

}
