# graphql-apigen

Generate Java APIs with GraphQL Schemas in order to facilitate "schema first" development.

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

Notice that we annotate the types with a java package name. The above schema
will generate the following java **interfaces** in `target/generated-sources/apigen`
(in the `com.distelli.posts` package):

* `Author` and `Author.Resolver`
* `Post` and `Post.Resolver`
* `QueryPosts`
* `InputPost`
* `MutatePosts`

The `*.Resolver` interfaces are only generated if their is a field named "id". This
interface may be implemented to resolve a `*.Unresolved` (only the id field defined)
into a fully resolved implementation (all fields defined). All interfaces have
"default" implementations that return null.

Each of these interfaces also have a default inner class named `*.Builder` and
`*.Impl`. The `*.Builder` will have a no-argument constructor and a constructor
that takes the parent interface as an argument. The `*.Builder` will also have a
method `with<FieldName>(<FieldType>)` for each no-arg field which returns the
builder and a `build()` method that creates a `*.Impl`.

Any field that takes arguments will cause a `*.<FieldName>Args` interface to be
generated with methods for each input field.

Finally, the above schema also generates a Guice module `PostsModule` which adds to
a `Map<String, GraphQLType>` multibinder (the name "PostsModule" comes from the
filename which defines the schema).

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

...and you can use Guice to wire it all together as such:

```java
    public class MainModule implements AbstractModule {
        @Override
        protected void configure() {
            Map<Integer, Post> posts = new LinkedHashMap<>();
            Map<Integer, Author> authors = new LinkedHashMap<>();
            install(new PostsModule()); // The generated module.
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

### Getting started

##### How to use the latest release with Maven

Dependency:

```xml
  <dependency>
    <groupId>com.distelli.graphql</groupId>
    <artifactId>graphql-apigen-deps</artifactId>
    <version>1.1.0</version>
  </dependency>

```

###
