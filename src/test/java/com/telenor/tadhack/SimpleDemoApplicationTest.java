package com.telenor.tadhack;

import org.apache.commons.codec.binary.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Simple, bare-bone demo of messaging api. NOT PRODUCTION CODE.
 *
 */
public class SimpleDemoApplicationTest{
    private final static Logger logger = Logger.getLogger(SimpleDemoApplicationTest.class.getName());
    private static WebDriver driver;
    //  websocket. ok this is a bit ugly
    private WebSocketClientEndpoint clientEndPoint;

    //urls for test: https://test-apigw.telenor.no/...
    private static final String authorizeUri = "https://test-apigw.telenor.no/oauth/v2/authorize";
    private static final String tokenUri = "https://test-apigw.telenor.no/oauth/v2/token";
    private static final String messageUri = "https://test-apigw.telenor.no/mobile-messaging/v2/";//{endUserId}/sms";
    private static final String messageSubscbriptionUri = "https://test-apigw.telenor.no/mobile-messaging-notification-subscription/v2/";//{endUserId}
    private static final String messageStoreUri = "https://test-apigw.telenor.no/mobile-messaging-store/v2/";//{endUserId}/messages

    //replace with own credentials!
    private final String clientId = "USGBO9NGw7etYsVoD3lDvUxrMBQIeEty";
    private final String clientSecret ="***";
    private final String msisdn = "***";
    private final String username = "****";
    private final String password = "****";
    private final String appname = "MartasTadhack"; //can be anything

    //for push messages
    private String topsUri = "wss://tops.telenor.no/ws";
    private String getTopsId =" {\"id\":123546789,\"method\":\"subscribe\",\"params\":{\"clientname\":\""+appname+"\"},\"jsonrpc\":\"2.0\"}";


    @Before
    public void initDriver() throws InterruptedException {
        System.setProperty("webdriver.chrome.driver", "c://dev/div/chromedriver.exe");
        driver = new ChromeDriver();
    }

    @Test
    public void fullTest() throws IOException, InterruptedException {

        //get access token first
        String access_token = authorizeApplication();
        assertNotNull(access_token);
        //try sending an SMS
        String result=postSendSMS(access_token, msisdn);
        logger.info("<--" +result);
    //    assertNotNull(result);
        //ok. cool. now for greater things...

        //connect to websocket and get a TOPS id
        String topsId= getTopsID();
        assertNotNull(topsId);
        //subcribe to get notifications on one or more of your msisdns
        subscribeToNotifictaions(access_token,msisdn,topsId);
        //just in case
        Thread.sleep(100);
        //ok, let's fire an SMS to yourself and see if you get a copy
        postSendSMS(access_token, msisdn);
        //let's insert dumb wait
        Thread.sleep(10000);
        //let's assume you got a notification (check the log output) yeah, this is really simplified
        //then you know you've got 1-2 new messages and can go fetch!
        String jsonList= getMessages(access_token,msisdn);
        assertNotNull(jsonList);

    }

    /**
     * What you need to let the user authorize the app

     */
    private String authorizeApplication() throws IOException, InterruptedException {
        System.setProperty("webdriver.chrome.driver", "c://dev/div/chromedriver.exe");
        driver = new ChromeDriver();
        driver.get(authorizeUri+"?client_id="+clientId);
        simulateUserAuthentication(driver, false);
        // Wait for the page to load, timeout after 10 seconds
        (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>() {
            public Boolean apply(WebDriver d) {
                return d.getCurrentUrl().toLowerCase().contains("tadschmack");
            }
        });

        String uri = driver.getCurrentUrl();
        assertTrue(uri.contains("code="));
        String code = null;
        String queryString = new URL(uri).getQuery();
        String[] params = queryString.split("&");

        for (String param : params) {
            if (param.startsWith("code=")) {
                code = param.substring(param.indexOf('=') + 1);
            }
        }
        assertNotNull(code);
        Thread.sleep(10);
        String response = getAccessToken(code);
        logger.info("response <-- " + response);
        //library-free json parsing..assuming following format. DON'T do this at home. Use a LIBRARY (gson, jackson...)
        // {"access_token" : "xxxx", "expires_in" : "xxx"}

        int i = response.indexOf(": \"")+3;
        int j = response.indexOf("\"",i);

        String accessToken = response.substring(i,j);
        //again..never do this
        logger.info("access_token= " + accessToken);
        return accessToken;


    }

