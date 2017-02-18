package com.distelli.graphql;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import graphql.schema.DataFetchingEnvironment;

public interface AsyncResolver<T> {
    CompletableFuture<List<T>> resolve(final List<T> unresolved);
}
