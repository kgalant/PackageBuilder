# PackageBuilder

This is a tool for salesforce.com. It will connect to an org and generate a package.xml that can subsequently be used with the Force.com Migration Tool to extract code and metadata from an org.

### Usage:
``` 
java -jar PackageBuilder.jar [-o <parameter file1>,<parameterfile2>,...] [-u <SF username>] [-p <SF password>] [-s <SF url>] [-a <apiversion>] [-mi <metadataType1>,<metadataType2>,...] [-sp <pattern1>,<pattern2>,...] [-d <destinationpath>] [-v]
```    
* -o,--orgfile <arg>          
file containing org parameters (see below)
* -u,--username <arg>         
username for the org (someuser@someorg.com)
* -p,--password <arg>        
password for the org (t0pSecr3t)
* -s,--serverurl <arg>        
server URL for the org (https://login.salesforce.com)
* -a,--apiversion <arg>       
api version to use, will default to 38.0
* -mi,--metadataitems <arg>   
metadata items to fetch
* -sp,--skippatterns <arg>    
patterns to skip when fetching
* -d,--destination <arg>    
directory where the generated package.xml will be written
* -v,--verbose                
output verbose logging instead of just core output

#### Example: 
java -jar PackageBuilder.jar properties\test.properties properties\fetch.properties
will list the items defined in the fetch.properties file from the org specified in the 
file properties\test.properties and put them in the target directory specified in the 
properties\fetch.properties file

See properties files for additional detail - should be self-explanatory
