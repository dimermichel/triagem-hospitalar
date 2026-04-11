package br.gov.sus.painel.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

/**
 * Registro de conexão WebSocket ativa.
 *
 * PK: connectionId (único por cliente)
 * GSI: hospitalId → para buscar todas as conexões de um hospital
 *
 * TTL de 2h garante limpeza automática de conexões inativas.
 */
@DynamoDbBean
public class ConexaoRecord {

    private String connectionId;
    private String hospitalId;
    private String endpoint;     // API Gateway WebSocket endpoint URL
    private long   conectadoEm;  // epoch seconds
    private long   ttlExpiraEm;  // epoch seconds (+2h)

    @DynamoDbPartitionKey
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String v) { connectionId = v; }

    @DynamoDbSecondaryPartitionKey(indexNames = "hospital-index")
    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String v) { hospitalId = v; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String v) { endpoint = v; }

    public long getConectadoEm() { return conectadoEm; }
    public void setConectadoEm(long v) { conectadoEm = v; }

    public long getTtlExpiraEm() { return ttlExpiraEm; }
    public void setTtlExpiraEm(long v) { ttlExpiraEm = v; }
}
