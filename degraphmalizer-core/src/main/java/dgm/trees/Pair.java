package dgm.trees;

import com.tinkerpop.blueprints.*;
import dgm.GraphUtilities;

/**
 * A pair of values
 * 
 * @author wires
 *
 * @param <A>
 * @param <B>
 */
public class Pair<A,B>
{
	public final A a;
	public final B b;
	
	public Pair(A a, B b)
	{
		this.a = a;
		this.b = b;
	}

    @Override
	public final String toString()
	{
		final StringBuilder s = new StringBuilder("<");
		s.append(getStringRepresentation(a));
		s.append(",");
		s.append(getStringRepresentation(b));
		s.append(">");
		return s.toString();
	}
	
	// TODO it's a hack...
	private static String getStringRepresentation(Object x)
	{
        if(x == null)
            return "null";

		if (x instanceof Vertex)
		{
			final Object value = ((Vertex)x).getProperty(GraphUtilities.IDENTIFIER);
			return value != null ? value.toString().trim() : x.toString();
		}

		if (x instanceof Edge)
		{
			final Edge e = (Edge)x;
			final StringBuilder sb = new StringBuilder();
			sb.append(getStringRepresentation(e.getVertex(Direction.OUT)));
			sb.append("---[");
			sb.append(e.getLabel());
			sb.append("]--->");
			sb.append(getStringRepresentation(e.getVertex(Direction.IN)));
			return sb.toString().trim();
		}

		return x.toString().trim();
	}
}
