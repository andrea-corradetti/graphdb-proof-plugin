package proof;

import com.ontotext.trree.AbstractInferencer;
import com.ontotext.trree.AbstractRepositoryConnection;
import com.ontotext.trree.sdk.Request;
import com.ontotext.trree.sdk.RequestContext;
import com.ontotext.trree.sdk.RequestOptions;
import com.ontotext.trree.sdk.SystemPluginOptions;

import java.util.HashMap;

import static com.ontotext.trree.sdk.SystemPluginOptions.Option.ACCESS_INFERENCER;
import static com.ontotext.trree.sdk.SystemPluginOptions.Option.ACCESS_REPOSITORY_CONNECTION;

/**
 * this is the context implementation where the plugin stores currently running patterns
 * it just keeps some values using sting keys for further access
 */
class ProofContext implements RequestContext {

    AbstractInferencer inferencer;
    AbstractRepositoryConnection repositoryConnection;

    HashMap<String, Object> map = new HashMap<>();
    Request request;

    public ProofContext(Request request) {
        this.request = request;
        if (request != null) {
            RequestOptions ops = request.getOptions();
            if (ops instanceof SystemPluginOptions) {
                Object obj = ((SystemPluginOptions) ops).getOption(ACCESS_INFERENCER);
                if (obj instanceof AbstractInferencer) {
                    inferencer = (AbstractInferencer) obj;
                }
                obj = ((SystemPluginOptions) ops).getOption(ACCESS_REPOSITORY_CONNECTION);
                if (obj instanceof AbstractRepositoryConnection) {
                    repositoryConnection = (AbstractRepositoryConnection) obj;
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
