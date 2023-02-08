/*******************************************************************************
 * Copyright (c) 2019 Georgia Tech Research Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
/**
 * 
 */
package edu.gatech.chai.omoponfhir.r4.security;

import java.util.Map;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.UriType;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.util.ExtensionConstants;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ExtensionUtil;

/**
 * @author mc142local
 *
 */
@Interceptor
public class SMARTonFHIRConformanceStatement {

	// static String authorizeURI =
	// "http://fhir-registry.smarthealthit.org/Profile/oauth-uris#authorize";
	// static String tokenURI =
	// "http://fhir-registry.smarthealthit.org/Profile/oauth-uris#token";
	// static String registerURI =
	// "http://fhir-registry.smarthealthit.org/Profile/oauth-uris#register";

	static String oauthURI = "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris";
	static String authorizeURI = "authorize";
	static String tokenURI = "token";
	static String registerURI = "register";

	String authorizeUrlValue = "http://localhost:8080/authorize";
	String tokenUrlValue = "http://localhost:8080/token";


	public SMARTonFHIRConformanceStatement() {
		String authorizeUrl = System.getenv("SMART_AUTHSERVERURL");
		String tokenUrl = System.getenv("SMART_TOKENSERVERURL");

		if (authorizeUrl != null && !authorizeUrl.isEmpty()) {
			authorizeUrlValue = authorizeUrl;
		}

		if (tokenUrl != null && !tokenUrl.isEmpty()) {
			tokenUrlValue = tokenUrl;
		}

	}

	@Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
	public void customize(IBaseConformance theCapabilityStatement) {
		CapabilityStatement cs = (CapabilityStatement) theCapabilityStatement;
		Map<String, Long> counts = ExtensionUtil.getResourceCounts();

		cs
         .getSoftware()
        //  .setName("OMOP v5.3.1 on FHIR R4")
         .setVersion("v1.3.1")
         .setReleaseDateElement(new DateTimeType("2023-02-07"));

		cs.setPublisher("Georgia Tech Research Institute - HEAT");

		for (CapabilityStatementRestComponent rest : cs.getRest()) {
			for (CapabilityStatementRestResourceComponent nextResource : rest.getResource()) {
				Long count = counts.get(nextResource.getTypeElement().getValueAsString());
				if (count != null) {
					nextResource.addExtension(
							new Extension(ExtensionConstants.CONF_RESOURCE_COUNT, new DecimalType(count)));
				}
			}

			CapabilityStatementRestSecurityComponent restSec = new CapabilityStatementRestSecurityComponent();

			// Set security.service
			CodeableConcept codeableConcept = new CodeableConcept();
			Coding coding = new Coding("https://www.hl7.org/fhir/codesystem-restful-security-service.html",
					"SMART-on-FHIR", "SMART-on-FHIR");
			codeableConcept.addCoding(coding);

			restSec.addService(codeableConcept);

			// We need to add SMART on FHIR required conformance statement.

			Extension secExtension = new Extension();
			secExtension.setUrl(oauthURI);

			Extension authorizeExtension = new Extension();
			authorizeExtension.setUrl(authorizeURI);
			authorizeExtension.setValue(new UriType(authorizeUrlValue));

			Extension tokenExtension = new Extension();
			tokenExtension.setUrl(tokenURI);
			tokenExtension.setValue(new UriType(tokenUrlValue));

			secExtension.addExtension(authorizeExtension);
			secExtension.addExtension(tokenExtension);

			restSec.addExtension(secExtension);

			rest.setSecurity(restSec);
		}
	}

	public void setAuthServerUrl(String authorizeUrlValue) {
		this.authorizeUrlValue = authorizeUrlValue;
	}

	public void setTokenServerUrl(String tokenUrlValue) {
		this.tokenUrlValue = tokenUrlValue;
	}
}
