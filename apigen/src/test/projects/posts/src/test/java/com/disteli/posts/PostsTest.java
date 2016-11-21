package com.distelli.posts;

import org.junit.Test;
import graphql.execution.batched.BatchedExecutionStrategy;
import java.util.*;
import graphql.schema.*;
import graphql.ExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.multibindings.MapBinder;
import javax.inject.Singleton;
import static org.junit.Assert.*;

public class PostsTest {
    public static class QueryPostsImpl implements QueryPosts {
        private Map<Integer, Post> posts;
        public QueryPostsImpl(Map<Integer, Post> posts) {
            this.posts = posts;
        }
        @Override
        public List<Post> getPosts() {
            return new ArrayList<>(posts.values());
        }
    }
    public static class MutatePostsImpl implements MutatePosts {
        private Map<Integer, Post> posts;
        public MutatePostsImpl(Map<Integer, Post> posts) {
            this.posts = posts;
        }
        @Override
        public Post upvotePost(MutatePosts.UpvotePostArgs args) {
            synchronized ( posts ) {
                Post post = posts.get(args.getPostId());
                // Should throw NoSuchEntityException!
                if ( null == post ) throw new RuntimeException("NotFound");

                Post upvoted = new Post.Builder(post)
                    .withVotes(post.getVotes()+1)
                    .build();
                posts.put(args.getPostId(), upvoted);
                return upvoted;
            }
        }
    }
    public static class AuthorResolver implements Author.Resolver {
        private Map<Integer, Author> authors;
        public AuthorResolver(Map<Integer, Author> authors) {
            this.authors = authors;
        }
        @Override
        public List<Author> resolve(List<Author> unresolvedList) {
            List<Author> result = new ArrayList<>();
            for ( Author unresolved : unresolvedList ) {
                // In a real app we would check if it is instanceof Author.Unresolved
                result.add(authors.get(unresolved.getId()));
            }
            return result;
        }
    }
    public static class PostResolver implements Post.Resolver {
        private Map<Integer, Post> posts;
        public PostResolver(Map<Integer, Post> posts) {
            this.posts = posts;
        }
        @Override
        public List<Post> resolve(List<Post> unresolvedList) {
            List<Post> result = new ArrayList<>();
            for ( Post unresolved : unresolvedList ) {
                result.add(posts.get(unresolved.getId()));
            }
            return result;
        }
    }
    @Test
    public void testQuery() throws Exception {
        // Setup datastores:
        Map<Integer, Author> authors = new HashMap<>();
        authors.put(1,
                    new Author.Builder()
                    .withId(1)
                    .withFirstName("Brian")
                    .withLastName("Maher")
                    .withPosts(Arrays.asList(
                                   new Post.Unresolved(1)))
                    .build());
        authors.put(2,
                    new Author.Builder()
                    .withId(2)
                    .withFirstName("Rahul")
                    .withLastName("Singh")
                    .withPosts(Arrays.asList(new Post[] {
                                new Post.Unresolved(4),
                                new Post.Unresolved(3),
                            }))
                    .build());
        Map<Integer, Post> posts = new HashMap<>();
        posts.put(1,
                  new Post.Builder()
                  .withId(1)
                  .withTitle("GraphQL Rocks")
                  .withAuthor(new Author.Unresolved(1))
                  .build());
        posts.put(3,
                  new Post.Builder()
                  .withId(3)
                  .withTitle("Announcing Callisto")
                  .withAuthor(new Author.Unresolved(2))
                  .build());
        posts.put(4,
                  new Post.Builder()
                  .withId(4)
                  .withTitle("Distelli Contributing to Open Source")
                  .withAuthor(new Author.Unresolved(2))
                  .build());

        Injector injector = Guice.createInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Author.Resolver.class)
                        .toInstance(new AuthorResolver(authors));
                    bind(Post.Resolver.class)
                        .toInstance(new PostResolver(posts));
                    bind(MutatePosts.class)
                        .toInstance(new MutatePostsImpl(posts));
                    bind(QueryPosts.class)
                        .toInstance(new QueryPostsImpl(posts));
                    // TODO: Generate this in GuiceModule:
                    MapBinder<String, GraphQLType> types =
                        MapBinder.newMapBinder(binder(), String.class, GraphQLType.class);
                    types.addBinding("MutatePosts")
                        .toProvider(MutatePostsTypeProvider.class)
                        .in(Singleton.class);
                    types.addBinding("QueryPosts")
                        .toProvider(QueryPostsTypeProvider.class)
                        .in(Singleton.class);
                    types.addBinding("Post")
                        .toProvider(PostTypeProvider.class)
                        .in(Singleton.class);
                    types.addBinding("Author")
                        .toProvider(AuthorTypeProvider.class)
                        .in(Singleton.class);
                }
            });

        // TODO: Support "schema" type so this is generated too :)
        Map<String, GraphQLType> types =
            injector.getInstance(Key.get(new TypeLiteral<Map<String, GraphQLType>>(){}));
        GraphQLSchema schema = GraphQLSchema.newSchema()
            .query((GraphQLObjectType)types.get("QueryPosts"))
            .mutation((GraphQLObjectType)types.get("MutatePosts"))
            .build(new HashSet<>(types.values()));

        ExecutionResult result = new GraphQL(schema, new BatchedExecutionStrategy()).execute("{posts{title author{firstName lastName}}}");
        if ( null != result.getErrors() && result.getErrors().size() > 0 ) {
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            String errors = om.writeValueAsString(result.getErrors());
            fail(errors);
        }
        ObjectMapper om = new ObjectMapper();
        om.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        String value = om.writeValueAsString(result.getData());
        assertEquals("{\"posts\":[{\"author\":{\"firstName\":\"Brian\",\"lastName\":\"Maher\"},\"title\":\"GraphQL Rocks\"},{\"author\":{\"firstName\":\"Rahul\",\"lastName\":\"Singh\"},\"title\":\"Announcing Callisto\"},{\"author\":{\"firstName\":\"Rahul\",\"lastName\":\"Singh\"},\"title\":\"Distelli Contributing to Open Source\"}]}", value);
    }
}
