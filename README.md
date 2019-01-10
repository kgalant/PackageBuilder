
# PackageBuilder

This is a tool for salesforce.com. It can do one of two things:
* connect to an org and generate a package.xml that can subsequently be used with the Force.com Migration Tool to extract code and metadata from an org.
* examine a directory containing an unzipped Force.com Migration Tool package and generate a package.xml 

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
api version to use, will default to 38.0
* -mi,--metadataitems `<arg>`   
metadata items to fetch (commaseparated list of metadata types in package.xml naming). If this parameter is not provided, PackageBuilder will query the org and inventory everything a Metadata Describe returns to it.
* -sp,--skippatterns `<arg>`    
patterns to skip when fetching
* -d,--destination `<arg>`    
directory where the generated package.xml will be written
* -b,--basedirectory `<arg>`    
directory where the the code will look for a SFDC package structure (e.g. classes folder, objects folder, etc.)
* -v,--verbose
output verbose logging instead of just core output
* -c,--includechangedata
include data on who last changed the item directly in the members tag of every item of the package.xml

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

#### Use of skipItems parameter
what to not put into the package.xml (omit) - regular expressions that will be matched against a string of {componentType}:{componentName}, so e.g. ApexClass:MySuperClass. 
I.e. by entering `skipItems=ApexClass.*` all classes will be omitted, while `skipItems=ApexClass:.*Super.*` will omit the classes MySuperClass, SuperClass2 and MyOtherSuperClass, but leave MyClass in the package.xml
can also be used without the component type in front, e.g. `skipItems=.*Super.*` will skip the classes MySuperClass, SuperClass2 and MyOtherSuperClass as well as the object MySuperCustomObject, etc.
Multiple patterns can be provided separated by commas.

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
