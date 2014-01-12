package in.geocoder.component.geocoder;

import java.util.List;

import org.apache.lucene.search.Query;


public class Permutation
{
	public Classification[] classifications;
	public List<String> queryTokens;
	public String fullAnnotation;
	public String shortAnnotation;
	public Query query;

	public Permutation(Classification[] classifications, List<String> queryTokens) {
		this.classifications = classifications;
		this.queryTokens = queryTokens;

		char[] symbols = new char[queryTokens.size()];
		for (int i = 0; i < symbols.length; i++)
			symbols[i] = '.';
		
		for (Classification c : classifications)
			if (c != null)
				for (int pos: c.tokenPositions)
					symbols[pos] = c.symbol;
		this.fullAnnotation = new String(symbols);

		StringBuilder sb = new StringBuilder();
		int prev = -1;
		// Assuming all the classifications are sorted in order of tokenPositions
		for (Classification c: classifications)
			if (c!=null) {
				if (c.tokenPositions.get(0)!=prev+1)
					sb.append('.');
				sb.append(c.symbol);
				prev = c.tokenPositions.get(c.tokenPositions.size()-1);
			}
		if (prev!=queryTokens.size()-1)
			sb.append('.');
		this.shortAnnotation = sb.toString();
	}


	public String toString() {
		return this.fullAnnotation + "(" + this.shortAnnotation + ")";
	}

}