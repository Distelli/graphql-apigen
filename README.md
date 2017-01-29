# graphql-apigen

Generate Java APIs with GraphQL Schemas in order to facilitate "schema first" development.

This project is sponsored by the [Distelli Platform](http://www.distelli.com/). Build and
deploy automation tools with audit trails.

## NOTE: Work in progress

The generated code is subject to change (hopefully for the better).

The code is all generated based on [graphql-apigen.stg](apigen/src/main/resources/graphql-apigen.stg),
and the model input is defined in [STModel.java](apigen/src/main/java/com/distelli/graphql/apigen/STModel.java).

Contributions are welcomed.

### Posts Example

Create a file to define your schema. In this example we are creating the `schema/posts.graphql` file:

```graphql
type Author @java(package:"com.distelli.posts") {
    id: Int! # the ! means that every author object _must_ have an id
    firstName: String
    lastName: String
    posts: [Post] # the list of Posts by this author
}

type Post @java(package:"com.distelli.posts") {
    id: Int!
    title: String
    author: Author
    votes: Int
}

# the schema allows the following query:
type QueryPosts @java(package:"com.distelli.posts") {
    posts: [Post]
}

input InputPost @java(package:"com.distelli.posts") {
    title: String
    authorId: Int!
}

# this schema allows the following mutation:
type MutatePosts @java(package:"com.distelli.posts") {
    createPost(post:InputPost): Post
    upvotePost(
      postId: Int!
    ): Post
}
```

Notice that we annotate the types with a java package name.  The above schema
will generate the following java **interfaces** in `target/generated-sources/apigen`
(in the `com.distelli.posts` package):

* `Author` and `Author.Resolver`
* `Post` and `Post.Resolver`
* `QueryPosts`
* `InputPost`
* `MutatePosts`

The `*.Resolver` interfaces are only generated if their is a field named "id". This
interface may be implemented to resolve a `*.Unresolved` (only the id field defined)
into a fully resolved implementation (all fields defined). All interface methods
have "default" implementations that return null.

Each of these interfaces also have a default inner class named `*.Builder` and
`*.Impl`. The `*.Builder` will have a no-argument constructor and a constructor
that takes the parent interface as an argument. The `*.Builder` will also have a
method `with<FieldName>(<FieldType>)` for each no-arg field which returns the
builder and a `build()` method that creates a `*.Impl`.

Any field that takes arguments will cause a `*.<FieldName>Args` interface to be
generated with methods for each input field.

Any field that does NOT take arguments will generate method names prefixed with
"get".

Finally, the above schema also generates a Guice module `PostsModule` which adds to
a `Map<String, GraphQLType>` multibinder (the name "PostsModule" comes from the
filename which defines the schema). See below for information about using Spring for
Dependency Injection.

Putting this all together, we can implement the `QueryPosts` implementation as such:

```java
    public class QueryPostsImpl implements QueryPosts {
        private Map<Integer, Post> posts;
        public QueryPostsImpl(Map<Integer, Post> posts) {
            this.posts = posts;
        }
        @Override
        public List<Post> getPosts() {
            return new ArrayList<>(posts.values());
        }
    }
```

...and the `MutatePosts` implementation as such:

```java
    public class MutatePostsImpl implements MutatePosts {
        private AtomicInteger nextPostId = new AtomicInteger(1);
        private Map<Integer, Post> posts;
        public MutatePostsImpl(Map<Integer, Post> posts) {
            this.posts = posts;
        }
        @Override
        public Post createPost(MutatePosts.CreatePostArgs args) {
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
                if ( null == post ) {
                    throw new NoSuchEntityException("PostId="+args.getPostId());
                }
                Post upvoted = new Post.Builder(post)
                    .withVotes(post.getVotes()+1)
                    .build();
                posts.put(args.getPostId(), upvoted);
                return upvoted;
            }
        }
    }
```

...and the `Author.Resolver` interface as such:

```java
    public class AuthorResolver implements Author.Resolver {
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
```

...and the `Post.Resolver` interface as such:

```java
     public class PostResolver implements Post.Resolver {
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
```

...and you can use Guice to wire it all together as such (see below on
using this from Spring):

```java
    public class MainModule implements AbstractModule {
        @Override
        protected void configure() {
            // Create the "data" used by the implementations:
            Map<Integer, Post> posts = new LinkedHashMap<>();
            Map<Integer, Author> authors = new LinkedHashMap<>();
            // Install the generated module:
            install(new PostsModule());
            // Declare our implementations:
            bind(Author.Resolver.class)
                .toInstance(new AuthorResolver(authors));
            bind(Post.Resolver.class)
                .toInstance(new PostResolver(posts));
            bind(MutatePosts.class)
                .toInstance(new MutatePostsImpl(posts));
            bind(QueryPosts.class)
                .toInstance(new QueryPostsImpl(posts));
        }
    }
```

...and to use it:

```java
    public class GraphQLServlet extends HttpServlet {
        private static ObjectMapper OM = new ObjectMapper();
        private GraphQL graphQL;
        @Inject
        protected void GraphQLServlet(Map<String, GraphQLType> types) {
            GraphQLSchema schema = GraphQLSchema.newSchema()
                .query((GraphQLObjectType)types.get("QueryPosts"))
                .mutation((GraphQLObjectType)types.get("MutatePosts"))
                .build(new HashSet<>(types.values()));
            graphQL = new GraphQL(schema, new BatchedExecutionStrategy());
        }
        protected void	service(HttpServletRequest req, HttpServletResponse resp) {
            ExecutionResult result = graphQL.execute(req.getParameter("query"));
            OM.writeValue(resp.getOutputStream(), result);
        }
    }
```

This example is also a unit test which can be found
[here](apigen/src/test/projects/posts/src/test/java/com/disteli/posts/PostsTest.java)

### Using Spring instead of Guice

If you want to use Spring to wire the components together instead of Guice, you need to 
instruct Spring to include the generated code in a package-scan. Spring will find the `@Named`
annotated components and will inject any dependencies (the type resolvers you implement, etc)

For example, if your code was generated into the package `com.distelli.posts`, the spring
configuration would look like this: 

```java 
@ComponentScan("com.distelli.posts")
@Configuration
public class MyAppConfig {
        ...
}                                   
```

To generate a mapping similar to the guice code above, you can add this to your spring
configuration:

```java
    @Bean
    public Map<String, GraphQLType> graphqlTypeMap(List<Provider<? extends GraphQLType>> typeList) {
        return typeList.stream().map(Provider::get).collect(Collectors.toMap(GraphQLType::getName, Function.identity()));
    }
```

This will take any GraphQLTypes and generate a map of their string name to their implementation.

### Getting started

#### How to use the latest release with Maven

Generate the code with the following maven:

```xml
<project ...>
  ...
  <properties>
    <apigen.version>2.0.0</apigen.version>
  </properties>

  <build>
    <plugins>
      ...
      <plugin>
        <groupId>com.distelli.graphql</groupId>
        <artifactId>graphql-apigen</artifactId>
        <version>${apigen.version}</version>
        <configuration>
          <!-- This is only needed when using Guice -->
          <guiceModuleName>com.example.my.MyGuiceModule</guiceModuleName>
          <!-- This is only needed if you omit the @java(package:"...")
               annotations from your schema types. Using this feature
               also means your GraphQL schema can NOT be depended upon
               by GraphQL schemas defined in other maven projects. See:
               https://github.com/Distelli/graphql-apigen/issues/5#issuecomment-275923555
          -->
          <defaultPackageName>com.example.my</defaultPackageName>
        </configuration>
        <executions>
          <execution>
            <id>why-is-this-needed-who-knows</id>
            <goals>
              <goal>apigen</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <dependencies>
    ...
    <!-- Required by the generated code -->
    <dependency>
      <groupId>com.distelli.graphql</groupId>
      <artifactId>graphql-apigen-deps</artifactId>
      <version>${apigen.version}</version>
    </dependency>

    <!-- Optional, dependencies if using Guice for Dependency Injection -->
    <dependency>
      <groupId>com.google.inject</groupId>
      <artifactId>guice</artifactId>
      <version>4.0</version>
    </dependency>

    <dependency>
      <groupId>com.google.inject.extensions</groupId>
      <artifactId>guice-multibindings</artifactId>
      <version>4.0</version>
    </dependency>

  </dependencies>

</project>
```

Be sure to replace `com.example.*` above with the desired values.

Place your GraphQL files in `schema/*.graphql`.

#### TODO: Support gradle?

File an [issue](https://github.com/Distelli/graphql-apigen/issues), or even better
is to send a pull request :).
