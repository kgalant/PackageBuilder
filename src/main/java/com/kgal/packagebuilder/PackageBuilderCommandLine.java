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

        this.parameters.put("apiversion", "" + PackageBuilder.API_VERSION);
        this.parameters.put("metadataitems", null);
        this.parameters.put("skipItems", null);
        this.parameters.put("serverurl", null);
        this.parameters.put("username", null);
        this.parameters.put("password", null);
        this.parameters.put("targetdirectory", null);
        this.parameters.put("sourcedirectory", null);
        this.parameters.put("includechangedata", String.valueOf(PackageBuilder.INCLUDECHANGEDATA));

        // adding handling for building a package from a directory
        this.parameters.put("basedirectory", null);

        final HashSet<String> nonMandatoryParams = new HashSet<>();
        nonMandatoryParams.add("skipItems");

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

        if (line != null) {
            // first initialize parameters from any parameter files provided

            if (line.hasOption("o") && (line.getOptionValue("o") != null) && (line.getOptionValue("o").length() > 0)) {
                final String paramFilesParameter = line.getOptionValue("o");
                for (final String paramFileName : paramFilesParameter.split(",")) {
                    final Properties props = Utils.initProps(paramFileName.trim());
                    System.out.println("Loading parameters from file: " + paramFileName);
                    this.addParameterFromProperty(props, "sf.apiversion");
                    this.addParameterFromProperty(props, "metadataitems");
                    this.addParameterFromProperty(props, "sf.serverurl");
                    this.addParameterFromProperty(props, "sf.username");
                    this.addParameterFromProperty(props, "sf.password");
                    this.addParameterFromProperty(props, "skipItems");
                    this.addParameterFromProperty(props, "basedirectory");
                    this.addParameterFromProperty(props, "metadatatargetdir");
                    this.addParameterFromProperty(props, "includechangedata");

                    // adding handling for building a package from a directory
                    this.addParameterFromProperty(props, "targetdirectory");
                }
            }

            // now add all parameters form the commandline
            this.addCmdlineParameter(line, "a", "apiversion");
            this.addCmdlineParameter(line, "u", "username");
            this.addCmdlineParameter(line, "s", "serverurl");
            this.addCmdlineParameter(line, "p", "password");
            this.addCmdlineParameter(line, "mi", "metadataitems");
            this.addCmdlineParameter(line, "sp", "skipItems");
            this.addCmdlineParameter(line, "d", "targetdirectory");
            this.addCmdlineParameter(line, "mt", "metadatatargetdir");

            // adding handling for building a package from a directory
            this.addCmdlineParameter(line, "b", "basedirectory");

            // adding handling for brief output parameter
            if (line.hasOption("v")) {
                this.parameters.put("loglevel", "verbose");
            }

            // add default to current directory if no target directory given

            if (!this.isParameterProvided("targetdirectory")) {
                System.out.println("No target directory provided, will default to current directory.");
                this.parameters.put("targetdirectory", ".");
            }
            
            // add include change telemetry data and download
            this.addBooleanParameter(line, "c", "includechangedata");
            boolean download = this.addBooleanParameter(line, "do", "download");
            final boolean gitCommit = this.addBooleanParameter(line, "g", "gitcommit");

            // GIT needs download and changedata
            if (gitCommit) {
                download = true;
                this.parameters.put("includechangedata", "true");
                this.parameters.put("download", "true");
            }
            
            if (download && !this.isParameterProvided("sourcedirectory")) {
                System.out.println("No directory provided as download destination, will default to ./src.");
                this.parameters.put("sourcedirectory", "./src");
            }            

            // check that we have the minimum parameters
            // either b(asedir) and d(estinationdir)
            // or s(f_url), p(assword), u(sername), mi(metadataitems)

            if (this.isParameterProvided("basedirectory") &&
                    this.isParameterProvided("targetdirectory")) {
                canProceed = true;
            } else {
                if (this.isParameterProvided("serverurl") &&
                        this.isParameterProvided("username") &&
                        this.isParameterProvided("password")

                ) {
                    canProceed = true;
                } else {
                    System.out.println("Mandatory parameters not provided in files or commandline -"
                            + " either basedir and destination or serverurl, username, password and metadataitems required as minimum");
                    System.out.println("Visible parameters:");
                    for (final String key : this.parameters.keySet()) {
                        System.out.println(key + ":" + this.parameters.get(key));
                    }
                }
            }

            for (final String key : this.parameters.keySet()) {
                System.out.println(key + ":" + this.parameters.get(key));
            }

            if (!canProceed) {
                this.printHelp();

            }
        } else {
            this.printHelp();
        }

        return canProceed;
    }

    private boolean addBooleanParameter(final CommandLine line, final String optionName, final String paramName) {
        boolean result = false;
        if (line.hasOption(optionName)) {
            this.parameters.put(paramName, "true");
            result = true;
        }
        return result;
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

    // wraps the generations/fetching of an org id for database purposes

    private boolean isParameterProvided(final String parameterName) {
        return ((this.parameters.get(parameterName) != null) && (this.parameters.get(parameterName).length() > 0));
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
        this.options.addOption(Option.builder("o").longOpt("orgfile")
                .desc("file containing org parameters (see below)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder("u").longOpt("username")
                .desc("username for the org (someuser@someorg.com)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder("p").longOpt("password")
                .desc("password for the org (t0pSecr3t)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder("s").longOpt("serverurl")
                .desc("server URL for the org (https://login.salesforce.com)")
                .hasArg()
                .build());
        this.options.addOption(Option.builder("a").longOpt("apiversion")
                .desc("api version to use, will default to " + PackageBuilder.API_VERSION)
                .hasArg()
                .build());
        this.options.addOption(Option.builder("mi").longOpt("metadataitems")
                .desc("metadata items to fetch")
                .hasArg()
                .build());
        this.options.addOption(Option.builder("sp").longOpt("skippatterns")
                .desc("patterns to skip when fetching")
                .hasArg()
                .build());
        this.options.addOption(Option.builder("d").longOpt("destination")
                .desc("directory where the generated package.xml will be written")
                .hasArg()
                .build());

        // handling for building a package from a directory

        this.options.addOption(Option.builder("b").longOpt("basedirectory")
                .desc("base directory from which to generate package.xml")
                .hasArg()
                .build());
        
        // When downloading source, where does it go:
        this.options.addOption(Option.builder("mt").longOpt("metadatatargetdir")
                .desc("Directory to download meta data source to")
                .hasArg()
                .build());

        // adding handling for brief output parameter

        this.options.addOption(Option.builder("v").longOpt("verbose")
                .desc("output verbose logging instead of just core output")
                .build());

        // adding handling for change telemetry parameter

        this.options.addOption(Option.builder("c").longOpt("includechangedata")
                .desc("include lastmodifiedby and date fields in every metadataitem output")
                .build());

        // handling of direct download and git options
        this.options.addOption(Option.builder("do").longOpt("download")
                .desc("directly download assets, removing the need for ANT or MDAPI call")
                .build());

        this.options.addOption(Option.builder("g").longOpt("gitcommit")
                .desc("commits the changes to git. Requires -d -c options")
                .build());

    }

}
