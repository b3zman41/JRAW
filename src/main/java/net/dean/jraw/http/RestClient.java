package net.dean.jraw.http;

import com.google.common.util.concurrent.RateLimiter;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import net.dean.jraw.JrawUtils;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class provides a way to send RESTful HTTP requests
 */
public abstract class RestClient<T extends RestResponse> implements NetworkAccessible<T, RestClient<T>> {
    private final String defaultHost;
    private final RateLimiter rateLimiter;
    /** The OkHttpClient used to execute RESTful HTTP requests */
    protected final OkHttpClient http;
    /** The CookieStore that will contain all the cookies saved by {@link #http} */
    protected final CookieStore cookieJar;

    /** A list of Requests sent in the past */
    protected final LinkedHashMap<T, LocalDateTime> history;
    /** A list of headers to be sent for request */
    protected final Map<String, String> defaultHeaders;
    private boolean useHttpsDefault;
    private boolean enforceRatelimit;


    /**
     * Instantiates a new RestClient
     *
     * @param defaultHost The host on which to operate
     * @param userAgent The User-Agent header which will be sent with all requests
     * @param requestsPerMinute The amount of HTTP requests that can be sent in one minute. A value greater than 0 will
     *                          enable rate limit enforcing, one less than or equal to 0 will disable it. This value cannot
     *                          be changed aft
     */
    public RestClient(String defaultHost, String userAgent, int requestsPerMinute) {
        this.defaultHost = defaultHost;
        this.enforceRatelimit = requestsPerMinute > 0;
        this.rateLimiter = enforceRatelimit ? RateLimiter.create((double) requestsPerMinute / 60) : null;
        this.http = new OkHttpClient();
        CookieManager manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        http.setCookieHandler(manager);
        this.cookieJar = manager.getCookieStore();
        this.history = new LinkedHashMap<>();
        this.useHttpsDefault = false;
        this.defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", userAgent);
    }

    public String getDefaultHost() {
        return defaultHost;
    }

    /**
     * Checks to see if RequestBuilders returned from {@link #request()} will be executed with HTTPS. Note that this can
     * be changed per request later.
     * @return If HTTPS will be used by default
     */
    public boolean isHttpsDefault() {
        return useHttpsDefault;
    }

    /**
     * Sets whether or not RequestBuilders returned from {@link #request()} will be executed with HTTPS. Note that this
     * can be changed per request later
     * @param useHttpsDefault If HTTPS will be used by default
     */
    public void setHttpsDefault(boolean useHttpsDefault) {
        this.useHttpsDefault = useHttpsDefault;
    }

    @Override
    public RestRequest.Builder request() {
        return addDefaultHeaders(new RestRequest.Builder()
                .host(defaultHost)
                .https(useHttpsDefault));
    }

    private RestRequest.Builder addDefaultHeaders(RestRequest.Builder builder) {
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    /**
     * Sets the time in milliseconds the HTTP client will wait before timing out
     * @param milliseconds Timeout length in milliseconds
     */
    public void setTimeoutLength(long milliseconds) {
        http.setConnectTimeout(milliseconds, TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the time in milliseconds the HTTP client will wait before timing out
     * @return Timeout length in milliseconds
     */
    public long getTimeoutLength() {
        return http.getConnectTimeout();
    }

    /**
     * Whether to automatically manage the execution of HTTP requests based on time (enabled by default). If there has
     * been more than 30 requests in the last minute, this class will wait to execute the next request in order to
     * minimize the chance of getting IP banned by Reddit, or simply having the API return a 403.
     *
     * @param enabled Whether to enable request management
     */
    public void setEnforceRatelimit(boolean enabled) {
        this.enforceRatelimit = enabled;
    }

    public boolean isEnforcingRatelimit() {
        return enforceRatelimit;
    }

    @Override
    public T execute(RestRequest request) throws NetworkException {
        if (enforceRatelimit) {
            if (!rateLimiter.tryAcquire()) {
                JrawUtils.logger().info("Slept for {} seconds", rateLimiter.acquire());
            }
        }

        Request r = request.getRequest();
        try {
            Response response = http.newCall(r).execute();

            JrawUtils.logger().info("{} {}", r.method(), r.url());
            if (!response.isSuccessful()) {
                throw new NetworkException(response.code());
            }
            if (request.getFormArgs() != null) {
                for (Map.Entry<String, String> entry : request.getFormArgs().entrySet()) {
                    String val = request.isSensitive(entry.getKey()) ? "<sensitive>" : entry.getValue();
                    JrawUtils.logger().info("    {}={}", entry.getKey(), val);
                }
            }

            T genericResponse = initResponse(http.newCall(r).execute());

            history.put(genericResponse, LocalDateTime.now());
            return genericResponse;
        } catch (IOException e) {
            throw new NetworkException("Could not execute the request: " + r, e);
        }
    }

    @Override
    public RestClient<T> getCreator() {
        return this;
    }

    /**
     * Gets the User-Agent header for this RestClient
     * @return The value of the User-Agent header
     */
    public String getUserAgent() {
        return defaultHeaders.get("User-Agent");
    }

    /**
     * Sets the User-Agent header for this RestClient
     * @param userAgent The new User-Agent header
     */
    public void setUserAgent(String userAgent) {
        defaultHeaders.put("User-Agent", userAgent);
    }

    /**
     * This method is responsible for instantiating a new RestResponse or one of its subclasses
     *
     * @param r The OkHttp response given
     * @return A new response
     */
    protected abstract T initResponse(Response r);
}
