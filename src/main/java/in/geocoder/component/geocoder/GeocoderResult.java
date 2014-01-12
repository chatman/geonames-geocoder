package in.geocoder.component.geocoder;

import org.apache.solr.common.util.NamedList;

public class GeocoderResult
{
	private String unmatched;
	private Permutation permutation;
	private NamedList<String> fields = new NamedList<String>();
	
	public String toString() {
		return "Unmatched: "+unmatched+", Location: "+fields;
	}

	public void addField (String field, String val) {
		fields.add(field, val);
	}
	
	public NamedList<String> getFields() {
		return fields;
	}
	
	public String getUnmatched() {
		return this.unmatched;
	}

	public void setUnmatched(String unmatched) {
		this.unmatched = unmatched;
	}

	public Permutation getPermutation() {
		return this.permutation;
	}

	public void setPermutation(Permutation permutation) {
		this.permutation = permutation;
	}
}

/* Location:           /data/indexer-main.jar
 * Qualified Name:     org.openstreetmap.osmgeocoder.geocoder.GeocoderResult
 * JD-Core Version:    0.6.2
 */