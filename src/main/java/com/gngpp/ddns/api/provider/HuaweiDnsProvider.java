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
import com.gngpp.ddns.pojo.HuaweiDataResult;
import com.gngpp.ddns.util.*;
import com.gngpp.ddns.api.auth.BasicCredentials;
import com.gngpp.ddns.api.auth.ProviderCredentials;
import com.gngpp.ddns.api.signer.HuaweiRequest;
import com.gngpp.ddns.api.signer.client.AsyncHuaweiClientSinger;
import com.gngpp.ddns.enums.DnsProviderType;
import com.gngpp.ddns.enums.DnsRecordType;
import com.gngpp.ddns.enums.HttpMethod;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 华为DNS
 *
 * @author ant
 * Create by Ant on 2021/7/17 1:25 上午
 */
@SuppressWarnings({"SpellCheckingInspection"})
public class HuaweiDnsProvider extends AbstractDnsProvider<HuaweiDataResult, HuaweiDnsProvider.Action> {

    private final Logger log = LogManager.getLogger("[HuaweiDnsProvider]");
    private final String api = "https://dns.myhuaweicloud.com/v2/zones";
    private final Map<String, String> zoneMap = new ConcurrentHashMap<>();

    public HuaweiDnsProvider(String id, String secret, Vertx vertx) {
        this(new BasicCredentials(id, secret), vertx);
    }

    public HuaweiDnsProvider(ProviderCredentials dnsApiCredentials, Vertx vertx) {
        super(dnsApiCredentials, vertx);
        // init web client
        AsyncHuaweiClientSinger.initClient(super.webClient);
    }

    private void initZoneMap() {
        if (!CollectionUtil.isEmpty(this.zoneMap)) {
            return;
        }
        try {
            final var httpRequestBase = HuaweiRequest.newBuilder(dnsProviderCredentials)
                                                     .setUrl(this.api)
                                                     .setMethod(HttpMethod.GET)
                                                     .build();
            final var huaweiDataResult = this.sendRequest(httpRequestBase, null);
            this.initZone(huaweiDataResult);
        } catch (Exception e) {
            throw new InvalidDnsCredentialException(e.getMessage(), e.getCause());
        }
    }

    private Future<Void> initZoneMapAsync() {
        if (!CollectionUtil.isEmpty(this.zoneMap)) {
            return Future.succeededFuture();
        }
        final var httpRequest = HuaweiRequest.newBuilder(dnsProviderCredentials)
                                             .setUrl(this.api)
                                             .setMethod(HttpMethod.GET)
                                             .buildAsync();
        return httpRequest.send()
                          .compose(bufferHttpResponse -> {
                              final var body = bufferHttpResponse.bodyAsString();
                              this.initZone(this.bodyHandler(body, Action.DESCRIBE));
                              return Future.succeededFuture();
                          });
    }

    private void initZone(HuaweiDataResult huaweiDataResult) {
        if (huaweiDataResult == null) {
            return;
        }
        final var zones = huaweiDataResult.getZones();
        if (!CollectionUtil.isEmpty(zones)) {
            // 按域名划分区域
            for (HuaweiDataResult.Zones zone : zones) {
                var domain = zone.getName();
                domain = domain.substring(0, domain.length() - 1);
                this.zoneMap.put(domain, zone.getId());
            }
        }
    }

    /**
     * 查找记录列表
     *
     * @param domain        域名/不区分顶级域名、多级域名
     * @param dnsRecordType 记录类型
     * @return {@link HuaweiDataResult}
     */
    public HuaweiDataResult findDnsRecordList(String domain, DnsRecordType dnsRecordType) {
        final var httpRequestBase = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                 .setUrl(this.getZoneUrl(domain))
                                                 .addQueryStringParam("type", dnsRecordType.name())
                                                 .setMethod(HttpMethod.GET)
                                                 .build();
        return this.sendRequest(httpRequestBase, Action.DESCRIBE);
    }

    /**
     * 新增记录
     *
     * @param domain        域名/不区分顶级域名、多级域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link HuaweiDataResult}
     */
    public HuaweiDataResult createDnsRecord(String domain, String ip, DnsRecordType dnsRecordType) {
        final var jsonObject = new JsonObject().put("name", domain + ".")
                                               .put("type", dnsRecordType.name())
                                               .put("records", Collections.singletonList(ip));
        final var httpRequestBase = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                 .setUrl(this.getZoneUrl(domain))
                                                 .setMethod(HttpMethod.POST)
                                                 .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                 .setBody(jsonObject.encode())
                                                 .build();
        return this.sendRequest(httpRequestBase, Action.CREATE);
    }

