package proof;

import com.ontotext.trree.AbstractInferencer;
import com.ontotext.trree.AbstractRepositoryConnection;
import com.ontotext.trree.sdk.Request;
import com.ontotext.trree.sdk.RequestContext;
import com.ontotext.trree.sdk.RequestOptions;
import com.ontotext.trree.sdk.SystemPluginOptions;

import java.util.HashMap;

/**
 * this is the context implementation where the plugin stores currently running patterns
 * it just keeps some values using sting keys for further access
 */
class ProofContext implements RequestContext {

    static final String REPOSITORY_CONNECTION = "repconn";
    // private key to store the inferencer in the request context
    static final String INFERENCER = "infer";
    HashMap<String, Object> map = new HashMap<String, Object>();
    Request request;

    public ProofContext(Request request) {
        this.request = request;
        if (request != null ) {
            RequestOptions ops = request.getOptions();
            if (ops != null && ops instanceof SystemPluginOptions) {
                // retrieve the inferencer from the systemPluginOptions instance
                Object obj = ((SystemPluginOptions)ops).getOption(SystemPluginOptions.Option.ACCESS_INFERENCER);
                if (obj instanceof AbstractInferencer) {
                    setAttribute(ProofContext.INFERENCER, obj);
                }
                // retrieve the repository connection from the systemPluginOptions instance
                obj = ((SystemPluginOptions)ops).getOption(SystemPluginOptions.Option.ACCESS_REPOSITORY_CONNECTION);
                if (obj instanceof AbstractRepositoryConnection) {
                    setAttribute(ProofContext.REPOSITORY_CONNECTION, obj);
                }
            }
        }
    }

    @Override
    public Request getRequest() {
        return request;
    }

    @Override
    public void setRequest(Request request) {
        this.request = request;
    }

    public Object getAttribute(String key) {
        return map.get(key);
    }

    public void setAttribute(String key, Object value) {
        map.put(key, value);
    }

    public void removeAttribute(String key) {
        map.remove(key);
    }
}
