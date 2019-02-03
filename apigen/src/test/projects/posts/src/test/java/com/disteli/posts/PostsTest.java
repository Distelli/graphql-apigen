package com.distelli.posts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.*;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.batched.BatchedExecutionStrategy;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        private AtomicInteger nextPostId = new AtomicInteger(5);
        private Map<Integer, Post> posts;
        private DataFetchingEnvironment env;
        public MutatePostsImpl(Map<Integer, Post> posts) {
            this.posts = posts;
        }

        @Override
        public MutatePostsImpl resolve(DataFetchingEnvironment env) {
            this.env = env;
            return this;
        }

        @Override
        public Post createPost(MutatePosts.CreatePostArgs args) {
            if ( ! "authorized-user".equals(env.getContext()) ) {
                throw new java.security.AccessControlException("context MUST be authorized-user");
            }
            InputPost req = args.getPost();
            Post.Builder postBuilder = new Post.Builder()
                .withTitle(req.getTitle())
                .withAuthor(new Author.Unresolved(req.getAuthorId()));
            Post post;
            synchronized ( posts ) {
                Integer id = nextPostId.incrementAndGet();
                post = postBuilder.withId(id).build();
                posts.put(id, post);
            }
            return post;
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
                if ( null == unresolved ) {
                    result.add(null);
                } else {
                    result.add(posts.get(unresolved.getId()));
                }
            }
            return result;
        }
    }
    public Injector setup() throws Exception {
        // Setup datastores:
        Map<Integer, Author> authors = new LinkedHashMap<>();
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
        Map<Integer, Post> posts = new LinkedHashMap<>();
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
//            new PostsModule(),
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
                }
            });
        return null;
    }

    @Test
    public void testQuery() throws Exception {
        Injector injector = setup();

        // TODO: Support "schema" type so this is generated too :)
        Map<String, GraphQLType> types =
            injector.getInstance(Key.get(new TypeLiteral<Map<String, GraphQLType>>(){}));
        GraphQLSchema schema = GraphQLSchema.newSchema()
            .query((GraphQLObjectType)types.get("QueryPosts"))
            .mutation((GraphQLObjectType)types.get("MutatePosts"))
            .build(new HashSet<>(types.values()));

        GraphQL graphQL = new GraphQL(schema, new BatchedExecutionStrategy());
        ObjectMapper om = new ObjectMapper();
        om.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        // Using GraphQL Mutation:
        ExecutionResult result = graphQL.execute(
            "mutation{createPost(post:{title:\"NEW\" authorId:1}){title}}",
            "authorized-user");
        checkExecutionResult(result);
        assertEquals("{\"createPost\":{\"title\":\"NEW\"}}", om.writeValueAsString(result.getData()));

        // ...or the API:
        MutatePosts mutatePosts = injector.getInstance(MutatePosts.class);
        mutatePosts.createPost(new MutatePosts.CreatePostArgs() {
                public InputPost getPost() {
                    return new InputPost.Builder()
                        .withTitle("API")
                        .withAuthorId(2)
                        .build();
                }
            });

        // Using GraphQL Query:
        result = graphQL.execute("{posts{title author{firstName lastName}}}");
        checkExecutionResult(result);

        String value = om.writeValueAsString(result.getData());
        assertEquals("{\"posts\":[{\"author\":{\"firstName\":\"Brian\",\"lastName\":\"Maher\"},\"title\":\"GraphQL Rocks\"},{\"author\":{\"firstName\":\"Rahul\",\"lastName\":\"Singh\"},\"title\":\"Announcing Callisto\"},{\"author\":{\"firstName\":\"Rahul\",\"lastName\":\"Singh\"},\"title\":\"Distelli Contributing to Open Source\"},{\"author\":{\"firstName\":\"Brian\",\"lastName\":\"Maher\"},\"title\":\"NEW\"},{\"author\":{\"firstName\":\"Rahul\",\"lastName\":\"Singh\"},\"title\":\"API\"}]}", value);

        // ...or the API:
        QueryPosts queryPosts = injector.getInstance(QueryPosts.class);
        List<Post> posts = queryPosts.getPosts();
        // ...since we are not using GraphQL, the authors will not be resolved:
        assertEquals(posts.get(0).getAuthor().getClass(), Author.Unresolved.class);
        assertArrayEquals(
            new String[]{"GraphQL Rocks", "Announcing Callisto", "Distelli Contributing to Open Source", "NEW", "API"},
            posts.stream().map((post) -> post.getTitle()).toArray(size -> new String[size]));
        assertArrayEquals(
            new Integer[]{1,2,2,1,2},
            posts.stream().map((post) -> post.getAuthor().getId()).toArray(size -> new Integer[size]));
    }

    private void checkExecutionResult(ExecutionResult result) throws Exception {
        if ( null == result.getErrors() || result.getErrors().size() <= 0 ) return;
        ObjectMapper om = new ObjectMapper();
        om.enable(SerializationFeature.INDENT_OUTPUT);
        String errors = om.writeValueAsString(result.getErrors());
        fail(errors);
    }
}
