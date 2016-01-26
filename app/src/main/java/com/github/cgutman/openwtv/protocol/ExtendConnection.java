package com.github.cgutman.openwtv.protocol;


import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Stack;

public class ExtendConnection {
    private final String baseUrl;
    private final String sessionId;

    private static final String TAG = "OWTV";

    private static final boolean verbose = true;

    private ExtendConnection(String baseUrl, String sessionId) {
        this.baseUrl = baseUrl;
        this.sessionId = sessionId;
    }

    private static String buildBaseUrl(InetAddress address, int port) {
        StringBuilder baseUrl = new StringBuilder();

        baseUrl.append("http://");

        // If it's IPv6, we'll need to escape it for the URL string
        if (address instanceof Inet6Address) {
            baseUrl.append('[');
            baseUrl.append(address.getHostAddress());
            baseUrl.append(']');
        }
        else {
            baseUrl.append(address.getHostAddress());
        }

        baseUrl.append(':');
        baseUrl.append(port);

        return baseUrl.toString();
    }

    private static String getUrlToString(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        StringBuilder strb = new StringBuilder();

        try {
            Scanner s = new Scanner(conn.getInputStream());
            try {
                while (s.hasNext()) {
                    strb.append(s.next());
                    strb.append(' ');
                }
            } finally {
                s.close();
            }
        } finally {
            conn.disconnect();
        }

        if (verbose) {
            Log.d(TAG, url + " -> " + strb.toString());
        }

        return strb.toString();
    }

