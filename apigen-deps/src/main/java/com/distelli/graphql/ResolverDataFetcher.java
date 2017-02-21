package com.distelli.graphql;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import graphql.execution.batched.Batched;
import graphql.execution.batched.BatchedDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class ResolverDataFetcher implements DataFetcher {
    private DataFetcher fetcher;
    private Resolver<Object> resolver;
    private AsyncResolver<Object> asyncResolver;
    private boolean isBatched;
    private int listDepth;

    public ResolverDataFetcher(DataFetcher fetcher, Object resolver, int listDepth) {
        this.fetcher = fetcher;
        if (resolver instanceof AsyncResolver) {
            this.asyncResolver = (AsyncResolver) resolver;
        } else {
            this.resolver = (Resolver) resolver;
        }
        this.listDepth = listDepth;
        if ( fetcher instanceof BatchedDataFetcher ) {
            this.isBatched = true;
        } else {
            try {
                Method getMethod = fetcher.getClass()
                    .getMethod("get", DataFetchingEnvironment.class);
                this.isBatched = null != getMethod.getAnnotation(Batched.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    @Batched
    @Override
    public Object get(DataFetchingEnvironment env) {
        List<Object> unresolved = new ArrayList<>();
        Object result;
        int depth = listDepth;
        if ( env.getSource() instanceof List ) { // batched.
            result = getBatched(env);
            if (nonNull(resolver) || nonNull(asyncResolver)) addUnresolved(unresolved, result, ++depth);
        } else {
            result = getUnbatched(env);
            if (nonNull(resolver) || nonNull(asyncResolver)) addUnresolved(unresolved, result, depth);
        }
        if (isNull(resolver) && isNull(asyncResolver)) return result;
        final int finalDepth = depth;
        return isNull(asyncResolver) ?
          replaceResolved(result, resolver.resolve(unresolved).iterator(), depth) :
          asyncResolver.resolve(unresolved)
            .thenApplyAsync(resolved -> replaceResolved(result, resolved.iterator(), finalDepth));
    }

    public Object replaceResolved(Object result, Iterator<Object> resolved, int depth) {
        if ( depth <= 0 ) {
            return resolved.next();
        }
        List<Object> resolvedResults = new ArrayList<>();
        if ( null == result ) return null;
        final List list = (List) result;
        if (list.isEmpty() && !isBatched) {
            final ArrayList resolvedList = new ArrayList();
            resolved.forEachRemaining(resolvedList::add);
            return resolvedList;
        }
        for ( Object elm : list ) {
            resolvedResults.add(replaceResolved(elm, resolved, depth-1));
        }
        return resolvedResults;
    }

    public void addUnresolved(List<Object> unresolved, Object result, int depth) {
        if ( depth <= 0 ) {
            unresolved.add(result);
            return;
        }
        if ( ! (result instanceof List) ) {
            if ( null == result ) return;
            throw new IllegalStateException("Fetcher "+fetcher+" expected to return a List for each result, got="+result);
        }
        for ( Object elm : (List)result ) {
            addUnresolved(unresolved, elm, depth-1);
        }
    }

    public Object getUnbatched(DataFetchingEnvironment env) {
        if ( ! isBatched ) return fetcher.get(env);
        DataFetchingEnvironment envCopy =
            new DataFetchingEnvironmentImpl(
                Collections.singletonList(env.getSource()),
                env.getArguments(),
                env.getContext(),
                env.getFields(),
                env.getFieldType(),
                env.getParentType(),
                env.getGraphQLSchema());
        Object result = fetcher.get(envCopy);
        if ( !(result instanceof List) || ((List)result).size() != 1 ) {
            throw new IllegalStateException("Batched fetcher "+fetcher+" expected to return list of 1");
        }
        return ((List)result).get(0);
    }

    public List<Object> getBatched(DataFetchingEnvironment env) {
        List sources = env.getSource();
        if ( isBatched ) {
            Object result = fetcher.get(env);
            if ( !(result instanceof List) || ((List)result).size() != sources.size() ) {
                throw new IllegalStateException("Batched fetcher "+fetcher+" expected to return list of "+sources.size());
            }
            return (List<Object>)result;
        }
        List<Object> result = new ArrayList<>();
        for ( Object source : sources ) {
            DataFetchingEnvironment envCopy =
                new DataFetchingEnvironmentImpl(
                    source,
                    env.getArguments(),
                    env.getContext(),
                    env.getFields(),
                    env.getFieldType(),
                    env.getParentType(),
                    env.getGraphQLSchema());
            result.add(fetcher.get(envCopy));
        }
        return result;
    }

    @Override
    public String toString() {
        return "ResolverDataFetcher{"+
            "resolver="+resolver+
            ", fetcher="+fetcher+
            ", isBatched="+isBatched+
            ", listDepth="+listDepth+
            "}";
    }
}
