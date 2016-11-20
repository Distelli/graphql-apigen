package com.distelli.graphql.apigen;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.TestMavenRuntime;
import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.runner.RunWith;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.0.5"})
public class ApiGenMojoTest {
    @Rule
    public TestResources resources = new TestResources();

    public MavenRuntime mavenRuntime;

    public ApiGenMojoTest(MavenRuntimeBuilder builder) throws Exception {
        mavenRuntime = builder.build();
    }

    @Test
    public void testBasic() throws Exception {
        File basedir = resources.getBasedir("basic");
        MavenExecutionResult result = mavenRuntime
            .forProject(basedir)
            .execute("clean", "compile");

        result.assertErrorFreeLog();
    }

    @Test
    public void testStarwars() throws Exception {
        File basedir = resources.getBasedir("starwars");
        MavenExecutionResult result = mavenRuntime
            .forProject(basedir)
            .execute("clean", "compile");

        result.assertErrorFreeLog();
    }
}
