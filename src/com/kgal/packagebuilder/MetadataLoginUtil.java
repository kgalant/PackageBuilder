package com.kgal.packagebuilder;

import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.tooling.ToolingConnection;
import com.sforce.soap.partner.LoginResult;

import java.util.Properties;

import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.transport.SoapConnection;

public class MetadataLoginUtil {
	
	private static final String APIVERSION = "35.0"; 
	
	/*
	 * Properties key names
	 */

	private static final String USERNAME_PROPSKEY = "username";
	private static final String PASSWORD_PROPSKEY = "password";
	private static final String URL_PROPSKEY = "serverurl";
	private static final String APIVERSION_PROPSKEY = "apiversion";
	
    /*
     * Creates a MetadataConnection based on url, credentials
     */
	
    public static MetadataConnection mdLogin(String url, String user, String pwd) throws ConnectionException {
        final LoginResult loginResult = loginToSalesforce(user, pwd, url);
        return createMetadataConnection(loginResult);
    }
    
    /*
     * Creates a MetadataConnection based on a properties object
     */
    
    public static MetadataConnection mdLogin(Properties props) throws ConnectionException {
    	String username = props.getProperty(USERNAME_PROPSKEY);
    	String password = props.getProperty(PASSWORD_PROPSKEY);
    	String url = props.getProperty(URL_PROPSKEY);
    	
    	if (username != null && password != null && url != null) {
    		
    		// check if it's the full url (contains /services/Soap/u/API), add if necessary
    		
    		if (!(url.contains("/services/Soap/u/"))) {
    			url += "/services/Soap/u/" + props.getProperty(APIVERSION_PROPSKEY, APIVERSION);
    		}
    		
    		return mdLogin(url, username, password);
    	} else {
    		return null;
    	}
    }
    
    public static PartnerConnection soapLogin(String url, String user, String pwd) {
        
    	PartnerConnection conn = null;

        try {
           ConnectorConfig config = new ConnectorConfig();
           config.setUsername(user);
           config.setPassword(pwd);

           System.out.println("AuthEndPoint: " + url);
           config.setAuthEndpoint(url);

           conn = new PartnerConnection(config);
           
        } catch (ConnectionException ce) {
           ce.printStackTrace();
        } 

        return conn;
     }
    
    public static ToolingConnection toolingLogin(String url, String user, String pwd) throws ConnectionException {
    	
    	PartnerConnection conn = soapLogin(url, user, pwd);
    	
    	LoginResult lr = conn.login(user, pwd);

    	ConnectorConfig toolingConfig = new ConnectorConfig();
    	toolingConfig.setSessionId(lr.getSessionId());
    	toolingConfig.setServiceEndpoint(lr.getServerUrl().replace('u', 'T'));

    	ToolingConnection toolingConnection = com.sforce.soap.tooling.Connector.newConnection(toolingConfig);
    	
    	return toolingConnection;
    }

    private static MetadataConnection createMetadataConnection(final LoginResult loginResult) throws ConnectionException {
        final ConnectorConfig config = new ConnectorConfig();
        config.setServiceEndpoint(loginResult.getMetadataServerUrl());
        config.setSessionId(loginResult.getSessionId());
        return new MetadataConnection(config);
    }

    private static LoginResult loginToSalesforce(
            final String username,
            final String password,
            final String loginUrl) throws ConnectionException {
        final ConnectorConfig config = new ConnectorConfig();
        config.setAuthEndpoint(loginUrl);
        config.setServiceEndpoint(loginUrl);
        config.setManualLogin(true);
        return (new PartnerConnection(config)).login(username, password);
    }
}