package de.cxp.ocs.config;

import lombok.Getter;
import lombok.Setter;

@Setter
public class IndexSettings {

	public int replicaCount = 1;

	public String refreshInterval = "5s";

	public int minimumDocumentCount = 1;

	public int waitTimeMsForHealthyIndex = 3000;

	@Getter
	public boolean useDefaultConfig;
}
