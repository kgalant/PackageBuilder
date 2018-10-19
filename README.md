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
* -b,--basedirectory <arg>    
directory where the the code will look for a SFDC package structure (e.g. classes folder, objects folder, etc.)
* -v,--verbose                
output verbose logging instead of just core output

All parameters can be provided in parameter files specified with the -o parameter. More than one file can be provided (as in the example below, where one file would define what to fetch, skippatterns, etc., and the other where to fetch from). If any parameters are provided both in files and on the command line, the command line ones will be used. 

##### Property file format
The property files use standard Java property file format, i.e. `parameter=value`. E.g.

```property
# equivalent to -a commandline parameter
apiversion=44.0
# equivalent to -mi commandline parameter
metadataitems=ApexClass, ApexComponent, ApexPage
# equivalent to -s commandline parameter
sf_url=https://login.salesforce.com
# equivalent to -u commandline parameter
sf_username=my@user.name
# equivalent to -p commandline parameter
sf_password=t0ps3cr3t
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
Will run the packagebuilder outputting `package.xml` to the `src` folder, using parameters specified in the `pacakgebuilder.properties` and `org.properties` 

```
java -jar PackageBuilder.jar -d dst -b src
```
Will run the packagebuilder, inventory the `src` directory and write a `dst/package.xml` file corresponding to the (recognized) content in the directory

See properties files for additional detail - should be self-explanatory