    private static void verifyResponseStatus(XmlPullParser xpp) throws IOException {
        String statusMsg = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "stat");
        if (!statusMsg.equals("ok")) {
            throw new IOException("Request failed: "+statusMsg);
        }
    }

    private static String getXmlString(String data, String tagname) throws IOException {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput(new StringReader(data));
            int eventType = xpp.getEventType();
            Stack<String> currentTag = new Stack<String>();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case (XmlPullParser.START_TAG):
                        if (xpp.getName().equals("rsp")) {
                            verifyResponseStatus(xpp);
                        }
                        currentTag.push(xpp.getName());
                        break;
                    case (XmlPullParser.END_TAG):
                        currentTag.pop();
                        break;
                    case (XmlPullParser.TEXT):
                        if (currentTag.peek().equals(tagname)) {
                            return xpp.getText();
                        }
                        break;
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException e) {
            // Encapsulate the XMLPPE into an IOException
            throw new IOException(e);
        }

        return null;
    }

    private static void throwOnResponseFail(String resp) throws IOException {
        // This will cause the response status to be verified
        getXmlString(resp, "foo");
    }

    private String requestService(String serviceString) throws IOException {
        String resp = getUrlToString(baseUrl + "/services/service?method="+serviceString+"&sid="+sessionId);
        throwOnResponseFail(resp);
        return resp;
    }

    final private static char[] hexArray = "0123456789abcdef".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String md5(String str) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return bytesToHex(md.digest(str.getBytes()));
    }

    private static String buildMd5String(String password, String salt) throws NoSuchAlgorithmException {
        return md5(':' + md5(password.toLowerCase()) + ':' + salt);
    }

    public void beginTranscode(int channelId) throws IOException {
        requestService("channel.transcode.initiate&device=iPad&channel_id="+channelId);
    }

    public LinkedList<ChannelEntry> requestChannelListForGroup(int groupId) throws IOException {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput(new StringReader(requestService("channel.list&group_id="+groupId)));
            int eventType = xpp.getEventType();
            LinkedList<ChannelEntry> channelList = new LinkedList<ChannelEntry>();
            Stack<String> currentTag = new Stack<String>();

            int channelId = -1;
            String channelName = null;
            int channelNumber = -1;
            int channelType = -1;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case (XmlPullParser.START_TAG):
                        currentTag.push(xpp.getName());
                        break;
                    case (XmlPullParser.END_TAG):
                        if (currentTag.pop().equals("channel")) {
                            channelList.add(new ChannelEntry(channelId, channelName, channelNumber, channelType));
                        }
                        break;
                    case (XmlPullParser.TEXT):
                        if (currentTag.peek().equals("id")) {
                            try {
                                channelId = Integer.parseInt(xpp.getText().trim());
                            } catch (NumberFormatException e) {
                                throw new IOException("Channel list has invalid ID: "+xpp.getText());
                            }
                        } else if (currentTag.peek().equals("name")) {
                            channelName = xpp.getText().trim();
                        } else if (currentTag.peek().equals("number")) {
                            try {
                                channelNumber = Integer.parseInt(xpp.getText().trim());
                            } catch (NumberFormatException e) {
                                throw new IOException("Channel list has invalid number: "+xpp.getText());
                            }
                        }
                        else if (currentTag.peek().equals("type")) {
                            try {
                                channelType = Integer.parseInt(xpp.getText().trim());
                            } catch (NumberFormatException e) {
                                throw new IOException("Channel list has invalid type: "+xpp.getText());
                            }
                        }
                        break;
                }
                eventType = xpp.next();
            }

            return channelList;
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
    }

    private void setBitrate(int bitrate) throws IOException {
        requestService("setting.set&device=iPad&local_bitrate="+bitrate);
        requestService("setting.set&device=iPad&remote_bitrate="+bitrate);
    }

    private void setProfile(String profileString) throws IOException {
        profileString = profileString.replace(" ", "%20");
        requestService("setting.set&device=iPad&local_profile="+profileString);
        requestService("setting.set&device=iPad&remote_profile="+profileString);
    }

    public void setResolution1280x720() throws IOException {
        setProfile("(new iPad) 4096kbps, 1280x720");
        setBitrate(4096);
    }

    public void setResolution1920x1080() throws IOException {
        setProfile("(new iPad) 4096kbps, 1920x1080");
        setBitrate(4096);
    }

    public void setResolution1024x768() throws IOException {
        setProfile("(new iPad) 4096kbps, 1024x768");
        setBitrate(4096);
    }

    public TranscodeStatus requestTranscodeStatus() throws IOException {
        String resp = requestService("channel.transcode.status");

        String status = getXmlString(resp, "status");
        String finished = getXmlString(resp, "final");
        String percentage = getXmlString(resp, "percentage");

        if (status == null) {
            throw new IOException("Missing status in channel.transcode.status response");
        }
        if (finished == null) {
            throw new IOException("Missing final in channel.transcode.status response");
        }
        if (percentage == null) {
            throw new IOException("Missing percentage in channel.transcode.status response");
        }

        int percentageInt;
        try {
            percentageInt = Integer.parseInt(percentage);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid percentage in channel.transcode.status response: "+percentage);
        }

        return new TranscodeStatus(status, finished.toLowerCase().equals("true"), percentageInt);
    }

    public String getPlaybackUrl(int channelId) {
        return baseUrl + "/service/services/channelasync.m3u8?sid="+sessionId+"&channel_id="+channelId;
    }

    public static ExtendConnection establishConnection(InetAddress address, int port, String password) throws IOException {
        String baseUrl = buildBaseUrl(address, port);

        // Do the initial init request to get the session ID and salt
        String initiateResp = getUrlToString(baseUrl + "/services/service?method=session.initiate&ver=1.0&device=iPad");
        String sessionId = getXmlString(initiateResp, "sid");
        String salt = getXmlString(initiateResp, "salt");

        if (sessionId == null) {
            throw new IOException("Missing session ID in session.initiate response");
        }
        if (salt == null) {
            throw new IOException("Missing salt in session.initiate response");
        }

        ExtendConnection conn = new ExtendConnection(baseUrl, sessionId);

        // Complete the login process using the given password and salt
        try {
            conn.requestService("session.login&md5=" + buildMd5String(password, salt));
        } catch (NoSuchAlgorithmException e) {
            // MD5 should always be available
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return conn;
    }

    public static class TranscodeStatus {
        public final String status;
        public final boolean finishedBuffering;
        public final int percentage;

        public TranscodeStatus(String status, boolean finishedBuffering, int percentage) {
            this.status = status;
            this.finishedBuffering = finishedBuffering;
            this.percentage = percentage;
        }
    }

    public static class ChannelEntry {
        public final int channelId;
        public final String name;
        public final int number;
        public final int type;

        public ChannelEntry(int channelId, String name, int number, int type) {
            this.channelId = channelId;
            this.name = name;
            this.number = number;
            this.type = type;
        }
    }
}
