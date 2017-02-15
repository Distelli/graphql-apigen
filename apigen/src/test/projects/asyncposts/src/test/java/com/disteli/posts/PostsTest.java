package com.distelli.posts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import graphql.ExecutionResult;
import graphql.GraphQLAsync;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.junit.Before;
import org.junit.Test;

import static graphql.execution.async.AsyncExecutionStrategy.parallel;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class PostsTest {

  private Injector injector;
  private GraphQLAsync graphQL;
  private ObjectMapper om;

  @Before
  public void setup() throws Exception {
    injector = setupInjector();
    initGraphQLAsync(injector, "QueryPosts", "MutatePosts");
    om = new ObjectMapper();
    om.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  @Test
  public void shouldRetrieveAllPosts() throws Exception {
    // Given
    String query = "{posts{title author{firstName lastName}}}";

    // When
    CompletionStage<ExecutionResult> future =
      graphQL.executeAsync(query, null, null, emptyMap());

    // Then
    final String result = printAndExtractValue(future);
    assertTrue(result.contains("firstName\":\"Brian"));
    assertTrue(result.contains("firstName\":\"Rahul"));
  }

  @Test
  public void shouldCreatePost() throws Exception {
    // Given
    String query = "mutation{createPost(post:{title:\"NEW\" authorId:1}){title}}";

    // When
    CompletionStage<ExecutionResult> future =
      graphQL.executeAsync(query, null, null, emptyMap());

    // Then
    final String result = printAndExtractValue(future);
    assertTrue(result.contains("title\":\"NEW"));
    assertTrue(result.contains("createPost"));
  }

  public static class AuthorResolver implements Author.AsyncResolver {
    private Map<Integer, Author> authors;

    public AuthorResolver(Map<Integer, Author> authors) {
      this.authors = authors;
    }

    @Override
    public CompletableFuture<Object> resolve(final DataFetchingEnvironment env) {
      if (env.getParentType().getName().equals("Post")) {
        final Post post = env.getSource();
        if (post.getAuthor().getClass().equals(Author.Unresolved.class)) {
          return CompletableFuture.completedFuture(authors.values()
            .stream()
            .filter(it -> it.getId() == post.getAuthor().getId())
            .limit(1)
            .iterator()
            .next());
        }
      }
      return null;
    }
  }

  public static class PostResolver implements Post.AsyncResolver {
    private Map<Integer, Post> posts;

    public PostResolver(Map<Integer, Post> posts) {
      this.posts = posts;
    }

    @Override
    public CompletableFuture<Object> resolve(final DataFetchingEnvironment env) {
      if (env.containsArgument("post")) { // mutation
        Map<String, Object> values = env.getArgument("post");
        Post createVehicle = new Post.Builder()
          .withId(posts.size() + 1)
          .withTitle(values.get("title").toString())
          .withAuthor(new Author.Unresolved((Integer)values.get("authorId")))
          .build();
        return completedFuture(createVehicle);
      }
      return completedFuture(posts.values().stream().collect(toList())); // is query all
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
          bind(Author.AsyncResolver.class)
            .toInstance(new AuthorResolver(authors));
          bind(Post.AsyncResolver.class)
            .toInstance(new PostResolver(posts));
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
      .mutation((GraphQLObjectType)types.get(mutationName))
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

  private void checkExecutionResult(ExecutionResult result) throws Exception {
    if (null == result.getErrors() || result.getErrors().size() <= 0) return;
    ObjectMapper om = new ObjectMapper();
    om.enable(SerializationFeature.INDENT_OUTPUT);
    String errors = om.writeValueAsString(result.getErrors());
    fail(errors);
  }
}
