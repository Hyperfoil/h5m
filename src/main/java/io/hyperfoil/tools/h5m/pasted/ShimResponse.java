package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * A shim for the Response object from fetch()
 * https://developer.mozilla.org/en-US/docs/Web/API/Response
 */
public class ShimResponse {
    private String content;

    //member variables to provide the expected properties in javascript
    @HostAccess.Export
    public final int status;
    @HostAccess.Export
    public final String statusText;
    @HostAccess.Export
    public final JqObject headers;
    @HostAccess.Export
    public final boolean ok;
    @HostAccess.Export
    public final boolean redirected;
    @HostAccess.Export
    public final String type;
    @HostAccess.Export
    public final String url;


    public ShimResponse(String content, int status, String statusText, JqObject headers,boolean redirected,String type, String url){
        this.content = content;
        this.status = status;
        this.statusText = statusText;
        this.headers = headers;
        this.ok = status < 300 && status >= 200;
        this.redirected = redirected;
        this.type = type;
        this.url = url;
    }

    @HostAccess.Export
    public Object text(){
        return content;
    }

    @HostAccess.Export
    public Object json(){
        try {
            JqValue parsed = JqValues.parse(content);
            Object wrapped = ProxyJq.wrap(parsed);
            return new ShimThenable(){

                @Override
                public void then(Value onResolve, Value onReject) {
                    try{
                        if(onResolve.hasMember("then")){
                            onResolve.invokeMember("then",wrapped);
                        }else{
                            if(onResolve.canExecute()){
                                onResolve.execute(wrapped);
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
            };
        } catch (Exception e) {
            //throw new RuntimeException(e);
        }
        return ProxyJq.wrap(JqObject.EMPTY);
    }

    @Override
    public String toString(){return "Response";}
}
