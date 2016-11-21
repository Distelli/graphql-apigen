package com.distelli.graphql;

import java.util.List;

public interface Resolver<T> {
    public List<T> resolve(List<T> unresolved);
}
