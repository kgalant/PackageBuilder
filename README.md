
# PackageBuilder

This is a tool for salesforce.com. It can do one of two things:
* connect to an org and generate a package.xml that can subsequently be used with the Force.com Migration Tool to extract code and metadata from an org.
* examine a directory containing an unzipped Force.com Migration Tool package and generate a package.xml 

Current downloads support API up to 48 (Spring 20)

Please see http://bit.ly/PckBldDoc for full documentation.

### Usage:
``` 
java -jar PackageBuilder.jar [-o <parameter file1>,<parameterfile2>,...] [-u <SF username>] [-p <SF password>] [-s <SF url>] [-a <apiversion>] [-mi <metadataType1>,<metadataType2>,...] [-sp <pattern1>,<pattern2>,...] [-d <destinationpath>] [-v]
```

or 
``` 
java -jar PackageBuilder.jar -d <destinationpath> -b <packagePath>
```

* -o,--orgfile `<arg>`          
file containing org parameters (see below)
* -u,--username `<arg>`         
username for the org (someuser@someorg.com)
* -p,--password `<arg>`        
password for the org (t0pSecr3t)
* -s,--serverurl `<arg>`        
server URL for the org (https://login.salesforce.com)
* -a,--apiversion `<arg>`
api version to use, will default to 48.0
* -mi,--metadataitems `<arg>`   
metadata items to fetch (commaseparated list of metadata types in package.xml naming). If this parameter is not provided, PackageBuilder will query the org and inventory everything a Metadata Describe returns to it.
* -d,--destination `<arg>`    
directory where the generated package.xml will be written
* -b,--basedirectory `<arg>`    
directory where the the code will look for a SFDC package structure (e.g. classes folder, objects folder, etc.)
* -ll,--loglevel
output log level (INFO, FINE, FINER make sense) - defaults to INFO if not provided
* -c,--includechangedata
include data on who last changed the item directly in the members tag of every item of the package.xml
* -mx,--maxitems `<arg>`    
max number of items to put into a single package.xml (10000 is current max enforced by SF platform, for API 33 and higher, 5000 before)
* -do,--download
execute a retrieve from the org using the generated package(s)
* -u,--unzip
unzip any retrieved package(s)
* -mt,--metadatatargetdir
place unzipped downloaded packages in this directory (else defaults to current directory)
* -spp,--stripprofileuserpermissions
if using -do, and the package contains Profiles, unzip the package and remove all userPermissions from each Profile file, then zip again
* -in,--includenamespaceditems
include items from managed packages, including the managed packages themselves. If this flag is not set, anything from a managed package will be ignored - see also -imp below
* -imp,--includemanagedpackages
include managed packages only - so InstalledPackage::MyPackage is included, but CustomObject::MyPackage__MyCustomObject__c is not


#### Filtering what goes in the package

* -sp,--skippatterns `<arg>`    
name patterns to skip when fetching. 
* -ip,--includepatterns `<arg>`    
name patterns to skip when fetching

When filtering by item name, the PackageBuilder will create an artificial name by prepending the metadata type to the actual item name, so e.g. the Opportunity field My_field__c will be represented internally as `CustomField:Opportunity.My_field__c`. This enables writing more precise patterns like `CustomField:Opportunity.*` which will filter all custom fields on the Opportunity object, as opposed to the pattern `.*Opportunity.*` which would match anything and everything associated with the Opportunity object and beyond (e.g. an Account field called Parent_Opportunity__c).

Multiple patterns can be provided separated by commas. Unpredictable behavior if your pattern includes commas.

* -se,--skipemail `<arg>`    
email patterns to skip when fetching. 
* -ie,--includeemail `<arg>`    
email patterns to skip when fetching
* -su,--skipusername `<arg>`    
user name patterns to skip when fetching. 
* -iu,--includeusername `<arg>`    
user name patterns to include when fetching

This filter works against the email/name of the user who is the last to have modified a given item.

* -fd,--fromdate `<arg>`    
date (YYYY-[M]M-[D]D). All items modified on or after this date (and respecting the td flag) will be included in the result
* -td,--todate `<arg>`    
date (YYYY-[M]M-[D]D). All items modified before or on this date (and respecting the fd flag) will be included in the result

All parameters can be provided in parameter files specified with the -o parameter. More than one file can be provided (as in the example below, where one file would define what to fetch, skippatterns, etc., and the other where to fetch from). If any parameters are provided both in files and on the command line, the command line ones will be used. 

##### Property file format
The property files use standard Java property file format, i.e. `parameter=value`. E.g.

```property
# equivalent to -a commandline parameter
apiversion=44.0
# equivalent to -mi commandline parameter
metadataitems=ApexClass, ApexComponent, ApexPage
# equivalent to -s commandline parameter
sf.serverurl=https://login.salesforce.com
# equivalent to -u commandline parameter
sf.username=my@user.name
# equivalent to -p commandline parameter
sf.password=t0ps3cr3t
# equivalent to -sp commandline parameter
skipItems=.*fflib_.*,.*Class:AWS.*,ApexPage.*
# equivalent to -d commandline parameter
targetdirectory=src
```

###### Yes, I know that the property file parameters should be better aligned to the longnames of the commandline parameters. Coming in a future version!

---

#### Example: 
```
java -jar PackageBuilder.jar -d src -o packagebuilder.properties,org.properties -a 39.0
```
Will run the packagebuilder outputting `package.xml` to the `src` folder, using parameters specified in the `packagebuilder.properties` and `org.properties` 

```
java -jar PackageBuilder.jar -d src -o packagebuilder.properties,org.properties -a 44.0 -c
```
Will run the packagebuilder outputting `package.xml` to the `src` folder, using parameters specified in the `packagebuilder.properties` and `org.properties`. 

In addition, instead of outputting e.g. 
`<members>ChangePasswordController</members>`

will output

`<members lastmodifiedby="John Doe" lastmodified="2018-10-14T20:45:13"" lastmodifiedemail="johndoe@example.com">ChangePasswordController</members>`



```
java -jar PackageBuilder.jar -d dst -b src
```
Will run the packagebuilder, inventory the `src` directory and write a `dst/package.xml` file corresponding to the (recognized) content in the directory

See properties files for additional detail - should be self-explanatory

#### Use of changedata parameter
The changedata parameter will augment the generated package.xml file with data about who/when last changed the given metadata item. So instead of getting 
```
<name>CustomField</name>
<members>Account.Active__c</members>
<members>Account.CustomerPriority__c</members>
```
you will get
```
<name>CustomField</name>
<members lastmodified="2018-08-30T09:28:58" lastmodifiedby="Kim Galant"  lastmodifiedemail="kim.galant@salesforce.com">Account.Active__c</members>
<members lastmodified="2018-08-30T09:28:58" lastmodifiedby="Kim Galant"  lastmodifiedemail="kim.galant@salesforce.com">Account.CustomerPriority__c</members>
```
Note that this adds a lastmodified attribute which contains the last change date of that item, the name and email of the user who changed it (from the SF User table).
If this package.xml file is used for a retrieve, Salesforce (as of API 44) will happily ignore the additional attributes. They are added to help provide additional insight about who last touched each individual item.
