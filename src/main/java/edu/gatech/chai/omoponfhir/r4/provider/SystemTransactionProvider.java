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
package edu.gatech.chai.omoponfhir.r4.provider;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.exceptions.FHIRException;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import edu.gatech.chai.omoponfhir.omopv5.r4.mapping.OmopBundle;
import edu.gatech.chai.omoponfhir.omopv5.r4.model.MyBundle;
import edu.gatech.chai.omoponfhir.omopv5.r4.utilities.ThrowFHIRExceptions;
import jakarta.servlet.http.HttpServletRequest;

public class SystemTransactionProvider {

	private WebApplicationContext myAppCtx;
	private String myDbType;
	private OmopBundle myMapper;

	public static String getType() {
		return "Bundle";
	}

	public SystemTransactionProvider() {
		myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
		myDbType = myAppCtx.getServletContext().getInitParameter("backendDbType");
		if (myDbType.equalsIgnoreCase("omopv5") == true) {
			myMapper = new OmopBundle(myAppCtx);
		} else {
			myMapper = new OmopBundle(myAppCtx);
		}
	}

	/**
	 */
	@Transaction
	public Bundle transaction(@TransactionParam MyBundle theBundle, HttpServletRequest theRequest) {
		validateResource(theBundle);

		Bundle retVal = new Bundle();
		try {
			switch (theBundle.getType()) {
			case DOCUMENT:
				// https://www.hl7.org/fhir/documents.html#bundle
				// Ignore the fact that the bundle is a document and process all of the
				// resources that it contains as individual resources. Clients SHOULD not expect
				// that a server that receives a document submitted using this method will be
				// able to reassemble the document exactly. (Even if the server can reassemble
				// the document (see below), the result cannot be expected to be in the same
				// order, etc. Thus a document signature will very likely be invalid.)
				
				List<BundleEntryComponent> entries = theBundle.getEntry();
				if (!entries.isEmpty()) {
					BundleEntryComponent entry = entries.get(0);
					Resource resource = entry.getResource();

					if (!(resource instanceof Composition)) {
						ThrowFHIRExceptions.unprocessableEntityException("First entry in " 
							+ "Bundle document type should be Composition");
					}
				}

				myMapper.toDbase(theBundle, null);

				// theBundle should hae all thre status already been updated with updated resources IDs in entry.
				// we should be able to return this bundle as a response.
				retVal = theBundle;

				break;
			case BATCH:
				myMapper.toDbase(theBundle, null);
				retVal = theBundle;
				
				break;
			case TRANSACTION:
				myMapper.toDbase(theBundle, null);

				// theBundle should hae all thre status already been updated with updated resources IDs in entry.
				// we should be able to return this bundle as a response.
				retVal = theBundle;

				break;
			case MESSAGE:
				ThrowFHIRExceptions.unprocessableEntityException("Messages needs to be sent to $process-message operation API.");
				break;
			default:
				ThrowFHIRExceptions.unprocessableEntityException("Unsupported Bundle Type, "
						+ theBundle.getType().toString() + ". We support DOCUMENT and TRANSACTION");
			}

		} catch (FHIRException e) {
			e.printStackTrace();
			ThrowFHIRExceptions.unprocessableEntityException(e.getMessage());
		}

		return retVal;
	}

	// TODO: Add more validation code here.
	private void validateResource(MyBundle theBundle) {
	}

}