    /**
     * Automated user interaction this would normally happen in a webview or in the browser
     * */
    private void simulateUserAuthentication(WebDriver driver, boolean rememberMe) throws InterruptedException {

        WebElement usernameField = driver.findElement(By.id("username"));
        usernameField.sendKeys(username);
        WebElement passwordField = driver.findElement(By.id("password"));
        passwordField.sendKeys(password);
      /*  if(rememberMe){
            WebElement rememberMeLabel = driver.findElement(By.xpath("//label[@for='rememberMe']"));
            rememberMeLabel.click();
        }*/
        WebElement loginButton = driver.findElement(By.xpath("//button"));
        loginButton.click();
    }

    /**
     * What you need to do to get the AccessToken
     * Feel free to replace with apache http client, jersey client etc, but keeping it frameworkfree
     */
    private String getAccessToken(String code){
        try {
            StringBuilder sb = new StringBuilder();
            URL url = new URL(tokenUri);

            URLConnection urlConnection = url.openConnection();
            String authString = clientId + ":" + clientSecret;
            String authStringEnc = new String(Base64.encodeBase64(authString.getBytes()));
            urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
            HttpURLConnection conn= (HttpURLConnection) urlConnection;
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
            Map<String,Object> params = new LinkedHashMap<>();
            params.put("grant_type", "authorization_code");
            params.put("code", code);

            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String,Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            conn.setRequestProperty( "Content-Length", Integer.toString(postDataBytes.length));
            conn.getOutputStream().write(postDataBytes);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private class MessageHandler implements WebSocketClientEndpoint.MessageHandler{
        String topsId;


        public void handleMessage(String message) {
            logger.info(message);
            if(message.contains("method\":\"notify\"")) {
                //dosomething smart
            }
            else if(message.contains("result\":{\"topsid\"")) {
                int i = message.indexOf("\":\"") + 3;
                int j = message.indexOf("\"", i);
                topsId= message.substring(i, j);
                logger.info("topsid="+topsId);
            }
        }
    }

    private String getTopsID(){
        try {
            // open websocket
            clientEndPoint = new WebSocketClientEndpoint(new URI(topsUri));

            // add listener
            MessageHandler msghndlr= new MessageHandler();
            clientEndPoint.addMessageHandler(msghndlr);

            // send message to websocket
            clientEndPoint.sendMessage(getTopsId);

            // wait 5 seconds for messages from websocket
            Thread.sleep(2000);
            return msghndlr.topsId;

        } catch (InterruptedException ex) {
            logger.warning("InterruptedException exception: " + ex.getMessage());
        } catch (URISyntaxException ex) {
            logger.severe("URISyntaxException exception: " + ex.getMessage());
            fail();
        }
        return null;
    }


    /**
     *
     * Subscribing to notification for a given msisdn. It must be an msisdn that you're registered as user for
     */
    private void subscribeToNotifictaions(String access_token, String msisdn, String topsId) {

        try {
            StringBuilder sb = new StringBuilder();
            URL url = new URL(messageSubscbriptionUri+"tel:"+msisdn);

            HttpURLConnection conn= (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
            conn.setRequestProperty( "Authorization", "Bearer "+access_token );
            Map<String,Object> params = new LinkedHashMap<>();
            params.put("deliveryService", "TOPS");
            params.put("deliveryServiceDestination", topsId);
            params.put("product", "MineMeldinger");

            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String,Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            conn.setRequestProperty( "Content-Length", Integer.toString(postDataBytes.length));
            conn.getOutputStream().write(postDataBytes);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            logger.info(sb.toString());
        } catch (Exception e) {
            logger.severe(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     *
     *Your basic send sms
     */
    private String postSendSMS(String access_token, String msisdn){
        try {
            StringBuilder sb = new StringBuilder();
            URL url = new URL(messageUri+"tel:"+msisdn+"/sms");

            HttpURLConnection conn= (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
            conn.setRequestProperty( "Authorization", "Bearer "+access_token );
            Map<String,Object> params = new LinkedHashMap<>();
            params.put("recipients", msisdn);
            params.put("message", "Hello world!");

            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String,Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            conn.setRequestProperty( "Content-Length", Integer.toString(postDataBytes.length));
            conn.getOutputStream().write(postDataBytes);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetching messages from message store. You can use this without the push mechanism,
     * but you have to have the "mine meldinger" product activated on your subscription.
     * This is to ensure that you've read and given explicit consent for us to temporarily store sms-messages.
     */
    private String getMessages(String access_token, String msisdn) {

        try {
            StringBuilder sb = new StringBuilder();
            URL url = new URL(messageStoreUri+"tel:"+msisdn+"/messages");

            HttpURLConnection conn= (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty( "Authorization", "Bearer "+access_token );


            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            logger.info(sb.toString());
            return sb.toString();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @After
    public void closeBrowser() throws IOException {
        if(driver!=null) {
            driver.close();
            driver.quit();
        }
    }



}



