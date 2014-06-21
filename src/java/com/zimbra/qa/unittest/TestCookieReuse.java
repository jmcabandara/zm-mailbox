/*
 * ***** BEGIN LICENSE BLOCK *****
 * 
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2014 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.qa.unittest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Assert;
import org.junit.Test;

import com.zimbra.client.ZMailbox;
import com.zimbra.common.auth.ZAuthToken;
import com.zimbra.common.httpclient.HttpClientUtil;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.Element.JSONElement;
import com.zimbra.common.soap.Element.XMLElement;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.common.soap.SoapHttpTransport;
import com.zimbra.common.soap.SoapProtocol;
import com.zimbra.common.soap.SoapUtil;
import com.zimbra.common.util.ZimbraHttpConnectionManager;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.ZimbraAuthToken;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.soap.JaxbUtil;
import com.zimbra.soap.account.message.EndSessionRequest;
import com.zimbra.soap.mail.message.SearchRequest;
import com.zimbra.soap.mail.message.SearchResponse;
import com.zimbra.soap.type.SearchHit;

public class TestCookieReuse extends TestCase {
    private static final String NAME_PREFIX = TestUserServlet.class.getSimpleName();
    private static final String USER_NAME = "user1";
    private int currentSupportedAuthVersion = 2;
    @Override
    public void setUp()
    throws Exception {
        cleanUp();
        // Add a test message, in case the account is empty.
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        TestUtil.addMessage(mbox, NAME_PREFIX);
    }

    @Override
    public void tearDown()
    throws Exception {
        cleanUp();
    }

    private void cleanUp()
    throws Exception {
    	Provisioning.getInstance().getLocalServer().setLowestSupportedAuthVersion(currentSupportedAuthVersion);
        TestUtil.deleteTestData(USER_NAME, NAME_PREFIX);
    }

    public static void main(String[] args)
    throws Exception {
        TestUtil.cliSetup();
        TestUtil.runTest(TestCookieReuse.class);
    }

    /**
     * Verify that we can use the cookie for REST session if the session is valid
     */
    @Test
    public void testValidCookie() throws ServiceException, IOException {
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        URI uri = mbox.getRestURI("Inbox?fmt=rss");

        HttpClient client = mbox.getHttpClient(uri);
        GetMethod get = new GetMethod(uri.toString());
        int statusCode = HttpClientUtil.executeMethod(client, get);
        assertEquals("This request sohuld succeed. Getting status code " + statusCode, HttpStatus.SC_OK,statusCode);
    }

    /**
     * Verify that we can RE-use the cookie for REST session if the session is valid
     */
    @Test
    public void testValidSessionCookieReuse() throws ServiceException, IOException {
        //establish legitimate connection
    	ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        URI uri = mbox.getRestURI("Inbox?fmt=rss");
        HttpClient alice = mbox.getHttpClient(uri);
        //create evesdropper's connection
        HttpClient eve = ZimbraHttpConnectionManager.getInternalHttpConnMgr().newHttpClient();
        Cookie[] cookies = alice.getState().getCookies();
        HttpState state = new HttpState();
        for (int i=0;i<cookies.length;i++) {
        	Cookie cookie = cookies[i];
            state.addCookie(new Cookie(uri.getHost(), cookie.getName(), cookie.getValue(), "/", null, false));
        }
        eve.setState(state);
        GetMethod get = new GetMethod(uri.toString());
        int statusCode = HttpClientUtil.executeMethod(eve, get);
        assertEquals("This request should succeed. Getting status code " + statusCode, HttpStatus.SC_OK,statusCode);
    }

    /**
     * Verify that we canNOT RE-use the cookie for REST session if the session is valid
     */
    @Test
    public void testAutoEndSession() throws ServiceException, IOException {
        //establish legitimate connection
    	TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraForceClearCookies, "TRUE");
    	ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        URI uri = mbox.getRestURI("Inbox?fmt=rss");
        HttpClient alice = mbox.getHttpClient(uri);

        //create evesdropper's connection
        HttpClient eve = ZimbraHttpConnectionManager.getInternalHttpConnMgr().newHttpClient();
        Cookie[] cookies = alice.getState().getCookies();
        HttpState state = new HttpState();
        for (int i=0;i<cookies.length;i++) {
        	Cookie cookie = cookies[i];
            state.addCookie(new Cookie(uri.getHost(), cookie.getName(), cookie.getValue(), "/", null, false));
        }
        eve.setState(state);
        Account a = TestUtil.getAccount(USER_NAME);
        a.setForceClearCookies(true);

        EndSessionRequest esr = new EndSessionRequest();
        mbox.invokeJaxb(esr);
        GetMethod get = new GetMethod(uri.toString());
        int statusCode = HttpClientUtil.executeMethod(eve, get);
        assertEquals("This request should not succeed. Getting status code " + statusCode, HttpStatus.SC_UNAUTHORIZED,statusCode);
    }

    /**
     * Verify that we canNOT RE-use the cookie taken from a legitimate HTTP session for a REST request after ending the original session
     */
    @Test
    public void testForceEndSession() throws ServiceException, IOException {
        //establish legitimate connection
    	TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraForceClearCookies, "FALSE");
    	ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        URI uri = mbox.getRestURI("Inbox?fmt=rss");
        HttpClient alice = mbox.getHttpClient(uri);

        //create evesdropper's connection
        HttpClient eve = ZimbraHttpConnectionManager.getInternalHttpConnMgr().newHttpClient();
        Cookie[] cookies = alice.getState().getCookies();
        HttpState state = new HttpState();
        for (int i=0;i<cookies.length;i++) {
        	Cookie cookie = cookies[i];
            state.addCookie(new Cookie(uri.getHost(), cookie.getName(), cookie.getValue(), "/", null, false));
        }
        eve.setState(state);
        Account a = TestUtil.getAccount(USER_NAME);
        a.setForceClearCookies(false);

        EndSessionRequest esr = new EndSessionRequest();
        esr.setLogOff(true);
        mbox.invokeJaxb(esr);
        GetMethod get = new GetMethod(uri.toString());
        int statusCode = HttpClientUtil.executeMethod(eve, get);
        assertEquals("This request should not succeed. Getting status code " + statusCode, HttpStatus.SC_UNAUTHORIZED,statusCode);
    }

    /**
     * Verify that we canNOT RE-use the cookie taken from a legitimate HTTP session for a SOAP request after ending the original session
     */
    @Test
    public void testInvalidSearchRequest() throws ServiceException, IOException {
        //establish legitimate connection
    	TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraForceClearCookies, "FALSE");
    	ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        URI uri = mbox.getRestURI("Inbox?fmt=rss");
        mbox.getHttpClient(uri);
        ZAuthToken authT = mbox.getAuthToken();

        //create evesdropper's SOAP client
        SoapHttpTransport transport = new HttpCookieSoapTransport(TestUtil.getSoapUrl());
        transport.setAuthToken(authT);

        //check that search returns something
        SearchRequest searchReq = new SearchRequest();
        searchReq.setSearchTypes(MailItem.Type.MESSAGE.toString());
        searchReq.setQuery("in:inbox");

        Element req = JaxbUtil.jaxbToElement(searchReq, SoapProtocol.SoapJS.getFactory());
        Element res = transport.invoke(req);
        SearchResponse searchResp = JaxbUtil.elementToJaxb(res);
        List<SearchHit> searchHits = searchResp.getSearchHits();
        assertFalse("this search request should return some conversations", searchHits.isEmpty());


        //explicitely end cookie session
        Account a = TestUtil.getAccount(USER_NAME);
        a.setForceClearCookies(false);
        EndSessionRequest esr = new EndSessionRequest();
        esr.setLogOff(true);
        mbox.invokeJaxb(esr);

        //check that search returns nothing
        transport = new HttpCookieSoapTransport(TestUtil.getSoapUrl());
        transport.setAuthToken(authT);
        searchReq = new SearchRequest();
        searchReq.setSearchTypes(MailItem.Type.MESSAGE.toString());
        searchReq.setQuery("in:inbox");
        try {
	        req = JaxbUtil.jaxbToElement(searchReq, SoapProtocol.SoapJS.getFactory());
	        res = transport.invoke(req);
	        searchResp = JaxbUtil.elementToJaxb(res);
	        searchHits = searchResp.getSearchHits();
	        assertTrue("this search request should fail", searchHits.isEmpty());
        } catch (SoapFaultException ex) {
        	assertEquals("Should be getting 'auth required' exception", ServiceException.AUTH_EXPIRED, ex.getCode());
        }
    }

    /**
     * Verify that we canNOT RE-use the cookie for REST session after logging out of plain HTML client
     * @throws URISyntaxException
     * @throws InterruptedException
     */
    @Test
    public void testWebLogOut() throws ServiceException, IOException, URISyntaxException, InterruptedException {
        //establish legitimate connection
    	TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraForceClearCookies, "FALSE");
    	ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        URI uri = mbox.getRestURI("Inbox?fmt=rss");
        HttpClient alice = mbox.getHttpClient(uri);

        //create evesdropper's connection
        HttpClient eve = ZimbraHttpConnectionManager.getInternalHttpConnMgr().newHttpClient();
        Cookie[] cookies = alice.getState().getCookies();
        HttpState state = new HttpState();
        for (int i=0;i<cookies.length;i++) {
        	Cookie cookie = cookies[i];
            state.addCookie(new Cookie(uri.getHost(), cookie.getName(), cookie.getValue(), "/", null, false));
        }
        eve.setState(state);
        Account a = TestUtil.getAccount(USER_NAME);
        a.setForceClearCookies(false);

        URI logoutUri = new URI("http://" + uri.getHost() +  (uri.getPort() > 80 ? (":" + uri.getPort()) : "") + "/?loginOp=logout");
        GetMethod logoutMethod = new GetMethod(logoutUri.toString());
        int statusCode = alice.executeMethod(logoutMethod);
        assertEquals("Log out request should succeed. Getting status code " + statusCode, HttpStatus.SC_OK,statusCode);
        GetMethod get = new GetMethod(uri.toString());
        statusCode = HttpClientUtil.executeMethod(eve, get);
        assertEquals("This request should not succeed. Getting status code " + statusCode, HttpStatus.SC_UNAUTHORIZED,statusCode);
    }
    @Test
    public void testTokenRegistration () throws Exception {
    	Account a = TestUtil.getAccount(USER_NAME);
    	ZimbraAuthToken at = new ZimbraAuthToken(a);
    	Assert.assertTrue("token should be registered", at.isRegistered());
    }

    @Test
    public void testTokenDeregistration () throws Exception {
    	Account a = TestUtil.getAccount(USER_NAME);
    	ZimbraAuthToken at = new ZimbraAuthToken(a);
    	Assert.assertTrue("token should be registered", at.isRegistered());
    	at.deRegister();
    	Assert.assertFalse("token should not be registered", at.isRegistered());
    }

    @Test
    public void testTokenExpiredTokenDeregistration() throws Exception {
    	Account a = TestUtil.getAccount(USER_NAME);
    	ZimbraAuthToken at = new ZimbraAuthToken(a, System.currentTimeMillis() - 1000);

    	ZimbraAuthToken at2 = new ZimbraAuthToken(a, System.currentTimeMillis() + 10000);

    	Assert.assertFalse("First token should not be registered", at.isRegistered());
    	Assert.assertTrue("Second token should be registered", at2.isRegistered());
    }

    @Test
    public void testOldClientSupport() throws Exception {
    	Account a = TestUtil.getAccount(USER_NAME);
    	ZimbraAuthToken at = new ZimbraAuthToken(a, System.currentTimeMillis() - 1000);
    	Assert.assertTrue("token should be registered", at.isRegistered());
    	at.deRegister();
    	Assert.assertFalse("token should not be registered", at.isRegistered());

    	//lowering supported auth version should allow unregistered cookies
    	Provisioning.getInstance().getLocalServer().setLowestSupportedAuthVersion(1);
    	Assert.assertTrue("token should appear to be registered", at.isRegistered());

    	//raising supported auth version should not allow unregistered cookies
    	Provisioning.getInstance().getLocalServer().setLowestSupportedAuthVersion(2);
    	Assert.assertFalse("token should not be registered", at.isRegistered());
    }

    @Test
    public void testChangingSupportedAuthVersion() throws Exception {
        Provisioning.getInstance().getLocalServer().setLowestSupportedAuthVersion(2);
        Account a = TestUtil.getAccount(USER_NAME);
        String[] tokens = a.getAuthTokens();
        ZimbraAuthToken at1 = new ZimbraAuthToken(a, System.currentTimeMillis() + 10000);
        String[] tokens2 = a.getAuthTokens();
        assertEquals("should have one more registered token", tokens.length+1,tokens2.length);

        Provisioning.getInstance().getLocalServer().setLowestSupportedAuthVersion(1);
        ZimbraAuthToken at2 = new ZimbraAuthToken(a, System.currentTimeMillis() + 10000);
        String[] tokens3 = a.getAuthTokens();
        assertEquals("should have the same number of registered tokens as before", tokens2.length,tokens3.length);

    }
    @Test
    public void testClearCookies () throws Exception {
    	Account a = TestUtil.getAccount(USER_NAME);
    	a.setForceClearCookies(true);
    	ZimbraAuthToken at = new ZimbraAuthToken(a);
    	Assert.assertTrue("token should be registered", at.isRegistered());
    	at.deRegister();
    	Assert.assertFalse("token should not be registered", at.isRegistered());
    }

    /**
     * version of SOAP transport that uses HTTP cookies instead of SOAP message header for transporting Auth Token
     * @author gsolovyev
     */
    private class HttpCookieSoapTransport extends SoapHttpTransport {

		public HttpCookieSoapTransport(String uri) {
			super(uri);
		}

		  @Override
		protected final Element generateSoapMessage(Element document, boolean raw, boolean noSession,
		            String requestedAccountId, String changeToken, String tokenType) {

		        // don't use the default protocol version if it's incompatible with the passed-in request
		        SoapProtocol proto = getRequestProtocol();
		        if (proto == SoapProtocol.SoapJS) {
		            if (document instanceof XMLElement)
		                proto = SoapProtocol.Soap12;
		        } else {
		            if (document instanceof JSONElement)
		                proto = SoapProtocol.SoapJS;
		        }
		        SoapProtocol responseProto = getResponseProtocol() == null ? proto : getResponseProtocol();

		        String targetId = requestedAccountId != null ? requestedAccountId : getTargetAcctId();
		        String targetName = targetId == null ? getTargetAcctName() : null;

		        Element context = null;
		        if (generateContextHeader()) {
		            context = SoapUtil.toCtxt(proto, null);
		            if (noSession) {
		                SoapUtil.disableNotificationOnCtxt(context);
		            } else {
		                SoapUtil.addSessionToCtxt(context, getAuthToken() == null ? null : getSessionId(), getMaxNotifySeq());
		            }
		            SoapUtil.addTargetAccountToCtxt(context, targetId, targetName);
		            SoapUtil.addChangeTokenToCtxt(context, changeToken, tokenType);
		            SoapUtil.addUserAgentToCtxt(context, getUserAgentName(), getUserAgentVersion());
		            if (responseProto != proto) {
		                SoapUtil.addResponseProtocolToCtxt(context, responseProto);
		            }

		        }
		        Element envelope = proto.soapEnvelope(document, context);
		        return envelope;
		    }
    }
}
