package org.seamware.edc.tmf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.edc.domain.ExtendableAgreementCreateVO;
import org.seamware.edc.domain.ExtendableAgreementVO;
import org.seamware.edc.domain.ExtendableProductOffering;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.tmforum.agreement.model.AgreementVO;
import org.seamware.tmforum.productinventory.model.ProductCreateVO;
import org.seamware.tmforum.productinventory.model.ProductVO;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgreementApiClient extends ApiClient {

    private static final String AGREEMENT_PATH = "agreement";

    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public AgreementApiClient(Monitor monitor, OkHttpClient okHttpClient, String baseUrl, ObjectMapper objectMapper) {
        super(monitor, okHttpClient);
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    public ExtendableAgreementVO getAgreement(String agreementId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(AGREEMENT_PATH);
        urlBuilder.addPathSegment(agreementId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableAgreementVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get agreement %s.", agreementId), e);
        }
    }

    public List<ExtendableAgreementVO> getAgreements(int offset, int limit) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(AGREEMENT_PATH);
        urlBuilder.addQueryParameter(OFFSET_PARAM, String.valueOf(offset));
        urlBuilder.addQueryParameter(LIMIT_PARAM, String.valueOf(limit));
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper
                    .readValue(responseBody.bytes(), new TypeReference<List<ExtendableAgreementVO>>() {
                    });
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to get agreements.", e);
        }
    }

    public ExtendableAgreementVO createAgreement(ExtendableAgreementCreateVO extendableAgreementCreateVO) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(AGREEMENT_PATH);
        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(objectMapper.writeValueAsString(extendableAgreementCreateVO), JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Was not able to serialize agreement.", e);
        }
        Request request = new Request.Builder().url(urlBuilder.build()).post(requestBody).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            return objectMapper.readValue(responseBody.bytes(), ExtendableAgreementVO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Was not able to read agreement creation response.", e);
        }
    }


    public Optional<ExtendableAgreementVO> findByContractId(String contractId) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
        urlBuilder.addPathSegment(AGREEMENT_PATH);
        urlBuilder.addQueryParameter("externalId", contractId);
        Request request = new Request.Builder().url(urlBuilder.build()).build();
        try (ResponseBody responseBody = executeRequest(request)) {
            List<ExtendableAgreementVO> extendableAgreementVOS = objectMapper.readValue(responseBody.bytes(), new TypeReference<List<ExtendableAgreementVO>>() {
            });
            if (extendableAgreementVOS.size() > 1) {
                throw new IllegalArgumentException(String.format("There cannot be more than one agreement per contract id. Found multiple for %s.", contractId));
            }
            if (extendableAgreementVOS.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(extendableAgreementVOS.getFirst());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Was not able to get agreements for contractId %s", contractId), e);
        }
    }
}