package in.geocoder.component.geocoder;

import java.util.List;

public class Classification
{
	public List<Integer> tokenPositions;
	public String text;
	public String classification;
	public char symbol;

	public Classification(String classification, char symbol, String text, List<Integer> tokens)
	{
		this.tokenPositions = tokens;
		this.text = text;
		this.classification = classification;
		this.symbol = symbol;
	}

	public String toString()
	{
		return this.tokenPositions + "=" + this.classification;
	}
}
