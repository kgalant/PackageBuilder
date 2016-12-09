# PackageBuilder

This is a tool for salesforce.com. It will connect to an org and generate a package.xml that can subsequently be used with the Force.com Migration Tool to extract code and metadata from an org.

Usage:

java -jar PackageBuilder.jar <org property file path> <fetch property path>

		Example: 
		java -jar PackageBuilder.jar properties\test.properties properties\fetch.properties
		will list the items defined in the fetch.properties file from the org specified in the 
		file properties\test.properties and put them in the target directory specified in the 
		properties\fetch.properties file

See properties files for additional detail - should be self-explanatory