    /**
     * 更新记录
     *
     * @param id            记录唯一id
     * @param domain        域名/不区分顶级域名、多级域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link HuaweiDataResult}
     */
    public HuaweiDataResult modifyDnsRecord(String id, String domain, String ip, DnsRecordType dnsRecordType) {
        final var jsonObject = new JsonObject().put("type", dnsRecordType.name())
                                               .put("name", domain + ".")
                                               .put("records", Collections.singletonList(ip));
        final var httpRequestBase = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                 .setUrl(this.getZoneUrl(domain, id))
                                                 .setMethod(HttpMethod.PUT)
                                                 .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                 .setBody(jsonObject.encode())
                                                 .build();
        return this.sendRequest(httpRequestBase, Action.MODIFY);
    }

    /**
     * 删除记录
     *
     * @param id     记录唯一id
     * @param domain 域名/不区分顶级域名、多级域名
     * @return {@link HuaweiDataResult}
     */
    public HuaweiDataResult deleteDnsRecord(String id, String domain) {
        final var httpRequestBase = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                 .setUrl(this.getZoneUrl(domain, id))
                                                 .setMethod(HttpMethod.DELETE)
                                                 .build();
        return this.sendRequest(httpRequestBase, Action.DELETE);
    }

    /**
     * 异步版本
     *
     * @param domain        域名
     * @param dnsRecordType 记录类型
     * @return {@link Future<HuaweiDataResult>}
     */
    @Override
    public Future<HuaweiDataResult> findDnsRecordListAsync(String domain, DnsRecordType dnsRecordType) {
        final var asyncHttpRequest = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                  .setUrl(this.getZoneUrl(domain))
                                                  .addQueryStringParam("type", dnsRecordType.name())
                                                  .setMethod(HttpMethod.GET)
                                                  .buildAsync();
        return this.sendRequestAsync(asyncHttpRequest)
                   .compose(v -> this.bodyHandlerAsync(v, Action.DESCRIBE));
    }

    /**
     * 异步版本
     *
     * @param domain        域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link Future<HuaweiDataResult>}
     */
    @Override
    public Future<HuaweiDataResult> createDnsRecordAsync(String domain, String ip, DnsRecordType dnsRecordType) {
        final var jsonObject = new JsonObject().put("name", domain + ".")
                                               .put("type", dnsRecordType.name())
                                               .put("records", Collections.singletonList(ip));
        final var asyncHttpRequest = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                  .setUrl(this.getZoneUrl(domain))
                                                  .setMethod(HttpMethod.POST)
                                                  .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                  .setBody(jsonObject.encode())
                                                  .buildAsync();
        return this.sendRequestAsync(asyncHttpRequest, jsonObject)
                   .compose(v -> this.bodyHandlerAsync(v, Action.CREATE));
    }

    /**
     * 异步版本
     *
     * @param id            id
     * @param domain        域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link Future<HuaweiDataResult>}
     */
    @Override
    public Future<HuaweiDataResult> modifyDnsRecordAsync(String id,
                                                         String domain,
                                                         String ip,
                                                         DnsRecordType dnsRecordType) {
        final var jsonObject = new JsonObject().put("type", dnsRecordType.name())
                                               .put("name", domain + ".")
                                               .put("records", Collections.singletonList(ip));
        final var aysncHttpRequest = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                  .setUrl(this.getZoneUrl(domain, id))
                                                  .setMethod(HttpMethod.PUT)
                                                  .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                  .setBody(jsonObject.encode())
                                                  .buildAsync();
        return this.sendRequestAsync(aysncHttpRequest, jsonObject)
                   .compose(v -> this.bodyHandlerAsync(v, Action.MODIFY));
    }

    /**
     * 异步版本
     *
     * @param id     id
     * @param domain 域名
     * @return {@link Future<HuaweiDataResult>}
     */
    @Override
    public Future<HuaweiDataResult> deleteDnsRecordAsync(String id, String domain) {
        final var asyncHttpRequest = HuaweiRequest.newBuilder(this.dnsProviderCredentials)
                                                  .setUrl(this.getZoneUrl(domain, id))
                                                  .setMethod(HttpMethod.DELETE)
                                                  .buildAsync();
        return this.sendRequestAsync(asyncHttpRequest)
                   .compose(v -> this.bodyHandlerAsync(v, Action.DESCRIBE));
    }

