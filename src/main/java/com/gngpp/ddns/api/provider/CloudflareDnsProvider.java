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

package com.gngpp.ddns.api.provider;

import com.gngpp.ddns.api.provider.exception.DnsServiceResponseException;
import com.gngpp.ddns.api.provider.exception.InvalidDnsCredentialException;
import com.gngpp.ddns.api.provider.exception.ResolvedDomainException;
import com.gngpp.ddns.pojo.CloudflareDataResult;
import com.gngpp.ddns.util.*;
import com.gngpp.ddns.api.auth.ProviderCredentials;
import com.gngpp.ddns.api.auth.TokenCredentials;
import com.gngpp.ddns.enums.DnsProviderType;
import com.gngpp.ddns.enums.DnsRecordType;
import com.gngpp.ddns.enums.HttpMethod;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * cloudflare DNS
 *
 * @author ant
 * Create by Ant on 2021/7/17 1:24 上午
 */
@SuppressWarnings("RedundantCast")
public class CloudflareDnsProvider extends AbstractDnsProvider<CloudflareDataResult, CloudflareDnsProvider.Action> {

    private final Logger log = LogManager.getLogger("[CloudflareDnsProvider]");
    private final String api = "https://api.cloudflare.com/client/v4/zones";
    private final Map<String, String> zoneMap = new ConcurrentHashMap<>();

    public CloudflareDnsProvider(String token, Vertx vertx) {
        this(new TokenCredentials(token), vertx);
    }


    public CloudflareDnsProvider(ProviderCredentials dnsApiCredentials, Vertx vertx) {
        super(dnsApiCredentials, vertx);
    }

    private void initZoneMap(){
        if (!CollectionUtil.isEmpty(this.zoneMap)) {
            return;
        }
        final var request = HttpRequest.newBuilder()
                                       .GET()
                                       .uri(URI.create(api))
                                       .header(HttpHeaders.AUTHORIZATION, "Bearer " + dnsProviderCredentials.getAccessKeySecret())
                                       .build();
        final String body;
        try {
            body = super.httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException e) {
            throw new InvalidDnsCredentialException(e.getMessage(), e.getCause());
        }
        final var result = this.extractZoneResult(body);
        // 按域名分区映射区域id
        for (CloudflareDataResult.Result res : result) {
            this.zoneMap.put(res.getName(), res.getId());
        }
    }

    private Future<Void> initZoneMapAsync() {
        if (!CollectionUtil.isEmpty(this.zoneMap)) {
            return Future.succeededFuture();
        }
        return this.webClient.getAbs(this.api)
                             .putHeader(HttpHeaders.AUTHORIZATION, this.bearerToken())
                             .send()
                             .compose(v -> {
                                 final var body = v.bodyAsString();
                                 try {
                                     final var result = this.extractZoneResult(body);
                                     // 按域名分区映射区域id
                                     for (CloudflareDataResult.Result res : result) {
                                         this.zoneMap.put(res.getName(), res.getId());
                                     }
                                     return Future.succeededFuture();
                                 } catch (Exception e) {
                                     return Future.failedFuture(e.getMessage());
                                 }
                             });
    }

    private List<CloudflareDataResult.Result> extractZoneResult(String body) {
        final var cloudflareDataResult = this.mapperResult(body, CloudflareDataResult.class);
        Assert.notNull(cloudflareDataResult, "result cannot been null");
        if (!cloudflareDataResult.getSuccess()) {
            throw new RuntimeException(Json.encodePrettily(cloudflareDataResult.getErrors()));
        }
        return cloudflareDataResult.getResult();
    }

    /**
     * 查询所有记录
     *
     * @param domain        域名/不区分主域名、多级域名（相当于查询主域名下所有记录）
     * @param dnsRecordType 记录类型
     * @return {@link CloudflareDataResult}
     */
    public CloudflareDataResult findDnsRecordList(String domain, DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(domain, dnsRecordType, Action.DESCRIBE);
        final var request = this.requestBuild(domain, queryParam, HttpMethod.GET);
        return this.sendRequest(request);
    }

    /**
     * 新增记录
     *
     * @param domain        域名/区分主域名与多级域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link CloudflareDataResult}
     */
    public CloudflareDataResult createDnsRecord(String domain, String ip, DnsRecordType dnsRecordType) {
        final var data = this.getQueryParam(domain, ip, dnsRecordType, Action.CREATE);
        final var httpRequest = this.requestBuild(domain, data, HttpMethod.POST);
        return this.sendRequest(httpRequest);
    }

