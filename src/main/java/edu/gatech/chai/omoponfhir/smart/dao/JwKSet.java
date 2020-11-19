package edu.gatech.chai.omoponfhir.smart.dao;

import java.util.List;

import edu.gatech.chai.omoponfhir.smart.model.JwkSetEntry;

public interface JwKSet {
	public int save(JwkSetEntry jwkSetEntry);
	public void update(JwkSetEntry jwkSetEntry);
	public void delete(String appId);
	public List<JwkSetEntry> get();
	public List<JwkSetEntry> getJwkSetByAppId(String appId);
	public List<JwkSetEntry> getJwkSetByKidAndIss(String kid, String iss);
}
