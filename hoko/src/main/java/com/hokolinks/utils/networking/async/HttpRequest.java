package com.hokolinks.utils.networking.async;

import com.hokolinks.Hoko;
import com.hokolinks.model.App;
import com.hokolinks.model.Device;
import com.hokolinks.model.exceptions.HokoException;
import com.hokolinks.utils.log.HokoLog;
import com.hokolinks.utils.networking.Networking;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * HttpRequest is a savable model around HttpRequests.
 * It contains the path, an operation type, parameters in the form of json and the number of
 * retries.
 */
public class HttpRequest implements Serializable, HostnameVerifier{

    // Constants
    private static final int TASK_TIMEOUT = 15000; // millis
    private static final String TASK_VERSION = "v2";
    private static final String TASK_FORMAT = "json";

    private static String sTaskEndpoint = "https://api.hokolinks.com";

    // Properties
    private HokoNetworkOperationType mOperationType;
    private String mUrl;
    private String mToken;
    private String mParameters;
    private int mNumberOfRetries;

    /**
     * Creates a request with a type, path, token and parameters.
     *
     * @param operationType The operation type (e.g. GET/PUT/POST).
     * @param url           The url (e.g. "https://api.hokolinks.com/v1/routes.json").
     * @param token         The application token.
     * @param parameters    The parameters in json string form.
     */
    public HttpRequest(HokoNetworkOperationType operationType, String url, String token,
                       String parameters) {
        mOperationType = operationType;
        mUrl = url.contains("http") ? url : HttpRequest.getURLFromPath(url);
        mToken = token;
        mParameters = parameters;
        mNumberOfRetries = 0;
    }

    // Constructors

    public static void setEndpoint(String endpoint) {
        sTaskEndpoint = endpoint;
    }

    /**
     * Generates the full URL, merging the endpoint, version, path and format.
     *
     * @param path The path component.
     * @return The full URL.
     */
    public static String getURLFromPath(String path) {
        return sTaskEndpoint + "/" + TASK_VERSION + "/" + path + "."
                + TASK_FORMAT;
    }

    private static String getUserAgent() {
        String environment = App.getEnvironment(Networking.getNetworking().getContext());

        return "HOKO/" + Hoko.VERSION + " (" + environment + "; Linux; " + Device.getPlatform() + " " +
                Device.getSystemReleaseVersion() + "; " + Device.getVendor() + " " + Device.getModel() +
                " Build/" + Device.getBuildNumber() + ")";
    }

