/*
*Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/

package org.wso2.am.integration.tests.jwt;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.am.admin.clients.user.RemoteUserStoreManagerServiceClient;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.am.integration.test.utils.bean.*;
import org.wso2.am.integration.test.utils.clients.APIPublisherRestClient;
import org.wso2.am.integration.test.utils.clients.APIStoreRestClient;
import org.wso2.am.integration.test.utils.generic.APIMTestCaseUtils;
import org.wso2.am.integration.test.utils.monitor.utils.WireMonitorServer;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.carbon.integration.common.admin.client.UserManagementClient;
import org.wso2.carbon.integration.common.utils.mgt.ServerConfigurationManager;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;


public class JWTTestCase extends APIMIntegrationBaseTest {

    private ServerConfigurationManager serverConfigurationManager;
    private UserManagementClient userManagementClient;
    private static final Log log = LogFactory.getLog(JWTTestCase.class);

    private String publisherURLHttp;
    private String storeURLHttp;
    private WireMonitorServer server;
    int hostPort = 9988;

    private String apiName = "JWTTokenTestAPI";
    private String apiContext = "tokenTest";
    private String tags = "token, jwt";
    private String wireMonitorURL = "";
    private String description = "This is test API create by API manager integration test";
    private String providerName = "admin";
    private String apiVersion = "1.0.0";
    private String applicationName = "APILifeCycleTestAPI-application";
    private String apiTier = "Gold";

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();

        publisherURLHttp = publisherUrls.getWebAppURLHttp();
        storeURLHttp = storeUrls.getWebAppURLHttp();

        //enable JWT token generation
        serverConfigurationManager = new ServerConfigurationManager(gatewayContext);
        serverConfigurationManager.applyConfigurationWithoutRestart(
                new File(getAMResourceLocation() + File.separator + "configFiles/tokenTest/" + "api-manager.xml"));

        serverConfigurationManager.applyConfiguration(new File(getAMResourceLocation() + File.separator +
                                                               "configFiles/tokenTest/" + "log4j.properties"));


        userManagementClient = new UserManagementClient(
                gatewayContext.getContextUrls().getBackEndUrl(),
                gatewayContext.getContextTenant().getContextUser().getUserName(),
                gatewayContext.getContextTenant().getContextUser().getPassword());

        URL url = new URL(gatewayUrls.getWebAppURLHttp());
        wireMonitorURL = "http://" + url.getHost() + ":" + hostPort;

        server = new WireMonitorServer(hostPort);
        server.setReadTimeOut(300);
        server.start();

        String gatewaySessionCookie = createSession(gatewayContext);
        //Load the back-end dummy API
        loadSynapseConfigurationFromClasspath(
                "artifacts" + File.separator + "AM" + File.separator + "synapseconfigs" + File.separator + "rest" +
                        File.separator + "dummy_api.xml", gatewayContext, gatewaySessionCookie);
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanUp(gatewayContext.getContextTenant().getTenantAdmin().getUserName(),
                      gatewayContext.getContextTenant().getContextUser().getPassword(),
                      storeUrls.getWebAppURLHttp(), publisherUrls.getWebAppURLHttp());
        serverConfigurationManager.restoreToLastConfiguration();
    }

    private void addAPI(String apiName, String apiVersion, String apiContext, String description, String endpointURL,
            String tags, String providerName)
            throws APIManagerIntegrationTestException, MalformedURLException, XPathExpressionException {

        APIPublisherRestClient apiPublisher = new APIPublisherRestClient(publisherURLHttp);

        apiPublisher.login(publisherContext.getContextTenant().getContextUser().getUserName(),
                publisherContext.getContextTenant().getContextUser().getPassword());

        APIRequest apiRequest = new APIRequest(apiName, apiContext, new URL(endpointURL));
        apiRequest.setTags(tags);
        apiRequest.setDescription(description);
        apiRequest.setVersion(apiVersion);
        apiRequest.setVisibility("public");
        apiRequest.setProvider(providerName);
        apiPublisher.addAPI(apiRequest);

        APILifeCycleStateRequest updateRequest = new APILifeCycleStateRequest(apiName, providerName,
                APILifeCycleState.PUBLISHED);
        apiPublisher.changeAPILifeCycleStatus(updateRequest);

    }

    @Test(groups = {"wso2.am"}, description = "Enabling JWT Token generation, admin user claims", enabled = true)
    public void testEnableJWTAndClaims() throws Exception {

        RemoteUserStoreManagerServiceClient remoteUserStoreManagerServiceClient =
                new RemoteUserStoreManagerServiceClient(
                        gatewayContext.getContextUrls().getBackEndUrl(),
                        gatewayContext.getContextTenant().getContextUser().getUserName(),
                        gatewayContext.getContextTenant().getContextUser().getPassword());

        String username = gatewayContext.getContextTenant().getContextUser().getUserName();
        String profile = "default";

        remoteUserStoreManagerServiceClient.setUserClaimValue(
                username, "http://wso2.org/claims/givenname",
                "first name", profile);

        remoteUserStoreManagerServiceClient.setUserClaimValue(
                username, "http://wso2.org/claims/lastname",
                "last name", profile);

        // restart the server since updated claims not picked unless cache expired
        serverConfigurationManager.restartGracefully();

        addAPI(apiName, apiVersion, apiContext,description,wireMonitorURL,tags,providerName);

        APIStoreRestClient apiStoreRestClient = new APIStoreRestClient(storeURLHttp);
        apiStoreRestClient.login(storeContext.getContextTenant().getContextUser().getUserName(),
                storeContext.getContextTenant().getContextUser().getPassword());

        apiStoreRestClient.addApplication(applicationName, apiTier, "", "this-is-test");
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(apiName,
                storeContext.getContextTenant().getContextUser().getUserName());
        subscriptionRequest.setApplicationName(applicationName);
        apiStoreRestClient.subscribe(subscriptionRequest);

        APPKeyRequestGenerator generateAppKeyRequest = new APPKeyRequestGenerator(applicationName);
        String responseString = apiStoreRestClient.generateApplicationKey(generateAppKeyRequest).getData();
        JSONObject response = new JSONObject(responseString);
        String accessToken = response.getJSONObject("data").getJSONObject("key").get("accessToken").toString();

        String url = gatewayUrls.getWebAppURLNhttp() + "tokenTest/1.0.0";

        APIMTestCaseUtils.sendGetRequest(url, accessToken);
        String serverMessage = server.getCapturedMessage();
        
        //check the jwt header
        String decodedJWTHeaderString = APIMTestCaseUtils.getDecodedJWTHeader(serverMessage);
        if(decodedJWTHeaderString != null) {
        	 log.debug("Decoded JWT header String = " + decodedJWTHeaderString);
        	 JSONObject jsonHeaderObject = new JSONObject(decodedJWTHeaderString);
        	 Assert.assertEquals(jsonHeaderObject.getString("typ"), "JWT");
        	 Assert.assertEquals(jsonHeaderObject.getString("alg"), "RS256");   	 
        	 
        }

        String decodedJWTString = APIMTestCaseUtils.getDecodedJWT(serverMessage);

        log.debug("Decoded JWTString = " + decodedJWTString);

        JSONObject jsonObject = new JSONObject(decodedJWTString);

        // check default claims
        checkDefaultUserClaims(jsonObject);

        // check user profile info claims
        String claim = jsonObject.getString("http://wso2.org/claims/givenname");
        assertTrue("JWT claim givenname  not received" + claim, claim.contains("first name"));

        claim = jsonObject.getString("http://wso2.org/claims/lastname");
        assertTrue("JWT claim lastname  not received" + claim, claim.contains("last name"));

        boolean bExceptionOccured = false;
        try {
            jsonObject.getString("http://wso2.org/claims/wrongclaim");
        } catch (JSONException e) {
            bExceptionOccured = true;
        }

        assertTrue("JWT claim invalid  claim received", bExceptionOccured);
    }

    /**
     * This test case is a test for the fix fix for APIMANAGER-3912, where jwt claims are attempted to retrieve from
     * an invalidated cache and hence failed. In carbon 4.2 products cache invalidation timeout is not configurable
     * and is hardcoded to 15 mins. So the test case will take approximately 15mins to complete and it will delay the
     * product build unnecessarily, hence the test case is disabled.
     *
     */
    @Test(groups = { "wso2.am" }, description = "JWT Token generation when JWT caching is enabled", enabled = false)
    public void testAPIAccessWhenJWTCachingEnabledTestCase()
            throws APIManagerIntegrationTestException, XPathExpressionException, IOException, JSONException,
            InterruptedException {

        String applicationName = "JWTTokenCacheTestApp";
        String apiName = "JWTTokenCacheTestAPI";
        String apiContext = "JWTTokenCacheTestAPI";
        String apiVersion = "1.0.0";
        String description = "JWTTokenCacheTestAPI description";
        String endpointURL = gatewayUrls.getWebAppURLNhttp() + "response";
        String apiTier = "Gold";
        String tags = "token,jwt,cache";
        int waitingSecs = 900;

        addAPI(apiName, apiVersion, apiContext, description, endpointURL, tags, providerName);

        APIStoreRestClient apiStoreRestClient = new APIStoreRestClient(storeURLHttp);
        apiStoreRestClient.login(storeContext.getContextTenant().getContextUser().getUserName(),
                storeContext.getContextTenant().getContextUser().getPassword());

        apiStoreRestClient.addApplication(applicationName, apiTier, "", "this-is-test");
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(apiName,
                storeContext.getContextTenant().getContextUser().getUserName());
        subscriptionRequest.setApplicationName(applicationName);
        apiStoreRestClient.subscribe(subscriptionRequest);

        APPKeyRequestGenerator generateAppKeyRequest = new APPKeyRequestGenerator(applicationName);
        String responseString = apiStoreRestClient.generateApplicationKey(generateAppKeyRequest).getData();
        JSONObject response = new JSONObject(responseString);
        String accessToken = response.getJSONObject("data").getJSONObject("key").get("accessToken").toString();

        String url = gatewayUrls.getWebAppURLNhttp() + apiContext + "/" + apiVersion;

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + accessToken);
        //Invoke the API
        HttpResponse httpResponse = HttpRequestUtil.doGet(url, headers);
        assertEquals("GET request failed for " + url, 200, httpResponse.getResponseCode());

        //Wait till cache is invalidated
        log.info("Waiting " + waitingSecs + " sec(s) till claims local cache is invalidated");
        Thread.sleep(waitingSecs * 1000);

        //Second attempt to invoke the API.
        httpResponse = HttpRequestUtil.doGet(url, headers);
        assertEquals("GET request failed for " + url +
                        ". Most probably due to a failed invalidated cache access to retrieve JWT claims.", 200,
                httpResponse.getResponseCode());
    }

    private void checkDefaultUserClaims(JSONObject jsonObject) throws JSONException {
        String claim = jsonObject.getString("iss");
        assertTrue("JWT assertion is invalid", claim.contains("wso2.org/products/am"));

        claim = jsonObject.getString("http://wso2.org/claims/subscriber");
        assertTrue("JWT claim subscriber invalid. Received " + claim, claim.contains("admin"));

        claim = jsonObject.getString("http://wso2.org/claims/applicationname");
        assertTrue("JWT claim applicationname invalid. Received " + claim,
                claim.contains("APILifeCycleTestAPI-application"));

        claim = jsonObject.getString("http://wso2.org/claims/applicationtier");
        assertTrue("JWT claim applicationtier invalid. Received " + claim, claim.contains("Gold"));

        claim = jsonObject.getString("http://wso2.org/claims/apicontext");
        assertTrue("JWT claim apicontext invalid. Received " + claim, claim.contains("/tokenTest" + "/"
                + jsonObject.getString("http://wso2.org/claims/version")));

        claim = jsonObject.getString("http://wso2.org/claims/version");
        assertTrue("JWT claim version invalid. Received " + claim, claim.contains("1.0.0"));

        claim = jsonObject.getString("http://wso2.org/claims/tier");
        assertTrue("JWT claim tier invalid. Received " + claim, claim.contains("Gold"));

        claim = jsonObject.getString("http://wso2.org/claims/keytype");
        assertTrue("JWT claim keytype invalid. Received " + claim, claim.contains("PRODUCTION"));

        claim = jsonObject.getString("http://wso2.org/claims/usertype");
        assertTrue("JWT claim usertype invalid. Received " + claim, claim.contains("APPLICATION"));

        claim = jsonObject.getString("http://wso2.org/claims/enduserTenantId");
        assertTrue("JWT claim enduserTenantId invalid. Received " + claim, claim.contains("-1234"));

        claim = jsonObject.getString("http://wso2.org/claims/role");
        assertTrue("JWT claim role invalid. Received " + claim,
                claim.contains("admin") && claim.contains("Internal/subscriber") && claim.contains("Internal/everyone"));
    }

    @Test(groups = {"wso2.am"}, description = "Enabling JWT Token generation, specific user claims", enabled = true)
    public void testSpecificUserJWTClaims() throws Exception {

        server.setFinished(false);
        server.start();

        String subscriberUser = "subscriberUser";
        String password = "password@123";
        String accessToken;

        if ((userManagementClient != null) &&
                !userManagementClient.userNameExists("Internal/subscriber", subscriberUser)) {
            userManagementClient.addUser(subscriberUser, password,
                    new String[]{"Internal/subscriber"}, null);
        }

        RemoteUserStoreManagerServiceClient remoteUserStoreManagerServiceClient =
                new RemoteUserStoreManagerServiceClient(
                        gatewayContext.getContextUrls().getBackEndUrl(),
                        gatewayContext.getContextTenant().getContextUser().getUserName(),
                        gatewayContext.getContextTenant().getContextUser().getPassword());

        String profile = "default";

        remoteUserStoreManagerServiceClient.setUserClaimValue(
                subscriberUser, "http://wso2.org/claims/givenname",
                "subscriberUser name", profile);

        remoteUserStoreManagerServiceClient.setUserClaimValue(
                subscriberUser, "http://wso2.org/claims/lastname",
                "subscriberUser name", profile);

        // restart the server since updated claims not picked unless cache expired
        serverConfigurationManager.restartGracefully();
        super.init();

        APIStoreRestClient apiStoreRestClient = new APIStoreRestClient(storeURLHttp);
        apiStoreRestClient.login(subscriberUser, password);

        apiStoreRestClient.addApplication(applicationName, apiTier, "", "this-is-test");
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(apiName,
                providerName);
        subscriptionRequest.setApplicationName(applicationName);
        apiStoreRestClient.subscribe(subscriptionRequest);

        APPKeyRequestGenerator generateAppKeyRequest = new APPKeyRequestGenerator(applicationName);
        String responseString = apiStoreRestClient.generateApplicationKey(generateAppKeyRequest).getData();
        JSONObject response = new JSONObject(responseString);
        accessToken = response.getJSONObject("data").getJSONObject("key").get("accessToken").toString();

        String url = gatewayUrls.getWebAppURLNhttp() + "tokenTest/1.0.0/";

        APIMTestCaseUtils.sendGetRequest(url, accessToken);
        String serverMessage = server.getCapturedMessage();

        Assert.assertTrue(serverMessage.contains("X-JWT-Assertion"), "JWT assertion not in the header");

        String decodedJWTString = APIMTestCaseUtils.getDecodedJWT(serverMessage);

        log.debug("Decoded JWTString = " + decodedJWTString);

        JSONObject jsonObject = new JSONObject(decodedJWTString);

        // check claims
        String claim = jsonObject.getString("iss");
        assertTrue("JWT assertion is invalid", claim.contains("wso2.org/products/am"));

        claim = jsonObject.getString("http://wso2.org/claims/subscriber");
        assertTrue("JWT claim subscriber invalid. Received " + claim, claim.contains("subscriberUser"));

        claim = jsonObject.getString("http://wso2.org/claims/applicationname");
        assertTrue("JWT claim applicationname invalid. Received " + claim,
                claim.contains("APILifeCycleTestAPI-application"));

    }


    @Test(groups = {"wso2.am"}, description = "Enabling JWT Token generation, tenant user claims", enabled = false)
    public void testTenantUserJWTClaims() throws Exception {

        server.setFinished(false);
        server.start();

        serverConfigurationManager.restartGracefully();
        super.init();

        String provider = "admin@wso2.com";
        String tenantUser = "admin@wso2.com";
        String password = "wso2@123";
        String accessToken;

        APIPublisherRestClient apiPublisherRestClient = new APIPublisherRestClient(publisherURLHttp);

        apiPublisherRestClient.login(tenantUser, password);

        APIRequest apiRequest = new APIRequest(apiName, apiContext, new URL(wireMonitorURL));
        apiRequest.setTags(tags);
        apiRequest.setDescription(description);
        apiRequest.setVersion(apiVersion);
        apiRequest.setWsdl("https://svn.wso2.org/repos/wso2/carbon/platform/trunk/products" +
                "/bps/modules/samples/product/src/main/resources/bpel/2.0/MyRoleMexTestProcess/echo.wsdl");
        apiRequest.setVisibility("public");

        apiRequest.setProvider(provider);
        apiPublisherRestClient.addAPI(apiRequest);

        APILifeCycleStateRequest updateRequest = new APILifeCycleStateRequest(apiName, provider,
                APILifeCycleState.PUBLISHED);
        apiPublisherRestClient.changeAPILifeCycleStatus(updateRequest);

        APIStoreRestClient apiStoreRestClient = new APIStoreRestClient(storeURLHttp);
        apiStoreRestClient.login(tenantUser, password);

        apiStoreRestClient.addApplication(applicationName, apiTier, "", "this-is-test");
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(apiName,
                provider);
        subscriptionRequest.setApplicationName(applicationName);
        apiStoreRestClient.subscribe(subscriptionRequest);

        APPKeyRequestGenerator generateAppKeyRequest = new APPKeyRequestGenerator(applicationName);
        String responseString = apiStoreRestClient.generateApplicationKey(generateAppKeyRequest).getData();
        JSONObject response = new JSONObject(responseString);
        accessToken = response.getJSONObject("data").getJSONObject("key").get("accessToken").toString();

        String url = gatewayUrls.getWebAppURLNhttp() + "t/wso2.com/tokenTest/1.0.0/";
        APIMTestCaseUtils.sendGetRequest(url, accessToken);
        String serverMessage = server.getCapturedMessage();

        String decodedJWTString = APIMTestCaseUtils.getDecodedJWT(serverMessage);

        JSONObject jsonObject = new JSONObject(decodedJWTString);

        log.debug("Decoded JWTString = " + decodedJWTString);
        // check claims
        String claim = jsonObject.getString("iss");
        assertTrue("JWT assertion is invalid", claim.contains("wso2.org/products/am"));

        claim = jsonObject.getString("http://wso2.org/claims/subscriber");
        assertTrue("JWT claim subscriber invalid. Received " + claim, claim.contains("admin@wso2.com"));

        claim = jsonObject.getString("http://wso2.org/claims/apicontext");
        assertTrue("JWT claim apicontext invalid. Received " + claim, claim.contains("/t/wso2.com/tokenTest"));

    }
}
