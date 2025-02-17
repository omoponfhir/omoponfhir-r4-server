package edu.gatech.chai.omoponfhir.r4.provider;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import edu.gatech.chai.omoponfhir.omopv5.r4.mapping.OmopConceptMap;

public class ConceptMapResourceProvider implements IResourceProvider {
	private static final Logger logger = LoggerFactory.getLogger(ConceptMapResourceProvider.class);

	private WebApplicationContext myAppCtx;
	private OmopConceptMap myMapper;
	private FhirContext fhirContext;

	public ConceptMapResourceProvider() {
		myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
		myMapper = new OmopConceptMap(myAppCtx);
	}

	@Override
	public Class<ConceptMap> getResourceType() {
		return ConceptMap.class;
	}

	public static String getType() {
		return "ConceptMap";
	}

	public void setFhirContext(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
	}

	@Read()
	public ConceptMap readConceptMap(@IdParam IdType theId) {
		return null;
	}

	/**
	 * $translate operation for concept translation.
	 * 
	 */
	@Operation(name = "$translate", idempotent = true)
	public Parameters translateOperation(RequestDetails theRequestDetails,
			@OperationParam(name = "code") CodeType theCode, 
			@OperationParam(name = "system") UriType theSystem,
			@OperationParam(name = "version") StringType theVersion, 
			@OperationParam(name = "source") UriType theSource,
			@OperationParam(name = "coding") Coding theCoding,
			@OperationParam(name = "codeableConcept") CodeableConcept theCodeableConcept,
			@OperationParam(name = "target") UriType theTarget,
			@OperationParam(name = "targetsystem") UriType theTargetSystem,
			@OperationParam(name = "reverse") BooleanType theReverse) throws Exception {

		String mappingTerminologyUrl = System.getenv("MAPPING_TERMINOLOGY_URL");
		if (mappingTerminologyUrl != null && !mappingTerminologyUrl.isEmpty()) {
			String mappingRequestUrl = theRequestDetails.getCompleteUrl();
			if (mappingRequestUrl != null && !mappingRequestUrl.isEmpty() && !"none".equalsIgnoreCase(mappingRequestUrl)) {
				logger.debug("$translate: RequestDetails - " + theRequestDetails.getCompleteUrl());

				if (!mappingTerminologyUrl.endsWith("/")) {
					mappingTerminologyUrl = mappingTerminologyUrl.concat("/");
				}

				int urlTranslateIndex = mappingRequestUrl.indexOf("$translate");
				if (urlTranslateIndex >= 0) {
					String remoteMappingTerminologyUrlEncoded = mappingTerminologyUrl
							+ mappingRequestUrl.substring(urlTranslateIndex);
					
					String remoteMappingTerminologyUrl = remoteMappingTerminologyUrlEncoded;
					try {
						remoteMappingTerminologyUrl = URLDecoder.decode(remoteMappingTerminologyUrlEncoded, "UTF-8");
						RestTemplate restTemplate = new RestTemplate();

						String authTypeEnv = System.getenv("AUTH_TYPE");
						ResponseEntity<String> response;
						HttpEntity<String> entity = null;
						if (authTypeEnv != null && !authTypeEnv.isEmpty() && !"none".equalsIgnoreCase(authTypeEnv)) {
							String prefix = authTypeEnv.substring(0, 6);
							if ("basic ".equalsIgnoreCase(prefix)) {
								String rawString = authTypeEnv.substring(6);
								String base64encoded = Base64.getEncoder().encodeToString(rawString.getBytes());

								HttpHeaders headers = new HttpHeaders();
								headers.set("Authorization", "Basic " + base64encoded);
								entity = new HttpEntity<String>(headers);
							}
						}
						
						if (entity == null) {
							response = restTemplate.getForEntity(remoteMappingTerminologyUrl, String.class);
						} else {
							response = restTemplate.exchange(remoteMappingTerminologyUrl, HttpMethod.GET, entity, String.class);
						}
						
						if (response.getStatusCode().equals(HttpStatus.OK)) {
							String result = response.getBody();
							IParser fhirJsonParser = fhirContext.newJsonParser();
							Parameters parameters = fhirJsonParser.parseResource(Parameters.class, result);
							if (parameters != null && !parameters.isEmpty()) {
								logger.debug("$translate: responding parameters from external server, " + remoteMappingTerminologyUrl);
								return parameters;
							} else {
								logger.debug("$translate: empty parameter received from " + remoteMappingTerminologyUrl + ". Trying local");
							}
						}
					} catch (RestClientException | UnsupportedEncodingException e) {
						// We have an error.
						logger.error("$translate: Error on connecting to Remote ConceptMap server at "
								+ remoteMappingTerminologyUrl);

						// We may want not to return empty as we can try our internal server...
					}
				}
			}
		}

		String targetUri;
		String targetSystem;
		if (theTarget == null || theTarget.isEmpty()) {
			targetUri = "";
		} else {
			targetUri = theTarget.getValueAsString();
		}
		if (theTargetSystem == null || theTargetSystem.isEmpty()) {
			targetSystem = "";
		} else {
			targetSystem = theTargetSystem.getValueAsString();
		}

		List<Coding> codings = new ArrayList<Coding>();
		if (theCodeableConcept != null && !theCodeableConcept.isEmpty()) {
			// If codeableconcept exists, use this.
			// If we have multiple codings, run them until we have a matching
			// translation.
			codings.addAll(theCodeableConcept.getCoding());
		}

		if (theCoding != null && !theCoding.isEmpty()) {
			codings.add(theCoding);
		}

		if ((theCode != null && !theCode.isEmpty()) || (theSystem != null && !theSystem.isEmpty())) {
			Coding myCoding = new Coding();
			if (theCode != null) {
				myCoding.setCode(theCode.getValueAsString());
			}

			if (theSystem != null) {
				myCoding.setSystem(theSystem.getValueAsString());
			}

			codings.add(myCoding);
		}

		return myMapper.translateConcept(codings, targetUri, targetSystem);
	}
}
