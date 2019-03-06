package com.kgal.packagebuilder;

import java.util.Properties;

import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.partner.LoginResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

public class LoginUtil {

    private static final String APIVERSION = "44.0";

    /*
     * Properties key names
     */

    private static final String USERNAME_PROPSKEY   = "username";
    private static final String PASSWORD_PROPSKEY   = "password";
    private static final String URL_PROPSKEY        = "serverurl";
    private static final String APIVERSION_PROPSKEY = "apiversion";

    /*
     * Creates a MetadataConnection based on url, credentials
     */

    public static MetadataConnection mdLogin(final Properties props) throws ConnectionException {
        final String username = props.getProperty(LoginUtil.USERNAME_PROPSKEY);
        final String password = props.getProperty(LoginUtil.PASSWORD_PROPSKEY);
        String url = props.getProperty(LoginUtil.URL_PROPSKEY);

        if ((username != null) && (password != null) && (url != null)) {

            // check if it's the full url (contains /services/Soap/u/API), add
            // if necessary

            if (!(url.contains("/services/Soap/u/"))) {
                url += "/services/Soap/u/" + props.getProperty(LoginUtil.APIVERSION_PROPSKEY, LoginUtil.APIVERSION);
            }

            return LoginUtil.mdLogin(url, username, password);
        } else {
            return null;
        }
    }

    /*
     * Creates a MetadataConnection based on a properties object
     */

    public static MetadataConnection mdLogin(final String url, final String user, final String pwd)
            throws ConnectionException {
        final LoginResult loginResult = LoginUtil.loginToSalesforce(user, pwd, url);
        return LoginUtil.createMetadataConnection(loginResult);
    }

    public static PartnerConnection soapLogin(final String url, final String user, final String pwd) {

        PartnerConnection conn = null;

        try {
            final ConnectorConfig config = new ConnectorConfig();
            config.setUsername(user);
            config.setPassword(pwd);

            System.out.println("AuthEndPoint: " + url);
            config.setAuthEndpoint(url);

            conn = new PartnerConnection(config);

        } catch (final ConnectionException ce) {
            ce.printStackTrace();
        }

        return conn;
    }

    private static MetadataConnection createMetadataConnection(final LoginResult loginResult)
            throws ConnectionException {
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