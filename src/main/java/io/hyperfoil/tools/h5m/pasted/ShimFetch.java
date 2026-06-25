package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Proviles a fetch() method for javascript execution.
 * https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API
 */
public class ShimFetch implements ShimThenable {

    private static JqObject getDefaults(){
        return JqObject.of(
            "method", JqString.of("GET"),
            "mode", JqString.of("cors"),
            "cache", JqString.of("no-cache"),
            "credentials", JqString.of("same-origin"),
            "headers", JqObject.of("Content-Type", "application/json"),
            "redirect", JqString.of("follow"),
            "referrer", JqString.of("no-referrer"),
            "body", JqString.of("")
        );
    }

    /**
     * Merge source fields into destination. If override is false, existing keys
     * in destination are preserved. JqObject is immutable so we build a new one.
     */
    private static JqObject mergeObjects(JqObject destination, JqObject source, boolean override){
        JqObject.Builder builder = JqObject.builder();
        // Start with all destination fields
        for (Map.Entry<String, JqValue> entry : destination.entries()) {
            builder.put(entry.getKey(), entry.getValue());
        }
        // Merge source fields
        for (Map.Entry<String, JqValue> entry : source.entries()) {
            String key = entry.getKey();
            JqValue value = entry.getValue();
            if (override || !destination.has(key)) {
                builder.put(key, value);
            } else {
                JqValue destVal = destination.get(key);
                if (destVal instanceof JqArray destArr) {
                    if (value instanceof JqArray srcArr) {
                        // Concatenate arrays
                        JqValue[] combined = new JqValue[destArr.length() + srcArr.length()];
                        for (int i = 0; i < destArr.length(); i++) combined[i] = destArr.get(i);
                        for (int i = 0; i < srcArr.length(); i++) combined[destArr.length() + i] = srcArr.get(i);
                        builder.put(key, JqArray.of(combined));
                    } else {
                        builder.put(key, destArr.append(value));
                    }
                } else if (destVal instanceof JqObject destObj) {
                    if (value instanceof JqObject srcObj) {
                        builder.put(key, mergeObjects(destObj, srcObj, override));
                    } else {
                        // turn destination[key] into [destination[key], value]
                        builder.put(key, JqArray.of(destVal, value));
                    }
                }
                // else: not override, keep destination value (already in builder)
            }
        }
        return builder.build();
    }

    private Value config;
    private Value url;

    public ShimFetch(Value url, Value config){
        this.url = url;
        this.config = config;
    }
//    @HostAccess.Export
//    @Override
//    public void onPromiseCreation(ValueEntity onResolve, ValueEntity onReject){
//
//        then(onResolve,onReject);
//    }

    @HostAccess.Export
    @Override
    public void then(Value onResolve, Value onReject){
        try{
            Object rtrn = apply(url,config);
            if(onResolve.hasMember("then")){
                onResolve.invokeMember("then",rtrn);
            }else{
                if(onResolve.canExecute()){
                    onResolve.execute(rtrn);
                }
            }
        }catch(Exception e){
            if(onReject.hasMember("then")) {
                onReject.invokeMember("then", e.getMessage());
            }else{
                if(onReject.canExecute()){
                    onReject.execute(e.getMessage());
                }
            }
        }

    }

    @HostAccess.Export
    public Object apply(Value url, Value options){
        String urlString = url.isString() ? url.asString() : url.toString();
        JqObject optionsObj = Util.convertToJqObject(options);
        optionsObj = mergeObjects(optionsObj, getDefaults(), false);
        return apply(urlString, optionsObj, false);
    }
    public Object apply(String url, JqObject options, boolean redirected){
        try {
            //if the request is set to ignore tls checking
            if ("ignore".equalsIgnoreCase(options.has("tls") ? options.get("tls").asString("") : "")) {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                            public void checkClientTrusted(
                                    X509Certificate[] certs, String authType) {
                            }
                            public void checkServerTrusted(
                                    X509Certificate[] certs, String authType) {
                            }
                        }
                };
                // Install the all-trusting trust manager
                try {
                    SSLContext sc = SSLContext.getInstance("SSL");
                    sc.init(null, trustAllCerts, new java.security.SecureRandom());
                    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                } catch (GeneralSecurityException e) {
                    //what to do with the exception?
                }
            }
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            if (con instanceof HttpsURLConnection) {
                ((HttpsURLConnection) con).setHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String s, SSLSession sslSession) {
                        return true; // yikes
                    }
                });
            }
            String requestMethod = options.has("method") ? options.get("method").asString("GET") : "GET";
            boolean followRedirect = options.has("follow") && "redirect".equalsIgnoreCase(options.get("follow").asString(""));
            con.setRequestMethod(requestMethod);
            con.setInstanceFollowRedirects(followRedirect);
            con.setConnectTimeout(30_000);//TODO add configuration for 30 seconds timeout
            con.setReadTimeout(30_000);//30 seconds
            JqValue headersVal = options.get("headers");
            if (headersVal instanceof JqObject headersObj) {
                for (Map.Entry<String, JqValue> entry : headersObj.entries()) {
                    if (entry.getValue() instanceof JqString str) {
                        con.setRequestProperty(entry.getKey(), str.stringValue());
                    }
                }
            }
            //set the body if the request is post (also put?)
            if("POST".equalsIgnoreCase(requestMethod)){
                con.setDoOutput(true);
                String body = options.has("body") && options.get("body") instanceof JqString
                    ? options.get("body").asString("") : "";
                try(OutputStream os = con.getOutputStream()){
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input,0,input.length);
                }
            }

            int status = con.getResponseCode();
            if ((status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM) && followRedirect) {
                String location = con.getHeaderField("Location");
                return apply(location, options, true);//return the result of following the redirect
            }
            Reader streamReader = null;

            if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
                streamReader = new InputStreamReader(con.getErrorStream());
            } else {
                streamReader = new InputStreamReader(con.getInputStream());
            }


            String inputLine;
            StringBuffer content = new StringBuffer();
            try (BufferedReader in = new BufferedReader(streamReader)) {
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Map<String, List<String>> headerFields = con.getHeaderFields();

            JqObject.Builder headersBuilder = JqObject.builder();
            for (Map.Entry<String, List<String>> entries : headerFields.entrySet()) {
                for (String value : entries.getValue()) {
                    headersBuilder.put(entries.getKey() == null ? "" : entries.getKey(), value);
                }
            }
            JqObject headers = headersBuilder.build();
            String type = options.get("mode").asString("basic"); //not certain this is the correct place to read the type
            //https://developer.mozilla.org/en-US/docs/Web/API/Response/type
            return new ShimResponse(content.toString(),status,con.getResponseMessage(),headers,redirected,type,url);
        }catch (SocketTimeoutException e){
            return e;//someone else will turn that into json, right?
        } catch (MalformedURLException e) {
            return e;
        } catch (IOException e) {
            return e;
        }

    }

    @HostAccess.Export
    public static String btoa(Value input){
        String str = input == null ? "" : input.asString();
        return new String(Base64.getEncoder().encode(str.getBytes()), Charset.defaultCharset());
    }
    @HostAccess.Export
    public static String atob(Value input){
        String str = input == null ? "" : input.asString();
        return new String(Base64.getDecoder().decode(str.getBytes()), Charset.defaultCharset());
    }

}