    private static String urlEncode(String url, String jsonString) {
        if (jsonString == null) {
            return url;
        }

        String finalURL = url;
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            if (jsonObject.names().length() > 0) {
                finalURL += "?";
            }
            for (Iterator<String> iterator = jsonObject.keys(); iterator.hasNext(); ) {
                String key = iterator.next();
                finalURL += key + "=" + jsonObject.getString(key);
                if (iterator.hasNext()) {
                    finalURL += "&";
                }
            }
        } catch (JSONException e) {
            HokoLog.e(e);
        }
        return finalURL;
    }

    // Property Gets
    public HokoNetworkOperationType getOperationType() {
        return mOperationType;
    }

    public URL getUrl() {
        try {
            if (mOperationType != HokoNetworkOperationType.GET) {
                return new URL(mUrl);
            } else {
                return new URL(urlEncode(mUrl, getParameters()));
            }
        } catch (MalformedURLException e) {
            HokoLog.e(e);
        }
        return null;
    }

    public String getToken() {
        return mToken;
    }

    // Runnable

    public String getParameters() {
        return mParameters;
    }

    public int getNumberOfRetries() {
        return mNumberOfRetries;
    }

    // Networking

    public void incrementNumberOfRetries() {
        mNumberOfRetries++;
    }

    /**
     * Transforms the HttpRequest to a Runnable object so it can execute the request
     * on a background thread, usually inside a NetworkAsyncTask object.
     *
     * @return The runnable wrapper for the request.
     */
    public Runnable toRunnable() {
        return toRunnable(null);
    }

    /**
     * Transforms the HttpRequest to a Runnable object with a callback so it can execute the
     * request on a background thread, usually inside a NetworkAsyncTask object. It will then call
     * the callback functions accordingly.
     *
     * @param httpCallback The HttpRequestCallback object.e
     * @return The runnable wrapper for the request.
     */
    public Runnable toRunnable(final HttpRequestCallback httpCallback) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    switch (mOperationType) {
                        case GET:
                            performGET(httpCallback);
                            break;
                        case POST:
                            performPOST(httpCallback);
                            break;
                        case PUT:
                            performPUT(httpCallback);
                            break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    HokoLog.e(e);
                    if (httpCallback != null)
                        httpCallback.onFailure(e);
                }
            }
        };
    }

    private void applyHeaders(HttpURLConnection connection, boolean postOrPut) {
        connection.setConnectTimeout(TASK_TIMEOUT);
        connection.setReadTimeout(TASK_TIMEOUT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        if (postOrPut) {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        if (getToken() != null) {
            connection.setRequestProperty("Authorization", "Token " + getToken());
            connection.setRequestProperty("Hoko-SDK-Version", Hoko.VERSION);
            if (Networking.getNetworking() != null) {
                connection.setRequestProperty("User-Agent", HttpRequest.getUserAgent());
                connection.setRequestProperty("Hoko-SDK-Env",
                        App.getEnvironment(Networking.getNetworking().getContext()));
            }
        }
    }

    private void applyHttpsHostnameVerifier(HttpURLConnection connection, URL url) {
        if (url.getProtocol().equals("https")) {
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection)connection;
            httpsURLConnection.setHostnameVerifier(this);
        }
    }

    /**
     * Performs an HttpGet to the specified url, will handle the response with the callback.
     *
     * @param httpCallback The HttpRequestCallback object.
     * @throws IOException Throws an IOException in case of a network problem.
     */
    private void performGET(HttpRequestCallback httpCallback) throws IOException {
        URL url = getUrl();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        applyHttpsHostnameVerifier(connection, url);
        connection.setRequestMethod("GET");
        applyHeaders(connection, false);
        HokoLog.d("GET from " + getUrl());
        handleHttpResponse(connection, httpCallback);
    }

    /**
     * Performs an HttpPut to the specified url, will handle the response with the callback.
     *
     * @param httpCallback The HttpRequestCallback object.
     * @throws IOException Throws an IOException in case of a network problem.
     */
    private void performPUT(HttpRequestCallback httpCallback) throws IOException {
        URL url = getUrl();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        applyHttpsHostnameVerifier(connection, url);
        connection.setRequestMethod("PUT");
        applyHeaders(connection, true);
        if (getParameters() != null) {
            connection.setDoOutput(true);
            OutputStreamWriter outputStreamWriter =
                    new OutputStreamWriter(connection.getOutputStream());
            outputStreamWriter.write(getParameters());
            outputStreamWriter.flush();
            outputStreamWriter.close();
        }
        HokoLog.d("PUT to " + getUrl());
        handleHttpResponse(connection, httpCallback);
    }

    /**
     * Performs an HttpPost to the specified url, will handle the response with the callback.
     *
     * @param httpCallback The HttpRequestCallback object.
     * @throws IOException Throws an IOException in case of a network problem.
     */
    private void performPOST(HttpRequestCallback httpCallback) throws IOException {
        URL url = getUrl();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        applyHttpsHostnameVerifier(connection, url);
        connection.setRequestMethod("POST");
        applyHeaders(connection, true);
        HokoLog.d("POST to " + getUrl() + " with " + getParameters());
        if (getParameters() != null) {
            connection.setDoOutput(true);
            OutputStreamWriter outputStreamWriter =
                    new OutputStreamWriter(connection.getOutputStream());
            outputStreamWriter.write(getParameters());
            outputStreamWriter.flush();
            outputStreamWriter.close();
        }

        handleHttpResponse(connection, httpCallback);
    }

    /**
     * The HttpResponse handler, tries to parse the response into json, checks the status code and
     * throws exceptions accordingly. Will also use the callback to notify of the response given.
     *
     * @param connection   The HttpURLConnection object coming from a GET/POST/PUT URL connection.
     * @param httpCallback The HttpRequestCallback object.
     * @throws IOException Throws an IOException in case of a network problem.
     */
    private void handleHttpResponse(HttpURLConnection connection, HttpRequestCallback httpCallback)
            throws IOException {
        InputStream input = connection.getErrorStream();
        if (input == null) {
            input = connection.getInputStream();
        }
        if ("gzip".equals(connection.getContentEncoding())) {
            input = new GZIPInputStream(input);
        }
        String response = convertStreamToString(input);
        JSONObject jsonResponse;
        try {
            jsonResponse = new JSONObject(response);
        } catch (JSONException e) {
            try {
                jsonResponse = new JSONArray(response).getJSONObject(0);
            } catch (JSONException e2) {
                jsonResponse = new JSONObject();
            }
        }
        if (connection.getResponseCode() >= 300) {
            HokoException exception = HokoException.serverException(jsonResponse);
            HokoLog.e(exception);
            if (httpCallback != null) {
                httpCallback.onFailure(exception);
            }
        } else {
            if (httpCallback != null)
                httpCallback.onSuccess(jsonResponse);
        }
    }

    private String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        return sTaskEndpoint.contains(hostname);
    }

    // Type Enum

    /**
     * The possible network operation types: GET, POST and PUT.
     * Serializable for saving HokoHttpRequests to file.
     */
    public enum HokoNetworkOperationType implements Serializable {
        GET, POST, PUT
    }

}
