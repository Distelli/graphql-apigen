package com.distelli.posts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import graphql.ExecutionResult;
import graphql.GraphQLAsync;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.junit.Before;
import org.junit.Test;

import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;
import static com.google.common.collect.Lists.newArrayList;
import static graphql.execution.async.AsyncExecutionStrategy.parallel;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertTrue;


public class PostsTest {

  private Injector injector;
  private GraphQLAsync graphQL;
  private ObjectMapper om;

  @Before
  public void setup() throws Exception {
    injector = setupInjector();
    initGraphQLAsync(injector, "QueryPosts", "MutatePosts");
    om = new ObjectMapper();
    om.enable(ORDER_MAP_ENTRIES_BY_KEYS);
  }

  @Test
  public void shouldRetrieveAllPosts() throws Exception {
    // Given
    final String query = "{posts{title author{firstName lastName}}}";

    // When
    final CompletionStage<ExecutionResult> future =
      graphQL.executeAsync(query, null, null, emptyMap());

    // Then
    final String result = printAndExtractValue(future);
    assertTrue(result.contains("firstName\":\"Brian"));
    assertTrue(result.contains("firstName\":\"Rahul"));
  }

  @Test
  public void shouldCreatePost() throws Exception {
    // Given
    final String query = "mutation{createPost(post:{title:\"NEW\" authorId:1}){title}}";

    // When
    final CompletionStage<ExecutionResult> future =
      graphQL.executeAsync(query, null, null, emptyMap());

    // Then
    final String result = printAndExtractValue(future);
    assertTrue(result.contains("title\":\"NEW"));
    assertTrue(result.contains("createPost"));
  }

  public static class QueryPostsImpl implements QueryPosts {
    @Override
    public List<Post> getPosts() {
      return emptyList();
    }
  }

  public static class MutatePostsImpl implements MutatePosts {
    @Override
    public Post createPost(MutatePosts.CreatePostArgs args) {
      InputPost req = args.getPost();
      return new Post.Builder()
        .withTitle(req.getTitle())
        .withAuthor(new Author.Unresolved(req.getAuthorId()))
        .build();
    }

    @Override
    public Post upvotePost(MutatePosts.UpvotePostArgs args) {
      return new Post.Builder()
        .withId(args.getPostId())
        .withVotes(1)
        .build();
    }
  }

  public static class AuthorResolver implements Author.AsyncResolver {
    private Map<Integer, Author> authors;

    public AuthorResolver(Map<Integer, Author> authors) {
      this.authors = authors;
    }

    @Override
    public CompletableFuture<List<Author>> resolve(final List<Author> unresolved) {
      if (!requireNonNull(unresolved).isEmpty()) {
        final Author author = unresolved.get(0);
        return completedFuture(unresolved.stream().map(u -> authors.get(u.getId())).collect(toList()));
      }
      return completedFuture(newArrayList(authors.values())); // is query all
    }
  }

  public static class PostResolver implements Post.AsyncResolver {
    private Map<Integer, Post> posts;

    public PostResolver(Map<Integer, Post> posts) {
      this.posts = posts;
    }

    @Override
    public CompletableFuture<List<Post>> resolve(final List<Post> unresolved) {
      if (!requireNonNull(unresolved).isEmpty()) {
        Post post = unresolved.get(0);
        if (isNull(post.getId())) { // is create new post mutation
          post = new Post.Builder(post).withId(posts.size() + 1).build();
          posts.put(post.getId(), post);
          return completedFuture(newArrayList(post));
        }
        return completedFuture(unresolved.stream().map(u -> {
          Post resolved = posts.get(u.getId());
          if (u instanceof Post) { // upvote
            resolved = new Post.Builder(resolved).withVotes(resolved.getVotes() + 1).build();
            posts.put(resolved.getId(), resolved);
          }
          return resolved;
        }).collect(toList())); // is argument query by id
      }

      return completedFuture(newArrayList(posts.values())); // is query all
    }
  }

  public Injector setupInjector() throws Exception {
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
        .withPosts(Arrays.asList(new Post[]{
          new Post.Unresolved(3),
          new Post.Unresolved(2),
        }))
        .build());

    Map<Integer, Post> posts = new LinkedHashMap<>();
    posts.put(1,
      new Post.Builder()
        .withId(1)
        .withTitle("GraphQL Rocks")
        .withAuthor(new Author.Unresolved(1))
        .build());
    posts.put(2,
      new Post.Builder()
        .withId(2)
        .withTitle("Announcing Callisto")
        .withAuthor(new Author.Unresolved(2))
        .build());
    posts.put(3,
      new Post.Builder()
        .withId(3)
        .withTitle("Distelli Contributing to Open Source")
        .withAuthor(new Author.Unresolved(2))
        .build());

    Injector injector = Guice.createInjector(
      new PostsModule(),
      new AbstractModule() {
        @Override
        protected void configure() {
          bind(Author.AsyncResolver.class).toInstance(new AuthorResolver(authors));
          bind(Post.AsyncResolver.class).toInstance(new PostResolver(posts));
          bind(MutatePosts.class).toInstance(new MutatePostsImpl());
          bind(QueryPosts.class).toInstance(new QueryPostsImpl());
        }
      });
    return injector;
  }

  protected void initGraphQLAsync(final Injector injector, final String queryName, final String mutationName) {
    Map<String, GraphQLType> types =
      injector.getInstance(Key.get(new TypeLiteral<Map<String, GraphQLType>>() {
      }));

    GraphQLSchema schema = GraphQLSchema.newSchema()
      .query((GraphQLObjectType) types.get(queryName))
      .mutation((GraphQLObjectType) types.get(mutationName))
      .build(new HashSet<>(types.values()));
    graphQL = new GraphQLAsync(schema, parallel(), parallel());
  }

  protected String printAndExtractValue(CompletionStage<ExecutionResult> future) throws ExecutionException, InterruptedException {
    ExecutionResult result = future.toCompletableFuture().get();
    String doc = null;
    try {
      doc = om.writeValueAsString(result);
      System.out.println(doc);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return doc;
  }
}
