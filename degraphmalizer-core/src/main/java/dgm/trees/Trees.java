package dgm.trees;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import dgm.exceptions.UnreachableCodeReachedException;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Helper functions for {@link Tree}s
 * 
 * @author wires
 *
 */
public final class Trees
{
    private Trees() {}

	public static <A> void printTree(Tree<A> tree, PrintStream ps)
	{
		ps.printf("(%s (", tree.value());
		for (Tree<A> t : tree.children())
		{
			printTree(t, ps);
			ps.print(",");
		}
		ps.print("))");
	}

    /**
     * Turn a {@code Tree} of {@code Optional}s into a {@code Optional}al {@code Tree}, meaning that if there is one
     * absent value in the tree, the whole tree is absent.
     */
    public static <A> Optional<Tree<A>> optional(Tree<Optional<A>> treeOfOptionals)
    {
        class ValueIsAbsent extends RuntimeException {}

        final Function<Optional<A>, A> nonAbsent = new Function<Optional<A>, A>()
        {
            @Override
            public A apply(Optional<A> input)
            {
                if(!input.isPresent())
                    throw new ValueIsAbsent();

                return input.get();
            }
        };

        try
        {
            return Optional.of(map(nonAbsent, treeOfOptionals));
        }
        catch (ValueIsAbsent e)
        {
            return Optional.absent();
        }
    }

    public static final <_> boolean isLeaf(final Tree<_> tree)
    {
        return Iterables.isEmpty(tree.children());
    }

    /**
	 * Map a function over a tree
	 * 
	 * @param fn Function
	 * @param tree Tree of {@code A}'s
	 * @return Tree of {@code B}'s
	 */
	public static <A,B> Tree<B> map(final Function<A,B> fn, Tree<A> tree)
	{
		final B value = fn.apply(tree.value());
		
		if (isLeaf(tree))
            return new ImmutableTree<B>(value);

		else
		{
			final Function<Tree<A>,Tree<B>> tmap = new Function<Tree<A>,Tree<B>>()
				{
					public Tree<B> apply(Tree<A> tree)
					{
						return map(fn, tree);
					}
				};
			final Iterable<Tree<B>> tb = Iterables.transform(tree.children(), tmap);
			return new ImmutableTree<B>(value, tb);
		}
	}
	
	/**
	 * Turn a {@link Function} into an {@link Callable}
	 * 
	 * @param fn Function
	 * @return Callable
	 */
	public static <A,B> Function<A,Callable<B>> mkAsync(final Function<A,B> fn)
	{
		return new Function<A,Callable<B>>()
        {
            @Override
            public Callable<B> apply(final A a)
            {
                return new Callable<B>()
                {
                    @Override
                    public B call() throws Exception
                    {
                        return fn.apply(a);
                    }
                };
            }
        };
	}
	
	/**
	 * In parallel, map a function over a tree
	 * 
	 * @param executor Where to submit the jobs to
	 * @param fn The function applied to every node in the tree
	 * @param tree The tree to map
	 * @throws InterruptedException
	 */
	public static <A,B> Tree<B> pmap(final ExecutorService executor, final Function<A,B> fn, Tree<A> tree)
	        throws InterruptedException, ExecutionException
	{
        final class PmapException extends RuntimeException
        {
            public PmapException(Exception e)
            {
                super(e);
            }
        }

		// convert the tree into jobs, and submit them to the executor
		final Function<A,Future<B>> toJob = new Function<A,Future<B>>()
			{
				public Future<B> apply(final A a)
				{
					return executor.submit(new Callable<B>()
						{
							@Override
                            public B call() throws Exception
                            {
	                            return fn.apply(a);
                            }
						});
				}
			};
		
		// get automatically waits for jobs to finish
		final Function<Future<B>, B> waitDone = new Function<Future<B>, B>()
			{
                @Override
				public B apply(final Future<B> b)
				{
					try
                    {
	                    return b.get();
                    }
                    catch (InterruptedException e)
                    {
	                    throw new PmapException(e);
                    }
                    catch (ExecutionException e)
                    {
                    	throw new PmapException(e);
                    }
				}
			};

        // for each node in the tree, start a job
        final Tree<Future<B>> jobTree = map(toJob, tree);

        try
        {
            // blocking wait for all jobs to finish
            return map(waitDone, jobTree);
        }
        catch (final PmapException exception)
        {
            final Throwable inner = exception.getCause();

            if(inner instanceof InterruptedException)
                throw (InterruptedException)inner;

            if(inner instanceof ExecutionException)
                throw (ExecutionException)inner;

            throw exception;
        }
	}

	
	/**
	 * Convert a tree of JsonNode's into a Json tree like so:
	 * 
	 * <pre>
	 * { "value": NODE_VALUE,
	 *   "children: [
	 *   	{
	 *      	"value": CHILD1_VALUE,
	 *          "children": [ ... ]
	 *      },
	 *      {
	 *           "value": CHILD2_VALUE,
	 *           "children": [....]
	 *      }
	 *   ]
	 * }
	 * </pre>
	 */
	// TODO all this is very memory inefficient and will lead to stack overflows for very deep trees
	public static ObjectNode toJsonTree(ObjectMapper objectMapper, Tree<? extends JsonNode> tree)
	{
		final ObjectNode node = objectMapper.createObjectNode();
		node.put("_value", tree.value());
		
		final ArrayNode children = objectMapper.createArrayNode();
		for (Tree<? extends JsonNode> child : tree.children())
			children.add(toJsonTree(objectMapper, child));
		
		node.put("_children", children);
		
		return node;
	}
	
	/** Helper method to directly walk a TreeNode instance */
	public static <T> Iterable<T> bfsWalk(Tree<T> root)
	{
		// view TreeNode as tree
		final TreeViewer<Tree<T>> viewer = new TreeViewer<Tree<T>>()
			{
				@Override
				public Iterable<Tree<T>> children(Tree<T> node)
				{
					return node.children();
				}
			};
		
		// get value from a tree
		final Function<Tree<T>,T> getValue = new Function<Tree<T>,T>()
			{
                @Override
				public T apply(Tree<T> node)
				{
					return node.value();
				}
			};
		
		final Iterable<Tree<T>> ti = bfsWalk(root, viewer);
		
		return Iterables.transform(ti, getValue);
	}
	
	public static <A> Iterable<A> bfsWalk(final A root, final TreeViewer<A> viewer)
	{
		return new Iterable<A>()
			{
				@Override
				public Iterator<A> iterator()
				{
					return new Iterator<A>()
						{
							final Queue<A> q = new LinkedList<A>(Collections.singleton(root));
							
							@Override
							public boolean hasNext()
							{
								return !q.isEmpty();
							}
							
							@Override
							public A next()
							{
								final A a = q.poll();
								
								// add children to end of the queue
								for (A c : viewer.children(a))
									if(!q.offer(c))
                                        throw new UnreachableCodeReachedException();
								
								return a;
							}
							
							@Override
							public void remove()
							{
								// TODO use some sort of standard
								// "NotImplemented" exception (how does guava do
								// this?)
								throw new RuntimeException("Not Implemented");
							}
						};
				}
			};
	}
	

}