package com.zf1976.ddns.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zf1976.ddns.enums.DnsProviderType;
import com.zf1976.ddns.enums.LogStatus;

import java.io.Serializable;
import java.util.Date;

/**
 * @author ant
 * Create by Ant on 2021/8/17 1:52 AM
 */
@SuppressWarnings("RedundantCast")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DnsRecordLog implements Serializable {

    private DnsProviderType dnsProviderType;

    private LogStatus logStatus;

    private String content;

    private Long timestamp;

    public DnsRecordLog() {

    }


    private DnsRecordLog(DnsProviderType dnsProviderType, String domainOrMessage, LogStatus logStatus) {
        this(dnsProviderType, domainOrMessage, (String) null, logStatus);
    }

    private DnsRecordLog(DnsProviderType dnsProviderType, String domainOrMessage, String ip, LogStatus logStatus) {
        switch (logStatus) {
            case RAW -> this.content = "域名：" + domainOrMessage + " 没有发生变化, IP：" + ip;
            case CREATE -> this.content = "新增域名解析：" + domainOrMessage + " 成功！IP：" + ip;
            case CREATE_FAIL -> this.content = "新增域名解析：" + domainOrMessage + " 失败！";
            case MODIFY -> this.content = "更新域名解析：" + domainOrMessage + " 成功！IP：" + ip;
            case MODIFY_FAIL -> this.content = "更新域名解析：" + domainOrMessage + " 失败！";
            case ERROR -> this.content = domainOrMessage;
            default -> throw new UnsupportedOperationException("Unsupported log status: " + logStatus.name());
        }
        this.dnsProviderType = dnsProviderType;
        this.logStatus = logStatus;
        this.timestamp = new Date().getTime();
    }

    public static DnsRecordLog rawLog(DnsProviderType dnsProviderType, String domain, String ip) {
        return new DnsRecordLog(dnsProviderType, domain, ip, LogStatus.RAW);
    }

    public static DnsRecordLog createLog(DnsProviderType dnsProviderType, String domain, String ip) {
        return new DnsRecordLog(dnsProviderType, domain, ip, LogStatus.CREATE);
    }

    public static DnsRecordLog createFailLog(DnsProviderType dnsProviderType, String domain) {
        return new DnsRecordLog(dnsProviderType, domain, LogStatus.CREATE_FAIL);
    }

    public static DnsRecordLog modifyLog(DnsProviderType dnsProviderType, String domain, String ip) {
        return new DnsRecordLog(dnsProviderType, domain, ip, LogStatus.MODIFY);
    }

    public static DnsRecordLog modifyFailLog(DnsProviderType dnsProviderType, String domain) {
        return new DnsRecordLog(dnsProviderType, domain, LogStatus.MODIFY_FAIL);
    }

    public static DnsRecordLog errorLog(DnsProviderType dnsProviderType, String message) {
        return new DnsRecordLog(dnsProviderType, message, LogStatus.ERROR);
    }

    public DnsProviderType getDnsProviderType() {
        return dnsProviderType;
    }

    public DnsRecordLog setDnsProviderType(DnsProviderType dnsProviderType) {
        this.dnsProviderType = dnsProviderType;
        return this;
    }

    public String getContent() {
        return content;
    }

    public DnsRecordLog setContent(String content) {
        this.content = content;
        return this;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public DnsRecordLog setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public LogStatus getLogStatus() {
        return logStatus;
    }

    public DnsRecordLog setLogStatus(LogStatus logStatus) {
        this.logStatus = logStatus;
        return this;
    }

    @Override
    public String toString() {
        return "DnsRecordLog{" +
                "dnsProviderType=" + dnsProviderType +
                ", logStatus=" + logStatus +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
