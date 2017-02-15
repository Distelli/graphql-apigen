package com.distelli.graphql.apigen;

import java.io.File;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.2.5"})
public class AsyncApiGenMojoTest {
    @Rule
    public TestResources resources = new TestResources();

    public MavenRuntime mavenRuntime;

    public AsyncApiGenMojoTest(MavenRuntimeBuilder builder) throws Exception {
        mavenRuntime = builder.build();
    }

    @Test
    public void testBasic() throws Exception {
        File basedir = resources.getBasedir("asyncbasic");
        MavenExecutionResult result = mavenRuntime
            .forProject(basedir)
            .execute("clean", "test");

        result.assertErrorFreeLog();
    }

    @Test
    public void testStarwars() throws Exception {
        File basedir = resources.getBasedir("asyncstarwars");
        MavenExecutionResult result = mavenRuntime
            .forProject(basedir)
            .execute("clean", "compile");

        result.assertErrorFreeLog();
    }

    @Test
    public void testPosts() throws Exception {
        File basedir = resources.getBasedir("asyncposts");
        MavenExecutionResult result = mavenRuntime
            .forProject(basedir)
            .execute("clean", "test");

        result.assertErrorFreeLog();
    }
}
