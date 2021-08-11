package com.zf1976.ddns.api.provider;

import com.zf1976.ddns.api.auth.BasicCredentials;
import com.zf1976.ddns.api.auth.DnsApiCredentials;
import com.zf1976.ddns.api.enums.DnsRecordType;
import com.zf1976.ddns.api.enums.HttpMethod;
import com.zf1976.ddns.api.provider.exception.DnsServiceResponseException;
import com.zf1976.ddns.api.provider.exception.InvalidDnsCredentialException;
import com.zf1976.ddns.api.signer.rpc.AliyunSignatureComposer;
import com.zf1976.ddns.api.signer.rpc.RpcAPISignatureComposer;
import com.zf1976.ddns.pojo.AliyunDataResult;
import com.zf1976.ddns.util.HttpUtil;
import com.zf1976.ddns.util.LogUtil;
import com.zf1976.ddns.util.ParameterHelper;
import com.zf1976.ddns.util.StringUtil;
import com.zf1976.ddns.api.enums.DnsProviderType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 阿里云DNS
 *
 * @author mac
 * @date 2021/7/14
 */
public class AliyunDnsProvider extends AbstractDnsProvider<AliyunDataResult, AliyunDnsProvider.Action> {

    private final Logger log = LogManager.getLogger("[AliyunDnsProvider]");
    private final RpcAPISignatureComposer rpcSignatureComposer = AliyunSignatureComposer.getComposer();

    public AliyunDnsProvider(String accessKeyId, String accessKeySecret, Vertx vertx) {
        this(new BasicCredentials(accessKeyId, accessKeySecret), vertx);
    }

    public AliyunDnsProvider(DnsApiCredentials credentials, Vertx vertx) {
        super(credentials, vertx);
    }

    /**
     * 查询记录
     *
     * @param domain        域名/区分主域名、多级域名
     * @param dnsRecordType 记录类型
     * @return {@link AliyunDataResult}
     */
    @Override
    public AliyunDataResult findDnsRecordList(String domain, DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(domain, dnsRecordType, Action.DESCRIBE);
        final var httpRequest = this.requestBuild(queryParam);
        return this.sendRequest(httpRequest);
    }

    /**
     * 新增记录
     *
     * @param domain        域名/区分主域名、多级域名
     * @param ip            ip值
     * @param dnsRecordType 记录类型
     * @return {@link AliyunDataResult}
     */
    public AliyunDataResult createDnsRecord(String domain, String ip, DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(domain, ip, dnsRecordType, Action.CREATE);
        final var httpRequest = this.requestBuild(queryParam);
        return this.sendRequest(httpRequest);
    }

    /**
     * 更新记录
     *
     * @param id            记录id
     * @param domain        域名/区分主域名、多级域名
     * @param ip            ip值
     * @param dnsRecordType 记录类型
     */
    public AliyunDataResult modifyDnsRecord(String id, String domain, String ip, DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(id, domain, ip, dnsRecordType, Action.MODIFY);
        final var httpRequest = this.requestBuild(queryParam);
        return this.sendRequest(httpRequest);
    }


    /**
     * 删除域名记录
     *
     * @param id     记录id（记录唯一凭证）
     * @param domain 阿里云域名可以忽略
     * @return {@link AliyunDataResult}
     */
    @Override
    public AliyunDataResult deleteDnsRecord(String id, String domain) {
        final var queryParam = this.getQueryParam(id, domain, Action.DELETE);
        final var httpRequest = this.requestBuild(queryParam);
        return this.sendRequest(httpRequest);
    }

    /**
     * 异步版本
     *
     * @param domain        域名
     * @param dnsRecordType 记录类型
     * @return {@link Future<AliyunDataResult>}
     */
    @Override
    public Future<AliyunDataResult> findDnsRecordListAsync(String domain, DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(domain, dnsRecordType, Action.DESCRIBE);
        final var url = this.requestUrlBuild(queryParam);
        return this.sendRequestAsync(url)
                   .compose(this::resultHandlerAsync);
    }

    /**
     * 异步版本
     *
     * @param domain        域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link Future<AliyunDataResult>}
     */
    @Override
    public Future<AliyunDataResult> createDnsRecordAsync(String domain, String ip, DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(domain, ip, dnsRecordType, Action.CREATE);
        final var url = this.requestUrlBuild(queryParam);
        return this.sendRequestAsync(url)
                   .compose(this::resultHandlerAsync);
    }

    /**
     * 异步版本
     *
     * @param id            id
     * @param domain        域名
     * @param ip            ip
     * @param dnsRecordType 记录类型
     * @return {@link Future<AliyunDataResult>}
     */
    @Override
    public Future<AliyunDataResult> modifyDnsRecordAsync(String id,
                                                         String domain,
                                                         String ip,
                                                         DnsRecordType dnsRecordType) {
        final var queryParam = this.getQueryParam(id, domain, ip, dnsRecordType, Action.MODIFY);
        final var url = this.requestUrlBuild(queryParam);
        return this.sendRequestAsync(url)
                   .compose(this::resultHandlerAsync);
    }

