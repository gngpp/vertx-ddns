/*
 *
 *
 * MIT License
 *
 * Copyright (c) 2021 gngpp
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.gngpp.ddns.api.signer;

import com.gngpp.ddns.api.signer.client.AsyncHuaweiClientSinger;
import com.gngpp.ddns.api.signer.client.HuaweiClientSigner;
import com.gngpp.ddns.util.ApiURLEncoderUtil;
import com.gngpp.ddns.api.auth.ProviderCredentials;
import com.gngpp.ddns.enums.HttpMethod;
import com.gngpp.ddns.util.LogUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import org.apache.http.client.methods.HttpRequestBase;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * @author mac
 * @date 2021/7/24
 */
public class HuaweiRequest {
    private final Map<String, String> headers = new Hashtable<>();
    private final Map<String, List<String>> queryString = new Hashtable<>();
    private String key = null;
    private String secret = null;
    private String method = null;
    private String url = null;
    private String body = null;
    private String fragment = null;

    private HuaweiRequest(ProviderCredentials basicCredentials) {
        this.setKey(basicCredentials.getAccessKeyId());
        this.setSecret(basicCredentials.getAccessKeySecret());
    }

    public String getKey() {
        return this.key;
    }

    private void setKey(String appKey) {
        if (null != appKey && !appKey.trim()
                                     .isEmpty()) {
            this.key = appKey;
        } else {
            throw new RuntimeException("appKey can not be empty");
        }
    }

    public String getSecret() {
        return this.secret;
    }

    private void setSecret(String appSecret) {
        if (null != appSecret && !appSecret.trim()
                                           .isEmpty()) {
            this.secret = appSecret;
        } else {
            throw new RuntimeException("appSecret can not be empty");
        }
    }

    public HttpMethod getMethod() {
        return HttpMethod.valueOf(this.method.toUpperCase());
    }

    public HuaweiRequest setMethod(HttpMethod methodType) {
        final var method = methodType.name();
        if (!method.equalsIgnoreCase("post") && !method.equalsIgnoreCase("put") && !method.equalsIgnoreCase("patch") && !method.equalsIgnoreCase("delete") && !method.equalsIgnoreCase("get") && !method.equalsIgnoreCase("options") && !method.equalsIgnoreCase("head")) {
            throw new RuntimeException("unsupported method");
        } else {
            this.method = method;
        }
        return this;
    }

    public String getBody() {
        return this.body;
    }

    public HuaweiRequest setBody(String body) {
        this.body = body;
        return this;
    }

    public Map<String, String> getHeaders() {
        return this.headers;
    }

    public String getUrl() {
        StringBuilder uri = new StringBuilder(this.url);
        if (this.queryString.size() > 0) {
            uri.append("?");
            int loop = 0;

            for (Map.Entry<String, List<String>> entry : this.queryString.entrySet()) {
                for (var var5 = entry.getValue()
                                     .iterator(); var5.hasNext(); ++loop) {
                    String value = var5.next();
                    if (loop > 0) {
                        uri.append("&");
                    }

                    uri.append(ApiURLEncoderUtil.huaweiPercentEncode(entry.getKey(), false));
                    uri.append("=");
                    uri.append(ApiURLEncoderUtil.huaweiPercentEncode(value, false));
                }
            }
        }

        if (this.fragment != null) {
            uri.append("#");
            uri.append(this.fragment);
        }

        return uri.toString();
    }

    public HuaweiRequest setUrl(String url) {
        if (null != url && !url.trim()
                               .isEmpty()) {
            int i = url.indexOf(35);
            if (i >= 0) {
                url = url.substring(0, i);
            }

            i = url.indexOf(63);
            if (i >= 0) {
                String query = url.substring(i + 1);
                String[] var4 = query.split("&");

                for (String item : var4) {
                    String[] spl = item.split("=", 2);
                    String key = spl[0];
                    String value = "";
                    if (spl.length > 1) {
                        value = spl[1];
                    }

                    if (!key.trim()
                            .isEmpty()) {
                        key = URLDecoder.decode(key, StandardCharsets.UTF_8);
                        value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                        this.addQueryStringParam(key, value);
                    }
                }

                url = url.substring(0, i);
            }

            this.url = url;
        } else {
            throw new RuntimeException("url can not be empty");
        }
        return this;
    }

    public String getPath() {
        String url = this.url;
        int i = url.indexOf("://");
        if (i >= 0) {
            url = url.substring(i + 3);
        }

        i = url.indexOf(47);
        return i >= 0 ? url.substring(i) : "/";
    }

    public String getHost() {
        String url = this.url;
        int i = url.indexOf("://");
        if (i >= 0) {
            url = url.substring(i + 3);
        }

        i = url.indexOf(47);
        if (i >= 0) {
            url = url.substring(0, i);
        }

        return url;
    }

    public HuaweiRequest addQueryStringParam(String name, String value) {
        this.queryString.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(value);
        return this;
    }

    public Map<String, List<String>> getQueryStringParams() {
        return this.queryString;
    }

    public String getFragment() {
        return this.fragment;
    }

    public HuaweiRequest setFragment(String fragment) {
        if (null != fragment && !fragment.trim()
                                         .isEmpty()) {
            this.fragment = URLEncoder.encode(fragment, StandardCharsets.UTF_8);
        } else {
            throw new RuntimeException("fragment can not be empty");
        }
        return this;
    }

    public HuaweiRequest addHeader(String name, String value) {
        if (null != name && !name.trim()
                                 .isEmpty()) {
            this.headers.put(name, value);
        }
        return this;
    }

    public static HuaweiRequest newBuilder(ProviderCredentials credentials) {
        return new HuaweiRequest(credentials);
    }

    public HttpRequestBase build() {
        try {
            return HuaweiClientSigner.sign(this);
        } catch (Exception e) {
            LogUtil.printDebug(e.getMessage(), e.getCause());
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
    }

    public HttpRequest<Buffer> buildAsync() {
        try {
            return AsyncHuaweiClientSinger.signAsync(this);
        } catch (Exception e) {
            LogUtil.printDebug(e.getMessage(), e.getCause());
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
    }
}