    /**
     * 更新记录值
     *
     * @param id            记录标识符
     * @param domain        域名
     * @param ip            ip值
     * @param dnsRecordType 记录类型
     * @return {@link CloudflareDataResult}
     */
    public CloudflareDataResult modifyDnsRecord(String id,
                                                String domain,
                                                String ip,
                                                DnsRecordType dnsRecordType) {
        final var data = this.getQueryParam(id, domain, ip, dnsRecordType, Action.MODIFY);
        final var httpRequest = this.requestBuild(id, domain, data, HttpMethod.PUT);
        return this.sendRequest(httpRequest);
    }

    /**
     * 删除记录
     *
     * @param id     记录标识符
     * @param domain 域名/不区分顶级域名、多级域名
     * @return {@link CloudflareDataResult}
     */
    public CloudflareDataResult deleteDnsRecord(String id, String domain) {
        final var httpRequest = this.requestBuild(id, domain, HttpMethod.DELETE);
        return this.sendRequest(httpRequest);
    }

    /**
     * 异步版本
     *
     * @param domain        域名
     * @param dnsRecordType 记录类型
     * @return {@link Future<CloudflareDataResult>}
     */
    @Override
    public Future<CloudflareDataResult> findDnsRecordListAsync(String domain, DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(domain, dnsRecordType, Action.DESCRIBE);
        final var url = this.requestUrlBuild(domain, queryParam);
        return this.sendRequestAsync(url, HttpMethod.GET)
                   .compose(this::bodyHandlerAsync);
    }

    /**
     * 异步版本
     *
     * @param domain        域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link Future<CloudflareDataResult>}
     */
    @Override
    public Future<CloudflareDataResult> createDnsRecordAsync(String domain, String ip, DnsRecordType dnsRecordType) {
        final var data = this.getQueryParam(domain, ip, dnsRecordType, Action.CREATE);
        final var url = this.requestUrlBuild(domain);
        return this.sendRequestAsync(url, JsonObject.mapFrom(data), HttpMethod.POST)
                   .compose(this::bodyHandlerAsync);
    }

    /**
     * 异步版本
     *
     * @param id            id
     * @param domain        域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link Future<CloudflareDataResult>}
     */
    @Override
    public Future<CloudflareDataResult> modifyDnsRecordAsync(String id,
                                                             String domain,
                                                             String ip,
                                                             DnsRecordType dnsRecordType) {
        final var data = this.getQueryParam(id, domain, ip, dnsRecordType, Action.MODIFY);
        final var url = this.requestUrlBuild(id, domain);
        return this.sendRequestAsync(url, JsonObject.mapFrom(data), HttpMethod.PUT)
                   .compose(this::bodyHandlerAsync);
    }

    /**
     * 异步版本
     *
     * @param id     id
     * @param domain 域名
     * @return {@link Future<CloudflareDataResult>}
     */
    @Override
    public Future<CloudflareDataResult> deleteDnsRecordAsync(String id, String domain) {
        final var url = this.requestUrlBuild(id, domain);
        return this.sendRequestAsync(url, HttpMethod.DELETE)
                   .compose(this::bodyHandlerAsync);
    }

    /**
     * 是否支持
     *
     * @param dnsServiceType DNS服务商类型
     * @return {@link boolean}
     */
    @Override
    public boolean support(DnsProviderType dnsServiceType) {
        try {
            this.initZoneMap();
        } catch (Exception e) {
            throw new InvalidDnsCredentialException(e.getMessage(), e.getCause());
        }
        return DnsProviderType.CLOUDFLARE.check(dnsServiceType);
    }

    /**
     * 使用api之前调用该函数进行判断，是否支持使用
     *
     * @param dnsServiceType DNS服务商类型
     * @return {@link Future<Boolean>}
     */
    @Override
    public Future<Boolean> supportAsync(DnsProviderType dnsServiceType) {
        return this.initZoneMapAsync()
                   .compose(v -> {
                       if (!DnsProviderType.CLOUDFLARE.check(dnsServiceType)) {
                           return Future.failedFuture("The :{}" + dnsServiceType.name() + " DNS service provider is not supported");
                       }
                       return Future.succeededFuture(true);
                   });
    }

    private String bearerToken() {
        final var token = super.dnsProviderCredentials.getAccessKeySecret();
        return "Bearer " + token;
    }


