package com.distelli.graphql;

import graphql.schema.DataFetchingEnvironment;

public interface ResolveDataFetchingEnvironment<T> {
  public T resolve(DataFetchingEnvironment env);
}
