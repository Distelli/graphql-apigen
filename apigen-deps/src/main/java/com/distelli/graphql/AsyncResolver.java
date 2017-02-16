package com.distelli.graphql;

import java.util.concurrent.CompletableFuture;

import graphql.schema.DataFetchingEnvironment;

public interface AsyncResolver<T> {
    CompletableFuture<?> resolve(DataFetchingEnvironment env);
}
