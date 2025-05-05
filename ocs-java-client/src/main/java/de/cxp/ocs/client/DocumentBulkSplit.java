package de.cxp.ocs.client;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

/**
 * Helper class that is used to split an "abstract" document list into explicit product and document lists.
 * 
 * @author rudi
 *
 */
class DocumentBulkSplit {

	final List<Product>  products  = new ArrayList<>();
	final List<Document> documents = new ArrayList<>();

	public DocumentBulkSplit(List<Document> docs) {
		for (Document d : docs) {
			if (d instanceof Product) {
				products.add((Product) d);
			}
			else {
				documents.add(d);
			}
		}
	}
}
