package in.geocoder.component.geocoder.util;

import java.lang.reflect.Array;
import java.util.Iterator;

public class OrderedChoiceIterable implements Iterable<Integer[]> {
	private final Integer[] input;
	private final int numElements;

	public OrderedChoiceIterable(Integer[] input) {
		this.input = ((Integer[])input.clone());
		this.numElements = (1 << input.length);
	}

	public Iterator<Integer[]> iterator() {
		return new Iterator<Integer[]>() {
			int current = 0;

			public boolean hasNext() {
				return this.current < OrderedChoiceIterable.this.numElements;
			}

			public Integer[] next() {
				int size = countBits(this.current);

				Integer[] element = (Integer[])Array.newInstance(OrderedChoiceIterable.this.input
						.getClass().getComponentType(), size);

				int n = 0;
				for (int i = 0; i < OrderedChoiceIterable.this.input.length; i++) {
					long b = 1L << i;

					if ((this.current & b) != 0L)
					{
						element[(n++)] = OrderedChoiceIterable.this.input[i];
					}
				}

				if (element.length > 0) {
					for (int i = 1; i < element.length; i++) {
						if (element[i].intValue() - element[(i - 1)].intValue() > 1) {
							element = null;
							break;
						}
					}
				}

				this.current += 1;
				return element;
			}

			public void remove() {
				throw new UnsupportedOperationException(
						"May not remove elements from a power set");
			}
		};
	}

	private int countBits(int n) {
		int m = n - (n >> 1 & 0xDB6DB6DB) - (n >> 2 & 0x49249249);
		return (m + (m >> 3) & 0xC71C71C7) % 63;
	}
}