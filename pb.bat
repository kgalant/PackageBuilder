@echo off

rem MYDIR="$(dirname $0)"
SET DEFPACKAGEFILE=package.zip
SET PROPDIR=c:\dev\properties
rem PWDIR="$(pwd)"
SET PACKAGEBUILDER=c:\dev\tools\PackageBuilder.jar

rem check if a known command is called

if "%~1"=="" (
        call :PrintUsage
        exit /b
)

rem check if we just wanted PackageBuilder (not pb script) usage/parameters

if "%~1"=="/?" (
        java -jar %PACKAGEBUILDER%
        exit /b
)


rem check if we got a property file as a second parameter

IF EXIST %~1 (
	SET PROPFILE=%~1
) ELSE (
    IF EXIST %PROPDIR%\%~1.properties (
	    SET PROPFILE=%PROPDIR%\%~1.properties
    ) ELSE (
        echo Couldnt find property file %~1.properties or locate a %PROPDIR%\%~1.properties file, aborting...
        call :PrintUsage
        exit /b
    )
)

rem now go off and do stuff

echo 1: %~1
echo 2: %~2
echo 3: %~3
echo PROPFILE: %PROPFILE%

IF NOT %PROPFILE%=="" (
    @echo Command:  java -jar %PACKAGEBUILDER% -o %PROPFILE% %*
    java -jar %PACKAGEBUILDER% -o %PROPFILE% %*
)
exit /b

:PrintUsage

@echo "usage: pb <propertyfile> [<parameter1>] [<parameter2>] [<parameter...>]"
@echo "usage: pb /? for PackageBuilder proper command switches/parameters"
exit /b

:LoadPropFile

rem Params: 1: properties file to read

@echo off
for /f "eol=# delims== tokens=1,2" %%a in (%1) do (
	call :SetWithTrim %%a %%b
)
rem this is CALLed, so we need to Exit /b instead of the GOTO
exit /b

:SetWithTrim

rem Params: 1: name of param
rem Params: 2: value of param

@echo off

SET %1=%2

rem this is CALLed, so we need to Exit /b instead of the GOTO
exit /b