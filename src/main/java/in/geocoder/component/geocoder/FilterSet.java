package in.geocoder.component.geocoder;

import org.apache.hadoop.util.bloom.BloomFilter;
import org.apache.hadoop.util.bloom.Key;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse;

public class FilterSet {
	private Map<String, BloomFilter> filters = new HashMap<String, BloomFilter>();
	private Map<String, Character> symbols = new HashMap<String, Character>();

	public FilterSet() {

	}

	public FilterSet(SolrServer server, Map<String, Character> fieldSymbols) {
		for (String field: fieldSymbols.keySet()) {
			char symbol = fieldSymbols.get(field);
			try {
				generate(server, field, symbol);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	void generate(SolrServer server, String field, char symbol) throws SolrServerException, IOException {
		generate(server, field, symbol, 0);
	}
	void generate(SolrServer server, String field, char symbol, int minThreshold) throws SolrServerException, IOException {

		SolrQuery query = new SolrQuery();
		query.setParam("qt", new String[] { "/terms" });
		query.setParam("terms", true);
		query.setParam("terms.limit", new String[] { "-1" });
		query.setParam("terms.fl", new String[] { field });

		QueryResponse response = server.query(query);
		List<TermsResponse.Term> terms = response.getTermsResponse().getTerms(field);

		int filterSize = (int)Math.ceil(terms.size()/10000.0) * 8 * 1024 * 1024;
		if (filterSize==0) filterSize = 8 * 1024 * 1024;
		BloomFilter filter = new BloomFilter(filterSize, 10, 0);

		for (TermsResponse.Term term : terms) {
			if ((minThreshold >= 0) && (term.getFrequency() >= minThreshold)) {
				if(term.getTerm().length()>0)
					filter.add(new Key(term.getTerm().getBytes()));
			}
		}

		filters.put(field, filter);
		symbols.put(field, symbol);
	}

	public void addFilter(String field, char symbol, Set<String> terms) {
		int filterSize = (int)Math.ceil(terms.size()/10000.0) * 8 * 1024 * 1024;
		if (filterSize==0) filterSize = 8 * 1024 * 1024;
		BloomFilter filter = new BloomFilter(filterSize, 10, 0);

		for (String t: terms) {
			if (t.length()>0)
				filter.add(new Key(t.getBytes()));
		}
		filters.put(field, filter);
		symbols.put(field, symbol);
	}

	public Set<String> getFilters() {
		return filters.keySet();
	}


	public BloomFilter getFilter(String field) {
		return filters.get(field);
	}

	@Override
	public String toString() {
		return filters.keySet().toString();
	}

	public char getSymbol(String field) {
		return symbols.get(field);
	}

}