    /**
     * 是否支持
     *
     * @param dnsServiceType DNS服务商类型
     * @return {@link boolean}
     */
    @Override
    public boolean support(DnsProviderType dnsServiceType) {
        this.initZoneMap();
        return DnsProviderType.HUAWEI.check(dnsServiceType);
    }

    /**
     * 异步版本
     *
     * @param dnsServiceType DNS服务商类型
     * @return {@link Future <Boolean>}
     */
    @Override
    public Future<Boolean> supportAsync(DnsProviderType dnsServiceType) {
        return this.initZoneMapAsync()
                   .compose(v -> {
                       if (!DnsProviderType.HUAWEI.check(dnsServiceType)) {
                           return Future.failedFuture("The :" + dnsServiceType.name() + " DNS service provider is not supported");
                       }
                       return Future.succeededFuture(true);
                   });
    }

    private HuaweiDataResult sendRequest(HttpRequestBase httpRequestBase, Action action) {
        byte[] bytes;
        try (CloseableHttpResponse httpResponse = this.closeableHttpClient.execute(httpRequestBase)) {
            if (httpResponse != null) {
                bytes = this.getBodyBytes(httpResponse);
                if (!ObjectUtil.isEmpty(bytes)) {
                    return this.bodyHandler(new String(bytes), action);
                }
            }
            return null;
        } catch (Exception e) {
            LogUtil.printDebug(log, e.getMessage(), e.getCause());
            throw new DnsServiceResponseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    protected Future<HttpResponse<Buffer>> sendRequestAsync(HttpRequest<Buffer> httpRequest) {
        return this.sendRequestAsync(httpRequest, null);
    }

    @Override
    protected Future<HttpResponse<Buffer>> sendRequestAsync(HttpRequest<Buffer> httpRequest, JsonObject data) {
        if (data != null) {
            return httpRequest.sendJsonObject(data);
        }
        return httpRequest.send();
    }

    @Override
    protected HuaweiDataResult bodyHandler(String body, Action action) {
        HuaweiDataResult huaweiDataResult = this.mapperResult(body, HuaweiDataResult.class);
        switch (action) {
            case CREATE, MODIFY, DELETE -> {
                final var result = this.mapperResult(body, HuaweiDataResult.Recordsets.class);
                huaweiDataResult = this.resultToList(huaweiDataResult, result);
            }
            default -> huaweiDataResult = this.mapperResult(body, HuaweiDataResult.class);
        }
        if (huaweiDataResult != null && huaweiDataResult.getMessage() != null) {
            throw new DnsServiceResponseException(huaweiDataResult.getMessage());
        }
        return huaweiDataResult;
    }

    @Override
    protected Future<HuaweiDataResult> bodyHandlerAsync(HttpResponse<Buffer> responseFuture, Action action) {
        final var body = responseFuture.bodyAsString();
        try {
            final var huaweiDataResult = this.bodyHandler(body, action);
            return Future.succeededFuture(huaweiDataResult);
        } catch (Exception e) {
            LogUtil.printDebug(log, e.getMessage(), e.getCause());
            return Future.failedFuture(e);
        }
    }

    private HuaweiDataResult resultToList(final HuaweiDataResult huaweiDataResult, HuaweiDataResult.Recordsets result) {
        if (huaweiDataResult != null && result != null) {
            return huaweiDataResult.setRecordsets(Collections.singletonList(result));
        }
        return null;
    }

    private String getZoneUrl(String domain) {
        return this.getZoneUrl(domain, null);
    }

    private String getZoneUrl(String domain, String recordSetId) {
        final var extractDomain = HttpUtil.extractDomain(domain);
        String zoneId = this.zoneMap.get(extractDomain[0]);
        if (StringUtil.isEmpty(zoneId)) {
            throw new ResolvedDomainException("Resolved primary domain name:" + domain + " does not exist");
        }
        if (StringUtil.isEmpty(recordSetId)) {
            return this.concatUrl(this.api, zoneId, "recordsets");
        }
        return this.concatUrl(this.api, zoneId, "recordsets", recordSetId);
    }


    @Override
    public void reloadCredentials(ProviderCredentials dnsProviderCredentials) {
        this.zoneMap.clear();
        super.reloadCredentials(dnsProviderCredentials);
    }

    protected enum Action {
        DESCRIBE,
        CREATE,
        DELETE,
        MODIFY
    }
}
