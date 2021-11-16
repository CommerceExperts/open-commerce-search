package de.cxp.ocs.spi;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public class SuggestServiceConfig {

	public int updateRateInSeconds = 5;

	public String[] groupDeduplicationOrder = new String[0];

	public boolean useRelativeShareLimit = false;

	public List<GroupConfig> groupConfig = new ArrayList<>();

	@Getter
	static class GroupConfig {

		public String	groupType;
		public int		limit;
	}
}