    private HttpRequestBase requestBuild(String domain, Map<String, Object> queryParam, HttpMethod methodType) {
        return this.requestBuild((String) null, domain, queryParam, methodType);
    }


    private HttpRequestBase requestBuild(String id,
                                         String domain,
                                         @SuppressWarnings("SameParameterValue") HttpMethod methodType) {
        return this.requestBuild(id, domain, (Map<String, Object>) null, methodType);
    }

    private HttpRequestBase requestBuild(String id,
                                         String domain,
                                         Map<String, Object> queryParam,
                                         HttpMethod methodType) {
        final String url;
        HttpRequestBase httpRequest;
        switch (methodType) {
            case GET -> {
                url = this.requestUrlBuild(domain, queryParam);
                httpRequest = this.request(url, methodType);
            }
            case POST -> {
                url = this.requestUrlBuild(domain);
                httpRequest = this.request(url, JsonObject.mapFrom(queryParam), methodType);
            }
            case PUT -> {
                url = this.requestUrlBuild(id, domain);
                httpRequest = this.request(url, JsonObject.mapFrom(queryParam), methodType);
            }
            case DELETE -> {
                url = this.requestUrlBuild(id, domain);
                httpRequest = this.request(url, methodType);
            }
            default -> throw new IllegalStateException("Unexpected value: " + methodType);
        }
        return httpRequest;
    }

    private HttpRequestBase request(String url, HttpMethod methodType) {
        return this.request(url, null, methodType);
    }

    private HttpRequestBase request(String url, JsonObject data, HttpMethod methodType) {
        switch (methodType) {
            case GET -> {
                final var httpGet = new HttpGet(url);
                httpGet.addHeader(HttpHeaders.AUTHORIZATION, this.bearerToken());
                return httpGet;
            }
            case PUT -> {
                final var httpPut = new HttpPut(url);
                httpPut.addHeader(HttpHeaders.AUTHORIZATION, this.bearerToken());
                httpPut.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                httpPut.setEntity(new StringEntity(data.encode(), StandardCharsets.UTF_8));
                return httpPut;
            }
            case POST -> {
                final var httpPost = new HttpPost(url);
                httpPost.addHeader(HttpHeaders.AUTHORIZATION, this.bearerToken());
                httpPost.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                httpPost.setEntity(new StringEntity(data.encode(), StandardCharsets.UTF_8));
                return httpPost;
            }
            case DELETE -> {
                final var httpDelete = new HttpDelete(url);
                httpDelete.addHeader(HttpHeaders.AUTHORIZATION, this.bearerToken());
                return httpDelete;
            }
            default -> throw new UnsupportedOperationException("request method not supported:" + methodType.name());
        }
    }

    protected Future<io.vertx.ext.web.client.HttpResponse<Buffer>> sendRequestAsync(String url, HttpMethod methodType) {
        return this.sendRequestAsync(url, null, methodType);
    }


    protected Future<io.vertx.ext.web.client.HttpResponse<Buffer>> sendRequestAsync(String url,
                                                                                    JsonObject data,
                                                                                    HttpMethod methodType) {
        final Future<io.vertx.ext.web.client.HttpResponse<Buffer>> httpResponseFuture;
        switch (methodType) {
            case GET -> httpResponseFuture = this.webClient.getAbs(url)
                                                           .putHeader(HttpHeaders.AUTHORIZATION, this.bearerToken())
                                                           .send();
            case POST -> httpResponseFuture = this.webClient.postAbs(url)
                                                            .putHeader(HttpHeaders.AUTHORIZATION, this.bearerToken())
                                                            .sendJsonObject(data);
            case PUT -> httpResponseFuture = this.webClient.putAbs(url)
                                                           .putHeader(HttpHeaders.AUTHORIZATION, this.bearerToken())
                                                           .sendJsonObject(data);
            case DELETE -> httpResponseFuture = this.webClient.deleteAbs(url)
                                                              .putHeader(HttpHeaders.AUTHORIZATION, this.bearerToken())
                                                              .send();
            default -> {
                return Future.failedFuture("Unexpected value:" + methodType);
            }
        }
        return httpResponseFuture;
    }

    private String requestUrlBuild(String domain) {
        return this.requestUrlBuild(domain, (Map<String, Object>) null);
    }

    private String requestUrlBuild(String identifier, String domain) {
        return this.requestUrlBuild(identifier, domain, (Map<String, Object>) null);
    }

