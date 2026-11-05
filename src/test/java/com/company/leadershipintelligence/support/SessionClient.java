package com.company.leadershipintelligence.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One browser for the tests: its own cookie jar, the CSRF header filled in the way the web application does it, and an optional
 * {@code X-Company-Id}. Not a mock of the security: every call goes through the real filter chain.
 */
public final class SessionClient {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final Map<String, Cookie> jar = new LinkedHashMap<>();
    private String company;

    public SessionClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** The company the next calls name in {@code X-Company-Id}; null to send none. */
    public SessionClient inCompany(String tenantId) {
        this.company = tenantId;
        return this;
    }

    public MvcResult get(String path) throws Exception {
        return send(MockMvcRequestBuilders.get(path));
    }

    public MvcResult post(String path, Object body) throws Exception {
        csrf();
        return send(request(path, body).header("X-XSRF-TOKEN", jar.get("XSRF-TOKEN").getValue()));
    }

    public MvcResult patch(String path, Object body) throws Exception {
        csrf();
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.patch(path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return send(builder.header("X-XSRF-TOKEN", jar.get("XSRF-TOKEN").getValue()));
    }

    public MvcResult delete(String path) throws Exception {
        csrf();
        return send(MockMvcRequestBuilders.delete(path).header("X-XSRF-TOKEN", jar.get("XSRF-TOKEN").getValue()));
    }

    public MvcResult postWithoutCsrf(String path, Object body) throws Exception {
        csrf();
        return send(request(path, body));
    }

    public Map<String, Cookie> cookies() {
        return jar;
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public JsonNode body(MvcResult result) throws Exception {
        String text = result.getResponse().getContentAsString();
        return text.isEmpty() ? json.createObjectNode() : json.readTree(text);
    }

    private void csrf() throws Exception {
        if (!jar.containsKey("XSRF-TOKEN")) {
            send(MockMvcRequestBuilders.get("/api/v1/auth/csrf"));
        }
    }

    private MockHttpServletRequestBuilder request(String path, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return builder;
    }

    private MvcResult send(MockHttpServletRequestBuilder builder) throws Exception {
        if (!jar.isEmpty()) {
            builder.cookie(jar.values().toArray(new Cookie[0]));
        }
        if (company != null) {
            builder.header("X-Company-Id", company);
        }
        MvcResult result = mvc.perform(builder).andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                jar.remove(cookie.getName());
            } else {
                jar.put(cookie.getName(), cookie);
            }
        }
        return result;
    }
}