    /**
     * 异步版本
     *
     * @param id     id
     * @param domain 域名
     * @return {@link Future<AliyunDataResult>}
     */
    @Override
    public Future<AliyunDataResult> deleteDnsRecordAsync(String id, String domain) {
        final var queryParam = this.getQueryParam(id, domain, Action.DELETE);
        final var url = this.requestUrlBuild(queryParam);
        return this.sendRequestAsync(url)
                   .compose(this::resultHandlerAsync);
    }

    private HttpRequest requestBuild(Map<String, Object> queryParam) {
        final var requestUrl = this.requestUrlBuild(queryParam);
        return HttpRequest.newBuilder()
                          .GET()
                          .uri(URI.create(requestUrl))
                          .build();
    }

    /**
     * 是否支持
     *
     * @param dnsServiceType DNS服务商类型
     * @return {@link boolean}
     */
    @Override
    public boolean support(DnsProviderType dnsServiceType) {
        return DnsProviderType.ALIYUN.check(dnsServiceType);
    }

    @Override
    public Future<Boolean> supportAsync(DnsProviderType dnsServiceType) {
        if (this.support(dnsServiceType)) {
            return Future.succeededFuture(true);
        }
        return Future.failedFuture("The :" + dnsServiceType.name() + "DNS service provider is not supported");
    }

    private AliyunDataResult sendRequest(HttpRequest request) {
        try {
            final var body = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                                            .body();
            return this.resultHandler(body);
        } catch (IOException | InterruptedException e) {
            LogUtil.printDebug(log, e.getMessage(), e.getCause());
            throw new DnsServiceResponseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    protected Future<io.vertx.ext.web.client.HttpResponse<Buffer>> sendRequestAsync(String url) {
        return this.webClient.getAbs(url).send();
    }

    @Override
    protected AliyunDataResult resultHandler(String body) {
        final var aliyunDataResult = this.mapperResult(body, AliyunDataResult.class);
        if (aliyunDataResult != null && aliyunDataResult.getMessage() != null) {
            throw new DnsServiceResponseException(aliyunDataResult.getMessage());
        }
        return aliyunDataResult;
    }

    @Override
    protected Future<AliyunDataResult> resultHandlerAsync(io.vertx.ext.web.client.HttpResponse<Buffer> httpResponse) {
        try {
            final var body = httpResponse.bodyAsString();
            final var aliyunDataResult = this.resultHandler(body);
            return Future.succeededFuture(aliyunDataResult);
        } catch (Exception e) {
            LogUtil.printDebug(log, e.getMessage(), e.getCause());
            return Future.failedFuture(e);
        }
    }

    private String requestUrlBuild(Map<String, Object> queryParam) {
        final String api = "https://alidns.aliyuncs.com/";
        return this.rpcSignatureComposer.toSignatureUrl(this.dnsApiCredentials.getAccessKeySecret() + "&", api, HttpMethod.GET, queryParam);
    }


    private Map<String, Object> getCommonQueryParam(Action action) {
        final var queryParam = new HashMap<String, Object>();
        queryParam.put("Format", "JSON");
        queryParam.put("AccessKeyId", this.dnsApiCredentials.getAccessKeyId());
        queryParam.put("Action", action.value);
        queryParam.put("SignatureMethod", rpcSignatureComposer.signatureMethod());
        queryParam.put("SignatureNonce", ParameterHelper.getUniqueNonce());
        queryParam.put("SignatureVersion", rpcSignatureComposer.getSignerVersion());
        queryParam.put("Version", "2015-01-09");
        queryParam.put("Timestamp", ParameterHelper.getISO8601Time1(new Date()));
        return queryParam;
    }

    protected Map<String, Object> getQueryParam(String recordId,
                                                String domain,
                                                String ip,
                                                DnsRecordType dnsRecordType,
                                                AliyunDnsProvider.Action action) {
        final var queryParam = this.getCommonQueryParam(action);
        final var extractDomain = HttpUtil.extractDomain(domain);
        switch (action) {
            case CREATE -> {
                queryParam.put("Type", dnsRecordType.name());
                queryParam.put("Value", ip);
                queryParam.put("DomainName", extractDomain[0]);
                queryParam.put("RR", "".equals(extractDomain[1]) ? "@" : extractDomain[1]);
            }
            case DELETE -> queryParam.put("RecordId", recordId);
            case MODIFY -> {
                queryParam.put("RecordId", recordId);
                queryParam.put("Type", dnsRecordType.name());
                queryParam.put("Value", ip);
                queryParam.put("DomainName", extractDomain[0]);
                queryParam.put("RR", extractDomain[1]);
            }
            case DESCRIBE -> {
                queryParam.put("PageSize", "500");
                queryParam.put("TypeKeyWord", dnsRecordType.name());
                queryParam.put("DomainName", extractDomain[0]);
                if (!StringUtil.isEmpty(extractDomain[1])) {
                    queryParam.put("RRKeyWord", extractDomain[1]);
                }
            }
        }
        return queryParam;
    }

    protected enum Action {

        DESCRIBE("DescribeDomainRecords"),
        CREATE("AddDomainRecord"),
        MODIFY("UpdateDomainRecord"),
        DELETE("DeleteDomainRecord");

        public final String value;

        Action(String action) {
            value = action;
        }
    }

}
