package de.cxp.ocs.model.params;


public enum SortOrder {
	/**
	 * Ascending order.
	 */
	ASC {

		@Override
		public String toString() {
			return "asc";
		}
	},
	/**
	 * Descending order.
	 */
	DESC {

		@Override
		public String toString() {
			return "desc";
		}
	};
}
