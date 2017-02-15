package com.distelli.graphql;

import java.util.concurrent.CompletableFuture;

import graphql.schema.DataFetchingEnvironment;

public interface AsyncResolver<T> {
    public CompletableFuture<Object> resolve(DataFetchingEnvironment env);
}
