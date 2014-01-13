package in.geocoder.component.geocoder.util;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class AllCombinationIteratable<Object>
implements Iterator<Object[]>
{
	private List<Object[]> matrix;
	private int[] counter;
	private Object[] combination;
	int len;

	public AllCombinationIteratable(List<Object[]> matrix, Object[] combination)
	{
		this.matrix = matrix;
		this.counter = new int[matrix.size()];
		this.combination = combination;
		this.len = combination.length;
	}

	public void remove() {
		throw new IllegalStateException("not implemented");
	}

	public boolean hasNext() {
		int s = this.counter.length;
		if(s==0)
			return false;
		return this.counter[(s - 1)] < ((Object[])this.matrix.get(s - 1)).length;
	}

	public Object[] next(Object[] ls) {
		if (!hasNext()) {
			throw new NoSuchElementException("no more elements");
		}

		for (int i = 0; i < this.counter.length; i++) {
			ls[i] = ((Object[])this.matrix.get(i))[this.counter[i]];
		}
		incrementCounter();
		return ls;
	}

	private void incrementCounter() {
		for (int i = 0; i < this.counter.length; i++) {
			this.counter[i] += 1;
			if ((this.counter[i] != ((Object[])this.matrix.get(i)).length) || 
					(i >= this.counter.length - 1)) break;
			this.counter[i] = 0;
		}
	}

	public Object[] next()
	{
		this.combination = ((Object[])Array.newInstance(this.combination.getClass().getComponentType(), this.len));
		return next(this.combination);
	}
}

/* Location:           /data/indexer-main.jar
 * Qualified Name:     org.openstreetmap.osmgeocoder.util.AllCombinationIteratable
 * JD-Core Version:    0.6.2
 */