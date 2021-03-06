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

package com.gngpp.ddns.verticle.timer.service.impl;

import com.gngpp.ddns.api.provider.DnsRecordProvider;
import com.gngpp.ddns.api.provider.exception.FoundDnsProviderException;
import com.gngpp.ddns.api.provider.exception.NotSupportDnsProviderException;
import com.gngpp.ddns.pojo.vo.DnsRecord;
import com.gngpp.ddns.config.DnsConfig;
import com.gngpp.ddns.enums.DnsProviderType;
import com.gngpp.ddns.enums.DnsRecordType;
import com.gngpp.ddns.verticle.timer.service.AbstractDnsRecordService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author ant
 * Create by Ant on 2021/7/28 10:01 下午
 */
public class DnsRecordServiceImpl extends AbstractDnsRecordService {

    private final Logger log = LogManager.getLogger("[DnsRecordServiceImpl]");

    public DnsRecordServiceImpl(List<DnsConfig> ddnsConfigList, Vertx vertx) {
        super(ddnsConfigList, vertx);
    }

    public List<DnsRecord> findRecordList(DnsProviderType dnsServiceType, String domain, DnsRecordType dnsRecordType) {
        final var provider = this.providerMap.get(dnsServiceType);
        this.checkProvider(provider, dnsServiceType);
        final var result = provider.findDnsRecordList(domain, dnsRecordType);
        return super.findGenericsResultHandler(result, domain);
    }

    @Override
    public Boolean createRecord(DnsProviderType dnsProviderType, String domain, String ip, DnsRecordType dnsRecordType) {
        final var provider = this.providerMap.get(dnsProviderType);
        this.checkProvider(provider, dnsProviderType);
        final var result = provider.createDnsRecord(domain, ip, dnsRecordType);
        return super.createGenericsResultHandler(result);
    }

    @Override
    public Boolean modifyRecord(DnsProviderType dnsProviderType, String id, String domain, String ip, DnsRecordType dnsRecordType) {
        final var provider = this.providerMap.get(dnsProviderType);
        this.checkProvider(provider, dnsProviderType);
        final var result = provider.modifyDnsRecord(id, domain, ip, dnsRecordType);
        return super.modifyGenericsResultHandler(result);
    }

    @Override
    public Boolean deleteRecord(DnsProviderType dnsProviderType, String recordId, String domain) {
        final var provider = this.providerMap.get(dnsProviderType);
        this.checkProvider(provider, dnsProviderType);
        final var resultObj = provider.deleteDnsRecord(recordId, domain);
        return super.deleteGenericsResultHandler(resultObj);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<List<DnsRecord>> findRecordListAsync(DnsProviderType dnsServiceType,
                                                       String domain,
                                                       DnsRecordType dnsRecordType) {
        return Future.succeededFuture(this.providerMap.get(dnsServiceType))
                     .compose(provider -> {
                         if (provider == null) {
                             return Future.failedFuture("No service provider");
                         }
                         return provider.supportAsync(dnsServiceType)
                                        .compose(v -> provider.findDnsRecordListAsync(domain, dnsRecordType))
                                        .compose(result -> {
                                            final var dnsRecordVoList = this.findGenericsResultHandler(result, domain);
                                            return Future.succeededFuture(dnsRecordVoList);
                                        });
                     });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<Boolean> createRecordAsync(DnsProviderType dnsProviderType, String domain, String ip, DnsRecordType dnsRecordType) {
        return Future.succeededFuture(this.providerMap.get(dnsProviderType))
                     .compose(provider -> {
                         if (provider == null) {
                             return Future.failedFuture("No service provider");
                         }
                         return provider.supportAsync(dnsProviderType)
                                        .compose(v -> provider.createDnsRecordAsync(domain, ip, dnsRecordType))
                                        .compose(v -> Future.succeededFuture(super.createGenericsResultHandler(v)));
                     });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<Boolean> modifyRecordAsync(DnsProviderType dnsProviderType, String id, String domain, String ip, DnsRecordType dnsRecordType) {
        return Future.succeededFuture(this.providerMap.get(dnsProviderType))
                     .compose(provider -> {
                         if (provider == null) {
                             return Future.failedFuture("No service provider");
                         }
                         return provider.supportAsync(dnsProviderType)
                                        .compose(v -> provider.modifyDnsRecordAsync(id, domain, ip, dnsRecordType))
                                        .compose(v -> Future.succeededFuture(super.modifyGenericsResultHandler(v)));
                     });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<Boolean> deleteRecordAsync(DnsProviderType dnsProviderType, String id, String domain) {
        return Future.succeededFuture(this.providerMap.get(dnsProviderType))
                     .compose(provider -> {
                         if (provider == null) {
                             return Future.failedFuture("No service provider");
                         }
                         return provider.supportAsync(dnsProviderType)
                                        .compose(v -> provider.deleteDnsRecordAsync(id, domain))
                                        .compose(v -> Future.succeededFuture(super.deleteGenericsResultHandler(v)));
                     });

    }

    private void checkProvider(DnsRecordProvider<?> provider, DnsProviderType dnsProviderType) {
        if (provider == null) {
            throw new FoundDnsProviderException("No service provider");
        } else if (!provider.support(dnsProviderType)) {
            throw new NotSupportDnsProviderException("The :" + dnsProviderType.name() + " DNS service provider is not supported");
        }
    }

}