    private String requestUrlBuild(String domain, Map<String, Object> queryParam) {
        return this.requestUrlBuild((String) null, domain, queryParam);
    }

    private String requestUrlBuild(String identifier, String domain, Map<String, Object> queryParam) {
        final var canonicalizeStringQueryString = this.toCanonicalizeStringQueryString(queryParam);
        return this.urlBuild(identifier, domain, canonicalizeStringQueryString);
    }

    private String urlBuild(String identifier, String domain, String canonicalizeQueryString) {
        final var zoneUrl = this.getZoneUrl(domain);
        if (StringUtil.isEmpty(canonicalizeQueryString)) {
            return this.concatUrl(zoneUrl, (StringUtil.isEmpty(identifier) ? "?" : StringUtil.FOLDER_SEPARATOR + identifier));
        }
        return this.concatUrl(zoneUrl, (StringUtil.isEmpty(identifier) ? "?" : StringUtil.FOLDER_SEPARATOR + identifier + "?"), canonicalizeQueryString);
    }

    private String toCanonicalizeStringQueryString(Map<String, Object> queryParam) {
        if (!CollectionUtil.isEmpty(queryParam)) {
            final var builder = new StringBuilder();
            final var array = queryParam.keySet()
                                        .toArray(new String[]{});
            for (String key : array) {
                builder.append("&")
                       .append(key)
                       .append("=")
                       .append(queryParam.get(key));
            }
            return builder.substring(1);
        }
        return null;
    }

    private String getZoneUrl(String domain) {
        final var extractDomain = HttpUtil.extractDomain(domain);
        // 如果域名属于二级及以上域名，则根据cloudflare查询策略，按主域名查询
        final var zoneId = this.zoneMap.get(extractDomain[0]);
        if (StringUtil.isEmpty(zoneId)) {
            throw new ResolvedDomainException("Resolved primary domain name:" + domain + " does not exist");
        }
        return this.concatUrl(this.api, zoneId, "dns_records");
    }

    protected Future<CloudflareDataResult> bodyHandlerAsync(io.vertx.ext.web.client.HttpResponse<Buffer> httpResponse) {
        final String body = httpResponse.bodyAsString();
        try {
            final var cloudflareDataResult = this.bodyHandler(body);
            return Future.succeededFuture(cloudflareDataResult);
        } catch (Exception e) {
            LogUtil.printDebug(log, e.getMessage(), e.getCause());
            return Future.failedFuture(e);
        }
    }

    protected CloudflareDataResult bodyHandler(String body) {
        final CloudflareDataResult cloudflareDataResult;
        final var jsonObject = JsonObject.mapFrom(Json.decodeValue(body));
        final var resultKey = "result";
        final var result = jsonObject.getMap()
                                     .get(resultKey);
        if (result instanceof ArrayList) {
            cloudflareDataResult = this.mapperResult(body, CloudflareDataResult.class);
        } else {
            jsonObject.put(resultKey, Collections.singletonList(result));
            cloudflareDataResult = jsonObject.mapTo(CloudflareDataResult.class);
        }
        final var errors = cloudflareDataResult.getErrors();
        if (!CollectionUtil.isEmpty(errors)) {
            final var messages = errors.stream()
                                      .map(CloudflareDataResult.Error::getMessage)
                                      .collect(Collectors.joining(","));
            throw new DnsServiceResponseException(messages);
        }
        return cloudflareDataResult;
    }

    private Map<String, Object> getCommonQueryParam(DnsRecordType dnsRecordType) {
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("match", "all");
        queryParam.put("type", dnsRecordType.name());
        queryParam.put("per_page", "100");
        return queryParam;
    }

    @Override
    protected Map<String, Object> getQueryParam(String recordId,
                                                String domain,
                                                String ip,
                                                DnsRecordType dnsRecordType,
                                                Action action) {
        final var queryParam = this.getCommonQueryParam(dnsRecordType);
        switch (action) {
            case CREATE, MODIFY -> {
                queryParam.put("name", domain);
                queryParam.put("content", ip);
                queryParam.put("ttl", "120");
            }
            case DESCRIBE, DELETE -> {
            }
        }
        return queryParam;
    }

    @Override
    public void reloadCredentials(ProviderCredentials dnsProviderCredentials) {
        this.zoneMap.clear();
        super.reloadCredentials(dnsProviderCredentials);
    }

    protected enum Action {
        CREATE,
        MODIFY,
        DESCRIBE,
        DELETE
    }
}
