package com.distelli.graphql;

import graphql.execution.batched.Batched;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

public class AsyncResolverDataFetcher implements DataFetcher {
  private AsyncResolver resolver;

  public AsyncResolverDataFetcher(AsyncResolver resolver) {
    this.resolver = resolver;
  }

  @Batched
  @Override
  public Object get(DataFetchingEnvironment env) {
    return resolver.resolve(env);
  }

  @Override
  public String toString() {
    return "ResolverDataFetcher{" +
      "resolver=" + resolver +
      "}";
  }
}
