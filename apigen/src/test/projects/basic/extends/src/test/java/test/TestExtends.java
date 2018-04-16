package test;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import com.google.inject.Guice;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import test.ExtendsModule;
import test.BaseModule;
import test.Alphabet;
import javax.inject.Inject;
import java.util.Map;
import java.util.HashSet;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLObjectType;
import graphql.GraphQL;
import graphql.ExecutionResult;
import graphql.execution.batched.BatchedExecutionStrategy;
import java.util.Optional;

public class TestExtends {
    private static Injector INJECTOR = Guice.createInjector(
        new ExtendsModule(),
        new BaseModule(),
        new AbstractModule() {
            @Override
            protected void configure() {
                Extends instance = new Extends.Builder()
                    .withAlpha(Alphabet.B)
                    .build();
                bind(Extends.class)
                    .toInstance(instance);
            }
            @Provides
            protected GraphQL provideGraphQL(Map<String, GraphQLType> types) {
                GraphQLSchema schema = GraphQLSchema.newSchema()
                    .query((GraphQLObjectType)types.get("Extends"))
                    .build(new HashSet<>(types.values()));
                return new GraphQL(schema, new BatchedExecutionStrategy());
            }
        });
    @Inject
    private GraphQL _graphQL;

    @Before
    public void before() {
        INJECTOR.injectMembers(this);
    }

    @Test
    public void test() {
        Extends ext = new Extends.Builder()
            .withBase(new Base.Builder()
                      .withBase("string")
                      .build())
            .build();
        assertEquals("string", ext.getBase().getBase());

        // Not very exciting, but shows how to copy:
        Extends ext2 = new Extends.Builder(ext)
            .build();
        assertEquals("string", ext2.getBase().getBase());

        assertNotSame(ext, ext2);

        // Verify we can inject implementations...
        Map result = (Map)_graphQL.execute("{alpha}").getData();
        assertEquals("B", ""+result.get("alpha"));
    }
}
