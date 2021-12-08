package de.cxp.ocs.config;

import lombok.Setter;

@Setter
public class IndexSettings {

	public int replicaCount = 1;

	public String refreshInterval = "5s";

	public long minimumDocumentCount = 1;

}
