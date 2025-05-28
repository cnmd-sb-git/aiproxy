package com.example.aiproxy.common.mcpproxy;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A servlet that proxies HTTP and Server-Sent Events (SSE) requests,
 * managing sessions and backend endpoint mapping.
 * This corresponds to the StreamableProxy struct and its methods in Go.
 */
public class StreamableProxyServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StreamableProxyServlet.class.getName());
    private static final Duration DEFAULT_HTTP_CLIENT_TIMEOUT = Duration.ofSeconds(30);
    private static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";

    private SessionManager sessionManager;
    private String defaultBackendUrl; // For initial requests
    private Map<String, String> customHeadersToBackend; // Headers to add to requests to the backend
    private HttpClient httpClient;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // In a real application, these would be initialized from servlet init-params,
        // a dependency injection framework, or a configuration service.

        // Example: Get from servlet init parameters
        this.defaultBackendUrl = config.getInitParameter("defaultBackendUrl");
        if (this.defaultBackendUrl == null || this.defaultBackendUrl.isEmpty()) {
            throw new ServletException("Servlet init parameter 'defaultBackendUrl' is required.");
        }

        String customHeadersString = config.getInitParameter("customHeadersToBackend"); // e.g., "Header1:Value1,Header2:Value2"
        if (customHeadersString != null && !customHeadersString.isEmpty()) {
            this.customHeadersToBackend = parseCustomHeaders(customHeadersString);
        } else {
            this.customHeadersToBackend = Collections.emptyMap();
        }
        
        // For now, using InMemorySessionManager. This could be made configurable.
        this.sessionManager = new InMemorySessionManager(); 
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_HTTP_CLIENT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1) // SSE typically works better over HTTP/1.1
                .build();
        
        LOGGER.info("StreamableProxyServlet initialized. Default backend: " + defaultBackendUrl);
    }

    private Map<String, String> parseCustomHeaders(String headerString) {
        Map<String, String> headers = new HashMap<>();
        String[] pairs = headerString.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }
        return Collections.unmodifiableMap(headers);
    }


    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Add CORS headers for all responses
        resp.setHeader("Access-Control-Allow-Origin", "*"); // Make this configurable
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, Mcp-Session-Id");
        resp.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");

        if (req.getMethod().equalsIgnoreCase(HttpServletResponse.SC_OPTIONS + "")) { // SC_OPTIONS is int, method is String
             doOptions(req, resp);
        } else {
            super.service(req, resp);
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
    }


    @Override
    protected void doGet(HttpServletRequest clientReq, HttpServletResponse clientResp) throws ServletException, IOException {
        String acceptHeader = clientReq.getHeader("Accept");
        if (acceptHeader == null || !acceptHeader.toLowerCase().contains("text/event-stream")) {
            // If not an SSE request, treat as a potential initial request or simple GET proxy if session exists.
            // The Go code seems to imply GET is primarily for SSE, but initial requests might also be GET.
            // For simplicity here, if not SSE, it might be an initial request.
             LOGGER.fine("GET request without text/event-stream in Accept header. Proxying as initial/non-SSE.");
             proxyInitialOrNoSessionRequest(clientReq, clientResp, clientReq.getMethod());
            return;
        }

        String proxySessionId = clientReq.getHeader(MCP_SESSION_ID_HEADER);
        if (proxySessionID == null || proxySessionId.isEmpty()) {
            proxyInitialOrNoSessionRequest(clientReq, clientResp, HttpServletResponse.SC_GET + "");
            return;
        }

        Optional<String> backendInfoOpt = sessionManager.getSessionEndpoint(proxySessionId);
        if (backendInfoOpt.isEmpty()) {
            sendError(clientResp, "Invalid or expired session ID", HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String backendInfo = backendInfoOpt.get();

        HttpRequest.Builder backendReqBuilder;
        try {
            backendReqBuilder = HttpRequest.newBuilder()
                .uri(new URI(backendInfo))
                .GET()
                .timeout(DEFAULT_HTTP_CLIENT_TIMEOUT.plusSeconds(120)); // Longer timeout for SSE
        } catch (URISyntaxException e) {
            sendError(clientResp, "Invalid backend URI syntax in session store: " + backendInfo, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        
        copyRequestHeadersToBackend(clientReq, backendReqBuilder, proxySessionId, backendInfo);
        addCustomHeaders(backendReqBuilder);

        try {
            HttpRequest backendRequest = backendReqBuilder.build();
            HttpResponse<InputStream> backendResp = httpClient.send(backendRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (backendResp.statusCode() != HttpServletResponse.SC_OK || 
                !backendResp.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("text/event-stream")) {
                // Not an SSE response from backend, or an error. Proxy as regular HTTP.
                clientResp.setStatus(backendResp.statusCode());
                copyResponseHeadersToClient(backendResp, clientResp, proxySessionId, true); // Exclude Mcp-Session-Id from backend
                clientResp.setHeader(MCP_SESSION_ID_HEADER, proxySessionId); // Ensure our proxy session ID is sent

                try (InputStream backendBody = backendResp.body(); OutputStream clientOut = clientResp.getOutputStream()) {
                    backendBody.transferTo(clientOut);
                }
                return;
            }
            
            // SSE streaming
            streamSseResponse(clientReq, clientResp, backendResp.body());

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during GET backend request for session " + proxySessionId, e);
            if (!clientResp.isCommitted()) sendError(clientResp, "Backend connection error", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "GET request for session " + proxySessionId + " interrupted", e);
            if (!clientResp.isCommitted()) sendError(clientResp, "Request interrupted", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest clientReq, HttpServletResponse clientResp) throws ServletException, IOException {
        String proxySessionId = clientReq.getHeader(MCP_SESSION_ID_HEADER);
        if (proxySessionId == null || proxySessionId.isEmpty()) {
            proxyInitialOrNoSessionRequest(clientReq, clientResp, clientReq.getMethod());
            return;
        }

        Optional<String> backendInfoOpt = sessionManager.getSessionEndpoint(proxySessionId);
        if (backendInfoOpt.isEmpty()) {
            sendError(clientResp, "Invalid or expired session ID", HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String backendInfo = backendInfoOpt.get();
        
        HttpRequest.Builder backendReqBuilder;
        try {
             backendReqBuilder = HttpRequest.newBuilder()
                .uri(new URI(backendInfo))
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> {
                    try { return clientReq.getInputStream(); } catch (IOException e) { throw new RuntimeException(e); }
                }))
                .timeout(DEFAULT_HTTP_CLIENT_TIMEOUT);
        } catch (URISyntaxException e) {
            sendError(clientResp, "Invalid backend URI syntax in session store: " + backendInfo, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        copyRequestHeadersToBackend(clientReq, backendReqBuilder, proxySessionId, backendInfo);
        addCustomHeaders(backendReqBuilder);

        try {
            HttpRequest backendRequest = backendReqBuilder.build();
            HttpResponse<InputStream> backendResp = httpClient.send(backendRequest, HttpResponse.BodyHandlers.ofInputStream());

            clientResp.setStatus(backendResp.statusCode());
            copyResponseHeadersToClient(backendResp, clientResp, proxySessionId, true);
            clientResp.setHeader(MCP_SESSION_ID_HEADER, proxySessionId); // Ensure our proxy session ID

            if (backendResp.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("text/event-stream")) {
                streamSseResponse(clientReq, clientResp, backendResp.body());
            } else {
                try (InputStream backendBody = backendResp.body(); OutputStream clientOut = clientResp.getOutputStream()) {
                    backendBody.transferTo(clientOut);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during POST backend request for session " + proxySessionId, e);
            if (!clientResp.isCommitted()) sendError(clientResp, "Backend connection error", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "POST request for session " + proxySessionId + " interrupted", e);
            if (!clientResp.isCommitted()) sendError(clientResp, "Request interrupted", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest clientReq, HttpServletResponse clientResp) throws ServletException, IOException {
        String proxySessionId = clientReq.getHeader(MCP_SESSION_ID_HEADER);
        if (proxySessionId == null || proxySessionId.isEmpty()) {
            sendError(clientResp, "Missing session ID", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Optional<String> backendInfoOpt = sessionManager.getSessionEndpoint(proxySessionId);
        if (backendInfoOpt.isEmpty()) {
            sendError(clientResp, "Invalid or expired session ID", HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String backendInfo = backendInfoOpt.get();

        HttpRequest.Builder backendReqBuilder;
         try {
            backendReqBuilder = HttpRequest.newBuilder()
                .uri(new URI(backendInfo))
                .DELETE()
                .timeout(DEFAULT_HTTP_CLIENT_TIMEOUT);
        } catch (URISyntaxException e) {
            sendError(clientResp, "Invalid backend URI syntax in session store: " + backendInfo, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        copyRequestHeadersToBackend(clientReq, backendReqBuilder, proxySessionId, backendInfo);
        addCustomHeaders(backendReqBuilder);
        
        try {
            HttpRequest backendRequest = backendReqBuilder.build();
            HttpResponse<InputStream> backendResp = httpClient.send(backendRequest, HttpResponse.BodyHandlers.ofInputStream());
            
            sessionManager.deleteSession(proxySessionId); // Delete after successful backend call
            LOGGER.info("Deleted session: " + proxySessionId + " after backend DELETE call.");

            clientResp.setStatus(backendResp.statusCode());
            copyResponseHeadersToClient(backendResp, clientResp, proxySessionId, true); 
            // No Mcp-Session-Id header on DELETE response usually

            try (InputStream backendBody = backendResp.body(); OutputStream clientOut = clientResp.getOutputStream()) {
                backendBody.transferTo(clientOut);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during DELETE backend request for session " + proxySessionId, e);
             if (!clientResp.isCommitted()) sendError(clientResp, "Backend connection error", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "DELETE request for session " + proxySessionId + " interrupted", e);
            if (!clientResp.isCommitted()) sendError(clientResp, "Request interrupted", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void proxyInitialOrNoSessionRequest(HttpServletRequest clientReq, HttpServletResponse clientResp, String method) throws IOException {
        HttpRequest.Builder backendReqBuilder;
        try {
            backendReqBuilder = HttpRequest.newBuilder()
                .uri(new URI(this.defaultBackendUrl))
                .method(method, HttpRequest.BodyPublishers.ofInputStream(() -> {
                    try { return clientReq.getInputStream(); } catch (IOException e) { throw new RuntimeException(e); }
                }))
                .timeout(DEFAULT_HTTP_CLIENT_TIMEOUT);
        } catch (URISyntaxException e) {
            sendError(clientResp, "Invalid default backend URI syntax: " + this.defaultBackendUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }


        Enumeration<String> headerNames = clientReq.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
             if (headerName.equalsIgnoreCase("Host") || headerName.equalsIgnoreCase("Connection") || headerName.equalsIgnoreCase("Content-Length")) {
                continue;
            }
            Enumeration<String> headerValues = clientReq.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                backendReqBuilder.header(headerName, headerValues.nextElement());
            }
        }
        addCustomHeaders(backendReqBuilder);
        try {
            backendReqBuilder.header("Host", new URI(this.defaultBackendUrl).getHost());
        } catch(URISyntaxException e) { /* ignore if defaultBackendUrl is malformed here, already checked */ }


        try {
            HttpRequest backendRequest = backendReqBuilder.build();
            HttpResponse<InputStream> backendResp = httpClient.send(backendRequest, HttpResponse.BodyHandlers.ofInputStream());

            String backendSessionId = backendResp.headers().firstValue(MCP_SESSION_ID_HEADER).orElse(null);
            String proxySessionIdToClient = null;

            if (backendSessionId != null && !backendSessionId.isEmpty()) {
                proxySessionIdToClient = sessionManager.newSessionId();
                String backendUrlWithSession = this.defaultBackendUrl;
                backendUrlWithSession += (this.defaultBackendUrl.contains("?") ? "&" : "?") + "sessionId=" + backendSessionId;
                sessionManager.setSession(proxySessionIdToClient, backendUrlWithSession);
                LOGGER.info("Initial request: new proxy session " + proxySessionIdToClient + " mapped to backend session " + backendSessionId + " at " + backendUrlWithSession);
            }

            clientResp.setStatus(backendResp.statusCode());
            copyResponseHeadersToClient(backendResp, clientResp, proxySessionIdToClient, backendSessionId != null);


            if (backendResp.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("text/event-stream")) {
                streamSseResponse(clientReq, clientResp, backendResp.body());
            } else {
                try (InputStream backendBody = backendResp.body(); OutputStream clientOut = clientResp.getOutputStream()) {
                    backendBody.transferTo(clientOut);
                }
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during initial backend request to " + this.defaultBackendUrl, e);
            if (!clientResp.isCommitted()) sendError(clientResp, "Backend connection error", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Initial backend request to " + this.defaultBackendUrl + " interrupted", e);
            if (!clientResp.isCommitted()) sendError(clientResp, "Request interrupted", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    private void streamSseResponse(HttpServletRequest clientReq, HttpServletResponse clientResp, InputStream backendInputStream) throws IOException {
        clientResp.setContentType("text/event-stream");
        clientResp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        clientResp.setHeader("Cache-Control", "no-cache");
        clientResp.setHeader("Connection", "keep-alive");
        // Access-Control-Allow-Origin is set in service()

        final AsyncContext asyncContext = clientReq.isAsyncStarted() ? clientReq.getAsyncContext() : clientReq.startAsync();
        asyncContext.setTimeout(0); // We manage timeouts via HttpClient and client heartbeats potentially

        final PrintWriter clientResponseWriter = clientResp.getWriter();
        
        asyncContext.addListener(new AsyncListener() {
            @Override public void onComplete(AsyncEvent event) throws IOException { backendInputStream.close(); LOGGER.fine("SSE AsyncContext onComplete."); }
            @Override public void onTimeout(AsyncEvent event) throws IOException { backendInputStream.close(); LOGGER.warning("SSE AsyncContext onTimeout."); }
            @Override public void onError(AsyncEvent event) throws IOException { backendInputStream.close(); LOGGER.severe("SSE AsyncContext onError: " + event.getThrowable()); }
            @Override public void onStartAsync(AsyncEvent event) throws IOException {}
        });

        Thread sseProxyThread = new Thread(() -> {
            try (BufferedReader backendReader = new BufferedReader(new InputStreamReader(backendInputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = backendReader.readLine()) != null) {
                     if (clientResponseWriter.checkError()) { // Check if client is still connected
                        LOGGER.info("Client disconnected or writer error, stopping SSE stream.");
                        break;
                    }
                    clientResponseWriter.write(line + "\n");
                    clientResp.flushBuffer(); // Ensure data is sent to client
                }
                LOGGER.fine("SSE stream from backend finished.");
            } catch (IOException e) {
                if (SseUtil.isClientDisconnectException(e)) { // SseUtil.isClientDisconnectException
                    LOGGER.info("Client disconnected during SSE streaming.");
                } else {
                    LOGGER.log(Level.WARNING, "Error proxying SSE stream: " + e.getMessage(), e);
                }
            } finally {
                try {
                    backendInputStream.close();
                     if (asyncContext.getRequest().isAsyncStarted()) { // Check if it was started before completing
                        asyncContext.complete();
                    }
                } catch (Exception e) { // Catch IllegalStateException if complete() is called multiple times
                    LOGGER.log(Level.WARNING, "Error closing backend stream or completing async context: " + e.getMessage(), e);
                }
            }
        });
        sseProxyThread.setName("streamable-sse-proxy-" + clientReq.getRemoteAddr());
        sseProxyThread.start();
    }

    private void copyRequestHeadersToBackend(HttpServletRequest clientReq, HttpRequest.Builder backendReqBuilder, String proxySessionId, String backendInfoUrl) {
        Enumeration<String> headerNames = clientReq.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (headerName.equalsIgnoreCase(MCP_SESSION_ID_HEADER) || 
                headerName.equalsIgnoreCase("Host") || 
                headerName.equalsIgnoreCase("Connection") ||
                headerName.equalsIgnoreCase("Content-Length") // Handled by HttpClient
                ) {
                continue;
            }
            Enumeration<String> headerValues = clientReq.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                backendReqBuilder.header(headerName, headerValues.nextElement());
            }
        }
        // Extract actual backend session ID from stored URL and set it for the backend request
        if (backendInfoUrl != null) {
            try {
                Map<String, String> queryParams = splitQuery(new URL(backendInfoUrl).getQuery());
                String backendSessionId = queryParams.get("sessionId");
                if (backendSessionId != null && !backendSessionId.isEmpty()) {
                    backendReqBuilder.header(MCP_SESSION_ID_HEADER, backendSessionId);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not parse backend session ID from stored URL: " + backendInfoUrl, e);
            }
        }
         try {
            backendReqBuilder.header("Host", new URI(backendInfoUrl).getHost());
        } catch(URISyntaxException e) { /* ignore */ }
    }
    
    private static Map<String, String> splitQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        return Pattern.compile("&")
                      .splitAsStream(query)
                      .map(s -> s.split("=", 2))
                      .collect(Collectors.toMap(
                          array -> array[0], // key
                          array -> array.length > 1 ? array[1] : "", // value
                          (v1, v2) -> v1 // in case of duplicate keys, keep first
                      ));
    }


    private void addCustomHeaders(HttpRequest.Builder backendReqBuilder) {
        if (this.customHeadersToBackend != null) {
            for (Map.Entry<String, String> entry : this.customHeadersToBackend.entrySet()) {
                backendReqBuilder.header(entry.getKey(), entry.getValue());
            }
        }
    }

    private void copyResponseHeadersToClient(HttpResponse<?> backendResp, HttpServletResponse clientResp, String proxySessionIdToClient, boolean excludeBackendSessionId) {
        backendResp.headers().map().forEach((headerName, headerValues) -> {
            if (excludeBackendSessionId && headerName.equalsIgnoreCase(MCP_SESSION_ID_HEADER)) {
                return; // Skip
            }
            // Filter out connection-specific headers managed by the container/client
            if (headerName.equalsIgnoreCase("Transfer-Encoding") || 
                headerName.equalsIgnoreCase("Connection") ||
                (headerName.equalsIgnoreCase("Content-Encoding") && headerValues.contains("gzip"))
                ) {
                return;
            }
            for (String value : headerValues) {
                clientResp.addHeader(headerName, value);
            }
        });
        if (proxySessionIdToClient != null && !proxySessionIdToClient.isEmpty()) {
             clientResp.setHeader(MCP_SESSION_ID_HEADER, proxySessionIdToClient);
        }
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) {
        try {
            if (!response.isCommitted()) {
                response.sendError(statusCode, message);
            } else {
                LOGGER.warning("Cannot send error, response already committed. Status: " + statusCode + ", Msg: " + message);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error sending error response to client: " + e.getMessage(), e);
        }
    }
}
